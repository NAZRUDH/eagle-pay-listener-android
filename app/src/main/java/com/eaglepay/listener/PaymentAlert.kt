package com.eaglepay.listener

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Phase 3: Plays a chime sound and vibrates when a payment is detected.
 * Uses ToneGenerator (no audio file needed) + Vibrator API.
 * Safe to call from background thread.
 */
object PaymentAlert {

    private const val TAG = "EaglePayAlert"

    /**
     * Plays a success chime + vibration pattern.
     * Should be called only for real payments, not heartbeats.
     */
    fun play(ctx: Context) {
        try {
            vibrate(ctx)
            playChime()
        } catch (e: Exception) {
            Log.e(TAG, "Alert failed: ${e.message}")
        }
    }

    private fun playChime() {
        // ToneGenerator: needs no audio file, works offline
        // TONE_PROP_ACK = 2 tones ascending (pleasant "ding ding")
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 400)
        Thread.sleep(450)
        toneGen.release()
    }

    private fun vibrate(ctx: Context) {
        // Pattern: short-long-short (success feel)
        val pattern = longArrayOf(0, 80, 100, 200)

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }
}
