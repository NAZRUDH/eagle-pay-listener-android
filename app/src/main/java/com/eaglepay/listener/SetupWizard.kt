package com.eaglepay.listener

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Phase 3: Setup wizard helper.
 * Tracks which steps are complete and returns the next required action.
 */
object SetupWizard {

    enum class Step { PAIR, NOTIFICATION_ACCESS, BATTERY, COMPLETE }

    data class Status(
        val isPaired: Boolean,
        val hasNotificationAccess: Boolean,
        val hasBatteryExemption: Boolean,
    ) {
        val currentStep: Step get() = when {
            !isPaired -> Step.PAIR
            !hasNotificationAccess -> Step.NOTIFICATION_ACCESS
            !hasBatteryExemption -> Step.BATTERY
            else -> Step.COMPLETE
        }

        val progressPercent: Int get() = when (currentStep) {
            Step.PAIR -> 0
            Step.NOTIFICATION_ACCESS -> 33
            Step.BATTERY -> 66
            Step.COMPLETE -> 100
        }

        val stepLabel: String get() = when (currentStep) {
            Step.PAIR -> "Step 1 of 3 — Scan pairing QR"
            Step.NOTIFICATION_ACCESS -> "Step 2 of 3 — Grant notification access"
            Step.BATTERY -> "Step 3 of 3 — Disable battery optimisation"
            Step.COMPLETE -> "Setup complete!"
        }

        val stepDescription: String get() = when (currentStep) {
            Step.PAIR -> "Open your Eagle Pay dashboard → Listeners → Pair Device, then scan the QR code shown."
            Step.NOTIFICATION_ACCESS -> "Tap the button below, find 'Eagle Pay Listener' and toggle it ON. This lets the app read UPI payment notifications."
            Step.BATTERY -> "Tap the button below and select 'Don't optimise'. This prevents Android from killing the app in the background."
            Step.COMPLETE -> "Eagle Pay Listener is active and listening for UPI payments on your device."
        }

        val actionLabel: String get() = when (currentStep) {
            Step.PAIR -> "📷 Scan Pairing QR"
            Step.NOTIFICATION_ACCESS -> "Enable Notification Access →"
            Step.BATTERY -> "Disable Battery Optimisation →"
            Step.COMPLETE -> "✅ All done!"
        }
    }

    fun getStatus(ctx: Context): Status {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return Status(
            isPaired = Prefs.isPaired(ctx),
            hasNotificationAccess = NotificationCaptureService.isAccessGranted(ctx),
            hasBatteryExemption = pm.isIgnoringBatteryOptimizations(ctx.packageName),
        )
    }

    fun openNotificationSettings(ctx: Context) {
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    fun openBatterySettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            ctx.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
