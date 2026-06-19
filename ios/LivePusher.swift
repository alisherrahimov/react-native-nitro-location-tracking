import Foundation

/**
 * Per-fix HTTP POST that runs on the native side, so it keeps delivering while
 * the JS thread is suspended (screen off / backgrounded — the `location`
 * background mode keeps this process un-suspended). Fed by
 * LocationEngine.onLocationNative.
 *
 * Three gates decide whether a fix is sent (all controlled from JS, on rare
 * events while JS is awake): tracking must be running (no fix otherwise),
 * `enabled` must be true, and `config` must be set.
 *
 * Generic by design: the lib owns transport, the app owns schema. The body is
 * the parsed `LivePushConfig.extraFieldsJson` object with the location fields
 * merged on top.
 */
final class LivePusher {
  private let lock = NSLock()
  private var config: LivePushConfig?
  private var enabled = false
  /// Optional observer of each POST outcome. Invoked from URLSession's
  /// completion handler. Fires only while the JS thread is alive — results
  /// that land while JS is suspended are not buffered (Nitro owns JS delivery).
  private var onResult: ((LivePushResult) -> Void)?
  private let session = URLSession(configuration: .default)

  func configure(_ c: LivePushConfig) {
    lock.lock(); config = c; lock.unlock()
  }

  func setEnabled(_ value: Bool) {
    lock.lock(); enabled = value; lock.unlock()
  }

  func setOnResult(_ cb: ((LivePushResult) -> Void)?) {
    lock.lock(); onResult = cb; lock.unlock()
  }

  func clear() {
    lock.lock(); config = nil; enabled = false; lock.unlock()
  }

  func push(_ loc: LocationData) {
    lock.lock()
    let cfg = config
    let on = enabled
    lock.unlock()

    guard on, let cfg, !cfg.url.isEmpty, let url = URL(string: cfg.url) else { return }

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

    guard let body = try? JSONSerialization.data(withJSONObject: fields) else { return }

    var req = URLRequest(url: url)
    req.httpMethod = "POST"
    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
    req.setValue("Bearer \(cfg.authToken)", forHTTPHeaderField: "Authorization")
    req.httpBody = body

    session.dataTask(with: req) { [weak self] _, response, error in
      guard let self else { return }
      self.lock.lock(); let cb = self.onResult; self.lock.unlock()

      if let http = response as? HTTPURLResponse {
        let code = http.statusCode
        let ok = (200...299).contains(code)
        cb?(LivePushResult(ok: ok, statusCode: Double(code), error: ok ? "" : String(code)))
        // Token rotated while JS was asleep. Drop config so we stop spamming
        // 401s; JS re-configures on its next wake-up.
        if code == 401 {
          self.lock.lock(); self.config = nil; self.lock.unlock()
        }
      } else {
        // Network error / timeout — no HTTP response.
        cb?(LivePushResult(ok: false, statusCode: 0, error: error?.localizedDescription ?? "network error"))
      }
    }.resume()
  }
}
