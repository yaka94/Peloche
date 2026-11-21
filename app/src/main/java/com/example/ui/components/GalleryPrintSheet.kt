package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.VideoView
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import com.example.viewmodel.InstantPrintPreview
import com.example.viewmodel.RecordedClipPreview
import java.io.File

// Museum Archival Cream Paper Palette (matching fine-art gallery prints)
val MuseumPaperCream = Color(0xFFF4F2EC)
val MuseumInkDark = Color(0xFF23211E)
val MuseumCaptionGrey = Color(0xFF706B63)
val MuseumHairline = Color(0xFFD9D4C8)

/**
 * Museum-paper "Tirage Galerie" reveal modal used for:
 * 1) Instant Print Preview ("Mode Aperçu Immédiat / Labo Express") right after shutter release.
 * 2) Zero-permission Gallery Photo Import developed through the mounted camera & film stack.
 * Supports Press & Hold Avant/Après comparison and 1-tap Raw / 4:5 Post / 9:16 Story export & share.
 */
@Composable
fun InstantGalleryPrintDialog(
    preview: InstantPrintPreview,
    onDismiss: () -> Unit,
    onUpdateNote: (String) -> Unit,
    onSaveRaw: () -> Unit,
    onSaveGalleryPost: () -> Unit,
    onSaveGalleryStory: () -> Unit,
    onShareGalleryCard: () -> Unit
) {
    var holdingOriginal by remember(preview.developedPath) { mutableStateOf(false) }
    val developedFile = remember(preview.developedPath) { File(preview.developedPath) }
    val originalFile = remember(preview.originalPath) { File(preview.originalPath) }
    val hasOriginal = originalFile.exists()

    val needsChemicalReveal = preview.revealType == "POLAROID" || preview.revealType == "COLLODION"
    val revealProgress = remember(preview.developedPath) {
        Animatable(if (needsChemicalReveal) 0f else 1f)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(preview.developedPath, needsChemicalReveal) {
        if (needsChemicalReveal) {
            revealProgress.snapTo(0f)
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1850, easing = FastOutSlowInEasing)
            )
        } else {
            revealProgress.snapTo(1f)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuseumPaperCream)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("instant_gallery_print_overlay"),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top minimal gallery bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (preview.isImportedFromGallery) "TIRAGE IMPORTÉ" else "TIRAGE GALERIE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                            color = MuseumCaptionGrey,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = preview.lensLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MuseumCaptionGrey.copy(alpha = 0.85f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasOriginal) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (holdingOriginal) MuseumInkDark else Color(0xFFE7E3D8))
                                    .border(1.dp, MuseumHairline, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Compare,
                                    contentDescription = null,
                                    tint = if (holdingOriginal) MuseumPaperCream else MuseumInkDark,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (holdingOriginal) "ORIGINAL" else "Maintenir : Avant",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (holdingOriginal) MuseumPaperCream else MuseumInkDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE7E3D8))
                                .testTag("close_instant_print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Fermer le tirage",
                                tint = MuseumInkDark
                            )
                        }
                    }
                }

                // Floating Museum Print with realistic soft elevation shadow + Hold-to-Compare
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .pointerInput(hasOriginal) {
                                if (hasOriginal) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        holdingOriginal = true
                                        waitForUpOrCancellation()
                                        holdingOriginal = false
                                    }
                                }
                            }
                            .testTag("instant_print_compare_surface"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(3.dp),
                                    ambientColor = Color(0x55181512),
                                    spotColor = Color(0x66181512)
                                ),
                            color = Color.Transparent,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = if (holdingOriginal && hasOriginal) originalFile else developedFile,
                                    contentDescription = preview.caption,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                )

                                // Authentic Polaroid milky veil or 19th-century Collodion silver-nitrate bath reveal
                                val veilAlpha = (1f - revealProgress.value).coerceIn(0f, 1f)
                                if (needsChemicalReveal && !holdingOriginal && veilAlpha > 0.02f) {
                                    val veilColor = if (preview.revealType == "POLAROID") {
                                        Color(0xFFCDD4C8) // Milky turquoise-cream integral film opacifier
                                    } else {
                                        Color(0xFF171614) // Dark metallic collodion / silver nitrate bath
                                    }
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(veilColor.copy(alpha = veilAlpha * 0.94f))
                                            .clickable {
                                                scope.launch { revealProgress.snapTo(1f) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xB80E0D0C))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (preview.revealType == "POLAROID") {
                                                    "Révélation instantanée… (toucher)"
                                                } else {
                                                    "Bain nitrate d'argent… (toucher)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = FilmCream
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Inline handwritten fountain-pen caption (baked on Polaroid / Instax / Gallery Card)
                    if (!holdingOriginal) {
                        HandwrittenNoteInput(
                            note = preview.handwrittenNote,
                            onNoteChange = onUpdateNote,
                            testTag = "instant_handwritten_note_input"
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    // Minimalist fine-art museum caption ("Pastel 400 · Half Frame")
                    Text(
                        text = if (holdingOriginal) "Original numérique (sans traitement)" else preview.caption,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MuseumCaptionGrey,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Museum Export & Share Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSaveRaw,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("instant_export_raw_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MuseumInkDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Brut",
                                style = MaterialTheme.typography.labelMedium,
                                color = MuseumInkDark,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onSaveGalleryPost,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("instant_export_card_45_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Photo,
                                contentDescription = null,
                                tint = MuseumInkDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Carte 4:5",
                                style = MaterialTheme.typography.labelMedium,
                                color = MuseumInkDark,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onSaveGalleryStory,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("instant_export_story_916_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CropPortrait,
                                contentDescription = null,
                                tint = MuseumInkDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Story 9:16",
                                style = MaterialTheme.typography.labelMedium,
                                color = MuseumInkDark,
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = onShareGalleryCard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuseumInkDark,
                            contentColor = MuseumPaperCream
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("instant_share_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Partager le Tirage Galerie",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable tactile fountain-pen italic note input shown under museum/Polaroid/Instax prints.
 */
@Composable
fun HandwrittenNoteInput(
    note: String,
    onNoteChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFECE8DD))
            .border(1.dp, MuseumHairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = MuseumCaptionGrey,
            modifier = Modifier.size(15.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (note.isEmpty()) {
                Text(
                    text = "Écrire une note manuscrite (Pola / Galerie)…",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp
                    ),
                    color = MuseumCaptionGrey.copy(alpha = 0.65f)
                )
            }
            BasicTextField(
                value = note,
                onValueChange = { onNoteChange(it.take(72)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.sp,
                    color = MuseumInkDark,
                    textAlign = TextAlign.Start
                ),
                cursorBrush = SolidColor(MuseumInkDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
        if (note.isNotEmpty()) {
            IconButton(
                onClick = { onNoteChange("") },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Effacer la note",
                    tint = MuseumCaptionGrey,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Dark skeuomorphic Super 8 / 35mm Cinema Reel Preview Dialog with looping native VideoView
 * and 1-tap export to Movies/Peloche or Android Share Sheet.
 */
@Composable
fun Super8ClipPreviewDialog(
    clip: RecordedClipPreview,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blackLeatherTexture()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("super8_clip_preview_dialog"),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Cinema Slate Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(LeicaCrimson)
                    )
                    Column {
                        Text(
                            text = "BOBINE ${clip.videoFilterBadge} • ${clip.durationSec}s",
                            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                            color = FilmCream,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${clip.videoFilterLabel} • ${clip.bodyName} (${clip.aspectLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = KodakAmber
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .brushedMetalPlate(shape = CircleShape, borderColor = HairlineMetal, elevated = true)
                        .testTag("close_super8_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fermer l'aperçu Super 8",
                        tint = SilverChrome
                    )
                }
            }

            // Center Looping Cinema Frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
                    .brushedMetalPlate(
                        shape = RoundedCornerShape(14.dp),
                        borderColor = BrassAccent.copy(alpha = 0.55f),
                        borderWidth = 1.2.dp
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(clip.filePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                start()
                            }
                            setOnErrorListener { _, _, _ -> true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )

                // Subtle bottom-left cinema gauge stamp
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB809090C))
                        .border(0.8.dp, BrassAccent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = null,
                        tint = BrassAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${clip.fpsLabel.uppercase()} • ${clip.videoFilterBadge} • ${clip.filmCode}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                        color = FilmCream,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Save & Share Cinema Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrassAccent,
                        contentColor = MatteLeather
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_super8_clip_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Enregistrer MP4", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("share_super8_clip_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        tint = BrassAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Partager", style = MaterialTheme.typography.labelLarge, color = FilmCream)
                }
            }
        }
    }
}
