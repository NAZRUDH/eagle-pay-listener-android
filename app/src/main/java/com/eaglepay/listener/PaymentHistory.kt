package com.eaglepay.listener

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Phase 2: Local payment history — stores last 10 detected payments.
 * Uses SharedPreferences + Gson for zero-dependency persistence.
 */
object PaymentHistory {

    private const val NAME = "eagle_pay_history"
    private const val KEY_HISTORY = "payment_history"
    private const val MAX_ENTRIES = 10

    private val gson = Gson()

    data class Entry(
        val amount: Double,
        val utr: String?,
        val source: String,
        val payerVpa: String?,
        val capturedAt: Long = System.currentTimeMillis(),
        val webhookSuccess: Boolean = false,
    )

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun add(ctx: Context, entry: Entry) {
        val current = getAll(ctx).toMutableList()
        current.add(0, entry)           // newest first
        val trimmed = current.take(MAX_ENTRIES)
        sp(ctx).edit()
            .putString(KEY_HISTORY, gson.toJson(trimmed))
            .apply()
    }

    fun getAll(ctx: Context): List<Entry> {
        val json = sp(ctx).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().remove(KEY_HISTORY).apply()
    }
}
