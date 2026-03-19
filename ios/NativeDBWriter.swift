import SQLite3

class NativeDBWriter {
    private var db: OpaquePointer?

    init() {
        let documentsDir = FileManager.default.urls(
            for: .documentDirectory, in: .userDomainMask).first!
        let dbPath = documentsDir
            .appendingPathComponent("nitro_location.db").path
        sqlite3_open(dbPath, &db)
        sqlite3_exec(db, "PRAGMA journal_mode = WAL", nil, nil, nil)
        createTables()
    }

    private func createTables() {
        let sql = """
        CREATE TABLE IF NOT EXISTS locations (
            id TEXT PRIMARY KEY,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            altitude REAL,
            speed REAL,
            bearing REAL,
            accuracy REAL,
            timestamp INTEGER NOT NULL,
            ride_id TEXT,
            synced INTEGER DEFAULT 0,
            retry_count INTEGER DEFAULT 0,
            created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
        );
        CREATE INDEX IF NOT EXISTS idx_locations_synced ON locations(synced);
        CREATE INDEX IF NOT EXISTS idx_locations_timestamp ON locations(timestamp);
        CREATE INDEX IF NOT EXISTS idx_locations_ride_id ON locations(ride_id);
        """
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    func insert(_ location: LocationData, rideId: String? = nil) {
        let sql = """
        INSERT INTO locations
            (id, latitude, longitude, altitude, speed,
             bearing, accuracy, timestamp, ride_id, synced)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            let id = UUID().uuidString
            sqlite3_bind_text(stmt, 1, (id as NSString).utf8String, -1, nil)
            sqlite3_bind_double(stmt, 2, location.latitude)
            sqlite3_bind_double(stmt, 3, location.longitude)
            sqlite3_bind_double(stmt, 4, location.altitude)
            sqlite3_bind_double(stmt, 5, location.speed)
            sqlite3_bind_double(stmt, 6, location.bearing)
            sqlite3_bind_double(stmt, 7, location.accuracy)
            sqlite3_bind_int64(stmt, 8, Int64(location.timestamp))
            if let rideId = rideId {
                sqlite3_bind_text(stmt, 9,
                    (rideId as NSString).utf8String, -1, nil)
            } else {
                sqlite3_bind_null(stmt, 9)
            }
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    func getUnsyncedBatch(limit: Int) -> [(id: String, data: LocationData)] {
        let sql = """
        SELECT id, latitude, longitude, altitude, speed,
               bearing, accuracy, timestamp
        FROM locations WHERE synced = 0
        ORDER BY timestamp ASC LIMIT ?
        """
        var results: [(id: String, data: LocationData)] = []
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_int(stmt, 1, Int32(limit))
            while sqlite3_step(stmt) == SQLITE_ROW {
                let id = String(cString: sqlite3_column_text(stmt, 0))
                let data = LocationData(
                    latitude: sqlite3_column_double(stmt, 1),
                    longitude: sqlite3_column_double(stmt, 2),
                    altitude: sqlite3_column_double(stmt, 3),
                    speed: sqlite3_column_double(stmt, 4),
                    bearing: sqlite3_column_double(stmt, 5),
                    accuracy: sqlite3_column_double(stmt, 6),
                    timestamp: Double(sqlite3_column_int64(stmt, 7)),
                    isMockLocation:true // we need to check here 
                )
                results.append((id: id, data: data))
            }
        }
        sqlite3_finalize(stmt)
        return results
    }

    func markSynced(_ ids: [String]) {
        let placeholders = ids.map { _ in "?" }.joined(separator: ",")
        let sql = "UPDATE locations SET synced = 1 WHERE id IN (\(placeholders))"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            for (i, id) in ids.enumerated() {
                sqlite3_bind_text(stmt, Int32(i + 1),
                    (id as NSString).utf8String, -1, nil)
            }
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    func clearOldSynced() {
        sqlite3_exec(db, """
            DELETE FROM locations WHERE synced = 1
            AND timestamp < (strftime('%s','now') * 1000 - 86400000)
        """, nil, nil, nil)
    }

    deinit {
        sqlite3_close(db)
    }
}
