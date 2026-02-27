import XCTest
@testable import DrivestNavigation

final class SpeechBudgetEnforcerTests: XCTestCase {
    private let enforcer = SpeechBudgetEnforcer()
    private let policy = HazardVoiceModePolicy()

    func testAllHazardPhrasesStayWithinBudget() {
        allPromptTypes.enumerated().forEach { index, type in
            let result = enforcer.enforce(
                text: longTemplate(for: type),
                promptType: type,
                distanceMeters: 180 + (index * 10)
            )
            XCTAssertLessThanOrEqual(result.count, 70, "chars exceeded for \(type)")
            XCTAssertLessThanOrEqual(wordCount(result), 14, "words exceeded for \(type)")
        }
    }

    func testEveryHazardTypeRetainsKeyword() {
        allPromptTypes.forEach { type in
            let result = enforcer.enforce(
                text: longTemplate(for: type),
                promptType: type,
                distanceMeters: 220
            ).lowercased()
            XCTAssertTrue(
                result.contains(expectedKeyword(for: type)),
                "missing keyword for \(type) in '\(result)'"
            )
        }
    }

    func testAlertsModeOutputRespectsBudget() {
        allPromptTypes
            .filter { policy.canSpeak($0, voiceMode: .alerts) }
            .forEach { type in
                let result = enforcer.enforce(
                    text: longTemplate(for: type),
                    promptType: type,
                    distanceMeters: 250
                )
                XCTAssertLessThanOrEqual(result.count, 70, "alerts chars exceeded for \(type)")
                XCTAssertLessThanOrEqual(wordCount(result), 14, "alerts words exceeded for \(type)")
            }
    }

    func testMuteModeDoesNotAllowAnyHazardSpeech() {
        allPromptTypes.forEach { type in
            XCTAssertFalse(policy.canSpeak(type, voiceMode: .mute), "mute should block \(type)")
        }
    }

    private var allPromptTypes: [PromptType] {
        [
            .roundabout,
            .miniRoundabout,
            .schoolZone,
            .zebraCrossing,
            .giveWay,
            .trafficSignal,
            .speedCamera,
            .busLane,
            .busStop,
            .noEntry,
        ]
    }

    private func longTemplate(for type: PromptType) -> String {
        "Advisory for \(type.rawValue.lowercased()): this sentence is intentionally long so the " +
            "speech budget enforcer must shorten it deterministically while keeping key hazard meaning."
    }

    private func expectedKeyword(for type: PromptType) -> String {
        switch type {
        case .roundabout: return "roundabout"
        case .miniRoundabout: return "mini roundabout"
        case .schoolZone: return "school zone"
        case .zebraCrossing: return "zebra crossing"
        case .giveWay: return "give way"
        case .trafficSignal: return "traffic lights"
        case .speedCamera: return "speed camera"
        case .busLane: return "bus lane"
        case .busStop: return "bus stop"
        case .noEntry: return "no entry"
        case .unknown: return "hazard"
        }
    }

    private func wordCount(_ text: String) -> Int {
        text.split(whereSeparator: { $0.isWhitespace }).count
    }
}
