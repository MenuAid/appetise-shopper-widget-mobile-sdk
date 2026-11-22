import UIKit

/// The Appetise shopper widget, as a native sheet. Names follow the web SDK's `window.Appetise`
/// (mobile-sdk D11): `configure`, `open` for `render`, `openRecipe` for `renderRecipe`, `close`
/// for `destroy`, and `isReady` for `ready()`.
@MainActor
public enum Appetise {
    /// Cheap: no WebView yet. Call once, early. A new configuration drops any warm page.
    public static func configure(_ configuration: AppetiseConfiguration) {
        Core.shared.configure(configuration)
    }

    /// Warms the page and asks the server whether the widget may draw. Call when a screen that
    /// might show the button appears. Subscribing to readiness does this too.
    public static func preload() {
        Core.shared.preload()
    }

    /// True only when `open` right now would draw. Gate the button on it.
    public static var isReady: Bool { Core.shared.isReady }

    /// Why not, once the page has answered.
    public static var notReadyReason: AppetiseNotReadyReason? { Core.shared.notReadyReason }

    public static func onReadinessChange(_ handler: @escaping (Bool) -> Void) -> AppetiseSubscription {
        Core.shared.onReadinessChange(handler)
    }

    /// The web SDK's public events, e.g. `embed_opened`, with their payloads.
    public static func on(_ event: String, _ handler: @escaping ([String: Any]) -> Void) -> AppetiseSubscription {
        Core.shared.on(event, handler)
    }

    /// `externalRef` is the partner's own reference for this list, returned unchanged on its session.
    public static func open(_ items: [AppetiseIngredient], externalRef: String? = nil, from presenter: UIViewController? = nil) {
        Core.shared.open(Bridge.open(items, externalRef: externalRef), from: presenter)
    }

    public static func openRecipe(_ recipe: AppetiseRecipe, from presenter: UIViewController? = nil) {
        Core.shared.open(Bridge.openRecipe(recipe), from: presenter)
    }

    /// Ends the shop and dismisses the sheet.
    public static func close() {
        Core.shared.close()
    }
}

@MainActor
final class Core {
    static let shared = Core()

    private(set) var isReady = false
    private(set) var notReadyReason: AppetiseNotReadyReason?

    private var configuration: AppetiseConfiguration?
    private var host: WebViewHost?
    private var sheet: SheetController?
    /// The open waiting for the page's first readiness answer.
    private var pendingOpen: [String: Any]?
    /// Whether the current page has answered; `isReady` keeps the last answer across reloads.
    private var answered = false
    private var readinessHandlers: [UUID: (Bool) -> Void] = [:]
    private var eventHandlers: [String: [UUID: ([String: Any]) -> Void]] = [:]

    private init() {
        let center = NotificationCenter.default
        // Block observers stay registered for the process; Core is never released.
        _ = center.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { Core.shared.host?.send(Bridge.foreground()) }
        }
        _ = center.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { Core.shared.dropIfIdle() }
        }
    }

    func configure(_ configuration: AppetiseConfiguration) {
        guard configuration != self.configuration else { return }
        self.configuration = configuration
        if sheet == nil {
            tearDown()
        }
    }

    func preload() {
        warmHost()?.loadIfNeeded()
    }

    func onReadinessChange(_ handler: @escaping (Bool) -> Void) -> AppetiseSubscription {
        let id = UUID()
        readinessHandlers[id] = handler
        preload()
        return AppetiseSubscription { [weak self] in
            MainActor.assumeIsolated { _ = self?.readinessHandlers.removeValue(forKey: id) }
        }
    }

    func on(_ event: String, _ handler: @escaping ([String: Any]) -> Void) -> AppetiseSubscription {
        let id = UUID()
        eventHandlers[event, default: [:]][id] = handler
        return AppetiseSubscription { [weak self] in
            MainActor.assumeIsolated { _ = self?.eventHandlers[event]?.removeValue(forKey: id) }
        }
    }

    func open(_ message: [String: Any], from presenter: UIViewController?) {
        guard let host = warmHost() else {
            log("open() before configure(). Nothing will draw.")
            return
        }

        // D9: a refusal does nothing; the page has already emitted its reason.
        if answered && !isReady {
            log("open() ignored: \(notReadyReason?.rawValue ?? "not ready")")
            return
        }

        host.loadIfNeeded()
        present(host, from: presenter)

        if answered {
            host.send(message)
        } else {
            pendingOpen = message
        }
    }

    func close() {
        host?.send(Bridge.close())
        dismissSheet()
    }

    private func dismissSheet(animated: Bool = true) {
        sheet?.dismiss(animated: animated)
        sheet = nil
    }

    private func warmHost() -> WebViewHost? {
        if let host { return host }
        guard let configuration else { return nil }

        let host = WebViewHost(configuration: configuration)
        host.onMessage = { [weak self] message in self?.handle(message) }
        host.onTerminated = { [weak self] in self?.terminated() }
        self.host = host
        return host
    }

    private func present(_ host: WebViewHost, from presenter: UIViewController?) {
        guard sheet == nil, let presenter = presenter ?? UIApplication.shared.topViewController else { return }

        let sheet = SheetController(webView: host.webView)
        sheet.onSwipeDismissed = { [weak self] in
            self?.host?.send(Bridge.closeRequested(via: "dismiss"))
            self?.sheet = nil
        }
        self.sheet = sheet
        presenter.present(sheet, animated: true)
    }

    private func handle(_ message: Bridge.PageMessage) {
        switch message {
        case .readiness(let ready, let reason):
            let changed = ready != isReady || !answered
            answered = true
            isReady = ready
            notReadyReason = ready ? nil : reason
            if changed {
                readinessHandlers.values.forEach { $0(ready) }
            }
            flushPendingOpen()
        case .closable(let closable):
            sheet?.setClosable(closable)
            if closable { sheet?.reveal() }
        case .closed:
            dismissSheet()
        case .linkHint(let url, let appURL):
            host?.rememberAppURL(appURL, for: url)
        case .event(let name, let payload):
            eventHandlers[name]?.values.forEach { $0(payload) }
        }
    }

    private func flushPendingOpen() {
        guard let message = pendingOpen else { return }
        pendingOpen = nil

        if isReady {
            host?.send(message)
        } else {
            dismissSheet()
        }
    }

    /// The OS killed the page. The next preload or open loads it again.
    private func terminated() {
        answered = false
        dismissSheet(animated: false)
    }

    private func dropIfIdle() {
        guard sheet == nil else { return }
        tearDown()
    }

    private func tearDown() {
        host = nil
        answered = false
        pendingOpen = nil
    }

    private func log(_ message: String) {
        if configuration?.debug == true {
            print("[Appetise] \(message)")
        }
    }
}
