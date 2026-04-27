package com.eaglepay.listener

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.eaglepay.listener.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) return@registerForActivityResult
        try {
            val payload = gson.fromJson(result.contents, PairingPayload::class.java)
            require(payload.token.isNotBlank() && payload.webhook.isNotBlank()) { "invalid QR" }
            Prefs.savePairing(this, payload.token, payload.webhook, payload.name ?: "Phone")
            ForegroundService.start(this)
            Toast.makeText(this, "✅ Paired successfully!", Toast.LENGTH_SHORT).show()
            refreshUi()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    data class PairingPayload(
        val v: Int = 1,
        val token: String = "",
        val webhook: String = "",
        val name: String? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phase 3: Apply saved theme before setContentView
        applyTheme()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Prefs.isPaired(this)) ForegroundService.start(this)

        // Phase 3: Theme toggle button
        b.btnTheme.setOnClickListener {
            val current = Prefs.isDarkMode(this)
            Prefs.setDarkMode(this, !current)
            applyTheme()
            recreate() // Restart activity to apply theme
        }

        // Phase 3: Setup wizard — single action button handles all steps
        b.btnWizardAction.setOnClickListener {
            val status = SetupWizard.getStatus(this)
            when (status.currentStep) {
                SetupWizard.Step.PAIR -> scanLauncher.launch(ScanOptions().apply {
                    setOrientationLocked(true)
                    setBeepEnabled(false)
                    setPrompt("Scan the pairing QR from your Eagle Pay dashboard")
                })
                SetupWizard.Step.NOTIFICATION_ACCESS -> SetupWizard.openNotificationSettings(this)
                SetupWizard.Step.BATTERY -> SetupWizard.openBatterySettings(this)
                SetupWizard.Step.COMPLETE -> { /* nothing — setup is done */ }
            }
        }

        b.btnTestConnection.setOnClickListener {
            Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()
            Thread {
                val code = WebhookSender.testConnection(this)
                runOnUiThread {
                    when {
                        code in 200..299 -> Toast.makeText(this, "✅ Connected! (HTTP $code)", Toast.LENGTH_SHORT).show()
                        code == -1 -> Toast.makeText(this, "❌ Network error", Toast.LENGTH_LONG).show()
                        code == 401 -> Toast.makeText(this, "❌ Auth failed — re-pair device", Toast.LENGTH_LONG).show()
                        else -> Toast.makeText(this, "⚠️ Server returned HTTP $code", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        b.btnUpdateWebhook.setOnClickListener {
            val currentUrl = Prefs.webhook(this) ?: ""
            val input = android.widget.EditText(this).apply {
                setText(currentUrl)
                hint = "https://your-domain.vercel.app/api/notification-webhook"
                setPadding(40, 20, 40, 20)
            }
            AlertDialog.Builder(this)
                .setTitle("Update Webhook URL")
                .setMessage("Enter your new webhook URL:")
                .setView(input)
                .setPositiveButton("Update") { _, _ ->
                    val newUrl = input.text.toString().trim()
                    if (newUrl.startsWith("https://")) {
                        Prefs.updateWebhookUrl(this, newUrl)
                        Toast.makeText(this, "✅ Webhook URL updated!", Toast.LENGTH_SHORT).show()
                        refreshUi()
                    } else {
                        Toast.makeText(this, "URL must start with https://", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        b.btnHistory.setOnClickListener { showPaymentHistory() }

        b.btnUnpair.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Unpair Device")
                .setMessage("Are you sure? You'll need to scan a new QR code to re-pair.")
                .setPositiveButton("Unpair") { _, _ ->
                    Prefs.clear(this)
                    PaymentHistory.clear(this)
                    ForegroundService.stop(this)
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        b.btnCheckUpdate.setOnClickListener {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
            Thread {
                val info = UpdateChecker.check(this)
                runOnUiThread {
                    if (info == null) {
                        Toast.makeText(this, "Could not check for updates.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    if (info.available) {
                        AlertDialog.Builder(this)
                            .setTitle("Update Available! v${info.latestVersion}")
                            .setMessage("You have v${info.currentVersion}.\n\nWhat's new:\n${info.releaseNotes}")
                            .setPositiveButton("Download") { _, _ -> UpdateChecker.openDownload(this) }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        Toast.makeText(this, "✅ Latest version (v${info.currentVersion})", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        checkForUpdatesInBackground()
    }

    private fun checkForUpdatesInBackground() {
        Thread {
            val info = UpdateChecker.check(this) ?: return@Thread
            if (info.available) {
                runOnUiThread {
                    b.btnCheckUpdate.text = "🆕 Update Available! v${info.latestVersion}"
                }
            }
        }.start()
    }

    private fun showPaymentHistory() {
        val history = PaymentHistory.getAll(this)
        if (history.isEmpty()) {
            Toast.makeText(this, "No payment history yet", Toast.LENGTH_SHORT).show()
            return
        }
        val lines = history.joinToString("\n\n") { e ->
            val status = if (e.webhookSuccess) "✅" else "❌"
            val time = dateFormat.format(Date(e.capturedAt))
            val utr = e.utr ?: "—"
            "$status ₹${e.amount} via ${e.source}\n   UTR: $utr\n   ${e.payerVpa ?: ""}\n   $time"
        }
        AlertDialog.Builder(this)
            .setTitle("Last ${history.size} Payments")
            .setMessage(lines)
            .setPositiveButton("Clear History") { _, _ ->
                PaymentHistory.clear(this)
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.isDarkMode(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun refreshUi() {
        val status = SetupWizard.getStatus(this)
        val paired = status.isPaired
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }

        // Phase 3: Version + theme toggle
        b.tvVersion.text = "Eagle Pay Listener v$version"
        b.btnTheme.text = if (Prefs.isDarkMode(this)) "☀️" else "🌙"

        // Phase 3: Setup wizard UI
        if (status.currentStep != SetupWizard.Step.COMPLETE) {
            b.wizardCard.visibility = View.VISIBLE
            b.mainContent.visibility = View.GONE
            b.tvWizardStep.text = status.stepLabel
            b.tvWizardDesc.text = status.stepDescription
            b.btnWizardAction.text = status.actionLabel
            b.btnWizardAction.isEnabled = status.currentStep != SetupWizard.Step.COMPLETE
            b.wizardProgress.progress = status.progressPercent
        } else {
            b.wizardCard.visibility = View.GONE
            b.mainContent.visibility = View.VISIBLE
        }

        // Main content (shown after setup complete)
        val webhookUrl = Prefs.webhook(this)
        b.statusPaired.text = "✅ Paired (${Prefs.deviceName(this)})"
        b.statusWebhook.text = "🔗 ${webhookUrl?.take(55) ?: "No webhook"}..."
        b.statusAccess.text = if (status.hasNotificationAccess) "✅ Notification access granted" else "❌ Notification access required"
        b.statusBattery.text = if (status.hasBatteryExemption) "✅ Battery optimisation off" else "⚠️ Battery optimisation on"

        val last = Prefs.lastEventAt(this)
        val count = Prefs.eventCount(this)
        b.statusLast.text = if (last == 0L) "No events yet"
        else "Last: ${dateFormat.format(Date(last))} • $count total"

        val historyCount = PaymentHistory.getAll(this).size
        b.btnHistory.text = "📋 Payment History ($historyCount)"

        val ready = paired && status.hasNotificationAccess
        b.statusReady.text = if (ready) "🟢 Listener is RUNNING" else "🔴 Setup incomplete"
        b.statusReady.setTextColor(
            if (ready) getColor(R.color.success) else getColor(R.color.error)
        )

        b.tvVersion.text = "Eagle Pay Listener v$version"
    }
}
