import UIKit

final class HomeViewController: UIViewController {
    private let settingsStore = SettingsStore.shared
    private let centreRepository = CentreRepository()
    private let routeStore: PracticeRouteStore = DataSourcePracticeRouteStore()
    private let theoryProgressStore = TheoryProgressStore.shared
    private let theoryPackLoader = TheoryPackLoader.shared

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let subtitleLabel = UILabel()
    private let driverModeLabel = UILabel()
    private let confidenceLabel = UILabel()
    private let recommendedLabel = UILabel()
    private let recommendedValueLabel = UILabel()
    private let theoryReadinessLabel = UILabel()
    private let theoryProgressBadgeLabel = UILabel()
    private var didAttemptSimulatorAutoStart = false

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Drivest"
        view.backgroundColor = .clear
        setupLayout()
        renderDynamicContent()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        renderDynamicContent()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        maybeAutoStartPracticeInSimulator()
    }

    private func setupLayout() {
        navigationItem.largeTitleDisplayMode = .never
        let settingsButton = UIButton(type: .system)
        settingsButton.translatesAutoresizingMaskIntoConstraints = false
        var settingsConfig = UIButton.Configuration.plain()
        settingsConfig.image = UIImage(systemName: "gearshape.fill")
        settingsConfig.baseForegroundColor = DrivestPalette.textPrimary
        settingsConfig.background.backgroundColor = UIColor.white.withAlphaComponent(0.95)
        settingsConfig.background.cornerRadius = 18
        settingsConfig.background.strokeColor = DrivestPalette.cardStroke
        settingsConfig.background.strokeWidth = 1
        settingsButton.configuration = settingsConfig
        settingsButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
        NSLayoutConstraint.activate([
            settingsButton.widthAnchor.constraint(equalToConstant: 36),
            settingsButton.heightAnchor.constraint(equalToConstant: 36)
        ])
        navigationItem.rightBarButtonItem = UIBarButtonItem(customView: settingsButton)

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 14

        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 20),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -20),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
        ])

        contentStack.addArrangedSubview(buildHeroCard())
        contentStack.addArrangedSubview(buildModeButtonsRow())

        theoryProgressBadgeLabel.font = .systemFont(ofSize: 12, weight: .bold)
        theoryProgressBadgeLabel.textColor = DrivestPalette.accentChipText
        theoryProgressBadgeLabel.backgroundColor = DrivestPalette.accentPrimarySoft
        theoryProgressBadgeLabel.layer.cornerRadius = 8
        theoryProgressBadgeLabel.layer.masksToBounds = true
        theoryProgressBadgeLabel.textAlignment = .center
        theoryProgressBadgeLabel.text = "Theory progress: 0%"
        theoryProgressBadgeLabel.heightAnchor.constraint(equalToConstant: 28).isActive = true
        contentStack.addArrangedSubview(theoryProgressBadgeLabel)

        theoryReadinessLabel.font = .systemFont(ofSize: 13, weight: .bold)
        theoryReadinessLabel.textColor = DrivestPalette.accentPrimary
        theoryReadinessLabel.textAlignment = .center
        theoryReadinessLabel.text = "Readiness: Building"
        contentStack.addArrangedSubview(theoryReadinessLabel)
    }

    private func buildHeroCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 24)

        let logoImageView = DrivestBrand.logoImageView()
        logoImageView.isUserInteractionEnabled = true
        let logoLongPress = UILongPressGestureRecognizer(target: self, action: #selector(openDebugFromLogo))
        logoImageView.addGestureRecognizer(logoLongPress)

        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.text = "Choose your mode to start"
        subtitleLabel.textColor = DrivestPalette.textSecondary
        subtitleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        subtitleLabel.textAlignment = .center

        driverModeLabel.translatesAutoresizingMaskIntoConstraints = false
        driverModeLabel.textColor = DrivestPalette.textPrimary
        driverModeLabel.font = .systemFont(ofSize: 15, weight: .bold)
        driverModeLabel.textAlignment = .center

        confidenceLabel.translatesAutoresizingMaskIntoConstraints = false
        confidenceLabel.textColor = DrivestPalette.accentChipText
        confidenceLabel.font = .systemFont(ofSize: 15, weight: .bold)
        confidenceLabel.textAlignment = .center

        recommendedLabel.translatesAutoresizingMaskIntoConstraints = false
        recommendedLabel.text = "Recommended Today"
        recommendedLabel.textColor = DrivestPalette.accentPrimary
        recommendedLabel.font = .systemFont(ofSize: 13, weight: .bold)
        recommendedLabel.textAlignment = .center
        recommendedLabel.isHidden = true

        recommendedValueLabel.translatesAutoresizingMaskIntoConstraints = false
        recommendedValueLabel.font = .systemFont(ofSize: 14, weight: .bold)
        recommendedValueLabel.textColor = DrivestPalette.textPrimary
        recommendedValueLabel.textAlignment = .center
        recommendedValueLabel.numberOfLines = 2
        recommendedValueLabel.isHidden = true

        card.addSubview(logoImageView)
        card.addSubview(subtitleLabel)
        card.addSubview(driverModeLabel)
        card.addSubview(confidenceLabel)
        card.addSubview(recommendedLabel)
        card.addSubview(recommendedValueLabel)

        NSLayoutConstraint.activate([
            logoImageView.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            logoImageView.centerXAnchor.constraint(equalTo: card.centerXAnchor),
            logoImageView.heightAnchor.constraint(equalToConstant: 82),
            logoImageView.widthAnchor.constraint(lessThanOrEqualTo: card.widthAnchor, multiplier: 0.78),

            subtitleLabel.topAnchor.constraint(equalTo: logoImageView.bottomAnchor, constant: 12),
            subtitleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            subtitleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            driverModeLabel.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 8),
            driverModeLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            driverModeLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            confidenceLabel.topAnchor.constraint(equalTo: driverModeLabel.bottomAnchor, constant: 8),
            confidenceLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            confidenceLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            recommendedLabel.topAnchor.constraint(equalTo: confidenceLabel.bottomAnchor, constant: 12),
            recommendedLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            recommendedLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            recommendedValueLabel.topAnchor.constraint(equalTo: recommendedLabel.bottomAnchor, constant: 4),
            recommendedValueLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            recommendedValueLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            recommendedValueLabel.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])

        return card
    }

    private func buildModeButtonsRow() -> UIView {
        let container = UIStackView()
        container.axis = .vertical
        container.spacing = 8

        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.spacing = 8

        let practiceButton = makeModeButton(
            title: "Practice",
            symbol: "figure.walk",
            filled: true,
            action: #selector(openPractice)
        )
        let navigationButton = makeModeButton(
            title: "Navigation",
            symbol: "location.fill",
            filled: false,
            action: #selector(openNavigation)
        )
        let theoryButton = makeModeButton(
            title: "Theory",
            symbol: "book.fill",
            filled: false,
            action: #selector(openTheory)
        )
        let highwayCodeButton = makeModeButton(
            title: "Highway Code",
            symbol: "road.lanes",
            filled: false,
            action: #selector(openHighwayCode)
        )
        let knowYourSignsButton = makeModeButton(
            title: "Know Your Signs",
            symbol: "rectangle.and.text.magnifyingglass",
            filled: false,
            action: #selector(openKnowYourSigns)
        )

        row.addArrangedSubview(practiceButton)
        row.addArrangedSubview(navigationButton)
        row.addArrangedSubview(theoryButton)
        container.addArrangedSubview(row)
        container.addArrangedSubview(highwayCodeButton)
        container.addArrangedSubview(knowYourSignsButton)

        NSLayoutConstraint.activate([
            practiceButton.heightAnchor.constraint(equalToConstant: 96),
            navigationButton.heightAnchor.constraint(equalToConstant: 96),
            theoryButton.heightAnchor.constraint(equalToConstant: 96),
            highwayCodeButton.heightAnchor.constraint(equalToConstant: 84),
            knowYourSignsButton.heightAnchor.constraint(equalToConstant: 84)
        ])

        return container
    }

    private func makeModeButton(title: String, symbol: String, filled: Bool, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false

        var configuration: UIButton.Configuration = filled ? .filled() : .plain()
        configuration.cornerStyle = .large
        configuration.image = UIImage(systemName: symbol)
        configuration.imagePlacement = .top
        configuration.imagePadding = 8
        configuration.title = title
        button.configuration = configuration
        if filled {
            DrivestBrand.stylePrimaryButton(button)
        } else {
            DrivestBrand.styleOutlinedButton(button)
        }
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }

    private func renderDynamicContent() {
        driverModeLabel.text = "Mode: \(driverModeDisplayName(settingsStore.driverMode))"
        confidenceLabel.text = "Confidence score: \(confidenceForMode(settingsStore.driverMode))/100"

        let recommendedRouteName = recommendedRouteNameForLastCentre()
        let hasRecommendation = recommendedRouteName != nil
        recommendedLabel.isHidden = !hasRecommendation
        recommendedValueLabel.isHidden = !hasRecommendation
        recommendedValueLabel.text = recommendedRouteName

        let pack = theoryPackLoader.load()
        let progress = theoryProgressStore.progress
        let readiness = TheoryReadinessCalculator.calculate(progress: progress, totalTopics: pack.topics.count)
        theoryProgressBadgeLabel.text = "Theory progress: \(readiness.masteredTopicsPercent)%"
        theoryReadinessLabel.text = "Readiness: \(readinessLabel(readiness.label))"
    }

    private func readinessLabel(_ label: TheoryReadinessLabel) -> String {
        switch label {
        case .building:
            return "Building"
        case .almostReady:
            return "Almost ready"
        case .ready:
            return "Ready"
        }
    }

    private func recommendedRouteNameForLastCentre() -> String? {
        guard let centre = centreRepository.findById(settingsStore.lastCentreId) else { return nil }
        let routes = routeStore.loadRoutesForCentre(centre.id)
        return routes.first?.name
    }

    private func driverModeDisplayName(_ mode: DriverMode) -> String {
        switch mode {
        case .learner:
            return "Learner"
        case .newDriver:
            return "New Driver"
        case .standard:
            return "Standard"
        }
    }

    private func confidenceForMode(_ mode: DriverMode) -> Int {
        switch mode {
        case .learner:
            return 42
        case .newDriver:
            return 63
        case .standard:
            return 78
        }
    }

    @objc
    private func openPractice() {
        settingsStore.lastMode = .practice
        print("[Drivest iOS] home_open_practice")
        navigationController?.pushViewController(CentrePickerViewController(), animated: true)
    }

    @objc
    private func openNavigation() {
        settingsStore.lastMode = .navigation
        print("[Drivest iOS] home_open_navigation")
        navigationController?.pushViewController(DestinationSearchViewController(), animated: true)
    }

    @objc
    private func openTheory() {
        navigationController?.pushViewController(TheoryHomeViewController(), animated: true)
    }

    @objc
    private func openHighwayCode() {
        navigationController?.pushViewController(HighwayCodeModuleViewController(), animated: true)
    }

    @objc
    private func openKnowYourSigns() {
        navigationController?.pushViewController(KnowYourSignsModuleViewController(), animated: true)
    }

    @objc
    private func openSettings() {
        navigationController?.pushViewController(SettingsViewController(), animated: true)
    }

    @objc
    private func openDebugFromLogo(_ recognizer: UILongPressGestureRecognizer) {
        guard recognizer.state == .began else { return }
        navigationController?.pushViewController(DebugParityViewController(), animated: true)
    }

    private func maybeAutoStartPracticeInSimulator() {
        #if targetEnvironment(simulator)
            guard !didAttemptSimulatorAutoStart else { return }
            didAttemptSimulatorAutoStart = true

            let processInfo = ProcessInfo.processInfo
            let args = processInfo.arguments
            let env = processInfo.environment
            let requestedByArg = args.contains("--drivest-autostart-practice")
            let requestedByEnv = env["DRIVEST_AUTOSTART_PRACTICE"] == "1"
            guard requestedByArg || requestedByEnv else { return }

            let explicitCentreId = valueForLaunchArgument(
                "--drivest-centre-id",
                from: args
            )
            let targetCentreId = explicitCentreId ?? settingsStore.lastCentreId
            let centres = centreRepository.loadCentres()
            let selectedCentre =
                centres.first(where: { $0.id == targetCentreId }) ??
                centres.first
            guard let selectedCentre else {
                print("[Drivest iOS] simulator_autostart_failed reason=no_centres")
                return
            }

            let selectedRoute = routeStore.loadRoutesForCentre(selectedCentre.id).first
            guard let selectedRoute else {
                print(
                    "[Drivest iOS] simulator_autostart_failed " +
                        "reason=no_routes centre_id=\(selectedCentre.id)"
                )
                return
            }

            settingsStore.lastMode = .practice
            settingsStore.lastCentreId = selectedCentre.id
            settingsStore.hazardsEnabled = true
            settingsStore.dataSourceMode = .backendOnly

            print(
                "[Drivest iOS] simulator_autostart_practice " +
                    "centre_id=\(selectedCentre.id) route_id=\(selectedRoute.id)"
            )
            navigationController?.pushViewController(
                NavigationSessionViewController(
                    mode: .practice,
                    centre: selectedCentre,
                    practiceRoute: selectedRoute,
                    destination: nil
                ),
                animated: true
            )
        #endif
    }

    private func valueForLaunchArgument(_ key: String, from args: [String]) -> String? {
        guard let index = args.firstIndex(of: key) else { return nil }
        let valueIndex = args.index(after: index)
        guard valueIndex < args.endIndex else { return nil }
        let value = args[valueIndex].trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
