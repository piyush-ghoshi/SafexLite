package com.campus.panicbutton.utils

import android.location.Location
import android.util.Log
import com.campus.panicbutton.models.CampusBlock
import kotlin.math.*

/**
 * Utility class for location-related operations
 * Provides methods for mapping GPS coordinates to campus blocks
 * and calculating distances between locations
 */
object LocationUtils {
    
    private const val TAG = "LocationUtils"
    private const val EARTH_RADIUS_METERS = 6371000.0 // Earth's radius in meters
    
    /**
     * Maps GPS coordinates to the nearest campus block within its radius
     * @param location Current GPS location
     * @param campusBlocks List of available campus blocks
     * @return CampusBlock? The nearest campus block within radius, or null if none found
     */
    fun mapLocationToBlock(location: Location, campusBlocks: List<CampusBlock>): CampusBlock? {
        if (campusBlocks.isEmpty()) {
            Log.w(TAG, "No campus blocks available for mapping")
            return null
        }
        
        Log.d(TAG, "Mapping location (${location.latitude}, ${location.longitude}) to campus blocks")
        
        var nearestBlock: CampusBlock? = null
        var shortestDistance = Double.MAX_VALUE
        
        for (block in campusBlocks) {
            val distance = calculateDistance(
                location.latitude,
                location.longitude,
                block.coordinates.latitude,
                block.coordinates.longitude
            )
            
            Log.d(TAG, "Distance to ${block.name}: ${distance}m (radius: ${block.radius}m)")
            
            // Check if location is within the block's radius
            if (distance <= block.radius) {
                // If multiple blocks contain the location, choose the nearest one
                if (distance < shortestDistance) {
                    shortestDistance = distance
                    nearestBlock = block
                    Log.d(TAG, "Found closer block within radius: ${block.name}")
                }
            }
        }
        
        if (nearestBlock != null) {
            Log.d(TAG, "Location mapped to block: ${nearestBlock.name} (distance: ${shortestDistance}m)")
        } else {
            Log.w(TAG, "Location not within any campus block radius")
        }
        
        return nearestBlock
    }
    
    /**
     * Calculates the distance between two GPS coordinates using the Haversine formula
     * @param lat1 Latitude of first location
     * @param lon1 Longitude of first location
     * @param lat2 Latitude of second location
     * @param lon2 Longitude of second location
     * @return Double Distance in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Convert latitude and longitude from degrees to radians
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        
        // Calculate differences
        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad
        
        // Apply Haversine formula
        val a = sin(deltaLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        // Calculate distance in meters
        val distance = EARTH_RADIUS_METERS * c
        
        return distance
    }
    
    /**
     * Find the nearest campus block to a given location (regardless of radius)
     * Useful for fallback when no block contains the location
     * @param location Current GPS location
     * @param campusBlocks List of available campus blocks
     * @return CampusBlock? The nearest campus block, or null if no blocks available
     */
    fun findNearestBlock(location: Location, campusBlocks: List<CampusBlock>): CampusBlock? {
        if (campusBlocks.isEmpty()) {
            Log.w(TAG, "No campus blocks available for nearest block search")
            return null
        }
        
        Log.d(TAG, "Finding nearest block to location (${location.latitude}, ${location.longitude})")
        
        var nearestBlock: CampusBlock? = null
        var shortestDistance = Double.MAX_VALUE
        
        for (block in campusBlocks) {
            val distance = calculateDistance(
                location.latitude,
                location.longitude,
                block.coordinates.latitude,
                block.coordinates.longitude
            )
            
            if (distance < shortestDistance) {
                shortestDistance = distance
                nearestBlock = block
            }
        }
        
        if (nearestBlock != null) {
            Log.d(TAG, "Nearest block found: ${nearestBlock.name} (distance: ${shortestDistance}m)")
        }
        
        return nearestBlock
    }
    
    /**
     * Validate if GPS coordinates are reasonable (not null island, etc.)
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Boolean true if coordinates seem valid
     */
    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0
    }
    
    /**
     * Check if a location has sufficient accuracy for campus block mapping
     * @param location GPS location with accuracy information
     * @param maxAccuracyMeters Maximum acceptable accuracy in meters
     * @return Boolean true if location is accurate enough
     */
    fun hasAcceptableAccuracy(location: Location, maxAccuracyMeters: Float = 100f): Boolean {
        return location.hasAccuracy() && location.accuracy <= maxAccuracyMeters
    }
}