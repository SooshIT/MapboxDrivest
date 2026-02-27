import Foundation
import UIKit

@MainActor
final class KnowYourSignsPackLoader {
    static let shared = KnowYourSignsPackLoader()

    private let lock = NSLock()
    private var cachedTheory: KnowYourSignsTheoryPack?
    private var cachedQuestions: [KnowYourSignsQuestion]?

    func loadTheory() -> KnowYourSignsTheoryPack {
        lock.lock()
        if let cachedTheory {
            lock.unlock()
            return cachedTheory
        }
        lock.unlock()

        guard
            let url = Bundle.main.url(
                forResource: "Drivest_KnowYourSigns_Theory_Expanded",
                withExtension: "json",
                subdirectory: "Data/knowyoursigns"
            ),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode(KnowYourSignsTheoryPack.self, from: data)
        else {
            print("[Drivest iOS] know_your_signs_theory_load_failed")
            return .empty()
        }

        lock.lock()
        cachedTheory = decoded
        lock.unlock()
        return decoded
    }

    func loadQuestions() -> [KnowYourSignsQuestion] {
        lock.lock()
        if let cachedQuestions {
            lock.unlock()
            return cachedQuestions
        }
        lock.unlock()

        guard
            let url = Bundle.main.url(
                forResource: "Drivest_KnowYourSigns_Questions_Expanded",
                withExtension: "json",
                subdirectory: "Data/knowyoursigns"
            ),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode([KnowYourSignsQuestion].self, from: data)
        else {
            print("[Drivest iOS] know_your_signs_questions_load_failed")
            return []
        }

        lock.lock()
        cachedQuestions = decoded
        lock.unlock()
        return decoded
    }

    func resetCache() {
        lock.lock()
        cachedTheory = nil
        cachedQuestions = nil
        lock.unlock()
    }
}

enum KnowYourSignsImageResolver {
    private static let supportedExtensions = Set(["jpg", "jpeg", "png", "webp"])
    nonisolated(unsafe) private static var cachedCodeToRelativePath: [String: String] = [:]
    nonisolated(unsafe) private static var didBuildIndex = false
    private static let indexLock = NSLock()

    static func image(for relativePath: String?) -> UIImage? {
        guard let relativePath, !relativePath.isEmpty else { return nil }
        let cleaned = normalizedRelativePath(relativePath)
        if let image = loadImageFromRelativePath(cleaned) {
            return image
        }
        if let byCodePath = relativePathForSignCode(cleaned) {
            return loadImageFromRelativePath(byCodePath)
        }
        return nil
    }

    static func image(forSignCode signCode: String?) -> UIImage? {
        guard let path = relativePathForSignCode(signCode) else { return nil }
        return loadImageFromRelativePath(path)
    }

    static func relativePathForSignCode(_ signCode: String?) -> String? {
        guard let signCode, !signCode.isEmpty else { return nil }
        buildSignCodeIndexIfNeeded()
        let normalized = normalizeSignCode(signCode)
        guard !normalized.isEmpty else { return nil }

        if let exact = cachedCodeToRelativePath[normalized] {
            return exact
        }

        // Some feeds provide "uk:611.1" or similar; check last token as fallback.
        if let tail = normalized.split(separator: "_").last,
            let matched = cachedCodeToRelativePath[String(tail)]
        {
            return matched
        }
        return nil
    }

    private static func normalizedRelativePath(_ raw: String) -> String {
        var cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        cleaned = cleaned.replacingOccurrences(of: "\\", with: "/")
        cleaned = cleaned.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if cleaned.hasPrefix("Data/trafficsigns/") {
            cleaned = String(cleaned.dropFirst("Data/trafficsigns/".count))
        }
        if cleaned.hasPrefix("trafficsigns/") {
            cleaned = String(cleaned.dropFirst("trafficsigns/".count))
        }
        return cleaned
    }

    private static func loadImageFromRelativePath(_ relativePath: String) -> UIImage? {
        guard let resourceRoot = Bundle.main.resourceURL else { return nil }
        let fullPath = resourceRoot
            .appendingPathComponent("Data", isDirectory: true)
            .appendingPathComponent("trafficsigns", isDirectory: true)
            .appendingPathComponent(relativePath)
            .path
        return UIImage(contentsOfFile: fullPath)
    }

    private static func buildSignCodeIndexIfNeeded() {
        indexLock.lock()
        defer { indexLock.unlock() }
        guard !didBuildIndex else { return }
        didBuildIndex = true

        guard let root = Bundle.main.resourceURL?
            .appendingPathComponent("Data", isDirectory: true)
            .appendingPathComponent("trafficsigns", isDirectory: true)
        else { return }

        let fm = FileManager.default
        guard let enumerator = fm.enumerator(at: root, includingPropertiesForKeys: nil) else { return }
        var index: [String: String] = [:]
        while let fileURL = enumerator.nextObject() as? URL {
            guard supportedExtensions.contains(fileURL.pathExtension.lowercased()) else { continue }
            let relativePath = fileURL.path.replacingOccurrences(of: root.path + "/", with: "")
            let basename = fileURL.deletingPathExtension().lastPathComponent
            let normalized = normalizeSignCode(basename)
            guard !normalized.isEmpty else { continue }
            index[normalized] = relativePath
            if let primary = normalized.split(separator: ".").first {
                index[String(primary)] = index[String(primary)] ?? relativePath
            }
        }
        cachedCodeToRelativePath = index
    }

    private static func normalizeSignCode(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if let lastColon = value.lastIndex(of: ":") {
            value = String(value[value.index(after: lastColon)...])
        }
        value = value.replacingOccurrences(of: " ", with: "")
        value = value.replacingOccurrences(of: "-", with: "")
        value = value.replacingOccurrences(of: "/", with: "")
        value = value.replacingOccurrences(of: "SIGN", with: "")
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.+")
        let filtered = value.unicodeScalars.filter { allowed.contains($0) }
        return String(String.UnicodeScalarView(filtered))
    }
}

enum KnowYourSignsTextFormatter {
    private static let signPrefixPattern = #"(?i)^sign\s+[a-z0-9.+\-/ ]+\s*$"#
    private static let signTokenPattern = #"(?i)\bsign\s+[a-z0-9.+\-/ ]+\b"#

    static func displayTitle(for sign: KnowYourSign) -> String {
        let raw = sign.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if !isCodeOnlyTitle(raw) {
            return raw
        }
        return categoryBasedName(for: sign.category, action: sign.driverAction)
    }

    static func displayMeaning(for sign: KnowYourSign) -> String {
        sanitizeBody(sign.meaning, category: sign.category)
    }

    static func displayPrompt(for question: KnowYourSignsQuestion) -> String {
        let fallback = "You see this \(singularCategory(question.topic).lowercased()) on the road. What is the safest response?"
        return sanitizeQuestion(question.question, fallback: fallback)
    }

    static func displayExplanation(for question: KnowYourSignsQuestion) -> String {
        sanitizeBody(question.explanation, category: question.topic)
    }

    private static func isCodeOnlyTitle(_ value: String) -> Bool {
        guard let regex = try? NSRegularExpression(pattern: signPrefixPattern) else { return false }
        let range = NSRange(value.startIndex ..< value.endIndex, in: value)
        return regex.firstMatch(in: value, options: [], range: range) != nil
    }

    private static func sanitizeQuestion(_ value: String, fallback: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: signTokenPattern) else { return fallback }
        let range = NSRange(value.startIndex ..< value.endIndex, in: value)
        let replaced = regex.stringByReplacingMatches(in: value, options: [], range: range, withTemplate: "this sign")
        return replaced == value ? fallback : replaced
    }

    private static func sanitizeBody(_ value: String, category: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: signTokenPattern) else { return value }
        let range = NSRange(value.startIndex ..< value.endIndex, in: value)
        return regex.stringByReplacingMatches(in: value, options: [], range: range, withTemplate: "This \(singularCategory(category).lowercased())")
    }

    private static func categoryBasedName(for category: String, action: String) -> String {
        let normalizedCategory = category.lowercased()
        let normalizedAction = action.lowercased()

        if normalizedCategory.contains("parking") { return "Parking sign" }
        if normalizedCategory.contains("speed") { return "Speed limit sign" }
        if normalizedCategory.contains("warning") { return "Warning sign" }
        if normalizedCategory.contains("regulatory") { return "Regulatory sign" }
        if normalizedCategory.contains("direction") || normalizedCategory.contains("tourist") { return "Direction sign" }
        if normalizedCategory.contains("information") { return "Information sign" }
        if normalizedCategory.contains("low bridge") || normalizedAction.contains("height") { return "Low bridge sign" }
        if normalizedCategory.contains("level crossing") { return "Level crossing sign" }
        if normalizedCategory.contains("road works") || normalizedCategory.contains("temporary") { return "Road works sign" }
        if normalizedCategory.contains("traffic calming") { return "Traffic calming sign" }
        if normalizedCategory.contains("tram") { return "Tram sign" }
        if normalizedCategory.contains("tidal flow") { return "Tidal flow lane sign" }
        if normalizedCategory.contains("bus and cycle") { return "Bus and cycle sign" }
        if normalizedCategory.contains("cyclists and pedestrians") { return "Cycle and pedestrian sign" }
        if normalizedCategory.contains("pedestrian zone") { return "Pedestrian zone sign" }
        if normalizedCategory.contains("pedestrian, cycle and equestrian") { return "Pedestrian / cycle / equestrian sign" }
        if normalizedCategory.contains("miscellaneous") { return "Road information sign" }
        return singularCategory(category)
    }

    private static func singularCategory(_ category: String) -> String {
        let trimmed = category.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Road sign" }
        if trimmed.lowercased().hasSuffix(" signs") {
            return String(trimmed.dropLast(1))
        }
        return trimmed
    }
}
