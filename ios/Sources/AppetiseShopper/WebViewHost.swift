import SafariServices
import UIKit
import WebKit

/// Owns the one warm WKWebView (D9): loads the hosted page, carries the bridge, and keeps every
/// non-Appetise navigation out of it (D3, D7).
@MainActor
final class WebViewHost: NSObject {
    /// Stable, so the widget's storage (its store selection) survives launches (D13), and kept
    /// apart from any future supermarket WebView's (D6).
    private static let dataStoreID = UUID(uuidString: "6E1D5C2A-2E0B-4E4C-9D4F-3B7A0C8E1F52")!

    let webView: WKWebView
    var onMessage: ((Bridge.PageMessage) -> Void)?
    var onTerminated: (() -> Void)?

    private let configuration: AppetiseConfiguration
    private let allowedHosts: Set<String>
    /// From `link_hint`: web URLs native should first try as the supermarket app's deep link.
    private var appURLHints: [URL: URL] = [:]
    private var loaded = false

    init(configuration: AppetiseConfiguration) {
        self.configuration = configuration
        self.allowedHosts = Self.hosts(for: configuration)

        let config = WKWebViewConfiguration()
        if #available(iOS 17.0, *) {
            config.websiteDataStore = WKWebsiteDataStore(forIdentifier: Self.dataStoreID)
        } else {
            config.websiteDataStore = .default()
        }
        config.allowsInlineMediaPlayback = true
        config.dataDetectorTypes = []

        webView = WKWebView(frame: .zero, configuration: config)
        super.init()

        config.userContentController.add(WeakMessageHandler(self), name: Bridge.handlerName)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.backgroundColor = .white
        webView.isOpaque = true
        webView.scrollView.bounces = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.allowsLinkPreview = false
        if #available(iOS 16.4, *) {
            webView.isInspectable = configuration.debug
        }
    }

    func loadIfNeeded() {
        guard !loaded, let url = Bridge.pageURL(for: configuration, idiom: UIDevice.current.userInterfaceIdiom) else {
            return
        }
        loaded = true
        webView.load(URLRequest(url: url))
    }

    func send(_ message: [String: Any]) {
        guard let script = Bridge.script(for: message) else { return }
        webView.evaluateJavaScript(script) { [configuration] _, error in
            if let error, configuration.debug {
                print("[Appetise] Bridge send failed: \(error.localizedDescription)")
            }
        }
    }

    func rememberAppURL(_ appURL: URL, for url: URL) {
        appURLHints[url] = appURL
    }

    /// D7: the supermarket's app first, then an in-app browser.
    private func openExternally(_ url: URL) {
        guard let appURL = appURLHints[url] else {
            Self.openInBrowser(url)
            return
        }

        UIApplication.shared.open(appURL, options: [:]) { opened in
            if !opened {
                Self.openInBrowser(url)
            }
        }
    }

    private static func openInBrowser(_ url: URL) {
        guard let presenter = UIApplication.shared.topViewController else {
            UIApplication.shared.open(url)
            return
        }
        presenter.present(SFSafariViewController(url: url), animated: true)
    }

    private static func hosts(for configuration: AppetiseConfiguration) -> Set<String> {
        var hosts: Set<String> = []
        if let host = configuration.hostedPage.host?.lowercased() { hosts.insert(host) }
        if let host = configuration.apiBase?.host?.lowercased() { hosts.insert(host) }
        return hosts
    }

    private func isAppetise(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased() else { return false }
        if scheme == "about" || scheme == "data" || scheme == "blob" { return true }
        guard let host = url.host?.lowercased() else { return false }
        return allowedHosts.contains(host) || host.hasSuffix(".appetise.io")
    }
}

extension WebViewHost: WKNavigationDelegate, WKUIDelegate {
    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }

        if isAppetise(url) {
            decisionHandler(.allow)
            return
        }

        decisionHandler(.cancel)
        openExternally(url)
    }

    /// `target="_blank"`, including the checkout frame's "view cart" link.
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if let url = navigationAction.request.url, !isAppetise(url) {
            openExternally(url)
        }
        return nil
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        loaded = false
        onTerminated?()
    }
}

extension WebViewHost: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == Bridge.handlerName else { return }

        guard let parsed = Bridge.read(message.body) else {
            if configuration.debug { print("[Appetise] Ignored an off-protocol message: \(message.body)") }
            return
        }
        onMessage?(parsed)
    }
}

/// WKUserContentController retains its handlers; this keeps it from retaining the host.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?

    init(_ target: WKScriptMessageHandler) {
        self.target = target
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(controller, didReceive: message)
    }
}

extension UIApplication {
    var topViewController: UIViewController? {
        let window = connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
