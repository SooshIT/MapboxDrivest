import Foundation

enum TheoryReadinessLabel {
    case building
    case almostReady
    case ready
}

struct TheoryReadiness {
    let score: Int
    let label: TheoryReadinessLabel
    let masteredTopicsPercent: Int
}

enum TheoryReadinessCalculator {
    private static let masteryThreshold = 75

    static func calculate(progress: TheoryProgress, totalTopics: Int) -> TheoryReadiness {
        let safeTotalTopics = max(totalTopics, 1)
        let masteredTopicsCount = progress.topicStats.values.filter { $0.masteryPercent >= masteryThreshold }.count
        let masteryPercent = max(0, min(100, Int((Float(masteredTopicsCount) * 100) / Float(safeTotalTopics))))

        let attempts = max(progress.topicStats.values.reduce(0) { $0 + $1.attempts }, 1)
        let correct = progress.topicStats.values.reduce(0) { $0 + $1.correct }
        let accuracyPercent = max(0, min(100, Int((Float(correct) * 100) / Float(attempts))))

        let consistencyBonus = min(progress.streakDays * 2, 10)
        let rawScore = Int((Float(masteryPercent) * 0.6) + (Float(accuracyPercent) * 0.4)) + consistencyBonus
        let score = max(0, min(100, rawScore))

        let label: TheoryReadinessLabel
        if score >= 75 {
            label = .ready
        } else if score >= 40 {
            label = .almostReady
        } else {
            label = .building
        }

        return TheoryReadiness(score: score, label: label, masteredTopicsPercent: masteryPercent)
    }
}

enum TheoryRouteTagMapper {
    private static let map: [String: String] = [
        "zebra_crossings": "pedestrian_crossings",
        "traffic_lights": "signals_and_junction_control",
        "bus_lanes": "bus_lane_rules",
        "roundabouts": "roundabouts",
        "school_zones": "speed_limits_and_school_zones",
        "mini_roundabouts": "roundabouts"
    ]

    static func mapTags(_ tags: [String]) -> [String] {
        var seen: Set<String> = []
        var result: [String] = []
        for tag in tags {
            let key = tag.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            guard let mapped = map[key], !seen.contains(mapped) else { continue }
            seen.insert(mapped)
            result.append(mapped)
        }
        return result
    }
}

enum TheoryQuizQuestionSelector {
    static func selectQuestions(
        allQuestions: [TheoryQuestion],
        bookmarks: Set<String>,
        wrongQueue: Set<String>,
        mode: TheoryQuizMode,
        topicId: String?,
        requestedCount: Int
    ) -> [TheoryQuestion] {
        guard !allQuestions.isEmpty else { return [] }
        let safeCount = max(requestedCount, 1)

        let pool: [TheoryQuestion] = {
            switch mode {
            case .bookmarks:
                return allQuestions.filter { bookmarks.contains($0.id) }
            case .wrong:
                return allQuestions.filter { wrongQueue.contains($0.id) }
            case .topic:
                if let topicId, !topicId.isEmpty {
                    return allQuestions.filter { $0.topicId == topicId }
                }
                return allQuestions
            case .mock:
                return allQuestions
            }
        }()

        guard !pool.isEmpty else { return [] }

        let orderedPool = pool.sorted {
            let lhsIndex = questionOrderIndex($0.id)
            let rhsIndex = questionOrderIndex($1.id)
            if lhsIndex == rhsIndex { return $0.id < $1.id }
            return lhsIndex < rhsIndex
        }

        switch mode {
        case .topic:
            return Array(orderedPool.prefix(min(safeCount, orderedPool.count)))
        case .bookmarks, .wrong, .mock:
            return Array(orderedPool.shuffled().prefix(min(safeCount, orderedPool.count)))
        }
    }

    private static func questionOrderIndex(_ questionId: String) -> Int {
        if let range = questionId.range(of: #"_q_(\d+)$"#, options: .regularExpression),
            let value = Int(questionId[range].replacingOccurrences(of: "_q_", with: ""))
        {
            return value
        }
        if let range = questionId.range(of: #"(\d+)$"#, options: .regularExpression),
            let value = Int(questionId[range])
        {
            return value
        }
        return Int.max
    }
}
