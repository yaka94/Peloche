package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.VideoFilterStyle
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.BrushedGunmetal
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactile machined segmented mode selector (PHOTO | CINÉ) placed cleanly above the shutter deck.
 * Gives Photo vs Cinema primary top-level hierarchy with generous >= 48dp touch width & crisp feedback.
 */
@Composable
fun PhotoCineModeSwitch(
    isCineMode: Boolean,
    enabled: Boolean,
    onSelectMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .recessedInstrumentWell(
                shape = RoundedCornerShape(16.dp),
                borderColor = if (isCineMode) LeicaCrimson.copy(alpha = 0.55f) else HairlineMetal
            )
            .padding(3.dp)
            .testTag("photo_cine_mode_switch"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ModeSegmentButton(
            label = "PHOTO",
            selected = !isCineMode,
            activeColor = BrassAccent,
            enabled = enabled,
            testTag = "mode_switch_photo",
            onClick = { if (isCineMode) onSelectMode(false) }
        )
        ModeSegmentButton(
            label = "CINÉ",
            selected = isCineMode,
            activeColor = LeicaCrimson,
            enabled = enabled,
            testTag = "mode_switch_cine",
            onClick = { if (!isCineMode) onSelectMode(true) }
        )
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    selected: Boolean,
    activeColor: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF282A33) else Color.Transparent,
        animationSpec = tween(180),
        label = "mode_seg_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) activeColor else WarmEngraving,
        animationSpec = tween(180),
        label = "mode_seg_txt"
    )
    Row(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bgColor)
            .then(
                if (selected) {
                    Modifier.border(1.dp, activeColor.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = textColor,
            maxLines = 1
        )
    }
}

/**
 * Unified Cinema Console that seamlessly replaces the 3-slot Photo Hardware Rack when CINÉ mode
 * is active — preventing vertical stacking and keeping the live viewfinder large and uncluttered.
 * Uses a restrained machined-metal + brass + film-cream + Leica-crimson palette with authentic
 * miniature cartridge/cassette/reel illustrations and comfortable >= 56dp thumb targets.
 */
@Composable
fun CineConsoleDeck(
    selected: VideoFilterStyle,
    lensShortCode: String,
    aspectBadge: String,
    enabled: Boolean,
    compact: Boolean = false,
    onSelect: (VideoFilterStyle) -> Unit,
    onOpenOpticalGear: () -> Unit,
    onOpenAllFormatsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cine_console_deck"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        // Concise instrument header bar: Active Cinema Format on left, Optics & All Formats on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LeicaCrimson)
                )
                Text(
                    text = selected.labelFr.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = FilmCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "• ${selected.fpsLabel}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = BrassAccent,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Optical Gear pill (lens + aspect ratio) without showing the full 3-card photo rack
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(AnodizedSlate)
                        .border(0.8.dp, HairlineMetal, RoundedCornerShape(7.dp))
                        .clickable(enabled = enabled) { onOpenOpticalGear() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("cine_optics_shortcut"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Changer boîtier ou objectif",
                        tint = SilverChrome,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "$lensShortCode • $aspectBadge",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = SilverChrome,
                        maxLines = 1
                    )
                }

                // Open 2-column full cinema catalog sheet
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF23252E))
                        .border(0.9.dp, BrassAccent.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                        .clickable(enabled = enabled) { onOpenAllFormatsSheet() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("cine_all_formats_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = "Voir les 9 formats vidéo",
                        tint = BrassAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "TOUS (${VideoFilterStyle.entries.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        ),
                        color = BrassAccent,
                        maxLines = 1
                    )
                }
            }
        }

        // Tactile Cartridge / Cassette / Cinema Reel Carousel
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_filter_selector_strip"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(VideoFilterStyle.entries, key = { it.name }) { style ->
                val isSelected = style == selected
                CineCartridgeCard(
                    style = style,
                    isSelected = isSelected,
                    compact = compact,
                    enabled = enabled,
                    onClick = { onSelect(style) }
                )
            }
        }
    }
}

@Composable
private fun CineCartridgeCard(
    style: VideoFilterStyle,
    isSelected: Boolean,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderCol = when {
        isSelected -> LeicaCrimson
        else -> HairlineMetal
    }
    Column(
        modifier = Modifier
            .width(if (compact) 86.dp else 94.dp)
            .brushedMetalPlate(
                shape = RoundedCornerShape(12.dp),
                borderColor = borderCol,
                borderWidth = if (isSelected) 1.5.dp else 0.85.dp,
                elevated = isSelected
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = if (compact) 5.dp else 7.dp)
            .testTag("video_filter_chip_${style.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
    ) {
        CineFormatThumbnail(
            style = style,
            isSelected = isSelected,
            modifier = Modifier.size(width = if (compact) 40.dp else 46.dp, height = if (compact) 26.dp else 30.dp)
        )
        Text(
            text = style.shortBadge,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                letterSpacing = 0.5.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
            ),
            color = if (isSelected) FilmCream else SilverChrome,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = style.fpsLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = if (isSelected) BrassAccent else WarmEngraving,
            maxLines = 1
        )
    }
}

/**
 * Custom mechanical Canvas illustrations depicting each analog cinema / video format's physical
 * medium: Super 8 cartridge, 1920s Pathé spoked reel, VHS videocassette, Hi8 mini-tape,
 * Technicolor 3-strip reel, 16mm daylight spool, 35mm cinema can, LomoKino strip, or Optical aperture.
 */
@Composable
fun CineFormatThumbnail(
    style: VideoFilterStyle,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val metalRim = if (isSelected) BrassAccent else Color(0xFF7D8291)
    val darkShell = Color(0xFF17181E)
    val highlight = if (isSelected) FilmCream else Color(0xFFAEB3C2)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        when (style) {
            VideoFilterStyle.SUPER_8_KODACHROME -> {
                // Classic Kodak Super 8 black cartridge + Kodak amber stripe + center core
                drawRoundRect(
                    color = darkShell,
                    topLeft = Offset(w * 0.08f, h * 0.08f),
                    size = Size(w * 0.84f, h * 0.84f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = metalRim,
                    topLeft = Offset(w * 0.08f, h * 0.08f),
                    size = Size(w * 0.84f, h * 0.84f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 1.6f)
                )
                // Signature warm Kodak stripe along top edge
                drawRect(
                    color = if (isSelected) KodakAmber else KodakAmber.copy(alpha = 0.65f),
                    topLeft = Offset(w * 0.14f, h * 0.18f),
                    size = Size(w * 0.72f, h * 0.18f)
                )
                // Center Super 8 drive core + gate notch on right
                drawCircle(color = highlight, radius = h * 0.19f, center = Offset(cx, h * 0.62f), style = Stroke(1.6f))
                drawCircle(color = Color(0xFF0A0B0E), radius = h * 0.09f, center = Offset(cx, h * 0.62f))
                drawRect(color = Color(0xFF0A0B0E), topLeft = Offset(w * 0.84f, h * 0.42f), size = Size(w * 0.10f, h * 0.24f))
            }

            VideoFilterStyle.SILENT_1920 -> {
                // Antique Pathé 1920 brass 5-spoke cinema reel
                val r = minOf(w, h) * 0.44f
                drawCircle(color = darkShell, radius = r, center = Offset(cx, cy))
                drawCircle(color = BrassAccent.copy(alpha = if (isSelected) 1f else 0.72f), radius = r, center = Offset(cx, cy), style = Stroke(2f))
                for (i in 0 until 5) {
                    val a = (i * 72.0 - 90.0) * PI / 180.0
                    val hx = cx + cos(a).toFloat() * (r * 0.56f)
                    val hy = cy + sin(a).toFloat() * (r * 0.56f)
                    drawCircle(color = Color(0xFF0B0C10), radius = r * 0.18f, center = Offset(hx, hy))
                    drawCircle(color = BrassAccent.copy(alpha = 0.55f), radius = r * 0.18f, center = Offset(hx, hy), style = Stroke(1f))
                }
                drawCircle(color = highlight, radius = r * 0.14f, center = Offset(cx, cy))
            }

            VideoFilterStyle.VHS_1989 -> {
                // Classic rectangular VHS videocassette with twin tape spools + center label
                drawRoundRect(
                    color = darkShell,
                    topLeft = Offset(w * 0.05f, h * 0.12f),
                    size = Size(w * 0.90f, h * 0.76f),
                    cornerRadius = CornerRadius(3.5f, 3.5f)
                )
                drawRoundRect(
                    color = metalRim,
                    topLeft = Offset(w * 0.05f, h * 0.12f),
                    size = Size(w * 0.90f, h * 0.76f),
                    cornerRadius = CornerRadius(3.5f, 3.5f),
                    style = Stroke(1.5f)
                )
                // Top hinged dust door line
                drawLine(metalRim.copy(alpha = 0.55f), Offset(w * 0.06f, h * 0.30f), Offset(w * 0.94f, h * 0.30f), strokeWidth = 1.1f)
                // Left & right white tape reels
                drawCircle(color = highlight, radius = h * 0.18f, center = Offset(w * 0.26f, h * 0.58f), style = Stroke(1.7f))
                drawCircle(color = highlight, radius = h * 0.18f, center = Offset(w * 0.74f, h * 0.58f), style = Stroke(1.7f))
                // Center VHS spine sticker
                drawRoundRect(
                    color = if (isSelected) FilmCream.copy(alpha = 0.9f) else Color(0xFF6B7080),
                    topLeft = Offset(w * 0.39f, h * 0.42f),
                    size = Size(w * 0.22f, h * 0.32f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }

            VideoFilterStyle.HANDYCAM_HI8_1996 -> {
                // Compact Sony Hi8 mini-cassette with metallic top guard & angled window
                drawRoundRect(
                    color = Color(0xFF1F222B),
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = metalRim,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(1.5f)
                )
                // Brushed silver top shutter plate
                drawRect(
                    color = SilverChrome.copy(alpha = if (isSelected) 0.82f else 0.45f),
                    topLeft = Offset(w * 0.15f, h * 0.16f),
                    size = Size(w * 0.70f, h * 0.18f)
                )
                // Window & dual mini hubs
                drawRoundRect(
                    color = Color(0xFF0E1015),
                    topLeft = Offset(w * 0.22f, h * 0.44f),
                    size = Size(w * 0.56f, h * 0.32f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawCircle(color = KodakAmber, radius = h * 0.10f, center = Offset(w * 0.36f, h * 0.60f))
                drawCircle(color = KodakAmber, radius = h * 0.10f, center = Offset(w * 0.64f, h * 0.60f))
            }

            VideoFilterStyle.TECHNICOLOR_1955 -> {
                // Hollywood 3-Strip Technicolor dye-transfer cinema reel with subtle RGB strip bars
                val r = minOf(w, h) * 0.42f
                drawCircle(color = darkShell, radius = r, center = Offset(w * 0.36f, cy))
                drawCircle(color = metalRim, radius = r, center = Offset(w * 0.36f, cy), style = Stroke(1.8f))
                drawCircle(color = highlight, radius = r * 0.26f, center = Offset(w * 0.36f, cy), style = Stroke(1.4f))
                // 3-Strip dye transfer bars (Crimson, Emerald, Cobalt)
                val barX = w * 0.66f
                val barW = w * 0.24f
                val barH = h * 0.18f
                drawRoundRect(color = Color(0xFFD93B43), topLeft = Offset(barX, h * 0.18f), size = Size(barW, barH), cornerRadius = CornerRadius(2f, 2f))
                drawRoundRect(color = Color(0xFF3DA36E), topLeft = Offset(barX, h * 0.41f), size = Size(barW, barH), cornerRadius = CornerRadius(2f, 2f))
                drawRoundRect(color = Color(0xFF3B7DD8), topLeft = Offset(barX, h * 0.64f), size = Size(barW, barH), cornerRadius = CornerRadius(2f, 2f))
            }

            VideoFilterStyle.CINE_16MM_BW -> {
                // 16mm silver-aluminum daylight spool + single-edge perforated celluloid strip
                drawRoundRect(
                    color = Color(0xFF141519),
                    topLeft = Offset(w * 0.10f, h * 0.14f),
                    size = Size(w * 0.80f, h * 0.72f),
                    cornerRadius = CornerRadius(2.5f, 2.5f)
                )
                drawRoundRect(
                    color = highlight,
                    topLeft = Offset(w * 0.10f, h * 0.14f),
                    size = Size(w * 0.80f, h * 0.72f),
                    cornerRadius = CornerRadius(2.5f, 2.5f),
                    style = Stroke(1.5f)
                )
                // Single-row 16mm sprockets along top
                for (i in 0 until 4) {
                    drawRect(
                        color = highlight,
                        topLeft = Offset(w * (0.17f + i * 0.19f), h * 0.22f),
                        size = Size(w * 0.09f, h * 0.12f)
                    )
                }
                // High-contrast B&W frame split inside
                drawRect(color = Color(0xFFE6E4DC), topLeft = Offset(w * 0.18f, h * 0.42f), size = Size(w * 0.30f, h * 0.34f))
                drawRect(color = Color(0xFF3A3D45), topLeft = Offset(w * 0.52f, h * 0.42f), size = Size(w * 0.30f, h * 0.34f))
            }

            VideoFilterStyle.NIGHT_35MM_NEON -> {
                // 35mm cinema motion-picture film can with subtle ECN-2 anamorphic streak
                val r = minOf(w, h) * 0.43f
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF283242), darkShell)),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(color = metalRim, radius = r, center = Offset(cx, cy), style = Stroke(1.8f))
                drawCircle(color = Color(0xFF56B4E9).copy(alpha = 0.75f), radius = r * 0.62f, center = Offset(cx, cy), style = Stroke(1.2f))
                // Horizontal anamorphic flare bar across lens/can center
                drawLine(
                    color = Color(0xFF56B4E9),
                    start = Offset(w * 0.10f, cy),
                    end = Offset(w * 0.90f, cy),
                    strokeWidth = 2.0f
                )
                drawCircle(color = LeicaCrimson, radius = r * 0.18f, center = Offset(cx, cy))
            }

            VideoFilterStyle.LOMOKINO_35MM -> {
                // Hand-cranked LomoKino panoramic 35mm strip + mechanical crank icon
                drawRoundRect(
                    color = darkShell,
                    topLeft = Offset(w * 0.06f, h * 0.20f),
                    size = Size(w * 0.68f, h * 0.60f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = metalRim,
                    topLeft = Offset(w * 0.06f, h * 0.20f),
                    size = Size(w * 0.68f, h * 0.60f),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(1.5f)
                )
                // Wide panoramic frame window inside
                drawRect(
                    color = KodakAmber.copy(alpha = if (isSelected) 0.85f else 0.45f),
                    topLeft = Offset(w * 0.14f, h * 0.35f),
                    size = Size(w * 0.52f, h * 0.30f)
                )
                // External hand-crank arm on right
                drawLine(highlight, Offset(w * 0.74f, cy), Offset(w * 0.88f, cy), strokeWidth = 2.0f)
                drawLine(highlight, Offset(w * 0.88f, cy), Offset(w * 0.88f, h * 0.24f), strokeWidth = 2.0f)
                drawCircle(color = LeicaCrimson, radius = 3.2f, center = Offset(w * 0.88f, h * 0.22f))
            }

            VideoFilterStyle.OPTICAL_CHAIN -> {
                // Precision brass iris / optical lens aperture emblem (inherits mounted camera + lens + film)
                val r = minOf(w, h) * 0.43f
                drawCircle(color = darkShell, radius = r, center = Offset(cx, cy))
                drawCircle(color = BrassAccent, radius = r, center = Offset(cx, cy), style = Stroke(1.9f))
                drawCircle(color = BrassAccent.copy(alpha = 0.6f), radius = r * 0.55f, center = Offset(cx, cy), style = Stroke(1.2f))
                for (i in 0 until 6) {
                    val a = (i * 60.0) * PI / 180.0
                    val sx = cx + cos(a).toFloat() * (r * 0.35f)
                    val sy = cy + sin(a).toFloat() * (r * 0.35f)
                    val ex = cx + cos(a + 0.55).toFloat() * (r * 0.90f)
                    val ey = cy + sin(a + 0.55).toFloat() * (r * 0.90f)
                    drawLine(BrassAccent.copy(alpha = 0.85f), Offset(sx, sy), Offset(ex, ey), strokeWidth = 1.3f)
                }
            }
        }
    }
}

/**
 * Full 2-column Cinema & Vintage Video Formats sheet ("TOUS (9)") with grouped families,
 * physical medium illustrations, FPS cadence badges, and complete readable descriptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineFormatsBottomSheet(
    selected: VideoFilterStyle,
    activeBodyName: String,
    activeFilmName: String,
    onSelect: (VideoFilterStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MatteLeather,
        contentColor = FilmCream,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HairlineMetal)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("cine_formats_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Formats Ciné & Vidéo",
                        style = MaterialTheme.typography.titleMedium,
                        color = FilmCream,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Chaque format règle sa colorimétrie, son grain et sa vraie cadence d'images.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmEngraving
                    )
                }
            }

            // Group 1: Pellicules Cinéma (Celluloid)
            CineFormatGroupSection(
                title = "PELLICULES CINÉMA",
                styles = listOf(
                    VideoFilterStyle.SUPER_8_KODACHROME,
                    VideoFilterStyle.SILENT_1920,
                    VideoFilterStyle.TECHNICOLOR_1955,
                    VideoFilterStyle.CINE_16MM_BW,
                    VideoFilterStyle.NIGHT_35MM_NEON,
                    VideoFilterStyle.LOMOKINO_35MM
                ),
                selected = selected,
                onSelect = {
                    onSelect(it)
                    onDismiss()
                }
            )

            // Group 2: Bandes Magnétiques (Caméscopes)
            CineFormatGroupSection(
                title = "BANDES MAGNÉTIQUES",
                styles = listOf(
                    VideoFilterStyle.VHS_1989,
                    VideoFilterStyle.HANDYCAM_HI8_1996
                ),
                selected = selected,
                onSelect = {
                    onSelect(it)
                    onDismiss()
                }
            )

            // Group 3: Chaîne Optique Active (uses mounted camera + lens + film!)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CHAÎNE OPTIQUE MONTÉE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = BrassAccent,
                    fontWeight = FontWeight.Bold
                )
                val optStyle = VideoFilterStyle.OPTICAL_CHAIN
                val isOptSelected = selected == optStyle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brushedMetalPlate(
                            shape = RoundedCornerShape(14.dp),
                            borderColor = if (isOptSelected) LeicaCrimson else BrassAccent.copy(alpha = 0.55f),
                            borderWidth = if (isOptSelected) 1.6.dp else 1.dp,
                            elevated = isOptSelected
                        )
                        .clickable {
                            onSelect(optStyle)
                            onDismiss()
                        }
                        .padding(12.dp)
                        .testTag("cine_sheet_item_${optStyle.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CineFormatThumbnail(
                        style = optStyle,
                        isSelected = isOptSelected,
                        modifier = Modifier.size(width = 50.dp, height = 34.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = optStyle.labelFr,
                                style = MaterialTheme.typography.titleSmall,
                                color = FilmCream,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = optStyle.fpsLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = BrassAccent
                            )
                        }
                        Text(
                            text = "Filme à travers votre configuration actuelle : $activeBodyName + $activeFilmName (Collodion, Autochrome, Petzval, etc.).",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = SilverChrome
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CineFormatGroupSection(
    title: String,
    styles: List<VideoFilterStyle>,
    selected: VideoFilterStyle,
    onSelect: (VideoFilterStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = BrassAccent,
            fontWeight = FontWeight.Bold
        )
        styles.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPair.forEach { style ->
                    val isSelected = style == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .brushedMetalPlate(
                                shape = RoundedCornerShape(14.dp),
                                borderColor = if (isSelected) LeicaCrimson else HairlineMetal,
                                borderWidth = if (isSelected) 1.6.dp else 0.9.dp,
                                elevated = isSelected
                            )
                            .clickable { onSelect(style) }
                            .padding(10.dp)
                            .testTag("cine_sheet_item_${style.name}"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CineFormatThumbnail(
                                style = style,
                                isSelected = isSelected,
                                modifier = Modifier.size(width = 44.dp, height = 28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) LeicaCrimson else MatteLeather)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = style.fpsLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isSelected) Color.White else BrassAccent
                                )
                            }
                        }
                        Text(
                            text = style.labelFr,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = FilmCream,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = style.descriptionFr,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                            color = WarmEngraving,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowPair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
