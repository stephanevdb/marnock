// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Marnock",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "Marnock", targets: ["Marnock"])
    ],
    targets: [
        .executableTarget(
            name: "Marnock",
            path: "Marnock",
            exclude: ["Info.plist", "Marnock.entitlements", "Resources"],
            linkerSettings: [
                .linkedFramework("Contacts")
            ]
        )
    ]
)
