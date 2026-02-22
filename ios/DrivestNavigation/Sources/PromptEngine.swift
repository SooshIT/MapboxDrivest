import Foundation

enum PromptType: String, Codable {
    case roundabout = "ROUNDABOUT"
    case miniRoundabout = "MINI_ROUNDABOUT"
    case schoolZone = "SCHOOL_ZONE"
    case zebraCrossing = "ZEBRA_CROSSING"
    case giveWay = "GIVE_WAY"
    case trafficSignal = "TRAFFIC_SIGNAL"
    case speedCamera = "SPEED_CAMERA"
    case busLane = "BUS_LANE"
    case busStop = "BUS_STOP"
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
    private var firedFeatureIds: Set<String> = []
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
        sensitivity: PromptSensitivity = .standard
    ) -> PromptEvaluationResult {
        guard visualEnabled else {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .visualDisabled)
        }
        guard gpsAccuracyM <= 25 else {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .gpsAccuracyTooLow)
        }
        if let upcomingManeuverTimeS, upcomingManeuverTimeS < 6 {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .maneuverTimeSuppression)
        }
        if let upcomingManeuverDistanceM, upcomingManeuverDistanceM < 80 {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .maneuverDistanceSuppression)
        }
        guard !features.isEmpty else {
            return PromptEvaluationResult(promptEvent: nil, suppressionReason: .noFeatures)
        }

        let _ = speedMps
        var suppressedByConfidence = 0
        var suppressedByDedupe = 0
        var suppressedByCooldown = 0
        var suppressedByDistance = 0

        var candidates: [(feature: HazardFeature, type: PromptType, distanceM: Int, priority: Int)] = []
        for feature in features {
            let type = feature.type
            let confidence = feature.confidenceHint ?? 0.5
            if confidence < visualMinConfidenceHint {
                suppressedByConfidence += 1
                continue
            }

            if firedFeatureIds.contains(feature.id) {
                suppressedByDedupe += 1
                continue
            }

            if let lastFired = lastFiredAtByType[type], nowMs - lastFired < 60_000 {
                suppressedByCooldown += 1
                continue
            }

            let distance = haversineDistanceMeters(
                lat1: locationLat,
                lon1: locationLon,
                lat2: feature.lat,
                lon2: feature.lon
            )
            if distance > triggerDistance(for: type, sensitivity: sensitivity) {
                suppressedByDistance += 1
                continue
            }

            candidates.append(
                (
                    feature: feature,
                    type: type,
                    distanceM: Int(distance.rounded()),
                    priority: priority(for: type)
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
            if suppressedByConfidence == features.count {
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

        firedFeatureIds.insert(selected.feature.id)
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
                confidenceHint: selected.feature.confidenceHint ?? 0.5
            ),
            suppressionReason: nil
        )
    }

    private func triggerDistance(for type: PromptType, sensitivity: PromptSensitivity) -> Double {
        let baseDistance: Double = switch type {
        case .roundabout: 250
        case .miniRoundabout: 190
        case .schoolZone: 250
        case .zebraCrossing: 120
        case .giveWay: 110
        case .trafficSignal: 150
        case .speedCamera: 220
        case .busLane: 250
        case .busStop: 120
        }

        let multiplier: Double = switch sensitivity {
        case .minimal: 0.80
        case .standard: 1.0
        case .extraHelp: 1.25
        }
        return baseDistance * multiplier
    }

    private func priority(for type: PromptType) -> Int {
        switch type {
        case .roundabout: return 6
        case .miniRoundabout: return 5
        case .speedCamera: return 5
        case .schoolZone: return 4
        case .zebraCrossing: return 3
        case .giveWay: return 3
        case .trafficSignal: return 2
        case .busStop: return 2
        case .busLane: return 1
        }
    }

    private func message(for type: PromptType) -> String {
        switch type {
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
}
