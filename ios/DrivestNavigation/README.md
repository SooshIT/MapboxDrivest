# iOS setup (DrivestNavigation)

This folder contains an iOS app scaffold generated with XcodeGen settings and Mapbox Navigation SDK via Swift Package Manager.

## Prerequisites (macOS)

1. Install Xcode 16+ from the App Store.
2. Install Xcode command-line tools:

```bash
xcode-select --install
```

3. Install XcodeGen:

```bash
brew install xcodegen
```

4. Create a Mapbox downloads token (`DOWNLOADS:READ`) and add it to `~/.netrc`:

```txt
machine api.mapbox.com
  login mapbox
  password YOUR_MAPBOX_DOWNLOADS_READ_TOKEN
```

5. Open `Configs/Base.xcconfig` and set `MBX_ACCESS_TOKEN` to your public Mapbox token (`pk...`).

## Generate project

```bash
cd ios/DrivestNavigation
xcodegen generate
```

## Run in Xcode

1. Open `DrivestNavigation.xcodeproj` in Xcode.
2. Select the `DrivestNavigation` scheme.
3. Choose an iOS simulator (or a connected iPhone).
4. Press `Cmd + R`.

## G2 Smoke Checklist (Strict)

Run this checklist in order for parity validation.

### 1) Clean build

1. In Xcode: `Product` -> `Clean Build Folder` (`Shift + Cmd + K`).
2. Build once without running (`Cmd + B`).
3. Confirm no compile errors and no unresolved deprecation breakages.

### 2) Simulator run

1. Select an iOS simulator (iPhone).
2. Run app (`Cmd + R`).
3. Verify Home screen opens.
4. Start Practice flow:
   - Home -> `Practice` -> centre -> route -> `Start Guidance`.
5. Start Navigation flow:
   - Home -> `Navigation` -> destination search -> pick destination -> `Start Guidance`.
6. Trigger hazard prompt:
   - In Xcode: `Debug` -> `Simulate Location` and drive route progress.
   - Confirm prompt banner appears.
7. Trigger hazard voice modes:
   - Cycle `Voice: All`, `Voice: Alerts`, `Voice: Mute`.
   - Confirm speech behavior changes by mode.
8. Off-route checks:
   - Move simulated route away from guidance path.
   - Confirm off-route state enters and exits.
9. Offline pack behavior:
   - Set `dataSourceMode` to `assetsOnly` in Settings-backed debug state.
   - Confirm route and hazard data still load from bundle.
10. Restore purchases (if applicable):
   - If StoreKit test account or existing purchase path is configured, run restore flow.

### 3) Device run

1. Connect a physical iPhone.
2. Select the device target.
3. Run app (`Cmd + R`).
4. Repeat steps 4-10 from simulator checklist on device.

## Runtime logs

Session and voice logs are printed with `[Drivest iOS]` prefix:

- `session_start`
- `session_stop`
- `voice_mode_changed`
- `loaded_hazards`
- `observer_attach`
- `observer_detach`

## Hidden parity debug screen

1. Open `Settings`.
2. Tap the `Drivest iOS` footer label 5 times.
3. `Parity Debug` screen will open.

Fields shown:

- `dataSourceMode`
- `voiceMode`
- `promptSensitivity`
- `hazardsEnabled`
- `analyticsConsent`
- `safetyAcknowledged`
- `routesPackVersionId`
- `hazardsPackVersionId`
- `lastPromptFired`
- `lastPromptSuppressed`
- `lastOffRouteState`
- `offRouteRawDistanceM`
- `offRouteSmoothedDistanceM`

## Green-run note template

Use this single note format after macOS verification:

```txt
iOS Green Run
- Device: <device model>
- iOS: <version>
- Xcode: <version>
- Tests passed:
  - clean build
  - simulator run
  - device run
  - practice guidance start
  - navigation guidance start
  - hazard prompt trigger
  - voice mode all/alerts/mute
  - off-route enter/exit
  - offline pack use
  - restore purchases (if configured)
```

## Current behavior

- Home flow with `Practice`, `Navigation`, and `Settings`.
- Practice flow: centre picker -> route list -> preview -> start guidance.
- Navigation flow: destination search -> preview -> start guidance.
- Local data loading from bundled `Resources/Data/centres.json` and `Resources/Data/routes/<centreId>/routes.json`.
- Voice mode and units persisted in local settings (`UserDefaults`).
- Visual prompt evaluation and hazard voice controller integrated during active guidance.

## StoreKit scaffold (BF)

- `Sources/StoreKitBillingScaffold.swift` provides a non-invasive StoreKit scaffold API.
- Android billing is fully wired in BF; iOS remains scaffold-only until full StoreKit runtime wiring/validation.
