import SwiftUI

struct ConnectionStatusBadge: View {
    let path: ConnectionPath

    private var label: String {
        switch path {
        case .lan: return "LAN"
        case .relay: return "Relay"
        case .offline: return "Offline"
        }
    }

    private var tint: Color {
        switch path {
        case .lan: return .green
        case .relay: return .blue
        case .offline: return .secondary
        }
    }

    var body: some View {
        Text(label)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(tint.opacity(0.18), in: Capsule())
            .foregroundStyle(tint == .secondary ? Color.secondary : tint)
    }
}
