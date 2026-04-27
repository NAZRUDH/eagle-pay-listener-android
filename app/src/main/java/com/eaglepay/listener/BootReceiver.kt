package com.eaglepay.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isPaired(ctx)) return
        // Phase 1 Fix 1: Start foreground service on boot
        ForegroundService.start(ctx)
    }
}
