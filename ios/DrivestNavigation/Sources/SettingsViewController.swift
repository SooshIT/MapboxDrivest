import UIKit

final class SettingsViewController: UIViewController {
    private let settingsStore = SettingsStore.shared

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let driverModeControl = UISegmentedControl(items: ["Learner", "New Driver", "Standard"])
    private let voiceControl = UISegmentedControl(items: ["All", "Alerts", "Mute"])
    private let promptSensitivityControl = UISegmentedControl(items: ["Minimal", "Standard", "Extra help"])
    private let unitsControl = UISegmentedControl(items: ["UK (mph)", "Metric (km/h)"])
    private let dataSourceControl = UISegmentedControl(items: ["Backend/Cache/Assets", "Backend only", "Assets only"])

    private let confidenceLabel = UILabel()
    private let subscriptionLabel = UILabel()
    private let driverModeDescriptionLabel = UILabel()

    private let hazardsSwitch = UISwitch()
    private let lowStressRoutingSwitch = UISwitch()
    private let analyticsSwitch = UISwitch()
    private let notificationsSwitch = UISwitch()
    private let notificationsStatusLabel = UILabel()
    private let debugUnlockLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Settings"
        view.backgroundColor = .clear
        setupLayout()
        render()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    private func setupLayout() {
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
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -28),
        ])

        contentStack.addArrangedSubview(buildHeroCard())
        contentStack.addArrangedSubview(buildProfileSection())
        contentStack.addArrangedSubview(buildNavigationSection())
        contentStack.addArrangedSubview(buildPrivacySection())
        contentStack.addArrangedSubview(buildLegalSection())
        contentStack.addArrangedSubview(buildSupportSection())

        debugUnlockLabel.translatesAutoresizingMaskIntoConstraints = false
        debugUnlockLabel.text = "Drivest iOS"
        debugUnlockLabel.textColor = .tertiaryLabel
        debugUnlockLabel.font = .systemFont(ofSize: 12, weight: .regular)
        debugUnlockLabel.textAlignment = .center
        debugUnlockLabel.isUserInteractionEnabled = true
        let debugTapGesture = UITapGestureRecognizer(target: self, action: #selector(openHiddenDebug))
        debugTapGesture.numberOfTapsRequired = 5
        debugUnlockLabel.addGestureRecognizer(debugTapGesture)
        contentStack.addArrangedSubview(debugUnlockLabel)

        driverModeControl.addTarget(self, action: #selector(driverModeChanged), for: .valueChanged)
        voiceControl.addTarget(self, action: #selector(voiceChanged), for: .valueChanged)
        promptSensitivityControl.addTarget(self, action: #selector(promptSensitivityChanged), for: .valueChanged)
        unitsControl.addTarget(self, action: #selector(unitsChanged), for: .valueChanged)
        dataSourceControl.addTarget(self, action: #selector(dataSourceChanged), for: .valueChanged)
        hazardsSwitch.addTarget(self, action: #selector(hazardsChanged), for: .valueChanged)
        lowStressRoutingSwitch.addTarget(self, action: #selector(lowStressRoutingChanged), for: .valueChanged)
        analyticsSwitch.addTarget(self, action: #selector(analyticsChanged), for: .valueChanged)
        notificationsSwitch.addTarget(self, action: #selector(notificationsChanged), for: .valueChanged)

        [
            driverModeControl,
            voiceControl,
            promptSensitivityControl,
            unitsControl,
            dataSourceControl
        ].forEach { DrivestBrand.styleSegmentedControl($0) }

        hazardsSwitch.onTintColor = DrivestPalette.accentPrimary
        lowStressRoutingSwitch.onTintColor = DrivestPalette.accentPrimary
        analyticsSwitch.onTintColor = DrivestPalette.accentPrimary
        notificationsSwitch.onTintColor = DrivestPalette.accentPrimary
    }

    private func buildHeroCard() -> UIView {
        let card = UIView()
        card.translatesAutoresizingMaskIntoConstraints = false
        card.backgroundColor = DrivestPalette.heroStart
        card.layer.cornerRadius = 24
        card.layer.borderColor = DrivestPalette.cardStroke.cgColor
        card.layer.borderWidth = 1

        let iconContainer = UIView()
        iconContainer.translatesAutoresizingMaskIntoConstraints = false
        iconContainer.backgroundColor = .white
        iconContainer.layer.cornerRadius = 12
        iconContainer.layer.masksToBounds = true
        iconContainer.layer.borderWidth = 1
        iconContainer.layer.borderColor = UIColor.white.withAlphaComponent(0.35).cgColor

        let icon = DrivestBrand.logoImageView(contentMode: .scaleAspectFit)
        icon.translatesAutoresizingMaskIntoConstraints = false
        icon.backgroundColor = .clear

        let titleLabel = UILabel()
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.text = "Settings"
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 30, weight: .bold)

        let subtitleLabel = UILabel()
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.text = "Fine-tune your drive for confidence, calm, and control."
        subtitleLabel.textColor = UIColor(red: 1.00, green: 0.90, blue: 0.83, alpha: 1)
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .regular)
        subtitleLabel.numberOfLines = 0

        card.addSubview(iconContainer)
        iconContainer.addSubview(icon)
        card.addSubview(titleLabel)
        card.addSubview(subtitleLabel)

        NSLayoutConstraint.activate([
            iconContainer.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            iconContainer.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            iconContainer.bottomAnchor.constraint(lessThanOrEqualTo: card.bottomAnchor, constant: -14),
            iconContainer.widthAnchor.constraint(equalToConstant: 120),
            iconContainer.heightAnchor.constraint(equalToConstant: 64),

            icon.leadingAnchor.constraint(equalTo: iconContainer.leadingAnchor, constant: 8),
            icon.trailingAnchor.constraint(equalTo: iconContainer.trailingAnchor, constant: -8),
            icon.topAnchor.constraint(equalTo: iconContainer.topAnchor, constant: 8),
            icon.bottomAnchor.constraint(equalTo: iconContainer.bottomAnchor, constant: -8),

            titleLabel.leadingAnchor.constraint(equalTo: iconContainer.trailingAnchor, constant: 12),
            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            subtitleLabel.leadingAnchor.constraint(equalTo: iconContainer.trailingAnchor, constant: 12),
            subtitleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),
            subtitleLabel.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])

        return card
    }

    private func buildProfileSection() -> UIView {
        let card = makeCard()
        let stack = makeCardStack(in: card)

        stack.addArrangedSubview(makeSectionLabel("Profile"))
        stack.addArrangedSubview(makeRowTitle("Driver Mode"))
        stack.addArrangedSubview(driverModeControl)

        driverModeDescriptionLabel.translatesAutoresizingMaskIntoConstraints = false
        driverModeDescriptionLabel.numberOfLines = 0
        driverModeDescriptionLabel.font = .systemFont(ofSize: 13, weight: .regular)
        driverModeDescriptionLabel.textColor = DrivestPalette.textPrimary
        driverModeDescriptionLabel.backgroundColor = DrivestPalette.accentPrimarySoft
        driverModeDescriptionLabel.layer.cornerRadius = 8
        driverModeDescriptionLabel.layer.masksToBounds = true
        driverModeDescriptionLabel.textAlignment = .left

        stack.addArrangedSubview(driverModeDescriptionLabel)

        confidenceLabel.translatesAutoresizingMaskIntoConstraints = false
        confidenceLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        confidenceLabel.textColor = DrivestPalette.textPrimary
        stack.addArrangedSubview(confidenceLabel)

        subscriptionLabel.translatesAutoresizingMaskIntoConstraints = false
        subscriptionLabel.font = .systemFont(ofSize: 14, weight: .regular)
        subscriptionLabel.textColor = DrivestPalette.textSecondary
        subscriptionLabel.text = "Subscription: Free (no expiry)"
        stack.addArrangedSubview(subscriptionLabel)

        return card
    }

    private func buildNavigationSection() -> UIView {
        let card = makeCard()
        let stack = makeCardStack(in: card)

        stack.addArrangedSubview(makeSectionLabel("Navigation"))

        stack.addArrangedSubview(makeRowTitle("Voice Guidance"))
        stack.addArrangedSubview(voiceControl)

        stack.addArrangedSubview(makeRowTitle("Prompt Sensitivity"))
        stack.addArrangedSubview(promptSensitivityControl)

        stack.addArrangedSubview(makeRowTitle("Speed and Distance Units"))
        stack.addArrangedSubview(unitsControl)

        stack.addArrangedSubview(makeRowTitle("Data Source Mode"))
        stack.addArrangedSubview(dataSourceControl)

        stack.addArrangedSubview(makeSwitchRow(title: "Hazard prompts", toggle: hazardsSwitch))
        stack.addArrangedSubview(makeSwitchRow(title: "Low-stress routing", toggle: lowStressRoutingSwitch))

        let hint = UILabel()
        hint.translatesAutoresizingMaskIntoConstraints = false
        hint.text = "Changes are saved automatically and apply immediately."
        hint.textColor = .secondaryLabel
        hint.font = .systemFont(ofSize: 12, weight: .regular)
        hint.numberOfLines = 0
        stack.addArrangedSubview(hint)

        return card
    }

    private func buildPrivacySection() -> UIView {
        let card = makeCard()
        let stack = makeCardStack(in: card)

        stack.addArrangedSubview(makeSectionLabel("Privacy"))
        stack.addArrangedSubview(makeSwitchRow(title: "Analytics (optional)", toggle: analyticsSwitch))
        stack.addArrangedSubview(makeSwitchRow(title: "Notifications", toggle: notificationsSwitch))

        notificationsStatusLabel.translatesAutoresizingMaskIntoConstraints = false
        notificationsStatusLabel.font = .systemFont(ofSize: 13, weight: .regular)
        notificationsStatusLabel.textColor = DrivestPalette.textSecondary
        notificationsStatusLabel.text = "Notifications status: Disabled"
        stack.addArrangedSubview(notificationsStatusLabel)

        return card
    }

    private func buildLegalSection() -> UIView {
        let card = makeCard()
        let stack = makeCardStack(in: card)

        stack.addArrangedSubview(makeSectionLabel("Legal"))
        stack.addArrangedSubview(makeLinkButton(title: "Terms and Conditions", url: "https://drivest.uk/terms"))
        stack.addArrangedSubview(makeLinkButton(title: "Privacy Policy", url: "https://drivest.uk/privacy"))
        stack.addArrangedSubview(makeLinkButton(title: "FAQ", url: "https://drivest.uk/faq"))

        stack.addArrangedSubview(makePushButton(title: "Data rights") {
            InfoTextViewController(
                title: "Data rights",
                body: "You can request data export or deletion by contacting support."
            )
        })
        stack.addArrangedSubview(makePushButton(title: "Service availability") {
            InfoTextViewController(
                title: "Service availability",
                body: "Drivest services may vary by area, connectivity, and data-source mode."
            )
        })
        stack.addArrangedSubview(makePushButton(title: "Safety notice") {
            InfoTextViewController(
                title: "Safety notice",
                body: "Drivest provides advisory prompts only. Follow road signs and the Highway Code."
            )
        })
        stack.addArrangedSubview(makePushButton(title: "Content accuracy") {
            InfoTextViewController(
                title: "Content accuracy",
                body: "Map and prompt data may vary. Verify with real-world signage and road conditions."
            )
        })
        stack.addArrangedSubview(makePushButton(title: "Theory settings") {
            TheorySettingsViewController()
        })

        return card
    }

    private func buildSupportSection() -> UIView {
        let card = makeCard()
        let stack = makeCardStack(in: card)

        stack.addArrangedSubview(makeSectionLabel("Support"))
        stack.addArrangedSubview(makeLinkButton(title: "Contact support", url: "mailto:admin@drivest.uk"))
        stack.addArrangedSubview(makePushButton(title: "About Drivest") {
            AboutDrivestViewController()
        })

        let exportButton = UIButton(type: .system)
        var exportConfig = UIButton.Configuration.plain()
        exportConfig.title = "Export drive log"
        exportConfig.baseForegroundColor = DrivestPalette.accentPrimary
        exportConfig.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 0, bottom: 6, trailing: 0)
        exportConfig.titleAlignment = .leading
        exportButton.configuration = exportConfig
        exportButton.contentHorizontalAlignment = .leading
        exportButton.addTarget(self, action: #selector(exportDriveLog), for: .touchUpInside)
        stack.addArrangedSubview(exportButton)

        return card
    }

    private func makeCard() -> UIView {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.applyCardStyle(cornerRadius: 20)
        return view
    }

    private func makeCardStack(in container: UIView) -> UIStackView {
        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 10
        container.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: container.topAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -14),
        ])
        return stack
    }

    private func makeSectionLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = text.uppercased()
        label.textColor = DrivestPalette.textSecondary
        label.font = .systemFont(ofSize: 12, weight: .bold)
        return label
    }

    private func makeRowTitle(_ text: String) -> UILabel {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = text
        label.textColor = DrivestPalette.textPrimary
        label.font = .systemFont(ofSize: 16, weight: .semibold)
        return label
    }

    private func makeSwitchRow(title: String, toggle: UISwitch) -> UIView {
        let row = UIView()
        row.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = title
        label.font = .systemFont(ofSize: 15, weight: .regular)
        label.textColor = DrivestPalette.textPrimary

        toggle.translatesAutoresizingMaskIntoConstraints = false

        row.addSubview(label)
        row.addSubview(toggle)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: row.leadingAnchor),
            label.topAnchor.constraint(equalTo: row.topAnchor, constant: 8),
            label.bottomAnchor.constraint(equalTo: row.bottomAnchor, constant: -8),

            toggle.leadingAnchor.constraint(greaterThanOrEqualTo: label.trailingAnchor, constant: 12),
            toggle.trailingAnchor.constraint(equalTo: row.trailingAnchor),
            toggle.centerYAnchor.constraint(equalTo: label.centerYAnchor),
        ])
        return row
    }

    private func makeLinkButton(title: String, url: String) -> UIButton {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        var configuration = UIButton.Configuration.plain()
        configuration.title = title
        configuration.baseForegroundColor = DrivestPalette.accentPrimary
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 0, bottom: 6, trailing: 0)
        configuration.titleAlignment = .leading
        button.configuration = configuration
        button.contentHorizontalAlignment = .leading
        button.addAction(UIAction { _ in
            guard let targetURL = URL(string: url) else { return }
            UIApplication.shared.open(targetURL)
        }, for: .touchUpInside)
        return button
    }

    private func makePushButton(title: String, build: @escaping () -> UIViewController) -> UIButton {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        var configuration = UIButton.Configuration.plain()
        configuration.title = title
        configuration.baseForegroundColor = DrivestPalette.accentPrimary
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 0, bottom: 6, trailing: 0)
        configuration.titleAlignment = .leading
        button.configuration = configuration
        button.contentHorizontalAlignment = .leading
        button.addAction(UIAction { [weak self] _ in
            self?.navigationController?.pushViewController(build(), animated: true)
        }, for: .touchUpInside)
        return button
    }

    private func render() {
        driverModeControl.selectedSegmentIndex = switch settingsStore.driverMode {
        case .learner: 0
        case .newDriver: 1
        case .standard: 2
        }

        voiceControl.selectedSegmentIndex = switch settingsStore.voiceMode {
        case .all: 0
        case .alerts: 1
        case .mute: 2
        }

        promptSensitivityControl.selectedSegmentIndex = switch settingsStore.promptSensitivity {
        case .minimal: 0
        case .standard: 1
        case .extraHelp: 2
        }

        unitsControl.selectedSegmentIndex = switch settingsStore.unitsMode {
        case .ukMph: 0
        case .metricKmh: 1
        }

        dataSourceControl.selectedSegmentIndex = switch settingsStore.dataSourceMode {
        case .backendThenCacheThenAssets: 0
        case .backendOnly: 1
        case .assetsOnly: 2
        }

        hazardsSwitch.isOn = settingsStore.hazardsEnabled
        lowStressRoutingSwitch.isOn = settingsStore.lowStressRoutingEnabled
        analyticsSwitch.isOn = settingsStore.analyticsConsent
        notificationsSwitch.isOn = settingsStore.notificationsPreference

        confidenceLabel.text = "Driver confidence: \(confidenceForMode(settingsStore.driverMode))/100"
        driverModeDescriptionLabel.text = modeDescription(settingsStore.driverMode)
        notificationsStatusLabel.text = "Notifications status: \(settingsStore.notificationsPreference ? "Enabled" : "Disabled")"
    }

    @objc
    private func driverModeChanged() {
        let selectedMode: DriverMode = switch driverModeControl.selectedSegmentIndex {
        case 1: .newDriver
        case 2: .standard
        default: .learner
        }
        settingsStore.applyDriverProfilePreset(selectedMode)
        render()
    }

    @objc
    private func voiceChanged() {
        settingsStore.voiceMode = switch voiceControl.selectedSegmentIndex {
        case 1: .alerts
        case 2: .mute
        default: .all
        }
    }

    @objc
    private func promptSensitivityChanged() {
        settingsStore.promptSensitivity = switch promptSensitivityControl.selectedSegmentIndex {
        case 0: .minimal
        case 2: .extraHelp
        default: .standard
        }
    }

    @objc
    private func unitsChanged() {
        settingsStore.unitsMode = unitsControl.selectedSegmentIndex == 1 ? .metricKmh : .ukMph
    }

    @objc
    private func dataSourceChanged() {
        settingsStore.dataSourceMode = switch dataSourceControl.selectedSegmentIndex {
        case 1: .backendOnly
        case 2: .assetsOnly
        default: .backendThenCacheThenAssets
        }
    }

    @objc
    private func hazardsChanged() {
        settingsStore.hazardsEnabled = hazardsSwitch.isOn
    }

    @objc
    private func lowStressRoutingChanged() {
        settingsStore.lowStressRoutingEnabled = lowStressRoutingSwitch.isOn
    }

    @objc
    private func analyticsChanged() {
        settingsStore.analyticsConsent = analyticsSwitch.isOn
    }

    @objc
    private func notificationsChanged() {
        settingsStore.notificationsPreference = notificationsSwitch.isOn
        notificationsStatusLabel.text = "Notifications status: \(notificationsSwitch.isOn ? "Enabled" : "Disabled")"
    }

    @objc
    private func exportDriveLog() {
        let payload: [String: Any] = [
            "app": [
                "platform": "iOS",
                "timestamp": ISO8601DateFormatter().string(from: Date())
            ],
            "settings": [
                "voiceMode": settingsStore.voiceMode.rawValue,
                "promptSensitivity": settingsStore.promptSensitivity.rawValue,
                "units": settingsStore.unitsMode.rawValue,
                "hazardsEnabled": settingsStore.hazardsEnabled,
                "lowStressRoutingEnabled": settingsStore.lowStressRoutingEnabled,
                "analyticsConsent": settingsStore.analyticsConsent,
                "notificationsPreference": settingsStore.notificationsPreference,
                "dataSourceMode": settingsStore.dataSourceMode.rawValue,
                "lastCentreId": settingsStore.lastCentreId,
                "lastMode": settingsStore.lastMode.rawValue,
                "driverMode": settingsStore.driverMode.rawValue
            ],
            "parity": [
                "routesPackVersionId": DebugParityStateStore.shared.routesPackVersionId,
                "hazardsPackVersionId": DebugParityStateStore.shared.hazardsPackVersionId,
                "lastPromptFired": DebugParityStateStore.shared.lastPromptFired,
                "lastPromptSuppressed": DebugParityStateStore.shared.lastPromptSuppressed,
                "lastOffRouteState": DebugParityStateStore.shared.lastOffRouteState,
                "offRouteRawDistanceMeters": DebugParityStateStore.shared.offRouteRawDistanceMeters,
                "offRouteSmoothedDistanceMeters": DebugParityStateStore.shared.offRouteSmoothedDistanceMeters
            ]
        ]

        guard
            let data = try? JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted]),
            let jsonText = String(data: data, encoding: .utf8)
        else {
            return
        }

        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("drivest-ios-drive-log.json")
        try? jsonText.data(using: .utf8)?.write(to: tempURL)

        let sheet = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
        if let popover = sheet.popoverPresentationController {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
        }
        present(sheet, animated: true)
    }

    @objc
    private func openHiddenDebug() {
        navigationController?.pushViewController(DebugParityViewController(), animated: true)
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

    private func modeDescription(_ mode: DriverMode) -> String {
        switch mode {
        case .learner:
            return "Learner mode prioritises extra guidance and practice-focused setup."
        case .newDriver:
            return "New Driver mode keeps hazards on and enables a steadier low-stress driving setup."
        case .standard:
            return "Standard mode reduces prompt intensity for confident day-to-day driving."
        }
    }
}
