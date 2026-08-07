import AppKit
import Foundation

enum AppUpdaterError: LocalizedError {
    case notBundled
    case downloadFailed
    case unzipFailed

    var errorDescription: String? {
        switch self {
        case .notBundled: return "Run Marnock as a .app bundle to update."
        case .downloadFailed: return "Could not download the update."
        case .unzipFailed: return "Could not unpack the update archive."
        }
    }
}

enum AppUpdater {
    /// Download zip, replace the running .app via a short post-quit script, relaunch.
    static func downloadAndInstall(
        from url: URL,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws {
        let bundleURL = Bundle.main.bundleURL
        guard bundleURL.pathExtension == "app" else { throw AppUpdaterError.notBundled }

        let tempRoot = FileManager.default.temporaryDirectory
            .appendingPathComponent("MarnockUpdate-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempRoot, withIntermediateDirectories: true)

        progress(0.05)
        let (tempFile, response) = try await URLSession.shared.download(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw AppUpdaterError.downloadFailed
        }
        let zipURL = tempRoot.appendingPathComponent("Marnock-macos.zip")
        try? FileManager.default.removeItem(at: zipURL)
        try FileManager.default.moveItem(at: tempFile, to: zipURL)
        progress(0.6)

        let extractDir = tempRoot.appendingPathComponent("extract", isDirectory: true)
        try FileManager.default.createDirectory(at: extractDir, withIntermediateDirectories: true)
        let unzip = Process()
        unzip.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
        unzip.arguments = ["-x", "-k", zipURL.path, extractDir.path]
        try unzip.run()
        unzip.waitUntilExit()
        guard unzip.terminationStatus == 0 else { throw AppUpdaterError.unzipFailed }
        progress(0.8)

        guard let newApp = findApp(in: extractDir) else { throw AppUpdaterError.unzipFailed }

        let script = tempRoot.appendingPathComponent("install.sh")
        let dest = bundleURL.path
        let src = newApp.path
        let scriptBody = """
        #!/bin/bash
        set -euo pipefail
        sleep 1
        rm -rf "\(dest)"
        ditto "\(src)" "\(dest)"
        codesign --force --deep --sign - "\(dest)" 2>/dev/null || true
        open "\(dest)"
        rm -rf "\(tempRoot.path)"
        """
        try scriptBody.write(to: script, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: script.path)

        let installer = Process()
        installer.executableURL = script
        try installer.run()
        progress(1)

        await MainActor.run {
            NSApp.terminate(nil)
        }
    }

    private static func findApp(in dir: URL) -> URL? {
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(
            at: dir,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else { return nil }
        for case let url as URL in enumerator {
            if url.pathExtension == "app" { return url }
        }
        return nil
    }
}
