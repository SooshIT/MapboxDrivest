import AVFoundation
import Foundation

final class HazardVoiceController: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private let speechBudgetEnforcer: SpeechBudgetEnforcer
    private let voiceModePolicy = HazardVoiceModePolicy()

    private var isHazardSpeaking = false
    private var queue: [PromptEvent] = []
    private var lastSpokenAtByType: [PromptType: Int64] = [:]
    private var lastSpokenAtMs: Int64 = 0

    init(speechBudgetEnforcer: SpeechBudgetEnforcer = SpeechBudgetEnforcer()) {
        self.speechBudgetEnforcer = speechBudgetEnforcer
        super.init()
        synthesizer.delegate = self
    }

    func enqueue(
        _ promptEvent: PromptEvent,
        voiceMode: VoiceMode,
        upcomingManeuverTimeS: Double?,
        isManeuverSpeechPlaying: Bool
    ) {
        guard voiceModePolicy.canSpeak(promptEvent.type, voiceMode: voiceMode) else { return }
        if let upcomingManeuverTimeS, upcomingManeuverTimeS < 6 { return }
        if isManeuverSpeechPlaying { return }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        if let lastByType = lastSpokenAtByType[promptEvent.type], nowMs - lastByType < 90_000 {
            return
        }
        if nowMs - lastSpokenAtMs < 20_000 {
            return
        }

        queue.append(promptEvent)
        speakNextIfPossible()
    }

    func clear() {
        queue.removeAll()
    }

    func stopSpeaking() {
        queue.removeAll()
        isHazardSpeaking = false
        synthesizer.stopSpeaking(at: .immediate)
    }

    func onManeuverInstructionArrived() {
        if isHazardSpeaking {
            stopSpeaking()
        } else {
            clear()
        }
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        isHazardSpeaking = false
        speakNextIfPossible()
    }

    private func speakNextIfPossible() {
        guard !isHazardSpeaking else { return }
        guard !queue.isEmpty else { return }
        let event = queue.removeFirst()
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        lastSpokenAtByType[event.type] = nowMs
        lastSpokenAtMs = nowMs

        let baseText = speechText(for: event)
        let speechText = speechBudgetEnforcer.enforce(
            text: baseText,
            promptType: event.type,
            distanceMeters: event.distanceM
        )

        let utterance = AVSpeechUtterance(string: speechText)
        utterance.rate = 0.5
        utterance.voice = AVSpeechSynthesisVoice(language: "en-GB")
        isHazardSpeaking = true
        synthesizer.speak(utterance)
    }

    private func speechText(for event: PromptEvent) -> String {
        switch event.type {
        case .roundabout:
            return "Roundabout ahead. Prepare early."
        case .miniRoundabout:
            return "Mini roundabout ahead. Slow now."
        case .schoolZone:
            return "School zone ahead. Slow down."
        case .zebraCrossing:
            return "Zebra crossing ahead. Watch for pedestrians."
        case .giveWay:
            return "Give way ahead. Yield."
        case .trafficSignal:
            return "Traffic lights ahead. Prepare to stop."
        case .speedCamera:
            return "Speed camera ahead. Check speed."
        case .busLane:
            return "Bus lane ahead."
        case .busStop:
            return "Bus stop ahead. Watch for buses."
        }
    }
}
