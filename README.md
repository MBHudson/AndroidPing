# PingViz — Android app

Native Android (Kotlin) app that pings one or more targets (hostname, IP, or domain)
and graphs round-trip ping times live on a line graph, with per-target color, custom
name, optional custom port, and HTTP/HTTPS transport selection.

## Build

Toolchain (installed once on this machine):

- JDK 17: `/usr/lib/jvm/java-17-openjdk-amd64`
- Android SDK: `ANDROID_HOME=/opt/android-sdk` (platform-tools, platforms;android-34,
  build-tools;34.0.0). NOTE: the SDK lives on `/opt` (the large overlay), NOT in
  `/home` (only 300M). Do not move it.
- Gradle: `/opt/gradle/gradle-8.7/bin/gradle` (or `./gradlew`). Dependency + build
  caches live in `GRADLE_USER_HOME=/opt/gradle-home` (also on `/opt`).
- `gradle.properties` pins `org.gradle.jvmargs=-Xmx1280m`, `workers.max=2`,
  `kotlin.daemon.jvmargs=-Xmx768m` to stay within the machine's ~3.9GB RAM.

Build the debug APK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/opt/gradle-home
cd /home/team/shared/pingviz
/opt/gradle/gradle-8.7/bin/gradle assembleDebug --no-daemon --console=plain
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
A copy is kept at `PingViz-debug.apk` (project root and `/home/team/shared/`).

## Features implemented

1. **Target manager** (MainActivity + AddTargetActivity): "+" button adds multiple
   targets. Each target has a name (defaults to host if blank), host/IP/domain,
   probe method (ICMP / HTTP / HTTPS), optional custom port (defaults to 80/443 for
   HTTP(S); blank port = default), and a 10-color palette picker. Targets are listed
   with a color dot; tap to edit, "Remove" to delete.
2. **Live line graph** (`LiveChartView`, hand-rolled Canvas, no chart library): one
   colored line per target over a rolling ~90s window, with gridlines, ms y-axis, and
   a color-coded legend label near each line. Unreachable samples (rtt = -1) break
   the line (gap).
3. **Settings** (SettingsActivity): ping interval (seconds, default 1), theme
   (System default / Light / Dark), alarm on/off, and a Save button. Targets and
   settings persist in SharedPreferences.
4. **Alarm alert toggle**: when enabled, a beep (ToneGenerator) plays when any
   target becomes unreachable.

## Ping method / Android ICMP constraint

- **ICMP**: raw ICMP_ECHO needs a privileged (root) socket that a normal Android app
  does not have. We use `InetAddress.isReachable()` for the ICMP option — a
  best-effort ICMP echo where the platform permits it, falling back to a TCP probe
  otherwise. It is allowed on any device and safely times out.
- **HTTP / HTTPS**: a real HTTP HEAD round trip is timed.

Requires only the `INTERNET` permission.

## Notes

- Min SDK 24, target/compile SDK 34. Dependencies: appcompat, material, core-ktx.
- Do NOT build the signed release APK here — that's a later task with a keystore.
