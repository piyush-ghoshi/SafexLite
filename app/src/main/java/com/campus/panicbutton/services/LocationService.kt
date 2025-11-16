package com.campus.panicbutton.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.utils.LocationUtils
import com.campus.panicbutton.utils.BatteryOptimizer
import com.campus.panicbutton.utils.PerformanceManager
import com.campus.panicbutton.utils.ErrorHandler
import com.campus.panicbutton.utils.LifecycleManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Service for location detection and campus block mapping with battery optimization
 * Integrates with FusedLocationProvider API for GPS location detection
 * and maps coordinates to predefined campus blocks
 */
class LocationService : Service() {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firestore = FirebaseFirestore.getInstance()
    private var campusBlocks: List<CampusBlock> = emptyList()
    
    // Performance optimizers
    private lateinit var batteryOptimizer: BatteryOptimizer
    private lateinit var performanceManager: PerformanceManager
    private val lifecycleManager = LifecycleManager.getInstance()
    
    companion object {
        private const val TAG = "LocationService"
        private const val CAMPUS_BLOCKS_COLLECTION = "campus_blocks"
        private const val LOCATION_TIMEOUT_MS = 10000L // 10 seconds
        
        /**
         * Static method to get current location from any context
         * @param context Application context
         * @return Task<Location?> Location result or null if unavailable
         */
        fun getCurrentLocation(context: Context): Task<Location?> {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            // Check location permissions
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && 
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Location permissions not granted")
                return Tasks.forResult(null)
            }
            
            Log.d(TAG, "Requesting current location")
            val cancellationTokenSource = CancellationTokenSource()
            
            return fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).continueWith { task ->
                if (task.isSuccessful) {
                    val location = task.result
                    if (location != null) {
                        Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
                        location
                    } else {
                        Log.w(TAG, "Location is null, trying last known location")
                        // Try to get last known location as fallback
                        try {
                            fusedLocationClient.lastLocation.result
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get last known location", e)
                            null
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get current location", task.exception)
                    null
                }
            }
        }
        
        /**
         * Load campus blocks from Firestore
         * @return Task<List<CampusBlock>> List of campus blocks
         */
        fun loadCampusBlocks(): Task<List<CampusBlock>> {
            Log.d(TAG, "Loading campus blocks from Firestore")
            val firestore = FirebaseFirestore.getInstance()
            
            return firestore.collection(CAMPUS_BLOCKS_COLLECTION)
                .get()
                .continueWith { task ->
                    if (task.isSuccessful) {
                        val blocks = task.result?.documents?.mapNotNull { document ->
                            try {
                                document.toObject(CampusBlock::class.java)?.copy(id = document.id)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse campus block: ${document.id}", e)
                                null
                            }
                        } ?: emptyList()
                        Log.d(TAG, "Loaded ${blocks.size} campus blocks")
                        blocks
                    } else {
                        Log.e(TAG, "Failed to load campus blocks", task.exception)
                        emptyList()
                    }
                }
        }
        
        /**
         * Get current location and map to campus block
         * @param context Application context
         * @return Task<CampusBlock?> Mapped campus block or null
         */
        fun getCurrentLocationWithBlock(context: Context): Task<CampusBlock?> {
            Log.d(TAG, "Getting current location with block mapping")
            
            return Tasks.whenAllComplete(
                getCurrentLocation(context),
                loadCampusBlocks()
            ).continueWith { task ->
                try {
                    val locationTask = task.result[0]
                    val blocksTask = task.result[1]
                    
                    if (locationTask.isSuccessful && blocksTask.isSuccessful) {
                        val location = locationTask.result as? Location
                        val blocks = blocksTask.result as? List<CampusBlock> ?: emptyList()
                        
                        if (location != null && blocks.isNotEmpty()) {
                            val mappedBlock = LocationUtils.mapLocationToBlock(location, blocks)
                            Log.d(TAG, "Location mapped to block: ${mappedBlock?.name ?: "None"}")
                            mappedBlock
                        } else {
                            Log.w(TAG, "Location or blocks unavailable for mapping")
                            null
                        }
                    } else {
                        Log.e(TAG, "Failed to get location or blocks for mapping")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during location mapping", e)
                    null
                }
            }
        }
        
        /**
         * Get location with enhanced accuracy checking and fallback options
         * @param context Application context
         * @return Task<LocationResult> Result containing location, block, and accuracy info
         */
        fun getLocationWithAccuracyCheck(context: Context): Task<LocationResult> {
            Log.d(TAG, "Getting location with accuracy check")
            
            return Tasks.whenAllComplete(
                getCurrentLocation(context),
                loadCampusBlocks()
            ).continueWith { task ->
                try {
                    val locationTask = task.result[0]
                    val blocksTask = task.result[1]
                    
                    if (locationTask.isSuccessful && blocksTask.isSuccessful) {
                        val location = locationTask.result as? Location
                        val blocks = blocksTask.result as? List<CampusBlock> ?: emptyList()
                        
                        when {
                            location == null -> {
                                Log.w(TAG, "Location unavailable")
                                LocationResult(
                                    location = null,
                                    block = null,
                                    accuracy = LocationAccuracy.UNAVAILABLE,
                                    requiresManualSelection = true
                                )
                            }
                            !LocationUtils.hasAcceptableAccuracy(location) -> {
                                Log.w(TAG, "Location accuracy too low: ${location.accuracy}m")
                                val nearestBlock = LocationUtils.findNearestBlock(location, blocks)
                                LocationResult(
                                    location = location,
                                    block = nearestBlock,
                                    accuracy = LocationAccuracy.LOW,
                                    requiresManualSelection = true
                                )
                            }
                            else -> {
                                val mappedBlock = LocationUtils.mapLocationToBlock(location, blocks)
                                val accuracy = if (mappedBlock != null) {
                                    LocationAccuracy.HIGH
                                } else {
                                    LocationAccuracy.MEDIUM
                                }
                                Log.d(TAG, "Location with good accuracy mapped to: ${mappedBlock?.name ?: "No block"}")
                                LocationResult(
                                    location = location,
                                    block = mappedBlock,
                                    accuracy = accuracy,
                                    requiresManualSelection = mappedBlock == null
                                )
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to get location or blocks")
                        LocationResult(
                            location = null,
                            block = null,
                            accuracy = LocationAccuracy.UNAVAILABLE,
                            requiresManualSelection = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during location accuracy check", e)
                    LocationResult(
                        location = null,
                        block = null,
                        accuracy = LocationAccuracy.UNAVAILABLE,
                        requiresManualSelection = true
                    )
                }
            }
        }
    }
    
    /**
     * Data class representing location result with accuracy information
     */
    data class LocationResult(
        val location: Location?,
        val block: CampusBlock?,
        val accuracy: LocationAccuracy,
        val requiresManualSelection: Boolean
    )
    
    /**
     * Enum representing location accuracy levels
     */
    enum class LocationAccuracy {
        HIGH,       // Good GPS accuracy and mapped to block
        MEDIUM,     // Good GPS accuracy but no block match
        LOW,        // Poor GPS accuracy
        UNAVAILABLE // No GPS location available
    }
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        batteryOptimizer = BatteryOptimizer.getInstance(this)
        performanceManager = PerformanceManager.getInstance(this)
        
        // Register with performance manager
        performanceManager.registerReference("location_service", this)
        
        Log.d(TAG, "LocationService created with battery optimization")
        
        // Load campus blocks on service creation
        loadCampusBlocks().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                campusBlocks = task.result
                Log.d(TAG, "Campus blocks loaded in service: ${campusBlocks.size}")
            } else {
                Log.e(TAG, "Failed to load campus blocks in service", task.exception)
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    /**
     * Get current location using FusedLocationProvider API
     * @return Task<Location?> Current location or null if unavailable
     */
    fun getCurrentLocation(): Task<Location?> {
        return LocationService.getCurrentLocation(this)
    }
    
    /**
     * Map current location to nearest campus block
     * @return Task<CampusBlock?> Mapped campus block or null
     */
    fun getCurrentLocationWithBlock(): Task<CampusBlock?> {
        return LocationService.getCurrentLocationWithBlock(this)
    }
    
    /**
     * Get location with accuracy check
     * @return Task<LocationResult> Location result with accuracy info
     */
    fun getLocationWithAccuracyCheck(): Task<LocationResult> {
        return LocationService.getLocationWithAccuracyCheck(this)
    }
    
    /**
     * Check if location permissions are granted
     * @return Boolean true if permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get cached campus blocks
     * @return List<CampusBlock> Currently loaded campus blocks
     */
    fun getCampusBlocks(): List<CampusBlock> {
        return campusBlocks
    }
    
    /**
     * Refresh campus blocks from Firestore
     * @return Task<List<CampusBlock>> Updated list of campus blocks
     */
    fun refreshCampusBlocks(): Task<List<CampusBlock>> {
        return loadCampusBlocks().continueWith { task ->
            if (task.isSuccessful) {
                campusBlocks = task.result
                Log.d(TAG, "Campus blocks refreshed: ${campusBlocks.size}")
            }
            campusBlocks
        }
    }
    
    /**
     * Enhanced location methods with comprehensive error handling
     */
    
    /**
     * Get current location with comprehensive error handling
     */
    fun getCurrentLocationWithErrorHandling(
        onSuccess: (Location) -> Unit,
        onError: (String, Boolean, String?) -> Unit, // message, isRetryable, actionText
        onPermissionRequired: () -> Unit
    ) {
        // Check permissions first
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            onPermissionRequired()
            return
        }
        
        getCurrentLocation()
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Location obtained successfully: ${location.latitude}, ${location.longitude}")
                    onSuccess(location)
                } else {
                    Log.w(TAG, "Location is null")
                    onError(
                        "Unable to determine your location. Please ensure GPS is enabled and try again.",
                        true,
                        "Enable GPS"
                    )
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get current location", exception)
                val errorHandler = ErrorHandler(this)
                val handledError = errorHandler.handleError(exception, "getCurrentLocation")
                onError(handledError.userMessage, handledError.isRetryable, handledError.actionText)
            }
    }
    
    /**
     * Get location with block mapping and comprehensive error handling
     */
    fun getLocationWithBlockAndErrorHandling(
        onSuccess: (Location, CampusBlock?) -> Unit,
        onError: (String, Boolean, String?) -> Unit,
        onPermissionRequired: () -> Unit,
        onManualSelectionRequired: (Location?) -> Unit
    ) {
        getCurrentLocationWithErrorHandling(
            onSuccess = { location ->
                // Try to map location to block
                loadCampusBlocks()
                    .addOnSuccessListener { blocks ->
                        val mappedBlock = LocationUtils.mapLocationToBlock(location, blocks)
                        if (mappedBlock != null) {
                            Log.d(TAG, "Location mapped to block: ${mappedBlock.name}")
                            onSuccess(location, mappedBlock)
                        } else {
                            Log.w(TAG, "Location could not be mapped to any campus block")
                            onManualSelectionRequired(location)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to load campus blocks for mapping", exception)
                        onError(
                            "Unable to load campus locations. Please try again or select manually.",
                            true,
                            null
                        )
                    }
            },
            onError = onError,
            onPermissionRequired = onPermissionRequired
        )
    }
    
    /**
     * Enhanced location accuracy check with detailed feedback
     */
    fun checkLocationAccuracyWithErrorHandling(
        onHighAccuracy: (Location, CampusBlock) -> Unit,
        onMediumAccuracy: (Location, CampusBlock?) -> Unit,
        onLowAccuracy: (Location?) -> Unit,
        onError: (String, Boolean) -> Unit,
        onPermissionRequired: () -> Unit
    ) {
        getLocationWithAccuracyCheck()
            .addOnSuccessListener { result ->
                when (result.accuracy) {
                    LocationAccuracy.HIGH -> {
                        result.block?.let { block ->
                            onHighAccuracy(result.location!!, block)
                        } ?: run {
                            result.location?.let { location ->
                                onMediumAccuracy(location, null)
                            } ?: onLowAccuracy(null)
                        }
                    }
                    LocationAccuracy.MEDIUM -> {
                        result.location?.let { location ->
                            onMediumAccuracy(location, result.block)
                        } ?: onLowAccuracy(null)
                    }
                    LocationAccuracy.LOW -> {
                        onLowAccuracy(result.location)
                    }
                    LocationAccuracy.UNAVAILABLE -> {
                        onLowAccuracy(null)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Location accuracy check failed", exception)
                if (exception is SecurityException) {
                    onPermissionRequired()
                } else {
                    val errorHandler = ErrorHandler(this)
                    val handledError = errorHandler.handleError(exception, "locationAccuracyCheck")
                    onError(handledError.userMessage, handledError.isRetryable)
                }
            }
    }
    
    // Battery Optimized Location Methods
    
    /**
     * Get optimized location with battery-aware caching
     */
    fun getOptimizedLocation(
        priority: BatteryOptimizer.LocationPriority = BatteryOptimizer.LocationPriority.BALANCED,
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        performanceManager.checkMemoryUsage()
        
        batteryOptimizer.getOptimizedLocation(
            requestId = "location_service_${System.currentTimeMillis()}",
            priority = priority,
            onSuccess = { location ->
                Log.d(TAG, "Optimized location obtained: ${location.latitude}, ${location.longitude}")
                onSuccess(location)
            },
            onError = { exception ->
                Log.e(TAG, "Optimized location request failed", exception)
                onError(exception)
            }
        )
    }
    
    /**
     * Get optimized location with block mapping
     */
    fun getOptimizedLocationWithBlock(
        priority: BatteryOptimizer.LocationPriority = BatteryOptimizer.LocationPriority.BALANCED,
        onSuccess: (Location, CampusBlock?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        getOptimizedLocation(
            priority = priority,
            onSuccess = { location ->
                val mappedBlock = LocationUtils.mapLocationToBlock(location, campusBlocks)
                onSuccess(location, mappedBlock)
            },
            onError = onError
        )
    }
    
    /**
     * Get last known location with battery optimization
     */
    fun getOptimizedLastKnownLocation(
        maxAge: Long = 60000L, // 1 minute
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        batteryOptimizer.getLastKnownLocation(
            maxAge = maxAge,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * Stop all location updates to save battery
     */
    fun stopLocationUpdatesForBattery() {
        batteryOptimizer.stopLocationUpdates()
        Log.d(TAG, "Location updates stopped for battery optimization")
    }
    
    /**
     * Clear location cache
     */
    fun clearLocationCache() {
        batteryOptimizer.clearCache()
        Log.d(TAG, "Location cache cleared")
    }
    
    /**
     * Get battery optimization statistics
     */
    fun getBatteryOptimizationStats(): BatteryOptimizer.BatteryStats {
        return batteryOptimizer.getBatteryStats()
    }
    
    /**
     * Cleanup resources and stop optimizations
     */
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop location updates
        batteryOptimizer.stopLocationUpdates()
        
        // Unregister from performance manager
        performanceManager.unregisterReference("location_service")
        
        Log.d(TAG, "LocationService destroyed with cleanup")
    }
    
    /**
     * Get performance statistics for location service
     */
    fun getLocationServiceStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        // Battery optimization stats
        stats["battery"] = getBatteryOptimizationStats()
        
        // Service stats
        stats["campusBlocksLoaded"] = campusBlocks.size
        stats["hasLocationPermissions"] = hasLocationPermissions()
        
        // Performance stats
        val perfStats = performanceManager.getPerformanceStats()
        stats["memory"] = perfStats.memoryInfo
        
        return stats
    }
}