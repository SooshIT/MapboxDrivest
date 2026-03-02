import UIKit

final class DebugParityViewController: UIViewController {
    private let textView = UITextView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Parity Debug"
        view.backgroundColor = DrivestTheme.Colors.background
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
        textView.font = DrivestTheme.Fonts.monospace
        textView.backgroundColor = DrivestTheme.Colors.surface
        textView.layer.cornerRadius = DrivestTheme.CornerRadius.md
        textView.textContainerInset = UIEdgeInsets(
            top: DrivestTheme.Spacing.sm,
            left: DrivestTheme.Spacing.sm,
            bottom: DrivestTheme.Spacing.sm,
            right: DrivestTheme.Spacing.sm
        )

        view.addSubview(textView)
        NSLayoutConstraint.activate([
            textView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: DrivestTheme.Spacing.sm),
            textView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: DrivestTheme.Spacing.sm),
            textView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -DrivestTheme.Spacing.sm),
            textView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -DrivestTheme.Spacing.sm),
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
