import UIKit

final class PracticeRouteListViewController: UIViewController {
    private let centre: TestCentre
    private let routeStore: PracticeRouteStore
    private let settingsStore = SettingsStore.shared
    private var routes: [PracticeRoute] = []

    private let tableView = UITableView(frame: .zero, style: .plain)

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
        view.backgroundColor = .clear
        routes = routeStore.loadRoutesForCentre(centre.id)

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(PracticeRouteCardCell.self, forCellReuseIdentifier: PracticeRouteCardCell.reuseID)
        tableView.contentInset = UIEdgeInsets(top: 10, left: 0, bottom: 18, right: 0)
        tableView.tableHeaderView = makeHeaderView()

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

    private func makeHeaderView() -> UIView {
        let header = UIView(frame: CGRect(x: 0, y: 0, width: view.bounds.width - 28, height: 164))
        header.backgroundColor = .clear

        let logo = DrivestBrand.logoImageView()
        let titleLabel = UILabel()
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.text = "\(centre.name) Practice Routes"
        titleLabel.font = .systemFont(ofSize: 24, weight: .bold)
        titleLabel.textColor = DrivestPalette.textPrimary
        titleLabel.numberOfLines = 2

        let subtitle = UILabel()
        subtitle.translatesAutoresizingMaskIntoConstraints = false
        subtitle.text = centre.address
        subtitle.font = .systemFont(ofSize: 14, weight: .regular)
        subtitle.textColor = DrivestPalette.textSecondary
        subtitle.numberOfLines = 2

        let offlineBadge = UILabel()
        offlineBadge.translatesAutoresizingMaskIntoConstraints = false
        offlineBadge.text = settingsStore.dataSourceMode == .assetsOnly ? "Offline data" : ""
        offlineBadge.font = .systemFont(ofSize: 11, weight: .bold)
        offlineBadge.textColor = DrivestPalette.accentChipText
        offlineBadge.backgroundColor = DrivestPalette.accentPrimarySoft
        offlineBadge.layer.cornerRadius = 8
        offlineBadge.layer.masksToBounds = true
        offlineBadge.textAlignment = .center
        offlineBadge.isHidden = settingsStore.dataSourceMode != .assetsOnly

        header.addSubview(logo)
        header.addSubview(titleLabel)
        header.addSubview(subtitle)
        header.addSubview(offlineBadge)

        NSLayoutConstraint.activate([
            logo.topAnchor.constraint(equalTo: header.topAnchor, constant: 4),
            logo.centerXAnchor.constraint(equalTo: header.centerXAnchor),
            logo.heightAnchor.constraint(equalToConstant: 54),
            logo.widthAnchor.constraint(lessThanOrEqualTo: header.widthAnchor, multiplier: 0.56),

            titleLabel.topAnchor.constraint(equalTo: logo.bottomAnchor, constant: 10),
            titleLabel.leadingAnchor.constraint(equalTo: header.leadingAnchor),
            titleLabel.trailingAnchor.constraint(equalTo: header.trailingAnchor),

            subtitle.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
            subtitle.leadingAnchor.constraint(equalTo: header.leadingAnchor),
            subtitle.trailingAnchor.constraint(equalTo: header.trailingAnchor),

            offlineBadge.topAnchor.constraint(equalTo: subtitle.bottomAnchor, constant: 8),
            offlineBadge.leadingAnchor.constraint(equalTo: header.leadingAnchor),
            offlineBadge.heightAnchor.constraint(equalToConstant: 24),
            offlineBadge.widthAnchor.constraint(greaterThanOrEqualToConstant: 90)
        ])

        return header
    }
}

extension PracticeRouteListViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        routes.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        guard let cell = tableView.dequeueReusableCell(
            withIdentifier: PracticeRouteCardCell.reuseID,
            for: indexPath
        ) as? PracticeRouteCardCell else {
            return UITableViewCell()
        }
        cell.configure(with: routes[indexPath.row])
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let route = routes[indexPath.row]
        settingsStore.lastMode = .practice
        settingsStore.lastCentreId = centre.id
        navigationController?.pushViewController(
            NavigationSessionViewController(
                mode: .practice,
                centre: centre,
                practiceRoute: route,
                destination: nil
            ),
            animated: true
        )
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        86
    }
}

private final class PracticeRouteCardCell: UITableViewCell {
    static let reuseID = "practice.route.card.cell"

    private let cardView = UIView()
    private let nameLabel = UILabel()
    private let metaLabel = UILabel()
    private let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        cardView.translatesAutoresizingMaskIntoConstraints = false
        cardView.applyCardStyle(cornerRadius: 12)

        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        nameLabel.font = .systemFont(ofSize: 17, weight: .bold)
        nameLabel.textColor = DrivestPalette.textPrimary

        metaLabel.translatesAutoresizingMaskIntoConstraints = false
        metaLabel.font = .systemFont(ofSize: 13, weight: .regular)
        metaLabel.textColor = DrivestPalette.textSecondary

        chevron.translatesAutoresizingMaskIntoConstraints = false
        chevron.tintColor = DrivestPalette.accentPrimary

        contentView.addSubview(cardView)
        cardView.addSubview(nameLabel)
        cardView.addSubview(metaLabel)
        cardView.addSubview(chevron)

        NSLayoutConstraint.activate([
            cardView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            cardView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            cardView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            cardView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),

            nameLabel.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 12),
            nameLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 14),
            nameLabel.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),

            metaLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 4),
            metaLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 14),
            metaLabel.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),
            metaLabel.bottomAnchor.constraint(lessThanOrEqualTo: cardView.bottomAnchor, constant: -12),

            chevron.centerYAnchor.constraint(equalTo: cardView.centerYAnchor),
            chevron.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -14),
            chevron.widthAnchor.constraint(equalToConstant: 11)
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    func configure(with route: PracticeRoute) {
        nameLabel.text = route.name
        metaLabel.text = String(format: "%.1f mi  ·  %.0f min", route.distanceM / 1609.344, route.durationS / 60.0)
    }
}
