# Theory Option A Discovery (Android)

## Platform and stack
- Mobile framework: native Android (Kotlin, Activities, ViewBinding).
- Navigation stack: Activity-based app flow via `AndroidManifest.xml`, launcher in `SplashActivity`, route mode in `MainActivity`.
- Map/navigation SDK: Mapbox Navigation SDK (`com.mapbox.navigationcore:navigation:3.11.6`).
- Storage: Jetpack DataStore Preferences repositories (settings, consent, subscription, driver profile).

## Exact integration paths
- Home screen UI and Practice/Navigation tiles:
  - `android/app/src/main/java/com/drivest/navigation/HomeActivity.kt`
  - `android/app/src/main/res/layout/activity_home.xml`
- Launcher/onboarding/app flow routing:
  - `android/app/src/main/java/com/drivest/navigation/SplashActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/AppFlow.kt`
  - `android/app/src/main/AndroidManifest.xml`
- Existing storage layer:
  - `android/app/src/main/java/com/drivest/navigation/settings/SettingsRepository.kt`
  - `android/app/src/main/java/com/drivest/navigation/legal/ConsentRepository.kt`
  - `android/app/src/main/java/com/drivest/navigation/subscription/SubscriptionRepository.kt`
  - `android/app/src/main/java/com/drivest/navigation/profile/DriverProfileRepository.kt`
- Route intelligence source:
  - `android/app/src/main/java/com/drivest/navigation/intelligence/RouteIntelligenceEngine.kt`
  - `RouteIntelligenceSummary` currently exposes counts for roundabout, traffic signals, zebra, school, bus lane, plus complexity/stress/difficulty.
- Session summary/report render surface:
  - `android/app/src/main/java/com/drivest/navigation/SessionReportActivity.kt`
  - `android/app/src/main/res/layout/activity_session_report.xml`
  - `android/app/src/main/java/com/drivest/navigation/report/SessionSummaryPayload.kt`
- Session-end summary generation and telemetry call sites:
  - `android/app/src/main/java/com/drivest/navigation/MainActivity.kt`
  - `submitSessionSummary(...)` and `openSessionReport(...)`.

## Existing constraints relevant to Theory Option A
- App already has feature/access gating patterns (`FeatureAccessManager`, paywall routing) and privacy-gated telemetry (`TelemetryRepository` + consent policy).
- Build currently has no TypeScript runtime; module must be implemented in Kotlin/Android-native structure.
- Assets are bundled under `android/app/src/main/assets` and loaded offline by repositories/stores.
