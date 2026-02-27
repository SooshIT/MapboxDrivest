import UIKit
import MapboxMaps

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.titleTextAttributes = [.foregroundColor: DrivestPalette.textPrimary]
        appearance.largeTitleTextAttributes = [.foregroundColor: DrivestPalette.textPrimary]
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().tintColor = DrivestPalette.accentPrimary

        if let rawToken = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String {
            let trimmedToken = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmedToken.hasPrefix("pk.") {
                MapboxOptions.accessToken = trimmedToken
                print("[Drivest iOS] mapbox_token_loaded prefix=\(trimmedToken.prefix(12))")
            } else {
                print("[Drivest iOS] mapbox_token_invalid_or_missing_pk_prefix")
            }
        } else {
            print("[Drivest iOS] mapbox_token_missing_in_info_plist")
        }
        return true
    }
}
