package com.campus.panicbutton.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.campus.panicbutton.activities.ManualBlockSelectionActivity
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.services.LocationService
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/**
 * Helper class for location-related operations in activities
 * Provides convenient methods for location permission handling,
 * GPS location detection, and manual block selection integration
 */
object LocationHelper {
    
    private const val TAG = "LocationHelper"
    const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    const val MANUAL_BLOCK_SELECTION_REQUEST_CODE = 1002
    
    /**
     * Check if location permissions are granted
     * @param context Application context
     * @return Boolean true if permissions are granted
     */
    fun hasLocationPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Request location permissions from user
     * @param activity Activity to request permissions from
     */
    fun requestLocationPermissions(activity: Activity) {
        Log.d(TAG, "Requesting location permissions")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Check if location permission request was granted
     * @param requestCode Request code from onRequestPermissionsResult
     * @param grantResults Grant results array
     * @return Boolean true if location permissions were granted
     */
    fun isLocationPermissionGranted(requestCode: Int, grantResults: IntArray): Boolean {
        return requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.isNotEmpty() &&
                (grantResults[0] == PackageManager.PERMISSION_GRANTED ||
                 (grantResults.size > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED))
    }
    
    /**
     * Get location with automatic fallback to manual selection
     * This method handles the complete flow of location detection
     * @param activity Activity context for launching manual selection
     * @return Task<CampusBlock?> Selected or detected campus block
     */
    fun getLocationWithFallback(activity: Activity): Task<CampusBlock?> {
        Log.d(TAG, "Getting location with fallback for activity: ${activity.javaClass.simpleName}")
        
        if (!hasLocationPermissions(activity)) {
            Log.w(TAG, "Location permissions not granted, requesting manual selection")
            return launchManualBlockSelection(
                activity,
                ManualBlockSelectionActivity.REASON_GPS_UNAVAILABLE
            )
        }
        
        return LocationService.getLocationWithAccuracyCheck(activity)
            .continueWithTask { task ->
                if (task.isSuccessful) {
                    val result = task.result
                    when {
                        result.requiresManualSelection -> {
                            Log.d(TAG, "Manual selection required due to: ${result.accuracy}")
                            val reason = when (result.accuracy) {
                                LocationService.LocationAccuracy.UNAVAILABLE -> 
                                    ManualBlockSelectionActivity.REASON_GPS_UNAVAILABLE
                                LocationService.LocationAccuracy.LOW -> 
                                    ManualBlockSelectionActivity.REASON_LOW_ACCURACY
                                else -> 
                                    ManualBlockSelectionActivity.REASON_USER_CHOICE
                            }
                            launchManualBlockSelection(activity, reason)
                        }
                        result.block != null -> {
                            Log.d(TAG, "Location automatically mapped to: ${result.block.name}")
                            Tasks.forResult(result.block)
                        }
                        else -> {
                            Log.w(TAG, "No block found, launching manual selection")
                            launchManualBlockSelection(
                                activity,
                                ManualBlockSelectionActivity.REASON_USER_CHOICE
                            )
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get location", task.exception)
                    launchManualBlockSelection(
                        activity,
                        ManualBlockSelectionActivity.REASON_GPS_UNAVAILABLE
                    )
                }
            }
    }
    
    /**
     * Launch manual block selection activity
     * @param activity Activity to launch from
     * @param reason Reason for manual selection
     * @return Task<CampusBlock?> Task that completes when selection is made
     */
    private fun launchManualBlockSelection(activity: Activity, reason: String): Task<CampusBlock?> {
        Log.d(TAG, "Launching manual block selection with reason: $reason")
        
        val intent = Intent(activity, ManualBlockSelectionActivity::class.java).apply {
            putExtra(ManualBlockSelectionActivity.EXTRA_REASON, reason)
        }
        
        activity.startActivityForResult(intent, MANUAL_BLOCK_SELECTION_REQUEST_CODE)
        
        // Return a task that will be completed by the calling activity
        // when it receives the result in onActivityResult
        return Tasks.forResult(null)
    }
    
    /**
     * Handle result from manual block selection activity
     * Call this from onActivityResult in your activity
     * @param requestCode Request code from onActivityResult
     * @param resultCode Result code from onActivityResult
     * @param data Intent data from onActivityResult
     * @return CampusBlock? Selected block or null if cancelled
     */
    fun handleManualBlockSelectionResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): CampusBlock? {
        if (requestCode == MANUAL_BLOCK_SELECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val selectedBlock = data.getParcelableExtra<CampusBlock>(
                    ManualBlockSelectionActivity.EXTRA_SELECTED_BLOCK
                )
                Log.d(TAG, "Manual block selection result: ${selectedBlock?.name}")
                return selectedBlock
            } else {
                Log.d(TAG, "Manual block selection cancelled")
                return null
            }
        }
        return null
    }
    
    /**
     * Get location synchronously (for use in activities that handle async results)
     * @param context Application context
     * @return Task<LocationService.LocationResult> Location result with accuracy info
     */
    fun getLocationResult(context: Context): Task<LocationService.LocationResult> {
        return LocationService.getLocationWithAccuracyCheck(context)
    }
    
    /**
     * Check if manual block selection is needed based on location result
     * @param result LocationResult from location service
     * @return Boolean true if manual selection is recommended
     */
    fun shouldUseManualSelection(result: LocationService.LocationResult): Boolean {
        return result.requiresManualSelection
    }
    
    /**
     * Get appropriate reason for manual selection based on location result
     * @param result LocationResult from location service
     * @return String reason code for manual selection activity
     */
    fun getManualSelectionReason(result: LocationService.LocationResult): String {
        return when (result.accuracy) {
            LocationService.LocationAccuracy.UNAVAILABLE -> 
                ManualBlockSelectionActivity.REASON_GPS_UNAVAILABLE
            LocationService.LocationAccuracy.LOW -> 
                ManualBlockSelectionActivity.REASON_LOW_ACCURACY
            else -> 
                ManualBlockSelectionActivity.REASON_USER_CHOICE
        }
    }
}