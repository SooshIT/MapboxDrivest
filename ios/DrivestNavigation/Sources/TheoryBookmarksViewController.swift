import UIKit

final class TheoryBookmarksViewController: UIViewController {
    private let pack = TheoryPackLoader.shared.load()
    private let progressStore = TheoryProgressStore.shared

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let summaryLabel = UILabel()
    private let listStack = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Bookmarks"
        view.backgroundColor = .clear
        setupLayout()
        render()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        render()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    private func setupLayout() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 10

        listStack.axis = .vertical
        listStack.spacing = 6

        summaryLabel.font = .systemFont(ofSize: 14, weight: .medium)
        summaryLabel.textColor = DrivestPalette.textSecondary

        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20)
        ])

        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let startQuizButton = UIButton(type: .system)
        var config = UIButton.Configuration.filled()
        config.title = "Start bookmarked quiz"
        startQuizButton.configuration = config
        DrivestBrand.stylePrimaryButton(startQuizButton)
        startQuizButton.addAction(UIAction { [weak self] _ in
            self?.navigationController?.pushViewController(
                TheoryQuizViewController(mode: .bookmarks, topicId: nil, questionCount: 30),
                animated: true
            )
        }, for: .touchUpInside)

        let cardStack = UIStackView(arrangedSubviews: [summaryLabel, listStack, startQuizButton])
        cardStack.translatesAutoresizingMaskIntoConstraints = false
        cardStack.axis = .vertical
        cardStack.spacing = 10

        card.addSubview(cardStack)
        NSLayoutConstraint.activate([
            cardStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            cardStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            cardStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            cardStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            startQuizButton.heightAnchor.constraint(equalToConstant: 48)
        ])

        contentStack.addArrangedSubview(card)
    }

    private func render() {
        let bookmarks = progressStore.progress.bookmarks
        let questions = pack.questions.filter { bookmarks.contains($0.id) }

        summaryLabel.text = "Bookmarked questions: \(bookmarks.count)"

        listStack.arrangedSubviews.forEach {
            listStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        if questions.isEmpty {
            let empty = UILabel()
            empty.text = "No bookmarked questions yet."
            empty.font = .systemFont(ofSize: 14, weight: .regular)
            empty.textColor = DrivestPalette.textSecondary
            listStack.addArrangedSubview(empty)
            return
        }

        for question in questions.prefix(30) {
            let label = UILabel()
            label.text = "- \(question.prompt)"
            label.numberOfLines = 0
            label.font = .systemFont(ofSize: 14, weight: .regular)
            label.textColor = DrivestPalette.textSecondary
            listStack.addArrangedSubview(label)
        }
    }
}
