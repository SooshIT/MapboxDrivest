import UIKit

final class DebugParityViewController: UIViewController {
    private let textView = UITextView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Parity Debug"
        view.backgroundColor = .systemBackground
        setupLayout()
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "Refresh",
            style: .plain,
            target: self,
            action: #selector(refreshSnapshot)
        )
        refreshSnapshot()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshSnapshot()
    }

    private func setupLayout() {
        textView.translatesAutoresizingMaskIntoConstraints = false
        textView.isEditable = false
        textView.isSelectable = true
        textView.alwaysBounceVertical = true
        textView.font = UIFont.monospacedSystemFont(ofSize: 13, weight: .regular)
        textView.backgroundColor = .secondarySystemBackground
        textView.layer.cornerRadius = 10
        textView.textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)

        view.addSubview(textView)
        NSLayoutConstraint.activate([
            textView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            textView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            textView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            textView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
        ])
    }

    @objc
    private func refreshSnapshot() {
        let snapshot = DebugParityStateStore.shared.snapshot()
        textView.text = """
        dataSourceMode: \(snapshot.dataSourceMode)
        voiceMode: \(snapshot.voiceMode)
        promptSensitivity: \(snapshot.promptSensitivity)
        hazardsEnabled: \(snapshot.hazardsEnabled)
        analyticsConsent: \(snapshot.analyticsConsent)
        safetyAcknowledged: \(snapshot.safetyAcknowledged)

        routesPackVersionId: \(snapshot.routesPackVersionId)
        hazardsPackVersionId: \(snapshot.hazardsPackVersionId)

        lastPromptFired: \(snapshot.lastPromptFired)
        lastPromptSuppressed: \(snapshot.lastPromptSuppressed)

        lastOffRouteState: \(snapshot.lastOffRouteState)
        offRouteRawDistanceM: \(String(format: "%.2f", snapshot.offRouteRawDistanceMeters))
        offRouteSmoothedDistanceM: \(String(format: "%.2f", snapshot.offRouteSmoothedDistanceMeters))

        observerAttachCount: \(snapshot.observerAttachCount)
        observerDetachCount: \(snapshot.observerDetachCount)
        activeObserverAttachCount: \(snapshot.activeObserverAttachCount)
        """
    }
}
