package com.campus.panicbutton.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Utility class for handling location permissions with user-friendly dialogs
 * Provides comprehensive permission management for location services
 */
class LocationPermissionHandler(
    private val context: Context,
    private val activity: Activity? = null,
    private val fragment: Fragment? = null
) {
    
    companion object {
        private const val TAG = "LocationPermissionHandler"
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val LOCATION_SETTINGS_REQUEST_CODE = 1002
        
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    /**
     * Interface for permission result callbacks
     */
    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied(permanentlyDenied: Boolean)
        fun onPermissionRationale()
    }
    
    private var permissionCallback: PermissionCallback? = null
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if fine location permission is granted
     */
    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if coarse location permission is granted
     */
    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Request location permissions with proper rationale
     */
    fun requestLocationPermissions(callback: PermissionCallback) {
        this.permissionCallback = callback
        
        if (hasLocationPermissions()) {
            Log.d(TAG, "Location permissions already granted")
            callback.onPermissionGranted()
            return
        }
        
        // Check if we should show rationale
        val shouldShowRationale = REQUIRED_PERMISSIONS.any { permission ->
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false
        }
        
        if (shouldShowRationale) {
            Log.d(TAG, "Showing permission rationale")
            showPermissionRationale(callback)
        } else {
            Log.d(TAG, "Requesting location permissions directly")
            requestPermissionsInternal()
        }
    }
    
    /**
     * Show permission rationale dialog
     */
    private fun showPermissionRationale(callback: PermissionCallback) {
        AlertDialog.Builder(context)
            .setTitle("Location Permission Required")
            .setMessage(
                "This app needs location access to:\n\n" +
                "• Automatically detect your location during emergencies\n" +
                "• Map your position to campus blocks\n" +
                "• Help responders find you quickly\n\n" +
                "Your location is only used for emergency response and is not stored or shared."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                callback.onPermissionRationale()
                requestPermissionsInternal()
            }
            .setNegativeButton("Cancel") { _, _ ->
                callback.onPermissionDenied(false)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Request permissions from system
     */
    private fun requestPermissionsInternal() {
        when {
            activity != null -> {
                ActivityCompat.requestPermissions(
                    activity,
                    REQUIRED_PERMISSIONS,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            fragment != null -> {
                fragment.requestPermissions(
                    REQUIRED_PERMISSIONS,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            else -> {
                Log.e(TAG, "No activity or fragment available for permission request")
                permissionCallback?.onPermissionDenied(false)
            }
        }
    }
    
    /**
     * Handle permission request result
     * Call this from onRequestPermissionsResult in your Activity/Fragment
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) return
        
        val callback = permissionCallback ?: return
        
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Log.d(TAG, "Location permissions granted")
            callback.onPermissionGranted()
        } else {
            // Check if permission was permanently denied
            val permanentlyDenied = REQUIRED_PERMISSIONS.any { permission ->
                activity?.let { 
                    !ActivityCompat.shouldShowRequestPermissionRationale(it, permission) &&
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                } ?: false
            }
            
            Log.d(TAG, "Location permissions denied. Permanently denied: $permanentlyDenied")
            
            if (permanentlyDenied) {
                showPermanentlyDeniedDialog()
            } else {
                callback.onPermissionDenied(false)
            }
        }
    }
    
    /**
     * Show dialog when permission is permanently denied
     */
    private fun showPermanentlyDeniedDialog() {
        AlertDialog.Builder(context)
            .setTitle("Location Permission Required")
            .setMessage(
                "Location permission has been permanently denied. " +
                "To use emergency features, please enable location permission in app settings.\n\n" +
                "Go to Settings > Apps > Campus Panic Button > Permissions > Location"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Continue Without Location") { _, _ ->
                permissionCallback?.onPermissionDenied(true)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Open app settings for manual permission grant
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open settings", e2)
                permissionCallback?.onPermissionDenied(true)
            }
        }
    }
    
    /**
     * Show location services disabled dialog
     */
    fun showLocationServicesDisabledDialog(onEnableRequested: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Location Services Disabled")
            .setMessage(
                "Location services are turned off on your device. " +
                "Please enable location services to use emergency location features.\n\n" +
                "This will help responders find you quickly during emergencies."
            )
            .setPositiveButton("Enable Location") { _, _ ->
                onEnableRequested()
                openLocationSettings()
            }
            .setNegativeButton("Continue Without Location") { _, _ ->
                // User chooses to continue without location
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Open location settings
     */
    private fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open location settings", e)
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open settings", e2)
            }
        }
    }
    
    /**
     * Get permission status description for debugging
     */
    fun getPermissionStatusDescription(): String {
        val fineLocationStatus = if (hasFineLocationPermission()) "Granted" else "Denied"
        val coarseLocationStatus = if (hasCoarseLocationPermission()) "Granted" else "Denied"
        
        return "Fine Location: $fineLocationStatus, Coarse Location: $coarseLocationStatus"
    }
    
    /**
     * Check if location services are enabled on the device
     */
    fun areLocationServicesEnabled(): Boolean {
        return try {
            val locationMode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE
            )
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check location services status", e)
            // Assume enabled if we can't check
            true
        }
    }
    
    /**
     * Get comprehensive location status for debugging
     */
    fun getLocationStatusDescription(): String {
        val permissionsGranted = hasLocationPermissions()
        val servicesEnabled = areLocationServicesEnabled()
        val permissionDetails = getPermissionStatusDescription()
        
        return "Permissions: $permissionsGranted ($permissionDetails), Services: $servicesEnabled"
    }
    
    /**
     * Clear callback to prevent memory leaks
     */
    fun clearCallback() {
        permissionCallback = null
    }
}