import Foundation

extension Notification.Name {
    static let drivestSettingsChanged = Notification.Name("drivest.settings.changed")
}

enum VoiceMode: String, CaseIterable {
    case all
    case alerts
    case mute
}

enum DataSourceMode: String, CaseIterable {
    case backendThenCacheThenAssets
    case backendOnly
    case assetsOnly
}

enum PromptSensitivity: String, CaseIterable {
    case minimal
    case standard
    case extraHelp
}

enum UnitsMode: String, CaseIterable {
    case ukMph
    case metricKmh
}

final class SettingsStore {
    static let shared = SettingsStore()

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let voiceMode = "voice_mode"
        static let unitsMode = "units_mode"
        static let lastCentreId = "last_centre_id"
        static let lastMode = "last_mode"
        static let dataSourceMode = "data_source_mode"
        static let promptSensitivity = "prompt_sensitivity"
        static let hazardsEnabled = "hazards_enabled"
        static let analyticsConsent = "analytics_consent"
        static let safetyAcknowledged = "safety_acknowledged"
    }

    var voiceMode: VoiceMode {
        get { VoiceMode(rawValue: defaults.string(forKey: Keys.voiceMode) ?? "") ?? .all }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.voiceMode)
            notifyChanged()
        }
    }

    var unitsMode: UnitsMode {
        get { UnitsMode(rawValue: defaults.string(forKey: Keys.unitsMode) ?? "") ?? .ukMph }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.unitsMode)
            notifyChanged()
        }
    }

    var lastCentreId: String {
        get { defaults.string(forKey: Keys.lastCentreId) ?? "colchester" }
        set {
            defaults.set(newValue, forKey: Keys.lastCentreId)
            notifyChanged()
        }
    }

    var lastMode: AppMode {
        get { AppMode(rawValue: defaults.string(forKey: Keys.lastMode) ?? "") ?? .practice }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.lastMode)
            notifyChanged()
        }
    }

    var dataSourceMode: DataSourceMode {
        get {
            DataSourceMode(
                rawValue: defaults.string(forKey: Keys.dataSourceMode) ?? ""
            ) ?? .backendThenCacheThenAssets
        }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.dataSourceMode)
            notifyChanged()
        }
    }

    var promptSensitivity: PromptSensitivity {
        get {
            PromptSensitivity(
                rawValue: defaults.string(forKey: Keys.promptSensitivity) ?? ""
            ) ?? .standard
        }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.promptSensitivity)
            notifyChanged()
        }
    }

    var hazardsEnabled: Bool {
        get {
            if defaults.object(forKey: Keys.hazardsEnabled) == nil {
                return true
            }
            return defaults.bool(forKey: Keys.hazardsEnabled)
        }
        set {
            defaults.set(newValue, forKey: Keys.hazardsEnabled)
            notifyChanged()
        }
    }

    var analyticsConsent: Bool {
        get { defaults.bool(forKey: Keys.analyticsConsent) }
        set {
            defaults.set(newValue, forKey: Keys.analyticsConsent)
            notifyChanged()
        }
    }

    var safetyAcknowledged: Bool {
        get { defaults.bool(forKey: Keys.safetyAcknowledged) }
        set {
            defaults.set(newValue, forKey: Keys.safetyAcknowledged)
            notifyChanged()
        }
    }

    func cycleVoiceMode() -> VoiceMode {
        let next: VoiceMode = switch voiceMode {
        case .all:
            .alerts
        case .alerts:
            .mute
        case .mute:
            .all
        }
        voiceMode = next
        return next
    }

    private func notifyChanged() {
        NotificationCenter.default.post(name: .drivestSettingsChanged, object: nil)
    }
}
