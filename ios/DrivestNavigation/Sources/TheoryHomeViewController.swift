import UIKit

final class TheoryHomeViewController: UIViewController {
    private let packLoader = TheoryPackLoader.shared
    private let progressStore = TheoryProgressStore.shared

    private var pack: TheoryPack = .empty()

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let progressValueLabel = UILabel()
    private let readinessValueLabel = UILabel()
    private let weakestTopicsValueLabel = UILabel()
    private let recommendationsCard = UIView()
    private let recommendationsContainer = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Theory"
        view.backgroundColor = .clear
        pack = packLoader.load()
        setupLayout()
        render()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        render()
    }

    private func setupLayout() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 12

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

        contentStack.addArrangedSubview(buildTopPanel())
        contentStack.addArrangedSubview(buildWeakestTopicsCard())
        contentStack.addArrangedSubview(buildRecommendationsCard())
        contentStack.addArrangedSubview(buildQuickStartCard())
        contentStack.addArrangedSubview(buildActionsCard())
    }

    private func buildTopPanel() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 20)

        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 8

        let titleLabel = UILabel()
        titleLabel.text = "Theory"
        titleLabel.font = .systemFont(ofSize: 28, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        let subtitleLabel = UILabel()
        subtitleLabel.text = "Structured learning for learner and new drivers."
        subtitleLabel.font = .systemFont(ofSize: 14, weight: .regular)
        subtitleLabel.textColor = DrivestPalette.textSecondary
        subtitleLabel.numberOfLines = 0

        progressValueLabel.font = .systemFont(ofSize: 13, weight: .bold)
        progressValueLabel.textColor = DrivestPalette.accentChipText
        progressValueLabel.backgroundColor = DrivestPalette.accentPrimarySoft
        progressValueLabel.layer.cornerRadius = 8
        progressValueLabel.layer.masksToBounds = true
        progressValueLabel.textAlignment = .center

        readinessValueLabel.font = .systemFont(ofSize: 15, weight: .bold)
        readinessValueLabel.textColor = DrivestPalette.accentPrimary

        let continueButton = UIButton(type: .system)
        var config = UIButton.Configuration.filled()
        config.title = "Continue learning"
        continueButton.configuration = config
        continueButton.addTarget(self, action: #selector(openContinueLesson), for: .touchUpInside)
        DrivestBrand.stylePrimaryButton(continueButton)

        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(subtitleLabel)
        stack.addArrangedSubview(progressValueLabel)
        stack.addArrangedSubview(readinessValueLabel)
        stack.addArrangedSubview(continueButton)

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            continueButton.heightAnchor.constraint(equalToConstant: 50),
            progressValueLabel.heightAnchor.constraint(equalToConstant: 30)
        ])

        return card
    }

    private func buildWeakestTopicsCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let heading = makeCardHeading("Weakest topics")
        weakestTopicsValueLabel.font = .systemFont(ofSize: 14, weight: .regular)
        weakestTopicsValueLabel.textColor = DrivestPalette.textSecondary
        weakestTopicsValueLabel.numberOfLines = 0

        let stack = UIStackView(arrangedSubviews: [heading, weakestTopicsValueLabel])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 6

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func buildRecommendationsCard() -> UIView {
        recommendationsCard.applyCardStyle(cornerRadius: 18)
        recommendationsContainer.translatesAutoresizingMaskIntoConstraints = false
        recommendationsContainer.axis = .vertical
        recommendationsContainer.spacing = 8

        let heading = makeCardHeading("Route recommendations")
        let stack = UIStackView(arrangedSubviews: [heading, recommendationsContainer])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 8

        recommendationsCard.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: recommendationsCard.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: recommendationsCard.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: recommendationsCard.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: recommendationsCard.bottomAnchor, constant: -16)
        ])
        recommendationsCard.isHidden = true
        return recommendationsCard
    }

    private func buildQuickStartCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let heading = makeCardHeading("Quick start")

        let quickQuizButton = UIButton(type: .system)
        var quickConfig = UIButton.Configuration.filled()
        quickConfig.title = "Quick topic quiz"
        quickQuizButton.configuration = quickConfig
        DrivestBrand.stylePrimaryButton(quickQuizButton)
        quickQuizButton.addTarget(self, action: #selector(startQuickQuiz), for: .touchUpInside)

        let mockButton = UIButton(type: .system)
        var mockConfig = UIButton.Configuration.bordered()
        mockConfig.title = "Mock test"
        mockButton.configuration = mockConfig
        DrivestBrand.styleOutlinedButton(mockButton)
        mockButton.addTarget(self, action: #selector(startMockQuiz), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [heading, quickQuizButton, mockButton])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 10

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            quickQuizButton.heightAnchor.constraint(equalToConstant: 48),
            mockButton.heightAnchor.constraint(equalToConstant: 48)
        ])
        return card
    }

    private func buildActionsCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let topics = makeActionButton(title: "All topics", action: #selector(openTopics))
        let bookmarks = makeActionButton(title: "Bookmarks", action: #selector(openBookmarks))
        let wrongAnswers = makeActionButton(title: "Wrong answers", action: #selector(openWrongAnswers))
        let settings = makeActionButton(title: "Theory settings", action: #selector(openTheorySettings))

        let stack = UIStackView(arrangedSubviews: [topics, bookmarks, wrongAnswers, settings])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 8

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            topics.heightAnchor.constraint(equalToConstant: 48),
            bookmarks.heightAnchor.constraint(equalToConstant: 48),
            wrongAnswers.heightAnchor.constraint(equalToConstant: 48),
            settings.heightAnchor.constraint(equalToConstant: 48)
        ])
        return card
    }

    private func makeActionButton(title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        var config = UIButton.Configuration.bordered()
        config.title = title
        button.configuration = config
        DrivestBrand.styleOutlinedButton(button)
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }

    private func makeCardHeading(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 18, weight: .bold)
        label.textColor = DrivestPalette.textPrimary
        return label
    }

    private func render() {
        let progress = progressStore.progress
        let readiness = TheoryReadinessCalculator.calculate(progress: progress, totalTopics: pack.topics.count)
        progressValueLabel.text = "Progress: \(readiness.masteredTopicsPercent)%"
        readinessValueLabel.text = "Readiness: \(readinessLabel(readiness.label))"

        let weakestTopics = progress.topicStats
            .sorted { $0.value.masteryPercent < $1.value.masteryPercent }
            .prefix(3)
            .compactMap { stat in
                pack.topics.first { $0.id == stat.key }?.title
            }

        weakestTopicsValueLabel.text = weakestTopics.isEmpty
            ? "Keep practicing to reveal your weakest topics."
            : weakestTopics.map { "- \($0)" }.joined(separator: "\n")

        recommendationsContainer.arrangedSubviews.forEach { view in
            recommendationsContainer.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        if let snapshot = progress.lastRouteTagSnapshot {
            let topicIds = TheoryRouteTagMapper.mapTags(snapshot.tags)
            var count = 0
            for topicId in topicIds {
                guard let topic = pack.topics.first(where: { $0.id == topicId }) else { continue }
                let button = UIButton(type: .system)
                var config = UIButton.Configuration.bordered()
                config.title = "Revise \(topic.title)"
                button.configuration = config
                DrivestBrand.styleOutlinedButton(button)
                button.addAction(UIAction { [weak self] _ in
                    self?.navigationController?.pushViewController(
                        TheoryTopicDetailViewController(topicId: topic.id),
                        animated: true
                    )
                }, for: .touchUpInside)
                recommendationsContainer.addArrangedSubview(button)
                count += 1
            }
            recommendationsCard.isHidden = count == 0
        } else {
            recommendationsCard.isHidden = true
        }
    }

    private func readinessLabel(_ label: TheoryReadinessLabel) -> String {
        switch label {
        case .building:
            return "Building"
        case .almostReady:
            return "Almost ready"
        case .ready:
            return "Ready"
        }
    }

    @objc
    private func openContinueLesson() {
        let progress = progressStore.progress
        let lesson = pack.lessons.first(where: { !progress.completedLessons.contains($0.id) }) ?? pack.lessons.first
        guard let lesson else { return }
        navigationController?.pushViewController(TheoryLessonViewController(lessonId: lesson.id), animated: true)
    }

    @objc
    private func startQuickQuiz() {
        let progress = progressStore.progress
        let recommendedTopicId = progress.lastRouteTagSnapshot
            .map { TheoryRouteTagMapper.mapTags($0.tags).first }
            ?? nil
        let topicId = recommendedTopicId ?? pack.topics.first?.id
        navigationController?.pushViewController(
            TheoryQuizViewController(mode: .topic, topicId: topicId, questionCount: 10),
            animated: true
        )
    }

    @objc
    private func startMockQuiz() {
        navigationController?.pushViewController(
            TheoryQuizViewController(mode: .mock, topicId: nil, questionCount: 50),
            animated: true
        )
    }

    @objc
    private func openTopics() {
        navigationController?.pushViewController(TheoryTopicListViewController(), animated: true)
    }

    @objc
    private func openBookmarks() {
        navigationController?.pushViewController(TheoryBookmarksViewController(), animated: true)
    }

    @objc
    private func openWrongAnswers() {
        navigationController?.pushViewController(TheoryWrongAnswersViewController(), animated: true)
    }

    @objc
    private func openTheorySettings() {
        navigationController?.pushViewController(TheorySettingsViewController(), animated: true)
    }
}
