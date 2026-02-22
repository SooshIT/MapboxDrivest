import UIKit

final class SettingsViewController: UIViewController {
    private let settingsStore = SettingsStore.shared

    private let voiceControl = UISegmentedControl(items: ["All", "Alerts", "Mute"])
    private let unitsControl = UISegmentedControl(items: ["mph", "km/h"])
    private let debugUnlockLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Settings"
        view.backgroundColor = .systemBackground
        setupLayout()
        render()
    }

    private func setupLayout() {
        let voiceLabel = UILabel()
        voiceLabel.translatesAutoresizingMaskIntoConstraints = false
        voiceLabel.text = "Voice guidance"
        voiceLabel.font = .systemFont(ofSize: 17, weight: .semibold)

        voiceControl.translatesAutoresizingMaskIntoConstraints = false
        voiceControl.addTarget(self, action: #selector(voiceChanged), for: .valueChanged)

        let unitsLabel = UILabel()
        unitsLabel.translatesAutoresizingMaskIntoConstraints = false
        unitsLabel.text = "Units"
        unitsLabel.font = .systemFont(ofSize: 17, weight: .semibold)

        unitsControl.translatesAutoresizingMaskIntoConstraints = false
        unitsControl.addTarget(self, action: #selector(unitsChanged), for: .valueChanged)

        debugUnlockLabel.translatesAutoresizingMaskIntoConstraints = false
        debugUnlockLabel.text = "Drivest iOS"
        debugUnlockLabel.textColor = .tertiaryLabel
        debugUnlockLabel.font = .systemFont(ofSize: 12, weight: .regular)
        debugUnlockLabel.isUserInteractionEnabled = true
        let debugTapGesture = UITapGestureRecognizer(target: self, action: #selector(openHiddenDebug))
        debugTapGesture.numberOfTapsRequired = 5
        debugUnlockLabel.addGestureRecognizer(debugTapGesture)

        view.addSubview(voiceLabel)
        view.addSubview(voiceControl)
        view.addSubview(unitsLabel)
        view.addSubview(unitsControl)
        view.addSubview(debugUnlockLabel)

        NSLayoutConstraint.activate([
            voiceLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            voiceLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            voiceLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            voiceControl.topAnchor.constraint(equalTo: voiceLabel.bottomAnchor, constant: 10),
            voiceControl.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            voiceControl.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            unitsLabel.topAnchor.constraint(equalTo: voiceControl.bottomAnchor, constant: 28),
            unitsLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            unitsLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            unitsControl.topAnchor.constraint(equalTo: unitsLabel.bottomAnchor, constant: 10),
            unitsControl.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            unitsControl.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            debugUnlockLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            debugUnlockLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -10),
        ])
    }

    private func render() {
        voiceControl.selectedSegmentIndex = switch settingsStore.voiceMode {
        case .all: 0
        case .alerts: 1
        case .mute: 2
        }

        unitsControl.selectedSegmentIndex = switch settingsStore.unitsMode {
        case .ukMph: 0
        case .metricKmh: 1
        }
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
    private func unitsChanged() {
        settingsStore.unitsMode = unitsControl.selectedSegmentIndex == 1 ? .metricKmh : .ukMph
    }

    @objc
    private func openHiddenDebug() {
        navigationController?.pushViewController(DebugParityViewController(), animated: true)
    }
}
