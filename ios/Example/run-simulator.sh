#!/usr/bin/env bash
# Builds the example app with swiftc (no Xcode project) and runs it on a booted simulator.
# Usage: ios/Example/run-simulator.sh [simulator-udid]
# Env: APPETISE_HOSTED_PAGE, APPETISE_API_BASE, APPETISE_PARTNER and APPETISE_AUTO_OPEN (opens the
# sheet once ready, since a simulator can't be tapped from a script) are passed to the app.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"
out="${TMPDIR:-/tmp}/appetise-example"
app="$out/AppetiseExample.app"
udid="${1:-booted}"
sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
target="arm64-apple-ios16.0-simulator"

rm -rf "$out" && mkdir -p "$app"

xcrun swiftc -parse-as-library -sdk "$sdk" -target "$target" -O \
  -module-name AppetiseShopper -emit-module -emit-module-path "$out/AppetiseShopper.swiftmodule" \
  -emit-library -static -o "$out/libAppetiseShopper.a" \
  "$root"/Sources/AppetiseShopper/*.swift

xcrun swiftc -parse-as-library -sdk "$sdk" -target "$target" -O \
  -I "$out" -L "$out" -lAppetiseShopper \
  -o "$app/AppetiseExample" "$here/ExampleApp.swift"

cp "$root/Sources/AppetiseShopper/Resources/PrivacyInfo.xcprivacy" "$app/"
cat > "$app/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleIdentifier</key><string>com.appetise.tester</string>
  <key>CFBundleName</key><string>Appetise Example</string>
  <key>CFBundleExecutable</key><string>AppetiseExample</string>
  <key>CFBundleShortVersionString</key><string>1.0.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>UILaunchScreen</key><dict/>
  <key>UIApplicationSceneManifest</key><dict><key>UIApplicationSupportsMultipleScenes</key><false/></dict>
  <key>NSAppTransportSecurity</key><dict><key>NSAllowsLocalNetworking</key><true/></dict>
  <key>LSApplicationQueriesSchemes</key><array/>
</dict>
</plist>
PLIST
codesign --force --sign - "$app" >/dev/null

xcrun simctl install "$udid" "$app"
SIMCTL_CHILD_APPETISE_HOSTED_PAGE="${APPETISE_HOSTED_PAGE:-}" \
SIMCTL_CHILD_APPETISE_API_BASE="${APPETISE_API_BASE:-}" \
SIMCTL_CHILD_APPETISE_PARTNER="${APPETISE_PARTNER:-local-dev}" \
SIMCTL_CHILD_APPETISE_AUTO_OPEN="${APPETISE_AUTO_OPEN:-}" \
  xcrun simctl launch "$udid" com.appetise.tester
