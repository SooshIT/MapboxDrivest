# Drivest Mapbox Navigation

This repo contains a dual-platform starter for Drivest based on the two Mapbox videos you shared:

- Android: Colchester-focused map setup, token config, route request, multi-route rendering, and route-line tap selection.
- iOS: Colchester-focused turn-by-turn flow using Mapbox Navigation SDK and full-screen `NavigationViewController`.

## Structure

- `android/`: Android Studio project (Kotlin + Mapbox Navigation SDK)
- `ios/DrivestNavigation/`: iOS app scaffold (Swift + XcodeGen + Mapbox Navigation SPM package)

## Free-tier usage notes

- Use one public token for runtime map/navigation requests.
- Use a separate private token only for SDK artifact downloads when needed:
  - Android: optional `MAPBOX_DOWNLOADS_TOKEN` fallback
  - iOS: `.netrc` token with `DOWNLOADS:READ`
- Keep test traffic low and monitor token usage in your Mapbox dashboard.

## Quick start

### Android

1. Replace the placeholder token in `android/app/src/main/res/values/mapbox_access_token.xml` (or copy from `android/mapbox_access_token.xml.example`).
2. Set `MAPBOX_DOWNLOADS_TOKEN` in your environment or `~/.gradle/gradle.properties`.
3. Open `android/` in Android Studio and run the `app` module.
4. For background guidance behavior (foreground service + notification permission), see `android/README.md`.

### iOS

1. Follow `ios/DrivestNavigation/README.md` to configure `.netrc`, token, and generate the Xcode project.
2. Open on macOS and run on iPhone/simulator.
