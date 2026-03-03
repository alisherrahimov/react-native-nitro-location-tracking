import Foundation

class ConnectionManager: NSObject, URLSessionWebSocketDelegate {
    private var webSocket: URLSessionWebSocketTask?
    private var session: URLSession?
    private var config: ConnectionConfig?
    private var reconnectAttempts = 0
    private var connected = false
    private var syncTimer: Timer?

    var onStateChange: ((ConnectionState) -> Void)?
    var onMessage: ((String) -> Void)?
    var dbWriter: NativeDBWriter?

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
        startSyncTimer()
        connected = true
        reconnectAttempts = 0
        onStateChange?(.connected)
    }

    func disconnect() {
        syncTimer?.invalidate()
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

    private func startSyncTimer() {
        guard let config = config else { return }
        DispatchQueue.main.async {
            self.syncTimer = Timer.scheduledTimer(
                withTimeInterval: config.syncIntervalMs / 1000.0,
                repeats: true
            ) { [weak self] _ in self?.flushQueue() }
        }
    }

    func flushQueue() -> Bool {
        guard let config = config, let dbWriter = dbWriter else { return false }
        let queued = dbWriter.getUnsyncedBatch(limit: Int(config.batchSize))
        guard !queued.isEmpty else { return true }

        let ids = queued.map { $0.id }
        let payload: [[String: Any]] = queued.map { item in
            [
                "latitude": item.data.latitude,
                "longitude": item.data.longitude,
                "altitude": item.data.altitude,
                "speed": item.data.speed,
                "bearing": item.data.bearing,
                "accuracy": item.data.accuracy,
                "timestamp": item.data.timestamp
            ]
        }

        if connected {
            if let json = try? JSONSerialization.data(withJSONObject: payload),
               let str = String(data: json, encoding: .utf8) {
                send(str)
                dbWriter.markSynced(ids)
                return true
            }
        } else {
            sendBatchHTTP(payload, ids: ids)
        }
        return true
    }

    private func sendBatchHTTP(_ locations: [[String: Any]], ids: [String]) {
        guard let config = config,
              let url = URL(string: "\(config.restUrl)/locations/batch")
        else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(config.authToken)",
                         forHTTPHeaderField: "Authorization")
        request.httpBody = try? JSONSerialization.data(withJSONObject: locations)

        URLSession.shared.dataTask(with: request) { [weak self] _, response, error in
            let http = response as? HTTPURLResponse
            if error == nil && http?.statusCode == 200 {
                self?.dbWriter?.markSynced(ids)
            }
        }.resume()
    }
}
