// swift-tools-version: 5.9
import PackageDescription

// At the root because Swift Package Manager only reads a repository's root manifest.
let package = Package(
    name: "AppetiseShopper",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "AppetiseShopper", targets: ["AppetiseShopper"]),
    ],
    targets: [
        .target(
            name: "AppetiseShopper",
            path: "ios/Sources/AppetiseShopper",
            resources: [.copy("Resources/PrivacyInfo.xcprivacy")]
        ),
    ]
)
