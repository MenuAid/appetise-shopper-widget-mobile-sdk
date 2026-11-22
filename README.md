# Appetise Shopper — mobile SDKs

The shopper widget in partner apps, as a native sheet around the web widget. Design and decisions:
`appetise-shopper-widget/.ai/specs/mobile-sdk.md`. The bridge protocol is defined in that repo
(`src/native-host/protocol.ts`) and published as `m/v1/bridge-protocol.v1.schema.json`.

| Directory | What | Status |
| --- | --- | --- |
| `ios/` | Swift package `AppetiseShopper` (iOS 15+) | Core working on the simulator |
| `android/sdk` | Kotlin library `io.appetise.shopper` (API 24+) | Core working on the emulator |
| `android/example`, `ios/Example` | A recipe screen with a Shop button gated on readiness | Spike-level |
| `react-native/`, `flutter/` | Wrappers over the two cores (D1) | Not started |

## API

```swift
Appetise.configure(AppetiseConfiguration(partner: "9b2f6c1e-…"))
Appetise.preload()                                  // when a screen with the button appears
let ready = Appetise.onReadinessChange { ready in button.isHidden = !ready }   // keep the handle
Appetise.open([.raw("100g butter"), .raw("2 eggs")], externalRef: "list-42")
Appetise.openRecipe(.params(name: "Pasta", ingredients: [...], externalRef: "post-123"))
let opened = Appetise.on("embed_opened") { payload in ... }
Appetise.close()
```

Kotlin is the same, with an `Activity` passed to `open` and a `Context` to `configure`.

## Running the examples locally

Both load the hosted page from the widget's dev server and call a local appetise-web. The partner
must allow the app: locally `local-dev` allows `localhost` by domain, so any app ID works.

1. In `appetise-shopper-widget`: `npm run dev -- --port 5199 --strictPort`.
2. **iOS**: boot a simulator, trust Herd's CA in it once
   (`xcrun simctl keychain booted add-root-cert ~/Library/Application\ Support/Herd/config/valet/CA/LaravelValetCASelfSigned.pem`),
   then:
   ```bash
   APPETISE_HOSTED_PAGE=http://localhost:5199/m/v1/index.html APPETISE_API_BASE=https://appetise.test \
     ios/Example/run-simulator.sh
   ```
   It builds with `swiftc`, so it needs no Xcode project. `APPETISE_AUTO_OPEN=1` opens the sheet
   once ready, since a simulator can't be tapped from a script.
3. **Android**: the emulator can't resolve `*.test` or trust Herd's certificate, so put an HTTP
   proxy to `https://appetise.test` on host port 8787, then:
   ```bash
   cd android && ./gradlew :example:installDebug
   adb reverse tcp:5199 tcp:5199 && adb reverse tcp:8787 tcp:8787
   adb shell am start -n com.appetise.tester/io.appetise.example.MainActivity \
     --es hostedPage http://localhost:5199/m/v1/index.html --es apiBase http://localhost:8787 --ez autoOpen true
   ```

## Tests

- Android: `cd android && ./gradlew :sdk:testDebugUnitTest`.
- iOS: none yet. `xcodebuild` on this package needs the iOS platform matching the installed SDK.
