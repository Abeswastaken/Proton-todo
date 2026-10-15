# Proton To-Do

A personal Android to-do and events app that works alongside the Proton suite.

- Tasks and events, with optional due dates, repeating events and reminders.
- Reminders arrive as phone notifications and as emails sent through Proton SMTP from your own domain.
- Dated items are sent to Proton Calendar as invites, so they show up on every device.
- The last reminder for each item has to be acknowledged by passing a short challenge. There is no snooze.

The full plan lives in the project's spec. This repo holds no personal data: email addresses and the SMTP token are entered in the app's Settings on the phone.

## Installing

1. Open the latest release on GitHub and download the `.apk` file on your phone.
2. Tap it and allow installs from your browser or file manager if Android asks.
3. For later versions, tap the newer APK and choose **Update**. Don't uninstall first: updating keeps your data.

Settings also has **Export** and **Import**, for moving to a new phone or restoring after a reinstall.

## Building

Every push to `main` builds a debug APK in GitHub Actions (see the **Build** workflow's artifacts).

Signed releases come from pushing a tag such as `v0.1.0`. They need these repository secrets:

| Secret | What it is |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | The release keystore file, base64-encoded |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Keep a copy of the keystore somewhere safe. Android only accepts updates signed with the same key.
