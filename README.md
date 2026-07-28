# Wallhavend

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/292773435b4a4e0abc882e5180cbe6bb)](https://app.codacy.com/gh/Attacktive/Wallhavend-android?utm_source=github.com&utm_medium=referral&utm_content=Attacktive/Wallhavend-android&utm_campaign=Badge_Grade)
[![CodeFactor](https://www.codefactor.io/repository/github/attacktive/wallhavend-android/badge)](https://www.codefactor.io/repository/github/attacktive/wallhavend-android)
[![Test](https://github.com/Attacktive/Wallhavend-android/actions/workflows/test.yaml/badge.svg)](https://github.com/Attacktive/Wallhavend-android/actions/workflows/test.yaml)

Android app that automatically rotates wallpapers from [Wallhaven](https://wallhaven.cc).

Supports filtering by category, purity, and aspect ratio. Runs as a foreground service on a configurable schedule (1 min – 24 hr). Requires an API key for NSFW content only.

**Min SDK:** Android 8.0 (API 26)

<a href="https://play.google.com/store/apps/details?id=xyz.attacktive.wallhavend">
	<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" width="200">
</a>

## Sister project

[Weatherd](https://github.com/Attacktive/weatherd) paints a live, procedurally animated weather scene as your wallpaper — same bones, opposite art department.
[Get it on Google Play](https://play.google.com/store/apps/details?id=xyz.attacktive.weatherd).

## Building from source

Requires JDK 17 and the Android SDK ([Android Studio](https://developer.android.com/studio) bundles both).

```sh
git clone https://github.com/Attacktive/Wallhavend-android.git
cd Wallhavend-android
./gradlew assembleDebug
```

Debug builds need no secrets. The Wallhaven API key is entered in the app at runtime (for NSFW content only), and `release.keystore` with `KEYSTORE_PASSWORD` are needed only for release signing.

Run the unit tests with `./gradlew test`.

## Contributing

The codebase follows a formatting style that differs from the IDE defaults — see [CONTRIBUTING.md](CONTRIBUTING.md) before sending a pull request.

---

Wallpapers provided by [Wallhaven](https://wallhaven.cc). All images remain property of their respective uploaders.
