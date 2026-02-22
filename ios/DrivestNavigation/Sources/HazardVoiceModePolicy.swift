import Foundation

struct HazardVoiceModePolicy {
    func canSpeak(_ type: PromptType, voiceMode: VoiceMode) -> Bool {
        switch voiceMode {
        case .all:
            return type == .roundabout ||
                type == .miniRoundabout ||
                type == .schoolZone ||
                type == .zebraCrossing ||
                type == .giveWay ||
                type == .trafficSignal ||
                type == .speedCamera ||
                type == .busStop

        case .alerts:
            return type == .roundabout ||
                type == .miniRoundabout ||
                type == .schoolZone ||
                type == .speedCamera

        case .mute:
            return false
        }
    }
}
