import Foundation

struct TheoryTopicStat: Codable {
    let attempts: Int
    let correct: Int
    let wrong: Int
    let masteryPercent: Int
}

struct TheoryRouteTagSnapshot: Codable {
    let tags: [String]
    let centreId: String?
    let routeId: String?
    let recordedAtMs: Int64
}

struct TheoryProgress {
    let completedLessons: Set<String>
    let topicStats: [String: TheoryTopicStat]
    let wrongQueue: Set<String>
    let bookmarks: Set<String>
    let streakDays: Int
    let lastActiveAtMs: Int64
    let lastRouteTagSnapshot: TheoryRouteTagSnapshot?
}

@MainActor
final class TheoryProgressStore {
    static let shared = TheoryProgressStore()

    private enum Keys {
        static let completedLessonsCSV = "theory_completed_lessons_csv"
        static let topicStatsJSON = "theory_topic_stats_json"
        static let wrongQueueCSV = "theory_wrong_queue_csv"
        static let bookmarksCSV = "theory_bookmarks_csv"
        static let streakDays = "theory_streak_days"
        static let lastActiveMs = "theory_last_active_ms"
        static let lastRouteTagsCSV = "theory_last_route_tags_csv"
        static let lastRouteCentreId = "theory_last_route_centre_id"
        static let lastRouteId = "theory_last_route_id"
        static let lastRouteRecordedMs = "theory_last_route_recorded_ms"
    }

    private let defaults = UserDefaults.standard

    var progress: TheoryProgress {
        TheoryProgress(
            completedLessons: decodeStringSet(defaults.string(forKey: Keys.completedLessonsCSV)),
            topicStats: decodeTopicStats(defaults.string(forKey: Keys.topicStatsJSON)),
            wrongQueue: decodeStringSet(defaults.string(forKey: Keys.wrongQueueCSV)),
            bookmarks: decodeStringSet(defaults.string(forKey: Keys.bookmarksCSV)),
            streakDays: defaults.integer(forKey: Keys.streakDays),
            lastActiveAtMs: Int64(defaults.object(forKey: Keys.lastActiveMs) as? Int ?? 0),
            lastRouteTagSnapshot: decodeRouteTagSnapshot(
                tagsRaw: defaults.string(forKey: Keys.lastRouteTagsCSV),
                centreId: defaults.string(forKey: Keys.lastRouteCentreId),
                routeId: defaults.string(forKey: Keys.lastRouteId),
                recordedAtMs: Int64(defaults.object(forKey: Keys.lastRouteRecordedMs) as? Int ?? 0)
            )
        )
    }

    func markLessonCompleted(_ lessonId: String) {
        guard !lessonId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        var set = decodeStringSet(defaults.string(forKey: Keys.completedLessonsCSV))
        set.insert(lessonId)
        defaults.set(encodeStringSet(set), forKey: Keys.completedLessonsCSV)
        updateLastActive()
    }

    func recordQuizAnswer(topicId: String, questionId: String, isCorrect: Bool) {
        guard !topicId.isEmpty, !questionId.isEmpty else { return }

        var stats = decodeTopicStats(defaults.string(forKey: Keys.topicStatsJSON))
        let current = stats[topicId] ?? TheoryTopicStat(attempts: 0, correct: 0, wrong: 0, masteryPercent: 0)
        let attempts = current.attempts + 1
        let correct = current.correct + (isCorrect ? 1 : 0)
        let wrong = current.wrong + (isCorrect ? 0 : 1)
        let masteryPercent = max(0, min(100, Int((Float(correct) / Float(max(attempts, 1))) * 100)))
        stats[topicId] = TheoryTopicStat(
            attempts: attempts,
            correct: correct,
            wrong: wrong,
            masteryPercent: masteryPercent
        )
        defaults.set(encodeTopicStats(stats), forKey: Keys.topicStatsJSON)

        var wrongQueue = decodeStringSet(defaults.string(forKey: Keys.wrongQueueCSV))
        if isCorrect {
            wrongQueue.remove(questionId)
        } else {
            wrongQueue.insert(questionId)
        }
        defaults.set(encodeStringSet(wrongQueue), forKey: Keys.wrongQueueCSV)
        updateLastActive()
    }

    @discardableResult
    func toggleBookmark(_ questionId: String) -> Bool {
        guard !questionId.isEmpty else { return false }
        var bookmarks = decodeStringSet(defaults.string(forKey: Keys.bookmarksCSV))
        let bookmarked: Bool
        if bookmarks.contains(questionId) {
            bookmarks.remove(questionId)
            bookmarked = false
        } else {
            bookmarks.insert(questionId)
            bookmarked = true
        }
        defaults.set(encodeStringSet(bookmarks), forKey: Keys.bookmarksCSV)
        updateLastActive()
        return bookmarked
    }

    func setBookmark(_ questionId: String, bookmarked: Bool) {
        guard !questionId.isEmpty else { return }
        var bookmarks = decodeStringSet(defaults.string(forKey: Keys.bookmarksCSV))
        if bookmarked {
            bookmarks.insert(questionId)
        } else {
            bookmarks.remove(questionId)
        }
        defaults.set(encodeStringSet(bookmarks), forKey: Keys.bookmarksCSV)
        updateLastActive()
    }

    func clearWrongQuestion(_ questionId: String) {
        guard !questionId.isEmpty else { return }
        var wrong = decodeStringSet(defaults.string(forKey: Keys.wrongQueueCSV))
        wrong.remove(questionId)
        defaults.set(encodeStringSet(wrong), forKey: Keys.wrongQueueCSV)
        updateLastActive()
    }

    func touch() {
        updateLastActive()
    }

    func recordLastRouteTagSnapshot(tags: [String], centreId: String?, routeId: String?) {
        let normalizedTags = Array(
            Set(tags.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                .filter { !$0.isEmpty })
        ).sorted()
        defaults.set(encodeStringSet(Set(normalizedTags)), forKey: Keys.lastRouteTagsCSV)
        defaults.set(centreId, forKey: Keys.lastRouteCentreId)
        defaults.set(routeId, forKey: Keys.lastRouteId)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        defaults.set(Int(nowMs), forKey: Keys.lastRouteRecordedMs)
        updateLastActive(nowMs: nowMs)
    }

    private func decodeRouteTagSnapshot(
        tagsRaw: String?,
        centreId: String?,
        routeId: String?,
        recordedAtMs: Int64
    ) -> TheoryRouteTagSnapshot? {
        let tags = Array(decodeStringSet(tagsRaw)).sorted()
        guard !tags.isEmpty, recordedAtMs > 0 else { return nil }
        return TheoryRouteTagSnapshot(tags: tags, centreId: centreId, routeId: routeId, recordedAtMs: recordedAtMs)
    }

    private func updateLastActive(nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        let previousMs = Int64(defaults.object(forKey: Keys.lastActiveMs) as? Int ?? 0)
        let previousDay = previousMs > 0 ? previousMs / 86_400_000 : Int64.min
        let currentDay = nowMs / 86_400_000
        let previousStreak = defaults.integer(forKey: Keys.streakDays)

        let nextStreak: Int
        if previousDay == Int64.min {
            nextStreak = 1
        } else if currentDay == previousDay {
            nextStreak = max(previousStreak, 1)
        } else if currentDay == previousDay + 1 {
            nextStreak = max(previousStreak, 1) + 1
        } else {
            nextStreak = 1
        }

        defaults.set(Int(nowMs), forKey: Keys.lastActiveMs)
        defaults.set(nextStreak, forKey: Keys.streakDays)
    }

    private func decodeTopicStats(_ raw: String?) -> [String: TheoryTopicStat] {
        guard let raw, !raw.isEmpty,
            let data = raw.data(using: .utf8),
            let decoded = try? JSONDecoder().decode([String: TheoryTopicStat].self, from: data)
        else {
            return [:]
        }
        return decoded
    }

    private func encodeTopicStats(_ stats: [String: TheoryTopicStat]) -> String {
        guard let data = try? JSONEncoder().encode(stats), let text = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return text
    }

    private func decodeStringSet(_ raw: String?) -> Set<String> {
        guard let raw, !raw.isEmpty,
            let data = raw.data(using: .utf8),
            let decoded = try? JSONDecoder().decode([String].self, from: data)
        else {
            return []
        }
        return Set(decoded.filter { !$0.isEmpty })
    }

    private func encodeStringSet(_ values: Set<String>) -> String {
        let sorted = Array(values).sorted()
        guard let data = try? JSONEncoder().encode(sorted), let text = String(data: data, encoding: .utf8) else {
            return "[]"
        }
        return text
    }
}
