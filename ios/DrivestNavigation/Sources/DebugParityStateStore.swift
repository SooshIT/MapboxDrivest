import Foundation

final class DebugParityStateStore {
    static let shared = DebugParityStateStore()

    var routesPackVersionId: String = "routes-assets-v1"
    var hazardsPackVersionId: String = "hazards-assets-v1"
    var lastPromptFired: String = "-"
    var lastPromptSuppressed: String = "-"
    var lastOffRouteState: String = "-"
    var offRouteRawDistanceMeters: Double = 0
    var offRouteSmoothedDistanceMeters: Double = 0

    private(set) var observerAttachCount: Int = 0
    private(set) var observerDetachCount: Int = 0
    private(set) var activeObserverAttachCount: Int = 0

    private init() {}

    func markObserverAttached() {
        observerAttachCount += 1
        activeObserverAttachCount += 1
        print(
            "[Drivest iOS] observer_attach active=\(activeObserverAttachCount) " +
                "attach_total=\(observerAttachCount)"
        )
        assert(
            activeObserverAttachCount <= 1,
            "[Drivest iOS] observer attach count exceeded 1"
        )
    }

    func markObserverDetached() {
        if activeObserverAttachCount > 0 {
            activeObserverAttachCount -= 1
        }
        observerDetachCount += 1
        print(
            "[Drivest iOS] observer_detach active=\(activeObserverAttachCount) " +
                "detach_total=\(observerDetachCount)"
        )
    }

    func snapshot(settingsStore: SettingsStore = .shared) -> DebugParitySnapshot {
        DebugParitySnapshot(
            dataSourceMode: settingsStore.dataSourceMode.rawValue,
            voiceMode: settingsStore.voiceMode.rawValue,
            promptSensitivity: settingsStore.promptSensitivity.rawValue,
            hazardsEnabled: settingsStore.hazardsEnabled,
            analyticsConsent: settingsStore.analyticsConsent,
            safetyAcknowledged: settingsStore.safetyAcknowledged,
            routesPackVersionId: routesPackVersionId,
            hazardsPackVersionId: hazardsPackVersionId,
            lastPromptFired: lastPromptFired,
            lastPromptSuppressed: lastPromptSuppressed,
            lastOffRouteState: lastOffRouteState,
            offRouteRawDistanceMeters: offRouteRawDistanceMeters,
            offRouteSmoothedDistanceMeters: offRouteSmoothedDistanceMeters,
            observerAttachCount: observerAttachCount,
            observerDetachCount: observerDetachCount,
            activeObserverAttachCount: activeObserverAttachCount
        )
    }
}

struct DebugParitySnapshot {
    let dataSourceMode: String
    let voiceMode: String
    let promptSensitivity: String
    let hazardsEnabled: Bool
    let analyticsConsent: Bool
    let safetyAcknowledged: Bool
    let routesPackVersionId: String
    let hazardsPackVersionId: String
    let lastPromptFired: String
    let lastPromptSuppressed: String
    let lastOffRouteState: String
    let offRouteRawDistanceMeters: Double
    let offRouteSmoothedDistanceMeters: Double
    let observerAttachCount: Int
    let observerDetachCount: Int
    let activeObserverAttachCount: Int
}
