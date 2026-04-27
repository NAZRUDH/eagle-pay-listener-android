package com.eaglepay.listener

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "eagle_pay_prefs"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_WEBHOOK = "webhook_url"
    private const val KEY_NAME = "device_name"
    private const val KEY_LAST = "last_event_at"
    private const val KEY_COUNT = "event_count_long"  // Long key — avoids Int overflow

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun savePairing(ctx: Context, token: String, webhook: String, name: String) {
        sp(ctx).edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_WEBHOOK, webhook)
            putString(KEY_NAME, name)
        }.apply()
    }

    /**
     * Phase 1 Fix 3: Update webhook URL without re-scanning QR.
     * Called when user manually enters a new webhook URL in settings.
     */
    fun updateWebhookUrl(ctx: Context, newUrl: String) {
        sp(ctx).edit().putString(KEY_WEBHOOK, newUrl).apply()
    }

    fun token(ctx: Context): String? = sp(ctx).getString(KEY_TOKEN, null)
    fun webhook(ctx: Context): String? = sp(ctx).getString(KEY_WEBHOOK, null)
    fun deviceName(ctx: Context): String? = sp(ctx).getString(KEY_NAME, null)

    fun isPaired(ctx: Context): Boolean = token(ctx) != null && webhook(ctx) != null

    fun recordEvent(ctx: Context) {
        val s = sp(ctx)
        s.edit()
            .putLong(KEY_LAST, System.currentTimeMillis())
            .putLong(KEY_COUNT, eventCount(ctx) + 1L)
            .apply()
    }

    fun lastEventAt(ctx: Context): Long = sp(ctx).getLong(KEY_LAST, 0L)

    fun eventCount(ctx: Context): Long {
        val s = sp(ctx)
        return try {
            s.getLong(KEY_COUNT, 0L)
        } catch (_: ClassCastException) {
            // Migrate legacy Int value
            val legacy = s.getInt(KEY_COUNT, 0).toLong()
            s.edit().putLong(KEY_COUNT, legacy).apply()
            legacy
        }
    }

    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }

    // Phase 3: Theme preference
    fun isDarkMode(ctx: Context): Boolean = sp(ctx).getBoolean("dark_mode", true)
    fun setDarkMode(ctx: Context, dark: Boolean) { sp(ctx).edit().putBoolean("dark_mode", dark).apply() }
}
