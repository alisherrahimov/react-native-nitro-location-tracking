// import { open } from 'react-native-nitro-sqlite'

// // Open the SAME database that native module writes to
// export const db = open({ name: 'nitro_location.db' })

// // Enable WAL for safe concurrent access with native writer
// db.execute('PRAGMA journal_mode = WAL')

// // ─── Additional Tables (JS-managed) ────────────────

// db.execute(`
//   CREATE TABLE IF NOT EXISTS rides (
//     id TEXT PRIMARY KEY,
//     driver_id TEXT,
//     rider_id TEXT,
//     status TEXT DEFAULT 'pending',
//     pickup_lat REAL, pickup_lng REAL,
//     dropoff_lat REAL, dropoff_lng REAL,
//     pickup_address TEXT, dropoff_address TEXT,
//     fare_amount REAL,
//     distance_km REAL,
//     duration_minutes REAL,
//     started_at INTEGER,
//     completed_at INTEGER,
//     created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
//   )
// `)

// db.execute(`
//   CREATE TABLE IF NOT EXISTS earnings (
//     id TEXT PRIMARY KEY,
//     date TEXT UNIQUE,
//     ride_count INTEGER DEFAULT 0,
//     total_amount REAL DEFAULT 0,
//     total_distance REAL DEFAULT 0,
//     online_hours REAL DEFAULT 0
//   )
// `)

// db.execute(`
//   CREATE TABLE IF NOT EXISTS settings (
//     key TEXT PRIMARY KEY,
//     value TEXT
//   )
// `)

// // ─── Query Helpers — Locations ──────────────────────

// export function getRecentLocations(limit = 100) {
//   return db.execute(
//     'SELECT * FROM locations ORDER BY timestamp DESC LIMIT ?',
//     [limit]
//   )
// }

// export function getRideLocations(rideId: string) {
//   return db.execute(
//     `
//     SELECT latitude, longitude, speed, bearing, timestamp
//     FROM locations WHERE ride_id = ? ORDER BY timestamp ASC
//   `,
//     [rideId]
//   )
// }

// export function getLocationsBetween(startMs: number, endMs: number) {
//   return db.execute(
//     `
//     SELECT * FROM locations
//     WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC
//   `,
//     [startMs, endMs]
//   )
// }

// export function getUnsyncedCount(): number {
//   return db.execute(
//     'SELECT COUNT(*) as count FROM locations WHERE synced = 0'
//   ).rows.item(0)!.count as number
// }

// // ─── Query Helpers — Analytics ──────────────────────

// export function getTodayStats() {
//   const today = new Date().toISOString().split('T')[0]!
//   return db.execute(
//     `
//     SELECT COUNT(*) as trips,
//       COALESCE(SUM(fare_amount), 0) as earnings,
//       COALESCE(SUM(distance_km), 0) as distance,
//       COALESCE(SUM(duration_minutes), 0) as minutes
//     FROM rides
//     WHERE date(started_at / 1000, 'unixepoch') = ?
//     AND status = 'completed'
//   `,
//     [today]
//   )
// }

// export function getWeeklyEarnings() {
//   return db.execute(
//     `
//     SELECT date(started_at / 1000, 'unixepoch') as day,
//       COUNT(*) as trips, SUM(fare_amount) as earnings,
//       SUM(distance_km) as distance
//     FROM rides WHERE started_at > ? AND status = 'completed'
//     GROUP BY day ORDER BY day ASC
//   `,
//     [Date.now() - 7 * 86400000]
//   )
// }

// export function getSpeedHistory(lastHours = 1) {
//   return db.execute(
//     `
//     SELECT CAST(timestamp / 60000 AS INTEGER) * 60000 as minute,
//       AVG(speed) as avg_speed, MAX(speed) as max_speed
//     FROM locations WHERE timestamp > ?
//     GROUP BY minute ORDER BY minute ASC
//   `,
//     [Date.now() - lastHours * 3600000]
//   )
// }

// export function getPickupHeatmap(limit = 50) {
//   return db.execute(
//     `
//     SELECT ROUND(pickup_lat, 3) as lat, ROUND(pickup_lng, 3) as lng,
//       COUNT(*) as ride_count
//     FROM rides WHERE status = 'completed'
//     GROUP BY lat, lng ORDER BY ride_count DESC LIMIT ?
//   `,
//     [limit]
//   )
// }

// export function getPeakHours() {
//   return db.execute(`
//     SELECT CAST(strftime('%H', started_at / 1000, 'unixepoch') AS INTEGER) as hour,
//       COUNT(*) as trips, AVG(fare_amount) as avg_fare
//     FROM rides WHERE status = 'completed'
//     GROUP BY hour ORDER BY hour ASC
//   `)
// }

// // ─── Query Helpers — Ride Management ────────────────

// export function createRide(ride: {
//   id: string
//   driverId?: string
//   riderId?: string
//   pickupLat: number
//   pickupLng: number
//   pickupAddress: string
//   dropoffLat: number
//   dropoffLng: number
//   dropoffAddress: string
// }) {
//   return db.execute(
//     `
//     INSERT INTO rides (id, driver_id, rider_id, status,
//       pickup_lat, pickup_lng, pickup_address,
//       dropoff_lat, dropoff_lng, dropoff_address, started_at)
//     VALUES (?, ?, ?, 'active', ?, ?, ?, ?, ?, ?, ?)
//   `,
//     [
//       ride.id,
//       ride.driverId ?? null,
//       ride.riderId ?? null,
//       ride.pickupLat,
//       ride.pickupLng,
//       ride.pickupAddress,
//       ride.dropoffLat,
//       ride.dropoffLng,
//       ride.dropoffAddress,
//       Date.now(),
//     ]
//   )
// }

// export function completeRide(
//   rideId: string,
//   fare: number,
//   distKm: number
// ) {
//   const now = Date.now()
//   db.execute(
//     `
//     UPDATE rides SET status = 'completed', fare_amount = ?,
//       distance_km = ?, completed_at = ?,
//       duration_minutes = (? - started_at) / 60000.0
//     WHERE id = ?
//   `,
//     [fare, distKm, now, now, rideId]
//   )
// }

// export function getRideHistory(limit = 20) {
//   return db.execute(
//     `
//     SELECT * FROM rides WHERE status = 'completed'
//     ORDER BY completed_at DESC LIMIT ?
//   `,
//     [limit]
//   )
// }

// export function getRideWithLocations(rideId: string) {
//   const ride = db.execute('SELECT * FROM rides WHERE id = ?', [rideId])
//   const locs = db.execute(
//     `
//     SELECT latitude, longitude, speed, bearing, timestamp
//     FROM locations WHERE ride_id = ? ORDER BY timestamp ASC
//   `,
//     [rideId]
//   )
//   return { ride: ride.rows.item(0), locations: locs.rows._array }
// }

// // ─── Settings Helper ────────────────────────────────

// export function getSetting(key: string): string | null {
//   const r = db.execute('SELECT value FROM settings WHERE key = ?', [key])
//   return r.rows.length > 0 ? (r.rows.item(0)!.value as string) : null
// }

// export function setSetting(key: string, value: string) {
//   db.execute(
//     'INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)',
//     [key, value]
//   )
// }
