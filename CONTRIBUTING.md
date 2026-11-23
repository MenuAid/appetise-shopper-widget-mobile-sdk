# Contributing

The SDKs are thin native hosts around a page Appetise serves (`hostedPage`), which runs the web
widget and talks to the app over a versioned JSON bridge. The bridge protocol lives with the web
widget and is published as `m/v1/bridge-protocol.v1.schema.json` next to the hosted page.

`AppetiseConfiguration`'s `hostedPage` and `apiBase` point the SDK at a local or staging page and
API; partners never set them.

## Tests

- Android: `cd android && ./gradlew :sdk:testDebugUnitTest`.
- iOS: CI builds the package through the root `Package.swift`
  (`xcodebuild -scheme AppetiseShopper -destination 'generic/platform=iOS Simulator' build`), which
  needs the iOS platform matching the installed Xcode.

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

