# Theory Option A Backlog

## Scope
- Add a first-class offline Theory module in Drivest.
- Keep content original/OGL-attributed where permitted.
- Exclude DVSA licensed question banks and official hazard clips.

## Epic 1: Main Page Integration

### Feature 1.1 Theory tile on Home
- Story TH-1 (3 pts): As a driver, I see a `Theory` tile on Home beside Practice and Navigation.
  - Acceptance:
    - Theory tile appears only when `THEORY_MODULE_ENABLED` is true.
    - Existing Practice/Navigation tiles still function unchanged.
    - Home render time remains stable (<16ms jank spikes unchanged under normal load).

### Feature 1.2 Progress badge + readiness
- Story TH-2 (3 pts): As a learner, I see mastered-topic progress percent and readiness status on the Theory tile.
  - Acceptance:
    - Badge shows mastered topics percentage.
    - Readiness label maps to thresholds (`Building`, `Almost ready`, `Ready`).
    - Values come from local Theory progress store.

### Feature 1.3 Open Theory Home
- Story TH-3 (2 pts): As a learner, tapping Theory opens Theory Home dashboard.
  - Acceptance:
    - Tapping Theory routes to `TheoryHomeActivity`.
    - Back navigation returns to Home.

## Epic 2: Theory Home Dashboard

### Feature 2.1 Continue/weakest/recommendations/actions
- Story TH-4 (5 pts): As a learner, I see continue-learning, weakest-topics, route-linked recommendations, and quick actions.
  - Acceptance:
    - Continue card points to most recent incomplete lesson or quiz topic.
    - Weakest topics list is derived from local topic mastery.
    - “Based on your last route” cards appear when route tag snapshot exists.
    - Quick action buttons: Topic Quiz and Mock Test.

## Epic 3: Content Engine

### Feature 3.1 Models and schema
- Story TH-5 (5 pts): As engineering, we load theory content through strict schema-validated models.
  - Acceptance:
    - Kotlin models for Topic, Lesson, Sign, Question, AnswerOption, Explanation.
    - Schema validator rejects malformed packs.
    - Loader fails gracefully to empty pack + user-safe message.

### Feature 3.2 Bundled pack
- Story TH-6 (8 pts): As a learner, I have bundled offline content v1.
  - Acceptance:
    - Asset file `theory/theory_pack_v1.json`.
    - Includes at least 12 topics, 24 lessons, 240 original questions.
    - Each question has explanation and topic mapping.

## Epic 4: Practice Quiz Engine

### Feature 4.1 Topic quizzes
- Story TH-7 (8 pts): As a learner, I can run 10/20/30 question topic quizzes with instant feedback.
  - Acceptance:
    - Topic and length selection works.
    - Immediate correctness + explanation shown.
    - Attempt results persist.

### Feature 4.2 Bookmarks and wrong queue
- Story TH-8 (5 pts): As a learner, I can bookmark questions and revisit wrong answers.
  - Acceptance:
    - Bookmark toggle persists.
    - Wrong-answer queue persists and is reviewable.

## Epic 5: Mock Test Engine

### Feature 5.1 Timed mock test
- Story TH-9 (8 pts): As a learner, I can take a 50-question timed mock simulation.
  - Acceptance:
    - 50-question mixed-topic test starts and runs offline.
    - Timer visible and stable.
    - App backgrounding pauses timer state safely.

### Feature 5.2 Results and review
- Story TH-10 (5 pts): As a learner, I get score, topic breakdown, and review mode.
  - Acceptance:
    - Result summary shows pass/fail and per-topic performance.
    - Full answer review works from local session state.

## Epic 6: Route-linked Intelligence

### Feature 6.1 Route tag mapping
- Story TH-11 (5 pts): As a learner, route hazard tags map to theory topics.
  - Acceptance:
    - Mapping service supports:
      - `zebra_crossings -> pedestrian_crossings`
      - `traffic_lights -> signals_and_junction_control`
      - `bus_lanes -> bus_lane_rules`
      - `roundabouts -> roundabouts`
      - `school_zones -> speed_limits_and_school_zones`
    - Unknown tags are ignored safely.

### Feature 6.2 Theory cards in dashboard and route summary
- Story TH-12 (5 pts): As a learner, I see “revise this route” theory cards in Theory home and session report.
  - Acceptance:
    - Theory home reads `lastRouteTagSnapshot`.
    - Session report shows mapped theory cards if counts/tags present.
    - Card taps deep link into Theory lesson/topic quiz.

## Epic 7: Progress and Readiness

### Feature 7.1 Progress store
- Story TH-13 (8 pts): As a learner, lesson completion and quiz stats persist offline.
  - Acceptance:
    - Stored: completed lessons, topic stats, wrong queue, bookmarks, streak, last active, last route tag snapshot.
    - Data survives app restarts.

### Feature 7.2 Readiness score
- Story TH-14 (5 pts): As a learner, I see readiness based on consistent thresholds.
  - Acceptance:
    - Readiness computed from mastered topic ratio + recent score trend.
    - Labels:
      - `<40`: Building
      - `40-74`: Almost ready
      - `>=75`: Ready

## Epic 8: Offline and Performance

### Feature 8.1 Offline-first operation
- Story TH-15 (5 pts): As a learner, all Theory content and quizzes work with no network.
  - Acceptance:
    - Content pack is local asset only in v1.
    - No network dependency for lesson/quiz/mock flows.

### Feature 8.2 Home performance protection
- Story TH-16 (3 pts): As a user, Home performance remains stable with Theory tile enabled.
  - Acceptance:
    - Home only reads lightweight progress snapshot flow.
    - No heavy JSON parse on Home thread.

## Epic 9: Compliance and Attribution

### Feature 9.1 Theory settings/legal copy
- Story TH-17 (3 pts): As a user, I can read Theory disclaimer and attribution.
  - Acceptance:
    - “Not affiliated with DVSA” copy in Theory settings.
    - OGL attribution section present where content references public guidance.

## Data Model
- `TheoryTopic`:
  - `id`, `title`, `description`, `tags`, `lessonIds`, `questionIds`.
- `TheoryLesson`:
  - `id`, `topicId`, `title`, `content`, `keyPoints`, `signIds`.
- `TheorySign`:
  - `id`, `topicId`, `name`, `meaning`, `memoryHint`.
- `TheoryQuestion`:
  - `id`, `topicId`, `prompt`, `options[]`, `correctOptionId`, `explanation`, `difficulty`.
- `TheoryAnswerOption`:
  - `id`, `text`.
- `TheoryProgressSnapshot`:
  - `completedLessons`, `topicStats`, `wrongQueue`, `bookmarks`, `streakDays`, `lastActiveAtMs`, `lastRouteTagSnapshot`.

## Storage Keys (DataStore)
- `theory_completed_lessons_csv`
- `theory_topic_stats_json`
- `theory_wrong_queue_csv`
- `theory_bookmarks_csv`
- `theory_streak_days`
- `theory_last_active_ms`
- `theory_last_route_tags_csv`
- `theory_last_route_centre_id`
- `theory_last_route_id`
- `theory_last_route_recorded_ms`

## Telemetry Events
- `theory_open`
- `theory_lesson_start`
- `theory_lesson_complete`
- `theory_quiz_complete` (topicId, score, total, mode)
- `theory_mock_complete` (score, total, durationSec)
- `theory_route_card_click` (tag, topicId, sourceSurface)

## Build Order
1. Discovery + docs
2. Feature flag + content models/schema + loader + pack
3. Progress store + readiness calculator
4. Home tile integration
5. Theory activities (home/topics/lesson/quiz/mock/results/bookmarks/wrong/settings)
6. Route-tag mapping + recommendation surfaces
7. Telemetry events
8. Unit tests + assemble

## Total estimate
- 84 story points.
