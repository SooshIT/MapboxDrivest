import Foundation
import StoreKit

enum AppleSubscriptionProductId: String, CaseIterable {
    case practiceMonthly = "drivest_practice_monthly"
    case globalAnnual = "drivest_global_annual"
}

struct AppleBillingProduct {
    let productId: String
    let title: String
    let displayPrice: String
}

enum AppleBillingPurchaseResult {
    case success
    case cancelled
    case failed(String)
}

enum AppleBillingRestoreResult {
    case restored(Bool)
    case failed(String)
}

protocol AppleBillingFacade {
    func queryProducts() async -> [AppleBillingProduct]
    func purchase(productId: String) async -> AppleBillingPurchaseResult
    func restorePurchases() async -> AppleBillingRestoreResult
}

final class StoreKitBillingScaffold: AppleBillingFacade {
    // Billing integration on iOS is scaffolded only in this epic.
    // Full StoreKit purchase validation/wiring lands in a dedicated iOS billing epic.
    func queryProducts() async -> [AppleBillingProduct] {
        guard #available(iOS 15.0, *) else {
            return []
        }
        do {
            let ids = AppleSubscriptionProductId.allCases.map(\.rawValue)
            let products = try await Product.products(for: ids)
            return products.map { product in
                AppleBillingProduct(
                    productId: product.id,
                    title: product.displayName,
                    displayPrice: product.displayPrice
                )
            }
        } catch {
            return []
        }
    }

    func purchase(productId: String) async -> AppleBillingPurchaseResult {
        return .failed("StoreKit purchase wiring not enabled in this build.")
    }

    func restorePurchases() async -> AppleBillingRestoreResult {
        return .failed("StoreKit restore wiring not enabled in this build.")
    }
}
