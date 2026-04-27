# Eagle Pay — Notification Listener (Android)

Lightweight Android app that listens for UPI payment notifications (GPay,
PhonePe, Paytm, bank apps, SMS) and forwards parsed amount + UTR to your
Eagle Pay backend for instant auto-verification.

## How it works

```
GPay receives ₹500
   ↓
Notification appears: "₹500 received from John Doe — UTR 412345678901"
   ↓
NotificationListenerService intercepts it
   ↓
Regex extracts amount=500, utr=412345678901, source="GPay"
   ↓
HTTPS POST to webhook with X-Device-Token header
   ↓
Backend matches with pending invoice → marks verified → fires merchant webhook
```

## Build instructions

1. Open this folder in **Android Studio** (Hedgehog 2023.1.1+).
2. Let Gradle sync (downloads dependencies automatically).
3. Connect a phone with **USB debugging enabled** (or use an emulator with
   GPay/PhonePe installed).
4. Click **Run** ▶ — or `./gradlew assembleDebug` for an APK in
   `app/build/outputs/apk/debug/`.

## Setup on the phone

1. Install the APK.
2. Open **Eagle Pay Listener**.
3. Tap **Scan Pairing QR** and scan the QR shown in your Eagle Pay
   dashboard → **Listeners** tab → **Pair Device**.
4. When prompted, **enable Notification Access** for the app
   (Settings → Notifications → Notification access → Eagle Pay Listener).
5. Tap **Start Listener**. The dashboard will show "Online" within seconds.

## Important: keep the app alive

- Disable battery optimisation for the app (the in-app prompt does this).
- Keep the phone charging and connected to Wi-Fi.
- Some OEMs (Xiaomi, Oppo, Vivo) require additional "Auto-start" permission.

## Files of interest

- `NotificationCaptureService.kt` — the listener that intercepts
  notifications and parses them.
- `UpiParser.kt` — regex patterns for various UPI apps and banks.
- `WebhookSender.kt` — posts parsed events to your backend.
- `MainActivity.kt` — pairing UI + status screen.

## Customising parsers

Add new regex patterns in `UpiParser.kt` if your bank's notification format
isn't matched out of the box. Open an issue with the exact notification text
(redacted) and we'll add it.

## Privacy

The app only processes notifications from a hardcoded allow-list of UPI
package names (`com.google.android.apps.nbu.paisa.user`, `net.one97.paytm`,
etc.). It never touches notifications from chat apps, email, etc.
No data is stored on the device — events are POSTed to your webhook and
discarded.
