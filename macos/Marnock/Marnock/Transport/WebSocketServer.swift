import Foundation
import Network

/// Minimal WebSocket server for LAN pairing/sync (RFC6455 via Network.framework).
final class WebSocketServer: @unchecked Sendable {
    private let port: NWEndpoint.Port
    private var listener: NWListener?
    private var connections: [ObjectIdentifier: ServerConnection] = [:]
    private let queue = DispatchQueue(label: "marnock.ws.server")

    var onMessage: ((Data) -> Void)?
    var onClientConnected: (() -> Void)?
    var onClientDisconnected: (() -> Void)?

    private(set) var listeningPort: UInt16 = 0

    init(port: UInt16 = 0) {
        self.port = NWEndpoint.Port(rawValue: port) ?? .any
    }

    func start() throws {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        let wsOptions = NWProtocolWebSocket.Options()
        wsOptions.autoReplyPing = true
        params.defaultProtocolStack.applicationProtocols.insert(wsOptions, at: 0)

        listener = try NWListener(using: params, on: port)
        listener?.stateUpdateHandler = { [weak self] state in
            if case .ready = state, let p = self?.listener?.port?.rawValue {
                self?.listeningPort = p
            }
        }
        listener?.newConnectionHandler = { [weak self] conn in
            self?.accept(conn)
        }
        listener?.start(queue: queue)
    }

    func stop() {
        listener?.cancel()
        listener = nil
        connections.values.forEach { $0.cancel() }
        connections.removeAll()
    }

    func broadcast(_ data: Data) {
        queue.async {
            for c in self.connections.values {
                c.send(data)
            }
        }
    }

    private func accept(_ connection: NWConnection) {
        // Prefer a single active phone session — drop older sockets to avoid half-open flaps
        let stale = connections
        connections.removeAll()
        stale.values.forEach { $0.cancel() }

        let sc = ServerConnection(connection: connection)
        let id = ObjectIdentifier(sc)
        connections[id] = sc
        sc.onMessage = { [weak self] data in
            self?.onMessage?(data)
        }
        sc.onClosed = { [weak self] in
            self?.queue.async {
                guard let self else { return }
                if self.connections[id] != nil {
                    self.connections.removeValue(forKey: id)
                    self.onClientDisconnected?()
                }
            }
        }
        sc.start(queue: queue)
        onClientConnected?()
    }
}

private final class ServerConnection: @unchecked Sendable {
    private let connection: NWConnection
    private var closed = false
    var onMessage: ((Data) -> Void)?
    var onClosed: (() -> Void)?

    init(connection: NWConnection) {
        self.connection = connection
    }

    func start(queue: DispatchQueue) {
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed, .cancelled:
                self?.finish()
            default:
                break
            }
        }
        connection.start(queue: queue)
        receive()
    }

    func send(_ data: Data) {
        let meta = NWProtocolWebSocket.Metadata(opcode: .binary)
        let context = NWConnection.ContentContext(identifier: "bin", metadata: [meta])
        connection.send(content: data, contentContext: context, completion: .contentProcessed { [weak self] error in
            if error != nil {
                self?.finish()
            }
        })
    }

    private func receive() {
        // IMPORTANT: `isComplete` means this WebSocket *message* finished — not the connection.
        // Cancelling on isComplete was causing constant reconnects after every frame.
        connection.receiveMessage { [weak self] content, _, _, error in
            guard let self, !self.closed else { return }
            if let content, !content.isEmpty {
                self.onMessage?(content)
            }
            if error != nil {
                self.finish()
                return
            }
            self.receive()
        }
    }

    func cancel() {
        finish()
    }

    private func finish() {
        guard !closed else { return }
        closed = true
        connection.cancel()
        onClosed?()
    }
}
