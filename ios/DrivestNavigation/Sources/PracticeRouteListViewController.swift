import UIKit

final class PracticeRouteListViewController: UIViewController {
    private let centre: TestCentre
    private let routeStore: PracticeRouteStore
    private let settingsStore = SettingsStore.shared
    private var routes: [PracticeRoute] = []

    private let tableView = UITableView(frame: .zero, style: .insetGrouped)

    init(centre: TestCentre, routeStore: PracticeRouteStore = DataSourcePracticeRouteStore()) {
        self.centre = centre
        self.routeStore = routeStore
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Practice Routes"
        view.backgroundColor = .systemBackground
        routes = routeStore.loadRoutesForCentre(centre.id)

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "route")

        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }
}

extension PracticeRouteListViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        routes.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "route", for: indexPath)
        var content = cell.defaultContentConfiguration()
        let route = routes[indexPath.row]
        content.text = route.name
        content.secondaryText = String(format: "%.1f mi - %.0f min", route.distanceM / 1609.344, route.durationS / 60.0)
        cell.contentConfiguration = content
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let route = routes[indexPath.row]
        settingsStore.lastMode = .practice
        settingsStore.lastCentreId = centre.id
        let controller = NavigationSessionViewController(
            mode: .practice,
            centre: centre,
            practiceRoute: route,
            destination: nil
        )
        navigationController?.pushViewController(controller, animated: true)
    }
}
