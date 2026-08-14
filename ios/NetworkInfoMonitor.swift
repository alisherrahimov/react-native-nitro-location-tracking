import Foundation
import CoreTelephony
import Network

/// Emits a `NetworkStatus` whenever the cellular generation or transport
/// changes. `NWPathMonitor` covers transport/expensive/constrained;
/// `CTServiceRadioAccessTechnologyDidChange` is the notification NetInfo is
/// missing on iOS and the whole reason this module exists — see
/// PLAN_NITRO_NETWORK_GENERATION_EVENTS.md.
final class NetworkInfoMonitor {
    var onChange: ((NetworkStatus) -> Void)?

    private let telephonyInfo = CTTelephonyNetworkInfo()
    private var pathMonitor: NWPathMonitor?
    private let queue = DispatchQueue(label: "com.margelo.nitro.nitrolocationtracking.NetworkInfoMonitor")

    private var isMonitoring = false
    private var latestPath: NWPath?
    private var lastStatus: NetworkStatus?
    private var radioObserver: NSObjectProtocol?

    func start() {
        if isMonitoring {
            // Idempotent: don't double-subscribe, just re-emit current state
            // for the (possibly newly-registered) callback.
            queue.async { [weak self] in self?.emit(force: true) }
            return
        }
        isMonitoring = true

        let monitor = NWPathMonitor()
        pathMonitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            self?.queue.async {
                self?.latestPath = path
                self?.emit(force: false)
            }
        }
        monitor.start(queue: queue)

        radioObserver = NotificationCenter.default.addObserver(
            forName: .CTServiceRadioAccessTechnologyDidChange,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.queue.async { self?.emit(force: false) }
        }

        // The callback fires once immediately with the current state so JS
        // never renders an empty initial value.
        queue.async { [weak self] in self?.emit(force: true) }
    }

    func stop() {
        pathMonitor?.cancel()
        pathMonitor = nil
        if let observer = radioObserver {
            NotificationCenter.default.removeObserver(observer)
            radioObserver = nil
        }
        isMonitoring = false
        lastStatus = nil
        latestPath = nil
    }

    func currentStatus() -> NetworkStatus {
        return buildStatus(path: latestPath)
    }

    private func emit(force: Bool) {
        let status = buildStatus(path: latestPath)
        if !force, let last = lastStatus, statusesEqual(last, status) { return }
        lastStatus = status
        onChange?(status)
    }

    private func statusesEqual(_ a: NetworkStatus, _ b: NetworkStatus) -> Bool {
        return a.transport == b.transport
            && a.generation == b.generation
            && a.radioTechnology == b.radioTechnology
            && a.isExpensive == b.isExpensive
            && a.isConstrained == b.isConstrained
    }

    private func buildStatus(path: NWPath?) -> NetworkStatus {
        let transport = transport(from: path)
        let radio = transport == .cellular ? currentRadioTechnology() : nil
        let generation = transport == .cellular ? generation(from: radio) : (CellularGeneration(fromString: "unknown") ?? .unknown)
        return NetworkStatus(
            transport: transport,
            generation: generation,
            radioTechnology: radio ?? "",
            isExpensive: path?.isExpensive ?? false,
            isConstrained: path?.isConstrained ?? false
        )
    }

    private func transport(from path: NWPath?) -> NetworkTransport {
        guard let path = path, path.status == .satisfied else {
            return NetworkTransport(fromString: "none") ?? .none
        }
        if path.usesInterfaceType(.cellular) { return NetworkTransport(fromString: "cellular") ?? .cellular }
        if path.usesInterfaceType(.wifi) { return NetworkTransport(fromString: "wifi") ?? .wifi }
        if path.usesInterfaceType(.wiredEthernet) { return NetworkTransport(fromString: "ethernet") ?? .ethernet }
        return NetworkTransport(fromString: "other") ?? .other
    }

    /// Non-deprecated, dual-SIM aware: picks the SIM actually carrying data
    /// (iOS 13+), falling back to the first reported service.
    private func currentRadioTechnology() -> String? {
        guard let techs = telephonyInfo.serviceCurrentRadioAccessTechnology, !techs.isEmpty else {
            return nil
        }
        if #available(iOS 13.0, *),
           let dataId = telephonyInfo.dataServiceIdentifier,
           let tech = techs[dataId] {
            return tech
        }
        return techs.values.first
    }

    private func generation(from radioTechnology: String?) -> CellularGeneration {
        guard let tech = radioTechnology else { return CellularGeneration(fromString: "unknown") ?? .unknown }

        switch tech {
        case CTRadioAccessTechnologyGPRS, CTRadioAccessTechnologyEdge, CTRadioAccessTechnologyCDMA1x:
            return CellularGeneration(fromString: "2g") ?? .unknown
        case CTRadioAccessTechnologyWCDMA, CTRadioAccessTechnologyHSDPA, CTRadioAccessTechnologyHSUPA,
             CTRadioAccessTechnologyCDMAEVDORev0, CTRadioAccessTechnologyCDMAEVDORevA,
             CTRadioAccessTechnologyCDMAEVDORevB, CTRadioAccessTechnologyeHRPD:
            return CellularGeneration(fromString: "3g") ?? .unknown
        case CTRadioAccessTechnologyLTE:
            return CellularGeneration(fromString: "4g") ?? .unknown
        default:
            if #available(iOS 14.1, *) {
                if tech == CTRadioAccessTechnologyNR || tech == CTRadioAccessTechnologyNRNSA {
                    return CellularGeneration(fromString: "5g") ?? .unknown
                }
            }
            // Many carriers report LTE while actually on 5G NSA — Apple's own
            // status bar lies about this too. Unrecognised strings (nil / no
            // SIM / airplane mode) fall through here as 'unknown'.
            return CellularGeneration(fromString: "unknown") ?? .unknown
        }
    }
}
