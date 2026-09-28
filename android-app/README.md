# Android app

This folder turns Remind Me There into an Android APK. The website files at the
top of the repo are copied inside the app, and the `android` folder adds the
phone-only parts: the contact picker and the pop-up when someone on your Calls
list rings (`CallerPlugin`, `CallerScreeningService`, `CallerPopup`).

- Every push to `main` runs `.github/workflows/android-apk.yml`, which builds a
  signed APK and publishes it as a GitHub Release. The newest APK is always at:
  https://github.com/SpectrumTechEngine/remindmethere/releases/latest/download/remind-me-there.apk
- People who already have the app need to download it again to get a new
  version. It installs over the old one and keeps their data.

The signing key is NOT in this repo. It lives in GitHub secrets
(ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD) and in your backed-up
keys folder.
