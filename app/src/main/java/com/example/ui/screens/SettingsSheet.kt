package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CameraSettings
import com.example.domain.model.DateStampStyle
import com.example.ui.components.brushedMetalPlate
import com.example.ui.components.recessedInstrumentWell
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.BrushedGunmetal
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: CameraSettings,
    supportsDiptych: Boolean,
    useSampleSceneInViewfinder: Boolean,
    onDismiss: () -> Unit,
    onSelectDateStampStyle: (DateStampStyle) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onToggleDiptych: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleLosslessPng: (Boolean) -> Unit,
    onToggleSampleSceneMode: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentDateStyle = settings.effectiveDateStampStyle

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14151B),
        contentColor = SilverChrome
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Réglages mécaniques & Labo",
                style = MaterialTheme.typography.headlineMedium,
                color = FilmCream
            )
            Text(
                text = "Traitement 100 % local sur l'appareil • Zéro permission réseau",
                style = MaterialTheme.typography.bodySmall,
                color = BrassAccent
            )
            Spacer(Modifier.height(2.dp))

            // Multi-Style Date Back Selector Card (6 authentic imprint styles!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .brushedMetalPlate(shape = RoundedCornerShape(13.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Dos dateur",
                        tint = if (currentDateStyle != DateStampStyle.OFF) BrassAccent else WarmEngraving
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dos Dateur • Impression de date",
                            style = MaterialTheme.typography.titleMedium,
                            color = FilmCream
                        )
                        Text(
                            text = currentDateStyle.descriptionFr,
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmEngraving
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("date_stamp_style_selector")
                ) {
                    items(DateStampStyle.entries) { style ->
                        val selected = style == currentDateStyle
                        val coreColor = Color(style.coreColorHex)
                        val glowColor = Color(style.glowColorHex)
                        Column(
                            modifier = Modifier
                                .recessedInstrumentWell(
                                    shape = RoundedCornerShape(10.dp),
                                    borderColor = if (selected) BrassAccent else HairlineMetal
                                )
                                .clickable { onSelectDateStampStyle(style) }
                                .padding(horizontal = 11.dp, vertical = 9.dp)
                                .testTag("date_style_${style.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = style.samplePreview,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = if (style == DateStampStyle.CLASSIC_WHITE || style == DateStampStyle.OFF) {
                                        FontFamily.SansSerif
                                    } else {
                                        FontFamily.Monospace
                                    },
                                    fontSize = 11.sp,
                                    letterSpacing = if (style == DateStampStyle.OFF) 0.sp else 1.2.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = if (style != DateStampStyle.OFF && style != DateStampStyle.CLASSIC_WHITE) {
                                        Shadow(color = glowColor, offset = Offset.Zero, blurRadius = 8f)
                                    } else null
                                ),
                                color = if (style == DateStampStyle.OFF) WarmEngraving else coreColor
                            )
                            Text(
                                text = style.shortLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (selected) BrassAccent else SilverChrome,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            SettingToggleRow(
                icon = Icons.Filled.GridOn,
                title = "Grille des tiers au viseur",
                subtitle = "Repères optiques discrets gravés dans le dépoli",
                checked = settings.gridEnabled,
                testTag = "setting_grid",
                onCheckedChange = onToggleGrid
            )

            SettingToggleRow(
                icon = Icons.Filled.ViewColumn,
                title = "Mode Diptyque (Demi-Format)",
                subtitle = if (supportsDiptych) {
                    "Assemble 2 vues verticales côte à côte au développement"
                } else {
                    "Sélectionnez un boîtier Half Frame (72 poses) pour activer"
                },
                checked = settings.diptychEnabled && supportsDiptych,
                enabled = supportsDiptych,
                testTag = "setting_diptych",
                onCheckedChange = onToggleDiptych
            )

            SettingToggleRow(
                icon = Icons.Filled.VolumeUp,
                title = "Bruitages mécaniques",
                subtitle = "Rideau du déclencheur, avance film automatique et rembobinage",
                checked = settings.soundEnabled,
                testTag = "setting_sound",
                onCheckedChange = onToggleSound
            )

            SettingToggleRow(
                icon = Icons.Filled.Vibration,
                title = "Retour haptique",
                subtitle = "Crans mécaniques sur les molettes et clic sec au déclenchement",
                checked = settings.hapticsEnabled,
                testTag = "setting_haptics",
                onCheckedChange = onToggleHaptics
            )

            SettingToggleRow(
                icon = Icons.Filled.HighQuality,
                title = "Export PNG sans perte",
                subtitle = "Désactivé : JPEG haute qualité 95 % avec tags EXIF argentiques",
                checked = settings.exportLosslessPng,
                testTag = "setting_png",
                onCheckedChange = onToggleLosslessPng
            )

            SettingToggleRow(
                icon = Icons.Filled.CameraAlt,
                title = "Mode Scène Témoin au viseur",
                subtitle = "Teste la chaîne optique en direct sur une photo de référence locale",
                checked = useSampleSceneInViewfinder,
                testTag = "setting_sample_scene",
                onCheckedChange = onToggleSampleSceneMode
            )

            // Discreet local-storage transparency notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .recessedInstrumentWell(shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = BrassAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Vos rouleaux sont archivés uniquement en local sur cet appareil. Pensez à exporter vos planches ou tirages importants dans DCIM/Peloche avant de désinstaller l'application.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmEngraving
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brushedMetalPlate(shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (enabled && checked) BrassAccent else WarmEngraving
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) FilmCream else WarmEngraving
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = WarmEngraving
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MatteLeather,
                checkedTrackColor = BrassAccent,
                uncheckedThumbColor = SilverChrome,
                uncheckedTrackColor = AnodizedSlate
            )
        )
    }
}
