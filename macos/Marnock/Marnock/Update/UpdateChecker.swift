import Foundation

struct AppUpdateInfo: Equatable {
    let version: String
    let downloadURL: URL
    let notes: String
    let htmlURL: URL?
}

enum UpdateChecker {
    static let repo = "stephanevdb/marnock"
    static let assetName = "Marnock-macos.zip"

    static var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
    }

    static func checkLatest() async -> AppUpdateInfo? {
        guard let url = URL(string: "https://api.github.com/repos/\(repo)/releases/latest") else {
            return nil
        }
        var req = URLRequest(url: url)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("Marnock-macOS", forHTTPHeaderField: "User-Agent")
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }
            let tag = ((json["tag_name"] as? String) ?? "").trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
            guard SemVer.isNewer(tag, than: currentVersion) else { return nil }
            guard let assets = json["assets"] as? [[String: Any]] else { return nil }
            guard let asset = assets.first(where: { ($0["name"] as? String) == assetName }),
                  let dl = asset["browser_download_url"] as? String,
                  let downloadURL = URL(string: dl) else { return nil }
            let notes = json["body"] as? String ?? ""
            let html = (json["html_url"] as? String).flatMap(URL.init(string:))
            return AppUpdateInfo(version: tag, downloadURL: downloadURL, notes: notes, htmlURL: html)
        } catch {
            return nil
        }
    }
}
