import CoreLocation
import Foundation

struct TestCentre: Codable {
    let id: String
    let name: String
    let address: String
    let lat: Double
    let lon: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}

struct PracticeRoutePoint: Codable {
    let lat: Double
    let lon: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}

struct PracticeRoute: Codable {
    let id: String
    let name: String
    let geometry: [PracticeRoutePoint]
    let distanceM: Double
    let durationS: Double
    let startLat: Double
    let startLon: Double
}

struct PracticeRouteEnvelope: Codable {
    let routes: [PracticeRoute]
}

struct DestinationSuggestion: Codable, Hashable {
    let name: String
    let placeName: String
    let lat: Double
    let lon: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}

enum AppMode: String, Codable {
    case practice
    case navigation
}

struct HazardFeature: Codable {
    let id: String
    let type: PromptType
    let lat: Double
    let lon: Double
    let confidenceHint: Double?
}

struct HazardEnvelope: Codable {
    let hazards: [HazardFeature]
}
