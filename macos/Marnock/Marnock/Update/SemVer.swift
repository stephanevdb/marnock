import Foundation

enum SemVer {
    /// True when `remote` is a newer release than `local`.
    static func isNewer(_ remote: String, than local: String) -> Bool {
        let r = parse(remote)
        let l = parse(local)
        let n = max(r.count, l.count)
        for i in 0..<n {
            let a = i < r.count ? r[i] : 0
            let b = i < l.count ? l[i] : 0
            if a != b { return a > b }
        }
        return false
    }

    private static func parse(_ raw: String) -> [Int] {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("v") || s.hasPrefix("V") { s.removeFirst() }
        if let dash = s.firstIndex(of: "-") { s = String(s[..<dash]) }
        if let plus = s.firstIndex(of: "+") { s = String(s[..<plus]) }
        if s.contains("dev") { return [0, 0, 0] }
        return s.split(separator: ".").map { Int($0) ?? 0 }
    }
}
