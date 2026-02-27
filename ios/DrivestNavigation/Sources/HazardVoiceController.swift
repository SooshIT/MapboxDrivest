import AVFoundation
import Foundation

@MainActor
final class HazardVoiceController: NSObject, AVSpeechSynthesizerDelegate {
    private struct QueuedPrompt {
        let event: PromptEvent
        var deferUntilMs: Int64
    }

    private let synthesizer = AVSpeechSynthesizer()
    private let speechBudgetEnforcer: SpeechBudgetEnforcer
    private let voiceModePolicy = HazardVoiceModePolicy()

    private var isHazardSpeaking = false
    private var queue: [QueuedPrompt] = []
    private var wakeTask: Task<Void, Never>?
    private var lastSpokenAtByType: [PromptType: Int64] = [:]
    private var lastSpokenAtByFeatureId: [String: Int64] = [:]
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

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        if usesPerFeatureDistanceGating(promptEvent.type) {
            if let lastByFeature = lastSpokenAtByFeatureId[promptEvent.featureId],
                nowMs - lastByFeature < featureSpeechCooldownMs
            {
                print(
                    "[Drivest iOS] voice_prompt_suppressed " +
                        "reason=feature_cooldown type=\(promptEvent.type.rawValue) " +
                        "feature_id=\(promptEvent.featureId)"
                )
                return
            }
            if let lastByType = lastSpokenAtByType[promptEvent.type],
                nowMs - lastByType < perFeatureTypeSpeechCooldownMs(for: promptEvent.type)
            {
                print(
                    "[Drivest iOS] voice_prompt_suppressed " +
                        "reason=type_cooldown type=\(promptEvent.type.rawValue)"
                )
                return
            }
        } else {
            if let lastByType = lastSpokenAtByType[promptEvent.type],
                nowMs - lastByType < typeSpeechCooldownMs
            {
                print(
                    "[Drivest iOS] voice_prompt_suppressed " +
                        "reason=type_cooldown type=\(promptEvent.type.rawValue)"
                )
                return
            }
            if nowMs - lastSpokenAtMs < overallSpeechCooldownMs {
                print(
                    "[Drivest iOS] voice_prompt_suppressed " +
                        "reason=overall_cooldown type=\(promptEvent.type.rawValue)"
                )
                return
            }
        }

        var deferUntilMs = nowMs
        if let upcomingManeuverTimeS, upcomingManeuverTimeS < maneuverSuppressionTimeS {
            deferUntilMs = max(
                deferUntilMs,
                nowMs + Int64(((upcomingManeuverTimeS + 0.8) * 1000).rounded())
            )
            print(
                "[Drivest iOS] voice_prompt_deferred " +
                    "reason=upcoming_maneuver type=\(promptEvent.type.rawValue) " +
                    "defer_ms=\(deferUntilMs - nowMs)"
            )
        }
        if isManeuverSpeechPlaying {
            deferUntilMs = max(deferUntilMs, nowMs + maneuverSpeechPlayingDeferralMs)
            print(
                "[Drivest iOS] voice_prompt_deferred " +
                    "reason=maneuver_speech_playing type=\(promptEvent.type.rawValue) " +
                    "defer_ms=\(deferUntilMs - nowMs)"
            )
        }

        if let existingIndex = queue.firstIndex(where: {
            $0.event.featureId == promptEvent.featureId && $0.event.type == promptEvent.type
        }) {
            if deferUntilMs < queue[existingIndex].deferUntilMs {
                queue[existingIndex].deferUntilMs = deferUntilMs
            }
            speakNextIfPossible()
            return
        }

        queue.append(QueuedPrompt(event: promptEvent, deferUntilMs: deferUntilMs))
        queue.sort {
            if $0.deferUntilMs == $1.deferUntilMs {
                if $0.event.priority == $1.event.priority {
                    return $0.event.distanceM < $1.event.distanceM
                }
                return $0.event.priority > $1.event.priority
            }
            return $0.deferUntilMs < $1.deferUntilMs
        }
        speakNextIfPossible()
    }

    func clear() {
        queue.removeAll()
        wakeTask?.cancel()
        wakeTask = nil
    }

    func stopSpeaking() {
        queue.removeAll()
        wakeTask?.cancel()
        wakeTask = nil
        isHazardSpeaking = false
        synthesizer.stopSpeaking(at: .immediate)
    }

    func onManeuverInstructionArrived() {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let minDeferredUntil = nowMs + postManeuverQueueDeferralMs
        for index in queue.indices {
            queue[index].deferUntilMs = max(queue[index].deferUntilMs, minDeferredUntil)
        }
        if isHazardSpeaking {
            isHazardSpeaking = false
            synthesizer.stopSpeaking(at: .immediate)
        }
        speakNextIfPossible()
    }

    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.isHazardSpeaking = false
            self.speakNextIfPossible()
        }
    }

    private func speakNextIfPossible() {
        guard !isHazardSpeaking else { return }
        guard !queue.isEmpty else { return }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        guard let next = queue.first else { return }
        if next.deferUntilMs > nowMs {
            scheduleWake(atEpochMs: next.deferUntilMs)
            return
        }

        wakeTask?.cancel()
        wakeTask = nil
        let event = queue.removeFirst().event
        let nowSpeakMs = Int64(Date().timeIntervalSince1970 * 1000)
        lastSpokenAtByType[event.type] = nowSpeakMs
        lastSpokenAtByFeatureId[event.featureId] = nowSpeakMs
        lastSpokenAtMs = nowSpeakMs

        let baseText = speechText(for: event)
        let speechText = speechBudgetEnforcer.enforce(
            text: baseText,
            promptType: event.type,
            distanceMeters: event.distanceM
        )

        let utterance = AVSpeechUtterance(string: speechText)
        utterance.rate = 0.5
        utterance.voice = AVSpeechSynthesisVoice(language: "en-GB")
        print(
            "[Drivest iOS] voice_prompt_spoken " +
                "type=\(event.type.rawValue) distance_m=\(event.distanceM)"
        )
        isHazardSpeaking = true
        synthesizer.speak(utterance)
    }

    private func scheduleWake(atEpochMs targetMs: Int64) {
        wakeTask?.cancel()
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let delayMs = max(0, targetMs - nowMs)
        wakeTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delayMs) * 1_000_000)
            guard let self else { return }
            self.wakeTask = nil
            self.speakNextIfPossible()
        }
    }

    private func speechText(for event: PromptEvent) -> String {
        switch event.type {
        case .noEntry:
            return "No entry ahead. Avoid this direction."
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
        case .unknown:
            return "Traffic sign ahead."
        }
    }

    private func usesPerFeatureDistanceGating(_ type: PromptType) -> Bool {
        switch type {
        case .busStop, .trafficSignal, .speedCamera, .miniRoundabout:
            return true
        default:
            return false
        }
    }

    private func perFeatureTypeSpeechCooldownMs(for type: PromptType) -> Int64 {
        switch type {
        case .busStop:
            return 22_000
        case .trafficSignal:
            return 14_000
        case .miniRoundabout:
            return 14_000
        case .speedCamera:
            return 20_000
        default:
            return defaultPerFeatureTypeSpeechCooldownMs
        }
    }

    private let maneuverSuppressionTimeS: Double = 1.6
    private let maneuverSpeechPlayingDeferralMs: Int64 = 900
    private let postManeuverQueueDeferralMs: Int64 = 900
    private let typeSpeechCooldownMs: Int64 = 45_000
    private let overallSpeechCooldownMs: Int64 = 8_000
    private let defaultPerFeatureTypeSpeechCooldownMs: Int64 = 10_000
    private let featureSpeechCooldownMs: Int64 = 75_000
}
