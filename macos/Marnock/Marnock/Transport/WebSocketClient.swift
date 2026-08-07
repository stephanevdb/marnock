import Foundation

final class WebSocketClient: NSObject, URLSessionWebSocketDelegate, @unchecked Sendable {
    private var task: URLSessionWebSocketTask?
    private var session: URLSession!
    var onMessage: ((Data) -> Void)?
    var onOpen: (() -> Void)?
    var onClose: (() -> Void)?

    override init() {
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }

    func connect(url: URL) {
        close()
        task = session.webSocketTask(with: url)
        task?.resume()
        receiveLoop()
    }

    func send(_ data: Data) {
        task?.send(.data(data)) { _ in }
    }

    func close() {
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
    }

    private func receiveLoop() {
        task?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .failure:
                self.onClose?()
            case .success(let message):
                switch message {
                case .data(let d): self.onMessage?(d)
                case .string(let s): self.onMessage?(Data(s.utf8))
                @unknown default: break
                }
                self.receiveLoop()
            }
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        onOpen?()
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        onClose?()
    }
}
