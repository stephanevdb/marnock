import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject var model: AppModel
    @State private var replyText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(
                title: "Notifications",
                subtitle: "Mirrored alerts from your phone"
            )

            if model.notificationsSuppressed {
                Text("Quiet hours active — mirrors paused.")
                    .foregroundStyle(.orange)
            }
            if model.notifications.isEmpty {
                Text("Mirrored phone notifications appear here.")
                    .foregroundStyle(.secondary)
                Spacer()
            } else {
                List(model.notifications) { n in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(n.title.isEmpty ? n.packageName : n.title).font(.headline)
                        Text(n.text)
                        Text(n.packageName).font(.caption).foregroundStyle(.secondary)
                        HStack {
                            ForEach(n.actions) { action in
                                Button(action.title) {
                                    if action.allowsReply {
                                        model.invokeNotificationAction(key: n.id, actionId: action.id, reply: replyText)
                                    } else {
                                        model.invokeNotificationAction(key: n.id, actionId: action.id, reply: nil)
                                    }
                                }
                            }
                            Button("Block app") {
                                model.deniedNotificationPackages.insert(n.packageName)
                            }
                        }
                        if n.actions.contains(where: \.allowsReply) {
                            TextField("Reply text", text: $replyText)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .padding(16)
    }
}
