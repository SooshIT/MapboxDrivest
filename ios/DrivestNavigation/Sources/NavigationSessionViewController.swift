import CoreLocation
import MapKit
@preconcurrency import MapboxDirections
@preconcurrency import MapboxNavigationCore
@preconcurrency import MapboxNavigationUIKit
import UIKit

final class NavigationSessionViewController: UIViewController {
    private let mode: AppMode
    private let centre: TestCentre?
    private let practiceRoute: PracticeRoute?
    private let destination: DestinationSuggestion?
    private let settingsStore = SettingsStore.shared
    private let parityStateStore = DebugParityStateStore.shared
    private let hazardRepository = HazardRepository()
    private let promptEngine = PromptEngine()
    private let hazardVoiceController = HazardVoiceController()
    private let voiceModePolicy = HazardVoiceModePolicy()

    private lazy var mapboxNavigationProvider: MapboxNavigationProvider =
        makeNavigationProvider(initialCoordinate: buildCoordinateList().first)
    private var mapboxNavigation: MapboxNavigation {
        mapboxNavigationProvider.mapboxNavigation
    }

    private var previewRoutes: NavigationRoutes?
    private var previewLoaded = false

    private let previewMapView = MKMapView()
    private let summaryLabel = UILabel()
    private let startButton = UIButton(type: .system)
    private let voiceButton = UIButton(type: .system)
    private let promptBannerContainer = UIView()
    private let promptBannerLabel = UILabel()
    private let routeProgressContainer = UIView()
    private let routeProgressSummaryLabel = UILabel()
    private let routeProgressBar = UIProgressView(progressViewStyle: .default)
    private let speedometerCard = UIView()
    private let speedLimitBadgeLabel = UILabel()
    private let speedValueLabel = UILabel()
    private let speedUnitLabel = UILabel()

    private weak var activeNavigationViewController: NavigationViewController?
    private var promptAutoHideTask: Task<Void, Never>?
    private var hazardLoadTask: Task<Void, Never>?
    private var isHazardFetchInProgress = false
    private var hazardFeatures: [HazardFeature] = []
    private var lastHazardFetchAtMs: Int64 = 0
    private var lastHazardFetchAnchor: CLLocationCoordinate2D?
    private var lastPromptEvaluationMs: Int64 = 0
    private var visualPromptsEnabled = true
    private var isManeuverSpeechPlaying = false
    private var activeRouteCoordinates: [CLLocationCoordinate2D] = []
    private var offRouteSmoothedDistanceM: Double?
    private var lastSpeedSampleAtMs: Int64?
    private var lastDistanceRemainingForSpeedM: CLLocationDistance?
    private var lastEffectiveSpeedMps: Double = 0
    private var lastSpeedGateLogAtMs: Int64 = 0
    private var lastPromptTickLogAtMs: Int64 = 0
    private var lastPromptSuppressionLogAtMs: Int64 = 0

    private let minimumPromptEvaluationSpeedMps = 0.556
    private let minimumReliableSpeedMps = 0.278
    private let offRouteSmoothingAlpha = 0.35
    private let hazardVoiceMinConfidenceHint = 0.80
    private let hazardVoiceProximityMeters = 55
    private let noEntryVoiceProximityMeters = 45
    private let maxPracticePreviewWaypoints = 20
    private let hazardFetchIntervalMs: Int64 = 10 * 60 * 1000
    private let hazardFetchMovementMeters = 300.0
    private let promptNearbyQueryRadiusMeters = 300.0

    private lazy var etaFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    init(
        mode: AppMode,
        centre: TestCentre?,
        practiceRoute: PracticeRoute?,
        destination: DestinationSuggestion?
    ) {
        self.mode = mode
        self.centre = centre
        self.practiceRoute = practiceRoute
        self.destination = destination
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = mode == .practice ? "Practice Preview" : "Navigation Preview"
        view.backgroundColor = .clear
        visualPromptsEnabled = settingsStore.hazardsEnabled
        setupLayout()
        renderVoiceChip()
        renderPreviewMap(with: buildCoordinateList())
        loadHazardFeatures(routeCoordinates: buildCoordinateList(), force: true)
        requestPreviewRoute()
        observeSettings()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    deinit {
        NotificationCenter.default.removeObserver(self, name: .drivestSettingsChanged, object: nil)
    }

    private func setupLayout() {
        previewMapView.translatesAutoresizingMaskIntoConstraints = false
        previewMapView.layer.cornerRadius = 14
        previewMapView.layer.masksToBounds = true
        previewMapView.showsCompass = true
        previewMapView.delegate = self

        summaryLabel.translatesAutoresizingMaskIntoConstraints = false
        summaryLabel.numberOfLines = 0
        summaryLabel.textAlignment = .center
        summaryLabel.text = "Preparing route preview..."

        startButton.translatesAutoresizingMaskIntoConstraints = false
        startButton.configuration = .filled()
        startButton.setTitle("Start Guidance", for: .normal)
        startButton.titleLabel?.font = .systemFont(ofSize: 18, weight: .semibold)
        startButton.isEnabled = false
        startButton.addTarget(self, action: #selector(startNavigation), for: .touchUpInside)
        DrivestBrand.stylePrimaryButton(startButton)

        voiceButton.translatesAutoresizingMaskIntoConstraints = false
        voiceButton.configuration = .bordered()
        voiceButton.addTarget(self, action: #selector(cycleVoiceMode), for: .touchUpInside)
        DrivestBrand.styleOutlinedButton(voiceButton)

        view.addSubview(previewMapView)
        view.addSubview(summaryLabel)
        view.addSubview(startButton)
        view.addSubview(voiceButton)

        NSLayoutConstraint.activate([
            previewMapView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            previewMapView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            previewMapView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            previewMapView.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: 0.54),

            summaryLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            summaryLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            summaryLabel.topAnchor.constraint(equalTo: previewMapView.bottomAnchor, constant: 16),

            startButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            startButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            startButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
            startButton.heightAnchor.constraint(equalToConstant: 54),

            voiceButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            voiceButton.bottomAnchor.constraint(equalTo: startButton.topAnchor, constant: -16),
        ])
    }

    private func requestPreviewRoute() {
        let coordinates = buildCoordinateList()
        guard coordinates.count >= 2 else {
            summaryLabel.text = "Missing origin/destination for route preview."
            return
        }
        print(
            "[Drivest iOS] preview_route_request mode=\(mode.rawValue) waypoint_count=\(coordinates.count)"
        )

        Task { [weak self] in
            guard let self else { return }
            do {
                let navigationRoutes = try await self.calculatePreviewRoutes(primaryCoordinates: coordinates)
                await MainActor.run {
                    self.previewRoutes = navigationRoutes
                    let fullPracticeFallback = self.practiceRoute?.geometry.map(\.coordinate) ?? coordinates
                    self.activeRouteCoordinates = self.resolveRouteCoordinates(
                        route: navigationRoutes.mainRoute,
                        fallback: self.mode == .practice ? fullPracticeFallback : coordinates
                    )
                    self.renderPreviewMap(with: self.activeRouteCoordinates)
                    self.loadHazardFeatures(routeCoordinates: self.activeRouteCoordinates, force: true)
                    self.configureSimulatorRouteReplayIfNeeded(initialCoordinate: self.activeRouteCoordinates.first)
                    if self.mode == .navigation {
                        self.parityStateStore.routesPackVersionId = "routes-mapbox-live-preview"
                    }
                    self.previewLoaded = true
                    self.startButton.isEnabled = true
                    self.renderPreviewSummary()
                    #if targetEnvironment(simulator)
                        if self.activeNavigationViewController == nil {
                            Task { @MainActor [weak self] in
                                try? await Task.sleep(nanoseconds: 350_000_000)
                                self?.startNavigation()
                            }
                        }
                    #endif
                }
            } catch {
                await MainActor.run {
                    self.activeRouteCoordinates = coordinates
                    self.renderPreviewMap(with: coordinates)
                    self.loadHazardFeatures(routeCoordinates: coordinates, force: true)
                    self.summaryLabel.text = self.humanReadablePreviewError(error)
                    self.startButton.isEnabled = false
                }
                print(
                    "[Drivest iOS] preview_route_failed mode=\(self.mode.rawValue) " +
                        "waypoint_count=\(coordinates.count) error=\(error.localizedDescription)"
                )
            }
        }
    }

    private func calculatePreviewRoutes(primaryCoordinates: [CLLocationCoordinate2D]) async throws -> NavigationRoutes {
        if mode == .practice {
            let matchOptions = navigationMatchOptions(for: primaryCoordinates)
            let matchRequest = mapboxNavigation.routingProvider().calculateRoutes(options: matchOptions)
            switch await matchRequest.result {
            case .success(let routes):
                let legCount = routes.mainRoute.route.legs.count
                print("[Drivest iOS] preview_match_received legs=\(legCount)")
                if legCount <= 1 {
                    return routes
                }
                print("[Drivest iOS] preview_match_multi_leg_fallback_to_route legs=\(legCount)")

            case .failure(let matchError):
                print("[Drivest iOS] preview_match_failed_retrying_route error=\(matchError.localizedDescription)")
            }
        }

        let primaryOptions = navigationRouteOptions(for: primaryCoordinates)
        let primaryRequest = mapboxNavigation.routingProvider().calculateRoutes(options: primaryOptions)
        switch await primaryRequest.result {
        case .success(let routes):
            let legCount = routes.mainRoute.route.legs.count
            print("[Drivest iOS] preview_route_received legs=\(legCount)")
            if mode != .practice || legCount <= 1 {
                return routes
            }
            print("[Drivest iOS] preview_route_multi_leg_retrying_od legs=\(legCount)")
            guard
                let origin = primaryCoordinates.first,
                let destination = primaryCoordinates.last
            else {
                return routes
            }

            let fallbackOptions = navigationRouteOptions(for: [origin, destination])
            let fallbackRequest = mapboxNavigation.routingProvider().calculateRoutes(options: fallbackOptions)
            switch await fallbackRequest.result {
            case .success(let fallbackRoutes):
                print(
                    "[Drivest iOS] preview_fallback_od_used " +
                        "legs=\(fallbackRoutes.mainRoute.route.legs.count)"
                )
                return fallbackRoutes
            case .failure(let fallbackError):
                print("[Drivest iOS] preview_fallback_od_failed error=\(fallbackError.localizedDescription)")
                return routes
            }

        case .failure(let primaryError):
            // Practice routes can include intermediary shaping points. If provider rejects that shape,
            // retry with origin/destination only so guidance can still start.
            guard
                mode == .practice,
                primaryCoordinates.count > 2,
                let origin = primaryCoordinates.first,
                let destination = primaryCoordinates.last
            else {
                throw primaryError
            }

            print("[Drivest iOS] preview_primary_failed_retrying_od error=\(primaryError.localizedDescription)")

            let fallbackOptions = navigationRouteOptions(for: [origin, destination])
            let fallbackRequest = mapboxNavigation.routingProvider().calculateRoutes(options: fallbackOptions)
            switch await fallbackRequest.result {
            case .success(let routes):
                print("[Drivest iOS] preview_fallback_od_used")
                print("[Drivest iOS] preview_route_received legs=\(routes.mainRoute.route.legs.count)")
                return routes
            case .failure(let fallbackError):
                print("[Drivest iOS] preview_fallback_od_failed error=\(fallbackError.localizedDescription)")
                throw fallbackError
            }
        }
    }

    private func navigationRouteOptions(
        for coordinates: [CLLocationCoordinate2D]
    ) -> NavigationRouteOptions {
        let options = NavigationRouteOptions(coordinates: coordinates)
        guard mode == .practice, options.waypoints.count > 2 else { return options }

        let firstIndex = options.waypoints.startIndex
        let lastIndex = options.waypoints.index(before: options.waypoints.endIndex)
        for index in options.waypoints.indices where index != firstIndex && index != lastIndex {
            options.waypoints[index].separatesLegs = false
        }
        let separatorCount = options.waypoints.filter(\.separatesLegs).count
        print(
            "[Drivest iOS] practice_route_waypoints " +
                "total=\(options.waypoints.count) separators=\(separatorCount)"
        )
        return options
    }

    private func navigationMatchOptions(
        for coordinates: [CLLocationCoordinate2D]
    ) -> NavigationMatchOptions {
        let options = NavigationMatchOptions(coordinates: coordinates)
        guard options.waypoints.count > 2 else { return options }

        let firstIndex = options.waypoints.startIndex
        let lastIndex = options.waypoints.index(before: options.waypoints.endIndex)
        for index in options.waypoints.indices where index != firstIndex && index != lastIndex {
            options.waypoints[index].separatesLegs = false
        }
        let separatorCount = options.waypoints.filter(\.separatesLegs).count
        print(
            "[Drivest iOS] practice_match_waypoints " +
                "total=\(options.waypoints.count) separators=\(separatorCount)"
        )
        return options
    }

    private func buildCoordinateList() -> [CLLocationCoordinate2D] {
        if mode == .practice, let practiceRoute {
            let fullGeometry = practiceRoute.geometry.map(\.coordinate)
            let simplified = simplifiedPracticePreviewCoordinates(
                from: fullGeometry,
                maxWaypoints: maxPracticePreviewWaypoints
            )
            return simplified
        }

        let fallbackOrigin = CLLocationCoordinate2D(latitude: 51.872116, longitude: 0.928174)
        let origin = centre?.coordinate ?? fallbackOrigin
        guard let destination else { return [origin] }
        return [origin, destination.coordinate]
    }

    private func renderPreviewSummary() {
        guard previewLoaded, let route = previewRoutes?.mainRoute else {
            summaryLabel.text = "Preview not available."
            return
        }
        let distanceMiles = route.route.distance / 1609.344
        let durationMinutes = route.route.expectedTravelTime / 60.0
        let summaryName = destination?.placeName ?? practiceRoute?.name ?? "Route"
        summaryLabel.text = String(
            format: "%@\n%.1f mi - %.0f min",
            summaryName,
            distanceMiles,
            durationMinutes
        )
    }

    private func renderPreviewMap(with coordinates: [CLLocationCoordinate2D]) {
        previewMapView.removeOverlays(previewMapView.overlays)
        previewMapView.removeAnnotations(previewMapView.annotations)

        guard coordinates.count >= 2 else {
            if let first = coordinates.first {
                let region = MKCoordinateRegion(
                    center: first,
                    span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
                )
                previewMapView.setRegion(region, animated: false)
            }
            return
        }

        let polyline = MKPolyline(coordinates: coordinates, count: coordinates.count)
        previewMapView.addOverlay(polyline)
        previewMapView.setVisibleMapRect(
            polyline.boundingMapRect,
            edgePadding: UIEdgeInsets(top: 50, left: 50, bottom: 50, right: 50),
            animated: false
        )

        let start = MKPointAnnotation()
        start.coordinate = coordinates.first ?? coordinates[0]
        start.title = "Start"

        let end = MKPointAnnotation()
        end.coordinate = coordinates.last ?? coordinates[coordinates.count - 1]
        end.title = "End"

        previewMapView.addAnnotations([start, end])
    }

    private func humanReadablePreviewError(_ error: Error) -> String {
        let message = error.localizedDescription
        let lower = message.lowercased()
        if lower.contains("not authorized") || lower.contains("invalid token") || lower.contains("401") {
            return "Preview failed: Mapbox token is not authorized for navigation."
        }
        if lower.contains("network") || lower.contains("offline") {
            return "Preview failed: Network unavailable. Check connection and retry."
        }
        return "Preview failed: \(message)"
    }

    @objc
    private func startNavigation() {
        guard previewLoaded, let previewRoutes else { return }
        guard activeNavigationViewController == nil else { return }
        configureSimulatorRouteReplayIfNeeded(initialCoordinate: activeRouteCoordinates.first)
        print(
            "[Drivest iOS] session_start " +
                "mode=\(mode.rawValue) " +
                "voice_mode=\(settingsStore.voiceMode.rawValue) " +
                "legs=\(previewRoutes.mainRoute.route.legs.count) " +
                "waypoints=\(previewRoutes.mainRoute.route.legs.count + 1)"
        )
        parityStateStore.markObserverAttached()

        let navigationOptions = NavigationOptions(
            mapboxNavigation: mapboxNavigation,
            voiceController: mapboxNavigationProvider.routeVoiceController,
            eventsManager: mapboxNavigationProvider.eventsManager()
        )

        let navigationVC = NavigationViewController(
            navigationRoutes: previewRoutes,
            navigationOptions: navigationOptions
        )
        navigationVC.delegate = self
        navigationVC.routeLineTracksTraversal = true
        navigationVC.showsSpeedLimits = true
        attachPromptBanner(on: navigationVC.view)
        activeNavigationViewController = navigationVC
        applyVoiceModeStatusText()
        present(navigationVC, animated: true)
    }

    @objc
    private func cycleVoiceMode() {
        let nextMode = settingsStore.cycleVoiceMode()
        print("[Drivest iOS] voice_mode_changed mode=\(nextMode.rawValue)")
        if nextMode == .mute {
            hazardVoiceController.stopSpeaking()
        }
        renderVoiceChip()
        applyVoiceModeStatusText()
    }

    private func renderVoiceChip() {
        let title = switch settingsStore.voiceMode {
        case .all:
            "Voice: All"
        case .alerts:
            "Voice: Alerts"
        case .mute:
            "Voice: Mute"
        }
        voiceButton.setTitle(title, for: .normal)
    }

    private func applyVoiceModeStatusText() {
        // Avoid UINavigationBar prompt constraint conflicts on iOS 18+; the voice chip already shows state.
        navigationItem.prompt = nil
        activeNavigationViewController?.navigationItem.prompt = nil
        navigationController?.navigationBar.topItem?.prompt = nil
    }

    private func loadHazardFeatures(
        routeCoordinates: [CLLocationCoordinate2D],
        force: Bool = false
    ) {
        guard !isHazardFetchInProgress || force else { return }
        let centreId = centre?.id ?? settingsStore.lastCentreId
        let route = routeCoordinates
        if force {
            hazardLoadTask?.cancel()
        }
        isHazardFetchInProgress = true
        hazardLoadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer { self.isHazardFetchInProgress = false }
            let loaded = await self.hazardRepository.loadHazards(
                centreId: centreId,
                routeCoordinates: route,
                dataSourceMode: self.settingsStore.dataSourceMode
            )
            guard !Task.isCancelled else { return }
            let routePayloadHazards = self.practiceRoute?.hazards ?? []
            let routePayloadScoped = self.filterHazardsToRouteCorridor(
                routePayloadHazards,
                routeCoordinates: route,
                radiusMeters: 220
            )
            let mergedHazards = self.mergeHazards(primary: loaded, secondary: routePayloadScoped)
            self.hazardFeatures = mergedHazards
            self.lastHazardFetchAtMs = Int64(Date().timeIntervalSince1970 * 1000)
            let byType = Dictionary(grouping: mergedHazards, by: \.type)
                .map { "\($0.key.rawValue)=\($0.value.count)" }
                .sorted()
                .joined(separator: ",")
            print(
                "[Drivest iOS] loaded_hazards centre_id=\(centreId) mode=\(self.settingsStore.dataSourceMode.rawValue) " +
                    "count=\(mergedHazards.count) by_type={\(byType)}"
            )
        }
    }

    private func attachPromptBanner(on containerView: UIView) {
        promptBannerContainer.removeFromSuperview()
        routeProgressContainer.removeFromSuperview()
        speedometerCard.removeFromSuperview()

        promptBannerContainer.translatesAutoresizingMaskIntoConstraints = false
        promptBannerContainer.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.95)
        promptBannerContainer.layer.cornerRadius = 10
        promptBannerContainer.layer.zPosition = 999
        promptBannerContainer.isHidden = true

        promptBannerLabel.translatesAutoresizingMaskIntoConstraints = false
        promptBannerLabel.textColor = .white
        promptBannerLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        promptBannerLabel.numberOfLines = 2

        promptBannerContainer.addSubview(promptBannerLabel)
        containerView.addSubview(promptBannerContainer)

        NSLayoutConstraint.activate([
            promptBannerContainer.leadingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            promptBannerContainer.trailingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.trailingAnchor, constant: -12),
            promptBannerContainer.topAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.topAnchor, constant: 92),

            promptBannerLabel.leadingAnchor.constraint(equalTo: promptBannerContainer.leadingAnchor, constant: 12),
            promptBannerLabel.trailingAnchor.constraint(equalTo: promptBannerContainer.trailingAnchor, constant: -12),
            promptBannerLabel.topAnchor.constraint(equalTo: promptBannerContainer.topAnchor, constant: 10),
            promptBannerLabel.bottomAnchor.constraint(equalTo: promptBannerContainer.bottomAnchor, constant: -10)
        ])

        setupRouteProgressOverlay(on: containerView)
        setupSpeedometerOverlay(on: containerView)
    }

    private func setupRouteProgressOverlay(on containerView: UIView) {
        routeProgressContainer.translatesAutoresizingMaskIntoConstraints = false
        routeProgressContainer.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.90)
        routeProgressContainer.layer.cornerRadius = 12
        routeProgressContainer.layer.masksToBounds = true
        routeProgressContainer.layer.zPosition = 999
        routeProgressContainer.isHidden = true

        routeProgressSummaryLabel.translatesAutoresizingMaskIntoConstraints = false
        routeProgressSummaryLabel.textColor = .white
        routeProgressSummaryLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        routeProgressSummaryLabel.numberOfLines = 1

        routeProgressBar.translatesAutoresizingMaskIntoConstraints = false
        routeProgressBar.trackTintColor = UIColor.white.withAlphaComponent(0.25)
        routeProgressBar.progressTintColor = DrivestPalette.accentPrimary
        routeProgressBar.progress = 0

        routeProgressContainer.addSubview(routeProgressSummaryLabel)
        routeProgressContainer.addSubview(routeProgressBar)
        containerView.addSubview(routeProgressContainer)

        NSLayoutConstraint.activate([
            routeProgressContainer.leadingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            routeProgressContainer.trailingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.trailingAnchor, constant: -12),
            routeProgressContainer.bottomAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.bottomAnchor, constant: -96),

            routeProgressSummaryLabel.leadingAnchor.constraint(equalTo: routeProgressContainer.leadingAnchor, constant: 12),
            routeProgressSummaryLabel.trailingAnchor.constraint(equalTo: routeProgressContainer.trailingAnchor, constant: -12),
            routeProgressSummaryLabel.topAnchor.constraint(equalTo: routeProgressContainer.topAnchor, constant: 10),

            routeProgressBar.leadingAnchor.constraint(equalTo: routeProgressContainer.leadingAnchor, constant: 12),
            routeProgressBar.trailingAnchor.constraint(equalTo: routeProgressContainer.trailingAnchor, constant: -12),
            routeProgressBar.topAnchor.constraint(equalTo: routeProgressSummaryLabel.bottomAnchor, constant: 8),
            routeProgressBar.bottomAnchor.constraint(equalTo: routeProgressContainer.bottomAnchor, constant: -10),
            routeProgressBar.heightAnchor.constraint(equalToConstant: 4)
        ])
    }

    private func setupSpeedometerOverlay(on containerView: UIView) {
        speedometerCard.translatesAutoresizingMaskIntoConstraints = false
        speedometerCard.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.92)
        speedometerCard.layer.cornerRadius = 14
        speedometerCard.layer.masksToBounds = true
        speedometerCard.layer.zPosition = 999
        speedometerCard.isHidden = true

        speedLimitBadgeLabel.translatesAutoresizingMaskIntoConstraints = false
        speedLimitBadgeLabel.textAlignment = .center
        speedLimitBadgeLabel.font = .systemFont(ofSize: 17, weight: .bold)
        speedLimitBadgeLabel.textColor = .white
        speedLimitBadgeLabel.backgroundColor = UIColor.white.withAlphaComponent(0.2)
        speedLimitBadgeLabel.layer.cornerRadius = 18
        speedLimitBadgeLabel.layer.masksToBounds = true
        speedLimitBadgeLabel.isHidden = true

        speedValueLabel.translatesAutoresizingMaskIntoConstraints = false
        speedValueLabel.textAlignment = .center
        speedValueLabel.font = .systemFont(ofSize: 30, weight: .bold)
        speedValueLabel.textColor = .white
        speedValueLabel.text = "0"

        speedUnitLabel.translatesAutoresizingMaskIntoConstraints = false
        speedUnitLabel.textAlignment = .center
        speedUnitLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        speedUnitLabel.textColor = UIColor.white.withAlphaComponent(0.88)
        speedUnitLabel.text = settingsStore.unitsMode == .metricKmh ? "km/h" : "mph"

        speedometerCard.addSubview(speedLimitBadgeLabel)
        speedometerCard.addSubview(speedValueLabel)
        speedometerCard.addSubview(speedUnitLabel)
        containerView.addSubview(speedometerCard)

        NSLayoutConstraint.activate([
            speedometerCard.leadingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            speedometerCard.bottomAnchor.constraint(equalTo: routeProgressContainer.topAnchor, constant: -12),
            speedometerCard.widthAnchor.constraint(equalToConstant: 86),
            speedometerCard.heightAnchor.constraint(equalToConstant: 118),

            speedLimitBadgeLabel.topAnchor.constraint(equalTo: speedometerCard.topAnchor, constant: 8),
            speedLimitBadgeLabel.centerXAnchor.constraint(equalTo: speedometerCard.centerXAnchor),
            speedLimitBadgeLabel.widthAnchor.constraint(equalToConstant: 36),
            speedLimitBadgeLabel.heightAnchor.constraint(equalToConstant: 36),

            speedValueLabel.centerXAnchor.constraint(equalTo: speedometerCard.centerXAnchor),
            speedValueLabel.topAnchor.constraint(equalTo: speedLimitBadgeLabel.bottomAnchor, constant: 8),

            speedUnitLabel.centerXAnchor.constraint(equalTo: speedometerCard.centerXAnchor),
            speedUnitLabel.topAnchor.constraint(equalTo: speedValueLabel.bottomAnchor, constant: 2),
            speedUnitLabel.bottomAnchor.constraint(lessThanOrEqualTo: speedometerCard.bottomAnchor, constant: -8)
        ])
    }

    private func showPromptBanner(_ message: String) {
        promptBannerLabel.text = message
        promptBannerContainer.isHidden = false
        promptAutoHideTask?.cancel()
        promptAutoHideTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 6_000_000_000)
            await MainActor.run {
                self?.promptBannerContainer.isHidden = true
            }
        }
    }

    private func observeSettings() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSettingsChanged),
            name: .drivestSettingsChanged,
            object: nil
        )
    }

    @objc
    private func handleSettingsChanged() {
        visualPromptsEnabled = settingsStore.hazardsEnabled
    }

}

extension NavigationSessionViewController: NavigationViewControllerDelegate {
    nonisolated func navigationViewController(
        _ navigationViewController: NavigationViewController,
        didUpdate progress: RouteProgress,
        with location: CLLocation,
        rawLocation: CLLocation
    ) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            let effectiveSpeedMps = self.resolveEffectiveSpeedMps(
                snappedLocation: location,
                rawLocation: rawLocation,
                progress: progress,
                nowMs: nowMs
            )

            self.updateOffRouteDiagnostics(progress: progress, location: location)
            self.updateRouteProgressOverlay(progress: progress)
            self.updateSpeedometerOverlay(
                speedMps: effectiveSpeedMps,
                navigationViewController: navigationViewController
            )

            if nowMs - self.lastPromptEvaluationMs < 1000 {
                return
            }
            self.lastPromptEvaluationMs = nowMs
            self.maybeRefreshHazardsIfNeeded(nowMs: nowMs, location: location)

            let upcomingDistance = progress.currentLegProgress.currentStepProgress.distanceRemaining
            let upcomingTime = progress.currentLegProgress.currentStepProgress.durationRemaining
            let nearbyFeatures = self.filterHazardsNearLocation(
                self.hazardFeatures,
                coordinate: location.coordinate,
                radiusMeters: self.promptNearbyQueryRadiusMeters
            )
            let snappedAccuracyM = max(location.horizontalAccuracy, 0)
            let rawAccuracyM = max(rawLocation.horizontalAccuracy, 0)
            var effectiveGpsAccuracyM: Double = {
                let candidate = min(snappedAccuracyM, rawAccuracyM)
                if candidate > 0 {
                    return candidate
                }
                return max(snappedAccuracyM, rawAccuracyM)
            }()
            #if targetEnvironment(simulator)
                // Route replay on iOS simulator frequently reports unstable horizontal accuracy.
                // Clamp for prompt evaluation so advisories can be validated in simulator mode.
                effectiveGpsAccuracyM = min(effectiveGpsAccuracyM, 10)
            #endif
            if nowMs - self.lastPromptTickLogAtMs >= 5_000 {
                self.lastPromptTickLogAtMs = nowMs
                print(
                    "[Drivest iOS] prompt_tick " +
                        "speed_mps=\(String(format: "%.2f", effectiveSpeedMps)) " +
                        "gps_accuracy_m=\(String(format: "%.1f", effectiveGpsAccuracyM)) " +
                        "hazards_total=\(self.hazardFeatures.count) nearby=\(nearbyFeatures.count) " +
                        "upcoming_distance_m=\(Int(upcomingDistance.rounded())) " +
                        "upcoming_time_s=\(Int(upcomingTime.rounded())) " +
                        "off_route_state=\(self.parityStateStore.lastOffRouteState)"
                )
            }
            self.isManeuverSpeechPlaying = upcomingTime < 2

            if upcomingTime < 6 {
                self.hazardVoiceController.onManeuverInstructionArrived()
            }
            #if targetEnvironment(simulator)
                let speedGateEnabled = false
            #else
                let speedGateEnabled = true
            #endif

            if speedGateEnabled && effectiveSpeedMps < self.minimumPromptEvaluationSpeedMps {
                self.parityStateStore.lastPromptSuppressed = "speed_below_gate"
                if nowMs - self.lastSpeedGateLogAtMs >= 5_000 {
                    self.lastSpeedGateLogAtMs = nowMs
                    print(
                        "[Drivest iOS] prompt_speed_gate " +
                            "effective_speed_mps=\(String(format: "%.2f", effectiveSpeedMps)) " +
                            "snapped_speed_mps=\(String(format: "%.2f", max(location.speed, 0))) " +
                            "raw_speed_mps=\(String(format: "%.2f", max(rawLocation.speed, 0))) " +
                            "nearby=\(nearbyFeatures.count)"
                    )
                }
                return
            }

            let featuresForEvaluation: [HazardFeature]
            if self.settingsStore.hazardsEnabled {
                featuresForEvaluation = nearbyFeatures
            } else {
                featuresForEvaluation = nearbyFeatures.filter { $0.type == .noEntry }
                if featuresForEvaluation.isEmpty {
                    self.parityStateStore.lastPromptSuppressed = "hazards_disabled"
                    return
                }
            }

            guard !featuresForEvaluation.isEmpty else {
                self.parityStateStore.lastPromptSuppressed = PromptSuppressionReason.noFeatures.rawValue
                if nowMs - self.lastPromptSuppressionLogAtMs >= 5_000 {
                    self.lastPromptSuppressionLogAtMs = nowMs
                    print("[Drivest iOS] prompt_suppressed reason=no_features nearby=0")
                }
                return
            }
            let visualEnabled = self.settingsStore.hazardsEnabled ||
                featuresForEvaluation.contains(where: { $0.type == .noEntry })
            guard visualEnabled else {
                self.parityStateStore.lastPromptSuppressed = PromptSuppressionReason.visualDisabled.rawValue
                if nowMs - self.lastPromptSuppressionLogAtMs >= 5_000 {
                    self.lastPromptSuppressionLogAtMs = nowMs
                    print("[Drivest iOS] prompt_suppressed reason=visual_disabled nearby=\(nearbyFeatures.count)")
                }
                return
            }

            let evaluationResult = self.promptEngine.evaluate(
                nowMs: nowMs,
                locationLat: location.coordinate.latitude,
                locationLon: location.coordinate.longitude,
                gpsAccuracyM: effectiveGpsAccuracyM,
                speedMps: effectiveSpeedMps,
                upcomingManeuverDistanceM: upcomingDistance,
                upcomingManeuverTimeS: upcomingTime,
                features: featuresForEvaluation,
                visualEnabled: visualEnabled,
                sensitivity: self.settingsStore.promptSensitivity,
                routePolyline: self.activeRouteCoordinates
            )
            if let suppression = evaluationResult.suppressionReason {
                self.parityStateStore.lastPromptSuppressed = suppression.rawValue
                if nowMs - self.lastPromptSuppressionLogAtMs >= 5_000 {
                    self.lastPromptSuppressionLogAtMs = nowMs
                    print(
                        "[Drivest iOS] prompt_suppressed " +
                            "reason=\(suppression.rawValue) nearby=\(nearbyFeatures.count)"
                    )
                }
            }

            guard let prompt = evaluationResult.promptEvent else { return }

            self.showPromptBanner(prompt.message)
            self.parityStateStore.lastPromptFired = "\(prompt.type.rawValue) \(prompt.distanceM)m"
            print(
                "[Drivest iOS] prompt_fired " +
                    "type=\(prompt.type.rawValue) distance_m=\(prompt.distanceM) " +
                    "confidence=\(String(format: "%.2f", prompt.confidenceHint))"
            )
            guard self.shouldSpeakHazardPrompt(prompt) else {
                self.parityStateStore.lastPromptSuppressed = "voice_confidence_or_mode_suppressed"
                return
            }
            self.hazardVoiceController.enqueue(
                prompt,
                voiceMode: self.settingsStore.voiceMode,
                upcomingManeuverTimeS: upcomingTime,
                isManeuverSpeechPlaying: self.isManeuverSpeechPlaying
            )
        }
    }

    nonisolated func navigationViewControllerDidDismiss(
        _ navigationViewController: NavigationViewController,
        byCanceling canceled: Bool
    ) {
        Task { @MainActor [weak self] in
            guard let self else { return }

            navigationViewController.dismiss(animated: true)
            self.promptAutoHideTask?.cancel()
            self.promptBannerContainer.isHidden = true
            self.routeProgressContainer.isHidden = true
            self.speedometerCard.isHidden = true
            self.activeNavigationViewController = nil
            self.hazardVoiceController.stopSpeaking()
            self.parityStateStore.markObserverDetached()
            print("[Drivest iOS] session_stop canceled=\(canceled)")
            if canceled {
                self.summaryLabel.text = "Navigation stopped."
            } else {
                self.summaryLabel.text = "Navigation completed."
            }
        }
    }

    private func shouldSpeakHazardPrompt(_ prompt: PromptEvent) -> Bool {
        if prompt.type == .busLane || prompt.type == .giveWay {
            return false
        }
        if prompt.type == .noEntry {
            if prompt.distanceM > noEntryVoiceProximityMeters {
                return false
            }
        } else if prompt.distanceM > hazardVoiceProximityMeters {
            return false
        }
        if !voiceModePolicy.canSpeak(prompt.type, voiceMode: settingsStore.voiceMode) {
            return false
        }
        return prompt.confidenceHint >= hazardVoiceMinConfidence(for: prompt.type)
    }

    private func hazardVoiceMinConfidence(for type: PromptType) -> Double {
        switch type {
        case .noEntry:
            return 0.50
        case .busStop:
            return 0.65
        case .schoolZone:
            return 0.70
        default:
            return hazardVoiceMinConfidenceHint
        }
    }

    private func updateRouteProgressOverlay(progress: RouteProgress) {
        let completionPercent = max(0, min(100, Int((progress.fractionTraveled * 100).rounded())))
        routeProgressSummaryLabel.text =
            "\(formatDistance(progress.distanceRemaining)) · " +
            "\(formatDuration(progress.durationRemaining)) · " +
            "\(etaFormatter.string(from: Date().addingTimeInterval(progress.durationRemaining))) · " +
            "\(completionPercent)%"
        routeProgressBar.progress = Float(completionPercent) / 100
        routeProgressContainer.isHidden = false
    }

    private func updateSpeedometerOverlay(
        speedMps: Double,
        navigationViewController: NavigationViewController
    ) {
        let usesMetric = settingsStore.unitsMode == .metricKmh
        let currentSpeed = usesMetric ? speedMps * 3.6 : speedMps * 2.2369363
        let roundedSpeed = max(0, Int(currentSpeed.rounded()))
        speedValueLabel.text = "\(roundedSpeed)"
        speedUnitLabel.text = usesMetric ? "km/h" : "mph"

        var postedLimitValue: Int?
        if let speedLimitMeasurement = navigationViewController.navigationView.speedLimitView.speedLimit {
            let converted = usesMetric
                ? speedLimitMeasurement.converted(to: .kilometersPerHour).value
                : speedLimitMeasurement.converted(to: .milesPerHour).value
            let roundedLimit = max(0, Int(converted.rounded()))
            if roundedLimit > 0 {
                postedLimitValue = roundedLimit
            }
        }

        if let postedLimitValue {
            speedLimitBadgeLabel.text = "\(postedLimitValue)"
            speedLimitBadgeLabel.isHidden = false
        } else {
            speedLimitBadgeLabel.isHidden = true
            speedLimitBadgeLabel.text = nil
        }

        let isOverLimit = if let postedLimitValue {
            roundedSpeed > postedLimitValue
        } else {
            false
        }
        if isOverLimit {
            speedometerCard.backgroundColor = UIColor(red: 0.42, green: 0.08, blue: 0.09, alpha: 0.94)
            speedLimitBadgeLabel.backgroundColor = UIColor(red: 0.89, green: 0.27, blue: 0.23, alpha: 0.95)
        } else {
            speedometerCard.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.92)
            speedLimitBadgeLabel.backgroundColor = UIColor.white.withAlphaComponent(0.2)
        }
        speedometerCard.isHidden = false
    }

    private func resolveEffectiveSpeedMps(
        snappedLocation: CLLocation,
        rawLocation: CLLocation,
        progress: RouteProgress,
        nowMs: Int64
    ) -> Double {
        let snapped = max(snappedLocation.speed, 0)
        let raw = max(rawLocation.speed, 0)
        if snapped >= minimumReliableSpeedMps {
            updateSpeedDerivationState(distanceRemaining: progress.distanceRemaining, nowMs: nowMs)
            lastEffectiveSpeedMps = snapped
            return snapped
        }
        if raw >= minimumReliableSpeedMps {
            updateSpeedDerivationState(distanceRemaining: progress.distanceRemaining, nowMs: nowMs)
            lastEffectiveSpeedMps = raw
            return raw
        }

        let distanceRemaining = progress.distanceRemaining
        var derived = 0.0
        if let lastDistanceRemainingForSpeedM,
            let lastSpeedSampleAtMs,
            nowMs > lastSpeedSampleAtMs
        {
            let deltaDistance = max(0, lastDistanceRemainingForSpeedM - distanceRemaining)
            let deltaSeconds = Double(nowMs - lastSpeedSampleAtMs) / 1_000.0
            if deltaSeconds >= 0.25 {
                derived = min(40, deltaDistance / deltaSeconds)
            }
        }
        updateSpeedDerivationState(distanceRemaining: distanceRemaining, nowMs: nowMs)

        if derived > 0 {
            lastEffectiveSpeedMps = derived
            return derived
        }

        // Keep short continuity between updates instead of oscillating to zero
        // when simulator speed is not populated.
        let bridged = max(0, lastEffectiveSpeedMps * 0.92)
        lastEffectiveSpeedMps = bridged
        return bridged
    }

    private func updateSpeedDerivationState(
        distanceRemaining: CLLocationDistance,
        nowMs: Int64
    ) {
        lastDistanceRemainingForSpeedM = distanceRemaining
        lastSpeedSampleAtMs = nowMs
    }

    private func formatDistance(_ meters: CLLocationDistance) -> String {
        let clamped = max(0, meters)
        if settingsStore.unitsMode == .metricKmh {
            let kilometers = clamped / 1000
            return kilometers >= 10
                ? "\(Int(kilometers.rounded())) km"
                : String(format: "%.1f km", kilometers)
        }

        let miles = clamped / 1609.344
        return miles >= 10
            ? "\(Int(miles.rounded())) mi"
            : String(format: "%.1f mi", miles)
    }

    private func formatDuration(_ seconds: TimeInterval) -> String {
        let total = max(0, Int(seconds.rounded()))
        if total < 60 {
            return "\(total)s"
        }
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(minutes)m"
    }

    private func filterHazardsNearLocation(
        _ features: [HazardFeature],
        coordinate: CLLocationCoordinate2D,
        radiusMeters: Double
    ) -> [HazardFeature] {
        guard !features.isEmpty else { return [] }
        return features.filter { feature in
            haversineDistanceMeters(
                lat1: coordinate.latitude,
                lon1: coordinate.longitude,
                lat2: feature.lat,
                lon2: feature.lon
            ) <= radiusMeters
        }
    }

    private func filterHazardsToRouteCorridor(
        _ features: [HazardFeature],
        routeCoordinates: [CLLocationCoordinate2D],
        radiusMeters: Double
    ) -> [HazardFeature] {
        guard routeCoordinates.count >= 2 else { return features }
        return features.filter { feature in
            let point = CLLocationCoordinate2D(latitude: feature.lat, longitude: feature.lon)
            return minimumDistanceToRouteMeters(point: point, route: routeCoordinates) <= radiusMeters
        }
    }

    private func mergeHazards(primary: [HazardFeature], secondary: [HazardFeature]) -> [HazardFeature] {
        var mergedByKey: [String: HazardFeature] = [:]
        for hazard in primary + secondary {
            let key = [
                hazard.type.rawValue,
                String(format: "%.5f", hazard.lat),
                String(format: "%.5f", hazard.lon),
            ].joined(separator: ":")
            if let existing = mergedByKey[key] {
                let existingConfidence = existing.confidenceHint ?? 0
                let candidateConfidence = hazard.confidenceHint ?? 0
                if candidateConfidence > existingConfidence {
                    mergedByKey[key] = hazard
                }
            } else {
                mergedByKey[key] = hazard
            }
        }
        return Array(mergedByKey.values)
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

    private func maybeRefreshHazardsIfNeeded(nowMs: Int64, location: CLLocation) {
        guard activeRouteCoordinates.count >= 2 else { return }
        guard !isHazardFetchInProgress else { return }

        let isStale = nowMs - lastHazardFetchAtMs >= hazardFetchIntervalMs
        let movedFar: Bool
        if let anchor = lastHazardFetchAnchor {
            let anchorLocation = CLLocation(latitude: anchor.latitude, longitude: anchor.longitude)
            movedFar = location.distance(from: anchorLocation) >= hazardFetchMovementMeters
        } else {
            movedFar = true
        }
        guard isStale || movedFar else { return }

        lastHazardFetchAnchor = location.coordinate
        loadHazardFeatures(routeCoordinates: activeRouteCoordinates, force: false)
    }

    private func updateOffRouteDiagnostics(progress: RouteProgress, location: CLLocation) {
        let rawDistance = minimumDistanceToRouteMeters(
            point: location.coordinate,
            route: activeRouteCoordinates
        )
        let previousSmoothed = offRouteSmoothedDistanceM ?? rawDistance
        let smoothedDistance = (offRouteSmoothingAlpha * rawDistance) +
            ((1 - offRouteSmoothingAlpha) * previousSmoothed)
        offRouteSmoothedDistanceM = smoothedDistance

        let offRouteState: String
        if smoothedDistance >= 50 {
            offRouteState = "OFF_ROUTE"
        } else if smoothedDistance >= 35 {
            offRouteState = "UNCERTAIN"
        } else {
            offRouteState = "ON_ROUTE"
        }

        parityStateStore.lastOffRouteState = offRouteState
        parityStateStore.offRouteRawDistanceMeters = rawDistance
        parityStateStore.offRouteSmoothedDistanceMeters = smoothedDistance
    }

    private func resolveRouteCoordinates(
        route: NavigationRoute?,
        fallback: [CLLocationCoordinate2D]
    ) -> [CLLocationCoordinate2D] {
        guard let route else { return fallback }
        if let shapeCoordinates = route.route.shape?.coordinates, !shapeCoordinates.isEmpty {
            return shapeCoordinates.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            }
        }
        return fallback
    }

    private func minimumDistanceToRouteMeters(
        point: CLLocationCoordinate2D,
        route: [CLLocationCoordinate2D]
    ) -> Double {
        guard route.count >= 2 else { return 0 }
        var minimumDistance = Double.greatestFiniteMagnitude
        for index in 0..<(route.count - 1) {
            let candidate = pointToSegmentDistanceMeters(
                point: point,
                segmentStart: route[index],
                segmentEnd: route[index + 1]
            )
            minimumDistance = min(minimumDistance, candidate)
        }
        return minimumDistance.isFinite ? minimumDistance : 0
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
        let sx = 0.0
        let sy = 0.0
        let ex = (segmentEnd.longitude - segmentStart.longitude) * metersPerDegreeLon
        let ey = (segmentEnd.latitude - segmentStart.latitude) * metersPerDegreeLat

        let segDx = ex - sx
        let segDy = ey - sy
        let segLengthSquared = (segDx * segDx) + (segDy * segDy)
        if segLengthSquared <= 0.000001 {
            return hypot(px - sx, py - sy)
        }

        let projection = ((px - sx) * segDx + (py - sy) * segDy) / segLengthSquared
        let clamped = max(0.0, min(1.0, projection))
        let closestX = sx + (clamped * segDx)
        let closestY = sy + (clamped * segDy)
        return hypot(px - closestX, py - closestY)
    }

    private func makeNavigationProvider(
        initialCoordinate: CLLocationCoordinate2D?
    ) -> MapboxNavigationProvider {
        #if targetEnvironment(simulator)
            let source = LocationSource.simulation(
                initialLocation: initialCoordinate.map {
                    CLLocation(latitude: $0.latitude, longitude: $0.longitude)
                }
            )
            let sourceLabel = "simulation"
        #else
            let source = LocationSource.live
            let sourceLabel = "live"
        #endif

        if let coreConfig = makeCoreConfig(
            locationSource: source,
            sourceLabel: sourceLabel
        ) {
            return MapboxNavigationProvider(coreConfig: coreConfig)
        }

        print(
            "[Drivest iOS] mapbox_navigation_provider_token_missing_or_invalid " +
                "fallback=default_provider location_source=\(sourceLabel)"
        )
        var fallbackCoreConfig = CoreConfig(locationSource: source)
        #if targetEnvironment(simulator)
            fallbackCoreConfig.routingConfig.rerouteConfig = RerouteConfig(detectsReroute: false)
            fallbackCoreConfig.routingConfig.routeRefreshPeriod = nil
            fallbackCoreConfig.routingConfig.prefersOnlineRoute = false
        #endif
        return MapboxNavigationProvider(coreConfig: fallbackCoreConfig)
    }

    private func makeCoreConfig(
        locationSource: LocationSource,
        sourceLabel: String
    ) -> CoreConfig? {
        guard let rawToken = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String else {
            return nil
        }
        let token = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard token.hasPrefix("pk.") else { return nil }

        print(
            "[Drivest iOS] mapbox_navigation_provider_token_loaded " +
                "length=\(token.count) location_source=\(sourceLabel)"
        )
        var coreConfig = CoreConfig(
            credentials: NavigationCoreApiConfiguration(accessToken: token),
            locationSource: locationSource
        )
        #if targetEnvironment(simulator)
            // Keep simulator guidance deterministic for validation:
            // avoid online reroute/refresh loops caused by token/env instability.
            coreConfig.routingConfig.rerouteConfig = RerouteConfig(detectsReroute: false)
            coreConfig.routingConfig.routeRefreshPeriod = nil
            coreConfig.routingConfig.prefersOnlineRoute = false
        #endif
        return coreConfig
    }

    private func configureSimulatorRouteReplayIfNeeded(
        initialCoordinate: CLLocationCoordinate2D?
    ) {
        #if targetEnvironment(simulator)
            guard let initialCoordinate else { return }
            let source = LocationSource.simulation(
                initialLocation: CLLocation(
                    latitude: initialCoordinate.latitude,
                    longitude: initialCoordinate.longitude
                )
            )
            if let coreConfig = makeCoreConfig(locationSource: source, sourceLabel: "simulation") {
                mapboxNavigationProvider.apply(coreConfig: coreConfig)
                print(
                    "[Drivest iOS] simulator_route_replay_enabled " +
                        "lat=\(initialCoordinate.latitude) lon=\(initialCoordinate.longitude)"
                )
            } else {
                var fallbackCoreConfig = CoreConfig(locationSource: source)
                fallbackCoreConfig.routingConfig.rerouteConfig = RerouteConfig(detectsReroute: false)
                fallbackCoreConfig.routingConfig.routeRefreshPeriod = nil
                fallbackCoreConfig.routingConfig.prefersOnlineRoute = false
                mapboxNavigationProvider.apply(coreConfig: fallbackCoreConfig)
                print(
                    "[Drivest iOS] simulator_route_replay_enabled_token_fallback " +
                        "lat=\(initialCoordinate.latitude) lon=\(initialCoordinate.longitude)"
                )
            }
        #else
            _ = initialCoordinate
        #endif
    }

    // Reduce local practice route geometry to a safe waypoint count for Directions preview requests.
    private func simplifiedPracticePreviewCoordinates(
        from coordinates: [CLLocationCoordinate2D],
        maxWaypoints: Int
    ) -> [CLLocationCoordinate2D] {
        let deduped = dedupeSequentialCoordinates(coordinates)
        guard deduped.count > maxWaypoints, maxWaypoints >= 2 else { return deduped }

        if maxWaypoints == 2, let first = deduped.first, let last = deduped.last {
            return [first, last]
        }

        let lastIndex = deduped.count - 1
        let targetCount = min(maxWaypoints, deduped.count)
        var sampled: [CLLocationCoordinate2D] = []
        sampled.reserveCapacity(targetCount)

        for sampleIndex in 0..<targetCount {
            let ratio = Double(sampleIndex) / Double(max(targetCount - 1, 1))
            let index = Int(round(ratio * Double(lastIndex)))
            sampled.append(deduped[index])
        }
        return dedupeSequentialCoordinates(sampled)
    }

    private func dedupeSequentialCoordinates(
        _ coordinates: [CLLocationCoordinate2D]
    ) -> [CLLocationCoordinate2D] {
        guard !coordinates.isEmpty else { return [] }
        var result: [CLLocationCoordinate2D] = []
        result.reserveCapacity(coordinates.count)
        var previous: CLLocationCoordinate2D?
        for coordinate in coordinates {
            if let previous,
                abs(previous.latitude - coordinate.latitude) < 0.000_001 &&
                    abs(previous.longitude - coordinate.longitude) < 0.000_001
            {
                continue
            }
            result.append(coordinate)
            previous = coordinate
        }
        return result
    }
}

extension NavigationSessionViewController: MKMapViewDelegate {
    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
        if let polyline = overlay as? MKPolyline {
            let renderer = MKPolylineRenderer(polyline: polyline)
            renderer.strokeColor = DrivestPalette.accentBlue
            renderer.lineWidth = 4
            return renderer
        }
        return MKOverlayRenderer(overlay: overlay)
    }
}

extension NavigationSessionViewController {
    nonisolated func navigationViewController(
        _ navigationViewController: NavigationViewController,
        didRerouteAlong route: Route
    ) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            if let shape = route.shape?.coordinates, !shape.isEmpty {
                self.activeRouteCoordinates = shape.map {
                    CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
                }
            }
            self.lastHazardFetchAnchor = nil
            self.loadHazardFeatures(routeCoordinates: self.activeRouteCoordinates, force: true)
            print(
                "[Drivest iOS] hazards_refresh reason=reroute route_points=\(self.activeRouteCoordinates.count)"
            )
        }
    }
}
