package com.example.domain.model

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Physical film frame geometry & aspect ratio defined by the Camera Body (Boîtier).
 */
enum class AspectRatioSpec(
    val displayLabel: String,
    val ratioWidth: Float,
    val ratioHeight: Float,
    val formatBadge: String,
    val filmGaugeLabel: String
) {
    CLASSIC_3_2("3:2 • 35mm", 3f, 2f, "3:2", "135 • 35mm"),
    COMPACT_4_3("4:3 • Compact 90s", 4f, 3f, "4:3", "135 • 4:3"),
    HALF_FRAME_3_4("3:4 • Demi-Format", 3f, 4f, "HALF", "135 • 18×24"),
    SQUARE_1_1("1:1 • Moyen Format 6×6", 1f, 1f, "6×6", "120 • 6×6"),
    PANORAMIC_XPAN("65:24 • Panoramique", 65f, 24f, "XPAN", "135 • 24×65"),
    DISPOSABLE_3_2("3:2 • Jetable", 3f, 2f, "TOY", "135 • JETABLE"),
    POLAROID_SQUARE("1:1 • Polaroid", 1f, 1f, "POLA", "SX-70 • INSTANT"),
    INSTAX_MINI("3:4 • Instax Mini", 3f, 4f, "INSTAX", "MINI • INSTANT"),
    LARGE_FORMAT_4_5("4:5 • Chambre 4×5", 4f, 5f, "4×5", "PLAQUE • XIXe");

    val aspectRatio: Float get() = ratioWidth / ratioHeight
}

/**
 * Signature physical film / instant paper / 19th-century plate border contour tied to each Camera Body.
 */
enum class FrameBorderStyle(
    val labelFr: String,
    val shortBadge: String
) {
    FILM_35MM_SPROCKETS("Pellicule 35mm perforée", "35mm"),
    HALF_FRAME_STRIP("Bande Demi-Format 18×24", "HALF"),
    MEDIUM_FORMAT_6X6("Cadre 6×6 encoches 120", "120 6×6"),
    PANORAMIC_XPAN("Bande Ciné Panoramique", "XPAN"),
    COMPACT_LAB_PRINT("Tirage Labo 90s Crème", "TIRAGE"),
    HOLGA_MASK_6X6("Masque plastique Holga", "HOLGA"),
    DISPOSABLE_CARD("Fenêtre Jetable 27", "JETABLE"),
    POLAROID_WHITE("Cadre Polaroid Blanc", "POLA"),
    INSTAX_MINI_WHITE("Cadre Instax Mini", "INSTAX"),
    WET_PLATE_COLLODION("Plaque Collodion / Tôle", "PLAQUE"),
    DAGUERREOTYPE_CASE("Écrin Daguerréotype Laiton", "ÉCRIN"),
    AUTOCHROME_GLASS("Plaque Verre Autochrome", "VERRE"),
    PINHOLE_WOOD_BOX("Chambre Sténopé Bois", "STÉNOPÉ")
}

/**
 * Procedural analog cinema & vintage video filters available when shooting in CINÉ mode.
 * Each filter runs live in GLSL and is baked directly into recorded MP4 clips.
 */
enum class VideoFilterStyle(
    val shaderModeId: Int,
    val labelFr: String,
    val shortBadge: String,
    val targetFps: Int,
    val fpsLabel: String,
    val accentHex: Long,
    val descriptionFr: String
) {
    SUPER_8_KODACHROME(
        shaderModeId = 1,
        labelFr = "Super 8 Kodachrome",
        shortBadge = "SUPER 8",
        targetFps = 18,
        fpsLabel = "18 i/s",
        accentHex = 0xFFE8A438,
        descriptionFr = "Grain chaud années 70, flottement de fenêtre (gate weave) et poussières"
    ),
    SILENT_1920(
        shaderModeId = 2,
        labelFr = "Muet Pathé 1920",
        shortBadge = "MUET 1920",
        targetFps = 14,
        fpsLabel = "14 i/s",
        accentHex = 0xFFCFA76E,
        descriptionFr = "Cinéma muet sépia orthochromatique, scintillement lampe à arc, rayures et iris"
    ),
    VHS_1989(
        shaderModeId = 3,
        labelFr = "VHS Caméscope 89",
        shortBadge = "VHS 89",
        targetFps = 25,
        fpsLabel = "25 i/s",
        accentHex = 0xFF4ECDC4,
        descriptionFr = "Lignes CRT, bavure chroma Y/C, tremblement de tracking bas d'écran et dropouts"
    ),
    HANDYCAM_HI8_1996(
        shaderModeId = 7,
        labelFr = "Hi8 Handycam 96",
        shortBadge = "HI8 96",
        targetFps = 25,
        fpsLabel = "25 i/s",
        accentHex = 0xFFFF9F43,
        descriptionFr = "Caméscope familial années 90 : teintes ambre pastel, entrelacement doux et halo vidéo"
    ),
    TECHNICOLOR_1955(
        shaderModeId = 6,
        labelFr = "Technicolor 1955",
        shortBadge = "TECHNI 55",
        targetFps = 24,
        fpsLabel = "24 i/s",
        accentHex = 0xFFE63946,
        descriptionFr = "Procédé 3-bandes Hollywood : rouges carmin, cyans émeraude et peaux dorées veloutées"
    ),
    CINE_16MM_BW(
        shaderModeId = 4,
        labelFr = "16mm Nouvelle Vague",
        shortBadge = "16MM N&B",
        targetFps = 24,
        fpsLabel = "24 i/s",
        accentHex = 0xFFD9D5CC,
        descriptionFr = "Noir & blanc argentique 16mm nerveux, contraste documentaire et grain fin"
    ),
    NIGHT_35MM_NEON(
        shaderModeId = 5,
        labelFr = "35mm Néon Ciné",
        shortBadge = "35MM NÉON",
        targetFps = 24,
        fpsLabel = "24 i/s",
        accentHex = 0xFF38BDF8,
        descriptionFr = "Pellicule cinéma nocturne ECN-2, halos carmin profonds et reflets anamorphiques bleutés"
    ),
    LOMOKINO_35MM(
        shaderModeId = 8,
        labelFr = "LomoKino Manivelle",
        shortBadge = "LOMOKINO",
        targetFps = 8,
        fpsLabel = "8 i/s",
        accentHex = 0xFFF97316,
        descriptionFr = "Ciné 35mm à manivelle saccadé 8 i/s, bande panoramique, contraste Lomo et fuite bord"
    ),
    OPTICAL_CHAIN(
        shaderModeId = 0,
        labelFr = "Chaîne Optique Active",
        shortBadge = "OPTIQUE",
        targetFps = 24,
        fpsLabel = "24 i/s",
        accentHex = 0xFFD4A253,
        descriptionFr = "Filme directement à travers votre boîtier, objectif et pellicule montés (dont Collodion)"
    );
}

/**
 * Curated factory hardware + film combos grouped by photography era for 1-tap discovery.
 */
data class CuratedEraCombo(
    val id: String,
    val eraBadge: String,
    val title: String,
    val subtitle: String,
    val bodyId: String,
    val lensId: String,
    val filmId: String,
    val accentHex: Long
)

/**
 * Hardware LED flash mode for night / direct-flash analog aesthetics.
 */
enum class CameraFlashMode(val labelFr: String, val shortBadge: String) {
    OFF("Flash désactivé", "OFF"),
    AUTO("Flash automatique", "AUTO"),
    ON("Flash forcé", "ON");

    fun next(): CameraFlashMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Controls physical light-seal leak intensity across camera bodies.
 * AUTO respects each camera body's authentic foam-seal age; SUBTLE/STRONG allow forcing
 * visible procedural light leaks on any body (including clean Pro SLR / Rangefinder bodies).
 */
enum class LightLeakMode(
    val labelFr: String,
    val shortLabel: String,
    val descriptionFr: String
) {
    AUTO(
        labelFr = "Auto boîtier",
        shortLabel = "AUTO",
        descriptionFr = "Respecte les joints d'origine : fort sur Holga/Jetable, subtil années 70, propre sur Pro"
    ),
    SUBTLE(
        labelFr = "Forcer Subtil",
        shortLabel = "SUBTIL",
        descriptionFr = "Ajoute un liseré lumineux chaud sur tous les boîtiers (même Pro SLR / M6)"
    ),
    STRONG(
        labelFr = "Forcer Fort",
        shortLabel = "FORT",
        descriptionFr = "Brûlures ambre et vermillon franches garanties sur chaque prise de vue"
    ),
    OFF(
        labelFr = "Désactivées",
        shortLabel = "OFF",
        descriptionFr = "Joints neufs étanches : supprime toute fuite de lumière sur tous les appareils"
    );

    fun resolveIntensity(bodyBaseIntensity: Float): Float = when (this) {
        AUTO -> bodyBaseIntensity
        SUBTLE -> max(bodyBaseIntensity, 0.44f)
        STRONG -> max(bodyBaseIntensity, 0.90f)
        OFF -> 0f
    }

    companion object {
        fun fromNameOrDefault(raw: String?): LightLeakMode =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

/**
 * User-saved hardware + film + date preset ("Mes Combos") persisted locally in DataStore.
 */
data class SavedComboPreset(
    val id: String,
    val name: String,
    val bodyId: String,
    val lensId: String,
    val filmId: String,
    val dateStampStyleName: String
) {
    fun serialize(): String {
        val safeName = name.replace("|", " ").trim().ifEmpty { "Combo" }
        return listOf(id, safeName, bodyId, lensId, filmId, dateStampStyleName).joinToString("|")
    }

    companion object {
        fun deserialize(raw: String): SavedComboPreset? {
            val parts = raw.split("|")
            if (parts.size < 6) return null
            return SavedComboPreset(
                id = parts[0],
                name = parts[1],
                bodyId = parts[2],
                lensId = parts[3],
                filmId = parts[4],
                dateStampStyleName = parts[5]
            )
        }
    }
}

/**
 * Chemical film family determining how expiration aging and darkroom processes react.
 */
enum class EmulsionType(val labelFr: String, val processCode: String) {
    COLOR_NEGATIVE("Négatif Couleur", "C-41"),
    COLOR_SLIDE("Diapositive (Inversible)", "E-6"),
    BLACK_AND_WHITE("Noir & Blanc Argentique", "N&B"),
    CINEMA_ECN2("Cinéma 35mm", "ECN-2")
}

/**
 * Physical lens mount system determining which Lenses can be mounted on each Camera Body.
 */
enum class LensMount(val labelFr: String, val shortCode: String) {
    SLR_35MM("Monture Reflex 35mm", "REFLEX"),
    RANGEFINDER_M("Monture Télémétrique M", "M-RF"),
    MEDIUM_FORMAT_6X6("Moyen Format 6×6", "MF 6×6"),
    PANORAMIC_XPAN("Monture Panoramique X", "XPAN"),
    COMPACT_SERIES("Optiques Compactes", "COMPACT"),
    TOY_LOFI("Optiques Toy / Plastique", "TOY"),
    LARGE_FORMAT_BRASS("Chambre Laiton XIXe", "LAITON")
}

/**
 * Stage 1: Lens (Objectif) - 18 hardware models (including 19th-century brass Petzval, Chevalier & Pinhole).
 */
data class LensModel(
    val id: String,
    val name: String,
    val shortCode: String,
    val mountLabel: String,
    val compatibleMounts: Set<LensMount>,
    val focalLengthMm: Int,
    val zoomMaxMm: Int? = null,
    val maxAperture: String,
    val cropZoomFactor: Float,
    val distortionK: Float,
    val vignetteStrength: Float,
    val chromaticAberration: Float,
    val opticalSoftness: Float,
    val halationStrength: Float,
    val flareStrength: Float,
    val tiltShiftAmount: Float,
    val plasticScratchIntensity: Float = 0f,
    val swirlyBokehAmount: Float = 0f,
    val coatingTintHex: Long,
    val barrelColorHex: Long,
    val ringAccentHex: Long,
    val characterTag: String,
    val description: String
) {
    val isZoom: Boolean get() = zoomMaxMm != null
    val focalLabel: String get() = if (zoomMaxMm != null) "${focalLengthMm}-${zoomMaxMm}mm" else "${focalLengthMm}mm"
}

/**
 * Stage 2: Camera Body (Boîtier) - 18 hardware models (1842 to 2013).
 */
data class CameraBodyModel(
    val id: String,
    val name: String,
    val shortCode: String,
    val era: String,
    val mount: LensMount,
    val defaultLensId: String,
    val aspectRatioSpec: AspectRatioSpec,
    val frameBorderStyle: FrameBorderStyle = FrameBorderStyle.FILM_35MM_SPROCKETS,
    val maxExposures: Int,
    val supportsDiptych: Boolean = false,
    val contrast: Float,
    val warmth: Float,
    val saturation: Float,
    val shadowR: Float = 0f,
    val shadowG: Float = 0f,
    val shadowB: Float = 0f,
    val highlightR: Float = 0f,
    val highlightG: Float = 0f,
    val highlightB: Float = 0f,
    val lightLeakIntensity: Float = 0f,
    val globalSoftness: Float = 0f,
    val plasticScratchIntensity: Float = 0f,
    val collodionPlateAmount: Float = 0f,
    val daguerreTarnishAmount: Float = 0f,
    val autochromeMosaicAmount: Float = 0f,
    val topPlateHex: Long,
    val leatherHex: Long,
    val accentHex: Long,
    val signatureTag: String,
    val description: String
) {
    /**
     * Toy, disposable & pinhole cameras use a fixed hyperfocal aperture with NO autofocus.
     */
    val isFixedFocusToyCam: Boolean get() = mount == LensMount.TOY_LOFI

    /**
     * 19th-century wooden bellows / brass view cameras (Collodion, Daguerreotype, Autochrome).
     */
    val isHistoricalLargeFormat: Boolean get() = mount == LensMount.LARGE_FORMAT_BRASS

    /**
     * Instant chemical peel/integral cameras (Polaroid SX-70, Instax Mini) with milky reveal animation.
     */
    val isInstantCamera: Boolean
        get() = frameBorderStyle == FrameBorderStyle.POLAROID_WHITE ||
            frameBorderStyle == FrameBorderStyle.INSTAX_MINI_WHITE

    val focusModeLabel: String
        get() = when (mount) {
            LensMount.TOY_LOFI -> "FIXE ∞"
            LensMount.RANGEFINDER_M, LensMount.PANORAMIC_XPAN -> "TÉLÉMÈTRE"
            LensMount.MEDIUM_FORMAT_6X6 -> "DÉPOLI MF"
            LensMount.LARGE_FORMAT_BRASS -> "DÉPOLI 4×5"
            LensMount.SLR_35MM -> "STIGMO SLR"
            LensMount.COMPACT_SERIES -> "AF 90s"
        }
}

/**
 * Multiple authentic analog date-back imprint styles burned onto developed negatives & previewed live.
 */
enum class DateStampStyle(
    val labelFr: String,
    val shortLabel: String,
    val samplePreview: String,
    val descriptionFr: String,
    val coreColorHex: Long,
    val glowColorHex: Long
) {
    OFF(
        labelFr = "Désactivée",
        shortLabel = "OFF",
        samplePreview = "Aucune date",
        descriptionFr = "Aucune incrustation de date sur les tirages",
        coreColorHex = 0xFF9A9DA8,
        glowColorHex = 0x00000000
    ),
    QUARTZ_90S(
        labelFr = "Quartz LED 90s",
        shortLabel = "LED 90s",
        samplePreview = "'26 11 20",
        descriptionFr = "Dos dateur orange lumineux classique (AA MM JJ)",
        coreColorHex = 0xFFFFA838,
        glowColorHex = 0xCCFF5412
    ),
    COMPACT_80S(
        labelFr = "Compact 80s Doré",
        shortLabel = "80s OR",
        samplePreview = "20 11 '26",
        descriptionFr = "Format européen jour/mois/année ambre doré",
        coreColorHex = 0xFFFFD166,
        glowColorHex = 0xB8F58216
    ),
    RED_DATABACK(
        labelFr = "Dos Dateur Rouge",
        shortLabel = "ROUGE",
        samplePreview = "'26.11.20",
        descriptionFr = "Segments LED rouge rubis façon boîtiers japonais",
        coreColorHex = 0xFFFF4D5A,
        glowColorHex = 0xD0CC1122
    ),
    TIMECODE_CINE(
        labelFr = "Timecode Ciné",
        shortLabel = "CINÉ",
        samplePreview = "2026·11·20 14:32",
        descriptionFr = "Horodatage complet avec heure style dos pro",
        coreColorHex = 0xFFFFBA5A,
        glowColorHex = 0x99E65C00
    ),
    CLASSIC_WHITE(
        labelFr = "Tirage Labo Crème",
        shortLabel = "LABO",
        samplePreview = "20 NOV. 2026",
        descriptionFr = "Typographie fine blanc crème sans halo LED",
        coreColorHex = 0xFFF5F1E6,
        glowColorHex = 0x66000000
    );

    fun formatDate(date: java.util.Date = java.util.Date()): String {
        val locale = java.util.Locale.FRENCH
        return when (this) {
            OFF -> ""
            QUARTZ_90S -> java.text.SimpleDateFormat("''yy MM dd", java.util.Locale.US).format(date)
            COMPACT_80S -> java.text.SimpleDateFormat("dd MM ''yy", java.util.Locale.US).format(date)
            RED_DATABACK -> java.text.SimpleDateFormat("''yy.MM.dd", java.util.Locale.US).format(date)
            TIMECODE_CINE -> java.text.SimpleDateFormat("yyyy·MM·dd  HH:mm", java.util.Locale.US).format(date)
            CLASSIC_WHITE -> java.text.SimpleDateFormat("dd MMM yyyy", locale).format(date).uppercase(locale)
        }
    }

    fun encodeStamp(date: java.util.Date = java.util.Date()): String? {
        if (this == OFF) return null
        return "$name|${formatDate(date)}"
    }

    fun next(): DateStampStyle {
        val list = entries
        return list[(ordinal + 1) % list.size]
    }

    companion object {
        fun fromNameOrDefault(raw: String?): DateStampStyle =
            entries.firstOrNull { it.name == raw } ?: QUARTZ_90S

        fun decodeStamp(raw: String?): Pair<DateStampStyle, String>? {
            if (raw.isNullOrBlank()) return null
            val sep = raw.indexOf('|')
            if (sep > 0) {
                val style = entries.firstOrNull { it.name == raw.substring(0, sep) } ?: QUARTZ_90S
                val text = raw.substring(sep + 1)
                if (style == OFF || text.isBlank()) return null
                return style to text
            }
            // Backwards compatibility with previously captured frames storing plain text
            return QUARTZ_90S to raw
        }
    }
}

enum class FilmMood(val labelFr: String) {
    ALL("Toutes"),
    HISTORICAL("XIXe • Plaques"),
    COLOR("Couleur"),
    PASTEL("Pastel"),
    GOLDEN("Dorée"),
    BW("N&B"),
    NIGHT_TUNGSTEN("Nuit / Tungstène"),
    EXPERIMENTAL("Expérimentale")
}

/**
 * Primary film family filter for quick 1-tap browsing (e.g., N&B only, Color Negative, Slide, Cinema).
 */
enum class FilmTypeFilter(val labelFr: String) {
    ALL("Toutes"),
    BW("N&B"),
    COLOR_NEG("Couleur"),
    SLIDE("Diapo"),
    CINEMA("Ciné");

    fun matches(film: FilmStockModel): Boolean = when (this) {
        ALL -> true
        BW -> film.emulsionType == EmulsionType.BLACK_AND_WHITE || film.isMonochrome || film.mood == FilmMood.BW
        COLOR_NEG -> film.emulsionType == EmulsionType.COLOR_NEGATIVE && !film.isMonochrome
        SLIDE -> film.emulsionType == EmulsionType.COLOR_SLIDE && !film.isMonochrome
        CINEMA -> film.emulsionType == EmulsionType.CINEMA_ECN2 && !film.isMonochrome
    }
}

/**
 * Sort order for browsing the 108-film catalog by ISO sensitivity, name, contrast, or grain.
 */
enum class FilmSortOption(val labelFr: String) {
    CATALOG("Sélection"),
    ISO_ASC("ISO ↑"),
    ISO_DESC("ISO ↓"),
    NAME_AZ("A–Z"),
    CONTRAST_DESC("Contraste ↓"),
    GRAIN_ASC("Grain fin");

    fun sort(films: List<FilmStockModel>): List<FilmStockModel> = when (this) {
        CATALOG -> films
        ISO_ASC -> films.sortedWith(compareBy<FilmStockModel> { it.iso }.thenBy { it.name })
        ISO_DESC -> films.sortedWith(compareByDescending<FilmStockModel> { it.iso }.thenBy { it.name })
        NAME_AZ -> films.sortedBy { it.name.lowercase() }
        CONTRAST_DESC -> films.sortedWith(compareByDescending<FilmStockModel> { it.contrast }.thenBy { it.name })
        GRAIN_ASC -> films.sortedWith(compareBy<FilmStockModel> { it.grainIntensity }.thenBy { it.iso })
    }
}

/**
 * Stage 3: Film Stock (Pellicule) - 108 unique emulsions.
 */
data class FilmStockModel(
    val id: String,
    val name: String,
    val code: String,
    val iso: Int,
    val mood: FilmMood,
    val emulsionType: EmulsionType,
    val canisterColorHex: Long,
    val stripeColorHex: Long,
    val labelTextHex: Long,
    val isMonochrome: Boolean = false,
    val redGain: Float = 1.0f,
    val greenGain: Float = 1.0f,
    val blueGain: Float = 1.0f,
    val contrast: Float = 1.05f,
    val saturation: Float = 1.0f,
    val gamma: Float = 1.0f,
    val toeLift: Float = 0.02f,
    val shoulderCompress: Float = 0.12f,
    val shadowTintR: Float = 0f,
    val shadowTintG: Float = 0f,
    val shadowTintB: Float = 0f,
    val highlightTintR: Float = 0f,
    val highlightTintG: Float = 0f,
    val highlightTintB: Float = 0f,
    val grainSize: Float = 1.0f,
    val grainIntensity: Float = 0.16f,
    val halationBoost: Float = 0.0f,
    val hueShift: Float = 0.0f,
    val sepiaAmount: Float = 0.0f,
    val cyanotypeAmount: Float = 0.0f,
    val infraredAmount: Float = 0.0f,
    val orthoUvAmount: Float = 0.0f,
    val collodionPlateAmount: Float = 0.0f,
    val daguerreTarnishAmount: Float = 0.0f,
    val autochromeMosaicAmount: Float = 0.0f,
    val paperFiberAmount: Float = 0.0f,
    val brushBorderAmount: Float = 0.0f,
    val tagline: String
)

/**
 * Exact randomized or preset Film Expiration Date ("Date de péremption").
 * Computes chemistry-specific aging:
 * - Color Negatives (C-41) -> green base fog & color cast
 * - Color Slides / Positives (E-6) -> magenta/purple cast
 * - Cinema ECN-2 -> green-amber veil & halation bloom
 * - Black & White -> silver base fog, flat contrast, coarser grain
 */
data class FilmExpirationDate(
    val expMonth: Int,
    val expYear: Int,
    val referenceYear: Int = CURRENT_YEAR,
    val referenceMonth: Int = CURRENT_MONTH
) {
    val monthsExpired: Int
        get() = max(0, (referenceYear - expYear) * 12 + (referenceMonth - expMonth))

    val yearsExpired: Float
        get() = monthsExpired / 12f

    val isExpired: Boolean
        get() = monthsExpired > 0

    val stampCode: String
        get() = "EXP %02d/%04d".format(expMonth, expYear)

    val shortBadge: String
        get() = if (!isExpired) {
            "FRAÎCHE • %02d/%02d".format(expMonth, expYear % 100)
        } else {
            val yrs = max(1, yearsExpired.roundToInt())
            "PÉRIMÉE ${yrs}A • %02d/%02d".format(expMonth, expYear % 100)
        }

    val severityLabel: String
        get() = when {
            !isExpired -> "Fraîche (conservée au frais)"
            yearsExpired < 3f -> "Légèrement périmée (${max(1, yearsExpired.roundToInt())} an${if (yearsExpired >= 1.5f) "s" else ""})"
            yearsExpired < 7f -> "Périmée (${yearsExpired.roundToInt()} ans)"
            else -> "Très périmée (${yearsExpired.roundToInt()} ans • archive)"
        }

    fun computeChemicalEffect(emulsionType: EmulsionType): ExpirationOpticalEffect {
        if (!isExpired) {
            return ExpirationOpticalEffect(
                fogLift = 0f,
                castR = 0f,
                castG = 0f,
                castB = 0f,
                contrastFactor = 1f,
                saturationFactor = 1f,
                grainMultiplier = 1f,
                exposureShiftEv = 0f
            )
        }
        // Normalize age factor cleanly across 1..15 years expired
        val ageNorm = (yearsExpired / 12f).coerceIn(0.08f, 1.25f)

        val (castR, castG, castB) = when (emulsionType) {
            // Color Negatives shift noticeably GREEN in shadows & midtones as dyes age
            EmulsionType.COLOR_NEGATIVE -> Triple(
                -0.015f * ageNorm,
                0.085f * ageNorm,
                -0.010f * ageNorm
            )
            // Color Positives / Slides shift strongly MAGENTA (R + B up, G down)
            EmulsionType.COLOR_SLIDE -> Triple(
                0.085f * ageNorm,
                -0.045f * ageNorm,
                0.075f * ageNorm
            )
            // Cinema ECN-2 shifts green-amber
            EmulsionType.CINEMA_ECN2 -> Triple(
                0.035f * ageNorm,
                0.060f * ageNorm,
                -0.030f * ageNorm
            )
            // B&W develops silver base fog (neutral milky lift)
            EmulsionType.BLACK_AND_WHITE -> Triple(
                0.015f * ageNorm,
                0.015f * ageNorm,
                0.010f * ageNorm
            )
        }

        return ExpirationOpticalEffect(
            fogLift = (0.018f + 0.085f * ageNorm).coerceAtMost(0.13f),
            castR = castR,
            castG = castG,
            castB = castB,
            contrastFactor = (1f - 0.24f * ageNorm).coerceAtLeast(0.68f),
            saturationFactor = (1f - 0.28f * ageNorm).coerceAtLeast(0.62f),
            grainMultiplier = 1f + 0.85f * ageNorm,
            exposureShiftEv = -(0.12f + 0.52f * ageNorm).coerceAtMost(0.85f)
        )
    }

    fun warningDescriptionFr(emulsionType: EmulsionType): String {
        if (!isExpired) {
            return "Pellicule fraîche ($stampCode) — sensibilité nominale, couleurs fidèles et voile nul."
        }
        val yrs = max(1, yearsExpired.roundToInt())
        val grainPct = ((computeChemicalEffect(emulsionType).grainMultiplier - 1f) * 100).roundToInt()
        val chemistryNote = when (emulsionType) {
            EmulsionType.COLOR_NEGATIVE -> "dominante verte caractéristique des négatifs couleur périmés"
            EmulsionType.COLOR_SLIDE -> "dérive magenta/pourpre typique des diapositives positives anciennes"
            EmulsionType.CINEMA_ECN2 -> "voile vert-ambre et halos prononcés sur l'émulsion cinéma"
            EmulsionType.BLACK_AND_WHITE -> "voile argentique gris dans les ombres et baisse de contraste"
        }
        return "Périmée depuis $yrs an${if (yrs > 1) "s" else ""} ($stampCode) : attendez-vous à une $chemistryNote et un grain accru (+$grainPct %)."
    }

    fun serialize(): String = "%02d/%04d".format(expMonth, expYear)

    companion object {
        const val CURRENT_YEAR = 2026
        const val CURRENT_MONTH = 11

        fun freshDefault(): FilmExpirationDate = FilmExpirationDate(expMonth = 8, expYear = 2028)

        /**
         * Generates a realistic randomized expiration date for every new roll
         * (~35% fresh stock, ~65% vintage expired shelf finds from 1 to 14 years old).
         */
        fun randomForNewRoll(seed: Long = System.nanoTime()): FilmExpirationDate {
            val rng = Random(seed)
            val roll = rng.nextFloat()
            val month = rng.nextInt(1, 13)
            val year = when {
                roll < 0.35f -> rng.nextInt(2027, 2030)      // Fresh stock
                roll < 0.62f -> rng.nextInt(2023, 2026)      // 1-3 years expired
                roll < 0.85f -> rng.nextInt(2019, 2023)      // 4-7 years expired
                else -> rng.nextInt(2012, 2019)              // 8-14 years vintage archive
            }
            return FilmExpirationDate(expMonth = month, expYear = year)
        }

        fun parse(raw: String): FilmExpirationDate {
            val parts = raw.split("/")
            if (parts.size == 2) {
                val m = parts[0].toIntOrNull()?.coerceIn(1, 12) ?: 8
                val y = parts[1].toIntOrNull()?.coerceIn(1995, 2035) ?: 2028
                return FilmExpirationDate(m, y)
            }
            return freshDefault()
        }

        val presets: List<Pair<String, FilmExpirationDate>> = listOf(
            "Fraîche (2028)" to FilmExpirationDate(8, 2028),
            "Périmée 2 ans" to FilmExpirationDate(5, 2024),
            "Périmée 5 ans" to FilmExpirationDate(10, 2021),
            "Périmée 10+ ans" to FilmExpirationDate(3, 2015)
        )
    }
}

data class ExpirationOpticalEffect(
    val fogLift: Float,
    val castR: Float,
    val castG: Float,
    val castB: Float,
    val contrastFactor: Float,
    val saturationFactor: Float,
    val grainMultiplier: Float,
    val exposureShiftEv: Float
)

/**
 * Advanced Darkroom Chemical Development Process ("Méthode de développement au Labo").
 * Supports push/pull effective ISO shifts, cross-processing, bleach bypass, sepia & cyanotype toning,
 * and high-acutance Rodinal stand development.
 */
enum class DevelopmentMethod(
    val titleFr: String,
    val shortBadge: String,
    val tempAndTime: String,
    val descriptionFr: String,
    val isoFactor: Float,
    val recommendedFor: Set<EmulsionType>,
    val exposureDelta: Float,
    val contrastMul: Float,
    val saturationMul: Float,
    val grainMul: Float,
    val crossProcessStrength: Float,
    val bleachBypassStrength: Float,
    val acutanceBoost: Float,
    val sepiaBathStrength: Float,
    val cyanotypeBathStrength: Float,
    val collodionBathStrength: Float = 0f,
    val daguerreBathStrength: Float = 0f,
    val platinumBathStrength: Float = 0f
) {
    STANDARD_C41(
        titleFr = "Développement Standard",
        shortBadge = "STD",
        tempAndTime = "38,0 °C • 3 min 15 s",
        descriptionFr = "Bain nominal C-41 / E-6 / D-76 respectant la courbe sensitométrique et l'ISO d'origine.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.COLOR_NEGATIVE, EmulsionType.COLOR_SLIDE, EmulsionType.BLACK_AND_WHITE, EmulsionType.CINEMA_ECN2),
        exposureDelta = 0.0f,
        contrastMul = 1.0f,
        saturationMul = 1.0f,
        grainMul = 1.0f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.0f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    PUSH_1(
        titleFr = "Poussé +1 IL (Push)",
        shortBadge = "PUSH +1",
        tempAndTime = "38,0 °C • 4 min 00 s",
        descriptionFr = "Double l'ISO effectif : ombres plus denses, contraste vif et grain argentique renforcé.",
        isoFactor = 2.0f,
        recommendedFor = setOf(EmulsionType.COLOR_NEGATIVE, EmulsionType.BLACK_AND_WHITE, EmulsionType.CINEMA_ECN2),
        exposureDelta = 0.36f,
        contrastMul = 1.20f,
        saturationMul = 1.08f,
        grainMul = 1.32f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.20f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    PUSH_2(
        titleFr = "Poussé +2 IL (Push)",
        shortBadge = "PUSH +2",
        tempAndTime = "38,0 °C • 5 min 15 s",
        descriptionFr = "Quadruple l'ISO effectif : contraste dramatique, hautes lumières nerveuses et grain prononcé.",
        isoFactor = 4.0f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.CINEMA_ECN2, EmulsionType.COLOR_NEGATIVE),
        exposureDelta = 0.68f,
        contrastMul = 1.38f,
        saturationMul = 1.15f,
        grainMul = 1.65f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.35f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    PULL_1(
        titleFr = "Retenu -1 IL (Pull)",
        shortBadge = "PULL -1",
        tempAndTime = "37,0 °C • 2 min 30 s",
        descriptionFr = "Divise l'ISO par deux : rendu pastel velouté, grain adouci et hautes lumières préservées.",
        isoFactor = 0.5f,
        recommendedFor = setOf(EmulsionType.COLOR_NEGATIVE, EmulsionType.COLOR_SLIDE, EmulsionType.BLACK_AND_WHITE),
        exposureDelta = -0.22f,
        contrastMul = 0.82f,
        saturationMul = 0.86f,
        grainMul = 0.76f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.0f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    CROSS_PROCESS(
        titleFr = "Traitement Croisé (X-Pro)",
        shortBadge = "X-PRO",
        tempAndTime = "38,5 °C • E-6 ↔ C-41",
        descriptionFr = "Inversion de chimie : dérives imprévisibles jaune-vert dans les clairs, ombres cyan/magenta et fort contraste.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.COLOR_SLIDE, EmulsionType.COLOR_NEGATIVE, EmulsionType.CINEMA_ECN2),
        exposureDelta = 0.16f,
        contrastMul = 1.28f,
        saturationMul = 1.36f,
        grainMul = 1.22f,
        crossProcessStrength = 1.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.15f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    BLEACH_BYPASS(
        titleFr = "Bleach Bypass (Ciné)",
        shortBadge = "BLEACH",
        tempAndTime = "38,0 °C • Rétention Ag",
        descriptionFr = "Blanchiment sauté : rétention d'argent donnant une image métallique désaturée aux noirs profonds.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.CINEMA_ECN2, EmulsionType.COLOR_NEGATIVE, EmulsionType.COLOR_SLIDE),
        exposureDelta = -0.05f,
        contrastMul = 1.42f,
        saturationMul = 0.42f,
        grainMul = 1.35f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 1.0f,
        acutanceBoost = 0.28f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    SEPIA_TONING(
        titleFr = "Virage Sépia d'Archive",
        shortBadge = "SÉPIA",
        tempAndTime = "22,0 °C • Sulfure chaud",
        descriptionFr = "Bain de virage au sulfure : teintes brun-ambre intemporelles et douceur d'archive sur tout type de film.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.COLOR_NEGATIVE, EmulsionType.COLOR_SLIDE),
        exposureDelta = 0.02f,
        contrastMul = 1.08f,
        saturationMul = 0.55f,
        grainMul = 1.12f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.12f,
        sepiaBathStrength = 0.85f,
        cyanotypeBathStrength = 0.0f
    ),
    CYANOTYPE_TONING(
        titleFr = "Virage Cyanotype (Prusse)",
        shortBadge = "CYANO",
        tempAndTime = "20,0 °C • Ferricyanure",
        descriptionFr = "Procédé ferrique historique : ombres bleu de Prusse profondes et lumières crème argentique.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.COLOR_NEGATIVE),
        exposureDelta = 0.0f,
        contrastMul = 1.15f,
        saturationMul = 0.50f,
        grainMul = 1.15f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.14f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.88f
    ),
    RODINAL_STAND(
        titleFr = "Rodinal Stand 1+100",
        shortBadge = "RODINAL",
        tempAndTime = "20,0 °C • 60 min statique",
        descriptionFr = "Développement lent haute acutance : micro-contraste ciselé et grain sculptural très apprécié en N&B.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.CINEMA_ECN2),
        exposureDelta = 0.08f,
        contrastMul = 1.18f,
        saturationMul = 0.90f,
        grainMul = 1.42f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.18f,
        acutanceBoost = 0.70f,
        sepiaBathStrength = 0.0f,
        cyanotypeBathStrength = 0.0f
    ),
    SILVER_NITRATE_COLLODION(
        titleFr = "Bain Nitrate d'Argent (Collodion)",
        shortBadge = "COLLODION",
        tempAndTime = "18,0 °C • 15 s cuvette",
        descriptionFr = "Révélation XIXe sur plaque humide : réponse orthochromatique UV, coulures d'émulsion et micro-bulles argentiques.",
        isoFactor = 0.5f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.COLOR_NEGATIVE),
        exposureDelta = 0.04f,
        contrastMul = 1.24f,
        saturationMul = 0.12f,
        grainMul = 1.22f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.25f,
        acutanceBoost = 0.45f,
        sepiaBathStrength = 0.18f,
        cyanotypeBathStrength = 0.0f,
        collodionBathStrength = 0.88f
    ),
    MERCURY_DAGUERRE(
        titleFr = "Vapeur de Mercure 1839",
        shortBadge = "MERCURE",
        tempAndTime = "60,0 °C • Boîte à vapeur",
        descriptionFr = "Procédé Daguerréotype sur cuivre argenté : piqué ciselé miroir et couronne d'oxydation irisée bleu-bronze.",
        isoFactor = 0.5f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.COLOR_SLIDE),
        exposureDelta = 0.06f,
        contrastMul = 1.32f,
        saturationMul = 0.15f,
        grainMul = 0.55f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.40f,
        acutanceBoost = 0.60f,
        sepiaBathStrength = 0.12f,
        cyanotypeBathStrength = 0.0f,
        daguerreBathStrength = 0.92f
    ),
    GOLD_PLATINUM_TONING(
        titleFr = "Virage Or-Platine 1880",
        shortBadge = "PLATINE",
        tempAndTime = "21,0 °C • Sels nobles",
        descriptionFr = "Tirage de musée sur papier coton : dégradés infinis champagne-graphite, noirs veloutés sans grain agressif.",
        isoFactor = 1.0f,
        recommendedFor = setOf(EmulsionType.BLACK_AND_WHITE, EmulsionType.COLOR_NEGATIVE, EmulsionType.COLOR_SLIDE),
        exposureDelta = 0.02f,
        contrastMul = 0.96f,
        saturationMul = 0.20f,
        grainMul = 0.72f,
        crossProcessStrength = 0.0f,
        bleachBypassStrength = 0.0f,
        acutanceBoost = 0.18f,
        sepiaBathStrength = 0.28f,
        cyanotypeBathStrength = 0.0f,
        platinumBathStrength = 0.90f
    );

    fun effectiveIso(baseIso: Int): Int = (baseIso * isoFactor).roundToInt().coerceIn(1, 12800)

    fun compatibilityLabelFr(emulsionType: EmulsionType): String =
        if (emulsionType in recommendedFor) "Idéal pour ${emulsionType.processCode}"
        else "Expérimental sur ${emulsionType.processCode}"
}

/**
 * Unified immutable snapshot of all parameters fed into both the real-time OpenGL ES 3.0
 * viewfinder shader AND the full-resolution offscreen EGL lab development renderer.
 */
data class OpticalChainParams(
    val lens: LensModel,
    val body: CameraBodyModel,
    val film: FilmStockModel,
    val expirationDate: FilmExpirationDate = FilmExpirationDate.freshDefault(),
    val development: DevelopmentMethod = DevelopmentMethod.STANDARD_C41,
    val evCompensation: Float = 0f,
    val zoomMultiplier: Float = 1.0f,
    val lightLeakSeed: Float = 0.37f,
    val isFrontCamera: Boolean = false,
    val lightLeakMode: LightLeakMode = LightLeakMode.AUTO,
    val tiltShiftPosition: Float = 0.5f,
    val tiltShiftVertical: Boolean = false,
    val invertedGroundGlass: Boolean = false,
    val videoFilterMode: Int = 0
) {
    val expirationEffect: ExpirationOpticalEffect
        get() = expirationDate.computeChemicalEffect(film.emulsionType)

    val effectiveLightLeakIntensity: Float
        get() = lightLeakMode.resolveIntensity(body.lightLeakIntensity)
}
