# iOS vs Android Parity Sanity Audit (2026-02-22)

## Scope
Code-level parity audit in current workspace (`android/` vs `ios/DrivestNavigation`).

This audit is **not** a macOS runtime verification. iOS runtime/simulator/device validation must still be completed on macOS.

## Headline Result
- **Target requested:** >= 95% same behaviour
- **Current iOS parity (full app, feature-domain based): NOT MET**
- **Estimated parity now (full app): ~30-40%**
- **Estimated parity on core route preview + hazard prompting path only (after patches in this pass): ~65-75%**

Reason: Android app includes many production modules (onboarding/consent, subscription/billing state, feature gating, telemetry policy, legal/support surfaces, theory module, settings 2.0, reports/coaching) that are not present in iOS source yet.

## Evidence Snapshot
- Android source files: `132` (`android/app/src/main/java/com/drivest/navigation`)
- iOS source files: `18` (`ios/DrivestNavigation/Sources`)
- Android UI surfaces in manifest: `33` activities
- iOS UI surfaces currently implemented: `7` view controllers (Home, CentrePicker, PracticeRouteList, DestinationSearch, NavigationSession, Settings, DebugParity)

## Domain-by-domain Parity Matrix

### 1. App launch / root flow
- Android: `SplashActivity` + launcher routing + onboarding redirects + consent gating
- iOS: `SceneDelegate` always roots to `HomeViewController`
- Status: **Missing parity**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/SplashActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/AppFlow.kt`
  - `ios/DrivestNavigation/Sources/SceneDelegate.swift`

### 2. Mapbox token initialization (runtime auth)
- Android: configured via app resources/build config
- iOS: was missing in app startup path in current repo; patched in this pass
- Status: **Partial -> improved** (needs macOS runtime confirmation)
- Files:
  - `ios/DrivestNavigation/Sources/AppDelegate.swift`

### 3. Home screen behaviour (mode/profile/confidence/recommendations/theory tile)
- Android: rich Home screen (driver mode, confidence, recommendation, practice/nav/theory tiles, readiness)
- iOS: simple scaffold with only Practice + Navigation buttons and Settings bar item
- Status: **Missing parity**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/HomeActivity.kt`
  - `ios/DrivestNavigation/Sources/HomeViewController.swift`

### 4. Onboarding consent flow (Terms/Age/Analytics/Notifications)
- Android: implemented and persisted
- iOS: no onboarding controllers/repository
- Status: **Missing parity**
- Android files include:
  - `android/app/src/main/java/com/drivest/navigation/OnboardingConsentActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/AgeRequirementActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/AnalyticsConsentActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/NotificationsConsentActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/legal/ConsentRepository.kt`

### 5. Safety gate before route usage
- Android: implemented (`SafetyNoticeDialog`, persistence in `ConsentRepository`, Settings re-open)
- iOS: only `safetyAcknowledged` bool in `SettingsStore`, no actual gate flow or screen
- Status: **Missing parity**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/SafetyNoticeActivity.kt`
  - `ios/DrivestNavigation/Sources/SettingsStore.swift`

### 6. Centre picker search + centre selection
- Android: implemented with richer UI + offline pack/download indicators
- iOS: basic centre picker implemented with search; search bar was hidden until scroll, patched in this pass
- Status: **Partial**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/CentrePickerActivity.kt`
  - `ios/DrivestNavigation/Sources/CentrePickerViewController.swift`

### 7. Practice route list
- Android: route list + preview integration + offline flags/gating
- iOS: basic route list implemented
- Status: **Partial**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/PracticeRoutesActivity.kt`
  - `ios/DrivestNavigation/Sources/PracticeRouteListViewController.swift`

### 8. Practice route preview/start guidance
- Android: production flow with safety/location/subscription gating and tuned UI
- iOS: preview + start guidance exists, but scaffold UI; preview failure observed in simulator due route request path
- Status: **Partial (high-priority runtime gap patched in this pass)**
- Patch added: waypoint downsampling for practice preview requests
- Files:
  - `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`

### 9. Destination search (Mapbox geocoding)
- Android: destination search + preview routing flow
- iOS: destination search implemented; search bar hidden until scroll (looked blank); patched in this pass
- Status: **Partial -> improved**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/DestinationSearchActivity.kt`
  - `ios/DrivestNavigation/Sources/DestinationSearchViewController.swift`
  - `ios/DrivestNavigation/Sources/Repositories.swift`

### 10. Navigation preview + start guidance
- Android: production UI and route switching behavior
- iOS: preview/start guidance exists, but much simpler UI and missing parity controls/features
- Status: **Partial**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/MainActivity.kt`
  - `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`

### 11. Hazard loading, prompts, and voice
- Android: extensive hazard pipeline + Map overlays + tuned behavior across epics
- iOS: hazards load from bundled assets; prompt engine + hazard voice + voice mode policy exist
- Status: **Partial**
- Files:
  - `ios/DrivestNavigation/Sources/PromptEngine.swift`
  - `ios/DrivestNavigation/Sources/HazardVoiceController.swift`
  - `ios/DrivestNavigation/Sources/HazardVoiceModePolicy.swift`
  - `ios/DrivestNavigation/Sources/Repositories.swift`

### 12. Speech duration enforcement (AG2)
- Android: implemented
- iOS: `SpeechBudgetEnforcer` exists with local deterministic trimming
- Status: **Complete in code (runtime parity still needs macOS validation)**
- Files:
  - `ios/DrivestNavigation/Sources/SpeechBudgetEnforcer.swift`
  - `ios/DrivestNavigation/Tests/SpeechBudgetEnforcerTests.swift`

### 13. Settings screen parity (Settings 2.0)
- Android: Profile / Navigation / Privacy / Legal / Support / Data Rights / export log / subscription info
- iOS: minimal settings (voice + units only) + hidden debug tap
- Status: **Missing parity**
- Files:
  - `android/app/src/main/java/com/drivest/navigation/SettingsActivity.kt`
  - `ios/DrivestNavigation/Sources/SettingsViewController.swift`
  - `ios/DrivestNavigation/Sources/SettingsStore.swift`

### 14. Legal URLs / support / data rights / about / content accuracy / service availability
- Android: implemented screens and browser intents
- iOS: no equivalent screens/constants surfaced
- Status: **Missing parity**

### 15. Consent repository + versioned legal acceptance timestamps
- Android: implemented (`termsAcceptedVersion`, `privacyAcceptedVersion`, timestamps, age, analytics, notifications, safety)
- iOS: not implemented (single booleans in `SettingsStore` only)
- Status: **Missing parity**

### 16. Subscription state machine (tier/expiry/provider)
- Android: implemented `SubscriptionRepository`, models, persistence
- iOS: no subscription repository/state machine
- Status: **Missing parity**
- iOS only has StoreKit billing scaffold product querying stubs

### 17. FeatureAccessManager / gating API
- Android: implemented and used for practice/training/offline gating
- iOS: no equivalent API
- Status: **Missing parity**

### 18. Billing / restore / payments surfaces
- Android: disclosure surfaces + restore + billing wiring/stubs/Google Play path in Android codebase
- iOS: only `StoreKitBillingScaffold.swift` stub (no UI surfaces, no repository integration)
- Status: **Missing parity**

### 19. Analytics consent enforcement and telemetry minimisation (BG)
- Android: consent-driven telemetry policy + repository gating
- iOS: no telemetry repository / policy / event transport layer
- Status: **Missing parity**

### 20. Driver profile + mode lifecycle engine (BH)
- Android: `DriverProfileRepository`, mode suggestions, practical-pass prompt, confidence integration
- iOS: no driver profile repository or lifecycle engine
- Status: **Missing parity**

### 21. Theory module (Option A)
- Android: implemented screens/content/progress/quiz/mock/tests
- iOS: no theory module at all
- Status: **Missing parity**

### 22. Debug parity screen / observer attach-detach logs
- Android: debug session screen + lifecycle diagnostics
- iOS: `DebugParityViewController` + `DebugParityStateStore` with attach/detach assertions/logs
- Status: **Partial to strong** (good foundation present)

### 23. Offline packs / pack version lifecycle / backend fallback controls
- Android: rich pack store, offline download, flags, fallback mode, data freshness warnings
- iOS: bundled assets only, no pack download/cache lifecycle, no offline pack UI
- Status: **Missing parity**

### 24. Reports / coaching / session summaries / exports
- Android: session report, coaching, export drive log support surface
- iOS: no equivalent surfaces/reports/export
- Status: **Missing parity**

## iOS Fixes Applied In This Audit Pass (repo changes)
These are high-impact runtime parity fixes that could be patched without macOS runtime:

1. **Mapbox token initialization at app launch**
- `ios/DrivestNavigation/Sources/AppDelegate.swift`

2. **Token-aware Mapbox navigation provider creation**
- `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`

3. **Practice preview request stability** (downsample route geometry waypoints before Directions request)
- `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`

4. **Remove nav prompt constraint warnings path** (voice mode chip remains source of truth)
- `ios/DrivestNavigation/Sources/NavigationSessionViewController.swift`

5. **Destination search bar visible immediately**
- `ios/DrivestNavigation/Sources/DestinationSearchViewController.swift`

6. **Centre picker search bar visible immediately**
- `ios/DrivestNavigation/Sources/CentrePickerViewController.swift`

7. **Repository async request path warning cleanup**
- `ios/DrivestNavigation/Sources/Repositories.swift`

## What Must Be Verified on macOS (Vivek)
1. Pull latest changes
2. `cd ios/DrivestNavigation`
3. `xcodegen generate`
4. Build/test:
   - `xcodebuild -project DrivestNavigation.xcodeproj -scheme DrivestNavigation -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16' clean build`
   - `xcodebuild -project DrivestNavigation.xcodeproj -scheme DrivestNavigation -destination 'platform=iOS Simulator,name=iPhone 16' test`
5. Runtime logs must show:
   - `[Drivest iOS] mapbox_token_loaded ...`
   - `[Drivest iOS] mapbox_navigation_provider_token_loaded ...`
6. Practice preview must load (no "server returned an empty response")
7. Destination and centre search bars must be visible immediately on screen load
8. Voice mode toggles must not produce nav-bar prompt AutoLayout warnings

## Conclusion
- **Current iOS != Android at 95% parity**
- iOS is a **functional scaffold** for core practice/navigation preview + hazards, but **not** a parity-complete counterpart to the Android production app.
- To reach 95%, iOS needs a dedicated parity implementation sprint covering onboarding/consent, settings 2.0, subscription+gating, legal/support surfaces, telemetry policy, and theory module at minimum.
