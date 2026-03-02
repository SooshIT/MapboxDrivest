import UIKit

enum DrivestTheme {
    enum Colors {
        static let background = UIColor.systemBackground
        static let surface = UIColor.secondarySystemBackground
        static let elevatedSurface = UIColor.tertiarySystemBackground
        static let textPrimary = UIColor.label
        static let textSecondary = UIColor.secondaryLabel
        static let textTertiary = UIColor.tertiaryLabel
        static let textOnDark = UIColor.white
        static let accent = UIColor.systemBlue
        static let accentMuted = UIColor.systemBlue.withAlphaComponent(0.15)
        static let success = UIColor.systemGreen
        static let warning = UIColor.systemOrange
        static let danger = UIColor.systemRed
        static let separator = UIColor.separator
        static let promptBanner = UIColor(red: 0.06, green: 0.10, blue: 0.19, alpha: 0.95)
    }

    enum Spacing {
        static let xxs: CGFloat = 4
        static let xs: CGFloat = 8
        static let sm: CGFloat = 12
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
        static let xxl: CGFloat = 48
    }

    enum CornerRadius {
        static let sm: CGFloat = 8
        static let md: CGFloat = 10
        static let lg: CGFloat = 16
        static let pill: CGFloat = 999
    }

    enum Fonts {
        static let titleLarge = UIFont.systemFont(ofSize: 28, weight: .semibold)
        static let title = UIFont.systemFont(ofSize: 22, weight: .semibold)
        static let headline = UIFont.systemFont(ofSize: 17, weight: .semibold)
        static let body = UIFont.systemFont(ofSize: 17, weight: .regular)
        static let bodySmall = UIFont.systemFont(ofSize: 14, weight: .regular)
        static let bodySmallEmphasis = UIFont.systemFont(ofSize: 14, weight: .semibold)
        static let caption = UIFont.systemFont(ofSize: 12, weight: .regular)
        static let button = UIFont.systemFont(ofSize: 18, weight: .semibold)
        static let monospace = UIFont.monospacedSystemFont(ofSize: 13, weight: .regular)
    }
}
