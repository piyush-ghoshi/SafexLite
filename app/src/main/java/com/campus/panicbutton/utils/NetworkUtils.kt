package com.campus.panicbutton.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Utility class for network connectivity detection and monitoring
 */
class NetworkUtils(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    companion object {
        private const val TAG = "NetworkUtils"
    }
    
    /**
     * Check if device is currently connected to the internet
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }
    
    /**
     * Check if device has internet connectivity (can reach external servers)
     */
    fun hasInternetConnectivity(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                isNetworkAvailable()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connectivity", e)
            false
        }
    }
    
    /**
     * Get network type as string for logging/debugging
     */
    fun getNetworkType(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return "None"
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
                
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Other"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.typeName ?: "None"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type", e)
            "Error"
        }
    }
    
    /**
     * Observe network connectivity changes as a Flow
     */
    fun observeNetworkConnectivity(): Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                trySend(false)
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed. Has internet: $hasInternet")
                trySend(hasInternet)
            }
        }
        
        // Register network callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }
        
        // Send initial state
        trySend(hasInternetConnectivity())
        
        awaitClose {
            Log.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()
    
    /**
     * Check if device is connected to WiFi
     */
    fun isWiFiConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi connectivity", e)
            false
        }
    }
    
    /**
     * Check if device is connected to cellular network
     */
    fun isCellularConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_MOBILE && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cellular connectivity", e)
            false
        }
    }
    
    /**
     * Get detailed network information for debugging
     */
    fun getNetworkInfo(): String {
        return try {
            val isAvailable = isNetworkAvailable()
            val hasInternet = hasInternetConnectivity()
            val networkType = getNetworkType()
            val isWiFi = isWiFiConnected()
            val isCellular = isCellularConnected()
            
            "Network - Available: $isAvailable, Internet: $hasInternet, Type: $networkType, WiFi: $isWiFi, Cellular: $isCellular"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network info", e)
            "Network info unavailable"
        }
    }
    
    /**
     * Check if the device has a stable internet connection by attempting to reach a reliable server
     */
    fun hasStableConnection(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking stable connection", e)
            // Fallback to basic connectivity check
            hasInternetConnectivity()
        }
    }
    
    /**
     * Get network quality assessment
     */
    fun getNetworkQuality(): NetworkQuality {
        return when {
            !isNetworkAvailable() -> NetworkQuality.NO_CONNECTION
            !hasInternetConnectivity() -> NetworkQuality.LIMITED_CONNECTION
            isWiFiConnected() -> NetworkQuality.GOOD
            isCellularConnected() -> NetworkQuality.FAIR
            else -> NetworkQuality.UNKNOWN
        }
    }
    
    /**
     * Network quality levels
     */
    enum class NetworkQuality {
        NO_CONNECTION,
        LIMITED_CONNECTION,
        FAIR,
        GOOD,
        UNKNOWN
    }
    
    /**
     * Get user-friendly network status message
     */
    fun getNetworkStatusMessage(): String {
        return when (getNetworkQuality()) {
            NetworkQuality.NO_CONNECTION -> "No internet connection"
            NetworkQuality.LIMITED_CONNECTION -> "Limited connectivity"
            NetworkQuality.FAIR -> "Connected via mobile data"
            NetworkQuality.GOOD -> "Connected via WiFi"
            NetworkQuality.UNKNOWN -> "Connection status unknown"
        }
    }
    
    /**
     * Check if network error is temporary and should be retried
     */
    fun isTemporaryNetworkError(exception: Throwable): Boolean {
        return when (exception) {
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.util.concurrent.TimeoutException -> true
            is java.net.UnknownHostException -> {
                // DNS issues might be temporary
                hasInternetConnectivity()
            }
            else -> false
        }
    }
}