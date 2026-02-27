import UIKit

final class TheorySettingsViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Theory settings"
        view.backgroundColor = .clear

        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 12

        view.addSubview(scroll)
        scroll.addSubview(stack)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -20)
        ])

        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let titleLabel = UILabel()
        titleLabel.text = "Theory"
        titleLabel.font = .systemFont(ofSize: 24, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        let disclaimerHeading = UILabel()
        disclaimerHeading.text = "Disclaimer"
        disclaimerHeading.font = .systemFont(ofSize: 17, weight: .bold)
        disclaimerHeading.textColor = DrivestPalette.textPrimary

        let disclaimerText = UILabel()
        disclaimerText.text = "Theory content is for practice support. Always follow official DVSA materials and the Highway Code."
        disclaimerText.font = .systemFont(ofSize: 14, weight: .regular)
        disclaimerText.textColor = DrivestPalette.textSecondary
        disclaimerText.numberOfLines = 0

        let attributionHeading = UILabel()
        attributionHeading.text = "Attribution"
        attributionHeading.font = .systemFont(ofSize: 17, weight: .bold)
        attributionHeading.textColor = DrivestPalette.textPrimary

        let attributionText = UILabel()
        attributionText.text = "Drivest theory module uses structured in-app content packs for learner revision and confidence building."
        attributionText.font = .systemFont(ofSize: 14, weight: .regular)
        attributionText.textColor = DrivestPalette.textSecondary
        attributionText.numberOfLines = 0

        let cardStack = UIStackView(arrangedSubviews: [
            titleLabel,
            disclaimerHeading,
            disclaimerText,
            attributionHeading,
            attributionText
        ])
        cardStack.translatesAutoresizingMaskIntoConstraints = false
        cardStack.axis = .vertical
        cardStack.spacing = 8

        card.addSubview(cardStack)
        NSLayoutConstraint.activate([
            cardStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            cardStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            cardStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            cardStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])

        stack.addArrangedSubview(card)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}
