# DRIVE TV — Android TV dashboard framework (prototype)

This is an Android Studio project for a full-screen, TV-remote-friendly dashboard. It wraps local HTML/CSS/JS in Android WebView, exposes a small native bridge for opening installed apps and URLs, and includes a local Wi-Fi upload page for transferring an audio file from an iPad to the TV.

## Features in this prototype
- Landscape, full-screen home dashboard.
- Built-in starter shortcuts for YouTube, Prime Video, Hotstar, Sony LIV, Netflix, KissKH and Flightradar24.
- Add custom website/app shortcuts; stored in WebView localStorage.
- Native launch-intent attempt for known Android TV app package names; falls back to opening the website.
- CarPlay-inspired section that receives one audio file from an iPad over the same Wi-Fi network and plays it locally.
- Local HTML assets are bundled; internet is only needed for remote artwork and the websites themselves.

## Important limitations
- This is a starter framework, not a signed release APK. Build it with Android Gradle Plugin 8.7.3 and JDK 17.
- Music transfer is a file transfer, not AirPlay, screen mirroring, Apple Music streaming, or background audio synchronisation. DRM-protected tracks cannot be exported from Apple Music as ordinary files.
- The receiver server listens on port 8787 on the TV's network interface and currently has no pairing/PIN. Use only on a trusted home network, and stop/exit the app when not using transfers. Before wider use, add a one-time pairing code, upload size/type checks and a stronger HTTP parser.
- Websites may block embedding or require sign-in, DRM, cookies, or a newer WebView. The prototype opens external websites through Android intents rather than forcing all sites into the embedded view.
- The current UI uses remote Unsplash artwork URLs. Replace with local licensed assets for a fully offline/polished release.
- This app does not replace the RiverOS system launcher and does not auto-start at boot yet.
- Verify the exact Android version, ABI and WebView implementation on the TV before release.

## Build
1. Open this folder in Android Studio on a computer, or push it to GitHub and use the included GitHub Actions workflow.
2. Use JDK 17.
3. Run `gradle assembleDebug` (requires Gradle installed) or use Android Studio's Gradle wrapper generation.
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`.
5. Transfer the APK to the TV and install using the TV's supported sideload flow.

## GitHub Actions
The workflow `.github/workflows/build-apk.yml` installs JDK 17 and Gradle, then builds a debug APK. Download the artifact named `drive-tv-debug-apk` from the workflow run.

## iPad audio transfer
1. Install and launch DRIVE TV on the TV.
2. In the dashboard's CarPlay section, read the local URL, normally `http://TV_IP:8787/`.
3. Open that URL in Safari on the iPad (same Wi-Fi).
4. Choose a non-DRM audio file in Files and tap Send to TV.
5. Return to the TV and press Play.
