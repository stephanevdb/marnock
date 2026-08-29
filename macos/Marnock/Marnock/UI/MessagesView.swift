import SwiftUI

struct MessagesView: View {
    @EnvironmentObject var model: AppModel
    @State private var smsBody = ""
    @State private var composeAddress = ""

    var body: some View {
        HSplitView {
            VStack(alignment: .leading) {
                HStack {
                    SectionHeader(title: "Messages", subtitle: "SMS from your phone")
                    Spacer()
                    Button("Refresh") { model.refreshSmsThreads() }
                }
                .padding([.horizontal, .top])
                HStack {
                    TextField("New SMS to", text: $composeAddress)
                    ContactPickerButton(title: "Contacts") { composeAddress = $0 }
                    Button("Open") {
                        model.startConversation(address: composeAddress)
                    }
                    .disabled(composeAddress.isEmpty)
                }
                .padding(.horizontal)
                List(model.smsThreads, selection: Binding(
                    get: { model.selectedThreadId },
                    set: { if let id = $0 { model.openThread(id) } }
                )) { thread in
                    VStack(alignment: .leading) {
                        Text(thread.contactName.isEmpty ? thread.address : thread.contactName)
                            .font(.headline)
                        Text(thread.snippet).lineLimit(1).foregroundStyle(.secondary)
                    }
                    .tag(thread.id)
                }
            }
            .frame(minWidth: 240)

            VStack(alignment: .leading, spacing: 8) {
                if let tid = model.selectedThreadId,
                   let thread = model.smsThreads.first(where: { $0.id == tid }) {
                    Text(thread.contactName.isEmpty ? thread.address : thread.contactName)
                        .font(.title3)
                        .padding(.horizontal)
                        .padding(.top, 12)
                    List(model.smsMessages) { msg in
                        HStack {
                            if msg.type == "sent" { Spacer() }
                            Text(msg.body)
                                .padding(8)
                                .background(msg.type == "sent" ? Color.accentColor.opacity(0.2) : Color.primary.opacity(0.06))
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            if msg.type != "sent" { Spacer() }
                        }
                    }
                    HStack {
                        TextField("Message", text: $smsBody)
                        Button("Send") {
                            model.sendSms(address: thread.address, body: smsBody)
                            smsBody = ""
                        }
                        .disabled(smsBody.isEmpty)
                    }
                    .padding()
                } else {
                    Text("Select a conversation")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(minWidth: 320)
        }
        .padding(8)
        .onAppear {
            if model.smsThreads.isEmpty { model.refreshSmsThreads() }
        }
    }
}
