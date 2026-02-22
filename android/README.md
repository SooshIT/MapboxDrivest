# Android setup

## 1) Public token

Replace `app/src/main/res/values/mapbox_access_token.xml` using `mapbox_access_token.xml.example` in this folder:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="mapbox_access_token">YOUR_PUBLIC_MAPBOX_ACCESS_TOKEN</string>
</resources>
```

## 2) Optional private downloads token (only if dependency resolution fails)

Set one of the following only if Android Studio/Gradle cannot resolve Mapbox artifacts from Maven Central:

- Environment variable: `MAPBOX_DOWNLOADS_TOKEN`
- `~/.gradle/gradle.properties`:

```properties
MAPBOX_DOWNLOADS_TOKEN=YOUR_DOWNLOADS_READ_TOKEN
```

## 3) Run

Open the `android/` folder in Android Studio and run the `app` target.

## Current behavior

- Loads Mapbox navigation style and live location puck.
- Generates 3 Colchester practice routes (loop templates around the test centre).
- Renders the 3 routes together.
- Lets user tap a route line to make it primary.
- Starts replay trip session for deterministic testing.

## Background navigation lifecycle

- While guidance is active, the app starts `NavigationForegroundService` with an ongoing notification.
- This keeps route guidance alive when the app is backgrounded (Home button) or interrupted by a phone call.
- Service stops automatically when guidance stops or completes.

### Permissions

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for route guidance.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` for Android foreground service execution.
- `POST_NOTIFICATIONS` (Android 13+) for visible ongoing notification.

If notification permission is denied, guidance still continues, but notification visibility is reduced by OS policy.

### Manual verification

1. Start a navigation route and begin guidance.
2. Press Home. Confirm the ongoing navigation notification is present.
3. Return to app. Route should still be active without restarting.
4. Start guidance again and take/receive a phone call.
5. End the call and return to app.
6. Confirm navigation session is still active and voice instructions continue on subsequent maneuvers.
