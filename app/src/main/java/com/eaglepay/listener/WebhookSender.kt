package com.eaglepay.listener

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object WebhookSender {
    private const val TAG = "EaglePayWebhook"

    // Phase 1 Fix 2: retry config
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 2000L  // 2s, 4s, 8s (exponential)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class Payload(
        val amount: Double,
        val utr: String?,
        val source: String,
        val payer_vpa: String?,
        val raw_text: String,
        val captured_at: Long = System.currentTimeMillis(),
    )

    /**
     * Phase 1 Fix 2: Retry with exponential backoff.
     * On network failure, retries up to MAX_RETRIES times.
     * Delay doubles each attempt: 2s → 4s → 8s.
     */
    fun send(ctx: Context, payload: Payload): Boolean {
        val url = Prefs.webhook(ctx) ?: return false
        val token = Prefs.token(ctx) ?: return false

        repeat(MAX_RETRIES) { attempt ->
            val success = trySend(url, token, payload)
            if (success) {
                if (payload.source != "heartbeat") {
                    Prefs.recordEvent(ctx)
                    // Phase 2: save to local payment history
                    PaymentHistory.add(ctx, PaymentHistory.Entry(
                        amount = payload.amount,
                        utr = payload.utr,
                        source = payload.source,
                        payerVpa = payload.payer_vpa,
                        capturedAt = payload.captured_at,
                        webhookSuccess = true,
                    ))
                }
                return true
            }
            if (attempt < MAX_RETRIES - 1) {
                val delay = RETRY_DELAY_MS * (1 shl attempt) // 2s, 4s, 8s
                Log.w(TAG, "Retry ${attempt + 1}/$MAX_RETRIES in ${delay}ms")
                Thread.sleep(delay)
            }
        }
        Log.e(TAG, "All $MAX_RETRIES attempts failed for amount=${payload.amount}")
        if (payload.source != "heartbeat") {
            // Phase 2: record failed delivery in history
            PaymentHistory.add(ctx, PaymentHistory.Entry(
                amount = payload.amount,
                utr = payload.utr,
                source = payload.source,
                payerVpa = payload.payer_vpa,
                capturedAt = payload.captured_at,
                webhookSuccess = false,
            ))
        }
        return false
    }

    private fun trySend(url: String, token: String, payload: Payload): Boolean {
        val req = Request.Builder()
            .url(url)
            .header("X-Device-Token", token)
            .header("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON))
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                Log.d(TAG, "POST ${res.code} amount=${payload.amount} utr=${payload.utr}")
                res.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            false
        }
    }

    /** Heartbeat: updates last_seen_at on server. No retry needed. */
    fun heartbeat(ctx: Context) {
        val url = Prefs.webhook(ctx) ?: return
        val token = Prefs.token(ctx) ?: return
        trySend(url, token, Payload(amount = 0.0, utr = null, source = "heartbeat", payer_vpa = null, raw_text = ""))
    }

    /**
     * Phase 1 Fix 3: Test the webhook connection.
     * Returns the HTTP status code or -1 on network error.
     * Used by the "Test Connection" button in MainActivity.
     */
    fun testConnection(ctx: Context): Int {
        val url = Prefs.webhook(ctx) ?: return -1
        val token = Prefs.token(ctx) ?: return -1
        val testPayload = Payload(
            amount = 0.0,
            utr = "TEST_${System.currentTimeMillis()}",
            source = "connection_test",
            payer_vpa = null,
            raw_text = "Connection test from Eagle Pay app"
        )
        val req = Request.Builder()
            .url(url)
            .header("X-Device-Token", token)
            .header("Content-Type", "application/json")
            .post(gson.toJson(testPayload).toRequestBody(JSON))
            .build()
        return try {
            client.newCall(req).execute().use { res -> res.code }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed: ${e.message}")
            -1
        }
    }
}
