package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmStockModel
import com.example.domain.model.LensModel
import com.example.domain.model.OpticalChainParams
import com.example.engine.AnalogGlSurfaceView
import com.example.engine.CameraSurfaceHolder
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.BrushedGunmetal
import com.example.ui.theme.ExpiredOlive
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import com.example.viewmodel.ExpiredFilmAlert

/**
 * Handle allowing Compose screens to start/stop hardware Super 8 MP4 recording
 * directly on the live AnalogGlSurfaceView EGL pipeline.
 */
class Super8RecorderHandle {
    internal var glView: AnalogGlSurfaceView? = null

    fun startRecording(
        outputFile: java.io.File,
        aspectRatio: Float,
        targetFps: Int = 24,
        onStarted: (Boolean) -> Unit
    ) {
        val view = glView
        if (view == null) {
            onStarted(false)
            return
        }
        view.startSuper8Recording(outputFile, aspectRatio, targetFps, onStarted)
    }

    fun stopRecording(onFinished: (java.io.File?) -> Unit) {
        val view = glView
        if (view == null) {
            onFinished(null)
            return
        }
        view.stopSuper8Recording(onFinished)
    }
}

/**
 * Reusable live OpenGL ES preview view that renders either a live CameraX stream
 * or the bundled Day/Night Scène Témoin through our 3-stage GLSL optical shader.
 */
@Composable
fun LiveOpticalPreviewSurface(
    params: OpticalChainParams,
    useCameraStream: Boolean,
    sampleSceneNight: Boolean,
    animatedEffectsEnabled: Boolean = true,
    recorderHandle: Super8RecorderHandle? = null,
    modifier: Modifier = Modifier,
    onSurfaceTextureReady: ((CameraSurfaceHolder) -> Unit)? = null,
    onSurfaceTextureDestroyed: ((CameraSurfaceHolder) -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnReady = rememberUpdatedState(onSurfaceTextureReady)
    val latestOnDestroyed = rememberUpdatedState(onSurfaceTextureDestroyed)

    fun readCurrentDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
            }
        } catch (_: Throwable) {
            Surface.ROTATION_0
        }
    }

    val glView = remember {
        AnalogGlSurfaceView(
            context = context,
            initialParams = params,
            useCameraStream = useCameraStream,
            onSurfaceTextureReady = if (onSurfaceTextureReady != null) {
                { holder -> latestOnReady.value?.invoke(holder) }
            } else {
                null
            },
            onSurfaceTextureDestroyed = if (onSurfaceTextureDestroyed != null) {
                { holder -> latestOnDestroyed.value?.invoke(holder) }
            } else {
                null
            }
        ).also { it.setDisplayRotation(readCurrentDisplayRotation()) }
    }

    DisposableEffect(lifecycleOwner, glView, recorderHandle) {
        recorderHandle?.glView = glView
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                glView.setDisplayRotation(readCurrentDisplayRotation())
            }
        }
        displayManager?.registerDisplayListener(displayListener, null)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glView.setDisplayRotation(readCurrentDisplayRotation())
                    glView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (recorderHandle?.glView === glView) {
                recorderHandle.glView = null
            }
            displayManager?.unregisterDisplayListener(displayListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.releaseSafely()
        }
    }

    val currentRotation = remember(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        readCurrentDisplayRotation()
    }

    AndroidView(
        factory = { glView },
        update = { view ->
            view.setDisplayRotation(currentRotation)
            view.setCameraFeedActive(useCameraStream)
            view.setSampleNight(sampleSceneNight)
            view.setAnimatedEffectsEnabled(animatedEffectsEnabled)
            view.updateParams(params)
        },
        modifier = modifier
    )
}

/**
 * Cinema Clapperboard Metadata Strip ("Clap Cinéma") summarizing the active
 * Boîtier + Objectif + Pellicule + Expiration Date trio in technical monospace typography.
 */
@Composable
fun CinemaClapperCard(
    rollNumber: Int,
    body: CameraBodyModel,
    lens: LensModel,
    film: FilmStockModel,
    expiration: FilmExpirationDate,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrushedGunmetal)
            .border(1.dp, HairlineMetal, RoundedCornerShape(10.dp))
    ) {
        // Classic diagonal black & ivory cinema clapperboard top bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MatteLeather)
        ) {
            val stripeW = 22f
            var x = -20f
            while (x < size.width + 20f) {
                val p = Path().apply {
                    moveTo(x, size.height)
                    lineTo(x + 10f, 0f)
                    lineTo(x + 10f + stripeW * 0.5f, 0f)
                    lineTo(x + stripeW * 0.5f, size.height)
                    close()
                }
                drawPath(p, color = FilmCream.copy(alpha = 0.88f))
                x += stripeW
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClapperCell(
                label = "ROULEAU",
                value = "N°%03d".format(rollNumber),
                accentColor = BrassAccent,
                modifier = Modifier.weight(0.75f)
            )
            ClapperVerticalDivider()
            ClapperCell(
                label = "BOÎTIER",
                value = body.shortCode,
                modifier = Modifier.weight(1f)
            )
            ClapperVerticalDivider()
            ClapperCell(
                label = "OPTIQUE",
                value = lens.shortCode,
                modifier = Modifier.weight(1.05f)
            )
            ClapperVerticalDivider()
            ClapperCell(
                label = "FILM • ${film.iso}",
                value = film.code,
                accentColor = if (expiration.isExpired) KodakAmber else SilverChrome,
                modifier = Modifier.weight(1.05f)
            )
        }
    }
}

@Composable
private fun ClapperCell(
    label: String,
    value: String,
    accentColor: Color = SilverChrome,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WarmEngraving,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ClapperVerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(HairlineMetal)
    )
}

/**
 * Prominent tactile warning banner shown whenever an expired film roll is active or selected,
 * explaining the exact expiration date and chemistry-specific color/grain impact.
 */
@Composable
fun ExpiredFilmWarningBanner(
    film: FilmStockModel,
    expiration: FilmExpirationDate,
    onDismiss: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!expiration.isExpired) return
    val severe = expiration.yearsExpired >= 5f
    val borderColor = if (severe) LeicaCrimson else KodakAmber
    val bgColor = if (severe) Color(0xFF2B1412) else Color(0xFF292013)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .then(if (onActionClick != null) Modifier.clickable { onActionClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("expired_film_warning_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = "Alerte pellicule périmée",
            tint = borderColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PELLICULE PÉRIMÉE • ${expiration.stampCode}",
                style = MaterialTheme.typography.labelMedium,
                color = borderColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = expiration.warningDescriptionFr(film.emulsionType),
                style = MaterialTheme.typography.bodySmall,
                color = FilmCream.copy(alpha = 0.92f)
            )
        }
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer l'avertissement",
                    tint = WarmEngraving,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Interactive Modal Alert displayed when the user picks or loads an expired film roll,
 * notifying them of exact chemical degradation and offering to keep it or pick fresh stock.
 */
@Composable
fun ExpiredFilmAlertDialog(
    alert: ExpiredFilmAlert,
    canSwapToFresh: Boolean,
    onKeepExpired: () -> Unit,
    onLoadFreshInstead: () -> Unit
) {
    val severe = alert.expirationDate.yearsExpired >= 5f
    AlertDialog(
        onDismissRequest = onKeepExpired,
        containerColor = BrushedGunmetal,
        titleContentColor = FilmCream,
        textContentColor = SilverChrome,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = "Pellicule périmée",
                tint = if (severe) LeicaCrimson else KodakAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Pellicule périmée détectée",
                style = MaterialTheme.typography.titleLarge,
                color = FilmCream
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AnodizedSlate)
                        .border(1.dp, HairlineMetal, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${alert.film.name} (${alert.film.emulsionType.labelFr})",
                            style = MaterialTheme.typography.titleMedium,
                            color = FilmCream
                        )
                        Text(
                            text = alert.expirationDate.stampCode + " • " + alert.expirationDate.severityLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (severe) LeicaCrimson else KodakAmber
                        )
                    }
                }
                Text(
                    text = alert.warningMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverChrome
                )
                Text(
                    text = "Ce rendu vieilli est visible en temps réel dans le viseur et sera fixé au développement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpiredOlive
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onKeepExpired,
                colors = ButtonDefaults.buttonColors(containerColor = KodakAmber, contentColor = MatteLeather),
                modifier = Modifier.testTag("keep_expired_film_button")
            ) {
                Text("Conserver ce lot", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            if (canSwapToFresh) {
                OutlinedButton(
                    onClick = onLoadFreshInstead,
                    modifier = Modifier.testTag("swap_fresh_film_button")
                ) {
                    Text("Prendre fraîche", color = SilverChrome, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    )
}
