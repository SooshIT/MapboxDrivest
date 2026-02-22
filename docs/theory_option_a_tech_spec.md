# Theory Option A Technical Spec (Android Native)

## 1. Architecture
- Module namespace: `com.drivest.navigation.theory`.
- Pattern: Activity + service/repository layering to match existing app architecture.
- Offline-first:
  - Theory content pack loaded from app assets.
  - Progress persisted in DataStore Preferences.
  - No network fetch required in v1.
- Existing app integration:
  - Home tile in `HomeActivity`.
  - Route-linked recommendations from session summary signals.
  - Telemetry routed through existing `TelemetryRepository` consent gate.

## 2. Folder Structure
- `android/app/src/main/java/com/drivest/navigation/theory/models/`
- `android/app/src/main/java/com/drivest/navigation/theory/content/`
- `android/app/src/main/java/com/drivest/navigation/theory/storage/`
- `android/app/src/main/java/com/drivest/navigation/theory/services/`
- `android/app/src/main/java/com/drivest/navigation/theory/screens/`
- `android/app/src/main/java/com/drivest/navigation/theory/navigation/`
- `android/app/src/main/res/layout/` (theory activity layouts)
- `android/app/src/main/assets/theory/theory_pack_v1.json`
- `android/app/src/test/java/com/drivest/navigation/theory/` (unit tests)

## 3. Data Schema

### 3.1 Content pack schema (`theory_pack_v1.json`)
- Root:
  - `version: string`
  - `updatedAt: string`
  - `topics: TheoryTopic[]`
  - `lessons: TheoryLesson[]`
  - `signs: TheorySign[]`
  - `questions: TheoryQuestion[]`
- Constraints:
  - unique IDs across each entity group
  - question must reference existing topic
  - lesson must reference existing topic
  - question `correctOptionId` must exist in options
  - minimum set: 12 topics / 24 lessons / 240 questions

### 3.2 Progress schema (DataStore logical model)
- `completedLessons: Set<String>`
- `topicStats: Map<String, TopicStat>`
  - `attempts`, `correct`, `wrong`, `masteryPercent`
- `wrongQueue: Set<String>`
- `bookmarks: Set<String>`
- `streakDays: Int`
- `lastActiveAtMs: Long`
- `lastRouteTagSnapshot: RouteTagSnapshot`
  - `tags: List<String>`
  - `centreId: String?`
  - `routeId: String?`
  - `recordedAtMs: Long`

## 4. Offline Strategy
- Parse and validate pack once via loader service.
- Cache in-memory in singleton-like provider per process.
- If parsing fails:
  - return empty safe model
  - UI shows fallback empty states, no crash.
- All quiz/mock execution uses local in-memory questions.
- All user state updates commit through DataStore repository.

## 5. Navigation Wiring
- New Activities:
  - `TheoryHomeActivity`
  - `TheoryTopicListActivity`
  - `TheoryTopicDetailActivity`
  - `TheoryLessonActivity`
  - `TheoryQuizActivity`
  - `TheoryMockTestActivity`
  - `TheoryMockResultsActivity`
  - `TheoryBookmarksActivity`
  - `TheoryWrongAnswersActivity`
  - `TheorySettingsActivity`
- Home integration:
  - add `Theory` tile and progress badge to `activity_home.xml`.
  - start `TheoryHomeActivity` on click.
- Route summary integration:
  - show “Theory to revise for this route” cards in `SessionReportActivity`.
  - card click deep-links to topic detail or quiz.

## 6. Deep Linking
- Internal intent extras:
  - `theory_topic_id`
  - `theory_lesson_id`
  - `theory_quiz_topic_id`
  - `theory_entry_source` (`home`, `route_summary`, `recommendation`)
- Optional URI deep links (for future):
  - `drivest://theory/topic/{topicId}`
  - `drivest://theory/lesson/{lessonId}`
  - `drivest://theory/quiz/{topicId}`

## 7. Analytics Events
- Emitted through `TelemetryRepository.sendEvent(TelemetryEvent.App(...))`.
- Event types:
  - `theory_open`
  - `theory_lesson_start`
  - `theory_lesson_complete`
  - `theory_quiz_complete`
  - `theory_mock_complete`
  - `theory_route_card_click`
- Respect existing telemetry policy:
  - consent false => blocked by minimal mode (except session summary behavior).

## 8. Feature Flag
- `THEORY_MODULE_ENABLED` in a dedicated config object.
- Default:
  - `true` in debug builds
  - `configurable` in release via build config field/constant.
- Home tile and theory entry points must honor flag.

## 9. Performance Constraints
- No heavy JSON parsing on Home render path.
- Parse pack lazily when entering Theory module, not at app launch.
- Home badge uses lightweight DataStore flow and computed readiness only.
- Recommendation generation uses stored last-route tags, not route recomputation.

## 10. Compliance and Content Policy
- No DVSA licensed question bank content.
- No official hazard perception clips.
- Include Theory settings disclaimer:
  - “Drivest Theory is revision support. It is not affiliated with DVSA.”
- Include attribution section for OGL/public guidance where referenced.

## 11. Implementation Notes
- Initial spec created before code changes.
- This section will be appended after each major implementation step with exact file paths and behavior changes.

### Step A: Foundation implemented
- Feature flag and build config:
  - `android/app/build.gradle`
  - `android/app/src/main/java/com/drivest/navigation/theory/TheoryFeatureFlags.kt`
- Theory core models and content loader/validation:
  - `android/app/src/main/java/com/drivest/navigation/theory/models/TheoryTypes.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/content/TheorySchemaValidator.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/content/TheoryPackLoader.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/content/TheoryReadiness.kt`
  - `android/app/src/main/assets/theory/theory_pack_v1.json`
- Theory progress persistence:
  - `android/app/src/main/java/com/drivest/navigation/theory/storage/TheoryProgressStore.kt`
- Route-tag mapping service:
  - `android/app/src/main/java/com/drivest/navigation/theory/services/MapRouteTagsToTheoryTopics.kt`
- Added unit tests:
  - `android/app/src/test/java/com/drivest/navigation/theory/TheorySchemaValidationTest.kt`
  - `android/app/src/test/java/com/drivest/navigation/theory/RouteTagMappingTest.kt`
  - `android/app/src/test/java/com/drivest/navigation/theory/TheoryReadinessScoreTest.kt`

### Step B: Theory UI and navigation integration
- Home tile and progress badge wiring:
  - `android/app/src/main/res/layout/activity_home.xml`
  - `android/app/src/main/java/com/drivest/navigation/HomeActivity.kt`
- New theory screens and routes:
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryHomeActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryTopicListActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryTopicDetailActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryLessonActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryQuizActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryMockTestActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryMockResultsActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryBookmarksActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheoryWrongAnswersActivity.kt`
  - `android/app/src/main/java/com/drivest/navigation/theory/screens/TheorySettingsActivity.kt`
  - `android/app/src/main/AndroidManifest.xml`
- Theory layouts:
  - `android/app/src/main/res/layout/activity_theory_home.xml`
  - `android/app/src/main/res/layout/activity_theory_topic_list.xml`
  - `android/app/src/main/res/layout/activity_theory_topic_detail.xml`
  - `android/app/src/main/res/layout/activity_theory_lesson.xml`
  - `android/app/src/main/res/layout/activity_theory_quiz.xml`
  - `android/app/src/main/res/layout/activity_theory_mock_test.xml`
  - `android/app/src/main/res/layout/activity_theory_mock_results.xml`
  - `android/app/src/main/res/layout/activity_theory_bookmarks.xml`
  - `android/app/src/main/res/layout/activity_theory_wrong_answers.xml`
  - `android/app/src/main/res/layout/activity_theory_settings.xml`
  - `android/app/src/main/res/values/strings.xml`

### Step C: Route-linked recommendation wiring
- Persist last-route tag snapshot when session summary is produced:
  - `android/app/src/main/java/com/drivest/navigation/MainActivity.kt`
- Route summary “Theory to revise for this route” card section:
  - `android/app/src/main/java/com/drivest/navigation/SessionReportActivity.kt`
  - `android/app/src/main/res/layout/activity_session_report.xml`
