// swift-tools-version:5.9

import PackageDescription

let package = Package(
    name: "tauri-plugin-health",
    platforms: [
        // Matches Tauri's default iOS deployment target — consumers build
        // this package at *their* app's target, so the sources avoid all
        // iOS-15+ conveniences (see HealthMapping's type lookups).
        .iOS(.v13),
        // SwiftPM resolution consistency with the Tauri package's macOS
        // declaration; the target is only ever compiled into iOS builds.
        .macOS(.v12),
    ],
    products: [
        .library(
            name: "tauri-plugin-health",
            type: .static,
            targets: ["tauri-plugin-health"]),
    ],
    dependencies: [
        // Tauri runtime injected as a sibling local package by the Tauri CLI
        // when the consumer runs `tauri ios init` / `tauri ios dev`.
        .package(name: "Tauri", path: "../.tauri/tauri-api"),
    ],
    targets: [
        .target(
            name: "tauri-plugin-health",
            dependencies: [
                .byName(name: "Tauri"),
            ],
            path: "Sources"),
    ]
)
