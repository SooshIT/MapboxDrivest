import UIKit

final class HomeViewController: UIViewController {
    private let settingsStore = SettingsStore.shared
    private let centreRepository = CentreRepository()
    private let routeStore: PracticeRouteStore = DataSourcePracticeRouteStore()

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let subtitleLabel = UILabel()
    private let driverModeLabel = UILabel()
    private let confidenceLabel = UILabel()
    private let recommendedLabel = UILabel()
    private let recommendedValueLabel = UILabel()
    private let theoryReadinessLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Drivest"
        view.backgroundColor = DrivestPalette.pageBackground
        setupLayout()
        renderDynamicContent()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        renderDynamicContent()
    }

    private func setupLayout() {
        navigationItem.largeTitleDisplayMode = .never
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "gearshape.fill"),
            style: .plain,
            target: self,
            action: #selector(openSettings)
        )

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 12

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

        theoryReadinessLabel.font = .systemFont(ofSize: 13, weight: .bold)
        theoryReadinessLabel.textColor = DrivestPalette.accentBlue
        theoryReadinessLabel.textAlignment = .center
        theoryReadinessLabel.text = "Readiness: Building"
        contentStack.addArrangedSubview(theoryReadinessLabel)
    }

    private func buildHeroCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let logoImageView = DrivestBrand.logoImageView()
        logoImageView.isUserInteractionEnabled = true
        let logoLongPress = UILongPressGestureRecognizer(target: self, action: #selector(openDebugFromLogo))
        logoImageView.addGestureRecognizer(logoLongPress)

        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.text = "Choose your mode to start"
        subtitleLabel.textColor = DrivestPalette.textSecondary
        subtitleLabel.font = .systemFont(ofSize: 17, weight: .regular)
        subtitleLabel.textAlignment = .center

        driverModeLabel.translatesAutoresizingMaskIntoConstraints = false
        driverModeLabel.textColor = DrivestPalette.textPrimary
        driverModeLabel.font = .systemFont(ofSize: 15, weight: .bold)
        driverModeLabel.textAlignment = .center

        confidenceLabel.translatesAutoresizingMaskIntoConstraints = false
        confidenceLabel.textColor = DrivestPalette.accentTeal
        confidenceLabel.font = .systemFont(ofSize: 15, weight: .bold)
        confidenceLabel.textAlignment = .center

        recommendedLabel.translatesAutoresizingMaskIntoConstraints = false
        recommendedLabel.text = "Recommended Today"
        recommendedLabel.textColor = DrivestPalette.accentBlue
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

        row.addArrangedSubview(practiceButton)
        row.addArrangedSubview(navigationButton)
        row.addArrangedSubview(theoryButton)

        NSLayoutConstraint.activate([
            practiceButton.heightAnchor.constraint(equalToConstant: 96),
            navigationButton.heightAnchor.constraint(equalToConstant: 96),
            theoryButton.heightAnchor.constraint(equalToConstant: 96)
        ])

        return row
    }

    private func makeModeButton(title: String, symbol: String, filled: Bool, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false

        var configuration: UIButton.Configuration = filled ? .filled() : .tinted()
        configuration.cornerStyle = .large
        configuration.image = UIImage(systemName: symbol)
        configuration.imagePlacement = .top
        configuration.imagePadding = 8
        configuration.title = title
        configuration.baseBackgroundColor = filled ? DrivestPalette.accentBlue : .secondarySystemBackground
        configuration.baseForegroundColor = filled ? .white : DrivestPalette.textPrimary
        button.configuration = configuration
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
        navigationController?.pushViewController(PracticeEntryViewController(), animated: true)
    }

    @objc
    private func openNavigation() {
        settingsStore.lastMode = .navigation
        navigationController?.pushViewController(NavigationEntryViewController(), animated: true)
    }

    @objc
    private func openTheory() {
        navigationController?.pushViewController(TheoryHomePlaceholderViewController(), animated: true)
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
}
