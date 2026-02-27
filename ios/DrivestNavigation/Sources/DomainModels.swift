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

struct PracticeRoute: Decodable {
    let id: String
    let name: String
    let geometry: [PracticeRoutePoint]
    let distanceM: Double
    let durationS: Double
    let startLat: Double
    let startLon: Double
    let hazards: [HazardFeature]?
}

struct PracticeRouteEnvelope: Decodable {
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

struct HazardFeature: Decodable {
    let id: String
    let type: PromptType
    let lat: Double
    let lon: Double
    let confidenceHint: Double?
    let sourceHazardType: String?
    let label: String?
    let signTitle: String?
    let signCode: String?
    let signImagePath: String?

    private enum CodingKeys: String, CodingKey {
        case id
        case type
        case lat
        case lon
        case confidenceHint
        case confidence
        case sourceHazardType
        case sourceHazardTypeSnake = "source_hazard_type"
        case label
        case signTitle
        case signTitleSnake = "sign_title"
        case signCode
        case signCodeSnake = "sign_code"
        case signImagePath
        case signImagePathSnake = "sign_image_path"
    }

    init(
        id: String,
        type: PromptType,
        lat: Double,
        lon: Double,
        confidenceHint: Double?,
        sourceHazardType: String? = nil,
        label: String? = nil,
        signTitle: String? = nil,
        signCode: String? = nil,
        signImagePath: String? = nil
    ) {
        self.id = id
        self.type = type
        self.lat = lat
        self.lon = lon
        self.confidenceHint = confidenceHint
        self.sourceHazardType = sourceHazardType
        self.label = label
        self.signTitle = signTitle
        self.signCode = signCode
        self.signImagePath = signImagePath
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        type = try container.decode(PromptType.self, forKey: .type)
        lat = try container.decode(Double.self, forKey: .lat)
        lon = try container.decode(Double.self, forKey: .lon)
        confidenceHint =
            try container.decodeIfPresent(Double.self, forKey: .confidenceHint) ??
            container.decodeIfPresent(Double.self, forKey: .confidence)
        sourceHazardType =
            try container.decodeIfPresent(String.self, forKey: .sourceHazardType) ??
            container.decodeIfPresent(String.self, forKey: .sourceHazardTypeSnake)
        label = try container.decodeIfPresent(String.self, forKey: .label)
        signTitle =
            try container.decodeIfPresent(String.self, forKey: .signTitle) ??
            container.decodeIfPresent(String.self, forKey: .signTitleSnake)
        signCode =
            try container.decodeIfPresent(String.self, forKey: .signCode) ??
            container.decodeIfPresent(String.self, forKey: .signCodeSnake)
        signImagePath =
            try container.decodeIfPresent(String.self, forKey: .signImagePath) ??
            container.decodeIfPresent(String.self, forKey: .signImagePathSnake)
    }
}

struct HazardEnvelope: Decodable {
    let hazards: [HazardFeature]
}
