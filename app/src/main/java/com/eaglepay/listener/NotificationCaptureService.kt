package com.eaglepay.listener

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

class NotificationCaptureService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    // Bug 2 fix: bounded thread pool instead of raw `Thread {}.start()` per notification.
    // - Caps concurrent network calls (avoids thread explosion under burst).
    // - Discards oldest if backlog grows beyond queue capacity.
    // - Wraps work in try/catch so a crash in one task can't kill the service.
    private lateinit var executor: ExecutorService

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            submit { WebhookSender.heartbeat(applicationContext) }
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        executor = ThreadPoolExecutor(
            2,                                // core threads
            4,                                // max threads
            30L, TimeUnit.SECONDS,            // idle keep-alive
            LinkedBlockingQueue(64),          // bounded backlog
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { executor.shutdownNow() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        handler.post(heartbeatRunnable)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        handler.removeCallbacks(heartbeatRunnable)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in WATCHED_PACKAGES) return
        if (!Prefs.isPaired(applicationContext)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val combined = listOf(title, text, bigText, sub).joinToString(" \n ").trim()
        if (combined.isBlank()) return

        val parsed = UpiParser.parse(combined) ?: return
        val source = SOURCE_LABELS[pkg] ?: pkg

        Log.i(TAG, "Parsed: amount=${parsed.amount} utr=${parsed.utr} src=$source")

        submit {
            WebhookSender.send(
                applicationContext,
                WebhookSender.Payload(
                    amount = parsed.amount,
                    utr = parsed.utr,
                    source = source,
                    payer_vpa = parsed.payerVpa,
                    raw_text = combined.take(500),
                )
            )
        }
    }

    private fun submit(task: () -> Unit) {
        try {
            // Phase 3: play sound + vibration when payment captured
        executor.execute { PaymentAlert.play(applicationContext) }
        executor.execute {
                try {
                    task()
                } catch (t: Throwable) {
                    Log.e(TAG, "Background task failed", t)
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Task rejected (executor saturated or shut down)", e)
        }
    }

    companion object {
        private const val TAG = "EaglePayListener"
        private const val HEARTBEAT_MS = 60_000L

        // Allow-list of UPI / banking apps we listen to. Add more as needed.
        val WATCHED_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user",  // GPay
            "com.phonepe.app",                          // PhonePe
            "net.one97.paytm",                          // Paytm
            "in.org.npci.upiapp",                       // BHIM
            "com.whatsapp",                             // WhatsApp Pay
            "com.amazon.mShop.android.shopping",        // Amazon Pay
            "com.csam.icici.bank.imobile",              // ICICI iMobile
            "com.snapwork.hdfc",                        // HDFC
            "com.sbi.SBIFreedomPlus",                   // SBI YONO
            "com.axis.mobile",                          // Axis
            "com.msf.kbank.mobile",                     // Kotak
            "com.fss.indus",                            // IndusInd
            "com.fedmobile",                            // Federal
            "com.idfcfirstbank.optimus",                // IDFC FIRST
            "com.android.mms",                          // SMS app fallback
            "com.google.android.apps.messaging",        // Google Messages
            "com.samsung.android.messaging",            // Samsung Messages
        )

        val SOURCE_LABELS = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "GPay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "com.whatsapp" to "WhatsApp Pay",
            "com.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.csam.icici.bank.imobile" to "ICICI",
            "com.snapwork.hdfc" to "HDFC",
            "com.sbi.SBIFreedomPlus" to "SBI",
            "com.axis.mobile" to "Axis",
            "com.msf.kbank.mobile" to "Kotak",
            "com.fss.indus" to "IndusInd",
            "com.fedmobile" to "Federal",
            "com.idfcfirstbank.optimus" to "IDFC",
            "com.android.mms" to "SMS",
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS",
        )

        fun isAccessGranted(ctx: Context): Boolean {
            val cn = ComponentName(ctx, NotificationCaptureService::class.java)
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            return flat?.contains(cn.flattenToString()) == true
        }
    }
}
