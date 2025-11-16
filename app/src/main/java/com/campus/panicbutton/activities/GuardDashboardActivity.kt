package com.campus.panicbutton.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campus.panicbutton.R
import com.campus.panicbutton.adapters.AlertsAdapter
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.models.CampusStructure
import com.campus.panicbutton.models.User
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.AdapterView
import com.campus.panicbutton.repository.OfflineRepository
import com.campus.panicbutton.utils.Constants
import com.campus.panicbutton.utils.ErrorHandler
import com.campus.panicbutton.utils.LoadingManager
import com.campus.panicbutton.utils.LocationPermissionHandler
import com.campus.panicbutton.utils.SimpleNotificationManager
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.services.LocationService
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Guard Dashboard Activity - Main interface for security guards
 * Features:
 * - Prominent panic button for emergency alerts
 * - Real-time alerts list with accept/resolve actions
 * - Location detection and display
 * - Real-time updates using Firestore listeners
 */
class GuardDashboardActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "GuardDashboardActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
        const val EXTRA_USER = "extra_user"
    }
    
    // UI Components
    private lateinit var btnPanic: Button
    private lateinit var tvGuardName: TextView
    private lateinit var tvCurrentLocation: TextView
    private lateinit var rvAlerts: RecyclerView
    private lateinit var tvNoAlerts: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutOfflineStatus: View
    private lateinit var tvOfflineStatus: TextView
    private lateinit var tvSyncStatus: TextView
    
    // Services
    private lateinit var firebaseService: FirebaseService
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var errorHandler: ErrorHandler
    private lateinit var loadingManager: LoadingManager
    private lateinit var locationPermissionHandler: LocationPermissionHandler
    private lateinit var simpleNotificationManager: SimpleNotificationManager
    
    // Data
    private lateinit var currentUser: User
    private lateinit var alertsAdapter: AlertsAdapter
    private var alertsListener: ListenerRegistration? = null
    private var currentLocation: CampusBlock? = null
    private var previousAlertIds = mutableSetOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guard_dashboard)
        
        // Get user data from intent
        currentUser = intent.getSerializableExtra(EXTRA_USER) as? User
            ?: run {
                Log.e(TAG, "No user data provided")
                finish()
                return
            }
        
        initializeServices()
        initializeViews()
        setupRecyclerView()
        setupPanicButton()
        requestLocationPermission()
        requestNotificationPermission()
        startListeningToAlerts()
        observeOfflineData()
        
        // Start background alert listener service
        startBackgroundAlertService()
        
        Log.d(TAG, "Guard Dashboard initialized for user: ${currentUser.name}")
    }
    
    private fun initializeServices() {
        firebaseService = FirebaseService()
        offlineRepository = OfflineRepository(this, firebaseService)
        errorHandler = ErrorHandler(this)
        loadingManager = LoadingManager(this)
        locationPermissionHandler = LocationPermissionHandler(this, this)
        simpleNotificationManager = SimpleNotificationManager(this)
    }
    
    private fun initializeViews() {
        btnPanic = findViewById(R.id.btnPanic)
        tvGuardName = findViewById(R.id.tvGuardName)
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)
        rvAlerts = findViewById(R.id.rvAlerts)
        tvNoAlerts = findViewById(R.id.tvNoAlerts)
        progressBar = findViewById(R.id.progressBar)
        layoutOfflineStatus = findViewById(R.id.layoutOfflineStatus)
        tvOfflineStatus = findViewById(R.id.tvOfflineStatus)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
        val layoutProfile = findViewById<LinearLayout>(R.id.layoutProfile)
        
        // Set guard name
        tvGuardName.text = currentUser.name
        Log.d(TAG, "Setting guard name: ${currentUser.name}")
        
        // Setup profile click
        layoutProfile.setOnClickListener {
            showProfilePopup()
        }
        
        // Setup logout button
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
        
        // Start observing connectivity
        observeConnectivity()
    }
    
    private fun setupRecyclerView() {
        alertsAdapter = AlertsAdapter(
            currentUserId = currentUser.id,
            onAcceptAlert = { alert -> acceptAlert(alert) },
            onResolveAlert = { alert -> resolveAlert(alert) },
            onAlertClick = { alert -> openAlertDetails(alert) }
        )
        
        rvAlerts.apply {
            layoutManager = LinearLayoutManager(this@GuardDashboardActivity)
            adapter = alertsAdapter
        }
    }
    
    private fun setupPanicButton() {
        btnPanic.setOnClickListener {
            showPanicConfirmationDialog()
        }
    }
    
    private fun showPanicConfirmationDialog() {
        // Show location selection dialog directly
        showLocationSelectionDialog()
    }
    
    private fun showLocationSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_location_selection, null)
        
        val spinnerDepartment = dialogView.findViewById<Spinner>(R.id.spinnerDepartment)
        val spinnerLocation = dialogView.findViewById<Spinner>(R.id.spinnerLocation)
        val etMessage = dialogView.findViewById<EditText>(R.id.etMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSendAlert = dialogView.findViewById<Button>(R.id.btnSendAlert)
        
        // Setup department spinner
        val departments = CampusStructure.getAllDepartments()
        val departmentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = departmentAdapter
        
        // Setup location spinner based on selected department
        var selectedDepartment = departments[0]
        var selectedLocation = ""
        
        fun updateLocationSpinner(department: String) {
            val locations = CampusStructure.getLocationsForDepartment(department)
            val locationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locations)
            locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerLocation.adapter = locationAdapter
            if (locations.isNotEmpty()) {
                selectedLocation = locations[0]
            }
        }
        
        // Initialize location spinner with first department
        updateLocationSpinner(selectedDepartment)
        
        // Handle department selection changes
        spinnerDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDepartment = departments[position]
                updateLocationSpinner(selectedDepartment)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Handle location selection changes
        spinnerLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val locations = CampusStructure.getLocationsForDepartment(selectedDepartment)
                if (position < locations.size) {
                    selectedLocation = locations[position]
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSendAlert.setOnClickListener {
            val message = etMessage.text.toString().trim()
            val locationText = "$selectedDepartment - $selectedLocation"
            
            dialog.dismiss()
            createEmergencyAlertWithLocation(locationText, message.takeIf { it.isNotEmpty() })
        }
        
        dialog.show()
    }
    
    private fun createEmergencyAlertWithLocation(locationText: String, message: String?) {
        showLoading(true)
        
        try {
            val fullMessage = if (message != null) {
                "Location: $locationText\n\n$message"
            } else {
                "Location: $locationText"
            }
            
            val alert = Alert(
                id = "", // Firebase will generate
                guardId = currentUser.id,
                guardName = currentUser.name,
                message = fullMessage,
                location = null, // Using text location instead
                timestamp = com.google.firebase.Timestamp.now(),
                status = AlertStatus.ACTIVE
            )
            
            // Create alert in Firebase
            Log.d(TAG, "Creating alert with location: $locationText")
            firebaseService.createAlert(alert)
                .addOnSuccessListener { documentReference ->
                    showLoading(false)
                    Log.d(TAG, "Emergency alert created successfully: ${documentReference.id}")
                    Toast.makeText(this, "Emergency alert sent successfully!", Toast.LENGTH_SHORT).show()
                    
                    // Manually trigger alert list refresh
                    startListeningToAlerts()
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Log.e(TAG, "Failed to create emergency alert", exception)
                    Toast.makeText(this, "Failed to send alert: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            showLoading(false)
            Log.e(TAG, "Error creating alert with location", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createEmergencyAlert() {
        showLoading(true)
        
        try {
            // Show message input dialog first, then handle location
            showMessageInputDialog { message ->
                // Create alert with basic location (simplified approach)
                createSimpleAlert(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in createEmergencyAlert", e)
            showLoading(false)
            Toast.makeText(this, "Error creating alert: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createSimpleAlert(message: String?) {
        try {
            val alert = Alert(
                id = "", // Firebase will generate
                guardId = currentUser.id,
                guardName = currentUser.name,
                message = message ?: "Emergency alert",
                location = null, // No specific location
                timestamp = com.google.firebase.Timestamp.now(),
                status = AlertStatus.ACTIVE
            )
            
            // Create alert in Firebase
            Log.d(TAG, "Creating alert: $alert")
            firebaseService.createAlert(alert)
                .addOnSuccessListener { documentReference ->
                    showLoading(false)
                    Log.d(TAG, "Emergency alert created successfully: ${documentReference.id}")
                    Toast.makeText(this, "Emergency alert sent successfully!", Toast.LENGTH_SHORT).show()
                    
                    // Manually trigger alert list refresh
                    startListeningToAlerts()
                }
                .addOnFailureListener { exception ->
                    showLoading(false)
                    Log.e(TAG, "Failed to create emergency alert", exception)
                    Toast.makeText(this, "Failed to send alert: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            showLoading(false)
            Log.e(TAG, "Error creating simple alert", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Create alert with comprehensive error handling
     */
    private fun createAlertWithErrorHandling(location: CampusBlock?, message: String?) {
        firebaseService.createAlertWithErrorHandling(
            guardId = currentUser.id,
            guardName = currentUser.name,
            location = location,
            message = message,
            onSuccess = { alertId ->
                showLoading(false)
                val statusMessage = if (offlineRepository.isOnline()) {
                    getString(R.string.success_alert_created)
                } else {
                    "Emergency alert queued - will send when online"
                }
                loadingManager.showSuccess(btnPanic, statusMessage)
                Log.d(TAG, "Emergency alert created: $alertId")
            },
            onError = { errorMessage, isRetryable ->
                showLoading(false)
                Log.e(TAG, "Failed to create emergency alert: $errorMessage")
                
                if (isRetryable) {
                    loadingManager.showErrorWithRetry(
                        view = btnPanic,
                        message = errorMessage,
                        retryAction = { createAlertWithErrorHandling(location, message) }
                    )
                } else {
                    // Try offline fallback
                    createAlertOfflineFallback(location, message, errorMessage)
                }
            }
        )
    }
    
    /**
     * Fallback to offline alert creation
     */
    private fun createAlertOfflineFallback(location: CampusBlock?, message: String?, originalError: String) {
        val alert = Alert(
            guardId = currentUser.id,
            guardName = currentUser.name,
            location = location,
            message = message
        )
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val task = offlineRepository.createAlert(alert)
                task.addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        loadingManager.showSuccess(
                            btnPanic,
                            "Emergency alert queued for offline sync"
                        )
                        Log.d(TAG, "Emergency alert queued offline: ${result.result}")
                    } else {
                        loadingManager.showOperationFeedback(
                            view = btnPanic,
                            message = "Failed to create alert: $originalError",
                            isSuccess = false
                        )
                        Log.e(TAG, "Failed to create offline alert", result.exception)
                    }
                }
            } catch (e: Exception) {
                loadingManager.showOperationFeedback(
                    view = btnPanic,
                    message = "Critical error: Unable to create alert",
                    isSuccess = false
                )
                Log.e(TAG, "Exception creating offline alert", e)
            }
        }
    }
    
    private fun showMessageInputDialog(onMessageEntered: (String?) -> Unit) {
        val input = android.widget.EditText(this)
        input.hint = "Optional: Describe the emergency"
        
        AlertDialog.Builder(this)
            .setTitle("Emergency Details")
            .setMessage("Add an optional message to describe the emergency:")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val message = input.text.toString().trim().takeIf { it.isNotEmpty() }
                onMessageEntered(message)
            }
            .setNegativeButton("Send without message") { _, _ ->
                onMessageEntered(null)
            }
            .show()
    }
    
    private fun acceptAlert(alert: Alert) {
        showLoading(true)
        
        firebaseService.updateAlertStatusWithErrorHandling(
            alertId = alert.id,
            status = AlertStatus.IN_PROGRESS,
            userId = currentUser.id,
            onSuccess = {
                showLoading(false)
                val statusMessage = if (offlineRepository.isOnline()) {
                    getString(R.string.success_alert_accepted)
                } else {
                    "Alert accepted - will sync when online"
                }
                loadingManager.showSuccess(rvAlerts, statusMessage)
                Log.d(TAG, "Alert accepted: ${alert.id}")
            },
            onError = { errorMessage, isRetryable ->
                showLoading(false)
                Log.e(TAG, "Failed to accept alert: $errorMessage")
                
                if (isRetryable) {
                    loadingManager.showErrorWithRetry(
                        view = rvAlerts,
                        message = errorMessage,
                        retryAction = { acceptAlert(alert) }
                    )
                } else {
                    // Try offline fallback
                    acceptAlertOfflineFallback(alert, errorMessage)
                }
            }
        )
    }
    
    /**
     * Fallback to offline alert acceptance
     */
    private fun acceptAlertOfflineFallback(alert: Alert, originalError: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val task = offlineRepository.updateAlertStatus(alert.id, AlertStatus.IN_PROGRESS, currentUser.id)
                task.addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        loadingManager.showSuccess(
                            rvAlerts,
                            "Alert accepted - will sync when online"
                        )
                        Log.d(TAG, "Alert accepted offline: ${alert.id}")
                    } else {
                        loadingManager.showOperationFeedback(
                            view = rvAlerts,
                            message = "Failed to accept alert: $originalError",
                            isSuccess = false
                        )
                        Log.e(TAG, "Failed to accept alert offline", result.exception)
                    }
                }
            } catch (e: Exception) {
                loadingManager.showOperationFeedback(
                    view = rvAlerts,
                    message = "Unable to accept alert",
                    isSuccess = false
                )
                Log.e(TAG, "Exception accepting alert offline", e)
            }
        }
    }
    
    private fun resolveAlert(alert: Alert) {
        AlertDialog.Builder(this)
            .setTitle("Resolve Alert")
            .setMessage("Mark this alert as resolved?")
            .setPositiveButton("Resolve") { _, _ ->
                showLoading(true)
                
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val task = offlineRepository.updateAlertStatus(alert.id, AlertStatus.RESOLVED, currentUser.id)
                        task.addOnCompleteListener { result ->
                            showLoading(false)
                            if (result.isSuccessful) {
                                val statusMessage = if (offlineRepository.isOnline()) {
                                    "Alert resolved"
                                } else {
                                    "Alert resolved - will sync when online"
                                }
                                Toast.makeText(this@GuardDashboardActivity, statusMessage, Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Alert resolved: ${alert.id}")
                            } else {
                                val error = result.exception?.message ?: "Failed to resolve alert"
                                Toast.makeText(this@GuardDashboardActivity, error, Toast.LENGTH_LONG).show()
                                Log.e(TAG, "Failed to resolve alert", result.exception)
                            }
                        }
                    } catch (e: Exception) {
                        showLoading(false)
                        Toast.makeText(this@GuardDashboardActivity, "Failed to resolve alert: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Exception resolving alert", e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAlertDetails(alert: Alert) {
        val intent = Intent(this, AlertDetailsActivity::class.java).apply {
            putExtra(Constants.EXTRA_ALERT_ID, alert.id)
        }
        startActivity(intent)
    }
    
    private fun startListeningToAlerts() {
        Log.d(TAG, "Starting to listen for alerts")
        
        alertsListener = firebaseService.addAlertsListener { alerts ->
            Log.d(TAG, "Received ${alerts.size} alerts")
            
            // Filter only active and in-progress alerts for notifications
            val activeAlerts = alerts.filter { alert ->
                alert.status == AlertStatus.ACTIVE ||
                alert.status == AlertStatus.IN_PROGRESS
            }
            
            // Check for new alerts and show notifications (only for active alerts)
            activeAlerts.forEach { alert ->
                if (!previousAlertIds.contains(alert.id) && alert.guardId != currentUser.id) {
                    // This is a new alert from another guard
                    simpleNotificationManager.showAlertNotification(alert)
                    Log.d(TAG, "Showing notification for new alert: ${alert.id}")
                }
            }
            
            // Update previous alert IDs (only track active alerts)
            previousAlertIds.clear()
            previousAlertIds.addAll(activeAlerts.map { it.id })
            
            // Show all alerts for guards (including history)
            val relevantAlerts = alerts.sortedByDescending { alert ->
                alert.timestamp.seconds
            }.take(20) // Show last 20 alerts
            
            runOnUiThread {
                updateAlertsList(relevantAlerts)
            }
        }
    }
    
    private fun updateAlertsList(alerts: List<Alert>) {
        if (alerts.isEmpty()) {
            rvAlerts.visibility = View.GONE
            tvNoAlerts.visibility = View.VISIBLE
        } else {
            rvAlerts.visibility = View.VISIBLE
            tvNoAlerts.visibility = View.GONE
            alertsAdapter.submitList(alerts)
        }
    }
    
    private fun requestLocationPermission() {
        try {
            locationPermissionHandler.requestLocationPermissions(object : LocationPermissionHandler.PermissionCallback {
                override fun onPermissionGranted() {
                    Log.d(TAG, "Location permission granted")
                    runOnUiThread {
                        tvCurrentLocation.text = "Location permission granted"
                    }
                }
                
                override fun onPermissionDenied(permanentlyDenied: Boolean) {
                    Log.w(TAG, "Location permission denied. Permanently: $permanentlyDenied")
                    runOnUiThread {
                        tvCurrentLocation.text = if (permanentlyDenied) {
                            "Location access disabled"
                        } else {
                            "Location permission required"
                        }
                    }
                }
                
                override fun onPermissionRationale() {
                    Log.d(TAG, "Location permission rationale shown")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location permission", e)
            runOnUiThread {
                tvCurrentLocation.text = "Location setup error"
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHandler.handlePermissionResult(requestCode, permissions, grantResults)
    }
    
    /**
     * Get current location with comprehensive error handling
     */
    private fun getCurrentLocationWithErrorHandling(onLocationReceived: (CampusBlock?) -> Unit) {
        tvCurrentLocation.text = getString(R.string.loading_getting_location)
        
        val locationService = LocationService()
        locationService.getLocationWithBlockAndErrorHandling(
            onSuccess = { location, block ->
                runOnUiThread {
                    if (block != null) {
                        tvCurrentLocation.text = block.name
                        Log.d(TAG, "Location detected: ${block.name}")
                        onLocationReceived(block)
                    } else {
                        tvCurrentLocation.text = "Location detected - no campus block match"
                        Log.w(TAG, "Location detected but could not map to campus block")
                        showManualLocationSelection(location, onLocationReceived)
                    }
                }
            },
            onError = { errorMessage, isRetryable, actionText ->
                runOnUiThread {
                    tvCurrentLocation.text = "Location detection failed"
                    Log.e(TAG, "Failed to get current location: $errorMessage")
                    
                    if (isRetryable) {
                        loadingManager.showErrorWithRetry(
                            view = tvCurrentLocation,
                            message = errorMessage,
                            retryAction = { getCurrentLocationWithErrorHandling(onLocationReceived) }
                        )
                    } else {
                        loadingManager.showOperationFeedback(
                            view = tvCurrentLocation,
                            message = errorMessage,
                            isSuccess = false
                        )
                        
                        // Offer manual location selection as fallback
                        showManualLocationSelection(null, onLocationReceived)
                    }
                }
            },
            onPermissionRequired = {
                runOnUiThread {
                    tvCurrentLocation.text = "Location permission required"
                    requestLocationPermission()
                }
            },
            onManualSelectionRequired = { location ->
                runOnUiThread {
                    showManualLocationSelection(location, onLocationReceived)
                }
            }
        )
    }
    
    /**
     * Show manual location selection dialog
     */
    private fun showManualLocationSelection(
        detectedLocation: android.location.Location?,
        onLocationReceived: (CampusBlock?) -> Unit
    ) {
        val intent = Intent(this, ManualBlockSelectionActivity::class.java)
        detectedLocation?.let {
            intent.putExtra("detected_latitude", it.latitude)
            intent.putExtra("detected_longitude", it.longitude)
        }
        
        // For now, just set unknown location and continue
        tvCurrentLocation.text = "Manual selection required"
        onLocationReceived(null)
        
        // Could launch ManualBlockSelectionActivity here if needed
        // startActivityForResult(intent, MANUAL_LOCATION_REQUEST_CODE)
    }
    
    private fun showLoading(show: Boolean) {
        if (show) {
            loadingManager.showButtonLoading(
                button = btnPanic,
                progressBar = progressBar,
                loadingText = getString(R.string.loading_creating_alert),
                operationId = "emergency_alert"
            )
        } else {
            loadingManager.hideButtonLoading(
                button = btnPanic,
                progressBar = progressBar,
                originalText = getString(R.string.panic_button),
                operationId = "emergency_alert"
            )
        }
    }
    
    /**
     * Observe network connectivity and update UI accordingly
     */
    private fun observeConnectivity() {
        CoroutineScope(Dispatchers.Main).launch {
            offlineRepository.observeConnectivity().collect { isOnline ->
                updateOfflineStatus(isOnline)
            }
        }
    }
    
    /**
     * Observe offline data from Room database
     */
    private fun observeOfflineData() {
        CoroutineScope(Dispatchers.Main).launch {
            offlineRepository.getActiveAlerts().collect { alerts ->
                // Filter to show only active and in-progress alerts for guards
                val relevantAlerts = alerts.filter { alert ->
                    alert.status == AlertStatus.ACTIVE || 
                    alert.status == AlertStatus.IN_PROGRESS ||
                    (alert.status == AlertStatus.RESOLVED && alert.acceptedBy == currentUser.id)
                }
                
                updateAlertsList(relevantAlerts)
                updateSyncStatus()
            }
        }
    }
    
    /**
     * Update offline status UI
     */
    private fun updateOfflineStatus(isOnline: Boolean) {
        if (isOnline) {
            layoutOfflineStatus.visibility = View.GONE
        } else {
            layoutOfflineStatus.visibility = View.VISIBLE
            tvOfflineStatus.text = "Offline - Data will sync when connection is restored"
        }
        updateSyncStatus()
    }
    
    /**
     * Update sync status information
     */
    private fun updateSyncStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            val syncStatus = offlineRepository.getSyncStatus()
            val statusText = if (syncStatus.isOnline) {
                "Online"
            } else {
                val pendingCount = syncStatus.pendingOperations + syncStatus.unsyncedAlerts
                if (pendingCount > 0) {
                    "$pendingCount pending"
                } else {
                    "Offline"
                }
            }
            tvSyncStatus.text = statusText
        }
    }
    
    /**
     * Show profile popup with user info and logout option
     */
    private fun showProfilePopup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_popup, null)
        
        val tvUserName = dialogView.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = dialogView.findViewById<TextView>(R.id.tvUserEmail)
        val tvUserRole = dialogView.findViewById<TextView>(R.id.tvUserRole)
        val btnLogoutDialog = dialogView.findViewById<Button>(R.id.btnLogoutDialog)
        
        // Set user info
        tvUserName.text = currentUser.name
        tvUserEmail.text = currentUser.email
        tvUserRole.text = currentUser.role.name
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        btnLogoutDialog.setOnClickListener {
            dialog.dismiss()
            showLogoutConfirmation()
        }
        
        dialog.show()
    }

    /**
     * Show logout confirmation dialog
     */
    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Perform logout operation
     */
    private fun performLogout() {
        Log.d(TAG, "Logging out user: ${currentUser.email}")
        
        // Sign out from Firebase
        firebaseService.signOut()
        
        // Clear any cached data
        offlineRepository.cleanup()
        
        // Navigate back to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Start background alert listener service
     * This keeps the app listening for alerts even when in background
     */
    private fun startBackgroundAlertService() {
        try {
            com.campus.panicbutton.services.AlertListenerService.start(this)
            Log.d(TAG, "Background alert service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background alert service", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        alertsListener?.remove()
        offlineRepository.cleanup()
        loadingManager.cleanup()
        locationPermissionHandler.clearCallback()
        Log.d(TAG, "Guard Dashboard destroyed, listeners removed")
    }
}
