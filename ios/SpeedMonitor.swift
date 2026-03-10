import Foundation

class SpeedMonitor {

    private var config: SpeedConfig?
    private var callback: ((SpeedAlertType, Double) -> Void)?
    private var currentState: SpeedAlertType = .normalized
    private var lastSpeedKmh: Double = 0.0

    init() {
        config = nil
        callback = nil
    }

    func configure(_ config: SpeedConfig) {
        self.config = config
    }

    func setCallback(_ callback: @escaping (SpeedAlertType, Double) -> Void) {
        self.callback = callback
    }

    func getCurrentSpeed() -> Double {
        return lastSpeedKmh
    }

    func feedLocation(_ data: LocationData) {
        guard let cfg = config else { return }

        let speedKmh = data.speed * 3.6 // m/s → km/h
        lastSpeedKmh = speedKmh

        let newState: SpeedAlertType
        if speedKmh > cfg.maxSpeedKmh {
            newState = .exceeded
        } else if speedKmh < cfg.minSpeedKmh {
            newState = .belowMinimum
        } else {
            newState = .normalized
        }

        // Only fire callback on state transitions
        if newState != currentState {
            currentState = newState
            callback?(newState, speedKmh)
        }
    }
}
