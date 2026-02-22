import Foundation

final class CentreRepository {
    func loadCentres() -> [TestCentre] {
        guard
            let url = Bundle.main.url(forResource: "centres", withExtension: "json", subdirectory: "Data"),
            let data = try? Data(contentsOf: url),
            let centres = try? JSONDecoder().decode([TestCentre].self, from: data)
        else {
            return []
        }
        return centres
    }

    func findById(_ centreId: String) -> TestCentre? {
        loadCentres().first { $0.id == centreId }
    }
}

protocol PracticeRouteStore {
    func loadRoutesForCentre(_ centreId: String) -> [PracticeRoute]
}

final class AssetsPracticeRouteStore: PracticeRouteStore {
    func loadRoutesForCentre(_ centreId: String) -> [PracticeRoute] {
        guard
            let url = Bundle.main.url(
                forResource: "routes",
                withExtension: "json",
                subdirectory: "Data/routes/\(centreId)"
            ),
            let data = try? Data(contentsOf: url),
            let envelope = try? JSONDecoder().decode(PracticeRouteEnvelope.self, from: data)
        else {
            return []
        }
        return envelope.routes
    }
}

final class DataSourcePracticeRouteStore: PracticeRouteStore {
    private let settingsStore: SettingsStore
    private let assetsStore: AssetsPracticeRouteStore

    init(
        settingsStore: SettingsStore = .shared,
        assetsStore: AssetsPracticeRouteStore = AssetsPracticeRouteStore()
    ) {
        self.settingsStore = settingsStore
        self.assetsStore = assetsStore
    }

    func loadRoutesForCentre(_ centreId: String) -> [PracticeRoute] {
        switch settingsStore.dataSourceMode {
        case .backendOnly:
            DebugParityStateStore.shared.routesPackVersionId = "routes-backend-unavailable"
            print("[Drivest iOS] routes_data_source backend_only_unavailable centre_id=\(centreId)")
            return []

        case .assetsOnly:
            let routes = assetsStore.loadRoutesForCentre(centreId)
            DebugParityStateStore.shared.routesPackVersionId = "routes-assets-v1:\(centreId)"
            return routes

        case .backendThenCacheThenAssets:
            let routes = assetsStore.loadRoutesForCentre(centreId)
            DebugParityStateStore.shared.routesPackVersionId =
                routes.isEmpty
                ? "routes-backend-fallback-assets-empty:\(centreId)"
                : "routes-backend-fallback-assets-v1:\(centreId)"
            return routes
        }
    }
}

final class DestinationSearchRepository {
    private struct GeocodingResponse: Codable {
        struct Feature: Codable {
            let text: String?
            let placeName: String?
            let center: [Double]?

            enum CodingKeys: String, CodingKey {
                case text
                case placeName = "place_name"
                case center
            }
        }

        let features: [Feature]
    }

    func search(query: String) async -> [DestinationSuggestion] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        guard let token = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String, !token.isEmpty else {
            return []
        }

        let encodedQuery = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        let endpoint = "https://api.mapbox.com/geocoding/v5/mapbox.places/\(encodedQuery).json?autocomplete=true&limit=8&country=gb&language=en&access_token=\(token)"
        guard let url = URL(string: endpoint) else { return [] }

        do {
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            let (data, response) = try await URLSession.shared.data(for: request, delegate: nil)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                return []
            }
            let decoded = try JSONDecoder().decode(GeocodingResponse.self, from: data)
            return decoded.features.compactMap { feature in
                guard let center = feature.center, center.count >= 2 else { return nil }
                let name = feature.text ?? feature.placeName ?? "Destination"
                let place = feature.placeName ?? name
                return DestinationSuggestion(
                    name: name,
                    placeName: place,
                    lat: center[1],
                    lon: center[0]
                )
            }
        } catch {
            return []
        }
    }
}

final class HazardRepository {
    func loadHazardsForCentre(
        _ centreId: String,
        dataSourceMode: DataSourceMode = .backendThenCacheThenAssets
    ) -> [HazardFeature] {
        if dataSourceMode == .backendOnly {
            DebugParityStateStore.shared.hazardsPackVersionId = "hazards-backend-unavailable"
            print("[Drivest iOS] hazards_data_source backend_only_unavailable centre_id=\(centreId)")
            return []
        }

        guard
            let url = Bundle.main.url(
                forResource: "hazards",
                withExtension: "json",
                subdirectory: "Data/hazards/\(centreId)"
            ),
            let data = try? Data(contentsOf: url),
            let envelope = try? JSONDecoder().decode(HazardEnvelope.self, from: data)
        else {
            DebugParityStateStore.shared.hazardsPackVersionId = "hazards-assets-missing:\(centreId)"
            return []
        }

        switch dataSourceMode {
        case .assetsOnly:
            DebugParityStateStore.shared.hazardsPackVersionId = "hazards-assets-v1:\(centreId)"
        case .backendOnly:
            DebugParityStateStore.shared.hazardsPackVersionId = "hazards-backend-unavailable"
        case .backendThenCacheThenAssets:
            DebugParityStateStore.shared.hazardsPackVersionId =
                envelope.hazards.isEmpty
                ? "hazards-backend-fallback-assets-empty:\(centreId)"
                : "hazards-backend-fallback-assets-v1:\(centreId)"
        }
        return envelope.hazards
    }
}
