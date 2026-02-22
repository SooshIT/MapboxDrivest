import UIKit

final class HomeViewController: UIViewController {
    private let settingsStore = SettingsStore.shared

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Drivest"
        view.backgroundColor = .systemBackground
        setupLayout()
    }

    private func setupLayout() {
        let subtitle = UILabel()
        subtitle.translatesAutoresizingMaskIntoConstraints = false
        subtitle.text = "Choose your mode to start"
        subtitle.textAlignment = .center
        subtitle.textColor = .secondaryLabel

        let practiceButton = UIButton(type: .system)
        practiceButton.translatesAutoresizingMaskIntoConstraints = false
        practiceButton.setTitle("Practice", for: .normal)
        practiceButton.titleLabel?.font = .systemFont(ofSize: 20, weight: .semibold)
        practiceButton.configuration = .filled()
        practiceButton.addTarget(self, action: #selector(openPractice), for: .touchUpInside)

        let navigationButton = UIButton(type: .system)
        navigationButton.translatesAutoresizingMaskIntoConstraints = false
        navigationButton.setTitle("Navigation", for: .normal)
        navigationButton.titleLabel?.font = .systemFont(ofSize: 20, weight: .semibold)
        navigationButton.configuration = .bordered()
        navigationButton.addTarget(self, action: #selector(openNavigation), for: .touchUpInside)

        let settingsButton = UIBarButtonItem(
            title: "Settings",
            style: .plain,
            target: self,
            action: #selector(openSettings)
        )
        navigationItem.rightBarButtonItem = settingsButton

        view.addSubview(subtitle)
        view.addSubview(practiceButton)
        view.addSubview(navigationButton)

        NSLayoutConstraint.activate([
            subtitle.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            subtitle.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            subtitle.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            practiceButton.topAnchor.constraint(equalTo: subtitle.bottomAnchor, constant: 32),
            practiceButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            practiceButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            practiceButton.heightAnchor.constraint(equalToConstant: 54),

            navigationButton.topAnchor.constraint(equalTo: practiceButton.bottomAnchor, constant: 14),
            navigationButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            navigationButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            navigationButton.heightAnchor.constraint(equalToConstant: 54),
        ])
    }

    @objc
    private func openPractice() {
        settingsStore.lastMode = .practice
        navigationController?.pushViewController(CentrePickerViewController(), animated: true)
    }

    @objc
    private func openNavigation() {
        settingsStore.lastMode = .navigation
        navigationController?.pushViewController(DestinationSearchViewController(), animated: true)
    }

    @objc
    private func openSettings() {
        navigationController?.pushViewController(SettingsViewController(), animated: true)
    }
}
