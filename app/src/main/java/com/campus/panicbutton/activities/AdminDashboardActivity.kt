package com.campus.panicbutton.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.campus.panicbutton.R
import com.campus.panicbutton.adapters.AdminAlertsAdapter
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.User
import com.campus.panicbutton.repository.OfflineRepository
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.utils.Constants
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import com.google.android.gms.tasks.Task

class AdminDashboardActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_USER = "extra_user"
        private const val TAG = "AdminDashboardActivity"
    }
    
    // UI Components
    private lateinit var tvActiveCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvResolvedCount: TextView
    private lateinit var tvClosedCount: TextView
    private lateinit var spinnerStatusFilter: Spinner
    private lateinit var btnRefresh: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerViewAlerts: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutOfflineStatus: LinearLayout
    private lateinit var tvOfflineStatus: TextView
    private lateinit var tvSyncStatus: TextView
    
    // Data and Services
    private lateinit var firebaseService: FirebaseService
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var adminAlertsAdapter: AdminAlertsAdapter
    private lateinit var simpleNotificationManager: com.campus.panicbutton.utils.SimpleNotificationManager
    private var currentUser: User? = null
    private var allAlerts: List<Alert> = emptyList()
    private var filteredAlerts: List<Alert> = emptyList()
    private var alertsListener: ListenerRegistration? = null
    private var previousAlertIds = mutableSetOf<String>()
    
    // Filter options
    private lateinit var filterOptions: Array<String>
    private var currentFilter = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)
        
        // Get user from intent
        currentUser = intent.getSerializableExtra(EXTRA_USER) as? User
        if (currentUser == null) {
            Log.e(TAG, "No user provided in intent")
            finish()
            return
        }
        
        Log.d(TAG, "Admin Dashboard started for user: ${currentUser?.name}")
        
        initializeComponents()
        setupUI()
        setupRealTimeListener()
        
        // Start background alert listener service
        startBackgroundAlertService()
    }
    
    private fun initializeComponents() {
        // Initialize Firebase service
        firebaseService = FirebaseService()
        offlineRepository = OfflineRepository(this, firebaseService)
        simpleNotificationManager = com.campus.panicbutton.utils.SimpleNotificationManager(this)
        
        // Initialize UI components
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvInProgressCount = findViewById(R.id.tvInProgressCount)
        tvResolvedCount = findViewById(R.id.tvResolvedCount)
        tvClosedCount = findViewById(R.id.tvClosedCount)
        spinnerStatusFilter = findViewById(R.id.spinnerStatusFilter)
        btnRefresh = findViewById(R.id.btnRefresh)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerViewAlerts = findViewById(R.id.recyclerViewAlerts)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        layoutOfflineStatus = findViewById(R.id.layoutOfflineStatus)
        tvOfflineStatus = findViewById(R.id.tvOfflineStatus)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
        val layoutAdminProfile = findViewById<LinearLayout>(R.id.layoutAdminProfile)
        val tvAdminName = findViewById<TextView>(R.id.tvAdminName)
        
        // Set admin name
        tvAdminName.text = currentUser?.name ?: "Admin"
        Log.d(TAG, "Setting admin name: ${currentUser?.name}")
        
        // Setup profile click
        layoutAdminProfile.setOnClickListener {
            showProfilePopup()
        }
        
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
        
        // Initialize adapter
        adminAlertsAdapter = AdminAlertsAdapter(
            onCloseAlert = { alert -> showCloseAlertDialog(alert) },
            onReopenAlert = { alert -> reopenAlert(alert) },
            onAlertClick = { alert -> openAlertDetails(alert) }
        )

        filterOptions = resources.getStringArray(R.array.admin_dashboard_filter_options)
        currentFilter = filterOptions[0]
    }
    
    private fun setupUI() {
        // Setup RecyclerView
        recyclerViewAlerts.apply {
            layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
            adapter = adminAlertsAdapter
        }
        
        // Setup filter spinner
        val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatusFilter.adapter = filterAdapter
        
        spinnerStatusFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = filterOptions[position]
                applyFilter()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Setup refresh button
        btnRefresh.setOnClickListener {
            refreshAlerts()
        }
        
        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener {
            refreshAlerts()
        }
        
        // Setup swipe refresh colors
        swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent,
            R.color.emergency_red
        )
        
        // Initialize offline functionality
        observeConnectivity()
        observeOfflineData()
    }
    
    private fun setupRealTimeListener() {
        Log.d(TAG, "Setting up real-time alerts listener")
        
        alertsListener = firebaseService.addAlertsListener { alerts ->
            Log.d(TAG, "Received ${alerts.size} alerts from Firebase")
            
            // Check for new alerts and show notifications
            alerts.forEach { alert ->
                if (!previousAlertIds.contains(alert.id) && alert.guardId != currentUser?.id) {
                    // This is a new alert from another user
                    simpleNotificationManager.showAlertNotification(alert)
                    Log.d(TAG, "Showing notification for new alert: ${alert.id}")
                }
            }
            
            // Update previous alert IDs
            previousAlertIds.clear()
            previousAlertIds.addAll(alerts.map { it.id })
            
            allAlerts = alerts
            updateStatistics()
            applyFilter()
            swipeRefreshLayout.isRefreshing = false
        }
    }
    
    private fun updateStatistics() {
        val activeCount = allAlerts.count { it.status == AlertStatus.ACTIVE }
        val inProgressCount = allAlerts.count { it.status == AlertStatus.IN_PROGRESS }
        val resolvedCount = allAlerts.count { it.status == AlertStatus.RESOLVED }
        val closedCount = allAlerts.count { it.status == AlertStatus.CLOSED }
        
        tvActiveCount.text = activeCount.toString()
        tvInProgressCount.text = inProgressCount.toString()
        tvResolvedCount.text = resolvedCount.toString()
        tvClosedCount.text = closedCount.toString()
        
        Log.d(TAG, "Statistics updated - Active: $activeCount, In Progress: $inProgressCount, Resolved: $resolvedCount, Closed: $closedCount")
    }
    
    private fun applyFilter() {
        filteredAlerts = when (currentFilter) {
            getString(R.string.active_alerts_count) -> allAlerts.filter { it.status == AlertStatus.ACTIVE }
            getString(R.string.in_progress_alerts_count) -> allAlerts.filter { it.status == AlertStatus.IN_PROGRESS }
            getString(R.string.resolved_alerts_count) -> allAlerts.filter { it.status == AlertStatus.RESOLVED }
            getString(R.string.closed_alerts_count) -> allAlerts.filter { it.status == AlertStatus.CLOSED }
            else -> allAlerts // "All Alerts"
        }
        
        adminAlertsAdapter.submitList(filteredAlerts)
        
        // Show/hide empty state
        if (filteredAlerts.isEmpty()) {
            recyclerViewAlerts.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerViewAlerts.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
        
        Log.d(TAG, "Filter applied: $currentFilter, showing ${filteredAlerts.size} alerts")
    }
    
    private fun refreshAlerts() {
        Log.d(TAG, "Manually refreshing alerts")
        swipeRefreshLayout.isRefreshing = true
        // The real-time listener will automatically update the data
        // We just need to show the refresh indicator
    }
    
    private fun showCloseAlertDialog(alert: Alert) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.close_alert_dialog_title))
            .setMessage(getString(R.string.close_alert_dialog_message_with_details, alert.guardName, alert.location?.name ?: getString(R.string.unknown_location)))
            .setPositiveButton(getString(R.string.close_alert)) { _, _ ->
                closeAlert(alert)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun closeAlert(alert: Alert) {
        val currentUser = this.currentUser ?: return
        
        Log.d(TAG, "Closing alert: ${alert.id}")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val task = offlineRepository.updateAlertStatus(alert.id, AlertStatus.CLOSED, currentUser?.id ?: "")
                task.addOnCompleteListener { result: Task<Void> ->
                    if (result.isSuccessful) {
                        val statusMessage = if (offlineRepository.isOnline()) {
                            getString(R.string.alert_closed_successfully)
                        } else {
                            getString(R.string.alert_closed_sync_pending)
                        }
                        Toast.makeText(this@AdminDashboardActivity, statusMessage, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Alert closed successfully: ${alert.id}")
                    } else {
                        val error = result.exception?.message ?: getString(R.string.failed_to_close_alert)
                        Toast.makeText(this@AdminDashboardActivity, error, Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Failed to close alert: ${alert.id}", result.exception)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminDashboardActivity, "${getString(R.string.failed_to_close_alert)}: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Exception closing alert", e)
            }
        }
    }
    
    private fun reopenAlert(alert: Alert) {
        val currentUser = this.currentUser ?: return
        
        Log.d(TAG, "Reopening alert: ${alert.id}")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val task = offlineRepository.updateAlertStatus(alert.id, AlertStatus.ACTIVE, currentUser?.id ?: "")
                task.addOnCompleteListener { result: Task<Void> ->
                    if (result.isSuccessful) {
                        val statusMessage = if (offlineRepository.isOnline()) {
                            getString(R.string.alert_reopened_successfully)
                        } else {
                            getString(R.string.alert_reopened_sync_pending)
                        }
                        Toast.makeText(this@AdminDashboardActivity, statusMessage, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Alert reopened successfully: ${alert.id}")
                    } else {
                        val error = result.exception?.message ?: getString(R.string.failed_to_reopen_alert)
                        Toast.makeText(this@AdminDashboardActivity, error, Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Failed to reopen alert: ${alert.id}", result.exception)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminDashboardActivity, "${getString(R.string.failed_to_reopen_alert)}: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Exception reopening alert", e)
            }
        }
    }
    
    private fun openAlertDetails(alert: Alert) {
        Log.d(TAG, "Opening alert details for: ${alert.id}")
        
        val intent = Intent(this, AlertDetailsActivity::class.java).apply {
            putExtra(Constants.EXTRA_ALERT_ID, alert.id)
        }
        startActivity(intent)
    }
    
    /**
     * Observe network connectivity and update UI accordingly
     */
    private fun observeConnectivity() {
        CoroutineScope(Dispatchers.Main).launch {
            offlineRepository.observeConnectivity().collect { isOnline: Boolean ->
                updateOfflineStatus(isOnline)
            }
        }
    }
    
    /**
     * Observe offline data from Room database
     */
    private fun observeOfflineData() {
        CoroutineScope(Dispatchers.Main).launch {
            offlineRepository.getAllAlerts().collect { alerts ->
                allAlerts = alerts
                applyFilter()
                updateStatistics()
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
            tvOfflineStatus.text = getString(R.string.offline_data_will_sync)
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
                getString(R.string.status_online)
            } else {
                val pendingCount = syncStatus.pendingOperations + syncStatus.unsyncedAlerts
                if (pendingCount > 0) {
                    getString(R.string.status_pending, pendingCount)
                } else {
                    getString(R.string.status_offline)
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
        tvUserName.text = currentUser?.name ?: "Unknown"
        tvUserEmail.text = currentUser?.email ?: "Unknown"
        tvUserRole.text = currentUser?.role?.name ?: "Unknown"
        
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
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.logout_title)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Perform logout operation
     */
    private fun performLogout() {
        Log.d(TAG, "Logging out admin user")
        
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

    override fun onDestroy() {
        super.onDestroy()
        
        // Remove Firebase listener
        alertsListener?.remove()
        offlineRepository.cleanup()
        Log.d(TAG, "Firebase listener removed")
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        super.onBackPressed()
        // Sign out user when back is pressed from admin dashboard
        firebaseService.signOut()
        
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
}
