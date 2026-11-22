import UIKit
import WebKit

/// The native sheet (D8): a large-detent page sheet around the warm WebView. The page keeps its own
/// header; this only adds the native gestures and a loading cover until the widget has drawn.
@MainActor
final class SheetController: UIViewController {
    var onSwipeDismissed: (() -> Void)?

    private let webView: WKWebView
    private let cover = UIView()
    private let spinner = UIActivityIndicatorView(style: .large)
    private static let grabberClearance: CGFloat = 14

    init(webView: WKWebView) {
        self.webView = webView
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .pageSheet
        if let sheet = sheetPresentationController {
            sheet.detents = [.large()]
            sheet.prefersGrabberVisible = true
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        presentationController?.delegate = self

        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)

        cover.backgroundColor = .white
        cover.translatesAutoresizingMaskIntoConstraints = false
        spinner.translatesAutoresizingMaskIntoConstraints = false
        spinner.startAnimating()
        cover.addSubview(spinner)
        view.addSubview(cover)

        NSLayoutConstraint.activate([
            // Clears the sheet's grabber, which would otherwise sit on the step indicator.
            webView.topAnchor.constraint(equalTo: view.topAnchor, constant: Self.grabberClearance),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            cover.topAnchor.constraint(equalTo: view.topAnchor),
            cover.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            cover.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            cover.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            spinner.centerXAnchor.constraint(equalTo: cover.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: cover.centerYAnchor),
        ])
    }

    /// The widget has drawn: its first `closable` message arrives as the modal opens.
    func reveal() {
        guard !cover.isHidden else { return }
        UIView.animate(withDuration: 0.15, animations: { self.cover.alpha = 0 }) { _ in
            self.cover.isHidden = true
        }
    }

    /// While a push is running the page says it can't be closed, so the swipe is blocked.
    func setClosable(_ closable: Bool) {
        isModalInPresentation = !closable
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        webView.removeFromSuperview()
    }
}

extension SheetController: UIAdaptivePresentationControllerDelegate {
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        onSwipeDismissed?()
    }
}
