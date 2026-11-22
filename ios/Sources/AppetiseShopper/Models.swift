import Foundation

/// How the partner's app sets the widget up. Only `partner` is required.
public struct AppetiseConfiguration: Equatable {
    /// The partner ID from Appetise Insights.
    public var partner: String
    /// `NZ` or `AU`. Worked out from the device's timezone when nil.
    public var countryCode: String?
    /// Overrides the API origin baked into the hosted page. For staging and local development.
    public var apiBase: URL?
    /// Overrides the hosted page. For staging and local development.
    public var hostedPage: URL
    /// Writes the SDK's and the page's lifecycle to the console, and makes the WebView inspectable.
    public var debug: Bool
    /// Set by the React Native and Flutter wrappers; leave as `native` otherwise.
    public var framework: String

    public init(
        partner: String,
        countryCode: String? = nil,
        apiBase: URL? = nil,
        hostedPage: URL = URL(string: "https://embed.appetise.io/m/v1/index.html")!,
        debug: Bool = false,
        framework: String = "native"
    ) {
        self.partner = partner
        self.countryCode = countryCode
        self.apiBase = apiBase
        self.hostedPage = hostedPage
        self.debug = debug
        self.framework = framework
    }
}

/// One line of a shopping list: either as the shopper would write it, or already split up.
public enum AppetiseIngredient: Equatable {
    case raw(String)
    case named(String, quantity: Double? = nil, unit: String? = nil)

    var json: [String: Any] {
        switch self {
        case .raw(let raw):
            return ["raw": raw]
        case .named(let name, let quantity, let unit):
            var line: [String: Any] = ["name": name]
            if let quantity { line["quantity"] = quantity }
            if let unit { line["unit"] = unit }
            return line
        }
    }
}

/// A recipe to shop. `externalRef` is required: in an app it is the recipe's only identity.
public enum AppetiseRecipe: Equatable {
    case params(name: String, ingredients: [AppetiseIngredient], externalRef: String, servings: Int? = nil, imageUrl: URL? = nil)
    case text(String, externalRef: String, name: String? = nil)

    var json: [String: Any] {
        switch self {
        case .params(let name, let ingredients, let externalRef, let servings, let imageUrl):
            var recipe: [String: Any] = ["name": name, "ingredients": ingredients.map(\.json), "externalRef": externalRef]
            if let servings { recipe["servings"] = servings }
            if let imageUrl { recipe["imageUrl"] = imageUrl.absoluteString }
            return recipe
        case .text(let text, let externalRef, let name):
            var recipe: [String: Any] = ["text": text, "externalRef": externalRef]
            if let name { recipe["name"] = name }
            return recipe
        }
    }
}

/// Why `open` would not draw right now. The same values as the web SDK's `ready()`.
public enum AppetiseNotReadyReason: String {
    case configUnresolved = "config_unresolved"
    case noCountry = "no_country"
    case disabled
    case partnerUnconfirmed = "partner_unconfirmed"
    case noStores = "no_stores"
}

/// Returned by `on` and `onReadinessChange`. Keep it: the handler is removed on `cancel()` or
/// when this is released, as with Combine's `AnyCancellable`.
public final class AppetiseSubscription {
    private var onCancel: (() -> Void)?

    init(onCancel: @escaping () -> Void) {
        self.onCancel = onCancel
    }

    public func cancel() {
        onCancel?()
        onCancel = nil
    }

    deinit {
        guard let onCancel else { return }
        if Thread.isMainThread {
            onCancel()
        } else {
            DispatchQueue.main.async(execute: onCancel)
        }
    }
}
