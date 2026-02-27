import UIKit

final class InfoTextViewController: UIViewController {
    private let screenTitle: String
    private let bodyText: String

    init(title: String, body: String) {
        self.screenTitle = title
        self.bodyText = body
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = screenTitle
        view.backgroundColor = .clear

        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        let card = UIView()
        card.translatesAutoresizingMaskIntoConstraints = false
        card.applyCardStyle(cornerRadius: 18)

        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = bodyText
        label.font = .systemFont(ofSize: 15, weight: .regular)
        label.textColor = DrivestPalette.textSecondary
        label.numberOfLines = 0

        view.addSubview(scroll)
        scroll.addSubview(card)
        card.addSubview(label)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            card.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            card.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -16),
            card.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -20),

            label.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            label.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            label.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            label.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}

final class AboutDrivestViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "About Drivest"
        view.backgroundColor = .clear

        let text = "Drivest is a UK focused navigation app designed for learners and new drivers. It provides structured practice routes, off-route alerts, and low-stress navigation modes.\n\nDrivest provides advisory prompts only. Follow road signs and the Highway Code at all times.\n\nDrivest is independent and not affiliated with DVSA.\n\nContact: admin@drivest.uk"

        let child = InfoTextViewController(title: "About Drivest", body: text)
        addChild(child)
        child.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(child.view)
        child.didMove(toParent: self)

        NSLayoutConstraint.activate([
            child.view.topAnchor.constraint(equalTo: view.topAnchor),
            child.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            child.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            child.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
}
