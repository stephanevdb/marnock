import Foundation

enum Framing {
    static func encode(_ jsonUTF8: Data) -> Data {
        var length = UInt32(jsonUTF8.count).bigEndian
        var out = Data(bytes: &length, count: 4)
        out.append(jsonUTF8)
        return out
    }

    static func decode(_ frame: Data) -> Data {
        guard frame.count >= 4 else { return frame }
        let n = frame.prefix(4).withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
        if Int(n) + 4 == frame.count {
            return frame.dropFirst(4)
        }
        return frame
    }
}
