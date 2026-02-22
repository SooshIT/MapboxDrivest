import UIKit

final class CentrePickerViewController: UIViewController {
    private let centreRepository = CentreRepository()
    private let settingsStore = SettingsStore.shared

    private var allCentres: [TestCentre] = []
    private var filteredCentres: [TestCentre] = []

    private let searchController = UISearchController(searchResultsController: nil)
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Choose Test Centre"
        view.backgroundColor = .systemBackground

        allCentres = centreRepository.loadCentres()
        filteredCentres = allCentres

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search by centre or town"
        navigationItem.searchController = searchController
        definesPresentationContext = true

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "centre")

        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func applySearch(_ text: String) {
        let query = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if query.isEmpty {
            filteredCentres = allCentres
        } else {
            filteredCentres = allCentres.filter { centre in
                centre.name.lowercased().contains(query) || centre.address.lowercased().contains(query)
            }
        }
        tableView.reloadData()
    }
}

extension CentrePickerViewController: UISearchResultsUpdating {
    func updateSearchResults(for searchController: UISearchController) {
        applySearch(searchController.searchBar.text ?? "")
    }
}

extension CentrePickerViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        filteredCentres.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "centre", for: indexPath)
        var content = cell.defaultContentConfiguration()
        let centre = filteredCentres[indexPath.row]
        content.text = centre.name
        content.secondaryText = centre.address
        cell.contentConfiguration = content
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let centre = filteredCentres[indexPath.row]
        settingsStore.lastCentreId = centre.id
        let vc = PracticeRouteListViewController(centre: centre)
        navigationController?.pushViewController(vc, animated: true)
    }
}
