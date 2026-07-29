// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TryZonAI",
    platforms: [
        .macOS(.v13),
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "TryZonAI",
            targets: ["TryZonAI"]
        )
    ],
    targets: [
        .target(
            name: "TryZonAI",
            path: "TryZonAI"
        )
    ]
)
