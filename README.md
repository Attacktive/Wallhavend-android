# Wallhavend

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/292773435b4a4e0abc882e5180cbe6bb)](https://app.codacy.com/gh/Attacktive/Wallhavend-android?utm_source=github.com&utm_medium=referral&utm_content=Attacktive/Wallhavend-android&utm_campaign=Badge_Grade)
[![CodeFactor](https://www.codefactor.io/repository/github/attacktive/wallhavend-android/badge)](https://www.codefactor.io/repository/github/attacktive/wallhavend-android)
[![Test](https://github.com/Attacktive/Wallhavend-android/actions/workflows/test.yaml/badge.svg)](https://github.com/Attacktive/Wallhavend-android/actions/workflows/test.yaml)

Android app that automatically rotates wallpapers from [Wallhaven](https://wallhaven.cc).

Supports filtering by category, purity, and aspect ratio. Schedules wallpaper rotation across device sleep and performs each update as bounded background work. Android may still delay updates while the device is idle, so configured intervals are approximate. Requires an API key for NSFW content only.

**Min SDK:** Android 8.0 (API 26)

<a href="https://play.google.com/store/apps/details?id=xyz.attacktive.wallhavend">
	<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" width="200">
</a>

## Sister project

[Weatherd](https://github.com/Attacktive/weatherd) paints a live, procedurally animated weather scene as your wallpaper — same bones, opposite art department.
[Get it on Google Play](https://play.google.com/store/apps/details?id=xyz.attacktive.weatherd).

## Building from source

Requires JDK 21 and the Android SDK ([Android Studio](https://developer.android.com/studio) bundles both).

```sh
git clone https://github.com/Attacktive/Wallhavend-android.git
cd Wallhavend-android
./gradlew assembleDebug
```

Debug builds need no secrets. The Wallhaven API key is entered in the app at runtime (for NSFW content only), and `release.keystore` with `KEYSTORE_PASSWORD` are needed only for release signing.

Run the unit tests with `./gradlew test`.

## Monitoring

A daily GitHub Actions workflow (`.github/workflows/google-play-vitals.yaml`) runs at `00:17` UTC and supports manual dispatch to monitor Android crashes and ANRs from the Google Play Developer Reporting API.

Requirements and operation:
- The Google Play Developer Reporting API must be enabled in Google Cloud.
- Enable the IAM API and IAM Service Account Credentials API in the service account's Google Cloud project.
- Grant the service account `roles/iam.serviceAccountTokenCreator` on itself, as required by the [authentication action's access-token setup](https://github.com/google-github-actions/auth/tree/7c6bc770dae815cd3e89ee6cdf493a5fab2cc093#inputs-service-account-key-json).
- The service account in `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` must have read-only access (**View app information (read-only)**) in Google Play Console.
- The workflow compares errors against the previous successful run's `play-vitals-snapshot` artifact (retained for 90 days), publishing a Markdown summary to GitHub Actions without sending notifications or opening issues.

## Contributing

The codebase follows a formatting style that differs from the IDE defaults — see [CONTRIBUTING.md](CONTRIBUTING.md) before sending a pull request.

---

Wallpapers provided by [Wallhaven](https://wallhaven.cc). All images remain property of their respective uploaders.
