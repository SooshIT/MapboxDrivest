import UIKit

enum DrivestPalette {
    static let pageBackground = UIColor.white
    static let pageGradientTop = UIColor(red: 0.96, green: 0.97, blue: 1.00, alpha: 1)
    static let pageGradientBottom = UIColor(red: 1.00, green: 0.96, blue: 0.93, alpha: 1)

    static let cardBackground = UIColor.white
    static let cardStroke = UIColor(red: 0.91, green: 0.91, blue: 0.95, alpha: 1)

    static let textPrimary = UIColor(red: 0.14, green: 0.12, blue: 0.13, alpha: 1)
    static let textSecondary = UIColor(red: 0.41, green: 0.38, blue: 0.44, alpha: 1)
    static let textMuted = UIColor(red: 0.54, green: 0.52, blue: 0.59, alpha: 1)

    static let accentPrimary = UIColor(red: 0.95, green: 0.44, blue: 0.13, alpha: 1)
    static let accentPrimarySoft = UIColor(red: 1.00, green: 0.91, blue: 0.84, alpha: 1)
    static let accentChipText = UIColor(red: 0.66, green: 0.35, blue: 0.12, alpha: 1)
    static let accentTeal = UIColor(red: 0.18, green: 0.49, blue: 0.20, alpha: 1)
    static let accentBlue = UIColor(red: 0.16, green: 0.42, blue: 0.87, alpha: 1)

    static let heroStart = UIColor(red: 0.11, green: 0.10, blue: 0.14, alpha: 1)
    static let heroEnd = UIColor(red: 0.18, green: 0.17, blue: 0.22, alpha: 1)
}

@MainActor
enum DrivestBrand {
    private static let pageGradientLayerName = "drivest.page.gradient"

    static func logoImage() -> UIImage? {
        UIImage(named: "drivest_logo_tight")
    }

    static func logoImageView(contentMode: UIView.ContentMode = .scaleAspectFit) -> UIImageView {
        let imageView = UIImageView(image: logoImage())
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = contentMode
        return imageView
    }

    static func ensurePageGradient(in view: UIView) {
        let gradientLayer: CAGradientLayer
        if let existing = view.layer.sublayers?.first(where: { $0.name == pageGradientLayerName }) as? CAGradientLayer {
            gradientLayer = existing
        } else {
            gradientLayer = CAGradientLayer()
            gradientLayer.name = pageGradientLayerName
            gradientLayer.colors = [
                DrivestPalette.pageGradientTop.cgColor,
                DrivestPalette.pageGradientBottom.cgColor
            ]
            gradientLayer.startPoint = CGPoint(x: 0.5, y: 0.0)
            gradientLayer.endPoint = CGPoint(x: 0.5, y: 1.0)
            view.layer.insertSublayer(gradientLayer, at: 0)
        }
        gradientLayer.frame = view.bounds
    }

    static func stylePrimaryButton(_ button: UIButton) {
        button.tintColor = .white
        guard var configuration = button.configuration else { return }
        configuration.baseForegroundColor = .white
        configuration.baseBackgroundColor = DrivestPalette.accentPrimary
        configuration.cornerStyle = .large
        configuration.background.strokeWidth = 0
        configuration.background.cornerRadius = 16
        button.configuration = configuration
    }

    static func styleOutlinedButton(_ button: UIButton) {
        guard var configuration = button.configuration else { return }
        configuration.baseForegroundColor = DrivestPalette.accentPrimary
        configuration.cornerStyle = .large
        configuration.background.backgroundColor = UIColor.white.withAlphaComponent(0.9)
        configuration.background.strokeColor = DrivestPalette.accentPrimary.withAlphaComponent(0.45)
        configuration.background.strokeWidth = 1
        configuration.background.cornerRadius = 16
        button.configuration = configuration
    }

    static func styleSegmentedControl(_ control: UISegmentedControl) {
        control.selectedSegmentTintColor = DrivestPalette.accentPrimary
        control.backgroundColor = UIColor.white.withAlphaComponent(0.9)
        control.setTitleTextAttributes([.foregroundColor: DrivestPalette.textPrimary], for: .normal)
        control.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
    }
}

extension UIView {
    @MainActor
    func applyCardStyle(cornerRadius: CGFloat = 16, borderColor: UIColor? = nil) {
        backgroundColor = DrivestPalette.cardBackground
        layer.cornerRadius = cornerRadius
        layer.borderColor = (borderColor ?? DrivestPalette.cardStroke).cgColor
        layer.borderWidth = 1
    }
}
