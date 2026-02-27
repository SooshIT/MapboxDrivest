import Foundation
import CoreLocation

private enum BackendIdentityKeys {
    static let appUserId = "drivest.backend.app_user_id"
    static let deviceId = "drivest.backend.device_id"
}

private final class BackendIdentityStore: @unchecked Sendable {
    static let shared = BackendIdentityStore()

    private let defaults = UserDefaults.standard

    var appUserId: String {
        if let existing = defaults.string(forKey: BackendIdentityKeys.appUserId), !existing.isEmpty {
            return existing
        }
        let generated = "ios-app-\(UUID().uuidString.lowercased())"
        defaults.set(generated, forKey: BackendIdentityKeys.appUserId)
        return generated
    }

    var deviceId: String {
        if let existing = defaults.string(forKey: BackendIdentityKeys.deviceId), !existing.isEmpty {
            return existing
        }
        let generated = "ios-device-\(UUID().uuidString.lowercased())"
        defaults.set(generated, forKey: BackendIdentityKeys.deviceId)
        return generated
    }
}

private func resolvedAPIBaseURL() -> String {
    let fallbackBaseURL = "https://api.drivest.uk"
    let configuredRaw = (Bundle.main.object(forInfoDictionaryKey: "APIBaseURL") as? String)?
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
    let configured = configuredRaw.map { raw -> String in
        if raw.contains("://") {
            return raw
        }
        return "https://\(raw)"
    }
    if let configured, !configured.isEmpty,
        let parsed = URL(string: configured),
        let scheme = parsed.scheme?.lowercased(),
        let host = parsed.host,
        (scheme == "https" || scheme == "http"),
        !host.isEmpty
    {
        return configured
    }
    if let configured, !configured.isEmpty {
        print("[Drivest iOS] invalid_api_base_url configured=\(configured) fallback=\(fallbackBaseURL)")
    }
    return fallbackBaseURL
}

private func applyBackendIdentityHeaders(_ request: inout URLRequest) {
    request.setValue(BackendIdentityStore.shared.appUserId, forHTTPHeaderField: "x-app-user-id")
    request.setValue(BackendIdentityStore.shared.deviceId, forHTTPHeaderField: "x-device-id")
}

final class CentreRepository {
    private struct BackendEnvelope<T: Decodable>: Decodable {
        let data: T?
    }

    private struct BackendPage<T: Decodable>: Decodable {
        let items: [T]
    }

    private struct BackendCentre: Decodable {
        struct GeoPoint: Decodable {
            let type: String?
            let coordinates: [Double]?
        }

        let id: String
        let slug: String?
        let name: String
        let address: String?
        let postcode: String?
        let city: String?
        let lat: Double?
        let lng: Double?
        let geo: GeoPoint?
    }

    private let settingsStore: SettingsStore
    private let session: URLSession
    private let apiBaseURL: String

    init(settingsStore: SettingsStore = .shared, session: URLSession = .shared) {
        self.settingsStore = settingsStore
        self.session = session
        self.apiBaseURL = resolvedAPIBaseURL()
    }

    func loadCentres() -> [TestCentre] {
        let assets = loadCentresFromAssets()
        switch settingsStore.dataSourceMode {
        case .assetsOnly:
            return assets
        case .backendOnly:
            return loadCentresFromBackend() ?? assets
        case .backendThenCacheThenAssets:
            guard let backend = loadCentresFromBackend(), !backend.isEmpty else { return assets }
            var byId: [String: TestCentre] = [:]
            for centre in assets + backend {
                byId[centre.id] = centre
            }
            return Array(byId.values).sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    func findById(_ centreId: String) -> TestCentre? {
        let key = centreId
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        guard !key.isEmpty else { return nil }

        let centres = loadCentres()
        if let exact = centres.first(where: { $0.id.lowercased() == key }) {
            return exact
        }
        return centres.first { centre in
            let normalizedId = centre.id.lowercased()
            let normalizedName = centre.name.lowercased()
            return normalizedId.contains(key) || normalizedName.contains(key)
        }
    }

    private func loadCentresFromAssets() -> [TestCentre] {
        guard
            let url = Bundle.main.url(forResource: "centres", withExtension: "json", subdirectory: "Data"),
            let data = try? Data(contentsOf: url),
            let centres = try? JSONDecoder().decode([TestCentre].self, from: data)
        else {
            return []
        }
        return centres
    }

    private func loadCentresFromBackend() -> [TestCentre]? {
        let endpoint = "\(apiBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/centres?limit=200"
        guard let url = URL(string: endpoint) else { return nil }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 3
        applyBackendIdentityHeaders(&request)

        let semaphore = DispatchSemaphore(value: 0)
        var mapped: [TestCentre]?
        let task = session.dataTask(with: request) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                print("[Drivest iOS] backend_centres_failed error=\(error.localizedDescription)")
                return
            }
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode), let data else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                print("[Drivest iOS] backend_centres_http_error status=\(code)")
                return
            }

            let decoder = JSONDecoder()
            if let wrapped = try? decoder.decode(BackendEnvelope<BackendPage<BackendCentre>>.self, from: data),
                let centres = wrapped.data?.items
            {
                mapped = self.mapBackendCentres(centres)
                return
            }
            if let directPage = try? decoder.decode(BackendPage<BackendCentre>.self, from: data) {
                mapped = self.mapBackendCentres(directPage.items)
                return
            }
            if let wrappedList = try? decoder.decode(BackendEnvelope<[BackendCentre]>.self, from: data),
                let centres = wrappedList.data
            {
                mapped = self.mapBackendCentres(centres)
                return
            }
            if let directList = try? decoder.decode([BackendCentre].self, from: data) {
                mapped = self.mapBackendCentres(directList)
                return
            }
            print("[Drivest iOS] backend_centres_decode_failed")
        }
        task.resume()

        let waitResult = semaphore.wait(timeout: .now() + 3)
        if waitResult == .timedOut {
            task.cancel()
            print("[Drivest iOS] backend_centres_timeout")
            return nil
        }
        return mapped
    }

    private func mapBackendCentres(_ centres: [BackendCentre]) -> [TestCentre] {
        centres.compactMap { centre in
            let resolvedLat = centre.lat ?? centre.geo?.coordinates?.dropFirst().first
            let resolvedLon = centre.lng ?? centre.geo?.coordinates?.first
            guard let lat = resolvedLat, let lon = resolvedLon else { return nil }
            let resolvedId = (centre.slug?.isEmpty == false ? centre.slug : centre.id) ?? centre.id
            let parts = [centre.address, centre.city, centre.postcode]
                .compactMap { value -> String? in
                    let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    return trimmed.isEmpty ? nil : trimmed
                }
            let resolvedAddress = parts.isEmpty ? centre.name : parts.joined(separator: ", ")
            return TestCentre(
                id: resolvedId,
                name: centre.name,
                address: resolvedAddress,
                lat: lat,
                lon: lon
            )
        }
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
    private struct BackendEnvelope<T: Decodable>: Decodable {
        let data: T?
    }

    private struct BackendRoutePayload: Decodable {
        struct HazardsV1: Decodable {
            let items: [HazardFeature]
        }

        let road_hazards_v1: HazardsV1?
    }

    private struct BackendRoute: Decodable {
        struct BackendGeoJSON: Decodable {
            struct Feature: Decodable {
                struct Geometry: Decodable {
                    let type: String?
                    let coordinates: [[Double]]?
                }

                let geometry: Geometry?
            }

            let features: [Feature]?
        }

        let id: String
        let name: String
        let distanceM: Double?
        let durationEstS: Double?
        let durationS: Double?
        let coordinates: [[Double]]?
        let polyline: String?
        let geojson: BackendGeoJSON?
        let payload: BackendRoutePayload?
    }

    private let settingsStore: SettingsStore
    private let assetsStore: AssetsPracticeRouteStore
    private let session: URLSession
    private let apiBaseURL: String

    init(
        settingsStore: SettingsStore = .shared,
        assetsStore: AssetsPracticeRouteStore = AssetsPracticeRouteStore(),
        session: URLSession = .shared
    ) {
        self.settingsStore = settingsStore
        self.assetsStore = assetsStore
        self.session = session
        self.apiBaseURL = resolvedAPIBaseURL()
    }

    func loadRoutesForCentre(_ centreId: String) -> [PracticeRoute] {
        switch settingsStore.dataSourceMode {
        case .backendOnly:
            guard let backendRoutes = loadRoutesFromBackend(centreId), !backendRoutes.isEmpty else {
                DebugParityStateStore.shared.routesPackVersionId = "routes-backend-unavailable"
                print("[Drivest iOS] routes_data_source backend_only_unavailable centre_id=\(centreId)")
                return []
            }
            DebugParityStateStore.shared.routesPackVersionId = "routes-backend-v1:\(centreId)"
            return backendRoutes

        case .assetsOnly:
            DebugParityStateStore.shared.routesPackVersionId = "routes-assets-disabled:\(centreId)"
            print("[Drivest iOS] routes_assets_disabled centre_id=\(centreId)")
            return []

        case .backendThenCacheThenAssets:
            if let backendRoutes = loadRoutesFromBackend(centreId), !backendRoutes.isEmpty {
                DebugParityStateStore.shared.routesPackVersionId = "routes-backend-v1:\(centreId)"
                return backendRoutes
            }
            DebugParityStateStore.shared.routesPackVersionId =
                "routes-backend-unavailable:\(centreId)"
            print("[Drivest iOS] routes_backend_unavailable_no_assets_fallback centre_id=\(centreId)")
            return []
        }
    }

    private func loadRoutesFromBackend(_ centreId: String) -> [PracticeRoute]? {
        let trimmedBase = apiBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let endpoint = "\(trimmedBase)/centres/\(centreId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? centreId)/routes"
        guard let url = URL(string: endpoint) else { return nil }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 3
        applyBackendIdentityHeaders(&request)

        let semaphore = DispatchSemaphore(value: 0)
        var parsedRoutes: [PracticeRoute]?

        let task = session.dataTask(with: request) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                print("[Drivest iOS] backend_routes_failed centre_id=\(centreId) error=\(error.localizedDescription)")
                return
            }
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode), let data else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                print("[Drivest iOS] backend_routes_http_error centre_id=\(centreId) status=\(code)")
                return
            }

            let decoder = JSONDecoder()
            if let wrapped = try? decoder.decode(BackendEnvelope<[BackendRoute]>.self, from: data),
                let routes = wrapped.data
            {
                parsedRoutes = self.mapBackendRoutes(routes)
                return
            }
            if let direct = try? decoder.decode([BackendRoute].self, from: data) {
                parsedRoutes = self.mapBackendRoutes(direct)
                return
            }
            print("[Drivest iOS] backend_routes_decode_failed centre_id=\(centreId)")
        }
        task.resume()

        let waitResult = semaphore.wait(timeout: .now() + 3)
        if waitResult == .timedOut {
            task.cancel()
            print("[Drivest iOS] backend_routes_timeout centre_id=\(centreId)")
            return nil
        }
        return parsedRoutes
    }

    private func mapBackendRoutes(_ backendRoutes: [BackendRoute]) -> [PracticeRoute] {
        backendRoutes.compactMap { route in
            let geometry = parseGeometry(route)
            guard !geometry.isEmpty else { return nil }

            let start = geometry[0]
            return PracticeRoute(
                id: route.id,
                name: route.name,
                geometry: geometry,
                distanceM: route.distanceM ?? 0,
                durationS: route.durationEstS ?? route.durationS ?? 0,
                startLat: start.lat,
                startLon: start.lon,
                hazards: route.payload?.road_hazards_v1?.items
            )
        }
    }

    private func parseGeometry(_ route: BackendRoute) -> [PracticeRoutePoint] {
        if let coordinates = route.coordinates {
            let mapped = mapLonLatPairs(coordinates)
            if !mapped.isEmpty { return mapped }
        }

        if let polyline = route.polyline?.trimmingCharacters(in: .whitespacesAndNewlines),
            !polyline.isEmpty,
            let data = polyline.data(using: .utf8),
            let coords = try? JSONDecoder().decode([[Double]].self, from: data)
        {
            let mapped = mapLonLatPairs(coords)
            if !mapped.isEmpty { return mapped }
        }

        if let line = route.geojson?.features?.first(where: { $0.geometry?.type == "LineString" }),
            let coordinates = line.geometry?.coordinates
        {
            let mapped = mapLonLatPairs(coordinates)
            if !mapped.isEmpty { return mapped }
        }

        return []
    }

    private func mapLonLatPairs(_ pairs: [[Double]]) -> [PracticeRoutePoint] {
        pairs.compactMap { pair in
            guard pair.count >= 2 else { return nil }
            return PracticeRoutePoint(lat: pair[1], lon: pair[0])
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
        guard
            let tokenRaw = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String
        else {
            print("[Drivest iOS] destination_search_missing_token")
            return []
        }
        let token = tokenRaw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else {
            print("[Drivest iOS] destination_search_empty_token")
            return []
        }

        let encodedQuery = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        let endpoint = "https://api.mapbox.com/geocoding/v5/mapbox.places/\(encodedQuery).json?autocomplete=true&limit=8&country=gb&language=en&access_token=\(token)"
        guard let url = URL(string: endpoint) else { return [] }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        do {
            let (data, response) = try await URLSession.shared.data(for: request, delegate: nil)
            guard let http = response as? HTTPURLResponse else {
                print("[Drivest iOS] destination_search_non_http_response")
                return []
            }
            guard (200...299).contains(http.statusCode) else {
                print("[Drivest iOS] destination_search_http_error status=\(http.statusCode)")
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
            print("[Drivest iOS] destination_search_failed error=\(error.localizedDescription)")
            return []
        }
    }
}

final class HazardRepository: @unchecked Sendable {
    private struct OverpassElement {
        let id: Int64
        let elementType: String
        let lat: Double?
        let lon: Double?
        let centroidLat: Double?
        let centroidLon: Double?
        let tags: [String: String]
    }

    private struct NestedRoadHazardsEnvelope: Decodable {
        struct RoadHazards: Decodable {
            let items: [HazardFeature]
        }

        let road_hazards_v1: RoadHazards?
    }

    private struct BackendEnvelope<T: Decodable>: Decodable {
        let data: T?
    }

    private struct HazardsPayload: Decodable {
        let hazards: [HazardFeature]?
        let items: [HazardFeature]?
        let road_hazards_v1: NestedRoadHazardsEnvelope.RoadHazards?
    }

    private let session: URLSession = .shared
    private let apiBaseURL: String

    init() {
        apiBaseURL = resolvedAPIBaseURL()
    }

    func loadHazardsForCentre(
        _ centreId: String,
        dataSourceMode: DataSourceMode = .backendThenCacheThenAssets
    ) -> [HazardFeature] {
        loadHazardsFromAssets(centreId)
    }

    func loadHazards(
        centreId: String?,
        routeCoordinates: [CLLocationCoordinate2D],
        dataSourceMode: DataSourceMode = .backendThenCacheThenAssets
    ) async -> [HazardFeature] {
        let trimmedCentreId = centreId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let normalizedCentreId = trimmedCentreId.isEmpty ? nil : trimmedCentreId
        let routeRadiusMeters = 220.0
        let requestedTypes = Set(supportedOverpassTypes)
        let assets = normalizedCentreId.map(loadHazardsFromAssets) ?? []
        if dataSourceMode == .assetsOnly {
            DebugParityStateStore.shared.hazardsPackVersionId =
                normalizedCentreId.map { "hazards-assets-v1:\($0)" } ?? "hazards-assets-v1:none"
            let routeScoped = filterToRouteCorridor(
                hazards: assets,
                routeCoordinates: routeCoordinates,
                radiusMeters: routeRadiusMeters
            )
            print("[Drivest iOS] hazards_assets_only centre_id=\(normalizedCentreId ?? "none") total=\(assets.count) route_scoped=\(routeScoped.count)")
            return routeScoped
        }

        if let routeScoped = await loadHazardsFromBackendRoute(
            centreId: normalizedCentreId,
            routeCoordinates: routeCoordinates
        ) {
            if !routeScoped.isEmpty {
                print("[Drivest iOS] hazards_backend_route centre_id=\(normalizedCentreId ?? "none") fetched=\(routeScoped.count)")
                let routeFiltered = filterToRouteCorridor(
                    hazards: routeScoped,
                    routeCoordinates: routeCoordinates,
                    radiusMeters: routeRadiusMeters
                )
                print("[Drivest iOS] hazards_backend_route_scoped centre_id=\(normalizedCentreId ?? "none") route_scoped=\(routeFiltered.count)")
                let merged = await mergeWithOverpassForMissingTypes(
                    baselineFeatures: routeFiltered,
                    routeCoordinates: routeCoordinates,
                    radiusMeters: routeRadiusMeters,
                    requestedTypes: requestedTypes
                )
                if merged.count > routeFiltered.count {
                    DebugParityStateStore.shared.hazardsPackVersionId =
                        normalizedCentreId.map { "hazards-backend-route-overpass-merged-v1:\($0)" } ?? "hazards-backend-route-overpass-merged-v1:none"
                } else {
                    DebugParityStateStore.shared.hazardsPackVersionId =
                        normalizedCentreId.map { "hazards-backend-route-v1:\($0)" } ?? "hazards-backend-route-v1:none"
                }
                return merged
            }
        }

        if let normalizedCentreId,
            let backendCentreHazards = await loadHazardsFromBackendCentre(centreId: normalizedCentreId),
            !backendCentreHazards.isEmpty
        {
            print("[Drivest iOS] hazards_backend_centre centre_id=\(normalizedCentreId) fetched=\(backendCentreHazards.count)")
            let routeFiltered = filterToRouteCorridor(
                hazards: backendCentreHazards,
                routeCoordinates: routeCoordinates,
                radiusMeters: routeRadiusMeters
            )
            print("[Drivest iOS] hazards_backend_centre_scoped centre_id=\(normalizedCentreId) route_scoped=\(routeFiltered.count)")
            let merged = await mergeWithOverpassForMissingTypes(
                baselineFeatures: routeFiltered,
                routeCoordinates: routeCoordinates,
                radiusMeters: routeRadiusMeters,
                requestedTypes: requestedTypes
            )
            if merged.count > routeFiltered.count {
                DebugParityStateStore.shared.hazardsPackVersionId = "hazards-backend-overpass-merged-v1:\(normalizedCentreId)"
            } else {
                DebugParityStateStore.shared.hazardsPackVersionId = "hazards-backend-v1:\(normalizedCentreId)"
            }
            return merged
        }

        switch dataSourceMode {
        case .backendOnly:
            DebugParityStateStore.shared.hazardsPackVersionId = "hazards-backend-unavailable"
            print("[Drivest iOS] hazards_backend_only_empty centre_id=\(normalizedCentreId ?? "none")")
            return []
        case .assetsOnly:
            DebugParityStateStore.shared.hazardsPackVersionId =
                normalizedCentreId.map { "hazards-assets-v1:\($0)" } ?? "hazards-assets-v1:none"
            print("[Drivest iOS] hazards_assets_only_fallback centre_id=\(normalizedCentreId ?? "none") total=\(assets.count)")
            return assets
        case .backendThenCacheThenAssets:
            DebugParityStateStore.shared.hazardsPackVersionId =
                assets.isEmpty
                ? "hazards-backend-fallback-assets-empty:\(normalizedCentreId ?? "none")"
                : "hazards-backend-fallback-assets-v1:\(normalizedCentreId ?? "none")"
            let routeScopedAssets = filterToRouteCorridor(
                hazards: assets,
                routeCoordinates: routeCoordinates,
                radiusMeters: routeRadiusMeters
            )
            print(
                "[Drivest iOS] hazards_backend_fallback_assets " +
                    "centre_id=\(normalizedCentreId ?? "none") assets_total=\(assets.count) route_scoped=\(routeScopedAssets.count)"
            )
            let merged = await mergeWithOverpassForMissingTypes(
                baselineFeatures: routeScopedAssets,
                routeCoordinates: routeCoordinates,
                radiusMeters: routeRadiusMeters,
                requestedTypes: requestedTypes
            )
            if !merged.isEmpty && routeScopedAssets.isEmpty {
                DebugParityStateStore.shared.hazardsPackVersionId = "hazards-overpass-v1:\(normalizedCentreId ?? "none")"
            } else if merged.count > routeScopedAssets.count {
                DebugParityStateStore.shared.hazardsPackVersionId = "hazards-overpass-merged-v1:\(normalizedCentreId ?? "none")"
            }
            return merged
        }
    }

    private func loadHazardsFromAssets(_ centreId: String) -> [HazardFeature] {
        guard
            let url = Bundle.main.url(
                forResource: "hazards",
                withExtension: "json",
                subdirectory: "Data/hazards/\(centreId)"
            ),
            let data = try? Data(contentsOf: url),
            let envelope = try? JSONDecoder().decode(HazardEnvelope.self, from: data)
        else {
            return []
        }
        return normalizeHazards(envelope.hazards)
    }

    private func loadHazardsFromBackendCentre(centreId: String) async -> [HazardFeature]? {
        let endpoint = "\(apiBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/centres/\(centreId)/hazards"
        guard let url = URL(string: endpoint) else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        applyBackendIdentityHeaders(&request)

        do {
            let (data, response) = try await session.data(for: request, delegate: nil)
            guard let http = response as? HTTPURLResponse else { return nil }
            guard (200...299).contains(http.statusCode) else {
                print("[Drivest iOS] backend_hazards_centre_http_error centre_id=\(centreId) status=\(http.statusCode)")
                return nil
            }
            return decodeHazardsPayload(data)
        } catch {
            print("[Drivest iOS] backend_hazards_centre_failed centre_id=\(centreId) error=\(error.localizedDescription)")
            return nil
        }
    }

    private func loadHazardsFromBackendRoute(
        centreId: String?,
        routeCoordinates: [CLLocationCoordinate2D]
    ) async -> [HazardFeature]? {
        guard routeCoordinates.count >= 2 else { return nil }
        guard let bbox = computeExpandedBbox(routeCoordinates: routeCoordinates, radiusMeters: 220) else { return nil }

        var components = URLComponents(
            string: "\(apiBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/hazards/route"
        )
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "south", value: String(format: "%.6f", bbox.south)),
            URLQueryItem(name: "west", value: String(format: "%.6f", bbox.west)),
            URLQueryItem(name: "north", value: String(format: "%.6f", bbox.north)),
            URLQueryItem(name: "east", value: String(format: "%.6f", bbox.east)),
            URLQueryItem(name: "types", value: supportedBackendTypes.joined(separator: ","))
        ]
        if let centreId, !centreId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "centreId", value: centreId))
        }
        components?.queryItems = queryItems
        guard let url = components?.url else { return nil }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        applyBackendIdentityHeaders(&request)

        do {
            let (data, response) = try await session.data(for: request, delegate: nil)
            guard let http = response as? HTTPURLResponse else { return nil }
            guard (200...299).contains(http.statusCode) else {
                print("[Drivest iOS] backend_hazards_route_http_error centre_id=\(centreId ?? "none") status=\(http.statusCode)")
                return nil
            }
            return decodeHazardsPayload(data)
        } catch {
            print("[Drivest iOS] backend_hazards_route_failed centre_id=\(centreId ?? "none") error=\(error.localizedDescription)")
            return nil
        }
    }

    private func decodeHazardsPayload(_ data: Data) -> [HazardFeature] {
        let decoder = JSONDecoder()

        if let envelope = try? JSONDecoder().decode(HazardEnvelope.self, from: data) {
            return normalizeHazards(envelope.hazards)
        }
        if let wrapped = try? decoder.decode(BackendEnvelope<HazardEnvelope>.self, from: data),
            let hazards = wrapped.data?.hazards
        {
            return normalizeHazards(hazards)
        }
        if let directList = try? decoder.decode([HazardFeature].self, from: data) {
            return normalizeHazards(directList)
        }
        if let wrappedList = try? decoder.decode(BackendEnvelope<[HazardFeature]>.self, from: data),
            let hazards = wrappedList.data
        {
            return normalizeHazards(hazards)
        }
        if let payload = try? decoder.decode(HazardsPayload.self, from: data) {
            if let hazards = payload.hazards {
                return normalizeHazards(hazards)
            }
            if let hazards = payload.items {
                return normalizeHazards(hazards)
            }
            if let hazards = payload.road_hazards_v1?.items {
                return normalizeHazards(hazards)
            }
        }
        if let wrappedPayload = try? decoder.decode(BackendEnvelope<HazardsPayload>.self, from: data),
            let payload = wrappedPayload.data
        {
            if let hazards = payload.hazards {
                return normalizeHazards(hazards)
            }
            if let hazards = payload.items {
                return normalizeHazards(hazards)
            }
            if let hazards = payload.road_hazards_v1?.items {
                return normalizeHazards(hazards)
            }
        }
        if let nested = try? decoder.decode(NestedRoadHazardsEnvelope.self, from: data),
            let hazards = nested.road_hazards_v1?.items
        {
            return normalizeHazards(hazards)
        }
        if let wrappedNested = try? decoder.decode(BackendEnvelope<NestedRoadHazardsEnvelope>.self, from: data),
            let hazards = wrappedNested.data?.road_hazards_v1?.items
        {
            return normalizeHazards(hazards)
        }
        return []
    }

    private func mergeWithOverpassForMissingTypes(
        baselineFeatures: [HazardFeature],
        routeCoordinates: [CLLocationCoordinate2D],
        radiusMeters: Double,
        requestedTypes: Set<PromptType>
    ) async -> [HazardFeature] {
        guard routeCoordinates.count >= 2 else { return baselineFeatures }
        guard !requestedTypes.isEmpty else { return baselineFeatures }

        if baselineFeatures.isEmpty {
            let overpassOnly = await loadHazardsFromOverpass(
                routeCoordinates: routeCoordinates,
                radiusMeters: Int(radiusMeters),
                types: requestedTypes
            )
            print("[Drivest iOS] hazards_overpass_only fetched=\(overpassOnly.count)")
            return overpassOnly
        }

        let presentTypes = Set(baselineFeatures.map(\.type))
        let missingTypes = requestedTypes.subtracting(presentTypes)
        guard !missingTypes.isEmpty else { return baselineFeatures }

        let missingFromOverpass = await loadHazardsFromOverpass(
            routeCoordinates: routeCoordinates,
            radiusMeters: Int(radiusMeters),
            types: missingTypes
        )
        print(
            "[Drivest iOS] hazards_overpass_merge baseline=\(baselineFeatures.count) " +
                "missing_types=\(missingTypes.map(\.rawValue).sorted()) fetched=\(missingFromOverpass.count)"
        )
        guard !missingFromOverpass.isEmpty else { return baselineFeatures }
        return normalizeHazards(baselineFeatures + missingFromOverpass)
    }

    private func loadHazardsFromOverpass(
        routeCoordinates: [CLLocationCoordinate2D],
        radiusMeters: Int,
        types: Set<PromptType>
    ) async -> [HazardFeature] {
        guard routeCoordinates.count >= 2 else { return [] }
        let allowedTypes = types.intersection(supportedOverpassTypes)
        guard !allowedTypes.isEmpty else { return [] }
        guard let bbox = computeExpandedBbox(
            routeCoordinates: routeCoordinates,
            radiusMeters: Double(radiusMeters)
        ) else {
            return []
        }
        let query = buildOverpassUnionQuery(
            south: bbox.south,
            west: bbox.west,
            north: bbox.north,
            east: bbox.east,
            types: allowedTypes
        )
        let routeRadius = max(100, min(300, Double(radiusMeters)))
        let primary = await executeOverpassFetch(
            query: query,
            routeCoordinates: routeCoordinates,
            routeRadiusMeters: routeRadius,
            allowedTypes: allowedTypes,
            attemptLabel: "union"
        )
        if !primary.isEmpty || allowedTypes.count <= 1 {
            return primary
        }

        print("[Drivest iOS] overpass_hazards_union_empty retrying_by_type count=\(allowedTypes.count)")
        var combined: [HazardFeature] = []
        for type in allowedTypes {
            let perTypeQuery = buildOverpassUnionQuery(
                south: bbox.south,
                west: bbox.west,
                north: bbox.north,
                east: bbox.east,
                types: [type]
            )
            let chunk = await executeOverpassFetch(
                query: perTypeQuery,
                routeCoordinates: routeCoordinates,
                routeRadiusMeters: routeRadius,
                allowedTypes: [type],
                attemptLabel: "per_type:\(type.rawValue)"
            )
            combined.append(contentsOf: chunk)
        }
        return normalizeHazards(combined)
    }

    private func executeOverpassFetch(
        query: String,
        routeCoordinates: [CLLocationCoordinate2D],
        routeRadiusMeters: Double,
        allowedTypes: Set<PromptType>,
        attemptLabel: String
    ) async -> [HazardFeature] {
        guard let url = URL(string: overpassEndpoint) else { return [] }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("Drivest-iOS/1.0 (+https://drivest.uk)", forHTTPHeaderField: "User-Agent")
        request.httpBody = "data=\(percentEncodeForForm(query))".data(using: .utf8)

        do {
            let (data, response) = try await session.data(for: request, delegate: nil)
            guard let http = response as? HTTPURLResponse else { return [] }
            guard (200...299).contains(http.statusCode) else {
                print("[Drivest iOS] overpass_hazards_http_error attempt=\(attemptLabel) status=\(http.statusCode)")
                return []
            }
            let elements = parseOverpassElements(from: data)
            let mapped = mapOverpassElements(elements, allowedTypes: allowedTypes)
            let routeScoped = filterToRouteCorridor(
                hazards: mapped,
                routeCoordinates: routeCoordinates,
                radiusMeters: routeRadiusMeters
            )
            print(
                "[Drivest iOS] overpass_hazards_success " +
                    "attempt=\(attemptLabel) elements=\(elements.count) mapped=\(mapped.count) route_scoped=\(routeScoped.count)"
            )
            return routeScoped
        } catch {
            print("[Drivest iOS] overpass_hazards_failed attempt=\(attemptLabel) error=\(error.localizedDescription)")
            return []
        }
    }

    private func parseOverpassElements(from data: Data) -> [OverpassElement] {
        guard
            let rootObject = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let elementsRaw = rootObject["elements"] as? [[String: Any]]
        else {
            return []
        }

        var parsed: [OverpassElement] = []
        parsed.reserveCapacity(elementsRaw.count)
        for raw in elementsRaw {
            let idValue: Int64
            if let id = raw["id"] as? Int64 {
                idValue = id
            } else if let id = raw["id"] as? Int {
                idValue = Int64(id)
            } else if let id = raw["id"] as? Double {
                idValue = Int64(id)
            } else {
                continue
            }
            guard idValue > 0 else { continue }
            guard let elementType = raw["type"] as? String, !elementType.isEmpty else { continue }

            let tags = raw["tags"] as? [String: String] ?? [:]
            let lat = raw["lat"] as? Double
            let lon = raw["lon"] as? Double
            let center = raw["center"] as? [String: Any]
            let centerLat = center?["lat"] as? Double
            let centerLon = center?["lon"] as? Double
            let geometry = raw["geometry"] as? [[String: Any]]
            let geometryCentroid = computeGeometryCentroid(geometry)

            parsed.append(
                OverpassElement(
                    id: idValue,
                    elementType: elementType,
                    lat: lat,
                    lon: lon,
                    centroidLat: centerLat ?? geometryCentroid?.lat,
                    centroidLon: centerLon ?? geometryCentroid?.lon,
                    tags: tags
                )
            )
        }
        return parsed
    }

    private func computeGeometryCentroid(_ geometry: [[String: Any]]?) -> (lat: Double, lon: Double)? {
        guard let geometry, !geometry.isEmpty else { return nil }
        var latSum = 0.0
        var lonSum = 0.0
        var count = 0.0
        for point in geometry {
            guard let lat = point["lat"] as? Double, let lon = point["lon"] as? Double else { continue }
            latSum += lat
            lonSum += lon
            count += 1
        }
        guard count > 0 else { return nil }
        return (latSum / count, lonSum / count)
    }

    private func mapOverpassElements(
        _ elements: [OverpassElement],
        allowedTypes: Set<PromptType>
    ) -> [HazardFeature] {
        var mapped: [HazardFeature] = []
        mapped.reserveCapacity(elements.count)

        for element in elements {
            let coordinate: (lat: Double, lon: Double)?
            if element.elementType == "node", let lat = element.lat, let lon = element.lon {
                coordinate = (lat, lon)
            } else if let lat = element.centroidLat, let lon = element.centroidLon {
                coordinate = (lat, lon)
            } else {
                coordinate = nil
            }
            guard let coordinate else { continue }

            let resolvedTypes = resolvePromptTypes(from: element.tags).intersection(allowedTypes)
            guard !resolvedTypes.isEmpty else { continue }
            let signMetadata = deriveSignMetadata(from: element.tags)

            for type in resolvedTypes {
                mapped.append(
                    HazardFeature(
                        id: "\(element.elementType):\(element.id):\(type.rawValue.lowercased())",
                        type: type,
                        lat: coordinate.lat,
                        lon: coordinate.lon,
                        confidenceHint: confidenceHint(for: type),
                        sourceHazardType: "osm_overpass",
                        label: signMetadata.label,
                        signTitle: signMetadata.title,
                        signCode: signMetadata.code,
                        signImagePath: signMetadata.imagePath
                    )
                )
            }
        }
        return normalizeHazards(mapped)
    }

    private func resolvePromptTypes(from tags: [String: String]) -> Set<PromptType> {
        guard !tags.isEmpty else { return [] }
        var types: Set<PromptType> = []
        let signTokens = trafficSignTokens(from: tags)

        if tags["highway"] == "traffic_signals" ||
            signTokens.contains(where: { $0.contains("traffic_signals") || $0.contains("signal") })
        {
            types.insert(.trafficSignal)
        }

        let isZebraCrossing = tags["crossing"] == "zebra" ||
            tags["crossing_ref"] == "zebra" ||
            (tags["highway"] == "crossing" &&
                (tags["crossing"] == "zebra" || tags["crossing_ref"] == "zebra")) ||
            signTokens.contains(where: { $0.contains("zebra") || $0.contains("pedestrian_crossing") })
        if isZebraCrossing {
            types.insert(.zebraCrossing)
        }

        let isGiveWay = tags["highway"] == "give_way" ||
            tags["give_way"] == "yes" ||
            signTokens.contains(where: { $0.contains("give_way") || $0.contains("give way") || $0.contains("yield") })
        if isGiveWay {
            types.insert(.giveWay)
        }

        let isSpeedCamera = tags["highway"] == "speed_camera" ||
            tags["enforcement"] == "speed_camera" ||
            tags["speed_camera"] == "yes" ||
            tags["camera:speed"] == "yes"
        if isSpeedCamera {
            types.insert(.speedCamera)
        }

        if tags["junction"] == "roundabout" {
            types.insert(.roundabout)
        }

        let isMiniRoundabout = tags["highway"] == "mini_roundabout" ||
            tags["junction"] == "mini_roundabout" ||
            tags["mini_roundabout"] == "yes" ||
            signTokens.contains(where: { $0.contains("mini_roundabout") })
        if isMiniRoundabout {
            types.insert(.miniRoundabout)
        }
        if signTokens.contains(where: { $0.contains("roundabout") }) {
            types.insert(.roundabout)
        }

        if tags["amenity"] == "school" || tags["landuse"] == "school" ||
            signTokens.contains(where: { $0.contains("school") })
        {
            types.insert(.schoolZone)
        }

        if tags.keys.contains("bus:lanes") ||
            tags.keys.contains("lanes:bus") ||
            tags.keys.contains("busway") ||
            tags.keys.contains("busway:left") ||
            tags.keys.contains("busway:right")
        {
            types.insert(.busLane)
        }

        let isBusStop = tags["highway"] == "bus_stop" ||
            (tags["public_transport"] == "stop_position" &&
                (tags["bus"] == "yes" || tags["bus"] == "designated")) ||
            (tags["public_transport"] == "platform" &&
                (tags["bus"] == "yes" || tags["highway"] == "bus_stop")) ||
            signTokens.contains(where: { $0.contains("bus_stop") || $0.contains("bus stop") })
        if isBusStop {
            types.insert(.busStop)
        }

        if isNoEntryOrRestriction(tags: tags) {
            types.insert(.noEntry)
        }

        if !signTokens.isEmpty && types.isEmpty {
            types.insert(.unknown)
        }

        return types
    }

    private func trafficSignTokens(from tags: [String: String]) -> [String] {
        let values = [
            tags["traffic_sign"],
            tags["traffic_sign:forward"],
            tags["traffic_sign:backward"],
            tags["traffic_sign:both"],
        ]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !values.isEmpty else { return [] }

        return values
            .flatMap { raw -> [String] in
                raw.split(whereSeparator: { ch in ch == ";" || ch == "," || ch == "|" })
                    .map { token in
                        token
                            .lowercased()
                            .replacingOccurrences(of: ":", with: "_")
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                    .filter { !$0.isEmpty }
            }
    }

    private func deriveSignMetadata(from tags: [String: String]) -> (
        label: String?,
        title: String?,
        code: String?,
        imagePath: String?
    ) {
        let tokens = trafficSignTokens(from: tags)
        guard let first = tokens.first else {
            return (nil, nil, nil, nil)
        }

        let code = first
        let title = first
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: "uk ", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .capitalized

        return (
            label: "osm_overpass",
            title: title.isEmpty ? nil : title,
            code: code,
            imagePath: nil
        )
    }

    private func isNoEntryOrRestriction(tags: [String: String]) -> Bool {
        // Keep no-entry classification strict. Broad restriction tags (access=no,
        // one-way direction, turn restrictions) produce false positives away from the
        // driver's lane, so only explicit no-entry signs are treated as NO_ENTRY prompts.
        hasNoEntryTrafficSign(tags: tags)
    }

    private func hasNoEntryTrafficSign(tags: [String: String]) -> Bool {
        let values = [
            tags["traffic_sign"],
            tags["traffic_sign:forward"],
            tags["traffic_sign:backward"],
        ]
        return values.contains { raw in
            guard let raw else { return false }
            let lower = raw.lowercased()
            return lower.contains("no_entry") || lower.contains("no entry")
        }
    }

    private func confidenceHint(for type: PromptType) -> Double {
        switch type {
        case .noEntry: return 0.92
        case .speedCamera: return 0.85
        case .giveWay: return 0.68
        case .busLane: return 0.30
        case .busStop: return 0.72
        case .schoolZone: return 0.60
        case .miniRoundabout: return 0.80
        case .roundabout: return 0.70
        case .zebraCrossing: return 0.70
        case .trafficSignal: return 0.80
        case .unknown: return 0.66
        }
    }

    private func buildOverpassUnionQuery(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        types: Set<PromptType>
    ) -> String {
        let clauses = types
            .sorted { $0.rawValue < $1.rawValue }
            .map { overpassClauses(for: $0, south: south, west: west, north: north, east: east) }
            .joined(separator: "\n")
        return """
        [out:json][timeout:20];
        (
        \(clauses)
        );
        out body center;
        """
    }

    private func overpassClauses(
        for type: PromptType,
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ) -> String {
        switch type {
        case .trafficSignal:
            return """
            node["highway"="traffic_signals"](\(south),\(west),\(north),\(east));
            way["highway"="traffic_signals"](\(south),\(west),\(north),\(east));
            """
        case .zebraCrossing:
            return """
            node["crossing"="zebra"](\(south),\(west),\(north),\(east));
            way["crossing"="zebra"](\(south),\(west),\(north),\(east));
            node["crossing_ref"="zebra"](\(south),\(west),\(north),\(east));
            way["crossing_ref"="zebra"](\(south),\(west),\(north),\(east));
            node["highway"="crossing"]["crossing"="zebra"](\(south),\(west),\(north),\(east));
            way["highway"="crossing"]["crossing"="zebra"](\(south),\(west),\(north),\(east));
            node["highway"="crossing"]["crossing_ref"="zebra"](\(south),\(west),\(north),\(east));
            way["highway"="crossing"]["crossing_ref"="zebra"](\(south),\(west),\(north),\(east));
            """
        case .giveWay:
            return """
            node["highway"="give_way"](\(south),\(west),\(north),\(east));
            way["highway"="give_way"](\(south),\(west),\(north),\(east));
            node["give_way"="yes"](\(south),\(west),\(north),\(east));
            way["give_way"="yes"](\(south),\(west),\(north),\(east));
            node["traffic_sign"~"give[_ ]?way", i](\(south),\(west),\(north),\(east));
            way["traffic_sign"~"give[_ ]?way", i](\(south),\(west),\(north),\(east));
            """
        case .speedCamera:
            return """
            node["highway"="speed_camera"](\(south),\(west),\(north),\(east));
            way["highway"="speed_camera"](\(south),\(west),\(north),\(east));
            node["enforcement"="speed_camera"](\(south),\(west),\(north),\(east));
            way["enforcement"="speed_camera"](\(south),\(west),\(north),\(east));
            node["speed_camera"="yes"](\(south),\(west),\(north),\(east));
            way["speed_camera"="yes"](\(south),\(west),\(north),\(east));
            node["camera:speed"="yes"](\(south),\(west),\(north),\(east));
            way["camera:speed"="yes"](\(south),\(west),\(north),\(east));
            """
        case .roundabout:
            return """
            node["junction"="roundabout"](\(south),\(west),\(north),\(east));
            way["junction"="roundabout"](\(south),\(west),\(north),\(east));
            """
        case .miniRoundabout:
            return """
            node["highway"="mini_roundabout"](\(south),\(west),\(north),\(east));
            way["highway"="mini_roundabout"](\(south),\(west),\(north),\(east));
            node["junction"="mini_roundabout"](\(south),\(west),\(north),\(east));
            way["junction"="mini_roundabout"](\(south),\(west),\(north),\(east));
            node["mini_roundabout"="yes"](\(south),\(west),\(north),\(east));
            way["mini_roundabout"="yes"](\(south),\(west),\(north),\(east));
            """
        case .schoolZone:
            return """
            node["amenity"="school"](\(south),\(west),\(north),\(east));
            way["amenity"="school"](\(south),\(west),\(north),\(east));
            node["landuse"="school"](\(south),\(west),\(north),\(east));
            way["landuse"="school"](\(south),\(west),\(north),\(east));
            """
        case .busLane:
            return """
            node["bus:lanes"](\(south),\(west),\(north),\(east));
            way["bus:lanes"](\(south),\(west),\(north),\(east));
            node["lanes:bus"](\(south),\(west),\(north),\(east));
            way["lanes:bus"](\(south),\(west),\(north),\(east));
            node["busway"](\(south),\(west),\(north),\(east));
            way["busway"](\(south),\(west),\(north),\(east));
            node["busway:left"](\(south),\(west),\(north),\(east));
            way["busway:left"](\(south),\(west),\(north),\(east));
            node["busway:right"](\(south),\(west),\(north),\(east));
            way["busway:right"](\(south),\(west),\(north),\(east));
            """
        case .busStop:
            return """
            node["highway"="bus_stop"](\(south),\(west),\(north),\(east));
            way["highway"="bus_stop"](\(south),\(west),\(north),\(east));
            node["public_transport"="stop_position"]["bus"="yes"](\(south),\(west),\(north),\(east));
            way["public_transport"="stop_position"]["bus"="yes"](\(south),\(west),\(north),\(east));
            node["public_transport"="platform"]["bus"="yes"](\(south),\(west),\(north),\(east));
            way["public_transport"="platform"]["bus"="yes"](\(south),\(west),\(north),\(east));
            """
        case .noEntry:
            return """
            node["traffic_sign"~"no[_ ]?entry", i](\(south),\(west),\(north),\(east));
            way["traffic_sign"~"no[_ ]?entry", i](\(south),\(west),\(north),\(east));
            node["traffic_sign:forward"~"no[_ ]?entry", i](\(south),\(west),\(north),\(east));
            way["traffic_sign:forward"~"no[_ ]?entry", i](\(south),\(west),\(north),\(east));
            node["traffic_sign:backward"~"no[_ ]?entry", i](\(south),\(west),\(north),\(east));
            way["traffic_sign:backward"~"no[_ ]?entry", i](\(south),\(west),\(north),\(east));
            node["no_entry"="yes"](\(south),\(west),\(north),\(east));
            way["no_entry"="yes"](\(south),\(west),\(north),\(east));
            """
        case .unknown:
            return """
            node["traffic_sign"](\(south),\(west),\(north),\(east));
            way["traffic_sign"](\(south),\(west),\(north),\(east));
            node["traffic_sign:forward"](\(south),\(west),\(north),\(east));
            way["traffic_sign:forward"](\(south),\(west),\(north),\(east));
            node["traffic_sign:backward"](\(south),\(west),\(north),\(east));
            way["traffic_sign:backward"](\(south),\(west),\(north),\(east));
            """
        }
    }

    private func percentEncodeForForm(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._* ")
        return value.addingPercentEncoding(withAllowedCharacters: allowed)?
            .replacingOccurrences(of: " ", with: "+") ?? value
    }

    private func normalizeHazards(_ hazards: [HazardFeature]) -> [HazardFeature] {
        hazards
            .filter {
                if $0.type != .unknown {
                    return true
                }
                return ($0.signTitle?.isEmpty == false) ||
                    ($0.signCode?.isEmpty == false) ||
                    ($0.signImagePath?.isEmpty == false)
            }
            .filter { $0.lat.isFinite && $0.lon.isFinite }
            .reduce(into: [String: HazardFeature]()) { partialResult, hazard in
                let signIdentity = (hazard.signCode ?? hazard.signTitle ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .lowercased()
                let key = [
                    hazard.type.rawValue,
                    String(format: "%.5f", hazard.lat),
                    String(format: "%.5f", hazard.lon),
                    signIdentity,
                ].joined(separator: ":")
                if let existing = partialResult[key] {
                    let existingConfidence = existing.confidenceHint ?? 0
                    let candidateConfidence = hazard.confidenceHint ?? 0
                    if candidateConfidence > existingConfidence {
                        partialResult[key] = hazard
                    }
                } else {
                    partialResult[key] = hazard
                }
            }
            .values
            .sorted { lhs, rhs in
                if lhs.type == rhs.type {
                    if lhs.lat == rhs.lat {
                        return lhs.lon < rhs.lon
                    }
                    return lhs.lat < rhs.lat
                }
                return lhs.type.rawValue < rhs.type.rawValue
            }
    }

    private func filterToRouteCorridor(
        hazards: [HazardFeature],
        routeCoordinates: [CLLocationCoordinate2D],
        radiusMeters: Double
    ) -> [HazardFeature] {
        guard routeCoordinates.count >= 2 else { return hazards }
        return hazards.filter { hazard in
            let hazardPoint = CLLocationCoordinate2D(latitude: hazard.lat, longitude: hazard.lon)
            let minimum = minimumDistanceToRouteMeters(point: hazardPoint, route: routeCoordinates)
            return minimum <= radiusMeters
        }
    }

    private func minimumDistanceToRouteMeters(
        point: CLLocationCoordinate2D,
        route: [CLLocationCoordinate2D]
    ) -> Double {
        guard route.count >= 2 else { return .greatestFiniteMagnitude }
        var minimumDistance = Double.greatestFiniteMagnitude
        for index in 0..<(route.count - 1) {
            let distance = pointToSegmentDistanceMeters(
                point: point,
                segmentStart: route[index],
                segmentEnd: route[index + 1]
            )
            minimumDistance = min(minimumDistance, distance)
        }
        return minimumDistance
    }

    private func pointToSegmentDistanceMeters(
        point: CLLocationCoordinate2D,
        segmentStart: CLLocationCoordinate2D,
        segmentEnd: CLLocationCoordinate2D
    ) -> Double {
        let referenceLatitude = point.latitude * .pi / 180
        let metersPerDegreeLat = 111_320.0
        let metersPerDegreeLon = cos(referenceLatitude) * 111_320.0

        let px = (point.longitude - segmentStart.longitude) * metersPerDegreeLon
        let py = (point.latitude - segmentStart.latitude) * metersPerDegreeLat
        let ex = (segmentEnd.longitude - segmentStart.longitude) * metersPerDegreeLon
        let ey = (segmentEnd.latitude - segmentStart.latitude) * metersPerDegreeLat

        let segmentLengthSquared = (ex * ex) + (ey * ey)
        if segmentLengthSquared <= 0.000001 {
            return hypot(px, py)
        }
        let t = max(0, min(1, ((px * ex) + (py * ey)) / segmentLengthSquared))
        let projectionX = t * ex
        let projectionY = t * ey
        return hypot(px - projectionX, py - projectionY)
    }

    private func computeExpandedBbox(
        routeCoordinates: [CLLocationCoordinate2D],
        radiusMeters: Double
    ) -> (south: Double, west: Double, north: Double, east: Double)? {
        guard !routeCoordinates.isEmpty else { return nil }
        var south = Double.greatestFiniteMagnitude
        var west = Double.greatestFiniteMagnitude
        var north = -Double.greatestFiniteMagnitude
        var east = -Double.greatestFiniteMagnitude
        var latSum = 0.0

        for coordinate in routeCoordinates {
            south = min(south, coordinate.latitude)
            west = min(west, coordinate.longitude)
            north = max(north, coordinate.latitude)
            east = max(east, coordinate.longitude)
            latSum += coordinate.latitude
        }

        let meanLat = latSum / Double(routeCoordinates.count)
        let latDelta = radiusMeters / 111_320.0
        let lonMetersPerDegree = max(abs(111_320.0 * cos(meanLat * .pi / 180)), 10_000.0)
        let lonDelta = radiusMeters / lonMetersPerDegree
        return (south - latDelta, west - lonDelta, north + latDelta, east + lonDelta)
    }

    private var supportedBackendTypes: [String] {
        PromptType.allCases
            .filter { $0 != .unknown }
            .map(\.rawValue)
    }

    private var supportedOverpassTypes: Set<PromptType> {
        [
            .roundabout,
            .miniRoundabout,
            .schoolZone,
            .zebraCrossing,
            .giveWay,
            .trafficSignal,
            .speedCamera,
            .busLane,
            .busStop,
            .noEntry,
            .unknown
        ]
    }

    private let overpassEndpoint = "https://overpass-api.de/api/interpreter"
}
