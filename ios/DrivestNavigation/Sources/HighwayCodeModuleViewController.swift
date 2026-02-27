import UIKit

final class HighwayCodeModuleViewController: UIViewController {
    private let loader = HighwayCodePackLoader.shared

    private var theoryPack: HighwayCodeTheoryPack = .empty()
    private var questions: [HighwayCodeQuestion] = []

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "The Highway Code"
        view.backgroundColor = .clear
        theoryPack = loader.loadTheory()
        questions = loader.loadQuestions()
        setupLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
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

        contentStack.addArrangedSubview(buildSummaryCard())
        contentStack.addArrangedSubview(buildActionsCard())
        contentStack.addArrangedSubview(buildSafetyCard())
    }

    private func buildSummaryCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 20)

        let totalSections = theoryPack.chapters.reduce(0) { $0 + $1.sections.count }
        let uniqueTopics = Set(questions.map(\.topic)).count

        let titleLabel = UILabel()
        titleLabel.text = "The Highway Code"
        titleLabel.font = .systemFont(ofSize: 28, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        let subtitleLabel = UILabel()
        subtitleLabel.text = "Learn the rules, practice by topic, and run realistic quiz challenges."
        subtitleLabel.numberOfLines = 0
        subtitleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        subtitleLabel.textColor = DrivestPalette.textSecondary

        let statsLabel = UILabel()
        statsLabel.text = "\(theoryPack.chapters.count) chapters • \(totalSections) sections • \(questions.count) questions • \(uniqueTopics) topics"
        statsLabel.numberOfLines = 0
        statsLabel.font = .systemFont(ofSize: 13, weight: .bold)
        statsLabel.textColor = DrivestPalette.accentChipText
        statsLabel.backgroundColor = DrivestPalette.accentPrimarySoft
        statsLabel.layer.cornerRadius = 8
        statsLabel.layer.masksToBounds = true
        statsLabel.textAlignment = .center

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel, statsLabel])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 10

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            statsLabel.heightAnchor.constraint(equalToConstant: 32)
        ])

        return card
    }

    private func buildActionsCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let theoryButton = makeActionButton(title: "Theory chapters", filled: false) { [weak self] in
            self?.navigationController?.pushViewController(HighwayCodeChapterListViewController(), animated: true)
        }
        let practiceButton = makeActionButton(title: "Practice by topic", filled: false) { [weak self] in
            self?.navigationController?.pushViewController(HighwayCodeTopicDrillViewController(), animated: true)
        }
        let quickQuizButton = makeActionButton(title: "Quick quiz (20)", filled: true) { [weak self] in
            self?.navigationController?.pushViewController(
                HighwayCodeQuizViewController(titleText: "Highway quick quiz", topic: nil, questionCount: 20),
                animated: true
            )
        }
        let challengeButton = makeActionButton(title: "Mock challenge (50)", filled: false) { [weak self] in
            self?.navigationController?.pushViewController(
                HighwayCodeQuizViewController(titleText: "Highway mock challenge", topic: nil, questionCount: 50),
                animated: true
            )
        }

        let heading = UILabel()
        heading.text = "Choose your mode"
        heading.font = .systemFont(ofSize: 18, weight: .bold)
        heading.textColor = DrivestPalette.textPrimary

        let stack = UIStackView(arrangedSubviews: [heading, theoryButton, practiceButton, quickQuizButton, challengeButton])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 8

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            theoryButton.heightAnchor.constraint(equalToConstant: 48),
            practiceButton.heightAnchor.constraint(equalToConstant: 48),
            quickQuizButton.heightAnchor.constraint(equalToConstant: 50),
            challengeButton.heightAnchor.constraint(equalToConstant: 48)
        ])

        return card
    }

    private func buildSafetyCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 16)

        let title = UILabel()
        title.text = "Safety and legal note"
        title.font = .systemFont(ofSize: 16, weight: .bold)
        title.textColor = DrivestPalette.textPrimary

        let body = UILabel()
        body.text = theoryPack.meta.scopeNote.isEmpty
            ? "Use this module for revision. Always follow road signs and the Highway Code."
            : theoryPack.meta.scopeNote
        body.numberOfLines = 0
        body.font = .systemFont(ofSize: 13, weight: .regular)
        body.textColor = DrivestPalette.textSecondary

        let stack = UIStackView(arrangedSubviews: [title, body])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 6

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])

        return card
    }

    private func makeActionButton(title: String, filled: Bool, action: @escaping () -> Void) -> UIButton {
        let button = UIButton(type: .system)
        var config: UIButton.Configuration = filled ? .filled() : .bordered()
        config.title = title
        button.configuration = config
        if filled {
            DrivestBrand.stylePrimaryButton(button)
        } else {
            DrivestBrand.styleOutlinedButton(button)
        }
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return button
    }
}

final class HighwayCodeChapterListViewController: UIViewController {
    private let chapters = HighwayCodePackLoader.shared.loadTheory().chapters
    private let tableView = UITableView(frame: .zero, style: .plain)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Highway theory"
        view.backgroundColor = .clear

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self

        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}

extension HighwayCodeChapterListViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        chapters.count
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        86
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        navigationController?.pushViewController(
            HighwayCodeChapterDetailViewController(chapter: chapters[indexPath.row]),
            animated: true
        )
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let identifier = "highway.chapter.cell"
        let cell = tableView.dequeueReusableCell(withIdentifier: identifier) ?? UITableViewCell(style: .subtitle, reuseIdentifier: identifier)
        let chapter = chapters[indexPath.row]

        cell.backgroundColor = .clear
        cell.selectionStyle = .none
        cell.contentView.subviews.forEach { subview in
            if subview.tag == 7001 {
                subview.removeFromSuperview()
            }
        }

        let card = UIView()
        card.tag = 7001
        card.translatesAutoresizingMaskIntoConstraints = false
        card.applyCardStyle(cornerRadius: 14)
        cell.contentView.addSubview(card)

        let title = UILabel()
        title.translatesAutoresizingMaskIntoConstraints = false
        title.text = chapter.title
        title.font = .systemFont(ofSize: 16, weight: .bold)
        title.textColor = DrivestPalette.textPrimary

        let subtitle = UILabel()
        subtitle.translatesAutoresizingMaskIntoConstraints = false
        subtitle.text = "\(chapter.sections.count) sections"
        subtitle.font = .systemFont(ofSize: 13, weight: .medium)
        subtitle.textColor = DrivestPalette.textSecondary

        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.translatesAutoresizingMaskIntoConstraints = false
        chevron.tintColor = DrivestPalette.accentPrimary

        card.addSubview(title)
        card.addSubview(subtitle)
        card.addSubview(chevron)

        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 6),
            card.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor),
            card.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor),
            card.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -6),

            title.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            title.topAnchor.constraint(equalTo: card.topAnchor, constant: 12),
            title.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),

            subtitle.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            subtitle.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 4),
            subtitle.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),

            chevron.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            chevron.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14)
        ])

        return cell
    }
}

final class HighwayCodeChapterDetailViewController: UIViewController {
    private let chapter: HighwayCodeChapter

    init(chapter: HighwayCodeChapter) {
        self.chapter = chapter
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = chapter.title
        view.backgroundColor = .clear
        setupLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    private func setupLayout() {
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

        let overviewCard = UIView()
        overviewCard.applyCardStyle(cornerRadius: 18)

        let overviewTitle = UILabel()
        overviewTitle.text = chapter.title
        overviewTitle.font = .systemFont(ofSize: 24, weight: .bold)
        overviewTitle.textColor = DrivestPalette.textPrimary

        let overviewBody = UILabel()
        overviewBody.text = chapter.overview
        overviewBody.numberOfLines = 0
        overviewBody.font = .systemFont(ofSize: 14, weight: .regular)
        overviewBody.textColor = DrivestPalette.textSecondary

        let overviewStack = UIStackView(arrangedSubviews: [overviewTitle, overviewBody])
        overviewStack.translatesAutoresizingMaskIntoConstraints = false
        overviewStack.axis = .vertical
        overviewStack.spacing = 8

        overviewCard.addSubview(overviewStack)
        NSLayoutConstraint.activate([
            overviewStack.topAnchor.constraint(equalTo: overviewCard.topAnchor, constant: 16),
            overviewStack.leadingAnchor.constraint(equalTo: overviewCard.leadingAnchor, constant: 16),
            overviewStack.trailingAnchor.constraint(equalTo: overviewCard.trailingAnchor, constant: -16),
            overviewStack.bottomAnchor.constraint(equalTo: overviewCard.bottomAnchor, constant: -16)
        ])
        stack.addArrangedSubview(overviewCard)

        for section in chapter.sections {
            stack.addArrangedSubview(sectionCard(section))
        }
    }

    private func sectionCard(_ section: HighwayCodeSection) -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 16)

        let title = UILabel()
        title.text = section.title
        title.font = .systemFont(ofSize: 18, weight: .bold)
        title.textColor = DrivestPalette.textPrimary

        let summary = UILabel()
        summary.text = section.summary
        summary.numberOfLines = 0
        summary.font = .systemFont(ofSize: 14, weight: .regular)
        summary.textColor = DrivestPalette.textSecondary

        let rules = UILabel()
        rules.text = "Key rules\n" + section.keyRules.map { "• \($0)" }.joined(separator: "\n")
        rules.numberOfLines = 0
        rules.font = .systemFont(ofSize: 13, weight: .medium)
        rules.textColor = DrivestPalette.textPrimary

        let prompts = UILabel()
        prompts.text = section.quickQuizPrompts.isEmpty
            ? ""
            : "Check yourself\n" + section.quickQuizPrompts.map { "• \($0)" }.joined(separator: "\n")
        prompts.numberOfLines = 0
        prompts.font = .systemFont(ofSize: 13, weight: .regular)
        prompts.textColor = DrivestPalette.textSecondary
        prompts.isHidden = section.quickQuizPrompts.isEmpty

        let stack = UIStackView(arrangedSubviews: [title, summary, rules, prompts])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 8

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])

        return card
    }
}

final class HighwayCodeTopicDrillViewController: UIViewController {
    private let questions = HighwayCodePackLoader.shared.loadQuestions()
    private let tableView = UITableView(frame: .zero, style: .plain)
    private lazy var topicCounts: [(topic: String, count: Int)] = {
        let grouped = Dictionary(grouping: questions, by: \.topic)
        return grouped
            .map { ($0.key, $0.value.count) }
            .sorted { lhs, rhs in
                if lhs.count == rhs.count {
                    return lhs.topic < rhs.topic
                }
                return lhs.count > rhs.count
            }
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Practice by topic"
        view.backgroundColor = .clear

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self

        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}

extension HighwayCodeTopicDrillViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        topicCounts.count
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        80
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let topic = topicCounts[indexPath.row].topic
        navigationController?.pushViewController(
            HighwayCodeQuizViewController(
                titleText: topic,
                topic: topic,
                questionCount: 15
            ),
            animated: true
        )
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let identifier = "highway.topic.cell"
        let cell = tableView.dequeueReusableCell(withIdentifier: identifier) ?? UITableViewCell(style: .subtitle, reuseIdentifier: identifier)
        let item = topicCounts[indexPath.row]

        var config = cell.defaultContentConfiguration()
        config.text = item.topic
        config.secondaryText = "\(item.count) questions"
        config.textProperties.font = .systemFont(ofSize: 15, weight: .bold)
        config.secondaryTextProperties.color = DrivestPalette.textSecondary
        cell.contentConfiguration = config
        cell.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        cell.layer.cornerRadius = 12
        cell.layer.borderWidth = 1
        cell.layer.borderColor = DrivestPalette.cardStroke.cgColor
        cell.selectionStyle = .none

        return cell
    }
}

final class HighwayCodeQuizViewController: UIViewController {
    private let titleText: String
    private let topic: String?
    private let questionCount: Int

    private let allQuestions = HighwayCodePackLoader.shared.loadQuestions()
    private var questions: [HighwayCodeQuestion] = []

    private var currentIndex = 0
    private var score = 0
    private var selectedIndex: Int?
    private var answerSubmitted = false

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let titleLabel = UILabel()
    private let progressLabel = UILabel()
    private let promptLabel = UILabel()
    private let optionsStack = UIStackView()
    private var optionButtons: [UIButton] = []
    private let feedbackLabel = UILabel()
    private let submitButton = UIButton(type: .system)
    private let nextButton = UIButton(type: .system)
    private let summaryLabel = UILabel()

    init(titleText: String, topic: String?, questionCount: Int) {
        self.titleText = titleText
        self.topic = topic
        self.questionCount = max(1, questionCount)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Quiz"
        view.backgroundColor = .clear
        setupLayout()
        initializeQuiz()
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

        let panel = UIView()
        panel.applyCardStyle(cornerRadius: 18)

        let panelStack = UIStackView()
        panelStack.translatesAutoresizingMaskIntoConstraints = false
        panelStack.axis = .vertical
        panelStack.spacing = 10

        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        progressLabel.font = .systemFont(ofSize: 13, weight: .bold)
        progressLabel.textColor = DrivestPalette.accentChipText
        progressLabel.backgroundColor = DrivestPalette.accentPrimarySoft
        progressLabel.layer.cornerRadius = 8
        progressLabel.layer.masksToBounds = true
        progressLabel.textAlignment = .center

        promptLabel.font = .systemFont(ofSize: 20, weight: .bold)
        promptLabel.textColor = DrivestPalette.textPrimary
        promptLabel.numberOfLines = 0

        optionsStack.axis = .vertical
        optionsStack.spacing = 8

        for index in 0..<4 {
            let optionButton = UIButton(type: .system)
            optionButton.tag = index
            optionButton.contentHorizontalAlignment = .leading
            optionButton.titleLabel?.numberOfLines = 0
            var config = UIButton.Configuration.bordered()
            config.title = ""
            config.baseForegroundColor = DrivestPalette.textPrimary
            config.background.cornerRadius = 12
            config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 14, bottom: 12, trailing: 14)
            optionButton.configuration = config
            optionButton.layer.borderColor = DrivestPalette.cardStroke.cgColor
            optionButton.layer.borderWidth = 1
            optionButton.layer.cornerRadius = 12
            optionButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 54).isActive = true
            optionButton.addTarget(self, action: #selector(selectOption(_:)), for: .touchUpInside)
            optionButtons.append(optionButton)
            optionsStack.addArrangedSubview(optionButton)
        }

        feedbackLabel.font = .systemFont(ofSize: 14, weight: .regular)
        feedbackLabel.textColor = DrivestPalette.textSecondary
        feedbackLabel.numberOfLines = 0

        var submitConfig = UIButton.Configuration.filled()
        submitConfig.title = "Submit answer"
        submitButton.configuration = submitConfig
        DrivestBrand.stylePrimaryButton(submitButton)
        submitButton.addTarget(self, action: #selector(submitAnswer), for: .touchUpInside)

        var nextConfig = UIButton.Configuration.bordered()
        nextConfig.title = "Next question"
        nextButton.configuration = nextConfig
        DrivestBrand.styleOutlinedButton(nextButton)
        nextButton.addTarget(self, action: #selector(nextTapped), for: .touchUpInside)
        nextButton.isHidden = true

        summaryLabel.font = .systemFont(ofSize: 16, weight: .bold)
        summaryLabel.textColor = DrivestPalette.textPrimary
        summaryLabel.numberOfLines = 0
        summaryLabel.isHidden = true

        panelStack.addArrangedSubview(titleLabel)
        panelStack.addArrangedSubview(progressLabel)
        panelStack.addArrangedSubview(promptLabel)
        panelStack.addArrangedSubview(optionsStack)
        panelStack.addArrangedSubview(feedbackLabel)
        panelStack.addArrangedSubview(submitButton)
        panelStack.addArrangedSubview(nextButton)
        panelStack.addArrangedSubview(summaryLabel)

        panel.addSubview(panelStack)
        NSLayoutConstraint.activate([
            panelStack.topAnchor.constraint(equalTo: panel.topAnchor, constant: 16),
            panelStack.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 16),
            panelStack.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -16),
            panelStack.bottomAnchor.constraint(equalTo: panel.bottomAnchor, constant: -16),
            progressLabel.heightAnchor.constraint(equalToConstant: 30),
            submitButton.heightAnchor.constraint(equalToConstant: 48),
            nextButton.heightAnchor.constraint(equalToConstant: 48)
        ])

        contentStack.addArrangedSubview(panel)
    }

    private func initializeQuiz() {
        let pool: [HighwayCodeQuestion]
        if let topic {
            pool = allQuestions.filter { $0.topic == topic }
        } else {
            pool = allQuestions
        }

        questions = Array(pool.shuffled().prefix(questionCount))
        if questions.isEmpty {
            titleLabel.text = titleText
            progressLabel.text = ""
            promptLabel.text = "No questions available."
            optionsStack.isHidden = true
            submitButton.isHidden = true
            nextButton.isHidden = true
            summaryLabel.isHidden = false
            summaryLabel.text = "Try another topic or refresh your question data."
            return
        }

        currentIndex = 0
        score = 0
        renderQuestion()
    }

    private func renderQuestion() {
        let question = questions[currentIndex]
        titleLabel.text = titleText
        progressLabel.text = "Question \(currentIndex + 1) / \(questions.count)"
        promptLabel.text = question.question
        feedbackLabel.text = ""
        feedbackLabel.textColor = DrivestPalette.textSecondary
        summaryLabel.isHidden = true
        submitButton.isHidden = false
        submitButton.isEnabled = true
        nextButton.isHidden = true
        optionsStack.isHidden = false
        selectedIndex = nil
        answerSubmitted = false

        for (index, button) in optionButtons.enumerated() {
            guard index < question.options.count else {
                button.isHidden = true
                continue
            }
            button.isHidden = false
            var config = button.configuration
            config?.title = question.options[index]
            config?.baseForegroundColor = DrivestPalette.textPrimary
            config?.baseBackgroundColor = UIColor.white
            button.configuration = config
            button.layer.borderColor = DrivestPalette.cardStroke.cgColor
            button.layer.borderWidth = 1
        }
    }

    @objc
    private func selectOption(_ sender: UIButton) {
        guard !answerSubmitted else { return }
        selectedIndex = sender.tag

        for button in optionButtons where !button.isHidden {
            var config = button.configuration
            let isSelected = button.tag == selectedIndex
            config?.baseBackgroundColor = isSelected ? DrivestPalette.accentPrimarySoft : UIColor.white
            button.configuration = config
            button.layer.borderColor = isSelected ? DrivestPalette.accentPrimary.cgColor : DrivestPalette.cardStroke.cgColor
            button.layer.borderWidth = isSelected ? 1.5 : 1
        }
    }

    @objc
    private func submitAnswer() {
        guard !answerSubmitted else { return }
        guard let selectedIndex else {
            feedbackLabel.text = "Pick an option before submitting."
            feedbackLabel.textColor = UIColor(red: 0.78, green: 0.16, blue: 0.16, alpha: 1)
            return
        }

        answerSubmitted = true
        submitButton.isEnabled = false

        let question = questions[currentIndex]
        let isCorrect = selectedIndex == question.correctAnswerIndex
        if isCorrect { score += 1 }

        for button in optionButtons where !button.isHidden {
            var config = button.configuration
            if button.tag == question.correctAnswerIndex {
                config?.baseBackgroundColor = UIColor(red: 0.84, green: 0.95, blue: 0.85, alpha: 1)
                button.layer.borderColor = UIColor(red: 0.18, green: 0.49, blue: 0.20, alpha: 1).cgColor
                button.layer.borderWidth = 1.5
            } else if button.tag == selectedIndex {
                config?.baseBackgroundColor = UIColor(red: 0.98, green: 0.86, blue: 0.86, alpha: 1)
                button.layer.borderColor = UIColor(red: 0.78, green: 0.16, blue: 0.16, alpha: 1).cgColor
                button.layer.borderWidth = 1.5
            } else {
                button.layer.borderColor = DrivestPalette.cardStroke.cgColor
                button.layer.borderWidth = 1
            }
            button.configuration = config
        }

        feedbackLabel.text = (isCorrect ? "Correct. " : "Incorrect. ") + question.explanation
        feedbackLabel.textColor = isCorrect
            ? UIColor(red: 0.18, green: 0.49, blue: 0.20, alpha: 1)
            : UIColor(red: 0.78, green: 0.16, blue: 0.16, alpha: 1)

        nextButton.isHidden = false
        if currentIndex == questions.count - 1 {
            var config = nextButton.configuration
            config?.title = "Finish quiz"
            nextButton.configuration = config
        }
    }

    @objc
    private func nextTapped() {
        if currentIndex < questions.count - 1 {
            currentIndex += 1
            renderQuestion()
        } else {
            showSummary()
        }
    }

    private func showSummary() {
        let percent = Int((Double(score) / Double(max(questions.count, 1))) * 100)
        optionsStack.isHidden = true
        submitButton.isHidden = true
        nextButton.isHidden = true
        summaryLabel.isHidden = false
        summaryLabel.text = "Score: \(score)/\(questions.count) (\(percent)% )\nGreat work. Use Practice by topic to target weak areas."
        feedbackLabel.text = ""
        progressLabel.text = "Completed"
    }
}
