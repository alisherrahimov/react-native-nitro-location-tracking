import Foundation

class ConnectionManager: NSObject, URLSessionWebSocketDelegate {
    private var webSocket: URLSessionWebSocketTask?
    private var session: URLSession?
    private var config: ConnectionConfig?
    private var reconnectAttempts = 0
    private var connected = false

    var onStateChange: ((ConnectionState) -> Void)?
    var onMessage: ((String) -> Void)?

    var isConnected: Bool { connected }

    func configure(_ config: ConnectionConfig) {
        self.config = config
        session = URLSession(configuration: .default,
                             delegate: self, delegateQueue: OperationQueue())
    }

    func connect() {
        guard let config = config,
              let url = URL(string: config.wsUrl) else { return }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(config.authToken)",
                         forHTTPHeaderField: "Authorization")
        webSocket = session?.webSocketTask(with: request)
        webSocket?.resume()
        startListening()
        connected = true
        reconnectAttempts = 0
        onStateChange?(.connected)
    }

    func disconnect() {
        webSocket?.cancel(with: .normalClosure, reason: nil)
        connected = false
        onStateChange?(.disconnected)
    }

    func send(_ message: String) {
        webSocket?.send(.string(message)) { [weak self] error in
            if error != nil { self?.handleDisconnect() }
        }
    }

    func getState() -> ConnectionState {
        return connected ? .connected : .disconnected
    }

    private func startListening() {
        webSocket?.receive { [weak self] result in
            switch result {
            case .success(let msg):
                if case .string(let text) = msg {
                    self?.onMessage?(text)
                }
                self?.startListening()
            case .failure(_):
                self?.handleDisconnect()
            }
        }
    }

    private func handleDisconnect() {
        guard let config = config else { return }
        connected = false
        onStateChange?(.reconnecting)
        guard reconnectAttempts < Int(config.maxReconnectAttempts) else {
            onStateChange?(.disconnected)
            return
        }
        reconnectAttempts += 1
        let delay = config.reconnectIntervalMs / 1000.0
        DispatchQueue.global().asyncAfter(deadline: .now() + delay) {
            [weak self] in self?.connect()
        }
    }

    // Location delivery now runs through LivePusher (per-fix native POST that
    // survives JS suspension). There is no local queue to drain, so forceSync
    // has nothing to flush — kept for API compatibility, always succeeds.
    @discardableResult
    func flushQueue() -> Bool { true }
}
