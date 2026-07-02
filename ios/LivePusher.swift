import Foundation

/**
 * Per-fix HTTP POST that runs on the native side, so it keeps delivering while
 * the JS thread is suspended (screen off / backgrounded — the `location`
 * background mode keeps this process un-suspended). Fed by
 * LocationEngine.onLocationNative.
 *
 * Two delivery modes, chosen by `LivePushConfig.persistQueue`:
 *
 *  - **Fire-and-forget** (default): one async POST per fix, dropped on failure.
 *  - **Durable queue** (persistQueue=true): each fix is written to a SQLite
 *    queue (`LocationQueue`) and a background drainer POSTs it — deleting rows
 *    only on 2xx, retrying with backoff on failure. Survives kill / reboot and
 *    supports batching (`LivePushConfig.batchSize`).
 *
 * Generic by design: the lib owns transport, the app owns schema. The per-fix
 * body is the parsed `LivePushConfig.extraFieldsJson` object with the location
 * fields merged on top. Batched POSTs send a JSON array of those objects.
 */
final class LivePusher {

  private static let defaultMaxQueue = 10000
  private static let resultBufferMax = 50
  private static let backoffStartMs: UInt64 = 2000
  private static let backoffMaxMs: UInt64 = 60000

  private let lock = NSLock()
  private var config: LivePushConfig?
  private var enabled = false

  /// Observer of each POST outcome. When nil (JS suspended) outcomes are held in
  /// `resultBuffer` and replayed when a callback is next registered.
  private var onResult: ((LivePushResult) -> Void)?
  private var resultBuffer: [LivePushResult] = []

  private let session: URLSession = {
    let cfg = URLSessionConfiguration.default
    cfg.timeoutIntervalForRequest = 30
    return URLSession(configuration: cfg)
  }()

  // Durable-queue plumbing. The queue + draining run on this serial queue so
  // rows are never touched concurrently.
  private let queue = LocationQueue()
  private let drainQueue = DispatchQueue(label: "com.margelo.nitro.livepush.drain")
  private var draining = false
  private var backoffMs = LivePusher.backoffStartMs

  func configure(_ c: LivePushConfig) { lock.lock(); config = c; lock.unlock() }
  func setEnabled(_ value: Bool) { lock.lock(); enabled = value; lock.unlock() }
  func clear() { lock.lock(); config = nil; enabled = false; lock.unlock() }

  func setOnResult(_ cb: ((LivePushResult) -> Void)?) {
    lock.lock()
    onResult = cb
    let pending = resultBuffer
    resultBuffer.removeAll()
    lock.unlock()
    // Replay anything buffered while JS was away, in order.
    if let cb { pending.forEach { cb($0) } }
  }

  private func emitResult(_ result: LivePushResult) {
    lock.lock()
    let cb = onResult
    if cb == nil {
      resultBuffer.append(result)
      if resultBuffer.count > LivePusher.resultBufferMax { resultBuffer.removeFirst(resultBuffer.count - LivePusher.resultBufferMax) }
    }
    lock.unlock()
    cb?(result)
  }

  // MARK: - Ingest

  func push(_ loc: LocationData) {
    lock.lock(); let cfg = config; let on = enabled; lock.unlock()
    guard on, let cfg, !cfg.url.isEmpty, URL(string: cfg.url) != nil else { return }
    guard let body = buildBody(cfg, loc) else { return }

    if cfg.persistQueue == true {
      let maxQueue = Int(cfg.maxQueueSize ?? Double(LivePusher.defaultMaxQueue))
      drainQueue.async {
        self.queue.enqueue(body: body, nowMs: Int64(Date().timeIntervalSince1970 * 1000))
        self.queue.trimToMax(maxQueue)
      }
      scheduleDrain(cfg)
    } else {
      sendSingle(cfg, body) // fire-and-forget
    }
  }

  private func buildBody(_ cfg: LivePushConfig, _ loc: LocationData) -> String? {
    var fields: [String: Any] = [:]
    if !cfg.extraFieldsJson.isEmpty,
       let data = cfg.extraFieldsJson.data(using: .utf8),
       let parsed = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
      fields = parsed
    }
    fields["latitude"] = loc.latitude
    fields["longitude"] = loc.longitude
    fields["timestamp"] = loc.timestamp
    if cfg.includeFullPoint {
      fields["speed"] = loc.speed
      fields["bearing"] = loc.bearing
      fields["accuracy"] = loc.accuracy
      fields["altitude"] = loc.altitude
    }
    guard let data = try? JSONSerialization.data(withJSONObject: fields) else { return nil }
    return String(data: data, encoding: .utf8)
  }

  // MARK: - Fire-and-forget path

  private func sendSingle(_ cfg: LivePushConfig, _ body: String) {
    guard let req = buildRequest(cfg, body) else { return }
    session.dataTask(with: req) { [weak self] _, response, error in
      guard let self else { return }
      if let http = response as? HTTPURLResponse {
        let code = http.statusCode
        let ok = (200...299).contains(code)
        self.emitResult(LivePushResult(ok: ok, statusCode: Double(code), error: ok ? "" : String(code)))
        if code == 401 { self.lock.lock(); self.config = nil; self.lock.unlock() }
      } else {
        self.emitResult(LivePushResult(ok: false, statusCode: 0, error: error?.localizedDescription ?? "network error"))
      }
    }.resume()
  }

  // MARK: - Durable-queue drain path

  func queuedCount() -> Int {
    return drainQueue.sync { queue.count() }
  }

  /// Blocking drain used by forceSync(); returns true when the queue is empty.
  func forceSyncBlocking() -> Bool {
    return drainQueue.sync { drainLoop() }
  }

  private func scheduleDrain(_ cfg: LivePushConfig) {
    lock.lock(); if draining { lock.unlock(); return }; draining = true; lock.unlock()
    let delayMs = UInt64(max(cfg.batchMaxDelayMs ?? 0, 0))
    drainQueue.asyncAfter(deadline: .now() + .milliseconds(Int(delayMs))) {
      self.lock.lock(); self.draining = false; self.lock.unlock()
      _ = self.drainLoop()
    }
  }

  private enum Disposition { case delivered, retry, drop }

  /// Drains synchronously on the drainQueue; returns true when empty.
  private func drainLoop() -> Bool {
    lock.lock(); let cfg = config; let on = enabled; lock.unlock()
    guard let cfg, on else { return queue.count() == 0 }
    let batchSize = max(Int(cfg.batchSize ?? 1), 1)

    while true {
      lock.lock(); let stillOn = enabled; lock.unlock()
      if !stillOn { return queue.count() == 0 }

      let rows = queue.peek(limit: batchSize)
      if rows.isEmpty { backoffMs = LivePusher.backoffStartMs; return true }

      let (disposition, code, errText) = postBatch(cfg, rows)
      switch disposition {
      case .delivered:
        queue.delete(ids: rows.map { $0.id })
        backoffMs = LivePusher.backoffStartMs
        emitResult(LivePushResult(ok: true, statusCode: Double(code), error: ""))
        // loop for next batch
      case .drop:
        queue.delete(ids: rows.map { $0.id })
        emitResult(LivePushResult(ok: false, statusCode: Double(code), error: errText))
        if code == 401 { lock.lock(); config = nil; lock.unlock(); return queue.count() == 0 }
      case .retry:
        emitResult(LivePushResult(ok: false, statusCode: Double(code), error: errText))
        scheduleBackoffRetry()
        return false
      }
    }
  }

  private func scheduleBackoffRetry() {
    let delay = backoffMs
    backoffMs = min(backoffMs * 2, LivePusher.backoffMaxMs)
    lock.lock(); if draining { lock.unlock(); return }; draining = true; lock.unlock()
    drainQueue.asyncAfter(deadline: .now() + .milliseconds(Int(delay))) {
      self.lock.lock(); self.draining = false; self.lock.unlock()
      _ = self.drainLoop()
    }
  }

  /// Synchronous POST (we're already on a background serial queue).
  private func postBatch(_ cfg: LivePushConfig, _ rows: [LocationQueue.Row]) -> (Disposition, Int, String) {
    let bodyStr: String
    if rows.count == 1 {
      bodyStr = rows[0].body
    } else {
      // batchSize > 1 → JSON array of the per-fix objects.
      let objects = rows.compactMap { row -> Any? in
        guard let data = row.body.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
      }
      guard let data = try? JSONSerialization.data(withJSONObject: objects),
            let s = String(data: data, encoding: .utf8) else {
        return (.drop, 0, "batch serialization failed")
      }
      bodyStr = s
    }

    guard let req = buildRequest(cfg, bodyStr) else { return (.drop, 0, "bad request") }

    let semaphore = DispatchSemaphore(value: 0)
    var result: (Disposition, Int, String) = (.retry, 0, "network error")
    session.dataTask(with: req) { _, response, error in
      if let http = response as? HTTPURLResponse {
        let code = http.statusCode
        switch code {
        case 200...299: result = (.delivered, code, "")
        case 400, 401, 413: result = (.drop, code, String(code))
        default: result = (.retry, code, String(code)) // 5xx, 429, etc.
        }
      } else {
        result = (.retry, 0, error?.localizedDescription ?? "network error")
      }
      semaphore.signal()
    }.resume()
    _ = semaphore.wait(timeout: .now() + 35)
    return result
  }

  private func buildRequest(_ cfg: LivePushConfig, _ body: String) -> URLRequest? {
    guard let url = URL(string: cfg.url) else { return nil }
    var req = URLRequest(url: url)
    req.httpMethod = "POST"
    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
    req.setValue("Bearer \(cfg.authToken)", forHTTPHeaderField: "Authorization")
    req.httpBody = body.data(using: .utf8)
    return req
  }
}
