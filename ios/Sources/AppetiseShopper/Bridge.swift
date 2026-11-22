import Foundation
import UIKit

/// The native half of the bridge protocol (mobile-sdk D3, D14). Messages are checked against
/// the schema the widget publishes at `m/v1/bridge-protocol.v1.schema.json`.
enum Bridge {
    static let protocolVersion = 1
    static let handlerName = "appetiseHost"

    enum PageMessage: Equatable {
        case readiness(isReady: Bool, reason: AppetiseNotReadyReason?)
        case closable(Bool)
        case closed
        case linkHint(url: URL, appURL: URL)
        case event(name: String, payload: [String: AnyHashable])
    }

    /// What `appetiseHost.postMessage` delivers. Anything off-protocol is dropped.
    static func read(_ body: Any) -> PageMessage? {
        guard let message = body as? [String: Any],
              message["v"] as? Int == protocolVersion,
              let type = message["type"] as? String else {
            return nil
        }

        switch type {
        case "readiness":
            guard let isReady = message["isReady"] as? Bool else { return nil }
            return .readiness(isReady: isReady, reason: (message["notReadyReason"] as? String).flatMap(AppetiseNotReadyReason.init))
        case "closable":
            guard let closable = message["closable"] as? Bool else { return nil }
            return .closable(closable)
        case "closed":
            return .closed
        case "link_hint":
            guard let url = webURL(message["url"]),
                  let appURL = (message["appUrl"] as? String).flatMap(URL.init(string:)) else { return nil }
            return .linkHint(url: url, appURL: appURL)
        case "event":
            guard let name = message["event"] as? String else { return nil }
            return .event(name: name, payload: (message["payload"] as? [String: AnyHashable]) ?? [:])
        default:
            return nil
        }
    }

    static func open(_ items: [AppetiseIngredient], externalRef: String?) -> [String: Any] {
        var fields: [String: Any] = ["items": items.map(\.json)]
        if let externalRef { fields["externalRef"] = externalRef }
        return envelope("open", fields)
    }

    static func openRecipe(_ recipe: AppetiseRecipe) -> [String: Any] {
        envelope("open_recipe", ["recipe": recipe.json])
    }

    static func closeRequested(via: String) -> [String: Any] {
        envelope("close_requested", ["via": via])
    }

    static func close() -> [String: Any] { envelope("close") }

    static func foreground() -> [String: Any] { envelope("foreground") }

    private static func envelope(_ type: String, _ fields: [String: Any] = [:]) -> [String: Any] {
        fields.merging(["v": protocolVersion, "type": type]) { _, envelope in envelope }
    }

    /// The JavaScript that delivers one message to the page.
    static func script(for message: [String: Any]) -> String? {
        guard let data = try? JSONSerialization.data(withJSONObject: message),
              let json = String(data: data, encoding: .utf8) else {
            return nil
        }
        return "window.AppetiseNativeHost && window.AppetiseNativeHost.receive(\(json));"
    }

    /// `hostedPage#context=<URI-encoded JSON>`: everything the page needs before it boots.
    static func pageURL(for configuration: AppetiseConfiguration, bundle: Bundle = .main, idiom: UIUserInterfaceIdiom) -> URL? {
        var context: [String: Any] = [
            "protocol": protocolVersion,
            "partner": configuration.partner,
            "appId": bundle.bundleIdentifier ?? "unknown",
            "appVersion": (bundle.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0",
            "sdkPlatform": "ios",
            "sdkFramework": configuration.framework,
            "sdkVersion": AppetiseVersion.current,
            "deviceType": idiom == .pad ? "tablet" : "mobile",
        ]
        if let countryCode = configuration.countryCode { context["countryCode"] = countryCode }
        if let apiBase = configuration.apiBase { context["apiBase"] = apiBase.absoluteString }
        if configuration.debug { context["debug"] = true }

        guard let data = try? JSONSerialization.data(withJSONObject: context, options: [.sortedKeys]),
              let json = String(data: data, encoding: .utf8),
              let encoded = json.addingPercentEncoding(withAllowedCharacters: .alphanumerics) else {
            return nil
        }

        var components = URLComponents(url: configuration.hostedPage, resolvingAgainstBaseURL: false)
        components?.percentEncodedFragment = "context=\(encoded)"
        return components?.url
    }

    private static func webURL(_ value: Any?) -> URL? {
        guard let string = value as? String, let url = URL(string: string),
              let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http" else {
            return nil
        }
        return url
    }
}

public enum AppetiseVersion {
    public static let current = "0.1.0"
}
