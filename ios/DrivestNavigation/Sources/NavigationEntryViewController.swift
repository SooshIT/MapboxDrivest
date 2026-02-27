import UIKit

final class NavigationEntryViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Navigation Mode"
        view.backgroundColor = .clear
        setupLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    private func setupLayout() {
        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 10
        stack.alignment = .fill

        let logoView = DrivestBrand.logoImageView()

        let titleLabel = UILabel()
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.text = "Navigation Mode"
        titleLabel.font = .systemFont(ofSize: 28, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary
        titleLabel.textAlignment = .center

        let descriptionLabel = UILabel()
        descriptionLabel.translatesAutoresizingMaskIntoConstraints = false
        descriptionLabel.text = "Plan and run point-to-point guidance with full navigation UI."
        descriptionLabel.font = .systemFont(ofSize: 15, weight: .regular)
        descriptionLabel.textColor = DrivestPalette.textSecondary
        descriptionLabel.textAlignment = .center
        descriptionLabel.numberOfLines = 0

        var config = UIButton.Configuration.filled()
        config.title = "Search Destination"
        config.cornerStyle = .large
        config.baseBackgroundColor = DrivestPalette.accentPrimary
        config.baseForegroundColor = .white

        let openButton = UIButton(type: .system)
        openButton.translatesAutoresizingMaskIntoConstraints = false
        openButton.configuration = config
        DrivestBrand.stylePrimaryButton(openButton)
        openButton.addTarget(self, action: #selector(openDestinationSearch), for: .touchUpInside)

        stack.addArrangedSubview(logoView)
        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(descriptionLabel)
        stack.addArrangedSubview(UIView())
        stack.addArrangedSubview(openButton)

        view.addSubview(stack)

        NSLayoutConstraint.activate([
            logoView.heightAnchor.constraint(equalToConstant: 92),
            openButton.heightAnchor.constraint(equalToConstant: 52),

            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
        ])
    }

    @objc
    private func openDestinationSearch() {
        print("[Drivest iOS] navigation_open_tapped")
        navigationController?.pushViewController(DestinationSearchViewController(), animated: true)
    }
}
