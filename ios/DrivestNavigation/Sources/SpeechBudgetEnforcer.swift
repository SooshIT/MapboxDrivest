import Foundation

final class SpeechBudgetEnforcer {
    let maxChars: Int
    let maxWords: Int

    init(maxChars: Int = 70, maxWords: Int = 14) {
        self.maxChars = maxChars
        self.maxWords = maxWords
    }

    func enforce(
        text: String,
        promptType: PromptType,
        distanceMeters: Int?
    ) -> String {
        let keyword = keyword(for: promptType)
        let distancePhrase = distancePhrase(for: distanceMeters)
        let actionHint = actionHint(for: promptType)
        let normalizedInput = normalize(text)

        let candidates = [
            normalizedInput,
            compose(keyword: keyword, distancePhrase: distancePhrase, actionHint: actionHint),
            compose(keyword: keyword, distancePhrase: distancePhrase, actionHint: nil),
            compose(keyword: keyword, distancePhrase: nil, actionHint: nil),
        ]
            .filter { !$0.isEmpty }
            .unique()

        for candidate in candidates {
            let prepared = normalize(candidate)
            guard containsKeyword(prepared, keyword: keyword) else { continue }
            if withinBudget(prepared) {
                return ensureTerminalPunctuation(prepared)
            }
        }

        return hardTrimWithSuffix(
            compose(keyword: keyword, distancePhrase: distancePhrase, actionHint: actionHint)
        )
    }

    private func compose(keyword: String, distancePhrase: String?, actionHint: String?) -> String {
        let firstClause: String
        if let distancePhrase {
            if distancePhrase == "ahead" {
                firstClause = "\(keyword) ahead"
            } else if distancePhrase == "now" {
                firstClause = "\(keyword) now"
            } else {
                firstClause = "\(keyword) \(distancePhrase)"
            }
        } else {
            firstClause = keyword
        }
        if let actionHint, !actionHint.isEmpty {
            return "\(firstClause). \(actionHint)"
        }
        return "\(firstClause)."
    }

    private func hardTrimWithSuffix(_ text: String) -> String {
        var shortened = trimWords(normalize(text), maxAllowedWords: maxWords)
        if shortened.count > maxChars {
            let allowedPrefix = max(0, maxChars - truncationSuffix.count)
            shortened = String(shortened.prefix(allowedPrefix)).trimmingCharacters(in: .whitespaces)
            shortened = shortened.isEmpty
                ? String(truncationSuffix.prefix(maxChars))
                : "\(shortened)\(truncationSuffix)"
        }
        if wordCount(shortened) > maxWords {
            shortened = trimWords(shortened, maxAllowedWords: maxWords)
        }
        if shortened.count > maxChars {
            shortened = String(shortened.prefix(maxChars)).trimmingCharacters(in: .whitespaces)
        }
        return ensureTerminalPunctuation(shortened)
    }

    private func withinBudget(_ text: String) -> Bool {
        text.count <= maxChars && wordCount(text) <= maxWords
    }

    private func wordCount(_ text: String) -> Int {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return 0 }
        return text
            .split(whereSeparator: { $0.isWhitespace })
            .count
    }

    private func trimWords(_ text: String, maxAllowedWords: Int) -> String {
        guard maxAllowedWords > 0 else { return "" }
        return text
            .split(whereSeparator: { $0.isWhitespace })
            .prefix(maxAllowedWords)
            .joined(separator: " ")
    }

    private func normalize(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func ensureTerminalPunctuation(_ text: String) -> String {
        guard !text.isEmpty else { return text }
        guard let last = text.last else { return text }
        if [".", "!", "?"].contains(last) {
            return text
        }
        if text.count + 1 > maxChars {
            return text
        }
        return "\(text)."
    }

    private func containsKeyword(_ text: String, keyword: String) -> Bool {
        text.lowercased().contains(keyword.lowercased())
    }

    private func keyword(for type: PromptType) -> String {
        switch type {
        case .roundabout: return "Roundabout"
        case .miniRoundabout: return "Mini roundabout"
        case .schoolZone: return "School zone"
        case .zebraCrossing: return "Zebra crossing"
        case .giveWay: return "Give way"
        case .trafficSignal: return "Traffic lights"
        case .speedCamera: return "Speed camera"
        case .busLane: return "Bus lane"
        case .busStop: return "Bus stop"
        case .noEntry: return "No entry"
        case .unknown: return "Hazard"
        }
    }

    private func actionHint(for type: PromptType) -> String {
        switch type {
        case .roundabout: return "Prepare early."
        case .miniRoundabout: return "Slow now."
        case .schoolZone: return "Slow down."
        case .zebraCrossing: return "Watch for pedestrians."
        case .giveWay: return "Yield."
        case .trafficSignal: return "Prepare to stop."
        case .speedCamera: return "Check speed."
        case .busLane: return "Follow lane signs."
        case .busStop: return "Watch for buses."
        case .noEntry: return "Rerouting now."
        case .unknown: return "Proceed with caution."
        }
    }

    private func distancePhrase(for distanceMeters: Int?) -> String {
        guard let distanceMeters, distanceMeters > 0 else { return "ahead" }
        if distanceMeters <= 80 { return "now" }
        let rounded = Int((Double(distanceMeters) / 10.0).rounded(.up) * 10.0)
        return "in \(rounded) meters"
    }

    private let truncationSuffix = "..."
}

private extension Array where Element == String {
    func unique() -> [String] {
        var seen = Set<String>()
        return filter { value in
            if seen.contains(value) { return false }
            seen.insert(value)
            return true
        }
    }
}
