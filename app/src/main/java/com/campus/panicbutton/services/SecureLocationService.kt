package com.campus.panicbutton.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.utils.LocationValidator
import com.campus.panicbutton.utils.CampusBlockMatch
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.GeoPoint

/**
 * Secure location service with validation and security measures
 */
class SecureLocationService(
    private val context: Context,
    private val locationService: LocationService = LocationService()
) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    companion object {
        private const val TAG = "SecureLocationService"
        private const val LOCATION_TIMEOUT_MS = 15000L // 15 seconds
        private const val MAX_LOCATION_ATTEMPTS = 3
    }
    
    /**
     * Get current location with validation and security checks
     */
    fun getCurrentLocationSecure(): Task<SecureLocationResult> {
        Log.d(TAG, "Getting current location with security validation")
        
        // Check permissions
        if (!hasLocationPermissions()) {
            return Tasks.forResult(
                SecureLocationResult(
                    success = false,
                    error = "Location permissions not granted",
                    requiresPermission = true
                )
            )
        }
        
        return getCurrentLocationWithRetry(MAX_LOCATION_ATTEMPTS)
    }
    
    /**
     * Get current location and map to campus block with validation
     */
    fun getCurrentLocationAndBlock(): Task<LocationBlockResult> {
        Log.d(TAG, "Getting current location and mapping to campus block")
        
        return getCurrentLocationSecure().continueWithTask { locationTask ->
            if (!locationTask.isSuccessful || !locationTask.result.success) {
                val error = locationTask.result?.error ?: "Failed to get location"
                return@continueWithTask Tasks.forResult(
                    LocationBlockResult(
                        success = false,
                        error = error,
                        requiresPermission = locationTask.result?.requiresPermission ?: false
                    )
                )
            }
            
            val location = locationTask.result.location!!
            
            // Load campus blocks and find match
            LocationService.loadCampusBlocks().continueWith { blocksTask ->
                if (blocksTask.isSuccessful) {
                    val blocks = blocksTask.result
                    val blockMatch = LocationValidator.findNearestBlock(
                        location.latitude,
                        location.longitude,
                        blocks
                    )
                    
                    LocationBlockResult(
                        success = true,
                        location = location,
                        campusBlock = blockMatch?.block,
                        blockMatch = blockMatch,
                        locationQuality = LocationValidator.getLocationQuality(location)
                    )
                } else {
                    LocationBlockResult(
                        success = false,
                        error = "Failed to load campus blocks: ${blocksTask.exception?.message}",
                        location = location
                    )
                }
            }
        }
    }
    
    /**
     * Validate location data before using it
     */
    fun validateLocation(location: Location): Task<LocationValidationResult> {
        Log.d(TAG, "Validating location data")
        
        return Tasks.call {
            // Sanitize location first
            val sanitizedLocation = LocationValidator.sanitizeLocation(location)
            
            // Validate the sanitized location
            val validationResult = LocationValidator.validateLocationData(sanitizedLocation)
            
            LocationValidationResult(
                isValid = validationResult.isValid,
                errors = validationResult.errors,
                sanitizedLocation = if (validationResult.isValid) sanitizedLocation else null,
                quality = LocationValidator.getLocationQuality(sanitizedLocation)
            )
        }
    }
    
    /**
     * Validate coordinates before mapping to campus blocks
     */
    fun validateCoordinates(latitude: Double, longitude: Double): Task<CoordinateValidationResult> {
        Log.d(TAG, "Validating coordinates: $latitude, $longitude")
        
        return Tasks.call {
            val validation = LocationValidator.validateCoordinates(latitude, longitude)
            val isWithinCampus = LocationValidator.isWithinCampusBounds(latitude, longitude)
            
            CoordinateValidationResult(
                isValid = validation.isValid,
                errors = validation.errors,
                isWithinCampusBounds = isWithinCampus,
                geoPoint = if (validation.isValid) GeoPoint(latitude, longitude) else null
            )
        }
    }
    
    /**
     * Find campus block for given coordinates with validation
     */
    fun findCampusBlockSecure(latitude: Double, longitude: Double): Task<CampusBlockSearchResult> {
        Log.d(TAG, "Finding campus block for coordinates with validation")
        
        return validateCoordinates(latitude, longitude).continueWithTask { validationTask ->
            if (!validationTask.isSuccessful) {
                return@continueWithTask Tasks.forResult(
                    CampusBlockSearchResult(
                        success = false,
                        error = "Coordinate validation failed"
                    )
                )
            }
            
            val validation = validationTask.result
            if (!validation.isValid) {
                return@continueWithTask Tasks.forResult(
                    CampusBlockSearchResult(
                        success = false,
                        error = "Invalid coordinates: ${validation.errors.joinToString(", ")}",
                        isOutsideCampus = !validation.isWithinCampusBounds
                    )
                )
            }
            
            // Load campus blocks and search
            LocationService.loadCampusBlocks().continueWith { blocksTask ->
                if (blocksTask.isSuccessful) {
                    val blocks = blocksTask.result
                    val blockMatch = LocationValidator.findNearestBlock(latitude, longitude, blocks)
                    
                    CampusBlockSearchResult(
                        success = true,
                        campusBlock = blockMatch?.block,
                        blockMatch = blockMatch,
                        isOutsideCampus = !validation.isWithinCampusBounds
                    )
                } else {
                    CampusBlockSearchResult(
                        success = false,
                        error = "Failed to load campus blocks: ${blocksTask.exception?.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Check if app has required location permissions
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get current location with retry mechanism
     */
    private fun getCurrentLocationWithRetry(attemptsRemaining: Int): Task<SecureLocationResult> {
        if (attemptsRemaining <= 0) {
            return Tasks.forResult(
                SecureLocationResult(
                    success = false,
                    error = "Failed to get location after maximum attempts"
                )
            )
        }
        
        val cancellationTokenSource = CancellationTokenSource()
        
        return fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).continueWithTask { task ->
            if (task.isSuccessful && task.result != null) {
                val location = task.result
                
                // Validate the location
                validateLocation(location).continueWith { validationTask ->
                    if (validationTask.isSuccessful) {
                        val validation = validationTask.result
                        if (validation.isValid) {
                            SecureLocationResult(
                                success = true,
                                location = validation.sanitizedLocation,
                                quality = validation.quality
                            )
                        } else {
                            // Location is invalid, try again if attempts remaining
                            if (attemptsRemaining > 1) {
                                Log.w(TAG, "Location validation failed, retrying: ${validation.errors}")
                                return@continueWith getCurrentLocationWithRetry(attemptsRemaining - 1).result
                            } else {
                                SecureLocationResult(
                                    success = false,
                                    error = "Location validation failed: ${validation.errors.joinToString(", ")}",
                                    location = location
                                )
                            }
                        }
                    } else {
                        SecureLocationResult(
                            success = false,
                            error = "Location validation error: ${validationTask.exception?.message}",
                            location = location
                        )
                    }
                }
            } else {
                // Try last known location as fallback
                tryLastKnownLocation().continueWithTask { fallbackTask ->
                    if (fallbackTask.isSuccessful && fallbackTask.result.success) {
                        Tasks.forResult(fallbackTask.result)
                    } else if (attemptsRemaining > 1) {
                        Log.w(TAG, "Location request failed, retrying")
                        getCurrentLocationWithRetry(attemptsRemaining - 1)
                    } else {
                        Tasks.forResult(
                            SecureLocationResult(
                                success = false,
                                error = "Failed to get current location: ${task.exception?.message}"
                            )
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Try to get last known location as fallback
     */
    private fun tryLastKnownLocation(): Task<SecureLocationResult> {
        Log.d(TAG, "Trying last known location as fallback")
        
        if (!hasLocationPermissions()) {
            return Tasks.forResult(
                SecureLocationResult(
                    success = false,
                    error = "Location permissions not granted",
                    requiresPermission = true
                )
            )
        }
        
        return fusedLocationClient.lastLocation.continueWithTask { task ->
            if (task.isSuccessful && task.result != null) {
                val location = task.result
                validateLocation(location).continueWith { validationTask ->
                    if (validationTask.isSuccessful) {
                        val validation = validationTask.result
                        SecureLocationResult(
                            success = validation.isValid,
                            location = if (validation.isValid) validation.sanitizedLocation else location,
                            quality = validation.quality,
                            error = if (!validation.isValid) 
                                "Last known location is invalid: ${validation.errors.joinToString(", ")}" 
                                else null,
                            isLastKnown = true
                        )
                    } else {
                        SecureLocationResult(
                            success = false,
                            error = "Last known location validation failed: ${validationTask.exception?.message}",
                            location = location,
                            isLastKnown = true
                        )
                    }
                }
            } else {
                Tasks.forResult(
                    SecureLocationResult(
                        success = false,
                        error = "No last known location available"
                    )
                )
            }
        }
    }
}

/**
 * Result of secure location request
 */
data class SecureLocationResult(
    val success: Boolean,
    val location: Location? = null,
    val error: String? = null,
    val quality: Int = 0,
    val requiresPermission: Boolean = false,
    val isLastKnown: Boolean = false
)

/**
 * Result of location and block mapping
 */
data class LocationBlockResult(
    val success: Boolean,
    val location: Location? = null,
    val campusBlock: CampusBlock? = null,
    val blockMatch: CampusBlockMatch? = null,
    val error: String? = null,
    val locationQuality: Int = 0,
    val requiresPermission: Boolean = false
)

/**
 * Result of location validation
 */
data class LocationValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val sanitizedLocation: Location? = null,
    val quality: Int = 0
)

/**
 * Result of coordinate validation
 */
data class CoordinateValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val isWithinCampusBounds: Boolean,
    val geoPoint: GeoPoint? = null
)

/**
 * Result of campus block search
 */
data class CampusBlockSearchResult(
    val success: Boolean,
    val campusBlock: CampusBlock? = null,
    val blockMatch: CampusBlockMatch? = null,
    val error: String? = null,
    val isOutsideCampus: Boolean = false
)