package com.margelo.nitro.nitrolocationtracking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Emits a [NetworkStatus] whenever the cellular generation or transport
 * changes. Android is already event-driven via `registerDefaultNetworkCallback`
 * (unlike iOS, which needs CoreTelephony notifications) — see
 * PLAN_NITRO_NETWORK_GENERATION_EVENTS.md.
 *
 * Generation comes from the permission-free `ConnectivityManager.getNetworkInfo`
 * subtype rather than `TelephonyManager.getDataNetworkType()`, which requires
 * READ_PHONE_STATE and a Play Console justification — not worth it for a label.
 */
class NetworkInfoMonitor(private val context: Context) {

    companion object {
        private const val TAG = "NetworkInfoMonitor"
    }

    var onChange: ((NetworkStatus) -> Unit)? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastStatus: NetworkStatus? = null
    private var isMonitoring = false

    fun start() {
        if (isMonitoring) {
            // Idempotent: don't double-register, just re-emit current state
            // for the (possibly newly-registered) callback.
            emit(force = true)
            return
        }

        val manager = connectivityManager
        if (manager == null) {
            Log.w(TAG, "ConnectivityManager unavailable — cannot monitor network")
            emit(force = true)
            return
        }

        isMonitoring = true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emit(force = false)
            }

            override fun onLost(network: Network) {
                emit(force = false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                emit(force = false)
            }
        }
        networkCallback = callback

        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }

        emit(force = true)
    }

    fun stop() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback: ${e.message}")
            }
        }
        networkCallback = null
        isMonitoring = false
        lastStatus = null
        onChange = null
    }

    fun currentStatus(): NetworkStatus {
        val manager = connectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }

        val transport = transportFrom(capabilities)
        val radio = if (transport == NetworkTransport.CELLULAR) currentRadioTechnology(network) else null
        val generation = if (transport == NetworkTransport.CELLULAR) {
            generationFrom(radio)
        } else {
            CellularGeneration.UNKNOWN
        }
        val isExpensive = capabilities?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: false

        return NetworkStatus(
            transport = transport,
            generation = generation,
            radioTechnology = radio ?: "",
            isExpensive = isExpensive,
            isConstrained = false // No Android equivalent to iOS Low Data Mode.
        )
    }

    private fun transportFrom(capabilities: NetworkCapabilities?): NetworkTransport {
        if (capabilities == null) return NetworkTransport.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
    }

    @Suppress("DEPRECATION")
    private fun currentRadioTechnology(network: Network?): String? {
        val manager = connectivityManager ?: return null
        val net = network ?: return null
        return try {
            val networkInfo = manager.getNetworkInfo(net) ?: return null
            networkTypeName(networkInfo.subtype)
        } catch (e: SecurityException) {
            Log.w(TAG, "getNetworkInfo threw SecurityException: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "getNetworkInfo failed: ${e.message}")
            null
        }
    }

    private fun networkTypeName(subtype: Int): String {
        return when (subtype) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "NR" // API 29+; safe as a compile-time int constant on minSdk 24.
            else -> "UNKNOWN"
        }
    }

    private fun generationFrom(radioTechnology: String?): CellularGeneration {
        return when (radioTechnology) {
            "GPRS", "EDGE", "CDMA", "1xRTT", "IDEN" -> CellularGeneration._2G
            "UMTS", "EVDO_0", "EVDO_A", "HSDPA", "HSUPA", "HSPA", "EVDO_B", "EHRPD", "HSPAP" -> CellularGeneration._3G
            "LTE" -> CellularGeneration._4G
            "NR" -> CellularGeneration._5G
            else -> CellularGeneration.UNKNOWN
        }
    }

    private fun emit(force: Boolean) {
        val status = currentStatus()
        if (!force && status == lastStatus) return
        lastStatus = status
        onChange?.invoke(status)
    }
}
