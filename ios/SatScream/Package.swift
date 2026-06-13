// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "SatScream",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "SatScream", targets: ["SatScream"]),
        .library(name: "SatScreamWidget", targets: ["SatScreamWidget"])
    ],
    targets: [
        .target(
            name: "SatScream",
            path: "Sources/SatScream",
            resources: [
                .process("Resources")
            ]
        ),
        .target(
            name: "SatScreamWidget",
            path: "Sources/SatScreamWidget",
            exclude: ["Info.plist"],
            swiftSettings: [
                .unsafeFlags(["-framework", "WidgetKit", "-framework", "SwiftUI"])
            ]
        )
    ]
)
