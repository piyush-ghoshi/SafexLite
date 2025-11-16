package com.campus.panicbutton.utils

import android.location.Location
import android.util.Log
import com.campus.panicbutton.models.CampusBlock
import com.google.firebase.firestore.GeoPoint
import kotlin.math.*

/**
 * Utility class for validating and processing location data
 */
object LocationValidator {
    
    private const val TAG = "LocationValidator"
    
    // Campus bounds - should be configured for actual campus
    private const val CAMPUS_MIN_LAT = 40.0
    private const val CAMPUS_MAX_LAT = 41.0
    private const val CAMPUS_MIN_LNG = -75.0
    private const val CAMPUS_MAX_LNG = -74.0
    
    // Location accuracy thresholds
    private const val MIN_ACCURACY_METERS = 100.0f
    private const val MAX_ACCURACY_METERS = 10.0f
    
    // Maximum age for location data (5 minutes)
    private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1000L
    
    /**
     * Validate location accuracy and freshness
     */
    fun validateLocationData(location: Location): LocationValidationResult {
        val errors = mutableListOf<String>()
        
        // Check location accuracy
        if (!location.hasAccuracy()) {
            errors.add("Location accuracy not available")
        } else if (location.accuracy > MIN_ACCURACY_METERS) {
            errors.add("Location accuracy is too low (${location.accuracy}m)")
        }
        
        // Check location age
        val locationAge = System.currentTimeMillis() - location.time
        if (locationAge > MAX_LOCATION_AGE_MS) {
            errors.add("Location data is too old (${locationAge / 1000}s)")
        }
        
        // Validate coordinates
        val coordValidation = validateCoordinates(location.latitude, location.longitude)
        if (!coordValidation.isValid) {
            errors.addAll(coordValidation.errors)
        }
        
        return LocationValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            ageMs = locationAge
        )
    }
    
    /**
     * Validate coordinates are within valid ranges and campus bounds
     */
    fun validateCoordinates(latitude: Double, longitude: Double): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check basic coordinate validity
        if (latitude < -90 || latitude > 90) {
            errors.add("Invalid latitude: $latitude")
        }
        
        if (longitude < -180 || longitude > 180) {
            errors.add("Invalid longitude: $longitude")
        }
        
        // Check if coordinates are within campus bounds
        if (!isWithinCampusBounds(latitude, longitude)) {
            errors.add("Location is outside campus boundaries")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validate GeoPoint coordinates
     */
    fun validateGeoPoint(geoPoint: GeoPoint): ValidationResult {
        return validateCoordinates(geoPoint.latitude, geoPoint.longitude)
    }
    
    /**
     * Check if coordinates are within campus bounds
     */
    fun isWithinCampusBounds(latitude: Double, longitude: Double): Boolean {
        return latitude >= CAMPUS_MIN_LAT && latitude <= CAMPUS_MAX_LAT &&
               longitude >= CAMPUS_MIN_LNG && longitude <= CAMPUS_MAX_LNG
    }
    
    /**
     * Find the nearest campus block to given coordinates
     */
    fun findNearestBlock(
        latitude: Double,
        longitude: Double,
        campusBlocks: List<CampusBlock>
    ): CampusBlockMatch? {
        if (campusBlocks.isEmpty()) {
            Log.w(TAG, "No campus blocks available for matching")
            return null
        }
        
        val targetPoint = GeoPoint(latitude, longitude)
        var nearestBlock: CampusBlock? = null
        var shortestDistance = Double.MAX_VALUE
        
        for (block in campusBlocks) {
            val distance = calculateDistance(targetPoint, block.coordinates)
            
            // Check if location is within block radius
            if (distance <= block.radius && distance < shortestDistance) {
                nearestBlock = block
                shortestDistance = distance
            }
        }
        
        return if (nearestBlock != null) {
            CampusBlockMatch(
                block = nearestBlock,
                distance = shortestDistance,
                isWithinRadius = true
            )
        } else {
            // Find closest block even if outside radius
            val closestBlock = campusBlocks.minByOrNull { block ->
                calculateDistance(targetPoint, block.coordinates)
            }
            
            if (closestBlock != null) {
                val distance = calculateDistance(targetPoint, closestBlock.coordinates)
                CampusBlockMatch(
                    block = closestBlock,
                    distance = distance,
                    isWithinRadius = false
                )
            } else {
                null
            }
        }
    }
    
    /**
     * Calculate distance between two geographic points using Haversine formula
     */
    fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLngRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Sanitize location data by removing potentially invalid values
     */
    fun sanitizeLocation(location: Location): Location {
        val sanitized = Location(location)
        
        // Clamp coordinates to valid ranges
        sanitized.latitude = sanitized.latitude.coerceIn(-90.0, 90.0)
        sanitized.longitude = sanitized.longitude.coerceIn(-180.0, 180.0)
        
        // Remove accuracy if it's unreasonably high
        if (sanitized.hasAccuracy() && sanitized.accuracy > MIN_ACCURACY_METERS) {
            sanitized.removeAccuracy()
        }
        
        return sanitized
    }
    
    /**
     * Check if location has sufficient accuracy for campus block mapping
     */
    fun hasSufficientAccuracy(location: Location): Boolean {
        return location.hasAccuracy() && 
               location.accuracy <= MIN_ACCURACY_METERS &&
               location.accuracy >= MAX_ACCURACY_METERS
    }
    
    /**
     * Check if location is recent enough to be reliable
     */
    fun isLocationFresh(location: Location): Boolean {
        val age = System.currentTimeMillis() - location.time
        return age <= MAX_LOCATION_AGE_MS
    }
    
    /**
     * Get location quality score (0-100, higher is better)
     */
    fun getLocationQuality(location: Location): Int {
        var score = 100
        
        // Deduct points for poor accuracy
        if (location.hasAccuracy()) {
            val accuracyPenalty = (location.accuracy / MIN_ACCURACY_METERS * 50).toInt()
            score -= accuracyPenalty.coerceAtMost(50)
        } else {
            score -= 30 // No accuracy info
        }
        
        // Deduct points for age
        val age = System.currentTimeMillis() - location.time
        val agePenalty = (age / MAX_LOCATION_AGE_MS.toDouble() * 30).toInt()
        score -= agePenalty.coerceAtMost(30)
        
        // Deduct points if outside campus bounds
        if (!isWithinCampusBounds(location.latitude, location.longitude)) {
            score -= 20
        }
        
        return score.coerceAtLeast(0)
    }
    
    /**
     * Validate campus block coordinates and radius
     */
    fun validateCampusBlockLocation(block: CampusBlock): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate coordinates
        val coordValidation = validateGeoPoint(block.coordinates)
        if (!coordValidation.isValid) {
            errors.addAll(coordValidation.errors)
        }
        
        // Validate radius
        if (block.radius <= 0) {
            errors.add("Block radius must be positive")
        } else if (block.radius > 1000) {
            errors.add("Block radius is too large (max 1000m)")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
}

/**
 * Result of location validation
 */
data class LocationValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val accuracy: Float? = null,
    val ageMs: Long = 0
)

/**
 * Result of campus block matching
 */
data class CampusBlockMatch(
    val block: CampusBlock,
    val distance: Double,
    val isWithinRadius: Boolean
)