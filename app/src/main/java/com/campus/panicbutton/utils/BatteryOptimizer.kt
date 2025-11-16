package com.campus.panicbutton.utils

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.ConcurrentHashMap

/**
 * Battery optimizer for location services
 * Implements intelligent location caching and request batching to minimize battery drain
 */
class BatteryOptimizer private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryOptimizer"
        private const val LOCATION_CACHE_DURATION_MS = 30000L // 30 seconds
        private const val MIN_LOCATION_INTERVAL_MS = 10000L // 10 seconds minimum
        private const val LOCATION_ACCURACY_THRESHOLD = 50f // 50 meters
        private const val MAX_LOCATION_AGE_MS = 60000L // 1 minute
        
        @Volatile
        private var INSTANCE: BatteryOptimizer? = null
        
        fun getInstance(context: Context): BatteryOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BatteryOptimizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCache = ConcurrentHashMap<String, CachedLocation>()
    private val pendingRequests = ConcurrentHashMap<String, LocationRequestInfo>()
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastLocationRequestTime = 0L
    private var cachedLocation: CachedLocation? = null
    private var activeLocationCallback: LocationCallback? = null
    
    /**
     * Get optimized location with intelligent caching
     */
    fun getOptimizedLocation(
        requestId: String = "default",
        priority: LocationPriority = LocationPriority.BALANCED,
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentTime = System.currentTimeMillis()
        
        // Check if we have a valid cached location
        cachedLocation?.let { cached ->
            if (currentTime - cached.timestamp < LOCATION_CACHE_DURATION_MS && 
                cached.location.accuracy <= LOCATION_ACCURACY_THRESHOLD) {
                Log.d(TAG, "Using cached location (${cached.location.accuracy}m accuracy)")
                onSuccess(cached.location)
                return
            }
        }
        
        // Check minimum interval between requests
        if (currentTime - lastLocationRequestTime < MIN_LOCATION_INTERVAL_MS) {
            // Queue the request for later
            queueLocationRequest(requestId, priority, onSuccess, onError)
            return
        }
        
        // Make new location request
        requestNewLocation(requestId, priority, onSuccess, onError)
    }
    
    /**
     * Queue location request for batching
     */
    private fun queueLocationRequest(
        requestId: String,
        priority: LocationPriority,
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val requestInfo = LocationRequestInfo(requestId, priority, onSuccess, onError)
        pendingRequests[requestId] = requestInfo
        
        val delay = MIN_LOCATION_INTERVAL_MS - (System.currentTimeMillis() - lastLocationRequestTime)
        handler.postDelayed({
            processPendingRequests()
        }, delay)
        
        Log.d(TAG, "Queued location request: $requestId (delay: ${delay}ms)")
    }
    
    /**
     * Process all pending location requests
     */
    private fun processPendingRequests() {
        if (pendingRequests.isEmpty()) return
        
        val requests = pendingRequests.values.toList()
        pendingRequests.clear()
        
        // Use highest priority from pending requests
        val highestPriority = requests.maxByOrNull { it.priority.value }?.priority ?: LocationPriority.BALANCED
        
        Log.d(TAG, "Processing ${requests.size} pending location requests with priority: $highestPriority")
        
        requestNewLocation("batch", highestPriority, 
            onSuccess = { location ->
                requests.forEach { it.onSuccess(location) }
            },
            onError = { error ->
                requests.forEach { it.onError(error) }
            }
        )
    }
    
    /**
     * Request new location from GPS
     */
    private fun requestNewLocation(
        requestId: String,
        priority: LocationPriority,
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            lastLocationRequestTime = System.currentTimeMillis()
            
            val locationRequest = LocationRequest.Builder(priority.toGmsPriority(), MIN_LOCATION_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .setMaxUpdateDelayMillis(5000L)
                .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
                .build()
            
            // Clean up previous callback
            activeLocationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
            
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        // Cache the location
                        cachedLocation = CachedLocation(location, System.currentTimeMillis())
                        
                        Log.d(TAG, "New location obtained: ${location.latitude}, ${location.longitude} " +
                                "(accuracy: ${location.accuracy}m)")
                        
                        onSuccess(location)
                        
                        // Remove location updates to save battery
                        fusedLocationClient.removeLocationUpdates(this)
                        activeLocationCallback = null
                    }
                }
            }
            
            activeLocationCallback = locationCallback
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            
            // Set timeout for location request
            handler.postDelayed({
                if (activeLocationCallback == locationCallback) {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    activeLocationCallback = null
                    onError(Exception("Location request timeout"))
                }
            }, 15000L) // 15 second timeout
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            onError(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location", e)
            onError(e)
        }
    }
    
    /**
     * Get last known location with age check
     */
    fun getLastKnownLocation(
        maxAge: Long = MAX_LOCATION_AGE_MS,
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val age = System.currentTimeMillis() - location.time
                        if (age <= maxAge) {
                            Log.d(TAG, "Using last known location (age: ${age}ms)")
                            onSuccess(location)
                        } else {
                            Log.w(TAG, "Last known location too old: ${age}ms")
                            onError(Exception("Last known location too old"))
                        }
                    } else {
                        Log.w(TAG, "No last known location available")
                        onError(Exception("No last known location"))
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get last known location", exception)
                    onError(exception)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            onError(e)
        }
    }
    
    /**
     * Clear location cache
     */
    fun clearCache() {
        cachedLocation = null
        locationCache.clear()
        Log.d(TAG, "Location cache cleared")
    }
    
    /**
     * Stop all location updates to save battery
     */
    fun stopLocationUpdates() {
        activeLocationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            activeLocationCallback = null
            Log.d(TAG, "Location updates stopped")
        }
    }
    
    /**
     * Get battery optimization statistics
     */
    fun getBatteryStats(): BatteryStats {
        val cacheHitRate = if (locationCache.isEmpty()) 0f else {
            locationCache.values.count { 
                System.currentTimeMillis() - it.timestamp < LOCATION_CACHE_DURATION_MS 
            }.toFloat() / locationCache.size
        }
        
        return BatteryStats(
            cachedLocationsCount = locationCache.size,
            cacheHitRate = cacheHitRate,
            lastLocationRequestTime = lastLocationRequestTime,
            pendingRequestsCount = pendingRequests.size,
            hasActiveCallback = activeLocationCallback != null
        )
    }
    
    /**
     * Location priority levels for battery optimization
     */
    enum class LocationPriority(val value: Int) {
        LOW_POWER(1),
        BALANCED(2),
        HIGH_ACCURACY(3);
        
        fun toGmsPriority(): Int = when (this) {
            LOW_POWER -> Priority.PRIORITY_LOW_POWER
            BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    
    /**
     * Data class for cached location
     */
    private data class CachedLocation(
        val location: Location,
        val timestamp: Long
    )
    
    /**
     * Data class for location request information
     */
    private data class LocationRequestInfo(
        val requestId: String,
        val priority: LocationPriority,
        val onSuccess: (Location) -> Unit,
        val onError: (Exception) -> Unit
    )
    
    /**
     * Data class for battery optimization statistics
     */
    data class BatteryStats(
        val cachedLocationsCount: Int,
        val cacheHitRate: Float,
        val lastLocationRequestTime: Long,
        val pendingRequestsCount: Int,
        val hasActiveCallback: Boolean
    )
}