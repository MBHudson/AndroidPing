# PingTrace — Android App

A focused network-diagnostics utility for Android: enter a host/IP address and a
ping interval (in seconds), and PingTrace pings that server on the chosen
interval while graphing the round-trip times (ms) on a live line chart.

Built with **Kotlin**, **Jetpack Compose**, and **Material 3**.

## Features

- Host/IP and ping-interval input screen
- Background ping loop that runs on the user-chosen interval
- Live line chart of round-trip times that updates as new pings land
- Start/stop controls
- `INTERNET` permission configured

## Tech stack

- **Kotlin** 2.0.21 (with the Jetpack Compose compiler plugin)
- **Jetpack Compose** + **Material 3** (Compose BOM `2024.09.03`)
- **Android Gradle Plugin** 8.7.3
- **Gradle** 8.10.2 (via the wrapper), Kotlin DSL build scripts
- `compileSdk` / `targetSdk` 35, `minSdk` 24, JVM target 17, requires **JDK 17**

## Building

Requirements: **JDK 17** and an Android SDK with `platforms;android-35` and
`build-tools;35.0.0`.

From the project root:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Lightweight verification (configures the project without a full compile):

```bash
./gradlew help
./gradlew tasks
```

## Continuous integration

`.github/workflows/android-ci.yml` runs `./gradlew assembleDebug` on every push
to `main` and on every pull request, and uploads the debug APK as a build
artifact so it can be downloaded directly from the workflow run.

## Project layout

```
├── build.gradle.kts            Top-level build config (plugin declarations)
├── settings.gradle.kts         Gradle settings + dependency repos
├── gradle.properties           Gradle/AndroidX/Jetifier flags
├── .github/workflows/          CI pipeline
├── gradle/
│   ├── libs.versions.toml      Version catalog (single source of versions)
│   └── wrapper/                Gradle wrapper (pins the Gradle version)
├── gradlew / gradlew.bat       Wrapper scripts — use these, not a local Gradle
└── app/
    ├── build.gradle.kts        App module config (SDK versions, deps)
    ├── proguard-rules.pro      Release ProGuard rules
    └── src/main/
        ├── AndroidManifest.xml INTERNET permission + MainActivity declaration
        ├── java/com/pingtrace/app/
        │   ├── MainActivity.kt                 Compose entry point
        │   ├── PingViewModel.kt                State + ping loop logic
        │   ├── pinger/PingService.kt           Background pinger
        │   ├── ui/PingScreen.kt                Main screen + controls
        │   ├── ui/components/PingChart.kt      Live line chart
        │   └── ui/theme/                       Material 3 theme (Color/Theme/Type)
        └── res/                                Launcher icons, strings, themes
```
