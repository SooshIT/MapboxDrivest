import UIKit

final class KnowYourSignsModuleViewController: UIViewController {
    private let loader = KnowYourSignsPackLoader.shared

    private var theoryPack: KnowYourSignsTheoryPack = .empty()
    private var questions: [KnowYourSignsQuestion] = []

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Know Your Signs"
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
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
        ])

        contentStack.addArrangedSubview(buildSummaryCard())
        contentStack.addArrangedSubview(buildActionsCard())
        contentStack.addArrangedSubview(buildSafetyCard())
    }

    private func buildSummaryCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 20)

        let sectionCount = theoryPack.chapters.reduce(0) { $0 + $1.sections.count }
        let signCount = theoryPack.chapters
            .flatMap(\.sections)
            .flatMap(\.signs)
            .count
        let uniqueTopics = Set(questions.map(\.topic)).count

        let titleLabel = UILabel()
        titleLabel.text = "Know Your Signs"
        titleLabel.font = .systemFont(ofSize: 28, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary

        let subtitleLabel = UILabel()
        subtitleLabel.text = "Master UK road signs with image-first revision, practical responses, and targeted quizzes."
        subtitleLabel.numberOfLines = 0
        subtitleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        subtitleLabel.textColor = DrivestPalette.textSecondary

        let statsLabel = UILabel()
        statsLabel.text = "\(theoryPack.chapters.count) chapters • \(sectionCount) sections • \(signCount) signs • \(questions.count) questions • \(uniqueTopics) topics"
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
            statsLabel.heightAnchor.constraint(equalToConstant: 36),
        ])

        return card
    }

    private func buildActionsCard() -> UIView {
        let card = UIView()
        card.applyCardStyle(cornerRadius: 18)

        let chaptersButton = makeActionButton(title: "Theory chapters", filled: false) { [weak self] in
            self?.navigationController?.pushViewController(KnowYourSignsChapterListViewController(), animated: true)
        }
        let topicsButton = makeActionButton(title: "Practice by topic", filled: false) { [weak self] in
            self?.navigationController?.pushViewController(KnowYourSignsTopicDrillViewController(), animated: true)
        }
        let quickQuizButton = makeActionButton(title: "Quick sign quiz (20)", filled: true) { [weak self] in
            self?.navigationController?.pushViewController(
                KnowYourSignsQuizViewController(titleText: "Know Your Signs quick quiz", topic: nil, questionCount: 20),
                animated: true
            )
        }
        let mockQuizButton = makeActionButton(title: "Mock sign test (50)", filled: false) { [weak self] in
            self?.navigationController?.pushViewController(
                KnowYourSignsQuizViewController(titleText: "Know Your Signs mock test", topic: nil, questionCount: 50),
                animated: true
            )
        }

        let heading = UILabel()
        heading.text = "Choose your mode"
        heading.font = .systemFont(ofSize: 18, weight: .bold)
        heading.textColor = DrivestPalette.textPrimary

        let stack = UIStackView(arrangedSubviews: [heading, chaptersButton, topicsButton, quickQuizButton, mockQuizButton])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 8

        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            chaptersButton.heightAnchor.constraint(equalToConstant: 48),
            topicsButton.heightAnchor.constraint(equalToConstant: 48),
            quickQuizButton.heightAnchor.constraint(equalToConstant: 50),
            mockQuizButton.heightAnchor.constraint(equalToConstant: 48),
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
        body.text = theoryPack.meta.note.isEmpty
            ? "Use this module for sign revision only. Always follow live road signs, signals, and the Highway Code."
            : theoryPack.meta.note
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
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14),
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

final class KnowYourSignsChapterListViewController: UIViewController {
    private let chapters = KnowYourSignsPackLoader.shared.loadTheory().chapters
    private let tableView = UITableView(frame: .zero, style: .plain)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Know Your Signs"
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
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}

extension KnowYourSignsChapterListViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        chapters.count
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        92
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        navigationController?.pushViewController(
            KnowYourSignsChapterDetailViewController(chapter: chapters[indexPath.row]),
            animated: true
        )
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let identifier = "kys.chapter.cell"
        let cell = tableView.dequeueReusableCell(withIdentifier: identifier) ?? UITableViewCell(style: .subtitle, reuseIdentifier: identifier)
        let chapter = chapters[indexPath.row]
        let signCount = chapter.sections.flatMap(\.signs).count

        cell.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        cell.layer.cornerRadius = 12
        cell.layer.borderWidth = 1
        cell.layer.borderColor = DrivestPalette.cardStroke.cgColor
        cell.selectionStyle = .none

        var config = cell.defaultContentConfiguration()
        config.text = chapter.title
        config.secondaryText = "\(chapter.sections.count) sections • \(signCount) signs"
        config.textProperties.font = .systemFont(ofSize: 16, weight: .bold)
        config.secondaryTextProperties.color = DrivestPalette.textSecondary
        cell.contentConfiguration = config

        return cell
    }
}

final class KnowYourSignsChapterDetailViewController: UIViewController {
    private let chapter: KnowYourSignsChapter
    private let signs: [KnowYourSign]

    private let tableView = UITableView(frame: .zero, style: .plain)

    init(chapter: KnowYourSignsChapter) {
        self.chapter = chapter
        self.signs = chapter.sections.flatMap(\.signs)
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

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(KnowYourSignCell.self, forCellReuseIdentifier: KnowYourSignCell.reuseID)

        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}

extension KnowYourSignsChapterDetailViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        2
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? 1 : signs.count
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        indexPath.section == 0 ? 200 : 156
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? nil : "Signs"
    }

    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        guard section == 1 else { return nil }
        let label = UILabel()
        label.text = "  Signs"
        label.font = .systemFont(ofSize: 16, weight: .bold)
        label.textColor = DrivestPalette.textPrimary
        return label
    }

    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        section == 1 ? 28 : 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if indexPath.section == 0 {
            let id = "kys.chapter.overview"
            let cell = tableView.dequeueReusableCell(withIdentifier: id) ?? UITableViewCell(style: .subtitle, reuseIdentifier: id)
            cell.backgroundColor = UIColor.white.withAlphaComponent(0.94)
            cell.layer.cornerRadius = 14
            cell.layer.borderWidth = 1
            cell.layer.borderColor = DrivestPalette.cardStroke.cgColor
            cell.selectionStyle = .none

            let section = chapter.sections.first
            let quickChecks = (section?.quickChecks ?? []).prefix(2).map { "• \($0)" }.joined(separator: "\n")
            let focus = (section?.commonExamFocus ?? []).prefix(2).map { "• \($0)" }.joined(separator: "\n")

            cell.textLabel?.text = chapter.overview
            cell.textLabel?.numberOfLines = 4
            cell.textLabel?.font = .systemFont(ofSize: 14, weight: .regular)
            cell.textLabel?.textColor = DrivestPalette.textSecondary

            cell.detailTextLabel?.text = [focus.isEmpty ? nil : "Exam focus\n\(focus)", quickChecks.isEmpty ? nil : "Quick checks\n\(quickChecks)"]
                .compactMap { $0 }
                .joined(separator: "\n\n")
            cell.detailTextLabel?.numberOfLines = 6
            cell.detailTextLabel?.font = .systemFont(ofSize: 12, weight: .medium)
            cell.detailTextLabel?.textColor = DrivestPalette.textPrimary
            return cell
        }

        let sign = signs[indexPath.row]
        guard let cell = tableView.dequeueReusableCell(withIdentifier: KnowYourSignCell.reuseID, for: indexPath) as? KnowYourSignCell else {
            return UITableViewCell()
        }
        cell.configure(with: sign)
        return cell
    }
}

final class KnowYourSignCell: UITableViewCell {
    static let reuseID = "kys.sign.cell"

    private let card = UIView()
    private let signImageView = UIImageView()
    private let titleLabel = UILabel()
    private let meaningLabel = UILabel()
    private let actionLabel = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setup()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    private func setup() {
        backgroundColor = .clear
        selectionStyle = .none

        card.translatesAutoresizingMaskIntoConstraints = false
        card.applyCardStyle(cornerRadius: 14)

        signImageView.translatesAutoresizingMaskIntoConstraints = false
        signImageView.contentMode = .scaleAspectFit
        signImageView.layer.cornerRadius = 8
        signImageView.layer.masksToBounds = true
        signImageView.backgroundColor = UIColor.white.withAlphaComponent(0.7)

        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.font = .systemFont(ofSize: 15, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary
        titleLabel.numberOfLines = 2

        meaningLabel.translatesAutoresizingMaskIntoConstraints = false
        meaningLabel.font = .systemFont(ofSize: 12, weight: .regular)
        meaningLabel.textColor = DrivestPalette.textSecondary
        meaningLabel.numberOfLines = 3

        actionLabel.translatesAutoresizingMaskIntoConstraints = false
        actionLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        actionLabel.textColor = DrivestPalette.accentChipText
        actionLabel.numberOfLines = 2

        contentView.addSubview(card)
        card.addSubview(signImageView)
        card.addSubview(titleLabel)
        card.addSubview(meaningLabel)
        card.addSubview(actionLabel)

        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            card.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 2),
            card.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -2),
            card.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),

            signImageView.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 10),
            signImageView.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            signImageView.widthAnchor.constraint(equalToConstant: 92),
            signImageView.heightAnchor.constraint(equalToConstant: 92),

            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 10),
            titleLabel.leadingAnchor.constraint(equalTo: signImageView.trailingAnchor, constant: 10),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -10),

            meaningLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),
            meaningLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            meaningLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            actionLabel.topAnchor.constraint(equalTo: meaningLabel.bottomAnchor, constant: 4),
            actionLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            actionLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            actionLabel.bottomAnchor.constraint(lessThanOrEqualTo: card.bottomAnchor, constant: -8),
        ])
    }

    func configure(with sign: KnowYourSign) {
        titleLabel.text = KnowYourSignsTextFormatter.displayTitle(for: sign)
        meaningLabel.text = KnowYourSignsTextFormatter.displayMeaning(for: sign)
        actionLabel.text = "Response: \(sign.driverAction)"
        signImageView.image = KnowYourSignsImageResolver.image(for: sign.imagePath)
    }
}

final class KnowYourSignsTopicDrillViewController: UIViewController {
    private let questions = KnowYourSignsPackLoader.shared.loadQuestions()
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
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }
}

extension KnowYourSignsTopicDrillViewController: UITableViewDataSource, UITableViewDelegate {
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
            KnowYourSignsQuizViewController(titleText: topic, topic: topic, questionCount: 20),
            animated: true
        )
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let identifier = "kys.topic.cell"
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

final class KnowYourSignsQuizViewController: UIViewController {
    private let titleText: String
    private let topic: String?
    private let questionCount: Int

    private let allQuestions = KnowYourSignsPackLoader.shared.loadQuestions()
    private var questions: [KnowYourSignsQuestion] = []

    private var currentIndex = 0
    private var score = 0
    private var selectedIndex: Int?
    private var answerSubmitted = false

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let titleLabel = UILabel()
    private let progressLabel = UILabel()
    private let signImageView = UIImageView()
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
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
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

        signImageView.contentMode = .scaleAspectFit
        signImageView.clipsToBounds = true
        signImageView.backgroundColor = UIColor.white.withAlphaComponent(0.75)
        signImageView.layer.cornerRadius = 10
        signImageView.translatesAutoresizingMaskIntoConstraints = false

        promptLabel.font = .systemFont(ofSize: 20, weight: .bold)
        promptLabel.textColor = DrivestPalette.textPrimary
        promptLabel.numberOfLines = 0

        optionsStack.axis = .vertical
        optionsStack.spacing = 8

        for index in 0 ..< 4 {
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
        panelStack.addArrangedSubview(signImageView)
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
            signImageView.heightAnchor.constraint(equalToConstant: 150),
            submitButton.heightAnchor.constraint(equalToConstant: 48),
            nextButton.heightAnchor.constraint(equalToConstant: 48),
        ])

        contentStack.addArrangedSubview(panel)
    }

    private func initializeQuiz() {
        let pool: [KnowYourSignsQuestion]
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
            signImageView.isHidden = true
            optionsStack.isHidden = true
            submitButton.isHidden = true
            nextButton.isHidden = true
            summaryLabel.isHidden = false
            summaryLabel.text = "Try another topic or refresh your sign data."
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
        promptLabel.text = KnowYourSignsTextFormatter.displayPrompt(for: question)
        feedbackLabel.text = ""
        feedbackLabel.textColor = DrivestPalette.textSecondary
        summaryLabel.isHidden = true
        submitButton.isHidden = false
        submitButton.isEnabled = true
        nextButton.isHidden = true
        optionsStack.isHidden = false
        selectedIndex = nil
        answerSubmitted = false

        signImageView.image = KnowYourSignsImageResolver.image(for: question.imagePath)
        signImageView.isHidden = signImageView.image == nil

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

        feedbackLabel.text = (isCorrect ? "Correct. " : "Incorrect. ") + KnowYourSignsTextFormatter.displayExplanation(for: question)
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
        signImageView.isHidden = true
        optionsStack.isHidden = true
        submitButton.isHidden = true
        nextButton.isHidden = true
        summaryLabel.isHidden = false
        summaryLabel.text = "Score: \(score)/\(questions.count) (\(percent)% )\nGreat work. Continue with topic drills to strengthen weaker sign groups."
        feedbackLabel.text = ""
        progressLabel.text = "Completed"
    }
}
