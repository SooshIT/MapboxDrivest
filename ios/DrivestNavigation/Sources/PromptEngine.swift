import CoreLocation
import Foundation

enum PromptType: String, Codable, CaseIterable {
    case roundabout = "ROUNDABOUT"
    case miniRoundabout = "MINI_ROUNDABOUT"
    case schoolZone = "SCHOOL_ZONE"
    case zebraCrossing = "ZEBRA_CROSSING"
    case giveWay = "GIVE_WAY"
    case trafficSignal = "TRAFFIC_SIGNAL"
    case speedCamera = "SPEED_CAMERA"
    case busLane = "BUS_LANE"
    case busStop = "BUS_STOP"
    case noEntry = "NO_ENTRY"
    case unknown = "UNKNOWN"

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = (try? container.decode(String.self)) ?? ""
        let normalized = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "-", with: "_")
            .uppercased()
        if let mapped = PromptType(rawValue: normalized) {
            self = mapped
            return
        }

        switch normalized {
        case "TRAFFIC_LIGHT", "TRAFFIC_SIGNALS":
            self = .trafficSignal
        case "ZEBRA", "PEDESTRIAN_CROSSING":
            self = .zebraCrossing
        case "GIVE_WAY", "YIELD":
            self = .giveWay
        case "SCHOOL_WARNING":
            self = .schoolZone
        case "STOP_SIGN":
            // iOS intentionally treats stop-sign source data as non-blocking advisory unknown.
            self = .unknown
        default:
            self = .unknown
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

struct PromptEvent {
    let id: String
    let type: PromptType
    let message: String
    let featureId: String
    let priority: Int
    let distanceM: Int
    let expiresAtEpochMs: Int64
    let confidenceHint: Double
    let signTitle: String?
    let signCode: String?
    let signImagePath: String?
    let sourceLabel: String?
}

enum PromptSuppressionReason: String {
    case visualDisabled = "visual_disabled"
    case gpsAccuracyTooLow = "gps_accuracy_too_low"
    case maneuverTimeSuppression = "maneuver_time_suppression"
    case maneuverDistanceSuppression = "maneuver_distance_suppression"
    case noFeatures = "no_features"
    case allFeaturesBelowConfidence = "all_features_below_confidence"
    case allFeaturesDeduped = "all_features_deduped"
    case allFeaturesCoolingDown = "all_features_cooling_down"
    case noFeaturesWithinTriggerDistance = "no_features_within_trigger_distance"
    case noEligibleCandidates = "no_eligible_candidates"
}

struct PromptEvaluationResult {
    let promptEvent: PromptEvent?
    let suppressionReason: PromptSuppressionReason?
}

final class PromptEngine {
    private enum TriggerStage {
        case primary
        case secondary
    }

    private struct Candidate {
        let feature: HazardFeature
        let type: PromptType
        let stage: TriggerStage
        let distanceM: Int
        let priority: Int
    }

    private struct FeaturePromptState {
        var primaryFired = false
        var secondaryFired = false
        var primaryFiredDistanceM: Double?
    }

    private var featurePromptState: [String: FeaturePromptState] = [:]
    private var lastFiredAtByType: [PromptType: Int64] = [:]

    func evaluate(
        nowMs: Int64,
        locationLat: Double,
        locationLon: Double,
        gpsAccuracyM: Double,
        speedMps: Double,
        upcomingManeuverDistanceM: Double?,
        upcomingManeuverTimeS: Double?,
        features: [HazardFeature],
        visualEnabled: Bool,
        sensitivity: PromptSensitivity = .standard,
        routePolyline: [CLLocationCoordinate2D] = []
    ) -> PromptEvaluationResult {
        guard visualEnabled else {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .visualDisabled)
        }
        guard gpsAccuracyM <= 25 else {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .gpsAccuracyTooLow)
        }
        guard !features.isEmpty else {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .noFeatures)
        }

        var suppressedByConfidence = 0
        var suppressedByDedupe = 0
        var suppressedByCooldown = 0
        var suppressedByDistance = 0
        var suppressedByManeuver = 0

        var candidates: [Candidate] = []
        for feature in features {
            let type = feature.type
            if type == .unknown,
                feature.signTitle == nil,
                feature.signCode == nil,
                feature.signImagePath == nil
            {
                continue
            }
            let confidence = feature.confidenceHint ?? 0.5
            if confidence < visualMinConfidenceHint {
                suppressedByConfidence += 1
                continue
            }

            var state = featurePromptState[feature.id] ?? FeaturePromptState()
            let directDistance = haversineDistanceMeters(
                lat1: locationLat,
                lon1: locationLon,
                lat2: feature.lat,
                lon2: feature.lon
            )
            let alongAheadDistance: Double? = if routePolyline.count >= 2 {
                RouteProjection.alongDistanceAheadMeters(
                    routePoints: routePolyline,
                    userPoint: CLLocationCoordinate2D(latitude: locationLat, longitude: locationLon),
                    featurePoint: CLLocationCoordinate2D(latitude: feature.lat, longitude: feature.lon)
                )
            } else {
                nil
            }

            if let alongAheadDistance {
                if alongAheadDistance <= -passResetDistanceMeters {
                    featurePromptState.removeValue(forKey: feature.id)
                    continue
                }
            } else if directDistance > passResetDistanceMeters, state.primaryFired {
                featurePromptState.removeValue(forKey: feature.id)
                state = FeaturePromptState()
            }

            // Prefer along-route distance when available; fallback to direct distance.
            let distanceAheadMeters = alongAheadDistance ?? directDistance
            if distanceAheadMeters < 0 {
                continue
            }

            if shouldSuppressForManeuverConflict(
                promptType: type,
                distanceAheadMeters: distanceAheadMeters,
                speedMps: speedMps,
                upcomingManeuverDistanceM: upcomingManeuverDistanceM,
                upcomingManeuverTimeS: upcomingManeuverTimeS
            ) {
                suppressedByManeuver += 1
                continue
            }

            let stage = triggerStage(
                promptType: type,
                state: state,
                distanceAheadMeters: distanceAheadMeters,
                speedMps: speedMps,
                sensitivity: sensitivity
            )
            guard let stage else {
                if state.primaryFired {
                    suppressedByDedupe += 1
                } else {
                    suppressedByDistance += 1
                }
                continue
            }

            if shouldApplyTypeCooldown(type),
                let lastFired = lastFiredAtByType[type],
                nowMs - lastFired < typeCooldownMs
            {
                suppressedByCooldown += 1
                continue
            }

            candidates.append(
                Candidate(
                    feature: feature,
                    type: type,
                    stage: stage,
                    distanceM: Int(distanceAheadMeters.rounded()),
                    priority: priority(for: type) + (stage == .secondary ? 1 : 0)
                )
            )
        }

        guard let selected = candidates.sorted(
            by: {
                if $0.priority == $1.priority {
                    return $0.distanceM < $1.distanceM
                }
                return $0.priority > $1.priority
            }
        ).first else {
            let reason: PromptSuppressionReason
            if suppressedByManeuver == features.count {
                if let upcomingManeuverTimeS, upcomingManeuverTimeS < maneuverTimeSuppressionS {
                    reason = .maneuverTimeSuppression
                } else if let upcomingManeuverDistanceM,
                    upcomingManeuverDistanceM < maneuverDistanceSuppressionM
                {
                    reason = .maneuverDistanceSuppression
                } else {
                    reason = .noEligibleCandidates
                }
            } else if suppressedByConfidence == features.count {
                reason = .allFeaturesBelowConfidence
            } else if suppressedByDedupe == features.count {
                reason = .allFeaturesDeduped
            } else if suppressedByCooldown == features.count {
                reason = .allFeaturesCoolingDown
            } else if suppressedByDistance == features.count {
                reason = .noFeaturesWithinTriggerDistance
            } else {
                reason = .noEligibleCandidates
            }
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: reason)
        }

        var selectedState = featurePromptState[selected.feature.id] ?? FeaturePromptState()
        switch selected.stage {
        case .primary:
            selectedState.primaryFired = true
            if selectedState.primaryFiredDistanceM == nil {
                selectedState.primaryFiredDistanceM = Double(selected.distanceM)
            }
        case .secondary:
            selectedState.primaryFired = true
            selectedState.secondaryFired = true
            if selectedState.primaryFiredDistanceM == nil {
                selectedState.primaryFiredDistanceM = Double(selected.distanceM)
            }
        }
        featurePromptState[selected.feature.id] = selectedState
        lastFiredAtByType[selected.type] = nowMs

        return PromptEvaluationResult(
            promptEvent: PromptEvent(
                id: "\(selected.feature.id):\(nowMs)",
                type: selected.type,
                message: message(for: selected.type),
                featureId: selected.feature.id,
                priority: selected.priority,
                distanceM: selected.distanceM,
                expiresAtEpochMs: nowMs + 6_000,
                confidenceHint: selected.feature.confidenceHint ?? 0.5,
                signTitle: selected.feature.signTitle,
                signCode: selected.feature.signCode,
                signImagePath: selected.feature.signImagePath,
                sourceLabel: selected.feature.label
            ),
            suppressionReason: nil
        )
    }

    private func triggerStage(
        promptType: PromptType,
        state: FeaturePromptState,
        distanceAheadMeters: Double,
        speedMps: Double,
        sensitivity: PromptSensitivity
    ) -> TriggerStage? {
        let primaryTrigger = triggerDistance(
            for: promptType,
            speedMps: speedMps,
            sensitivity: sensitivity
        )
        switch promptType {
        case .busStop, .trafficSignal, .miniRoundabout:
            let secondaryTrigger = max(26, min(primaryTrigger * 0.65, 55))
            let eligibleForSecondary = (state.primaryFiredDistanceM ?? .greatestFiniteMagnitude) >
                secondaryTrigger
            if !state.primaryFired && distanceAheadMeters <= primaryTrigger {
                return .primary
            }
            if state.primaryFired,
                !state.secondaryFired,
                eligibleForSecondary,
                distanceAheadMeters <= secondaryTrigger,
                speedMps > secondaryMinSpeedMps
            {
                return .secondary
            }
            return nil

        case .speedCamera:
            let secondaryTrigger = max(35, min(primaryTrigger * 0.45, 90))
            let eligibleForSecondary = (state.primaryFiredDistanceM ?? .greatestFiniteMagnitude) >
                secondaryTrigger
            if !state.primaryFired && distanceAheadMeters <= primaryTrigger {
                return .primary
            }
            if state.primaryFired,
                !state.secondaryFired,
                eligibleForSecondary,
                distanceAheadMeters <= secondaryTrigger
            {
                return .secondary
            }
            return nil

        default:
            guard !state.primaryFired else { return nil }
            if distanceAheadMeters <= primaryTrigger {
                return .primary
            }
            return nil
        }
    }

    private func shouldApplyTypeCooldown(_ type: PromptType) -> Bool {
        switch type {
        case .busStop, .trafficSignal, .miniRoundabout, .speedCamera:
            return false
        default:
            return true
        }
    }

    private func triggerDistance(
        for type: PromptType,
        speedMps: Double,
        sensitivity: PromptSensitivity
    ) -> Double {
        let base: Double = switch type {
        case .roundabout:
            160
        case .miniRoundabout:
            95
        case .schoolZone:
            140
        case .zebraCrossing:
            110
        case .giveWay:
            95
        case .trafficSignal:
            130
        case .speedCamera:
            260
        case .busLane:
            140
        case .busStop:
            110
        case .noEntry:
            80
        case .unknown:
            100
        }
        let speedLead = min(max(speedMps * 4.6, 12), 110)
        let speedWeight: Double = switch type {
        case .speedCamera:
            0.90
        case .noEntry:
            0.25
        case .unknown:
            0.45
        default:
            0.65
        }
        let withSpeed = base + (speedLead * speedWeight)
        let multiplier: Double = switch sensitivity {
        case .minimal:
            0.80
        case .standard:
            1.0
        case .extraHelp:
            1.25
        }
        let maxDistance: Double = switch type {
        case .roundabout:
            220
        case .miniRoundabout:
            140
        case .schoolZone:
            210
        case .zebraCrossing:
            160
        case .giveWay:
            140
        case .trafficSignal:
            190
        case .speedCamera:
            380
        case .busLane:
            220
        case .busStop:
            160
        case .noEntry:
            130
        case .unknown:
            140
        }
        return min(withSpeed * multiplier, maxDistance)
    }

    private func shouldSuppressForManeuverConflict(
        promptType: PromptType,
        distanceAheadMeters: Double,
        speedMps: Double,
        upcomingManeuverDistanceM: Double?,
        upcomingManeuverTimeS: Double?
    ) -> Bool {
        guard let maneuverDistance = upcomingManeuverDistanceM,
            let maneuverTime = upcomingManeuverTimeS
        else {
            return false
        }

        // If hazard is already imminent, we should still alert even near a maneuver.
        let immediateDistance = max(
            30,
            min(
                triggerDistance(
                    for: promptType,
                    speedMps: speedMps,
                    sensitivity: .standard
                ) * 0.55,
                85
            )
        )
        if distanceAheadMeters <= immediateDistance {
            return false
        }

        let severeManeuverConflict = maneuverTime <= 1.3 || maneuverDistance <= 18
        let mildManeuverConflict = maneuverTime <= 2.0 || maneuverDistance <= 28
        switch promptType {
        case .busStop, .busLane, .speedCamera:
            return mildManeuverConflict
        case .unknown:
            return severeManeuverConflict
        default:
            return severeManeuverConflict
        }
    }

    private func priority(for type: PromptType) -> Int {
        switch type {
        case .noEntry: return 7
        case .roundabout: return 6
        case .miniRoundabout: return 5
        case .speedCamera: return 5
        case .schoolZone: return 4
        case .zebraCrossing: return 3
        case .giveWay: return 3
        case .trafficSignal: return 2
        case .busStop: return 2
        case .busLane: return 1
        case .unknown: return 2
        }
    }

    private func message(for type: PromptType) -> String {
        switch type {
        case .noEntry:
            return "Advisory: no entry ahead"
        case .roundabout:
            return "Advisory: roundabout ahead"
        case .miniRoundabout:
            return "Advisory: mini roundabout ahead"
        case .schoolZone:
            return "Advisory: school zone ahead"
        case .zebraCrossing:
            return "Advisory: zebra crossing ahead"
        case .giveWay:
            return "Advisory: give way ahead"
        case .trafficSignal:
            return "Advisory: traffic lights ahead"
        case .speedCamera:
            return "Advisory: speed camera ahead"
        case .busLane:
            return "Advisory: bus lane ahead"
        case .busStop:
            return "Advisory: bus stop ahead"
        case .unknown:
            return "Advisory: traffic sign ahead"
        }
    }

    private func haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ) -> Double {
        let radius = 6_371_000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
            sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radius * c
    }

    private let visualMinConfidenceHint = 0.60
    private let passResetDistanceMeters = 80.0
    private let maneuverTimeSuppressionS = 2.0
    private let maneuverDistanceSuppressionM = 25.0
    private let typeCooldownMs: Int64 = 60_000
    private let primaryAdvisoryTriggerMeters = 60.0
    private let secondaryAdvisoryTriggerMeters = 35.0
    private let speedCameraPrimaryTriggerMeters = 250.0
    private let speedCameraSecondaryTriggerMeters = 80.0
    private let noEntryTriggerMeters = 60.0
    private let secondaryMinSpeedMps = 6.7
}
