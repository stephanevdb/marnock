import AppKit
import SwiftUI

struct DeviceStatusHeader: View {
    let deviceName: String
    let path: ConnectionPath
    let battery: Int
    let charging: Bool
    let wallpaper: NSImage?
    var onClose: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            wallpaperTile
            VStack(alignment: .leading, spacing: 4) {
                Text(deviceName)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                HStack(spacing: 10) {
                    HStack(spacing: 5) {
                        Circle()
                            .fill(statusColor)
                            .frame(width: 6, height: 6)
                        Text(statusLabel)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.62))
                    }
                    if battery >= 0 {
                        HStack(spacing: 4) {
                            Image(systemName: batterySymbol)
                                .foregroundStyle(batteryColor)
                                .imageScale(.small)
                            Text("\(battery)%")
                                .font(.caption.weight(.medium).monospacedDigit())
                                .foregroundStyle(.white)
                        }
                    }
                }
            }
            Spacer(minLength: 8)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.85))
                    .frame(width: 28, height: 28)
                    .background(Color.white.opacity(0.12), in: Circle())
            }
            .buttonStyle(.plain)
            .help("Close")
        }
    }

    @ViewBuilder
    private var wallpaperTile: some View {
        Group {
            if let wallpaper {
                Image(nsImage: wallpaper)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: .fill)
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(statusColor.opacity(0.22))
                    Image(systemName: "iphone")
                        .font(.title3.weight(.medium))
                        .foregroundStyle(.white.opacity(0.9))
                }
            }
        }
        .frame(width: 44, height: 56)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(Color.white.opacity(0.16), lineWidth: 0.75)
        }
    }

    private var statusLabel: String {
        switch path {
        case .lan: return "Connected"
        case .relay: return "Relay"
        case .offline: return "Offline"
        }
    }

    private var statusColor: Color {
        switch path {
        case .lan: return .green
        case .relay: return .blue
        case .offline: return .gray
        }
    }

    private var batterySymbol: String {
        if charging { return "battery.100.bolt" }
        switch battery {
        case ..<13: return "battery.0"
        case ..<38: return "battery.25"
        case ..<63: return "battery.50"
        case ..<88: return "battery.75"
        default: return "battery.100"
        }
    }

    private var batteryColor: Color {
        if charging { return .green }
        if battery < 20 { return .red }
        return .green
    }
}
