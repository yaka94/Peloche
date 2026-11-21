package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.DateStampStyle
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmStockModel
import com.example.domain.model.HardwareCatalog
import com.example.domain.model.LensModel
import com.example.domain.model.OpticalChainParams
import com.example.engine.ExifAndExportManager
import com.example.engine.OffscreenGlProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class InstantPrintResult(
    val developedPath: String,
    val originalPath: String
)

/**
 * 100% Local Repository enforcing the true analog workflow:
 * 1. Each loaded roll receives a randomized expiration date (unless user explicitly picks one).
 * 2. Captured shots are stored privately as latent undeveloped negatives (unviewable until developed!).
 * 3. Lab development applies the chosen chemical DevelopmentMethod (Standard, Push/Pull,
 *    Cross-Process, Bleach Bypass, Sepia, Cyanotype, Rodinal) in full resolution, writes EXIF,
 *    stitches Half-Frame diptychs when enabled, and exports to DCIM/Argentique.
 */
class FilmRepository(
    private val context: Context,
    private val filmDao: FilmDao
) {
    val activeRoll: Flow<FilmRollEntity?> = filmDao.observeActiveRoll()
    val inProgressRolls: Flow<List<FilmRollEntity>> = filmDao.observeInProgressRolls()
    val developedRolls: Flow<List<FilmRollEntity>> = filmDao.observeDevelopedRolls()
    val allDevelopedFrames: Flow<List<FilmFrameEntity>> = filmDao.observeAllDevelopedFrames()

    private val retroDateFormat = SimpleDateFormat("''yy MM dd", Locale.US)

    fun observeFrames(rollId: Long): Flow<List<FilmFrameEntity>> =
        filmDao.observeFramesForRoll(rollId)

    /**
     * Ensures there is always an active film roll loaded inside the camera body.
     * Generates a randomized expiration date for each newly loaded roll!
     */
    suspend fun ensureActiveRoll(): FilmRollEntity = withContext(Dispatchers.IO) {
        val existing = filmDao.getActiveRollOnce()
        if (existing != null) return@withContext existing

        val defaultBody = HardwareCatalog.bodies[0]
        val defaultLens = HardwareCatalog.defaultLensFor(defaultBody)
        val defaultFilm = HardwareCatalog.films[0]  // Vivid 100
        loadFreshRoll(
            body = defaultBody,
            lens = defaultLens,
            film = defaultFilm,
            expirationDate = FilmExpirationDate.randomForNewRoll(),
            isDiptychMode = false
        )
    }

    suspend fun loadFreshRoll(
        body: CameraBodyModel,
        lens: LensModel,
        film: FilmStockModel,
        expirationDate: FilmExpirationDate = FilmExpirationDate.randomForNewRoll(),
        isDiptychMode: Boolean = false
    ): FilmRollEntity = withContext(Dispatchers.IO) {
        filmDao.deactivateAllRolls()
        val resolvedLens = if (HardwareCatalog.isLensCompatible(lens, body)) lens else HardwareCatalog.defaultLensFor(body)
        val nextNumber = (filmDao.getMaxRollNumber() ?: 0) + 1
        val newRoll = FilmRollEntity(
            rollNumber = nextNumber,
            bodyId = body.id,
            lensId = resolvedLens.id,
            filmId = film.id,
            expirationSerialized = expirationDate.serialize(),
            maxExposures = body.maxExposures,
            shotsTaken = 0,
            isDeveloped = false,
            developmentMethodName = DevelopmentMethod.STANDARD_C41.name,
            isActive = true,
            isDiptychMode = isDiptychMode && body.supportsDiptych
        )
        val id = filmDao.insertRoll(newRoll)
        filmDao.deleteEmptyDraftRollsExcept(id)
        newRoll.copy(id = id)
    }

    /**
     * Photographer's Bag switching:
     * - Switching to a camera body that ALREADY holds an in-progress roll (shotsTaken > 0)
     *   immediately resumes that roll (film, expiration, counter) without losing any other body's roll!
     * - Switching to an empty camera body preserves the previous body's started roll in the bag
     *   and opens a fresh unexposed slot on the newly picked body (with a compatible lens).
     */
    suspend fun switchToBody(
        body: CameraBodyModel,
        preferredLens: LensModel,
        preferredFilm: FilmStockModel,
        preferredExpiration: FilmExpirationDate,
        isDiptychMode: Boolean
    ): FilmRollEntity = withContext(Dispatchers.IO) {
        val currentActive = filmDao.getActiveRollOnce()
        val existingForBody = filmDao.getUndevelopedRollForBody(body.id)
        val compatiblePreferredLens = if (HardwareCatalog.isLensCompatible(preferredLens, body)) {
            preferredLens
        } else {
            HardwareCatalog.defaultLensFor(body)
        }

        // Case 1: The target body already has a started roll sitting in the bag!
        if (existingForBody != null && existingForBody.shotsTaken > 0) {
            filmDao.deactivateAllRolls()
            val existingLens = HardwareCatalog.lensById(existingForBody.lensId)
            val resolvedLens = if (HardwareCatalog.isLensCompatible(existingLens, body)) {
                existingLens
            } else {
                HardwareCatalog.defaultLensFor(body)
            }
            val resumed = existingForBody.copy(
                isActive = true,
                lensId = resolvedLens.id
            )
            filmDao.updateRoll(resumed)
            filmDao.deleteEmptyDraftRollsExcept(resumed.id)
            return@withContext resumed
        }

        // Case 2: Target body has no started roll yet.
        // If current active roll is unexposed (0 shots), repurpose it cleanly so roll numbers don't inflate.
        if (currentActive != null && currentActive.shotsTaken == 0) {
            val updated = currentActive.copy(
                bodyId = body.id,
                lensId = compatiblePreferredLens.id,
                filmId = preferredFilm.id,
                expirationSerialized = preferredExpiration.serialize(),
                maxExposures = body.maxExposures,
                isActive = true,
                isDiptychMode = isDiptychMode && body.supportsDiptych
            )
            filmDao.updateRoll(updated)
            filmDao.deleteEmptyDraftRollsExcept(updated.id)
            return@withContext updated
        }

        // Case 3: Current active roll has shots taken -> preserve it in the bag and load a fresh slot for `body`.
        loadFreshRoll(
            body = body,
            lens = compatiblePreferredLens,
            film = preferredFilm,
            expirationDate = preferredExpiration,
            isDiptychMode = isDiptychMode
        )
    }

    /**
     * Activates a specific undeveloped roll from the photographer's bag (e.g., from Labo).
     */
    suspend fun switchToRoll(rollId: Long): FilmRollEntity? = withContext(Dispatchers.IO) {
        val target = filmDao.getRollById(rollId) ?: return@withContext null
        if (target.isDeveloped) return@withContext null
        filmDao.deactivateAllRolls()
        val body = HardwareCatalog.bodyById(target.bodyId)
        val currentLens = HardwareCatalog.lensById(target.lensId)
        val resolvedLens = if (HardwareCatalog.isLensCompatible(currentLens, body)) {
            currentLens
        } else {
            HardwareCatalog.defaultLensFor(body)
        }
        val activated = target.copy(isActive = true, lensId = resolvedLens.id)
        filmDao.updateRoll(activated)
        filmDao.deleteEmptyDraftRollsExcept(activated.id)
        activated
    }

    /**
     * Updates hardware on the active roll:
     * - Lens can ALWAYS be swapped mid-roll (like a real interchangeable-lens camera!).
     * - Body, Film & Expiration can be modified freely while shotsTaken == 0 (unexposed roll).
     */
    suspend fun updateActiveRollHardware(
        roll: FilmRollEntity,
        body: CameraBodyModel,
        lens: LensModel,
        film: FilmStockModel,
        expirationDate: FilmExpirationDate,
        isDiptychMode: Boolean
    ): FilmRollEntity = withContext(Dispatchers.IO) {
        val resolvedLens = if (HardwareCatalog.isLensCompatible(lens, body)) lens else HardwareCatalog.defaultLensFor(body)
        val updated = if (roll.shotsTaken == 0) {
            roll.copy(
                bodyId = body.id,
                lensId = resolvedLens.id,
                filmId = film.id,
                expirationSerialized = expirationDate.serialize(),
                maxExposures = body.maxExposures,
                isDiptychMode = isDiptychMode && body.supportsDiptych
            )
        } else {
            // Mid-roll: compatible lens swap & diptych toggle allowed; roll emulsion remains locked inside camera
            val rollBody = HardwareCatalog.bodyById(roll.bodyId)
            val rollLens = if (HardwareCatalog.isLensCompatible(lens, rollBody)) lens else HardwareCatalog.defaultLensFor(rollBody)
            roll.copy(
                lensId = rollLens.id,
                isDiptychMode = isDiptychMode && rollBody.supportsDiptych
            )
        }
        filmDao.updateRoll(updated)
        updated
    }

    /**
     * Saves a captured latent negative privately inside app storage (unviewable until developed!).
     */
    suspend fun recordLatentExposure(
        roll: FilmRollEntity,
        sourceBitmap: Bitmap,
        lens: LensModel,
        evCompensation: Float,
        zoomMultiplier: Float,
        dateStampStyle: DateStampStyle,
        isFrontCamera: Boolean,
        lightLeakSeed: Float = Random.nextFloat()
    ): Pair<FilmRollEntity, FilmFrameEntity> = withContext(Dispatchers.IO) {
        val nextFrameNum = roll.shotsTaken + 1
        val latentDir = File(context.filesDir, "latent/roll_${roll.id}").apply { mkdirs() }
        val latentFile = File(latentDir, "latent_%02d.jpg".format(nextFrameNum))

        FileOutputStream(latentFile).use { fos ->
            sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos)
        }

        val frame = FilmFrameEntity(
            rollId = roll.id,
            frameNumber = nextFrameNum,
            rawLatentPath = latentFile.absolutePath,
            developedPath = null,
            bodyId = roll.bodyId,
            lensId = lens.id,
            filmId = roll.filmId,
            expirationSerialized = roll.expirationSerialized,
            evCompensation = evCompensation,
            zoomMultiplier = zoomMultiplier,
            lightLeakSeed = lightLeakSeed,
            dateStampText = dateStampStyle.encodeStamp(Date()),
            isFrontCamera = isFrontCamera,
            isDiptychPaired = roll.isDiptychMode
        )
        val frameId = filmDao.insertFrame(frame)
        val updatedRoll = roll.copy(shotsTaken = nextFrameNum)
        filmDao.updateRoll(updatedRoll)
        updatedRoll to frame.copy(id = frameId)
    }

    /**
     * Executes chemical darkroom development of all latent frames on a roll:
     * Applies full-resolution Lens + Body + Film + Expiration + chosen DevelopmentMethod,
     * optionally stitches Half-Frame diptych pairs, writes EXIF metadata, saves to MediaStore,
     * marks roll as developed, and loads a fresh roll into the camera.
     */
    suspend fun developRoll(
        roll: FilmRollEntity,
        method: DevelopmentMethod,
        losslessPng: Boolean,
        includeFrameBorder: Boolean = true,
        lightLeakMode: com.example.domain.model.LightLeakMode = com.example.domain.model.LightLeakMode.AUTO,
        onProgress: (Float, String) -> Unit
    ): FilmRollEntity = withContext(Dispatchers.IO) {
        val frames = filmDao.getFramesForRollOnce(roll.id)
        val body = HardwareCatalog.bodyById(roll.bodyId)
        val film = HardwareCatalog.filmById(roll.filmId)
        val expiration = roll.expirationDate

        onProgress(0.08f, "Rembobinage dans la cartouche 35mm…")

        val overlayBmp = BitmapFactory.decodeResource(context.resources, com.example.R.drawable.img_optical_flare_overlay)

        val total = frames.size.coerceAtLeast(1)
        val renderedBitmaps = mutableListOf<Pair<FilmFrameEntity, Bitmap>>()

        frames.forEachIndexed { index, frame ->
            val pct = 0.12f + (index.toFloat() / total.toFloat()) * 0.62f
            val stepText = when {
                pct < 0.35f -> "Bain révélateur ${method.shortBadge} (${method.tempAndTime}) — vue ${frame.frameNumber}/$total"
                pct < 0.60f -> "Bain d'arrêt & fixateur argentique — vue ${frame.frameNumber}/$total"
                else -> "Lavage & numérisation haute résolution — vue ${frame.frameNumber}/$total"
            }
            onProgress(pct, stepText)

            val rawFile = File(frame.rawLatentPath)
            if (rawFile.exists()) {
                val src = BitmapFactory.decodeFile(rawFile.absolutePath)
                if (src != null) {
                    val lens = HardwareCatalog.lensById(frame.lensId)
                    val params = OpticalChainParams(
                        lens = lens,
                        body = body,
                        film = film,
                        expirationDate = expiration,
                        development = method,
                        evCompensation = frame.evCompensation,
                        zoomMultiplier = frame.zoomMultiplier,
                        lightLeakSeed = frame.lightLeakSeed,
                        isFrontCamera = frame.isFrontCamera,
                        lightLeakMode = lightLeakMode
                    )
                    val developed = OffscreenGlProcessor.processFrame(
                        source = src,
                        params = params,
                        dateStampText = frame.dateStampText,
                        overlayBitmap = overlayBmp,
                        timeSeed = (frame.frameNumber * 13.7f) + frame.lightLeakSeed * 50f,
                        includeFrameBorder = includeFrameBorder && !(roll.isDiptychMode && body.supportsDiptych),
                        frameNumber = frame.frameNumber
                    )
                    // Save lightweight original reference for Avant/Après comparison in the Labo Loupe
                    saveOriginalComparisonCopy(
                        source = src,
                        targetDir = File(context.filesDir, "developed/roll_${roll.id}"),
                        fileName = "orig_F%02d.jpg".format(frame.frameNumber)
                    )
                    src.recycle()
                    renderedBitmaps.add(frame to developed)
                }
            }
        }
        overlayBmp?.recycle()

        onProgress(0.80f, "Écriture des métadonnées EXIF et export local…")

        // Handle Half-Frame Diptych pairing if enabled on this roll
        val shouldStitchDiptych = roll.isDiptychMode && body.supportsDiptych && renderedBitmaps.size >= 2
        if (shouldStitchDiptych) {
            var i = 0
            while (i < renderedBitmaps.size) {
                val (frameA, bmpA) = renderedBitmaps[i]
                if (i + 1 < renderedBitmaps.size) {
                    val (frameB, bmpB) = renderedBitmaps[i + 1]
                    val diptych = OffscreenGlProcessor.stitchHalfFrameDiptych(bmpA, bmpB)
                    val lensA = HardwareCatalog.lensById(frameA.lensId)
                    val (pathA, uriA) = ExifAndExportManager.saveDevelopedFrame(
                        context, diptych, roll, frameA.frameNumber, body, lensA, film,
                        expiration, method, frameA.evCompensation, losslessPng
                    )
                    // Also save individual frames so every slot in contact sheet is intact
                    val (pathB, uriB) = ExifAndExportManager.saveDevelopedFrame(
                        context, bmpB, roll, frameB.frameNumber, body,
                        HardwareCatalog.lensById(frameB.lensId), film,
                        expiration, method, frameB.evCompensation, losslessPng
                    )
                    filmDao.updateFrame(frameA.copy(developedPath = pathA, exportedMediaUri = uriA?.toString(), isDiptychPaired = true))
                    filmDao.updateFrame(frameB.copy(developedPath = pathB, exportedMediaUri = uriB?.toString(), isDiptychPaired = false))
                    diptych.recycle()
                    bmpA.recycle()
                    bmpB.recycle()
                    i += 2
                } else {
                    val (pathA, uriA) = ExifAndExportManager.saveDevelopedFrame(
                        context, bmpA, roll, frameA.frameNumber, body,
                        HardwareCatalog.lensById(frameA.lensId), film,
                        expiration, method, frameA.evCompensation, losslessPng
                    )
                    filmDao.updateFrame(frameA.copy(developedPath = pathA, exportedMediaUri = uriA?.toString()))
                    bmpA.recycle()
                    i += 1
                }
            }
        } else {
            renderedBitmaps.forEach { (frame, bmp) ->
                val lens = HardwareCatalog.lensById(frame.lensId)
                val (path, uri) = ExifAndExportManager.saveDevelopedFrame(
                    context, bmp, roll, frame.frameNumber, body, lens, film,
                    expiration, method, frame.evCompensation, losslessPng
                )
                filmDao.updateFrame(frame.copy(developedPath = path, exportedMediaUri = uri?.toString()))
                bmp.recycle()
            }
        }

        // Remove private latent raw files once developed
        File(context.filesDir, "latent/roll_${roll.id}").deleteRecursively()

        val finishedRoll = roll.copy(
            isDeveloped = true,
            isActive = false,
            developmentMethodName = method.name,
            developedAt = System.currentTimeMillis()
        )
        filmDao.updateRoll(finishedRoll)

        onProgress(1.0f, "Rouleau développé et archivé !")
        finishedRoll
    }

    suspend fun deleteRoll(rollId: Long) = withContext(Dispatchers.IO) {
        File(context.filesDir, "latent/roll_$rollId").deleteRecursively()
        File(context.filesDir, "developed/roll_$rollId").deleteRecursively()
        filmDao.deleteRollById(rollId)
    }

    suspend fun renameRoll(rollId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        filmDao.updateRollTitle(rollId, newTitle.trim().take(48))
    }

    suspend fun updateFrameCaption(frameId: Long, caption: String) = withContext(Dispatchers.IO) {
        filmDao.updateFrameCaption(frameId, caption.trim().take(72))
    }

    /**
     * Develops a single frame immediately (used for "Mode Aperçu Immédiat" after shutter release
     * and for zero-permission Gallery Photo Import), saving both the developed print and the
     * untouched original for Press & Hold Avant/Après comparison.
     */
    suspend fun renderInstantPreview(
        sourceBitmap: Bitmap,
        body: CameraBodyModel,
        lens: LensModel,
        film: FilmStockModel,
        expirationDate: FilmExpirationDate,
        evCompensation: Float,
        zoomMultiplier: Float,
        dateStampStyle: DateStampStyle,
        includeFrameBorder: Boolean,
        frameNumber: Int,
        isFrontCamera: Boolean,
        lightLeakSeed: Float = Random.nextFloat(),
        lightLeakMode: com.example.domain.model.LightLeakMode = com.example.domain.model.LightLeakMode.AUTO,
        tiltShiftPosition: Float = 0.5f,
        tiltShiftVertical: Boolean = false
    ): InstantPrintResult = withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "instant_preview").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val origFile = saveOriginalComparisonCopy(
            source = sourceBitmap,
            targetDir = outDir,
            fileName = "orig_$stamp.jpg"
        )
        val overlayBmp = BitmapFactory.decodeResource(context.resources, com.example.R.drawable.img_optical_flare_overlay)
        val params = OpticalChainParams(
            lens = lens,
            body = body,
            film = film,
            expirationDate = expirationDate,
            development = DevelopmentMethod.STANDARD_C41,
            evCompensation = evCompensation,
            zoomMultiplier = zoomMultiplier,
            lightLeakSeed = lightLeakSeed,
            isFrontCamera = isFrontCamera,
            lightLeakMode = lightLeakMode,
            tiltShiftPosition = tiltShiftPosition,
            tiltShiftVertical = tiltShiftVertical
        )
        val developed = OffscreenGlProcessor.processFrame(
            source = sourceBitmap,
            params = params,
            dateStampText = dateStampStyle.encodeStamp(Date()),
            overlayBitmap = overlayBmp,
            timeSeed = (frameNumber * 13.7f) + lightLeakSeed * 50f,
            includeFrameBorder = includeFrameBorder,
            frameNumber = frameNumber
        )
        overlayBmp?.recycle()

        val devFile = File(outDir, "dev_$stamp.jpg")
        FileOutputStream(devFile).use { fos ->
            developed.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        developed.recycle()

        InstantPrintResult(
            developedPath = devFile.absolutePath,
            originalPath = origFile.absolutePath
        )
    }

    private fun saveOriginalComparisonCopy(
        source: Bitmap,
        targetDir: File,
        fileName: String
    ): File {
        targetDir.mkdirs()
        val outFile = File(targetDir, fileName)
        val maxDim = max(source.width, source.height).coerceAtLeast(1)
        val scale = if (maxDim > 1080) 1080f / maxDim else 1f
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        try {
            FileOutputStream(outFile).use { fos ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 84, fos)
            }
        } finally {
            if (thumb !== source) thumb.recycle()
        }
        return outFile
    }
}
