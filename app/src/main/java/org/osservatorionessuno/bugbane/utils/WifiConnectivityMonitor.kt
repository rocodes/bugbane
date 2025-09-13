package org.osservatorionessuno.bugbane.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WifiConnectivityMonitor"

class WifiConnectivityMonitor(context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _wifiState = MutableStateFlow(false)
    val wifiState: StateFlow<Boolean> = _wifiState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // available != connected
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val isConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (_wifiState.value != isConnected) {
                Log.d(TAG, "wifi connection state ${_wifiState.value} -> $isConnected")
                _wifiState.value = isConnected
            }
        }

        override fun onLost(network: Network) {
            if (_wifiState.value) {
                Log.d(TAG, "wifi lost")
                _wifiState.value = false
            }
        }
    }

    init {
        // Register the callback to track connectivity changes
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        // Initialize state by checking current connectivity
        _wifiState.value = checkCurrentWifiConnected()
    }

    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (ex: IllegalArgumentException) {
            Log.i(TAG, "NetworkCallback already unregistered")
        }
    }

    private fun checkCurrentWifiConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
