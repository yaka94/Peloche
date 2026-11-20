package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural mechanical camera sound synthesizer & tactile haptic controller.
 * Generates authentic zero-asset mechanical acoustics (focal-plane curtain shutter,
 * film advance lever ratchet, knurled dial detents, motorized cassette rewind).
 */
class MechanicalAudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleRate = 22050

    private val shutterPcm: ShortArray by lazy { synthesizeShutterCurtain() }
    private val advancePcm: ShortArray by lazy { synthesizeAdvanceLever() }
    private val detentPcm: ShortArray by lazy { synthesizeDialDetent() }
    private val rewindPcm: ShortArray by lazy { synthesizeFilmRewind() }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playShutterClick(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) vibrateSharp(28L, 220)
        if (soundEnabled) playPcm(shutterPcm)
    }

    fun playAdvanceLever(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) vibrateSharp(14L, 140)
        if (soundEnabled) playPcm(advancePcm)
    }

    fun playDialDetent(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) vibrateSharp(8L, 95)
        if (soundEnabled) playPcm(detentPcm)
    }

    fun playRewindSequence(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) vibrateRewindPattern()
        if (soundEnabled) playPcm(rewindPcm)
    }

    private fun vibrateSharp(durationMs: Long, amplitude: Int) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Throwable) {
        }
    }

    private fun vibrateRewindPattern() {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            val timings = longArrayOf(0, 45, 55, 45, 55, 45, 55, 90)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amps = intArrayOf(0, 110, 0, 130, 0, 150, 0, 210)
                v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        } catch (_: Throwable) {
        }
    }

    private fun playPcm(pcm: ShortArray) {
        scope.launch {
            try {
                val byteCount = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount.coerceAtLeast(1024))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val durationMs = (pcm.size * 1000L) / sampleRate + 60L
                kotlinx.coroutines.delay(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    /** Crisp 2-stage mechanical mirror slap + metallic cloth curtain snap (~110ms). */
    private fun synthesizeShutterCurtain(): ShortArray {
        val len = (sampleRate * 0.115).toInt()
        val out = ShortArray(len)
        val rng = Random(42)
        for (i in 0 until len) {
            val t = i.toDouble() / sampleRate
            // First curtain slap at t=0
            val c1 = (rng.nextDouble() * 2.0 - 1.0) * exp(-t * 140.0) * 0.85 +
                sin(2.0 * PI * 320.0 * t) * exp(-t * 95.0) * 0.45
            // Second curtain snap at t=0.048s
            val t2 = t - 0.048
            val c2 = if (t2 > 0) {
                (rng.nextDouble() * 2.0 - 1.0) * exp(-t2 * 165.0) * 0.72 +
                    sin(2.0 * PI * 540.0 * t2) * exp(-t2 * 120.0) * 0.35
            } else 0.0
            val sample = ((c1 + c2) * 0.75).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 4-click brass gear film advance ratchet (~190ms). */
    private fun synthesizeAdvanceLever(): ShortArray {
        val len = (sampleRate * 0.19).toInt()
        val out = ShortArray(len)
        val rng = Random(77)
        val clickTimes = doubleArrayOf(0.0, 0.042, 0.084, 0.130)
        for (i in 0 until len) {
            val t = i.toDouble() / sampleRate
            var acc = 0.0
            for (ct in clickTimes) {
                val dt = t - ct
                if (dt >= 0.0 && dt < 0.03) {
                    acc += ((rng.nextDouble() * 2.0 - 1.0) * 0.45 + sin(2.0 * PI * 1480.0 * dt) * 0.35) *
                        exp(-dt * 220.0)
                }
            }
            out[i] = (acc.coerceIn(-1.0, 1.0) * 0.55 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Precision knurled ring mechanical detent (~22ms). */
    private fun synthesizeDialDetent(): ShortArray {
        val len = (sampleRate * 0.022).toInt()
        val out = ShortArray(len)
        for (i in 0 until len) {
            val t = i.toDouble() / sampleRate
            val s = (sin(2.0 * PI * 2200.0 * t) * 0.4 + sin(2.0 * PI * 920.0 * t) * 0.3) * exp(-t * 260.0)
            out[i] = (s.coerceIn(-1.0, 1.0) * 0.45 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Authentic cassette motor rewind whirr + leader snap (~850ms). */
    private fun synthesizeFilmRewind(): ShortArray {
        val len = (sampleRate * 0.85).toInt()
        val out = ShortArray(len)
        val rng = Random(99)
        for (i in 0 until len) {
            val t = i.toDouble() / sampleRate
            val env = when {
                t < 0.08 -> t / 0.08
                t > 0.72 -> (0.85 - t) / 0.13
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val freq = 140.0 + 45.0 * (t / 0.85)
            val whirr = sin(2.0 * PI * freq * t) * 0.28 +
                sin(2.0 * PI * freq * 3.0 * t) * 0.14 +
                (rng.nextDouble() * 2.0 - 1.0) * 0.20 * (0.5 + 0.5 * sin(2.0 * PI * 28.0 * t))
            // Final leader detach snap
            val snapT = t - 0.76
            val snap = if (snapT > 0) (rng.nextDouble() * 2.0 - 1.0) * exp(-snapT * 140.0) * 0.65 else 0.0
            val s = ((whirr * env + snap) * 0.65).coerceIn(-1.0, 1.0)
            out[i] = (s * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
