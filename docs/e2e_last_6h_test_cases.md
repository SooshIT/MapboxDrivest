# Drivest Last-6h E2E Test Cases (Android Emulator)

Date: 2026-02-22  
Environment: `emulator-5554` (`Medium_Phone_API_36.1`, Android API 36), app id `com.drivest.navigation`

## Scope
Validation for changes touched in the last ~6 hours, with emulator E2E checks plus focused unit tests for logic-heavy features.

## E2E Cases

| ID | Area | Steps | Expected | Result |
|---|---|---|---|---|
| TC-01 | Onboarding flow order | Fresh app data -> launch -> complete Consent, Age, Analytics (Skip), Notifications (Skip) | Order preserved; lands on Home; no location prompt during onboarding | PASS |
| TC-02 | Onboarding legal links | Tap `View Terms and Conditions` and `View Privacy Policy` from consent screen | External browser activity opens for both links | PASS |
| TC-03 | Settings legal links | Home -> Settings -> Legal -> tap `Terms and Conditions`, `Privacy Policy`, `FAQ` | External browser activity opens for all 3 links | PASS |
| TC-04 | Theory quiz next navigation | Home -> Theory -> Quick Quiz -> choose answer -> Submit -> Next | Question progress advances (e.g., `Question 1/10` -> `Question 2/10`) | PASS |
| TC-05 | Export drive log fallback | Settings -> Support -> `Export Drive Log` with no completed drive summary | Share flow opens using fallback developer snapshot export | PASS |
| TC-06 | Build/install sanity | `:app:assembleDebug` + `:app:installDebug` | Debug APK builds and installs to emulator | PASS |

## Focused Unit Test Cases

| ID | Test Class | Coverage | Result |
|---|---|---|---|
| UT-01 | `HazardFetchTriggerPolicyTest` | refresh on session start, centre change, >5km movement | PASS |
| UT-02 | `SpeedCameraCacheStoreTest` | 24h TTL, route-scoped speed camera cache behavior | PASS |
| UT-03 | `PromptEngineDistanceGatingTest` | bus stop / traffic lights / speed camera / mini roundabout distance triggers and dedupe/reset | PASS |
| UT-04 | `SpeedLimitNormalizerTest` | UK rounding/snap behavior | PASS |
| UT-05 | `TheoryQuizFlowTest` + `TheoryColorContrastTest` | quiz progression logic + readability threshold checks | PASS |
| UT-06 | `PuckStabilityDeciderTest` | puck jitter/flicker dampening decisions | PASS |
| UT-07 | `LowStressToggleCoordinatorTest` | low-stress toggle stable state transitions | PASS |
| UT-08 | `StressAdjustmentPolicyTest` | stress reroute threshold + cooldown rules | PASS |
| UT-09 | `PracticeFlowDecisionsTest` | practice route phase transitions / start decisions | PASS |
| UT-10 | `NoEntryRestrictionGuardTest` | no-entry restriction guard logic | PASS |
| UT-11 | `HazardVoiceControllerTest` + `SpeechBudgetEnforcerTest` | voice behavior and speech budget enforcement | PASS |
| UT-12 | `LegalConstantsTest` | legal URL/email/version constants validity | PASS |

## Backend Smoke

| ID | Area | Command | Result |
|---|---|---|---|
| BE-01 | backend smoke | `npm test -- --runInBand` (in `backend`) | PASS (8/8 tests) |

## Commands Executed

```powershell
# Android
cd android
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:installDebug --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.drivest.navigation.hazards.SpeedCameraCacheStoreTest" --tests "com.drivest.navigation.session.HazardFetchTriggerPolicyTest" --tests "com.drivest.navigation.prompts.PromptEngineDistanceGatingTest" --tests "com.drivest.navigation.legal.LegalConstantsTest" --tests "com.drivest.navigation.theory.TheoryQuizFlowTest" --tests "com.drivest.navigation.theory.TheoryColorContrastTest" --tests "com.drivest.navigation.speed.SpeedLimitNormalizerTest" --no-daemon --console=plain --rerun-tasks
.\gradlew.bat :app:testDebugUnitTest --tests "com.drivest.navigation.camera.PuckStabilityDeciderTest" --tests "com.drivest.navigation.LowStressToggleCoordinatorTest" --tests "com.drivest.navigation.intelligence.StressAdjustmentPolicyTest" --tests "com.drivest.navigation.practice.PracticeFlowDecisionsTest" --tests "com.drivest.navigation.restrictions.NoEntryRestrictionGuardTest" --tests "com.drivest.navigation.prompts.HazardVoiceControllerTest" --tests "com.drivest.navigation.prompts.SpeechBudgetEnforcerTest" --no-daemon --console=plain --rerun-tasks

# Backend
cd ..\backend
npm test -- --runInBand
```

## Notes

- No source-code fixes were required during this pass; executed cases were green.
- Emulator navigation-drive physics scenarios (real call interruption, real vehicle movement) remain best validated on a physical device run.
