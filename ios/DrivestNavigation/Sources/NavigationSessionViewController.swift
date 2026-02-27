import CoreLocation
import MapKit
@preconcurrency import MapboxDirections
@preconcurrency import MapboxNavigationCore
@preconcurrency import MapboxNavigationUIKit
@preconcurrency import MapboxMaps
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
    private let promptBannerStack = UIStackView()
    private let promptTypeIconView = UIImageView()
    private var promptTypeIconWidthConstraint: NSLayoutConstraint?
    private let promptSignImageView = UIImageView()
    private var promptSignImageWidthConstraint: NSLayoutConstraint?
    private let promptBannerLabel = UILabel()
    private let routeProgressContainer = UIView()
    private let routeProgressIconView = UIImageView()
    private let routeProgressSummaryLabel = UILabel()
    private let routeProgressBar = UIProgressView(progressViewStyle: .default)
    private let speedometerCard = UIView()
    private let speedometerIconView = UIImageView()
    private let speedLimitBubble = UIView()
    private let speedLimitBadgeLabel = UILabel()
    private let speedValueLabel = UILabel()
    private let speedUnitLabel = UILabel()
    private lazy var mapboxUIKitIconsBundle: Bundle? = resolveMapboxUIKitIconsBundle()
    private lazy var resumeFloatingButton: FloatingButton = {
        let resumeImage = mapboxIcon(named: "recenter") ?? UIImage(systemName: "location.fill")
        let button: FloatingButton = .rounded(
            image: resumeImage,
            size: CGSize(width: 50, height: 50)
        )
        button.tintColor = UIColor(red: 0.22, green: 0.50, blue: 0.84, alpha: 1.0)
        button.addTarget(self, action: #selector(recenterFromFloatingButton), for: .touchUpInside)
        button.accessibilityLabel = "Resume guidance"
        return button
    }()

    private weak var activeNavigationViewController: NavigationViewController?
    private let practiceOriginLocationManager = CLLocationManager()
    private var promptAutoHideTask: Task<Void, Never>?
    private var hazardLoadTask: Task<Void, Never>?
    private var isHazardFetchInProgress = false
    private var didHandleSessionStop = false
    private var didAutoCompletePractice = false
    private var practiceOriginCoordinate: CLLocationCoordinate2D?
    private var isResolvingPracticeOrigin = false
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
    private var hazardPointAnnotationManager: PointAnnotationManager?
    private var lastAppliedCameraPhase: GuidanceCameraPhase?
    private var lastAppliedCameraMode: DriverMode?

    private let minimumPromptEvaluationSpeedMps = 0.556
    private let minimumReliableSpeedMps = 0.278
    private let offRouteSmoothingAlpha = 0.35
    private let hazardVoiceMinConfidenceHint = 0.65
    private let hazardVoiceProximityMeters = 120
    private let noEntryVoiceProximityMeters = 100
    private let maxPracticeRouteFallbackWaypoints = 20
    private let hazardFetchIntervalMs: Int64 = 10 * 60 * 1000
    private let hazardFetchMovementMeters = 300.0
    private let promptNearbyQueryRadiusMeters = 420.0
    private let practiceRouteMinValidDistanceMeters = 900.0
    private let practiceRouteDistanceValidityRatio = 0.45
    private let practiceRouteAutoCompleteMinFraction = 0.999
    private let practiceRouteAutoCompleteRemainingMeters = 12.0

    private enum GuidanceCameraPhase {
        case cruising
        case maneuver
    }

    private struct DriverGuidanceProfile {
        let cruiseZoom: Double
        let cruisePitch: Double
        let maneuverZoom: Double
        let maneuverPitch: Double
        let zoomRange: ClosedRange<Double>
        let defaultPitch: Double
        let pitchNearManeuverTriggerMeters: CLLocationDistance
        let intersectionDensityMultiplier: Double
        let frameAfterManeuverMeters: CLLocationDistance
        let compoundManeuverCoalesceMeters: CLLocationDistance
        let overviewMaxZoom: Double
        let hazardLeadDistanceMultiplier: Double
        let hazardConfidenceDelta: Double
        let nearbyHazardQueryRadiusMeters: Double
        let maneuverCameraTimeThresholdS: TimeInterval
        let maneuverCameraDistanceThresholdM: CLLocationDistance
    }

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
        let initialCoordinates = buildCoordinateList()
        renderPreviewMap(with: initialCoordinates)
        loadHazardFeatures(routeCoordinates: initialCoordinates, force: true)
        resolvePracticeOriginAndRequestPreview()
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
        let fallbackCoordinates = mode == .practice
            ? simplifiedPracticePreviewCoordinates(
                from: coordinates,
                maxWaypoints: maxPracticeRouteFallbackWaypoints
            )
            : coordinates
        print(
            "[Drivest iOS] preview_route_request " +
                "mode=\(mode.rawValue) " +
                "trace_points=\(coordinates.count) " +
                "fallback_waypoints=\(fallbackCoordinates.count)"
        )

        Task { [weak self] in
            guard let self else { return }
            do {
                let navigationRoutes = try await self.calculatePreviewRoutes(primaryCoordinates: coordinates)
                await MainActor.run {
                    self.previewRoutes = navigationRoutes
                    self.activeRouteCoordinates = self.resolveRouteCoordinates(
                        route: navigationRoutes.mainRoute,
                        fallback: coordinates
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
        let routeRequestCoordinates: [CLLocationCoordinate2D]
        if mode == .practice {
            routeRequestCoordinates = simplifiedPracticePreviewCoordinates(
                from: primaryCoordinates,
                maxWaypoints: maxPracticeRouteFallbackWaypoints
            )
        } else {
            routeRequestCoordinates = primaryCoordinates
        }

        if mode == .practice {
            print(
                "[Drivest iOS] preview_practice_route_request " +
                    "trace_points=\(primaryCoordinates.count) route_waypoints=\(routeRequestCoordinates.count)"
            )
        }

        let primaryOptions = navigationRouteOptions(for: routeRequestCoordinates)
        let primaryRequest = mapboxNavigation.routingProvider().calculateRoutes(options: primaryOptions)
        switch await primaryRequest.result {
        case .success(let routes):
            let legCount = routes.mainRoute.route.legs.count
            print("[Drivest iOS] preview_route_received legs=\(legCount)")
            if mode != .practice || isPracticePreviewDistanceValid(routes) {
                return routes
            }
            print("[Drivest iOS] preview_route_retrying_od_invalid_distance legs=\(legCount)")
            guard
                let origin = routeRequestCoordinates.first,
                let destination = routeRequestCoordinates.last
            else {
                return routes
            }

            let fallbackOptions = navigationRouteOptions(for: [origin, destination])
            let fallbackRequest = mapboxNavigation.routingProvider().calculateRoutes(options: fallbackOptions)
            switch await fallbackRequest.result {
            case .success(let fallbackRoutes):
                if isPracticePreviewDistanceValid(fallbackRoutes) {
                    print(
                        "[Drivest iOS] preview_fallback_od_used " +
                            "legs=\(fallbackRoutes.mainRoute.route.legs.count)"
                    )
                    return fallbackRoutes
                }
                print(
                    "[Drivest iOS] preview_fallback_od_rejected_invalid_distance " +
                        "legs=\(fallbackRoutes.mainRoute.route.legs.count)"
                )
                return routes
            case .failure(let fallbackError):
                print("[Drivest iOS] preview_fallback_od_failed error=\(fallbackError.localizedDescription)")
                return routes
            }

        case .failure(let primaryError):
            // Practice routes can include intermediary shaping points. If provider rejects that shape,
            // retry with origin/destination only so guidance can still start.
            guard
                mode == .practice,
                routeRequestCoordinates.count > 2,
                let origin = routeRequestCoordinates.first,
                let destination = routeRequestCoordinates.last
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
        options.distanceMeasurementSystem = settingsStore.unitsMode == .metricKmh ? .metric : .imperial
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
            let deduped = dedupeSequentialCoordinates(fullGeometry)
            guard
                let routeStart = deduped.first,
                let origin = practiceOriginCoordinate
            else {
                return deduped
            }
            // If user is already at route start, avoid injecting a duplicate approach leg.
            if coordinatesDistanceMeters(origin, routeStart) <= 35 {
                return deduped
            }
            return dedupeSequentialCoordinates([origin] + deduped)
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
        didHandleSessionStop = false
        didAutoCompletePractice = false
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
        navigationVC.showsSpeedLimits = false
        configureRightSideControls(for: navigationVC)
        attachPromptBanner(on: navigationVC.view, navigationView: navigationVC.navigationView)
        activeNavigationViewController = navigationVC
        syncNavigationVoiceMode()
        applyVoiceModeStatusText()
        applyGuidanceProfile(to: navigationVC, progress: nil, force: true)
        refreshHazardMapAnnotations()
        present(navigationVC, animated: true)
    }

    @objc
    private func recenterFromFloatingButton() {
        guard let navigationVC = activeNavigationViewController else { return }
        if triggerBuiltInResumeAction(in: navigationVC.navigationView) {
            applyGuidanceProfile(to: navigationVC, progress: nil, force: true)
            return
        }
        navigationVC.navigationMapView?.navigationCamera.update(cameraState: .following)
        applyGuidanceProfile(to: navigationVC, progress: nil, force: true)
    }

    private func configureRightSideControls(for navigationVC: NavigationViewController) {
        var controls = navigationVC.floatingButtons ?? []
        if !controls.contains(where: { $0 === resumeFloatingButton }) {
            controls.insert(resumeFloatingButton, at: 0)
            navigationVC.floatingButtons = controls
        }
    }

    private func triggerBuiltInResumeAction(in view: UIView) -> Bool {
        let className = NSStringFromClass(type(of: view))
        if className.contains("ResumeButton"), let control = view as? UIControl {
            control.sendActions(for: .touchUpInside)
            return true
        }
        for subview in view.subviews {
            if triggerBuiltInResumeAction(in: subview) {
                return true
            }
        }
        return false
    }

    @objc
    private func cycleVoiceMode() {
        let nextMode = settingsStore.cycleVoiceMode()
        print("[Drivest iOS] voice_mode_changed mode=\(nextMode.rawValue)")
        if nextMode == .mute {
            hazardVoiceController.stopSpeaking()
        }
        syncNavigationVoiceMode()
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

    private func syncNavigationVoiceMode() {
        // Keep Mapbox maneuver guidance voice in sync with Drivest voice mode.
        // Maneuver prompts should be audible unless explicit mute is selected.
        mapboxNavigationProvider.routeVoiceController.speechSynthesizer.muted = (settingsStore.voiceMode == .mute)
    }

    private func guidanceProfile(for mode: DriverMode) -> DriverGuidanceProfile {
        switch mode {
        case .learner:
            return DriverGuidanceProfile(
                cruiseZoom: 15.8,
                cruisePitch: 33.0,
                maneuverZoom: 17.2,
                maneuverPitch: 24.0,
                zoomRange: 13.8...18.2,
                defaultPitch: 34.0,
                pitchNearManeuverTriggerMeters: 280.0,
                intersectionDensityMultiplier: 5.2,
                frameAfterManeuverMeters: 70.0,
                compoundManeuverCoalesceMeters: 120.0,
                overviewMaxZoom: 17.4,
                hazardLeadDistanceMultiplier: 1.25,
                hazardConfidenceDelta: -0.08,
                nearbyHazardQueryRadiusMeters: 520.0,
                maneuverCameraTimeThresholdS: 22.0,
                maneuverCameraDistanceThresholdM: 230.0
            )
        case .newDriver:
            return DriverGuidanceProfile(
                cruiseZoom: 15.2,
                cruisePitch: 38.0,
                maneuverZoom: 16.4,
                maneuverPitch: 30.0,
                zoomRange: 12.8...17.4,
                defaultPitch: 39.0,
                pitchNearManeuverTriggerMeters: 230.0,
                intersectionDensityMultiplier: 6.0,
                frameAfterManeuverMeters: 85.0,
                compoundManeuverCoalesceMeters: 140.0,
                overviewMaxZoom: 16.9,
                hazardLeadDistanceMultiplier: 1.12,
                hazardConfidenceDelta: -0.04,
                nearbyHazardQueryRadiusMeters: 460.0,
                maneuverCameraTimeThresholdS: 18.0,
                maneuverCameraDistanceThresholdM: 190.0
            )
        case .standard:
            return DriverGuidanceProfile(
                cruiseZoom: 14.5,
                cruisePitch: 44.0,
                maneuverZoom: 15.3,
                maneuverPitch: 36.0,
                zoomRange: 10.5...16.35,
                defaultPitch: 45.0,
                pitchNearManeuverTriggerMeters: 170.0,
                intersectionDensityMultiplier: 7.0,
                frameAfterManeuverMeters: 100.0,
                compoundManeuverCoalesceMeters: 150.0,
                overviewMaxZoom: 16.35,
                hazardLeadDistanceMultiplier: 1.0,
                hazardConfidenceDelta: 0.0,
                nearbyHazardQueryRadiusMeters: promptNearbyQueryRadiusMeters,
                maneuverCameraTimeThresholdS: 14.0,
                maneuverCameraDistanceThresholdM: 150.0
            )
        }
    }

    private func currentGuidanceProfile() -> DriverGuidanceProfile {
        guidanceProfile(for: settingsStore.driverMode)
    }

    private func effectivePromptSensitivity() -> PromptSensitivity {
        switch settingsStore.driverMode {
        case .learner:
            return .extraHelp
        case .newDriver:
            return settingsStore.promptSensitivity == .minimal ? .standard : settingsStore.promptSensitivity
        case .standard:
            return settingsStore.promptSensitivity
        }
    }

    private func resolveGuidanceCameraPhase(
        progress: RouteProgress?,
        profile: DriverGuidanceProfile
    ) -> GuidanceCameraPhase {
        guard let progress else { return .cruising }
        let stepDistance = progress.currentLegProgress.currentStepProgress.distanceRemaining
        let stepTime = progress.currentLegProgress.currentStepProgress.durationRemaining
        let isApproachingManeuver =
            stepDistance <= profile.maneuverCameraDistanceThresholdM ||
            stepTime <= profile.maneuverCameraTimeThresholdS
        return isApproachingManeuver ? .maneuver : .cruising
    }

    private func applyGuidanceProfile(
        to navigationVC: NavigationViewController,
        progress: RouteProgress?,
        force: Bool = false
    ) {
        guard
            let viewportDataSource = navigationVC.navigationMapView?.navigationCamera.viewportDataSource
                as? MobileViewportDataSource
        else { return }

        let mode = settingsStore.driverMode
        let profile = guidanceProfile(for: mode)
        let phase = resolveGuidanceCameraPhase(progress: progress, profile: profile)

        if !force, lastAppliedCameraMode == mode, lastAppliedCameraPhase == phase {
            return
        }

        var viewportOptions = viewportDataSource.options
        viewportOptions.followingCameraOptions.defaultPitch = profile.defaultPitch
        viewportOptions.followingCameraOptions.zoomRange = profile.zoomRange
        viewportOptions.followingCameraOptions.pitchNearManeuver.enabled = true
        viewportOptions.followingCameraOptions.pitchNearManeuver.triggerDistanceToManeuver =
            profile.pitchNearManeuverTriggerMeters
        viewportOptions.followingCameraOptions.intersectionDensity.enabled = true
        viewportOptions.followingCameraOptions.intersectionDensity.averageDistanceMultiplier =
            profile.intersectionDensityMultiplier
        viewportOptions.followingCameraOptions.geometryFramingAfterManeuver.enabled = true
        viewportOptions.followingCameraOptions.geometryFramingAfterManeuver.distanceToFrameAfterManeuver =
            profile.frameAfterManeuverMeters
        viewportOptions.followingCameraOptions.geometryFramingAfterManeuver
            .distanceToCoalesceCompoundManeuvers = profile.compoundManeuverCoalesceMeters
        viewportOptions.overviewCameraOptions.maximumZoomLevel = profile.overviewMaxZoom
        viewportDataSource.options = viewportOptions

        var cameraOptions = viewportDataSource.currentNavigationCameraOptions
        switch phase {
        case .cruising:
            cameraOptions.followingCamera.zoom = profile.cruiseZoom
            cameraOptions.followingCamera.pitch = profile.cruisePitch
        case .maneuver:
            cameraOptions.followingCamera.zoom = profile.maneuverZoom
            cameraOptions.followingCamera.pitch = profile.maneuverPitch
        }
        viewportDataSource.currentNavigationCameraOptions = cameraOptions
        navigationVC.navigationMapView?.navigationCamera.update(cameraState: .following)

        lastAppliedCameraMode = mode
        lastAppliedCameraPhase = phase
        let zoomText = String(format: "%.2f", cameraOptions.followingCamera.zoom ?? 0)
        let pitchText = String(format: "%.1f", cameraOptions.followingCamera.pitch ?? 0)
        print(
            "[Drivest iOS] camera_profile_applied mode=\(mode.rawValue) phase=\(phase) " +
                "zoom=\(zoomText) " +
                "pitch=\(pitchText)"
        )
    }

    private func loadHazardFeatures(
        routeCoordinates: [CLLocationCoordinate2D],
        force: Bool = false
    ) {
        guard !isHazardFetchInProgress || force else { return }
        let centreId: String? =
            mode == .navigation ? nil : (centre?.id ?? settingsStore.lastCentreId)
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
                "[Drivest iOS] loaded_hazards centre_id=\(centreId ?? "none") mode=\(self.settingsStore.dataSourceMode.rawValue) " +
                    "count=\(mergedHazards.count) by_type={\(byType)}"
            )
            self.refreshHazardMapAnnotations()
        }
    }

    private func attachPromptBanner(on containerView: UIView, navigationView: NavigationView? = nil) {
        promptBannerContainer.removeFromSuperview()
        routeProgressContainer.removeFromSuperview()
        speedometerCard.removeFromSuperview()

        promptBannerContainer.translatesAutoresizingMaskIntoConstraints = false
        promptBannerContainer.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.95)
        promptBannerContainer.layer.cornerRadius = 10
        promptBannerContainer.layer.zPosition = 999
        promptBannerContainer.isHidden = true
        promptBannerContainer.isUserInteractionEnabled = false

        promptBannerLabel.translatesAutoresizingMaskIntoConstraints = false
        promptBannerLabel.textColor = .white
        promptBannerLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        promptBannerLabel.numberOfLines = 3

        promptTypeIconView.translatesAutoresizingMaskIntoConstraints = false
        promptTypeIconView.contentMode = .scaleAspectFit
        promptTypeIconView.tintColor = .white
        promptTypeIconView.isHidden = true

        promptSignImageView.translatesAutoresizingMaskIntoConstraints = false
        promptSignImageView.contentMode = .scaleAspectFit
        promptSignImageView.layer.cornerRadius = 6
        promptSignImageView.layer.masksToBounds = true
        promptSignImageView.backgroundColor = UIColor.white.withAlphaComponent(0.96)
        promptSignImageView.isHidden = true

        promptBannerStack.translatesAutoresizingMaskIntoConstraints = false
        promptBannerStack.axis = .horizontal
        promptBannerStack.alignment = .center
        promptBannerStack.spacing = 10
        promptBannerStack.distribution = .fill
        for arranged in promptBannerStack.arrangedSubviews {
            promptBannerStack.removeArrangedSubview(arranged)
            arranged.removeFromSuperview()
        }
        promptBannerStack.addArrangedSubview(promptTypeIconView)
        promptBannerStack.addArrangedSubview(promptSignImageView)
        promptBannerStack.addArrangedSubview(promptBannerLabel)

        promptBannerContainer.addSubview(promptBannerStack)
        containerView.addSubview(promptBannerContainer)

        NSLayoutConstraint.activate([
            promptBannerContainer.leadingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            promptBannerContainer.trailingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.trailingAnchor, constant: -12),
            promptBannerContainer.topAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.topAnchor, constant: 92),

            promptBannerStack.leadingAnchor.constraint(equalTo: promptBannerContainer.leadingAnchor, constant: 12),
            promptBannerStack.trailingAnchor.constraint(equalTo: promptBannerContainer.trailingAnchor, constant: -12),
            promptBannerStack.topAnchor.constraint(equalTo: promptBannerContainer.topAnchor, constant: 8),
            promptBannerStack.bottomAnchor.constraint(equalTo: promptBannerContainer.bottomAnchor, constant: -8),
            promptTypeIconView.heightAnchor.constraint(equalToConstant: 20),
            promptSignImageView.heightAnchor.constraint(equalToConstant: 30)
        ])
        promptTypeIconWidthConstraint?.isActive = false
        promptTypeIconWidthConstraint = promptTypeIconView.widthAnchor.constraint(equalToConstant: 0)
        promptTypeIconWidthConstraint?.isActive = true
        promptSignImageWidthConstraint?.isActive = false
        promptSignImageWidthConstraint = promptSignImageView.widthAnchor.constraint(equalToConstant: 0)
        promptSignImageWidthConstraint?.isActive = true

        setupRouteProgressOverlay(on: containerView, navigationView: navigationView)
        setupSpeedometerOverlay(on: containerView)
    }

    private func setupRouteProgressOverlay(on containerView: UIView, navigationView: NavigationView? = nil) {
        routeProgressContainer.translatesAutoresizingMaskIntoConstraints = false
        routeProgressContainer.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.90)
        routeProgressContainer.layer.cornerRadius = 12
        routeProgressContainer.layer.masksToBounds = true
        routeProgressContainer.layer.zPosition = 999
        routeProgressContainer.isHidden = true
        routeProgressContainer.isUserInteractionEnabled = false

        routeProgressSummaryLabel.translatesAutoresizingMaskIntoConstraints = false
        routeProgressSummaryLabel.textColor = .white
        routeProgressSummaryLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        routeProgressSummaryLabel.numberOfLines = 1

        routeProgressIconView.translatesAutoresizingMaskIntoConstraints = false
        routeProgressIconView.contentMode = .scaleAspectFit
        routeProgressIconView.tintColor = UIColor.white.withAlphaComponent(0.92)
        routeProgressIconView.image = mapboxIcon(named: "time")?.withRenderingMode(.alwaysTemplate)

        routeProgressBar.translatesAutoresizingMaskIntoConstraints = false
        routeProgressBar.trackTintColor = UIColor.white.withAlphaComponent(0.25)
        routeProgressBar.progressTintColor = DrivestPalette.accentPrimary
        routeProgressBar.progress = 0

        routeProgressContainer.addSubview(routeProgressIconView)
        routeProgressContainer.addSubview(routeProgressSummaryLabel)
        routeProgressContainer.addSubview(routeProgressBar)
        containerView.addSubview(routeProgressContainer)

        let verticalAnchorConstraint: NSLayoutConstraint
        if let navigationView {
            // Keep progress panel flush with the top edge of Mapbox bottom banner.
            verticalAnchorConstraint = routeProgressContainer.bottomAnchor.constraint(
                equalTo: navigationView.bottomBannerContainerView.topAnchor,
                constant: 0
            )
        } else {
            // Fallback for edge cases where navigation view is not available.
            verticalAnchorConstraint = routeProgressContainer.bottomAnchor.constraint(
                equalTo: containerView.safeAreaLayoutGuide.bottomAnchor,
                constant: -96
            )
        }

        NSLayoutConstraint.activate([
            routeProgressContainer.leadingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            routeProgressContainer.trailingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.trailingAnchor, constant: -12),
            verticalAnchorConstraint,

            routeProgressIconView.leadingAnchor.constraint(equalTo: routeProgressContainer.leadingAnchor, constant: 12),
            routeProgressIconView.centerYAnchor.constraint(equalTo: routeProgressSummaryLabel.centerYAnchor),
            routeProgressIconView.widthAnchor.constraint(equalToConstant: 14),
            routeProgressIconView.heightAnchor.constraint(equalToConstant: 14),

            routeProgressSummaryLabel.leadingAnchor.constraint(equalTo: routeProgressIconView.trailingAnchor, constant: 8),
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
        speedometerCard.layer.cornerRadius = 44
        speedometerCard.layer.masksToBounds = true
        speedometerCard.layer.zPosition = 999
        speedometerCard.isHidden = true
        speedometerCard.isUserInteractionEnabled = false
        speedometerCard.layer.borderWidth = 1
        speedometerCard.layer.borderColor = UIColor.white.withAlphaComponent(0.12).cgColor

        speedLimitBubble.translatesAutoresizingMaskIntoConstraints = false
        speedLimitBubble.backgroundColor = UIColor.white.withAlphaComponent(0.2)
        speedLimitBubble.layer.cornerRadius = 17
        speedLimitBubble.layer.masksToBounds = true
        speedLimitBubble.layer.zPosition = 1000
        speedLimitBubble.isHidden = true
        speedLimitBubble.isUserInteractionEnabled = false

        speedLimitBadgeLabel.translatesAutoresizingMaskIntoConstraints = false
        speedLimitBadgeLabel.textAlignment = .center
        speedLimitBadgeLabel.font = .systemFont(ofSize: 15, weight: .bold)
        speedLimitBadgeLabel.textColor = .white
        speedLimitBadgeLabel.isHidden = false

        speedValueLabel.translatesAutoresizingMaskIntoConstraints = false
        speedValueLabel.textAlignment = .center
        speedValueLabel.font = .systemFont(ofSize: 34, weight: .bold)
        speedValueLabel.textColor = .white
        speedValueLabel.text = "0"

        speedometerIconView.translatesAutoresizingMaskIntoConstraints = false
        speedometerIconView.contentMode = .scaleAspectFit
        speedometerIconView.tintColor = UIColor.white.withAlphaComponent(0.78)
        speedometerIconView.image = mapboxIcon(named: "feedback_speed_limit")?.withRenderingMode(.alwaysTemplate)

        speedUnitLabel.translatesAutoresizingMaskIntoConstraints = false
        speedUnitLabel.textAlignment = .center
        speedUnitLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        speedUnitLabel.textColor = UIColor.white.withAlphaComponent(0.88)
        speedUnitLabel.text = settingsStore.unitsMode == .metricKmh ? "km/h" : "mph"

        speedLimitBubble.addSubview(speedLimitBadgeLabel)
        speedometerCard.addSubview(speedometerIconView)
        speedometerCard.addSubview(speedValueLabel)
        speedometerCard.addSubview(speedUnitLabel)
        containerView.addSubview(speedometerCard)
        containerView.addSubview(speedLimitBubble)

        NSLayoutConstraint.activate([
            speedometerCard.leadingAnchor.constraint(equalTo: containerView.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            speedometerCard.bottomAnchor.constraint(equalTo: routeProgressContainer.topAnchor, constant: -12),
            speedometerCard.widthAnchor.constraint(equalToConstant: 88),
            speedometerCard.heightAnchor.constraint(equalToConstant: 88),

            speedLimitBubble.widthAnchor.constraint(equalToConstant: 34),
            speedLimitBubble.heightAnchor.constraint(equalToConstant: 34),
            speedLimitBubble.centerXAnchor.constraint(equalTo: speedometerCard.trailingAnchor, constant: 4),
            speedLimitBubble.centerYAnchor.constraint(equalTo: speedometerCard.topAnchor, constant: 8),

            speedLimitBadgeLabel.centerXAnchor.constraint(equalTo: speedLimitBubble.centerXAnchor),
            speedLimitBadgeLabel.centerYAnchor.constraint(equalTo: speedLimitBubble.centerYAnchor),

            speedometerIconView.centerXAnchor.constraint(equalTo: speedometerCard.centerXAnchor),
            speedometerIconView.topAnchor.constraint(equalTo: speedometerCard.topAnchor, constant: 11),
            speedometerIconView.widthAnchor.constraint(equalToConstant: 14),
            speedometerIconView.heightAnchor.constraint(equalToConstant: 14),

            speedValueLabel.centerXAnchor.constraint(equalTo: speedometerCard.centerXAnchor),
            speedValueLabel.centerYAnchor.constraint(equalTo: speedometerCard.centerYAnchor, constant: -3),

            speedUnitLabel.centerXAnchor.constraint(equalTo: speedometerCard.centerXAnchor),
            speedUnitLabel.topAnchor.constraint(equalTo: speedValueLabel.bottomAnchor, constant: 2),
            speedUnitLabel.bottomAnchor.constraint(lessThanOrEqualTo: speedometerCard.bottomAnchor, constant: -8)
        ])
    }

    private func promptBannerBackgroundColor(for type: PromptType?) -> UIColor {
        if type == .busLane {
            return UIColor(red: 0.66, green: 0.09, blue: 0.12, alpha: 0.96)
        }
        return UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.95)
    }

    private func advisorySignImage(for prompt: PromptEvent) -> UIImage? {
        if let image = advisorySignImage(
            type: prompt.type,
            signImagePath: prompt.signImagePath,
            signCode: prompt.signCode
        ) {
            return image.withRenderingMode(.alwaysOriginal)
        }
        return nil
    }

    private func advisoryFallbackSignImagePath(for type: PromptType) -> String? {
        switch type {
        case .trafficSignal:
            return "warning-signs-jpg/543.jpg"
        case .zebraCrossing:
            return "warning-signs-jpg/544.jpg"
        case .roundabout:
            return "regulatory-signs-jpg/611.jpg"
        case .miniRoundabout:
            return "regulatory-signs-jpg/611.1.jpg"
        case .busLane:
            return "bus-and-cycle-signs-jpg/958.jpg"
        case .busStop:
            return "bus-and-cycle-signs-jpg/975.jpg"
        case .giveWay:
            return "regulatory-signs-jpg/602.jpg"
        case .schoolZone:
            return "warning-signs-jpg/545.jpg"
        case .noEntry:
            return "regulatory-signs-jpg/616.jpg"
        case .speedCamera:
            return "speed-limit-signs-jpg/880.jpg"
        case .unknown:
            return "warning-signs-jpg/501.jpg"
        }
    }

    private func markerSizedImage(_ image: UIImage, maxDimension: CGFloat = 56) -> UIImage {
        let size = image.size
        let maxSide = max(size.width, size.height)
        guard maxSide > maxDimension, maxSide > 0 else {
            return image.withRenderingMode(.alwaysOriginal)
        }
        let scale = maxDimension / maxSide
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        let rendered = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return rendered.withRenderingMode(.alwaysOriginal)
    }

    private func advisorySignImage(
        type: PromptType,
        signImagePath: String?,
        signCode: String?
    ) -> UIImage? {
        if let direct = KnowYourSignsImageResolver.image(for: signImagePath) {
            return direct
        }
        if let byCode = KnowYourSignsImageResolver.image(forSignCode: signCode) {
            return byCode
        }
        if let fallbackPath = advisoryFallbackSignImagePath(for: type) {
            return KnowYourSignsImageResolver.image(for: fallbackPath)
        }
        return nil
    }

    private func resolveMapboxUIKitIconsBundle() -> Bundle? {
        if let directPath = Bundle.main.path(
            forResource: "MapboxNavigation_MapboxNavigationUIKit",
            ofType: "bundle"
        ),
            let directBundle = Bundle(path: directPath)
        {
            return directBundle
        }

        let bundles = Bundle.allBundles + Bundle.allFrameworks
        return bundles.first { $0.bundlePath.contains("MapboxNavigation_MapboxNavigationUIKit.bundle") }
    }

    private func mapboxIcon(named name: String) -> UIImage? {
        UIImage(named: name, in: mapboxUIKitIconsBundle, compatibleWith: nil)
    }

    private func showPromptBanner(_ prompt: PromptEvent) {
        promptBannerContainer.backgroundColor = promptBannerBackgroundColor(for: prompt.type)
        if let signTitle = prompt.signTitle?.trimmingCharacters(in: .whitespacesAndNewlines),
            !signTitle.isEmpty
        {
            promptBannerLabel.text = "\(prompt.message)\n\(signTitle)"
        } else {
            promptBannerLabel.text = prompt.message
        }
        let signImage = advisorySignImage(for: prompt)
        if let image = signImage {
            promptSignImageView.image = image
            promptSignImageView.isHidden = false
            promptSignImageWidthConstraint?.constant = 30
        } else {
            promptSignImageView.image = nil
            promptSignImageView.isHidden = true
            promptSignImageWidthConstraint?.constant = 0
        }
        if signImage != nil {
            promptTypeIconView.image = nil
            promptTypeIconView.isHidden = true
            promptTypeIconWidthConstraint?.constant = 0
        } else if let promptIcon = mapboxIcon(named: advisoryMapIconName(for: prompt.type)) {
            promptTypeIconView.image = promptIcon.withRenderingMode(.alwaysTemplate)
            promptTypeIconView.tintColor = .white
            promptTypeIconView.isHidden = false
            promptTypeIconWidthConstraint?.constant = 20
        } else {
            promptTypeIconView.image = nil
            promptTypeIconView.isHidden = true
            promptTypeIconWidthConstraint?.constant = 0
        }
        promptBannerContainer.isHidden = false
        promptAutoHideTask?.cancel()
        promptAutoHideTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 6_000_000_000)
            await MainActor.run {
                self?.promptBannerContainer.isHidden = true
            }
        }
    }

    private func advisoryMapIconName(for type: PromptType) -> String {
        switch type {
        case .trafficSignal:
            return "TrafficSignalDay"
        case .zebraCrossing:
            return "RailroadCrossingDay"
        case .roundabout, .miniRoundabout:
            return "feedback_lane_quidance"
        case .busLane:
            return "feedback_lane_quidance"
        case .busStop:
            return "pin"
        case .giveWay:
            return "YieldSignDay"
        case .schoolZone:
            return "feedback_traffic"
        case .noEntry:
            return "StopSignDay"
        case .speedCamera:
            return "feedback_camera"
        case .unknown:
            return "feedback_icon"
        }
    }

    private func advisoryMarkerImage(for feature: HazardFeature) -> UIImage? {
        if let signImage = advisorySignImage(
            type: feature.type,
            signImagePath: feature.signImagePath,
            signCode: feature.signCode
        ) {
            return markerSizedImage(signImage)
        }
        if let unknownFallback = KnowYourSignsImageResolver.image(
            for: advisoryFallbackSignImagePath(for: .unknown)
        ) {
            return markerSizedImage(unknownFallback)
        }
        return UIImage(systemName: "mappin.circle.fill")?.withRenderingMode(.alwaysOriginal)
    }

    private func advisoriesVisibleForCurrentSettings() -> [HazardFeature] {
        guard settingsStore.hazardsEnabled else {
            return hazardFeatures.filter { $0.type == .noEntry }
        }
        return hazardFeatures
    }

    private func ensureHazardPointAnnotationManager(
        for navigationVC: NavigationViewController
    ) -> PointAnnotationManager? {
        if let existing = hazardPointAnnotationManager {
            return existing
        }
        guard let mapView = navigationVC.navigationMapView?.mapView else { return nil }
        let manager = mapView.annotations.makePointAnnotationManager(
            id: "drivest-hazard-markers-\(mode.rawValue)"
        )
        manager.iconAllowOverlap = true
        manager.iconIgnorePlacement = true
        manager.iconSize = 0.62
        manager.iconAnchor = .bottom
        manager.symbolSortKey = 50
        manager.slot = "middle"
        hazardPointAnnotationManager = manager
        return manager
    }

    private func clearHazardMapAnnotations() {
        guard let manager = hazardPointAnnotationManager else { return }
        manager.annotations = []
        if let mapView = activeNavigationViewController?.navigationMapView?.mapView {
            mapView.annotations.removeAnnotationManager(withId: manager.id)
        }
        hazardPointAnnotationManager = nil
    }

    private func refreshHazardMapAnnotations() {
        guard let navigationVC = activeNavigationViewController,
            let manager = ensureHazardPointAnnotationManager(for: navigationVC)
        else { return }

        let features = advisoriesVisibleForCurrentSettings()
        let annotations: [PointAnnotation] = features.compactMap { feature in
            let coordinate = CLLocationCoordinate2D(latitude: feature.lat, longitude: feature.lon)
            guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }

            var annotation = PointAnnotation(
                id: "hazard-marker-\(feature.id)",
                coordinate: coordinate
            )
            if let markerImage = advisoryMarkerImage(for: feature) {
                let safeFeatureId = feature.id.replacingOccurrences(
                    of: "[^a-zA-Z0-9_-]",
                    with: "-",
                    options: .regularExpression
                )
                let imageName = "drivest-hazard-icon-\(safeFeatureId)"
                annotation.image = .init(image: markerImage, name: imageName)
            }
            annotation.iconOffset = [0, -1.5]
            annotation.iconSize = 0.9
            annotation.symbolSortKey = Double(promptEnginePriority(for: feature.type))
            return annotation
        }

        manager.annotations = annotations
        let byType = Dictionary(grouping: features, by: \.type)
            .map { "\($0.key.rawValue)=\($0.value.count)" }
            .sorted()
            .joined(separator: ",")
        print("[Drivest iOS] hazard_markers_rendered count=\(annotations.count) by_type={\(byType)}")
    }

    private func promptEnginePriority(for type: PromptType) -> Int {
        switch type {
        case .noEntry:
            return 7
        case .roundabout:
            return 6
        case .miniRoundabout, .speedCamera:
            return 5
        case .schoolZone:
            return 4
        case .zebraCrossing, .giveWay:
            return 3
        case .trafficSignal:
            return 2
        case .busStop, .busLane, .unknown:
            return 1
        }
    }

    private func resolvePracticeOriginAndRequestPreview() {
        guard mode == .practice else {
            requestPreviewRoute()
            return
        }
        #if targetEnvironment(simulator)
            requestPreviewRoute()
            return
        #else
            if practiceOriginCoordinate != nil {
                requestPreviewRoute()
                return
            }
            guard CLLocationManager.locationServicesEnabled() else {
                requestPreviewRoute()
                return
            }
            practiceOriginLocationManager.delegate = self
            practiceOriginLocationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation

            if let cached = practiceOriginLocationManager.location?.coordinate {
                practiceOriginCoordinate = cached
                if let routeStart = practiceRoute?.geometry.first?.coordinate {
                    let distance = coordinatesDistanceMeters(cached, routeStart)
                    print(
                        "[Drivest iOS] practice_origin_resolved source=cached " +
                            "distance_to_start_m=\(Int(distance.rounded()))"
                    )
                }
                requestPreviewRoute()
                return
            }

            let status = practiceOriginLocationManager.authorizationStatus
            switch status {
            case .authorizedAlways, .authorizedWhenInUse:
                isResolvingPracticeOrigin = true
                summaryLabel.text = "Locating you to guide to route start..."
                practiceOriginLocationManager.requestLocation()
                requestPreviewRoute()
            case .notDetermined:
                isResolvingPracticeOrigin = true
                summaryLabel.text = "Locating you to guide to route start..."
                practiceOriginLocationManager.requestWhenInUseAuthorization()
                requestPreviewRoute()
            case .denied, .restricted:
                requestPreviewRoute()
            @unknown default:
                requestPreviewRoute()
            }
        #endif
    }

    private func maybeAutoCompletePractice(
        progress: RouteProgress,
        navigationViewController: NavigationViewController
    ) {
        guard mode == .practice else { return }
        guard !didAutoCompletePractice else { return }
        let isComplete =
            progress.fractionTraveled >= practiceRouteAutoCompleteMinFraction ||
            progress.distanceRemaining <= practiceRouteAutoCompleteRemainingMeters
        guard isComplete else { return }

        didAutoCompletePractice = true
        print(
            "[Drivest iOS] practice_route_completed " +
                "fraction=\(String(format: "%.3f", progress.fractionTraveled)) " +
                "remaining_m=\(Int(progress.distanceRemaining.rounded()))"
        )
        handleSessionStop(canceled: false, summaryText: "Practice route completed.")
        navigationViewController.dismiss(animated: true) { [weak self] in
            self?.navigationController?.popViewController(animated: true)
        }
    }

    private func handleSessionStop(canceled: Bool, summaryText: String) {
        guard !didHandleSessionStop else { return }
        didHandleSessionStop = true
        promptAutoHideTask?.cancel()
        promptBannerContainer.isHidden = true
        routeProgressContainer.isHidden = true
        speedometerCard.isHidden = true
        speedLimitBubble.isHidden = true
        clearHazardMapAnnotations()
        activeNavigationViewController = nil
        lastAppliedCameraPhase = nil
        lastAppliedCameraMode = nil
        hazardVoiceController.stopSpeaking()
        parityStateStore.markObserverDetached()
        print("[Drivest iOS] session_stop canceled=\(canceled)")
        summaryLabel.text = summaryText
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
        syncNavigationVoiceMode()
        refreshHazardMapAnnotations()
        if let navigationVC = activeNavigationViewController {
            applyGuidanceProfile(to: navigationVC, progress: nil, force: true)
            updateSpeedometerOverlay(
                speedMps: max(lastEffectiveSpeedMps, 0),
                navigationViewController: navigationVC
            )
        }
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
            self.applyGuidanceProfile(to: navigationViewController, progress: progress)
            self.maybeAutoCompletePractice(
                progress: progress,
                navigationViewController: navigationViewController
            )
            if nowMs - self.lastPromptEvaluationMs < 1000 {
                return
            }
            self.lastPromptEvaluationMs = nowMs
            self.maybeRefreshHazardsIfNeeded(nowMs: nowMs, location: location)

            let upcomingDistance = progress.currentLegProgress.currentStepProgress.distanceRemaining
            let upcomingTime = progress.currentLegProgress.currentStepProgress.durationRemaining
            let profile = self.currentGuidanceProfile()
            let nearbyFeatures = self.filterHazardsNearLocation(
                self.hazardFeatures,
                coordinate: location.coordinate,
                radiusMeters: profile.nearbyHazardQueryRadiusMeters
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
            self.isManeuverSpeechPlaying = upcomingTime < 1.2

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
                sensitivity: self.effectivePromptSensitivity(),
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

            self.showPromptBanner(prompt)
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
            if canceled {
                self.handleSessionStop(canceled: true, summaryText: "Navigation stopped.")
            } else {
                self.handleSessionStop(canceled: false, summaryText: "Navigation completed.")
            }
            if navigationViewController.presentingViewController != nil {
                navigationViewController.dismiss(animated: true)
            }
        }
    }

    private func shouldSpeakHazardPrompt(_ prompt: PromptEvent) -> Bool {
        let maxDistanceMeters = hazardVoiceMaxDistanceMeters(for: prompt.type)
        if prompt.distanceM > maxDistanceMeters {
            return false
        }
        if !voiceModePolicy.canSpeak(prompt.type, voiceMode: settingsStore.voiceMode) {
            return false
        }
        return prompt.confidenceHint >= hazardVoiceMinConfidence(for: prompt.type)
    }

    private func hazardVoiceMinConfidence(for type: PromptType) -> Double {
        let base: Double
        switch type {
        case .noEntry:
            base = 0.45
        case .roundabout, .miniRoundabout:
            base = 0.60
        case .schoolZone:
            base = 0.60
        case .speedCamera:
            base = 0.65
        case .trafficSignal, .zebraCrossing, .giveWay:
            base = 0.55
        case .busLane:
            base = 0.55
        case .busStop:
            base = 0.55
        case .unknown:
            base = 0.50
        default:
            base = hazardVoiceMinConfidenceHint
        }
        let adjusted = base + currentGuidanceProfile().hazardConfidenceDelta
        return min(0.95, max(0.35, adjusted))
    }

    private func hazardVoiceMaxDistanceMeters(for type: PromptType) -> Int {
        let base: Int
        switch type {
        case .noEntry:
            base = noEntryVoiceProximityMeters
        case .roundabout:
            base = 180
        case .miniRoundabout:
            base = 110
        case .schoolZone:
            base = 150
        case .zebraCrossing:
            base = 130
        case .giveWay:
            base = 120
        case .trafficSignal:
            base = 150
        case .speedCamera:
            base = 250
        case .busLane:
            base = 160
        case .busStop:
            base = 130
        case .unknown:
            base = hazardVoiceProximityMeters
        }
        let adjusted = Int((Double(base) * currentGuidanceProfile().hazardLeadDistanceMultiplier).rounded())
        return max(60, adjusted)
    }

    private func updateRouteProgressOverlay(progress: RouteProgress) {
        let completionPercent = max(0, min(100, Int((progress.fractionTraveled * 100).rounded())))
        routeProgressSummaryLabel.text = "\(completionPercent)%"
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
            speedLimitBubble.isHidden = false
        } else {
            speedLimitBubble.isHidden = true
            speedLimitBadgeLabel.text = nil
        }

        let isOverLimit = if let postedLimitValue {
            roundedSpeed > postedLimitValue
        } else {
            false
        }
        if isOverLimit {
            speedometerCard.backgroundColor = UIColor(red: 0.42, green: 0.08, blue: 0.09, alpha: 0.94)
            speedLimitBubble.backgroundColor = UIColor(red: 0.89, green: 0.27, blue: 0.23, alpha: 0.95)
        } else {
            speedometerCard.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.92)
            speedLimitBubble.backgroundColor = UIColor.white.withAlphaComponent(0.2)
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
            // Practice routes are often loops that return near the same test centre.
            // If we collapse to just [start, end] and those points are too close relative
            // to the full route length, Directions can announce arrival almost immediately.
            // In that case, inject a farthest shaping waypoint to preserve loop guidance.
            let startToEndMeters = coordinatesDistanceMeters(first, last)
            let routeLengthMeters = polylineLengthMeters(deduped)
            let isLoopLike =
                startToEndMeters <= 250 ||
                (routeLengthMeters > 0 && (startToEndMeters / routeLengthMeters) <= 0.12)
            if isLoopLike,
                let shapingPoint = farthestCoordinate(from: first, in: deduped)
            {
                return dedupeSequentialCoordinates([first, shapingPoint, last])
            }
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

    private func farthestCoordinate(
        from origin: CLLocationCoordinate2D,
        in coordinates: [CLLocationCoordinate2D]
    ) -> CLLocationCoordinate2D? {
        guard coordinates.count >= 3 else { return nil }
        var best: CLLocationCoordinate2D?
        var maxDistance = 0.0
        let originLocation = CLLocation(latitude: origin.latitude, longitude: origin.longitude)
        for coordinate in coordinates.dropFirst().dropLast() {
            let candidate = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
            let distance = originLocation.distance(from: candidate)
            if distance > maxDistance {
                maxDistance = distance
                best = coordinate
            }
        }
        return best
    }

    private func coordinatesDistanceMeters(
        _ a: CLLocationCoordinate2D,
        _ b: CLLocationCoordinate2D
    ) -> Double {
        CLLocation(latitude: a.latitude, longitude: a.longitude)
            .distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude))
    }

    private func polylineLengthMeters(_ coordinates: [CLLocationCoordinate2D]) -> Double {
        guard coordinates.count >= 2 else { return 0 }
        var total = 0.0
        for index in 1..<coordinates.count {
            total += coordinatesDistanceMeters(coordinates[index - 1], coordinates[index])
        }
        return total
    }

    private func isPracticePreviewDistanceValid(_ routes: NavigationRoutes) -> Bool {
        let routeDistance = routes.mainRoute.route.distance
        let expectedDistance = practiceRoute?.distanceM ?? routeDistance
        let minimumAcceptableDistance = max(
            practiceRouteMinValidDistanceMeters,
            expectedDistance * practiceRouteDistanceValidityRatio
        )
        let stepCount = routes.mainRoute.route.legs.reduce(0) { partialResult, leg in
            partialResult + leg.steps.count
        }
        let isValid = routeDistance >= minimumAcceptableDistance && stepCount >= 2
        if !isValid {
            print(
                "[Drivest iOS] practice_preview_rejected_short_route " +
                    "actual=\(Int(routeDistance.rounded())) " +
                    "expected=\(Int(expectedDistance.rounded())) " +
                    "min=\(Int(minimumAcceptableDistance.rounded())) " +
                    "steps=\(stepCount)"
            )
        }
        return isValid
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
            self.refreshHazardMapAnnotations()
            self.lastHazardFetchAnchor = nil
            self.loadHazardFeatures(routeCoordinates: self.activeRouteCoordinates, force: true)
            print(
                "[Drivest iOS] hazards_refresh reason=reroute route_points=\(self.activeRouteCoordinates.count)"
            )
        }
    }
}

extension NavigationSessionViewController: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let authorizationStatus = manager.authorizationStatus
        Task { @MainActor [weak self] in
            guard let self else { return }
            guard self.mode == .practice else { return }
            guard self.practiceOriginCoordinate == nil else { return }
            guard self.isResolvingPracticeOrigin else { return }

            switch authorizationStatus {
            case .authorizedAlways, .authorizedWhenInUse:
                self.practiceOriginLocationManager.requestLocation()
            case .denied, .restricted:
                self.isResolvingPracticeOrigin = false
                print("[Drivest iOS] practice_origin_resolution_failed reason=location_denied")
            case .notDetermined:
                break
            @unknown default:
                self.isResolvingPracticeOrigin = false
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let latestCoordinate = locations.last?.coordinate
        Task { @MainActor [weak self] in
            guard let self else { return }
            guard self.mode == .practice else { return }
            guard self.practiceOriginCoordinate == nil else { return }
            guard let latestCoordinate else { return }

            self.practiceOriginCoordinate = latestCoordinate
            self.isResolvingPracticeOrigin = false
            if let routeStart = self.practiceRoute?.geometry.first?.coordinate {
                let distance = self.coordinatesDistanceMeters(latestCoordinate, routeStart)
                print(
                    "[Drivest iOS] practice_origin_resolved source=gps " +
                        "distance_to_start_m=\(Int(distance.rounded()))"
                )
            }

            if self.activeNavigationViewController == nil {
                self.previewLoaded = false
                self.startButton.isEnabled = false
                self.renderPreviewMap(with: self.buildCoordinateList())
                self.requestPreviewRoute()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let failureReason = error.localizedDescription
        Task { @MainActor [weak self] in
            guard let self else { return }
            guard self.mode == .practice else { return }
            self.isResolvingPracticeOrigin = false
            print("[Drivest iOS] practice_origin_resolution_failed reason=\(failureReason)")
        }
    }
}
