package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.data.local.FilmFrameEntity
import com.example.data.local.FilmRollEntity
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmStockModel
import com.example.domain.model.HardwareCatalog
import com.example.domain.model.LensModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Handles high-quality local file output, authentic EXIF metadata tagging
 * (Boîtier, Objectif, Pellicule, Date de péremption, Procédé de développement),
 * MediaStore saving to DCIM/Argentique, and high-res Planche-Contact rendering.
 */
object ExifAndExportManager {

    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    /**
     * Saves bitmap to internal app storage, writes full analog EXIF tags if JPEG,
     * and exports a copy to DCIM/Argentique via MediaStore.
     */
    fun saveDevelopedFrame(
        context: Context,
        bitmap: Bitmap,
        roll: FilmRollEntity,
        frameNumber: Int,
        body: CameraBodyModel,
        lens: LensModel,
        film: FilmStockModel,
        expiration: FilmExpirationDate,
        devMethod: DevelopmentMethod,
        evCompensation: Float,
        losslessPng: Boolean
    ): Pair<String, Uri?> {
        val rollDir = File(context.filesDir, "developed/roll_${roll.id}").apply { mkdirs() }
        val ext = if (losslessPng) "png" else "jpg"
        val localFile = File(rollDir, "R%03d_F%02d.%s".format(roll.rollNumber, frameNumber, ext))

        FileOutputStream(localFile).use { fos ->
            if (losslessPng) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
        }

        if (!losslessPng) {
            writeAnalogExif(
                filePath = localFile.absolutePath,
                body = body,
                lens = lens,
                film = film,
                expiration = expiration,
                devMethod = devMethod,
                evCompensation = evCompensation,
                capturedAt = System.currentTimeMillis()
            )
        }

        val mediaUri = exportFileToMediaStore(
            context = context,
            sourceFile = localFile,
            displayName = "PELOCHE_R%03d_%s_F%02d.%s".format(roll.rollNumber, film.code, frameNumber, ext),
            mimeType = if (losslessPng) "image/png" else "image/jpeg"
        )

        return localFile.absolutePath to mediaUri
    }

    fun writeAnalogExif(
        filePath: String,
        body: CameraBodyModel,
        lens: LensModel,
        film: FilmStockModel,
        expiration: FilmExpirationDate,
        devMethod: DevelopmentMethod,
        evCompensation: Float,
        capturedAt: Long
    ) {
        try {
            val exif = ExifInterface(filePath)
            val effectiveIso = devMethod.effectiveIso(film.iso)
            val comment = buildString {
                append("Boîtier: ${body.name} (${body.aspectRatioSpec.displayLabel}) | ")
                append("Objectif: ${lens.name} ${lens.maxAperture} | ")
                append("Pellicule: ${film.name} (${film.emulsionType.labelFr}, ISO ${film.iso}) | ")
                append("Péremption: ${expiration.stampCode} (${expiration.severityLabel}) | ")
                append("Labo: ${devMethod.titleFr} (EI $effectiveIso) | ")
                append("EV: %+.1f IL".format(evCompensation))
            }
            exif.setAttribute(ExifInterface.TAG_MAKE, "Peloche")
            exif.setAttribute(ExifInterface.TAG_MODEL, "${body.name} + ${lens.shortCode}")
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Peloche 100% Local • ${film.code}")
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, comment)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, comment)
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${lens.focalLengthMm}/1")
            exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, effectiveIso.toString())
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateFormat.format(Date(capturedAt)))
            exif.saveAttributes()
        } catch (_: Throwable) {
        }
    }

    fun exportFileToMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DCIM}/Peloche"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { inp -> inp.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Renders an authentic darkroom Contact Sheet (Planche-Contact) with 35mm sprocket holes,
     * orange film edge markings, clapperboard header, and exports it to DCIM/Argentique.
     */
    fun exportContactSheet(
        context: Context,
        roll: FilmRollEntity,
        frames: List<FilmFrameEntity>,
        losslessPng: Boolean
    ): Uri? {
        if (frames.isEmpty()) return null
        val body = HardwareCatalog.bodyById(roll.bodyId)
        val lens = HardwareCatalog.lensById(roll.lensId)
        val film = HardwareCatalog.filmById(roll.filmId)
        val dev = roll.developmentMethod
        val exp = roll.expirationDate

        val cols = when {
            frames.size <= 6 -> 3
            frames.size <= 16 -> 4
            else -> 6
        }
        val rows = ceil(frames.size.toDouble() / cols.toDouble()).toInt().coerceAtLeast(1)

        val sheetW = 2400
        val sidePad = 80
        val headerH = 220
        val stripGap = 28
        val stripBandH = 44 // sprocket strip height top/bottom
        val cellGap = 18
        val cellW = (sheetW - sidePad * 2 - cellGap * (cols - 1)) / cols
        val aspect = body.aspectRatioSpec.aspectRatio.coerceIn(0.65f, 2.5f)
        val cellH = (cellW / aspect).toInt().coerceIn(140, 420)
        val stripTotalH = cellH + stripBandH * 2
        val sheetH = headerH + rows * (stripTotalH + stripGap) + 90

        val bitmap = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(11, 11, 13))

        // Header Clapperboard Strip
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 245, 238)
            textSize = 46f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 196, 67)
            textSize = 26f
            typeface = Typeface.MONOSPACE
        }
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 122, 47)
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val sprocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 37, 46)
        }
        val filmBasePaint = Paint().apply {
            color = Color.rgb(18, 20, 25)
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(52, 56, 68)
            strokeWidth = 2f
        }

        canvas.drawText(
            "PELOCHE  •  PLANCHE-CONTACT N°%03d".format(roll.rollNumber),
            sidePad.toFloat(),
            84f,
            titlePaint
        )
        canvas.drawText(
            "${body.name.uppercase()}  |  ${lens.name.uppercase()}  |  ${film.name.uppercase()} (${film.code} ISO ${film.iso})",
            sidePad.toFloat(),
            132f,
            subPaint
        )
        canvas.drawText(
            "${exp.stampCode}  •  LABO: ${dev.titleFr.uppercase()} (EI ${dev.effectiveIso(film.iso)})  •  ${frames.size} VUES",
            sidePad.toFloat(),
            172f,
            edgePaint
        )
        canvas.drawLine(sidePad.toFloat(), 196f, (sheetW - sidePad).toFloat(), 196f, dividerPaint)

        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (r in 0 until rows) {
            val stripTop = headerH + r * (stripTotalH + stripGap)
            canvas.drawRect(
                sidePad.toFloat() - 16f,
                stripTop.toFloat(),
                (sheetW - sidePad + 16).toFloat(),
                (stripTop + stripTotalH).toFloat(),
                filmBasePaint
            )

            // Draw realistic 35mm sprocket holes along top and bottom film borders
            var sx = sidePad
            while (sx < sheetW - sidePad - 20) {
                canvas.drawRoundRect(
                    RectF(sx.toFloat(), stripTop + 10f, sx + 22f, stripTop + 30f),
                    4f, 4f, sprocketPaint
                )
                canvas.drawRoundRect(
                    RectF(sx.toFloat(), (stripTop + stripTotalH - 30).toFloat(), sx + 22f, (stripTop + stripTotalH - 10).toFloat()),
                    4f, 4f, sprocketPaint
                )
                sx += 48
            }

            for (c in 0 until cols) {
                val idx = r * cols + c
                if (idx >= frames.size) break
                val frame = frames[idx]
                val left = sidePad + c * (cellW + cellGap)
                val top = stripTop + stripBandH
                val dst = Rect(left, top, left + cellW, top + cellH)

                val path = frame.developedPath
                if (path != null && File(path).exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = BitmapFactory.decodeFile(path, opts)
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, null, dst, imgPaint)
                        bmp.recycle()
                    }
                }

                // Orange edge frame numbers
                canvas.drawText(
                    "%02dA  ${film.code}".format(frame.frameNumber),
                    left.toFloat() + 6f,
                    (stripTop + stripTotalH - 12).toFloat(),
                    edgePaint
                )
            }
        }

        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val tmpFile = File(
            shareDir,
            "PELOCHE_PLANCHE_R%03d_%s.%s".format(roll.rollNumber, film.code, if (losslessPng) "png" else "jpg")
        )
        FileOutputStream(tmpFile).use { fos ->
            if (losslessPng) bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        bitmap.recycle()

        return exportFileToMediaStore(
            context = context,
            sourceFile = tmpFile,
            displayName = tmpFile.name,
            mimeType = if (losslessPng) "image/png" else "image/jpeg"
        )
    }

    /**
     * Renders a temporary Contact Sheet image inside cache/share for instant Android Share Sheet dispatch.
     */
    fun renderContactSheetForShare(
        context: Context,
        roll: FilmRollEntity,
        frames: List<FilmFrameEntity>,
        losslessPng: Boolean
    ): File? {
        if (frames.isEmpty()) return null
        val film = HardwareCatalog.filmById(roll.filmId)
        exportContactSheet(context, roll, frames, losslessPng)
        val shareDir = File(context.cacheDir, "share")
        val file = File(
            shareDir,
            "PELOCHE_PLANCHE_R%03d_%s.%s".format(roll.rollNumber, film.code, if (losslessPng) "png" else "jpg")
        )
        return file.takeIf { it.exists() }
    }

    /**
     * Opens the native Android Share Sheet for any developed photo or contact sheet file.
     */
    fun shareImageFile(context: Context, file: File, chooserTitle: String): Boolean {
        if (!file.exists()) return false
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mime = if (file.name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
