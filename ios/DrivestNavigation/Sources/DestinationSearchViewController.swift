import UIKit

final class DestinationSearchViewController: UIViewController {
    private let settingsStore = SettingsStore.shared
    private var suggestions: [DestinationSuggestion] = []
    private var searchTask: Task<Void, Never>?

    private let tableView = UITableView(frame: .zero, style: .plain)
    private let searchController = UISearchController(searchResultsController: nil)
    private let emptyLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Choose Destination"
        view.backgroundColor = .clear

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search destination"
        searchController.searchBar.tintColor = DrivestPalette.accentPrimary
        searchController.searchBar.searchTextField.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        navigationItem.searchController = searchController
        navigationItem.hidesSearchBarWhenScrolling = false
        definesPresentationContext = true

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(DestinationCardCell.self, forCellReuseIdentifier: DestinationCardCell.reuseID)

        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        emptyLabel.text = "Type at least 2 characters"
        emptyLabel.textColor = DrivestPalette.textSecondary
        emptyLabel.font = .systemFont(ofSize: 15, weight: .regular)
        emptyLabel.textAlignment = .center

        view.addSubview(tableView)
        view.addSubview(emptyLabel)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        DrivestBrand.ensurePageGradient(in: view)
    }

    private func queryDestinations(_ text: String) {
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            guard let self else { return }
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.count < 2 {
                await MainActor.run {
                    self.suggestions = []
                    self.emptyLabel.text = "Type at least 2 characters"
                    self.emptyLabel.isHidden = false
                    self.tableView.reloadData()
                }
                return
            }

            let repository = DestinationSearchRepository()
            let results = await repository.search(query: trimmed)
            await MainActor.run {
                self.suggestions = results
                self.emptyLabel.text = results.isEmpty ? "No destinations found" : ""
                self.emptyLabel.isHidden = !results.isEmpty
                self.tableView.reloadData()
            }
        }
    }
}

extension DestinationSearchViewController: UISearchResultsUpdating {
    func updateSearchResults(for searchController: UISearchController) {
        queryDestinations(searchController.searchBar.text ?? "")
    }
}

extension DestinationSearchViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        suggestions.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        guard let cell = tableView.dequeueReusableCell(
            withIdentifier: DestinationCardCell.reuseID,
            for: indexPath
        ) as? DestinationCardCell else {
            return UITableViewCell()
        }
        cell.configure(with: suggestions[indexPath.row])
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let destination = suggestions[indexPath.row]
        settingsStore.lastMode = .navigation
        let centre = CentreRepository().findById(settingsStore.lastCentreId)
        navigationController?.pushViewController(
            NavigationSessionViewController(
                mode: .navigation,
                centre: centre,
                practiceRoute: nil,
                destination: destination
            ),
            animated: true
        )
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        84
    }
}

private final class DestinationCardCell: UITableViewCell {
    static let reuseID = "destination.card.cell"

    private let cardView = UIView()
    private let nameLabel = UILabel()
    private let placeNameLabel = UILabel()
    private let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        cardView.translatesAutoresizingMaskIntoConstraints = false
        cardView.applyCardStyle(cornerRadius: 12)

        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        nameLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        nameLabel.textColor = DrivestPalette.textPrimary

        placeNameLabel.translatesAutoresizingMaskIntoConstraints = false
        placeNameLabel.font = .systemFont(ofSize: 13, weight: .regular)
        placeNameLabel.textColor = DrivestPalette.textSecondary

        chevron.translatesAutoresizingMaskIntoConstraints = false
        chevron.tintColor = DrivestPalette.accentPrimary

        contentView.addSubview(cardView)
        cardView.addSubview(nameLabel)
        cardView.addSubview(placeNameLabel)
        cardView.addSubview(chevron)

        NSLayoutConstraint.activate([
            cardView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            cardView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            cardView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            cardView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),

            nameLabel.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 12),
            nameLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 14),
            nameLabel.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),

            placeNameLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 4),
            placeNameLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 14),
            placeNameLabel.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),
            placeNameLabel.bottomAnchor.constraint(lessThanOrEqualTo: cardView.bottomAnchor, constant: -12),

            chevron.centerYAnchor.constraint(equalTo: cardView.centerYAnchor),
            chevron.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -14),
            chevron.widthAnchor.constraint(equalToConstant: 11)
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    func configure(with suggestion: DestinationSuggestion) {
        nameLabel.text = suggestion.name
        placeNameLabel.text = suggestion.placeName
    }
}
