// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TryZonAI",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .executable(
            name: "TryZonAI",
            targets: ["TryZonAI"]
        )
    ],
    targets: [
        .executableTarget(
            name: "TryZonAI",
            path: "TryZonAI"
        )
    ]
)
