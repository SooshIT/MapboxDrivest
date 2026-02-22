import CoreLocation
import MapboxDirections
import MapboxNavigationCore
import MapboxNavigationUIKit
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

    private let mapboxNavigationProvider = MapboxNavigationProvider(coreConfig: .init(locationSource: .live))
    private var mapboxNavigation: MapboxNavigation {
        mapboxNavigationProvider.mapboxNavigation
    }

    private var previewRoutes: [NavigationRoute] = []
    private var previewLoaded = false

    private let summaryLabel = UILabel()
    private let startButton = UIButton(type: .system)
    private let voiceButton = UIButton(type: .system)
    private let promptBannerContainer = UIView()
    private let promptBannerLabel = UILabel()

    private weak var activeNavigationViewController: NavigationViewController?
    private var promptAutoHideTask: Task<Void, Never>?
    private var hazardFeatures: [HazardFeature] = []
    private var lastPromptEvaluationMs: Int64 = 0
    private var visualPromptsEnabled = true
    private var isManeuverSpeechPlaying = false
    private var activeRouteCoordinates: [CLLocationCoordinate2D] = []
    private var offRouteSmoothedDistanceM: Double?
    private var settingsObserver: NSObjectProtocol?

    private let minimumPromptEvaluationSpeedMps = 0.556
    private let offRouteSmoothingAlpha = 0.35
    private let hazardVoiceMinConfidenceHint = 0.80

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
        view.backgroundColor = .systemBackground
        visualPromptsEnabled = settingsStore.hazardsEnabled
        setupLayout()
        renderVoiceChip()
        loadHazardFeatures()
        requestPreviewRoute()
        observeSettings()
    }

    deinit {
        if let settingsObserver {
            NotificationCenter.default.removeObserver(settingsObserver)
        }
    }

    private func setupLayout() {
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

        voiceButton.translatesAutoresizingMaskIntoConstraints = false
        voiceButton.configuration = .bordered()
        voiceButton.addTarget(self, action: #selector(cycleVoiceMode), for: .touchUpInside)

        view.addSubview(summaryLabel)
        view.addSubview(startButton)
        view.addSubview(voiceButton)

        NSLayoutConstraint.activate([
            summaryLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            summaryLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            summaryLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -60),

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

        let options = NavigationRouteOptions(coordinates: coordinates)
        let request = mapboxNavigation.routingProvider().calculateRoutes(options: options)

        Task { [weak self] in
            guard let self else { return }
            switch await request.result {
            case .failure(let error):
                await MainActor.run {
                    self.summaryLabel.text = "Preview failed: \(error.localizedDescription)"
                    self.startButton.isEnabled = false
                }

            case .success(let navigationRoutes):
                await MainActor.run {
                    self.previewRoutes = navigationRoutes
                    self.activeRouteCoordinates = self.resolveRouteCoordinates(
                        route: navigationRoutes.first,
                        fallback: coordinates
                    )
                    if self.mode == .navigation {
                        self.parityStateStore.routesPackVersionId = "routes-mapbox-live-preview"
                    }
                    self.previewLoaded = true
                    self.startButton.isEnabled = true
                    self.renderPreviewSummary()
                }
            }
        }
    }

    private func buildCoordinateList() -> [CLLocationCoordinate2D] {
        if mode == .practice, let practiceRoute {
            return practiceRoute.geometry.map(\.coordinate)
        }

        let fallbackOrigin = CLLocationCoordinate2D(latitude: 51.872116, longitude: 0.928174)
        let origin = centre?.coordinate ?? fallbackOrigin
        guard let destination else { return [origin] }
        return [origin, destination.coordinate]
    }

    private func renderPreviewSummary() {
        guard previewLoaded, let route = previewRoutes.first else {
            summaryLabel.text = "Preview not available."
            return
        }
        let distanceMiles = route.route.distance / 1609.344
        let durationMinutes = route.route.expectedTravelTime / 60.0
        let summaryName = destination?.placeName ?? practiceRoute?.name ?? "Route"
        summaryLabel.text = String(
            format: "%@\n%.1f mi · %.0f min",
            summaryName,
            distanceMiles,
            durationMinutes
        )
    }

    @objc
    private func startNavigation() {
        guard previewLoaded, !previewRoutes.isEmpty else { return }
        guard activeNavigationViewController == nil else { return }
        print("[Drivest iOS] session_start mode=\(mode.rawValue) voice_mode=\(settingsStore.voiceMode.rawValue)")
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
        switch settingsStore.voiceMode {
        case .all:
            navigationItem.prompt = nil
        case .alerts:
            navigationItem.prompt = "Voice mode: Alerts only"
        case .mute:
            navigationItem.prompt = "Voice mode: Muted"
        }
    }

    private func loadHazardFeatures() {
        let centreId = centre?.id ?? settingsStore.lastCentreId
        guard settingsStore.hazardsEnabled else {
            hazardFeatures = []
            parityStateStore.lastPromptSuppressed = "hazards_disabled"
            print("[Drivest iOS] loaded_hazards centre_id=\(centreId) mode=disabled count=0")
            return
        }

        hazardFeatures = hazardRepository.loadHazardsForCentre(
            centreId,
            dataSourceMode: settingsStore.dataSourceMode
        )
        print(
            "[Drivest iOS] loaded_hazards centre_id=\(centreId) mode=\(settingsStore.dataSourceMode.rawValue) " +
                "count=\(hazardFeatures.count)"
        )
    }

    private func attachPromptBanner(on containerView: UIView) {
        promptBannerContainer.removeFromSuperview()
        promptBannerContainer.translatesAutoresizingMaskIntoConstraints = false
        promptBannerContainer.backgroundColor = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.95)
        promptBannerContainer.layer.cornerRadius = 10
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
        settingsObserver = NotificationCenter.default.addObserver(
            forName: .drivestSettingsChanged,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.visualPromptsEnabled = self.settingsStore.hazardsEnabled
        }
    }
}

extension NavigationSessionViewController: NavigationViewControllerDelegate {
    func navigationViewController(
        _ navigationViewController: NavigationViewController,
        didUpdate progress: RouteProgress,
        with location: CLLocation,
        rawLocation: CLLocation
    ) {
        updateOffRouteDiagnostics(progress: progress, location: location)

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        if nowMs - lastPromptEvaluationMs < 1000 {
            return
        }
        lastPromptEvaluationMs = nowMs

        let upcomingDistance = progress.currentLegProgress.currentStepProgress.distanceRemaining
        let upcomingTime = progress.currentLegProgress.currentStepProgress.durationRemaining
        isManeuverSpeechPlaying = upcomingTime < 6

        if upcomingTime < 6 {
            hazardVoiceController.onManeuverInstructionArrived()
        }
        if max(location.speed, 0) < minimumPromptEvaluationSpeedMps {
            parityStateStore.lastPromptSuppressed = "speed_below_gate"
            return
        }

        guard settingsStore.hazardsEnabled else {
            parityStateStore.lastPromptSuppressed = "hazards_disabled"
            return
        }
        guard !hazardFeatures.isEmpty else {
            parityStateStore.lastPromptSuppressed = PromptSuppressionReason.noFeatures.rawValue
            return
        }
        guard visualPromptsEnabled else {
            parityStateStore.lastPromptSuppressed = PromptSuppressionReason.visualDisabled.rawValue
            return
        }

        let evaluationResult = promptEngine.evaluate(
            nowMs: nowMs,
            locationLat: location.coordinate.latitude,
            locationLon: location.coordinate.longitude,
            gpsAccuracyM: max(location.horizontalAccuracy, 0),
            speedMps: max(location.speed, 0),
            upcomingManeuverDistanceM: upcomingDistance,
            upcomingManeuverTimeS: upcomingTime,
            features: hazardFeatures,
            visualEnabled: visualPromptsEnabled,
            sensitivity: settingsStore.promptSensitivity
        )
        if let suppression = evaluationResult.suppressionReason {
            parityStateStore.lastPromptSuppressed = suppression.rawValue
        }

        guard let prompt = evaluationResult.promptEvent else { return }

        showPromptBanner(prompt.message)
        parityStateStore.lastPromptFired = "\(prompt.type.rawValue) \(prompt.distanceM)m"
        guard shouldSpeakHazardPrompt(prompt) else {
            parityStateStore.lastPromptSuppressed = "voice_confidence_or_mode_suppressed"
            return
        }
        hazardVoiceController.enqueue(
            prompt,
            voiceMode: settingsStore.voiceMode,
            upcomingManeuverTimeS: upcomingTime,
            isManeuverSpeechPlaying: isManeuverSpeechPlaying
        )
    }

    func navigationViewControllerDidDismiss(
        _ navigationViewController: NavigationViewController,
        byCanceling canceled: Bool
    ) {
        navigationViewController.dismiss(animated: true)
        promptAutoHideTask?.cancel()
        promptBannerContainer.isHidden = true
        activeNavigationViewController = nil
        hazardVoiceController.stopSpeaking()
        parityStateStore.markObserverDetached()
        print("[Drivest iOS] session_stop canceled=\(canceled)")
        if canceled {
            summaryLabel.text = "Navigation stopped."
        } else {
            summaryLabel.text = "Navigation completed."
        }
    }

    private func shouldSpeakHazardPrompt(_ prompt: PromptEvent) -> Bool {
        if prompt.type == .busLane || prompt.type == .giveWay {
            return false
        }
        if !voiceModePolicy.canSpeak(prompt.type, voiceMode: settingsStore.voiceMode) {
            return false
        }
        return prompt.confidenceHint >= hazardVoiceMinConfidence(for: prompt.type)
    }

    private func hazardVoiceMinConfidence(for type: PromptType) -> Double {
        switch type {
        case .busStop:
            return 0.65
        case .schoolZone:
            return 0.70
        default:
            return hazardVoiceMinConfidenceHint
        }
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
        switch progress.routeState {
        case .offRoute:
            offRouteState = "OFF_ROUTE"
        case .tracking:
            offRouteState = "ON_ROUTE"
        default:
            offRouteState = String(describing: progress.routeState).uppercased()
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
}
