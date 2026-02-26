# Drivest Android — Claude Code Project Notes

## Project Overview
Drivest is an Android navigation app for UK learner drivers built on Mapbox SDK. It overlays OSM-sourced hazard prompts (roundabouts, bus lanes, school zones, speed cameras, etc.) on a live navigation map, with both visual banners and TTS voice alerts.

## Repository Structure
- `android/app/src/main/java/com/drivest/navigation/` — all Kotlin source
- `android/app/src/main/res/` — layouts, drawables, strings (8 locale folders)
- `android/app/src/main/assets/theory/` — theory pack JSON
- `android/app/src/main/assets/traffic_signs/` — traffic sign assets

## Key Architecture

### Prompt Pipeline
1. `OsmFeatureMapper` — maps raw OSM tags → `OsmFeature` (with `confidenceHint`)
2. `PromptEngine` — evaluates features against driver position/speed, produces `PromptEvent`
3. `PromptSpeechTemplates` — converts `PromptEvent` → localized TTS string via `context.getString()`
4. `NavigationSessionManager` — drives the pipeline each location update

**Critical gate:** `PromptEngine.VISUAL_MIN_CONFIDENCE_HINT = 0.60f` — features below this threshold are silently dropped. Each `OsmFeatureType` has a hardcoded confidence in `OsmFeatureMapper.confidenceFor()`.

### Bus Lane Pipeline (implemented Feb 2026)
- `OsmFeatureMapper.confidenceFor(BUS_LANE)` = `0.65f` (was `0.3f` — bug fixed)
- `BusLaneAccessChecker` (new) parses OSM `access:conditional` / `vehicle:conditional` / `motor_vehicle:conditional` tags to determine time-of-day restriction
- `PromptEvent.busLaneRestricted: Boolean?` — `true`=restricted now, `false`=open, `null`=no OSM data (safe default = treated as restricted)
- Restricted bus lanes: priority 4 (hazard); open bus lanes: priority 1 (advisory)

### Localisation
- Runtime locale switching: `AppLanguageManager` + `AppCompatDelegate.setApplicationLocales()`
- 8 locales: `values/` (en), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-nl/`, `values-pl/`, `values-pt-rPT/`
- `PromptSpeechTemplates` uses `context.getString()` — fully localised
- `PromptEngine.messageFor()` — **hardcoded English** (intentionally deferred; finalise English copy first)

### Settings
- `SettingsRepository` / `SettingsModels` — user preferences
- `AppearanceModeManager` — day/night/auto theme switching
- `PromptSensitivity` — controls how aggressively prompts fire

## Devices
- **Emulator:** `emulator-5554` (standard AVD)
- **Physical:** Samsung Galaxy Note 20, model `SM_N980F`, ADB serial `RZ8R70QM7HD`

## Build & Install Commands
```bash
# Build debug APK
cd android && ./gradlew assembleDebug

# Install on emulator
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# Install on physical phone
adb -s RZ8R70QM7HD install -r app/build/outputs/apk/debug/app-debug.apk
```

## ADB Notes (Git Bash)
- Prefix ADB commands that reference device paths with `MSYS_NO_PATHCONV=1` to prevent Git Bash converting `/sdcard/` → Windows paths
- Use `uiautomator dump` + parse bounds from XML for reliable tap coordinates on emulator:
```bash
MSYS_NO_PATHCONV=1 adb -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml
MSYS_NO_PATHCONV=1 adb -s emulator-5554 exec-out cat /sdcard/window_dump.xml
```

## Pending / Deferred Work
- **English banner copy finalisation**: `PromptEngine.messageFor()` has placeholder English strings. User wants to agree on final English copy before adding translations.
- **Translation architecture review**: Deferred until English copy is finalised.

## Key Files Quick Reference
| File | Purpose |
|------|---------|
| `prompts/PromptEngine.kt` | Core prompt logic, trigger distances, priority |
| `prompts/PromptModels.kt` | `PromptEvent`, `PromptType` enums |
| `prompts/PromptSpeechTemplates.kt` | TTS string selection |
| `osm/OsmFeatureMapper.kt` | OSM tag → OsmFeature mapping + confidence |
| `osm/BusLaneAccessChecker.kt` | Time-of-day bus lane restriction parser |
| `session/NavigationSessionManager.kt` | Main navigation session orchestrator |
| `settings/AppLanguageManager.kt` | Runtime locale switching |
| `settings/AppearanceModeManager.kt` | Day/night/auto theme |
| `res/values/strings.xml` | English string resources |
