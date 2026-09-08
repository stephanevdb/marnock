import Foundation

final class WebSocketClient: NSObject, URLSessionWebSocketDelegate, @unchecked Sendable {
    private var task: URLSessionWebSocketTask?
    private var session: URLSession!
    private var generation = 0
    private var closed = true
    private var connectTimeoutWork: DispatchWorkItem?
    private let connectTimeout: TimeInterval = 8

    var onMessage: ((Data) -> Void)?
    var onOpen: (() -> Void)?
    var onClose: (() -> Void)?

    override init() {
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }

    func connect(url: URL) {
        close(notify: false)
        closed = false
        let gen = generation
        task = session.webSocketTask(with: url)
        task?.resume()
        receiveLoop(gen: gen)
        let work = DispatchWorkItem { [weak self] in
            self?.timedOut(gen: gen)
        }
        connectTimeoutWork = work
        DispatchQueue.global().asyncAfter(deadline: .now() + connectTimeout, execute: work)
    }

    func send(_ data: Data) {
        task?.send(.data(data)) { _ in }
    }

    func close() {
        close(notify: false)
    }

    private func close(notify: Bool) {
        connectTimeoutWork?.cancel()
        connectTimeoutWork = nil
        generation += 1
        let old = task
        task = nil
        old?.cancel(with: .goingAway, reason: nil)
        if notify {
            emitClose()
        } else {
            closed = true
        }
    }

    private func timedOut(gen: Int) {
        guard generation == gen, !closed else { return }
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        emitClose()
    }

    private func emitClose() {
        connectTimeoutWork?.cancel()
        connectTimeoutWork = nil
        guard !closed else { return }
        closed = true
        onClose?()
    }

    private func receiveLoop(gen: Int) {
        task?.receive { [weak self] result in
            guard let self, self.generation == gen else { return }
            switch result {
            case .failure:
                self.emitClose()
            case .success(let message):
                switch message {
                case .data(let d): self.onMessage?(d)
                case .string(let s): self.onMessage?(Data(s.utf8))
                @unknown default: break
                }
                self.receiveLoop(gen: gen)
            }
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        guard webSocketTask === task else { return }
        connectTimeoutWork?.cancel()
        connectTimeoutWork = nil
        onOpen?()
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        guard webSocketTask === task else { return }
        emitClose()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let current = self.task, task === current else { return }
        if error != nil {
            emitClose()
        }
    }
}
