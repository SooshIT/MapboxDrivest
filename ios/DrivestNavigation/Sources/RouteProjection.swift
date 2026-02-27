import CoreLocation
import Foundation

struct RouteProjectionResult {
    let projectedPoint: CLLocationCoordinate2D
    let segmentIndex: Int
    let segmentProgress: Double
    let distanceAlongMeters: Double
    let segmentBearingDegrees: Double
    let lateralDistanceMeters: Double
}

enum RouteProjection {
    static func projectPointOntoRoute(
        routePoints: [CLLocationCoordinate2D],
        target: CLLocationCoordinate2D
    ) -> RouteProjectionResult? {
        guard routePoints.count >= 2 else { return nil }

        let referenceLatRad = target.latitude * .pi / 180
        let targetX = metersX(longitude: target.longitude, referenceLatRad: referenceLatRad)
        let targetY = metersY(latitude: target.latitude)

        var cumulativeBeforeSegment = 0.0
        var best: RouteProjectionResult?
        var bestDistanceSquared = Double.greatestFiniteMagnitude

        for index in 0..<(routePoints.count - 1) {
            let start = routePoints[index]
            let end = routePoints[index + 1]

            let startX = metersX(longitude: start.longitude, referenceLatRad: referenceLatRad)
            let startY = metersY(latitude: start.latitude)
            let endX = metersX(longitude: end.longitude, referenceLatRad: referenceLatRad)
            let endY = metersY(latitude: end.latitude)

            let segmentX = endX - startX
            let segmentY = endY - startY
            let segmentLengthSquared = (segmentX * segmentX) + (segmentY * segmentY)
            if segmentLengthSquared <= 1e-6 {
                continue
            }

            let rawT = ((targetX - startX) * segmentX + (targetY - startY) * segmentY) /
                segmentLengthSquared
            let t = max(0.0, min(1.0, rawT))
            let projectedX = startX + (segmentX * t)
            let projectedY = startY + (segmentY * t)

            let dx = targetX - projectedX
            let dy = targetY - projectedY
            let distanceSquared = (dx * dx) + (dy * dy)
            let segmentLength = sqrt(segmentLengthSquared)

            if distanceSquared < bestDistanceSquared {
                bestDistanceSquared = distanceSquared
                best = RouteProjectionResult(
                    projectedPoint: CLLocationCoordinate2D(
                        latitude: latitudeFromMetersY(projectedY),
                        longitude: longitudeFromMetersX(
                            projectedX,
                            referenceLatRad: referenceLatRad
                        )
                    ),
                    segmentIndex: index,
                    segmentProgress: t,
                    distanceAlongMeters: cumulativeBeforeSegment + (segmentLength * t),
                    segmentBearingDegrees: bearingDegrees(start: start, end: end),
                    lateralDistanceMeters: sqrt(distanceSquared)
                )
            }

            cumulativeBeforeSegment += segmentLength
        }

        return best
    }

    static func alongDistanceAheadMeters(
        routePoints: [CLLocationCoordinate2D],
        userPoint: CLLocationCoordinate2D,
        featurePoint: CLLocationCoordinate2D
    ) -> Double? {
        guard let userProjection = projectPointOntoRoute(routePoints: routePoints, target: userPoint) else {
            return nil
        }
        guard let featureProjection = projectPointOntoRoute(routePoints: routePoints, target: featurePoint) else {
            return nil
        }
        return featureProjection.distanceAlongMeters - userProjection.distanceAlongMeters
    }

    private static func bearingDegrees(
        start: CLLocationCoordinate2D,
        end: CLLocationCoordinate2D
    ) -> Double {
        let lat1 = start.latitude * .pi / 180
        let lat2 = end.latitude * .pi / 180
        let dLon = (end.longitude - start.longitude) * .pi / 180
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        let bearing = atan2(y, x) * 180 / .pi
        return ((bearing.truncatingRemainder(dividingBy: 360)) + 360)
            .truncatingRemainder(dividingBy: 360)
    }

    private static func metersX(longitude: Double, referenceLatRad: Double) -> Double {
        longitude * .pi / 180 * earthRadiusMeters * cos(referenceLatRad)
    }

    private static func metersY(latitude: Double) -> Double {
        latitude * .pi / 180 * earthRadiusMeters
    }

    private static func longitudeFromMetersX(_ x: Double, referenceLatRad: Double) -> Double {
        let cosLat = max(1e-6, min(1.0, cos(referenceLatRad)))
        return (x / (earthRadiusMeters * cosLat)) * 180 / .pi
    }

    private static func latitudeFromMetersY(_ y: Double) -> Double {
        (y / earthRadiusMeters) * 180 / .pi
    }

    private static let earthRadiusMeters = 6_371_000.0
}
