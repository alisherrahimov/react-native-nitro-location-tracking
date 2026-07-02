import Foundation
import SQLite3

/**
 * Durable FIFO queue of pending Live Push bodies, backed by SQLite (the system
 * libsqlite3) so it survives app kill / reboot. Each row is one serialized POST
 * body. The drainer in `LivePusher` peeks the oldest rows, POSTs them, and
 * deletes only on success.
 *
 * Not internally synchronized — `LivePusher` calls every method from its single
 * serial drain queue, so access is already serialized.
 */
final class LocationQueue {

  struct Row { let id: Int64; let body: String }

  // sqlite3 wants SQLITE_TRANSIENT so it copies bound text; the symbol isn't
  // imported into Swift, so reconstruct it.
  private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

  private var db: OpaquePointer?

  init() {
    let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
      ?? FileManager.default.temporaryDirectory
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    let path = dir.appendingPathComponent("nitro_livepush_queue.sqlite").path

    if sqlite3_open(path, &db) == SQLITE_OK {
      exec(
        "CREATE TABLE IF NOT EXISTS pending (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, created_at INTEGER NOT NULL)"
      )
    } else {
      NSLog("[NitroLocationQueue] failed to open db at \(path)")
      db = nil
    }
  }

  deinit {
    if db != nil { sqlite3_close(db) }
  }

  func enqueue(body: String, nowMs: Int64) {
    guard let db else { return }
    var stmt: OpaquePointer?
    defer { sqlite3_finalize(stmt) }
    guard sqlite3_prepare_v2(db, "INSERT INTO pending (body, created_at) VALUES (?, ?)", -1, &stmt, nil) == SQLITE_OK else { return }
    sqlite3_bind_text(stmt, 1, body, -1, SQLITE_TRANSIENT)
    sqlite3_bind_int64(stmt, 2, nowMs)
    if sqlite3_step(stmt) != SQLITE_DONE {
      NSLog("[NitroLocationQueue] enqueue failed")
    }
  }

  /// Oldest `limit` rows, FIFO order.
  func peek(limit: Int) -> [Row] {
    guard let db, limit > 0 else { return [] }
    var stmt: OpaquePointer?
    defer { sqlite3_finalize(stmt) }
    var rows: [Row] = []
    guard sqlite3_prepare_v2(db, "SELECT id, body FROM pending ORDER BY id ASC LIMIT ?", -1, &stmt, nil) == SQLITE_OK else { return [] }
    sqlite3_bind_int(stmt, 1, Int32(limit))
    while sqlite3_step(stmt) == SQLITE_ROW {
      let id = sqlite3_column_int64(stmt, 0)
      let body = sqlite3_column_text(stmt, 1).map { String(cString: $0) } ?? ""
      rows.append(Row(id: id, body: body))
    }
    return rows
  }

  func delete(ids: [Int64]) {
    guard let db, !ids.isEmpty else { return }
    let placeholders = ids.map { _ in "?" }.joined(separator: ",")
    var stmt: OpaquePointer?
    defer { sqlite3_finalize(stmt) }
    guard sqlite3_prepare_v2(db, "DELETE FROM pending WHERE id IN (\(placeholders))", -1, &stmt, nil) == SQLITE_OK else { return }
    for (i, id) in ids.enumerated() {
      sqlite3_bind_int64(stmt, Int32(i + 1), id)
    }
    if sqlite3_step(stmt) != SQLITE_DONE {
      NSLog("[NitroLocationQueue] delete failed")
    }
  }

  func count() -> Int {
    guard let db else { return 0 }
    var stmt: OpaquePointer?
    defer { sqlite3_finalize(stmt) }
    guard sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM pending", -1, &stmt, nil) == SQLITE_OK else { return 0 }
    return sqlite3_step(stmt) == SQLITE_ROW ? Int(sqlite3_column_int(stmt, 0)) : 0
  }

  /// Drop oldest rows so the queue never exceeds `maxSize`.
  func trimToMax(_ maxSize: Int) {
    guard maxSize > 0 else { return }
    let excess = count() - maxSize
    guard excess > 0 else { return }
    exec("DELETE FROM pending WHERE id IN (SELECT id FROM pending ORDER BY id ASC LIMIT \(excess))")
  }

  private func exec(_ sql: String) {
    guard let db else { return }
    if sqlite3_exec(db, sql, nil, nil, nil) != SQLITE_OK {
      NSLog("[NitroLocationQueue] exec failed: \(sql)")
    }
  }
}
