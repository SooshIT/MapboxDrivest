import UIKit

final class TheoryQuizViewController: UIViewController {
    private let mode: TheoryQuizMode
    private let topicId: String?
    private let questionCount: Int

    private let pack = TheoryPackLoader.shared.load()
    private let progressStore = TheoryProgressStore.shared

    private var questions: [TheoryQuestion] = []
    private var currentIndex: Int = 0
    private var score: Int = 0
    private var answerSubmitted = false
    private var selectedIndex: Int?
    private var currentOptionIds: [String] = []

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let titleLabel = UILabel()
    private let progressLabel = UILabel()
    private let promptLabel = UILabel()
    private let optionsStack = UIStackView()
    private var optionButtons: [UIButton] = []
    private let feedbackLabel = UILabel()
    private let summaryLabel = UILabel()

    private let bookmarkButton = UIButton(type: .system)
    private let submitButton = UIButton(type: .system)
    private let nextButton = UIButton(type: .system)

    init(mode: TheoryQuizMode, topicId: String?, questionCount: Int) {
        self.mode = mode
        self.topicId = topicId
        self.questionCount = max(questionCount, 1)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Theory quiz"
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

        titleLabel.font = .systemFont(ofSize: 23, weight: .bold)
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
            optionButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .medium)
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

        var bookmarkConfig = UIButton.Configuration.bordered()
        bookmarkConfig.title = "Bookmark question"
        bookmarkButton.configuration = bookmarkConfig
        DrivestBrand.styleOutlinedButton(bookmarkButton)
        bookmarkButton.addTarget(self, action: #selector(toggleBookmark), for: .touchUpInside)

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

        summaryLabel.font = .systemFont(ofSize: 15, weight: .bold)
        summaryLabel.textColor = DrivestPalette.textPrimary
        summaryLabel.numberOfLines = 0
        summaryLabel.isHidden = true

        panelStack.addArrangedSubview(titleLabel)
        panelStack.addArrangedSubview(progressLabel)
        panelStack.addArrangedSubview(promptLabel)
        panelStack.addArrangedSubview(optionsStack)
        panelStack.addArrangedSubview(feedbackLabel)
        panelStack.addArrangedSubview(bookmarkButton)
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
            bookmarkButton.heightAnchor.constraint(equalToConstant: 48),
            submitButton.heightAnchor.constraint(equalToConstant: 48),
            nextButton.heightAnchor.constraint(equalToConstant: 48)
        ])

        contentStack.addArrangedSubview(panel)
    }

    private func initializeQuiz() {
        let progress = progressStore.progress
        questions = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions: pack.questions,
            bookmarks: progress.bookmarks,
            wrongQueue: progress.wrongQueue,
            mode: mode,
            topicId: topicId,
            requestedCount: questionCount
        )

        if questions.isEmpty {
            titleLabel.text = "Theory quiz"
            progressLabel.text = ""
            promptLabel.text = "No questions available."
            optionsStack.isHidden = true
            bookmarkButton.isHidden = true
            submitButton.isHidden = true
            nextButton.isHidden = true
            summaryLabel.isHidden = false
            summaryLabel.text = "No questions available for this selection."
            return
        }

        currentIndex = 0
        score = 0
        answerSubmitted = false
        renderQuestion()
    }

    private func renderQuestion() {
        guard !questions.isEmpty else { return }

        let question = questions[currentIndex]
        titleLabel.text = modeTitle()
        progressLabel.text = "Question \(currentIndex + 1) / \(questions.count)"
        promptLabel.text = question.prompt

        feedbackLabel.text = ""
        feedbackLabel.textColor = DrivestPalette.textSecondary
        summaryLabel.isHidden = true
        submitButton.isHidden = false
        submitButton.isEnabled = true
        nextButton.isHidden = true
        optionsStack.isHidden = false
        bookmarkButton.isHidden = false
        selectedIndex = nil
        answerSubmitted = false

        let paddedOptions = Array(question.options.prefix(4))
        currentOptionIds = paddedOptions.map(\.id)

        for (index, button) in optionButtons.enumerated() {
            guard index < paddedOptions.count else {
                button.isHidden = true
                continue
            }
            button.isHidden = false
            var config = button.configuration
            config?.title = paddedOptions[index].text
            config?.baseForegroundColor = DrivestPalette.textPrimary
            config?.baseBackgroundColor = UIColor.white
            button.configuration = config
            button.layer.borderColor = DrivestPalette.cardStroke.cgColor
            button.layer.borderWidth = 1
        }

        let bookmarked = progressStore.progress.bookmarks.contains(question.id)
        var bookmarkConfig = bookmarkButton.configuration
        bookmarkConfig?.title = bookmarked ? "Bookmarked" : "Bookmark question"
        bookmarkButton.configuration = bookmarkConfig
    }

    private func modeTitle() -> String {
        switch mode {
        case .bookmarks:
            return "Bookmarks"
        case .wrong:
            return "Wrong answers"
        case .topic:
            return "Theory quiz"
        case .mock:
            return "Mock test"
        }
    }

    @objc
    private func selectOption(_ sender: UIButton) {
        guard !answerSubmitted else { return }
        selectedIndex = sender.tag

        for (index, button) in optionButtons.enumerated() {
            if button.isHidden { continue }
            var config = button.configuration
            if index == selectedIndex {
                config?.baseForegroundColor = DrivestPalette.textPrimary
                config?.baseBackgroundColor = DrivestPalette.accentPrimarySoft
                button.layer.borderColor = DrivestPalette.accentPrimary.cgColor
                button.layer.borderWidth = 1.5
            } else {
                config?.baseForegroundColor = DrivestPalette.textPrimary
                config?.baseBackgroundColor = UIColor.white
                button.layer.borderColor = DrivestPalette.cardStroke.cgColor
                button.layer.borderWidth = 1
            }
            button.configuration = config
        }
    }

    @objc
    private func submitAnswer() {
        guard !questions.isEmpty else { return }
        guard let selectedIndex, selectedIndex < currentOptionIds.count else {
            showInlineError("Select an answer first.")
            return
        }

        let question = questions[currentIndex]
        let selectedOptionId = currentOptionIds[selectedIndex]
        let isCorrect = selectedOptionId == question.correctOptionId
        if isCorrect { score += 1 }

        progressStore.recordQuizAnswer(topicId: question.topicId, questionId: question.id, isCorrect: isCorrect)

        if isCorrect {
            feedbackLabel.textColor = UIColor(red: 0.18, green: 0.49, blue: 0.20, alpha: 1)
            feedbackLabel.text = "Correct. \(question.explanation)"
        } else {
            feedbackLabel.textColor = UIColor(red: 0.78, green: 0.16, blue: 0.16, alpha: 1)
            feedbackLabel.text = "Incorrect. \(question.explanation)"
        }

        answerSubmitted = true
        submitButton.isHidden = true
        nextButton.isHidden = false

        var nextConfig = nextButton.configuration
        nextConfig?.title = currentIndex == questions.count - 1 ? "Finish quiz" : "Next question"
        nextButton.configuration = nextConfig
    }

    @objc
    private func nextTapped() {
        guard !questions.isEmpty else { return }

        if !answerSubmitted {
            submitAnswer()
            return
        }

        if currentIndex >= questions.count - 1 {
            finishQuiz()
        } else {
            currentIndex += 1
            renderQuestion()
        }
    }

    @objc
    private func toggleBookmark() {
        guard !questions.isEmpty else { return }
        let question = questions[currentIndex]
        let bookmarked = progressStore.toggleBookmark(question.id)
        var config = bookmarkButton.configuration
        config?.title = bookmarked ? "Bookmarked" : "Bookmark question"
        bookmarkButton.configuration = config
    }

    private func finishQuiz() {
        optionsStack.isHidden = true
        bookmarkButton.isHidden = true
        submitButton.isHidden = true
        nextButton.isHidden = true
        feedbackLabel.text = ""
        summaryLabel.isHidden = false
        summaryLabel.text = "Score: \(score) / \(questions.count)"
    }

    private func showInlineError(_ message: String) {
        feedbackLabel.textColor = UIColor(red: 0.78, green: 0.16, blue: 0.16, alpha: 1)
        feedbackLabel.text = message
    }
}
