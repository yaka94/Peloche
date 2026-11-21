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
            "PELOCHE  •  ${roll.displayTitle.uppercase()}  (N°%03d)".format(roll.rollNumber),
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

    /**
     * Renders a museum-paper Gallery Print Card (warm cream matte background #F4F2EC,
     * soft multi-layer realistic drop shadow, and centered refined caption e.g. "Pastel 400 · Half Frame")
     * in either 4:5 Post (1440x1800) or 9:16 Story (1440x2560) format for sharing.
     */
    fun renderGalleryPrintCard(
        sourceFile: File,
        caption: String,
        storyFormat916: Boolean,
        handwrittenNote: String = ""
    ): Bitmap? {
        if (!sourceFile.exists()) return null
        val src = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
        return try {
            val outW = 1440
            val outH = if (storyFormat916) 2560 else 1800
            val card = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(card)

            // Warm museum archival paper backdrop (#F4F2EC)
            canvas.drawColor(Color.rgb(244, 242, 236))

            val hasNote = handwrittenNote.trim().isNotEmpty()
            val sideMargin = (outW * 0.095f)
            val topMargin = if (storyFormat916) outH * 0.13f else outH * 0.075f
            val bottomCaptionZone = when {
                storyFormat916 && hasNote -> outH * 0.20f
                storyFormat916 -> outH * 0.17f
                hasNote -> outH * 0.18f
                else -> outH * 0.14f
            }
            val availW = outW - sideMargin * 2f
            val availH = outH - topMargin - bottomCaptionZone

            val imgAspect = src.width.toFloat() / src.height.coerceAtLeast(1).toFloat()
            val drawW: Float
            val drawH: Float
            if (availW / availH > imgAspect) {
                drawH = availH
                drawW = drawH * imgAspect
            } else {
                drawW = availW
                drawH = drawW / imgAspect
            }

            val left = (outW - drawW) / 2f
            val top = topMargin + (availH - drawH) / 2f
            val dstRect = RectF(left, top, left + drawW, top + drawH)

            // Multi-layer soft museum drop shadow underneath the print
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shadowLayers = listOf(
                Triple(32f, 22f, 16),
                Triple(20f, 14f, 24),
                Triple(10f, 7f, 34),
                Triple(4f, 3f, 40)
            )
            shadowLayers.forEach { (blurPad, yOff, alpha) ->
                shadowPaint.color = Color.argb(alpha, 24, 21, 18)
                canvas.drawRoundRect(
                    RectF(
                        dstRect.left - blurPad * 0.35f,
                        dstRect.top + yOff - blurPad * 0.2f,
                        dstRect.right + blurPad * 0.35f,
                        dstRect.bottom + yOff + blurPad * 0.55f
                    ),
                    blurPad * 0.45f,
                    blurPad * 0.45f,
                    shadowPaint
                )
            }

            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(src, null, dstRect, imgPaint)

            // Handwritten fountain-pen note (optional)
            val cleanNote = handwrittenNote.trim()
            val captionY = if (cleanNote.isNotEmpty()) {
                val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(38, 35, 31)
                    textSize = if (cleanNote.length > 36) 44f else 54f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                }
                val noteY = dstRect.bottom + 84f
                canvas.save()
                canvas.rotate(-1.1f, outW / 2f, noteY)
                canvas.drawText("“$cleanNote”", outW / 2f, noteY, notePaint)
                canvas.restore()
                dstRect.bottom + 148f
            } else {
                dstRect.bottom + 82f
            }

            // Minimalist centered museum caption below the print (e.g. "Pastel 400 · Half Frame")
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(112, 107, 99)
                textSize = 34f
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.04f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(caption, outW / 2f, captionY, captionPaint)
            card
        } finally {
            src.recycle()
        }
    }

    fun exportGalleryPrintCard(
        context: Context,
        sourceFile: File,
        caption: String,
        filePrefix: String,
        storyFormat916: Boolean,
        losslessPng: Boolean,
        handwrittenNote: String = ""
    ): Pair<File?, Uri?> {
        val card = renderGalleryPrintCard(
            sourceFile = sourceFile,
            caption = caption,
            storyFormat916 = storyFormat916,
            handwrittenNote = handwrittenNote
        ) ?: return null to null
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val ext = if (losslessPng) "png" else "jpg"
        val formatTag = if (storyFormat916) "STORY" else "GALERIE"
        val outFile = File(shareDir, "${filePrefix}_${formatTag}.$ext")
        FileOutputStream(outFile).use { fos ->
            if (losslessPng) card.compress(Bitmap.CompressFormat.PNG, 100, fos)
            else card.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        card.recycle()
        val uri = exportFileToMediaStore(
            context = context,
            sourceFile = outFile,
            displayName = outFile.name,
            mimeType = if (losslessPng) "image/png" else "image/jpeg"
        )
        return outFile to uri
    }

    /**
     * Exports a single developed print to DCIM/Peloche, optionally engraving a handwritten note
     * onto a clean cream margin (or Polaroid/Instax chin) when provided.
     */
    fun exportFrameWithOptionalNote(
        context: Context,
        sourceFile: File,
        handwrittenNote: String,
        displayName: String,
        losslessPng: Boolean
    ): Uri? {
        val cleanNote = handwrittenNote.trim()
        if (cleanNote.isEmpty()) {
            return exportFileToMediaStore(
                context = context,
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = if (losslessPng) "image/png" else "image/jpeg"
            )
        }
        val src = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
        val marginH = (src.height * 0.11f).toInt().coerceIn(90, 210)
        val out = Bitmap.createBitmap(src.width, src.height + marginH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(245, 242, 234))
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        src.recycle()

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 35, 31)
            textSize = (marginH * 0.36f).coerceIn(26f, 64f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val textY = out.height - marginH * 0.38f
        canvas.save()
        canvas.rotate(-0.9f, out.width / 2f, textY)
        canvas.drawText(cleanNote, out.width / 2f, textY, notePaint)
        canvas.restore()

        val tmpDir = File(context.cacheDir, "share").apply { mkdirs() }
        val tmpFile = File(tmpDir, displayName)
        FileOutputStream(tmpFile).use { fos ->
            if (losslessPng) out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            else out.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        out.recycle()
        return exportFileToMediaStore(
            context = context,
            sourceFile = tmpFile,
            displayName = displayName,
            mimeType = if (losslessPng) "image/png" else "image/jpeg"
        )
    }

    /**
     * Saves a recorded Super 8 / 35mm MP4 clip to Movies/Peloche via MediaStore (zero storage permission on API 29+).
     */
    fun exportVideoToMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String
    ): Uri? {
        if (!sourceFile.exists()) return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/Peloche"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { inp -> inp.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Shares a recorded Super 8 / 35mm MP4 clip via native Android Share Sheet.
     */
    fun shareVideoFile(context: Context, file: File, chooserTitle: String): Boolean {
        if (!file.exists()) return false
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
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
