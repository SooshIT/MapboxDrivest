import UIKit

final class TheoryHomePlaceholderViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Theory"
        view.backgroundColor = .systemGroupedBackground

        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 12

        let icon = UIImageView(image: UIImage(systemName: "book.fill"))
        icon.translatesAutoresizingMaskIntoConstraints = false
        icon.tintColor = UIColor(red: 0.11, green: 0.31, blue: 0.85, alpha: 1)
        icon.contentMode = .scaleAspectFit

        let titleLabel = UILabel()
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.text = "Theory"
        titleLabel.font = .systemFont(ofSize: 30, weight: .bold)
        titleLabel.textAlignment = .center

        let descriptionLabel = UILabel()
        descriptionLabel.translatesAutoresizingMaskIntoConstraints = false
        descriptionLabel.text = "Theory module parity is next in iOS migration."
        descriptionLabel.textColor = .secondaryLabel
        descriptionLabel.textAlignment = .center
        descriptionLabel.numberOfLines = 0

        stack.addArrangedSubview(icon)
        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(descriptionLabel)

        view.addSubview(stack)

        NSLayoutConstraint.activate([
            icon.heightAnchor.constraint(equalToConstant: 72),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
}
