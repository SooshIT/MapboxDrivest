import UIKit

final class TheoryTopicDetailViewController: UIViewController {
    private let topicId: String
    private let pack = TheoryPackLoader.shared.load()

    init(topicId: String) {
        self.topicId = topicId
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        guard let topic = pack.topics.first(where: { $0.id == topicId }) else {
            title = "Topic"
            return
        }

        title = topic.title

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

        let titleLabel = UILabel()
        titleLabel.text = topic.title
        titleLabel.font = .systemFont(ofSize: 28, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        let descriptionLabel = UILabel()
        descriptionLabel.text = topic.description
        descriptionLabel.font = .systemFont(ofSize: 14, weight: .regular)
        descriptionLabel.textColor = DrivestPalette.textSecondary
        descriptionLabel.numberOfLines = 0

        let lessonsHeading = UILabel()
        lessonsHeading.text = "Lessons"
        lessonsHeading.font = .systemFont(ofSize: 18, weight: .bold)
        lessonsHeading.textColor = DrivestPalette.textPrimary

        let quizHeading = UILabel()
        quizHeading.text = "Quiz length"
        quizHeading.font = .systemFont(ofSize: 18, weight: .bold)
        quizHeading.textColor = DrivestPalette.textPrimary

        let content = UIStackView()
        content.translatesAutoresizingMaskIntoConstraints = false
        content.axis = .vertical
        content.spacing = 8
        content.addArrangedSubview(titleLabel)
        content.addArrangedSubview(descriptionLabel)
        content.addArrangedSubview(lessonsHeading)

        let lessons = pack.lessons.filter { $0.topicId == topic.id }
        for lesson in lessons {
            let button = UIButton(type: .system)
            var config = UIButton.Configuration.bordered()
            config.title = lesson.title
            button.configuration = config
            DrivestBrand.styleOutlinedButton(button)
            button.addAction(UIAction { [weak self] _ in
                self?.navigationController?.pushViewController(TheoryLessonViewController(lessonId: lesson.id), animated: true)
            }, for: .touchUpInside)
            content.addArrangedSubview(button)
            button.heightAnchor.constraint(equalToConstant: 48).isActive = true
        }

        content.addArrangedSubview(quizHeading)
        content.addArrangedSubview(makeQuizButton(title: "Start 10 question quiz", count: 10, topicId: topic.id))
        content.addArrangedSubview(makeQuizButton(title: "Start 20 question quiz", count: 20, topicId: topic.id))
        content.addArrangedSubview(makeQuizButton(title: "Start 30 question quiz", count: 30, topicId: topic.id))

        card.addSubview(content)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            content.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            content.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            content.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])

        stack.addArrangedSubview(card)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    private func makeQuizButton(title: String, count: Int, topicId: String) -> UIButton {
        let button = UIButton(type: .system)
        var config: UIButton.Configuration = count == 10 ? .filled() : .bordered()
        config.title = title
        button.configuration = config
        if count == 10 {
            DrivestBrand.stylePrimaryButton(button)
        } else {
            DrivestBrand.styleOutlinedButton(button)
        }
        button.heightAnchor.constraint(equalToConstant: 48).isActive = true
        button.addAction(UIAction { [weak self] _ in
            self?.navigationController?.pushViewController(
                TheoryQuizViewController(mode: .topic, topicId: topicId, questionCount: count),
                animated: true
            )
        }, for: .touchUpInside)
        return button
    }
}
