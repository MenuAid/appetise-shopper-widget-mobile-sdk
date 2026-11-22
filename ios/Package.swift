// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AppetiseShopper",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "AppetiseShopper", targets: ["AppetiseShopper"]),
    ],
    targets: [
        .target(
            name: "AppetiseShopper",
            resources: [.copy("Resources/PrivacyInfo.xcprivacy")]
        ),
        .testTarget(name: "AppetiseShopperTests", dependencies: ["AppetiseShopper"]),
    ]
)
