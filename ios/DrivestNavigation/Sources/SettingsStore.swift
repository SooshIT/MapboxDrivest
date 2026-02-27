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

enum DriverMode: String, CaseIterable {
    case learner
    case newDriver
    case standard
}

final class SettingsStore: @unchecked Sendable {
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
        static let lowStressRoutingEnabled = "low_stress_routing_enabled"
        static let analyticsConsent = "analytics_consent"
        static let notificationsPreference = "notifications_preference"
        static let safetyAcknowledged = "safety_acknowledged"
        static let driverMode = "driver_mode"
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
            ) ?? .backendOnly
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

    var lowStressRoutingEnabled: Bool {
        get {
            if defaults.object(forKey: Keys.lowStressRoutingEnabled) == nil {
                return true
            }
            return defaults.bool(forKey: Keys.lowStressRoutingEnabled)
        }
        set {
            defaults.set(newValue, forKey: Keys.lowStressRoutingEnabled)
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

    var notificationsPreference: Bool {
        get { defaults.bool(forKey: Keys.notificationsPreference) }
        set {
            defaults.set(newValue, forKey: Keys.notificationsPreference)
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

    var driverMode: DriverMode {
        get {
            DriverMode(
                rawValue: defaults.string(forKey: Keys.driverMode) ?? ""
            ) ?? .learner
        }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.driverMode)
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

    func applyDriverProfilePreset(_ mode: DriverMode) {
        let preset: (voiceMode: VoiceMode, promptSensitivity: PromptSensitivity, lowStressRouting: Bool)
        switch mode {
        case .learner:
            preset = (.all, .extraHelp, true)
        case .newDriver:
            preset = (.all, .standard, true)
        case .standard:
            preset = (.alerts, .minimal, false)
        }

        defaults.set(mode.rawValue, forKey: Keys.driverMode)
        defaults.set(preset.voiceMode.rawValue, forKey: Keys.voiceMode)
        defaults.set(preset.promptSensitivity.rawValue, forKey: Keys.promptSensitivity)
        defaults.set(preset.lowStressRouting, forKey: Keys.lowStressRoutingEnabled)
        defaults.set(true, forKey: Keys.hazardsEnabled)
        notifyChanged()
    }

    private func notifyChanged() {
        NotificationCenter.default.post(name: .drivestSettingsChanged, object: nil)
    }
}
