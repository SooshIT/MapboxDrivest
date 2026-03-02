# UI Repo Audit

## UI Stack Summary
- Android UI framework: XML layouts + `AppCompatActivity` + ViewBinding (`buildFeatures.viewBinding = true`).
- Android navigation: Activity + Intent based flow (no Jetpack Navigation Component detected).
- Android styling/theming: Material Components + XML themes/styles in `android/app/src/main/res/values/themes.xml`, colors in `android/app/src/main/res/values/colors.xml`, dimensions in `android/app/src/main/res/values/dimens.xml`, custom attrs in `android/app/src/main/res/values/attrs.xml`.
- iOS UI framework: UIKit, programmatic layout (no Storyboards/SwiftUI detected).
- iOS navigation: `UINavigationController` push/pop + modal presentation (see `HomeViewController.swift`, `NavigationSessionViewController.swift`).
- iOS styling/theming: system colors + ad-hoc `UIColor` values in view controllers; no centralized theme/tokens found.

## Screen Inventory (File Paths)
### Android (Activities + Layouts)
- Home: `android/app/src/main/java/com/drivest/navigation/HomeActivity.kt` + `android/app/src/main/res/layout/activity_home.xml`
- Settings: `android/app/src/main/java/com/drivest/navigation/SettingsActivity.kt` + `android/app/src/main/res/layout/activity_settings.xml`
- About: `android/app/src/main/java/com/drivest/navigation/AboutDrivestActivity.kt` + `android/app/src/main/res/layout/activity_about_drivest.xml`
- Contact Support: `android/app/src/main/java/com/drivest/navigation/ContactSupportActivity.kt` + `android/app/src/main/res/layout/activity_contact_support.xml`
- Data Rights: `android/app/src/main/java/com/drivest/navigation/DataRightsActivity.kt` + `android/app/src/main/res/layout/activity_data_rights.xml`
- Content Accuracy: `android/app/src/main/java/com/drivest/navigation/ContentAccuracyActivity.kt` + `android/app/src/main/res/layout/activity_content_accuracy.xml`
- Service Availability: `android/app/src/main/java/com/drivest/navigation/ServiceAvailabilityActivity.kt` + `android/app/src/main/res/layout/activity_service_availability.xml`
- Payments & Subscriptions: `android/app/src/main/java/com/drivest/navigation/PaymentsSubscriptionsActivity.kt` + `android/app/src/main/res/layout/activity_payments_subscriptions.xml`
- Paywall: `android/app/src/main/java/com/drivest/navigation/PaywallActivity.kt` + `android/app/src/main/res/layout/activity_paywall.xml`
- Splash: `android/app/src/main/java/com/drivest/navigation/SplashActivity.kt` + `android/app/src/main/res/layout/activity_splash.xml`
- Onboarding consent: `android/app/src/main/java/com/drivest/navigation/OnboardingConsentActivity.kt` + `android/app/src/main/res/layout/activity_onboarding_consent.xml`
- Age requirement: `android/app/src/main/java/com/drivest/navigation/AgeRequirementActivity.kt` + `android/app/src/main/res/layout/activity_age_requirement.xml`
- Analytics consent: `android/app/src/main/java/com/drivest/navigation/AnalyticsConsentActivity.kt` + `android/app/src/main/res/layout/activity_analytics_consent.xml`
- Notifications consent: `android/app/src/main/java/com/drivest/navigation/NotificationsConsentActivity.kt` + `android/app/src/main/res/layout/activity_notifications_consent.xml`
- Safety notice: `android/app/src/main/java/com/drivest/navigation/SafetyNoticeActivity.kt` + `android/app/src/main/res/layout/activity_safety_notice.xml`
- Practice entry: `android/app/src/main/java/com/drivest/navigation/PracticeEntryActivity.kt` + `android/app/src/main/res/layout/activity_practice_entry.xml`
- Centre picker: `android/app/src/main/java/com/drivest/navigation/CentrePickerActivity.kt` + `android/app/src/main/res/layout/activity_centre_picker.xml`
- Practice routes list: `android/app/src/main/java/com/drivest/navigation/PracticeRoutesActivity.kt` + `android/app/src/main/res/layout/activity_practice_routes.xml`
- Navigation entry: `android/app/src/main/java/com/drivest/navigation/NavigationEntryActivity.kt` + `android/app/src/main/res/layout/activity_navigation_entry.xml`
- Destination search: `android/app/src/main/java/com/drivest/navigation/DestinationSearchActivity.kt` + `android/app/src/main/res/layout/activity_destination_search.xml`
- Navigation/Practice map session: `android/app/src/main/java/com/drivest/navigation/MainActivity.kt` + `android/app/src/main/res/layout/activity_main.xml`
- Highway Code: `android/app/src/main/java/com/drivest/navigation/highwaycode/HighwayCodeActivity.kt` + `android/app/src/main/res/layout/activity_highway_code.xml`
- Traffic Signs: `android/app/src/main/java/com/drivest/navigation/TrafficSignsActivity.kt` + `android/app/src/main/res/layout/activity_traffic_signs.xml`
- Fines & Penalties Quiz hub: `android/app/src/main/java/com/drivest/navigation/quiz/ui/QuizHubActivity.kt` + `android/app/src/main/res/layout/activity_quiz_hub.xml`
- Quiz play: `android/app/src/main/java/com/drivest/navigation/quiz/ui/QuizPlayActivity.kt` + `android/app/src/main/res/layout/activity_quiz_play.xml`
- Quiz scoreboard: `android/app/src/main/java/com/drivest/navigation/quiz/ui/QuizScoreboardActivity.kt` + `android/app/src/main/res/layout/activity_quiz_scoreboard.xml`
- Quiz error: `android/app/src/main/java/com/drivest/navigation/quiz/ui/QuizErrorActivity.kt` + `android/app/src/main/res/layout/activity_quiz_error.xml`
- Analytics dashboard: `android/app/src/main/java/com/drivest/navigation/AnalyticsDashboardActivity.kt` + `android/app/src/main/res/layout/activity_analytics_dashboard.xml`
- Analytics detail: `android/app/src/main/java/com/drivest/navigation/AnalyticsDetailActivity.kt` + `android/app/src/main/res/layout/activity_analytics_detail.xml`
- Theory home: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryHomeActivity.kt` + `android/app/src/main/res/layout/activity_theory_home.xml`
- Theory quiz: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryQuizActivity.kt` + `android/app/src/main/res/layout/activity_theory_quiz.xml`
- Theory mock test: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryMockTestActivity.kt` + `android/app/src/main/res/layout/activity_theory_mock_test.xml`
- Theory mock results: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryMockResultsActivity.kt` + `android/app/src/main/res/layout/activity_theory_mock_results.xml`
- Theory bookmarks: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryBookmarksActivity.kt` + `android/app/src/main/res/layout/activity_theory_bookmarks.xml`
- Theory wrong answers: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryWrongAnswersActivity.kt` + `android/app/src/main/res/layout/activity_theory_wrong_answers.xml`
- Theory topic list: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryTopicListActivity.kt` + `android/app/src/main/res/layout/activity_theory_topic_list.xml`
- Theory topic detail: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryTopicDetailActivity.kt` + `android/app/src/main/res/layout/activity_theory_topic_detail.xml`
- Theory lesson: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryLessonActivity.kt` + `android/app/src/main/res/layout/activity_theory_lesson.xml`
- Theory settings: `android/app/src/main/java/com/drivest/navigation/theory/screens/TheorySettingsActivity.kt` + `android/app/src/main/res/layout/activity_theory_settings.xml`
- Session report: `android/app/src/main/java/com/drivest/navigation/SessionReportActivity.kt` + `android/app/src/main/res/layout/activity_session_report.xml`
- Debug session: `android/app/src/main/java/com/drivest/navigation/DebugSessionActivity.kt` + `android/app/src/main/res/layout/activity_debug_session.xml`

### iOS (View Controllers)
- Home: `ios/DrivestNavigation/Sources/HomeViewController.swift`
- Settings: `ios/DrivestNavigation/Sources/SettingsViewController.swift`
- Practice centre picker: `ios/DrivestNavigation/Sources/CentrePickerViewController.swift`
- Practice route list: `ios/DrivestNavigation/Sources/PracticeRouteListViewController.swift`
- Navigation destination search: `ios/DrivestNavigation/Sources/DestinationSearchViewController.swift`
- Navigation session/preview: `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`
- Debug parity: `ios/DrivestNavigation/Sources/DebugParityViewController.swift`

## Component Inventory (File Paths)
### Buttons
- Android Material buttons used across layouts (examples): `android/app/src/main/res/layout/activity_home.xml`, `android/app/src/main/res/layout/activity_highway_code.xml`, `android/app/src/main/res/layout/activity_quiz_play.xml`, `android/app/src/main/res/layout/activity_settings.xml`.
- Programmatic Material buttons: `android/app/src/main/java/com/drivest/navigation/SessionReportActivity.kt`, `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryTopicListActivity.kt`.
- iOS `UIButton` usage: `ios/DrivestNavigation/Sources/HomeViewController.swift`, `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`, `ios/DrivestNavigation/Sources/SettingsViewController.swift`.

### Chips
- Android `Chip` / `ChipGroup`: `android/app/src/main/java/com/drivest/navigation/TrafficSignsActivity.kt`, `android/app/src/main/res/layout/activity_highway_code.xml`, `android/app/src/main/res/layout/activity_traffic_signs.xml`, `android/app/src/main/java/com/drivest/navigation/quiz/ui/QuizPlayActivity.kt`.

### Cards / Panels
- `MaterialCardView` panels in map UI: `android/app/src/main/res/layout/activity_main.xml`.
- Analytics cards: `android/app/src/main/res/layout/activity_analytics_dashboard.xml`.
- Theory panels: `android/app/src/main/res/layout/activity_theory_quiz.xml`, `android/app/src/main/res/layout/activity_theory_mock_test.xml`, `android/app/src/main/res/layout/activity_theory_home.xml`.
- Reusable card backgrounds in `android/app/src/main/res/drawable/bg_app_card.xml`, `android/app/src/main/res/drawable/bg_theory_panel.xml`, `android/app/src/main/res/drawable/bg_home_status_card.xml`.

### Gauges
- Custom gauge view: `android/app/src/main/java/com/drivest/navigation/analytics/GaugeView.kt`.
- Used in analytics screens: `android/app/src/main/res/layout/activity_analytics_dashboard.xml`, `android/app/src/main/res/layout/activity_analytics_detail.xml`.

### Sheets
- No bottom sheet implementations detected.

### Toasts / Snackbars
- Toasts used across many screens (examples): `android/app/src/main/java/com/drivest/navigation/TrafficSignsActivity.kt`, `android/app/src/main/java/com/drivest/navigation/SettingsActivity.kt`, `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryQuizActivity.kt`.
- Snackbars used in `android/app/src/main/java/com/drivest/navigation/CentrePickerActivity.kt`, `android/app/src/main/java/com/drivest/navigation/SessionReportActivity.kt`, `android/app/src/main/java/com/drivest/navigation/quiz/ui/QuizHubActivity.kt`.

## Mapbox Inventory
### Android
- MapView creation: `android/app/src/main/res/layout/activity_main.xml` (`<com.mapbox.maps.MapView ... />`).
- MapView usage/mount: `android/app/src/main/java/com/drivest/navigation/MainActivity.kt` via ViewBinding (`binding.mapView`).
- Navigation session lifecycle:
  - `MainActivity.kt` uses `MapboxNavigationApp.setup(...)` and `requireMapboxNavigation(...)` with `onAttached`/`onDetached` to start/stop trip sessions and register/unregister core observers.
  - `android/app/src/main/java/com/drivest/navigation/session/NavigationSessionManager.kt` controls session state, registers `RouteProgressObserver`/`VoiceInstructionsObserver`, and manages start/stop.
- Overlay components:
  - Maneuver view: `activity_main.xml` `<com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView>`.
  - Prompt banner: `activity_main.xml` `MaterialCardView` with `promptBanner*` views.
  - Route progress / preview banners + speedometer: `activity_main.xml`.
- Camera padding logic:
  - Dynamic camera padding and overview camera in `MainActivity.kt` (`dynamicCameraPadding(...)`, `cameraForCoordinates(..., EdgeInsets(...))`).
- Route rendering:
  - Route line API/view: `MainActivity.kt` (`MapboxRouteLineApi`, `MapboxRouteLineView`, `MapboxRouteLineViewOptions`).
  - Route selection/reordering logic in `MainActivity.kt` (uses `routeLineApi.findClosestRoute(...)`, `mapboxNavigation.setNavigationRoutes(...)`).
- Hazard prompt rendering:
  - Prompt evaluation: `NavigationSessionManager.kt` (`evaluatePrompts(...)` uses `PromptEngine` + `HazardRepository`).
  - UI banner + symbol rendering: `MainActivity.kt` (`renderPromptBanner(...)`, bitmap generation helpers, prompt icon caches).
  - Hazard marker rendering on map uses Mapbox annotations in `MainActivity.kt` (`PointAnnotationManager`, `PointAnnotationOptions`).

### iOS
- Mapbox Navigation SDK: `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift` uses `MapboxNavigationProvider`, `MapboxNavigation`, `NavigationViewController` (MapboxNavigationUIKit).
- MapView creation/mount: managed internally by `NavigationViewController`; custom prompt banner is attached to the navigation controller’s view (`attachPromptBanner(on:)`).
- Navigation session lifecycle:
  - Preview route request via `mapboxNavigation.routingProvider().calculateRoutes(...)`.
  - Session start/stop handled in `NavigationSessionViewController` (present/dismiss `NavigationViewController`).
- Hazard prompt rendering: in `NavigationSessionViewController` via `PromptEngine.evaluate(...)` and `showPromptBanner(...)`.

## State Sources Used By UI
### Android
- `androidx.datastore` preferences:
  - `SettingsRepository` (`android/app/src/main/java/com/drivest/navigation/settings/SettingsRepository.kt`)
  - `ConsentRepository` (`android/app/src/main/java/com/drivest/navigation/legal/ConsentRepository.kt`)
  - `DriverProfileRepository` (`android/app/src/main/java/com/drivest/navigation/profile/DriverProfileRepository.kt`)
  - `SubscriptionRepository` (`android/app/src/main/java/com/drivest/navigation/subscription/SubscriptionRepository.kt`)
  - `QuizProgressStore` (`android/app/src/main/java/com/drivest/navigation/quiz/data/QuizProgressStore.kt`)
  - `TheoryProgressStore` (`android/app/src/main/java/com/drivest/navigation/theory/storage/TheoryProgressStore.kt`)
- Kotlin Coroutines Flow / StateFlow used across UI (`MainViewModel.kt`, Settings/consent flows in `SettingsActivity.kt`).
- `SharedPreferences` usage in `DisclaimerManager` (`android/app/src/main/java/com/drivest/navigation/compliance/DisclaimerManager.kt`).

### iOS
- `SettingsStore` backed by `UserDefaults` (`ios/DrivestNavigation/Sources/SettingsStore.swift`).
- Settings change propagation via `NotificationCenter` (`.drivestSettingsChanged`).
- In-memory debug state: `DebugParityStateStore` (`ios/DrivestNavigation/Sources/DebugParityStateStore.swift`).

## Assets Used by UI
### Android
- Drawables (icons/backgrounds): `android/app/src/main/res/drawable/` (e.g., `ic_overview_route.xml`, `ic_settings_gear.xml`, `ic_home_*`, `bg_app_card.xml`, `bg_theory_panel.xml`, `bg_map_control_chip.xml`, `bg_speedometer_waze.xml`).
- Logos: `android/app/src/main/res/drawable/drivest_logo_tight.png`, `drivest_logo_badge.png`, `drivest_logo.png`.
- Launcher icons: `android/app/src/main/res/mipmap-*` + `android/app/src/main/res/drawable/ic_launcher_*`.
- Layout usage references are spread across `android/app/src/main/res/layout/*.xml` (see `activity_home.xml`, `activity_main.xml`, `activity_settings.xml`, `activity_highway_code.xml`, `activity_traffic_signs.xml`).

### iOS
- No `.xcassets` bundles or image assets detected under `ios/DrivestNavigation`.

## Localisation Approach
- Android: string resources in `android/app/src/main/res/values/strings.xml` with locale overrides in `values-de`, `values-es`, `values-fr`, `values-it`, `values-nl`, `values-pl`, `values-pt-rPT`.
- iOS: no `Localizable.strings` or `NSLocalizedString` usage detected.

## Design Tokens / Constants
- Android color tokens: `android/app/src/main/res/values/colors.xml`.
- Android dimension tokens: `android/app/src/main/res/values/dimens.xml`.
- Android themes/styles: `android/app/src/main/res/values/themes.xml` and `android/app/src/main/res/values-night/themes.xml`.
- Android custom attributes: `android/app/src/main/res/values/attrs.xml`.
- iOS: no centralized design token file detected; styling is inline in view controllers.

## UI + Mapbox Dependencies
### Android (`android/app/build.gradle`)
- `com.mapbox.navigationcore:navigation:3.11.6`
- `com.mapbox.navigationcore:ui-components:3.11.6`
- `com.google.android.material:material:1.12.0`
- AndroidX UI libs (`appcompat`, `constraintlayout`, `recyclerview`, `core-ktx`, `activity-ktx`, `lifecycle-*`).

### iOS (`ios/DrivestNavigation/project.yml`)
- `MapboxNavigationCore` (Mapbox Navigation iOS SDK 3.11.4)
- `MapboxNavigationUIKit`
- `MapboxDirections`

## UI Refactor Risks
- `android/app/src/main/java/com/drivest/navigation/MainActivity.kt` is a large, UI-heavy file with direct Mapbox calls, prompt rendering, camera control, and session orchestration. Refactors risk regressions in navigation and hazard logic.
- `android/app/src/main/java/com/drivest/navigation/session/NavigationSessionManager.kt` couples session state, prompt evaluation, and feature fetch logic; UI changes that affect prompts or banners likely touch this logic.
- Multiple Activities directly access repositories and flows (e.g., `SettingsActivity.kt`, `HomeActivity.kt`), which may complicate UI extraction into reusable components.
- iOS `NavigationSessionViewController.swift` mixes UI and Mapbox session logic (route preview, session start, prompt evaluation), increasing risk during UI decomposition.
