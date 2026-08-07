import AppKit
import Contacts
import SwiftUI

/// Lightweight contact lookup via Contacts framework (number/email into SMS or dial field).
struct ContactPickerButton: View {
    var title: String = "Pick contact"
    var onPick: (String) -> Void
    @State private var query = ""
    @State private var results: [(name: String, value: String)] = []
    @State private var showPopover = false

    var body: some View {
        Button(title) {
            requestAccessAndLoad()
            showPopover = true
        }
        .popover(isPresented: $showPopover) {
            VStack(alignment: .leading, spacing: 8) {
                TextField("Search contacts", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: query) { q in search(q) }
                List(results, id: \.value) { row in
                    Button("\(row.name) — \(row.value)") {
                        onPick(row.value)
                        showPopover = false
                    }
                    .buttonStyle(.plain)
                }
                .frame(width: 320, height: 220)
            }
            .padding(12)
        }
    }

    private func requestAccessAndLoad() {
        let store = CNContactStore()
        store.requestAccess(for: .contacts) { ok, _ in
            DispatchQueue.main.async {
                if ok { search("") }
            }
        }
    }

    private func search(_ q: String) {
        let store = CNContactStore()
        let keys: [CNKeyDescriptor] = [
            CNContactGivenNameKey as CNKeyDescriptor,
            CNContactFamilyNameKey as CNKeyDescriptor,
            CNContactPhoneNumbersKey as CNKeyDescriptor,
            CNContactEmailAddressesKey as CNKeyDescriptor
        ]
        let request = CNContactFetchRequest(keysToFetch: keys)
        var out: [(String, String)] = []
        try? store.enumerateContacts(with: request) { contact, stop in
            let name = "\(contact.givenName) \(contact.familyName)".trimmingCharacters(in: .whitespaces)
            for phone in contact.phoneNumbers {
                let v = phone.value.stringValue
                if q.isEmpty || name.localizedCaseInsensitiveContains(q) || v.contains(q) {
                    out.append((name.isEmpty ? v : name, v))
                }
            }
            if out.count > 40 { stop.pointee = true }
        }
        results = out
    }
}
