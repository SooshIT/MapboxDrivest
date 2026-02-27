import UIKit

final class TheoryLessonViewController: UIViewController {
    private let lessonId: String
    private let pack = TheoryPackLoader.shared.load()
    private let progressStore = TheoryProgressStore.shared

    init(lessonId: String) {
        self.lessonId = lessonId
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        guard let lesson = pack.lessons.first(where: { $0.id == lessonId }) else {
            title = "Lesson"
            return
        }

        title = lesson.title

        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 10

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

        let cardStack = UIStackView()
        cardStack.translatesAutoresizingMaskIntoConstraints = false
        cardStack.axis = .vertical
        cardStack.spacing = 10

        let titleLabel = UILabel()
        titleLabel.text = lesson.title
        titleLabel.font = .systemFont(ofSize: 24, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        let contentLabel = UILabel()
        contentLabel.text = lesson.content
        contentLabel.font = .systemFont(ofSize: 15, weight: .regular)
        contentLabel.textColor = DrivestPalette.textSecondary
        contentLabel.numberOfLines = 0

        let keyPointsHeading = UILabel()
        keyPointsHeading.text = "Key points"
        keyPointsHeading.font = .systemFont(ofSize: 18, weight: .bold)
        keyPointsHeading.textColor = DrivestPalette.textPrimary

        let keyPointsLabel = UILabel()
        keyPointsLabel.text = lesson.keyPoints.map { "- \($0)" }.joined(separator: "\n")
        keyPointsLabel.font = .systemFont(ofSize: 14, weight: .regular)
        keyPointsLabel.textColor = DrivestPalette.textSecondary
        keyPointsLabel.numberOfLines = 0

        let completeButton = UIButton(type: .system)
        var config = UIButton.Configuration.filled()
        config.title = "Mark lesson complete"
        completeButton.configuration = config
        DrivestBrand.stylePrimaryButton(completeButton)
        completeButton.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.progressStore.markLessonCompleted(lesson.id)
            self.navigationController?.popViewController(animated: true)
        }, for: .touchUpInside)

        cardStack.addArrangedSubview(titleLabel)
        cardStack.addArrangedSubview(contentLabel)
        cardStack.addArrangedSubview(keyPointsHeading)
        cardStack.addArrangedSubview(keyPointsLabel)
        cardStack.addArrangedSubview(completeButton)

        card.addSubview(cardStack)
        NSLayoutConstraint.activate([
            cardStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            cardStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            cardStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            cardStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            completeButton.heightAnchor.constraint(equalToConstant: 50)
        ])

        stack.addArrangedSubview(card)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}
