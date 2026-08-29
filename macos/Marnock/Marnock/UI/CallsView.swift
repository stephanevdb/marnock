import SwiftUI

struct CallsView: View {
    @EnvironmentObject var model: AppModel
    @State private var dialNumber = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionHeader(title: "Calls", subtitle: "Control dialing; audio stays on the phone")

            GroupBox("Live state") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("State: \(model.callState.state)")
                    if !model.callState.number.isEmpty {
                        Text("Number: \(model.callState.number)")
                    }
                    if !model.callState.name.isEmpty {
                        Text("Name: \(model.callState.name)")
                    }
                    if model.callState.state == "ringing" {
                        HStack {
                            Button("Answer") { model.answerCall() }
                                .buttonStyle(.borderedProminent)
                            Button("Reject", role: .destructive) { model.rejectCall() }
                        }
                    }
                    Text("Audio stays on the phone or its Bluetooth headset.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack {
                TextField("Dial number", text: $dialNumber)
                ContactPickerButton { dialNumber = $0.filter { $0.isNumber || $0 == "+" } }
                Button("Dial") { model.dial(dialNumber) }
                    .disabled(dialNumber.isEmpty)
                Button("Refresh history") { model.refreshCallHistory() }
            }

            List(model.callHistory) { entry in
                HStack {
                    VStack(alignment: .leading) {
                        Text(entry.name.isEmpty ? entry.number : entry.name)
                        Text(entry.type).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(entry.duration)s").foregroundStyle(.secondary)
                }
            }
        }
        .padding(24)
        .onAppear {
            if model.callHistory.isEmpty { model.refreshCallHistory() }
        }
    }
}
