import UIKit

final class DestinationSearchViewController: UIViewController {
    private let searchRepository = DestinationSearchRepository()
    private let settingsStore = SettingsStore.shared
    private var suggestions: [DestinationSuggestion] = []
    private var searchTask: Task<Void, Never>?

    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let searchController = UISearchController(searchResultsController: nil)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Choose Destination"
        view.backgroundColor = .systemBackground

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search destination"
        navigationItem.searchController = searchController
        navigationItem.hidesSearchBarWhenScrolling = false
        definesPresentationContext = true

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "destination")

        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func queryDestinations(_ text: String) {
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            guard let self else { return }
            if text.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 {
                await MainActor.run {
                    self.suggestions = []
                    self.tableView.reloadData()
                }
                return
            }

            let results = await searchRepository.search(query: text)
            await MainActor.run {
                self.suggestions = results
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
        let cell = tableView.dequeueReusableCell(withIdentifier: "destination", for: indexPath)
        var content = cell.defaultContentConfiguration()
        let suggestion = suggestions[indexPath.row]
        content.text = suggestion.name
        content.secondaryText = suggestion.placeName
        cell.contentConfiguration = content
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let destination = suggestions[indexPath.row]
        settingsStore.lastMode = .navigation
        let centre = CentreRepository().findById(settingsStore.lastCentreId)
        let controller = NavigationSessionViewController(
            mode: .navigation,
            centre: centre,
            practiceRoute: nil,
            destination: destination
        )
        navigationController?.pushViewController(controller, animated: true)
    }
}
