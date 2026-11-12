# KSEB SMS Forwarder

A small Android app intended for sideloading on a personal Android phone. It forwards incoming SMS only when the sender ID contains `KSEBIT` (case-insensitive).

## Battery and privacy design

- Uses Android's `SMS_RECEIVED` broadcast; no polling, alarms, foreground service, or continuously running process.
- No internet permission, analytics, cloud account, or message history.
- Stores only the recipient number and enabled/disabled setting in private app preferences.
- Reassembles multipart incoming SMS and sends the complete message as one SMS or multipart SMS.
- Does not request exemption from Android battery optimization.

## Build APK using GitHub Actions from a phone

1. Create a new GitHub repository.
2. Upload the contents of this folder to the repository root (keep the `.github/workflows` folder).
3. Open the repository's **Actions** tab.
4. Select **Build KSEB Forwarder APK** and run the workflow, or push the files to `main`.
5. Open the completed workflow run and download the artifact named `kseb-sms-forwarder-debug-apk`.
6. Extract the APK and install it on your Pixel 9.

## First-time setup

1. Open KSEB SMS Forwarder.
2. Enter the recipient's mobile number.
3. Enable Automatic forwarding and tap Save settings.
4. Grant both **Receive SMS** and **Send SMS** permissions.
5. Test with a genuine KSEB message and confirm that the destination phone receives it.

## Important limitations

- This is a personal-use sideloaded app, not a Play Store app. SMS permissions are sensitive and may be restricted by app stores.
- The phone needs an active SIM with outgoing SMS capability and sufficient SMS balance/plan.
- Carrier charges may apply to forwarded messages.
- Dual-SIM devices may use Android's current default SMS subscription.
- Android/OEM power management, reboot state, SIM availability, and OS updates can affect delivery. Test after installation, after reboot, and with the screen locked.
- SMS forwarding is not guaranteed to be instantaneous or delivered; do not use it as the only channel for urgent or safety-critical notices.
- Sender matching is based on the visible originating address containing `KSEBIT`. If a KSEB message arrives with a different sender format, this filter will intentionally not forward it.
- The app does not deduplicate messages; it forwards each matching SMS_RECEIVED event it receives.
- The app does not retain incoming message contents. Android and the mobile carrier may retain SMS according to their own policies.

## Permissions

- `RECEIVE_SMS`: detect incoming messages.
- `SEND_SMS`: send the forwarded message.

No contacts, location, microphone, camera, or internet permissions are requested.
