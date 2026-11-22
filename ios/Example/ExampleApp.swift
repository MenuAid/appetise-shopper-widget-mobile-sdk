import SwiftUI
import AppetiseShopper

// The example app: a recipe screen with a Shop button gated on readiness, as a partner would
// build it. For local development, set APPETISE_HOSTED_PAGE and APPETISE_API_BASE.
@main
struct ExampleApp: App {
    init() {
        let env = ProcessInfo.processInfo.environment
        Appetise.configure(AppetiseConfiguration(
            partner: env["APPETISE_PARTNER"] ?? "local-dev",
            countryCode: "NZ",
            apiBase: env["APPETISE_API_BASE"].flatMap(URL.init(string:)),
            hostedPage: env["APPETISE_HOSTED_PAGE"].flatMap(URL.init(string:))
                ?? URL(string: "https://embed.appetise.io/m/v1/index.html")!,
            debug: true
        ))
    }

    var body: some Scene {
        WindowGroup { RecipeScreen() }
    }
}

@MainActor
final class Readiness: ObservableObject {
    @Published var ready = Appetise.isReady
    @Published var events: [String] = []
    private var subscriptions: [AppetiseSubscription] = []

    init() {
        subscriptions.append(Appetise.onReadinessChange { [weak self] ready in
            self?.ready = ready
            // Dev only: lets a script open the sheet on a simulator, which can't tap.
            if ready, ProcessInfo.processInfo.environment["APPETISE_AUTO_OPEN"] == "1" {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                    Appetise.open([.raw("400g spaghetti"), .raw("50g butter"), .raw("300ml cream")])
                }
            }
        })
        for name in ["embed_opened", "embed_error", "embed_basket_viewed", "embed_checkout_started"] {
            subscriptions.append(Appetise.on(name) { [weak self] _ in self?.events.append(name) })
        }
    }
}

struct RecipeScreen: View {
    @StateObject private var readiness = Readiness()

    var body: some View {
        NavigationView {
            List {
                Section("Creamy mushroom pasta") {
                    Text("400g spaghetti")
                    Text("50g butter")
                    Text("250g chestnut mushrooms, sliced")
                    Text("300ml cream")
                }
                Section {
                    if readiness.ready {
                        Button("Shop ingredients") {
                            Appetise.open([
                                .raw("400g spaghetti"),
                                .raw("50g butter"),
                                .raw("250g chestnut mushrooms, sliced"),
                                .raw("300ml cream"),
                            ])
                        }
                        .accessibilityIdentifier("shop")
                        Button("Shop as a recipe") {
                            Appetise.openRecipe(.params(
                                name: "Creamy mushroom pasta",
                                ingredients: [.raw("400g spaghetti"), .raw("300ml cream")],
                                externalRef: "example-recipe-1"
                            ))
                        }
                    } else {
                        Text(Appetise.notReadyReason.map { "Not ready: \($0.rawValue)" } ?? "Checking…")
                            .foregroundColor(.secondary)
                    }
                }
                if !readiness.events.isEmpty {
                    Section("Events") {
                        ForEach(Array(readiness.events.enumerated()), id: \.offset) { Text($0.element).font(.caption) }
                    }
                }
            }
            .navigationTitle("Recipes")
        }
    }
}
