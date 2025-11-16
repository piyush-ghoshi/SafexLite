package com.campus.panicbutton.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.campus.panicbutton.R
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.services.LocationService
import com.campus.panicbutton.utils.LocationHelper

/**
 * Test activity to demonstrate location services functionality
 * This shows how to integrate location detection with manual block selection
 */
class LocationTestActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var locationButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    
    private var selectedBlock: CampusBlock? = null
    
    companion object {
        private const val TAG = "LocationTestActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_test)
        
        initializeViews()
        setupClickListeners()
        updateStatus("Ready to test location services")
    }
    
    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        locationButton = findViewById(R.id.locationButton)
        progressBar = findViewById(R.id.progressBar)
        resultText = findViewById(R.id.resultText)
    }
    
    private fun setupClickListeners() {
        locationButton.setOnClickListener {
            testLocationServices()
        }
    }
    
    private fun testLocationServices() {
        Log.d(TAG, "Testing location services")
        updateStatus("Testing location services...")
        showLoading(true)
        
        // Check permissions first
        if (!LocationHelper.hasLocationPermissions(this)) {
            Log.d(TAG, "Location permissions not granted, requesting...")
            updateStatus("Requesting location permissions...")
            LocationHelper.requestLocationPermissions(this)
            showLoading(false)
            return
        }
        
        // Get location with automatic fallback
        LocationHelper.getLocationResult(this)
            .addOnSuccessListener { result ->
                Log.d(TAG, "Location result received: ${result.accuracy}")
                handleLocationResult(result)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get location", exception)
                updateStatus("Failed to get location: ${exception.message}")
                showLoading(false)
            }
    }
    
    private fun handleLocationResult(result: LocationService.LocationResult) {
        when {
            result.block != null -> {
                // Location successfully mapped to a block
                selectedBlock = result.block
                updateStatus("Location detected successfully!")
                updateResult("Block: ${result.block.name}\nAccuracy: ${result.accuracy}\nCoordinates: ${result.location?.latitude}, ${result.location?.longitude}")
                showLoading(false)
            }
            result.requiresManualSelection -> {
                // Need manual selection
                updateStatus("GPS unavailable or inaccurate, launching manual selection...")
                launchManualSelection(result)
            }
            else -> {
                // No block found but location available
                updateStatus("Location detected but no campus block found")
                updateResult("Location: ${result.location?.latitude}, ${result.location?.longitude}\nAccuracy: ${result.accuracy}\nNo matching campus block")
                showLoading(false)
            }
        }
    }
    
    private fun launchManualSelection(result: LocationService.LocationResult) {
        val reason = LocationHelper.getManualSelectionReason(result)
        val intent = Intent(this, ManualBlockSelectionActivity::class.java).apply {
            putExtra(ManualBlockSelectionActivity.EXTRA_REASON, reason)
        }
        startActivityForResult(intent, LocationHelper.MANUAL_BLOCK_SELECTION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (LocationHelper.isLocationPermissionGranted(requestCode, grantResults)) {
            Log.d(TAG, "Location permissions granted")
            updateStatus("Location permissions granted, retrying...")
            testLocationServices()
        } else {
            Log.w(TAG, "Location permissions denied")
            updateStatus("Location permissions denied. Manual selection will be used.")
            Toast.makeText(this, "Location permissions are required for automatic detection", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        val block = LocationHelper.handleManualBlockSelectionResult(requestCode, resultCode, data)
        if (block != null) {
            selectedBlock = block
            updateStatus("Manual block selection completed!")
            updateResult("Manually selected block: ${block.name}\nDescription: ${block.description}")
            showLoading(false)
        } else if (requestCode == LocationHelper.MANUAL_BLOCK_SELECTION_REQUEST_CODE) {
            updateStatus("Manual block selection cancelled")
            showLoading(false)
        }
    }
    
    private fun updateStatus(message: String) {
        statusText.text = message
        Log.d(TAG, "Status: $message")
    }
    
    private fun updateResult(message: String) {
        resultText.text = message
        resultText.visibility = View.VISIBLE
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        locationButton.isEnabled = !show
    }
}