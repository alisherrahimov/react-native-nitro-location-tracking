import Foundation

class ConnectionManager: NSObject, URLSessionWebSocketDelegate {
    private var webSocket: URLSessionWebSocketTask?
    private var session: URLSession?
    private var config: ConnectionConfig?
    private var reconnectAttempts = 0
    private var connected = false
    // True only while WE initiated the close, so the failure that follows a
    // cancel() doesn't get mistaken for a dropped connection and reconnected.
    private var manualDisconnect = false
    // Guards against scheduling two reconnects for the same drop: a single
    // disconnect can surface as both a receive failure and a send failure.
    private var reconnecting = false

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
        manualDisconnect = false
        reconnecting = false
        var request = URLRequest(url: url)
        request.setValue("Bearer \(config.authToken)",
                         forHTTPHeaderField: "Authorization")
        webSocket = session?.webSocketTask(with: request)
        webSocket?.resume()
        startListening()
        // Do NOT report `.connected` here. resume() only *starts* the upgrade
        // handshake asynchronously — the socket isn't open yet. The state flips
        // to `.connected` from urlSession(_:webSocketTask:didOpenWithProtocol:)
        // once the server actually accepts the connection.
    }

    func disconnect() {
        manualDisconnect = true
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

    // MARK: - URLSessionWebSocketDelegate

    func urlSession(_ session: URLSession,
                    webSocketTask: URLSessionWebSocketTask,
                    didOpenWithProtocol protocol: String?) {
        connected = true
        reconnectAttempts = 0
        reconnecting = false
        onStateChange?(.connected)
    }

    func urlSession(_ session: URLSession,
                    webSocketTask: URLSessionWebSocketTask,
                    didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
                    reason: Data?) {
        guard !manualDisconnect else { return }
        connected = false
        onStateChange?(.disconnected)
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
        guard let config = config, !manualDisconnect, !reconnecting else { return }
        connected = false
        reconnecting = true
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

}
