package com.campus.panicbutton.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campus.panicbutton.R
import com.campus.panicbutton.adapters.TimelineAdapter
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.TimelineItem
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.utils.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for displaying detailed alert information with role-based actions
 * and timeline history. Supports both Guard and Admin user roles with
 * context-sensitive action buttons.
 */
class AlertDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlertDetailsActivity"
    }

    // UI Components
    private lateinit var tvAlertStatus: TextView
    private lateinit var tvAlertId: TextView
    private lateinit var tvGuardName: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvMessage: TextView
    private lateinit var layoutMessage: LinearLayout
    private lateinit var rvTimeline: RecyclerView
    private lateinit var layoutActionButtons: LinearLayout
    private lateinit var layoutGuardActions: LinearLayout
    private lateinit var layoutAdminActions: LinearLayout
    private lateinit var btnAcceptAlert: Button
    private lateinit var btnResolveAlert: Button
    private lateinit var btnCloseAlert: Button
    private lateinit var btnReopenAlert: Button
    private lateinit var progressBar: ProgressBar

    // Data and Services
    private lateinit var firebaseService: FirebaseService
    private lateinit var timelineAdapter: TimelineAdapter
    private var currentUser: User? = null
    private var currentAlert: Alert? = null
    private var alertId: String = ""
    private var alertListener: ListenerRegistration? = null

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_details)

        // Initialize Firebase service
        firebaseService = FirebaseService()

        // Get alert ID from intent
        alertId = intent.getStringExtra(Constants.EXTRA_ALERT_ID) ?: ""
        if (alertId.isEmpty()) {
            Log.e(TAG, "Alert ID not provided in intent")
            Toast.makeText(this, "Error: Alert ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UI components
        initializeViews()
        setupRecyclerView()
        setupActionButtons()

        // Load current user and alert data
        loadCurrentUser()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove Firestore listener
        alertListener?.remove()
    }

    /**
     * Initialize all UI components
     */
    private fun initializeViews() {
        tvAlertStatus = findViewById(R.id.tvAlertStatus)
        tvAlertId = findViewById(R.id.tvAlertId)
        tvGuardName = findViewById(R.id.tvGuardName)
        tvTimestamp = findViewById(R.id.tvTimestamp)
        tvLocation = findViewById(R.id.tvLocation)
        tvMessage = findViewById(R.id.tvMessage)
        layoutMessage = findViewById(R.id.layoutMessage)
        rvTimeline = findViewById(R.id.rvTimeline)
        layoutActionButtons = findViewById(R.id.layoutActionButtons)
        layoutGuardActions = findViewById(R.id.layoutGuardActions)
        layoutAdminActions = findViewById(R.id.layoutAdminActions)
        btnAcceptAlert = findViewById(R.id.btnAcceptAlert)
        btnResolveAlert = findViewById(R.id.btnResolveAlert)
        btnCloseAlert = findViewById(R.id.btnCloseAlert)
        btnReopenAlert = findViewById(R.id.btnReopenAlert)
        progressBar = findViewById(R.id.progressBar)
    }

    /**
     * Setup RecyclerView for timeline display
     */
    private fun setupRecyclerView() {
        timelineAdapter = TimelineAdapter(emptyList())
        rvTimeline.apply {
            layoutManager = LinearLayoutManager(this@AlertDetailsActivity)
            adapter = timelineAdapter
        }
    }

    /**
     * Setup click listeners for action buttons
     */
    private fun setupActionButtons() {
        btnAcceptAlert.setOnClickListener {
            acceptAlert()
        }

        btnResolveAlert.setOnClickListener {
            resolveAlert()
        }

        btnCloseAlert.setOnClickListener {
            showCloseAlertDialog()
        }

        btnReopenAlert.setOnClickListener {
            reopenAlert()
        }
    }

    /**
     * Load current user information
     */
    private fun loadCurrentUser() {
        showLoading(true)
        
        val currentFirebaseUser = firebaseService.getCurrentUser()
        if (currentFirebaseUser == null) {
            Log.e(TAG, "No authenticated user found")
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firebaseService.getUserProfile(currentFirebaseUser.uid)
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentUser = document.toObject(User::class.java)
                    Log.d(TAG, "Current user loaded: ${currentUser?.name} (${currentUser?.role})")
                    loadAlertDetails()
                } else {
                    Log.e(TAG, "User profile not found")
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to load user profile", exception)
                Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    /**
     * Load alert details and setup real-time listener
     */
    private fun loadAlertDetails() {
        // Setup real-time listener for alert changes
        alertListener = firebaseService.firestore.collection("alerts")
            .document(alertId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to alert changes", error)
                    Toast.makeText(this, "Error loading alert details", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    currentAlert = snapshot.toObject(Alert::class.java)?.copy(id = snapshot.id)
                    currentAlert?.let { alert ->
                        Log.d(TAG, "Alert loaded: ${alert.id} - Status: ${alert.status}")
                        displayAlertDetails(alert)
                        updateActionButtons(alert)
                        generateTimeline(alert)
                    }
                } else {
                    Log.e(TAG, "Alert not found: $alertId")
                    Toast.makeText(this, "Alert not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
                showLoading(false)
            }
    }

    /**
     * Display alert information in the UI
     */
    private fun displayAlertDetails(alert: Alert) {
        // Update alert status with appropriate styling
        tvAlertStatus.text = alert.status.name
        when (alert.status) {
            AlertStatus.ACTIVE -> {
                tvAlertStatus.setBackgroundResource(R.drawable.status_background_active)
            }
            AlertStatus.IN_PROGRESS -> {
                tvAlertStatus.setBackgroundResource(R.drawable.status_background_in_progress)
            }
            AlertStatus.RESOLVED -> {
                tvAlertStatus.setBackgroundResource(R.drawable.status_background_resolved)
            }
            AlertStatus.CLOSED -> {
                tvAlertStatus.setBackgroundResource(R.drawable.status_background_closed)
            }
        }

        // Display alert information
        tvAlertId.text = "Alert ID: #${alert.id.take(8)}"
        tvGuardName.text = alert.guardName
        tvTimestamp.text = dateFormat.format(alert.timestamp.toDate())
        
        // Display location
        if (alert.location != null) {
            tvLocation.text = "${alert.location.name} - ${alert.location.description}"
        } else {
            tvLocation.text = "Location not available"
        }

        // Display message if available
        if (!alert.message.isNullOrBlank()) {
            layoutMessage.visibility = View.VISIBLE
            tvMessage.text = alert.message
        } else {
            layoutMessage.visibility = View.GONE
        }
    }

    /**
     * Update action buttons based on user role and alert status
     */
    private fun updateActionButtons(alert: Alert) {
        val user = currentUser ?: return

        // Hide all action layouts initially
        layoutGuardActions.visibility = View.GONE
        layoutAdminActions.visibility = View.GONE
        btnAcceptAlert.visibility = View.GONE
        btnResolveAlert.visibility = View.GONE
        btnCloseAlert.visibility = View.GONE
        btnReopenAlert.visibility = View.GONE

        when (user.role) {
            UserRole.GUARD -> {
                layoutGuardActions.visibility = View.VISIBLE
                updateGuardActions(alert, user)
            }
            UserRole.ADMIN -> {
                layoutAdminActions.visibility = View.VISIBLE
                updateAdminActions(alert)
            }
        }
    }

    /**
     * Update guard-specific action buttons
     */
    private fun updateGuardActions(alert: Alert, user: User) {
        when (alert.status) {
            AlertStatus.ACTIVE -> {
                // Guards can accept active alerts (except their own)
                if (alert.guardId != user.id) {
                    btnAcceptAlert.visibility = View.VISIBLE
                }
            }
            AlertStatus.IN_PROGRESS -> {
                // Only the assigned guard can resolve the alert
                if (alert.acceptedBy == user.id) {
                    btnResolveAlert.visibility = View.VISIBLE
                }
            }
            AlertStatus.RESOLVED, AlertStatus.CLOSED -> {
                // No actions available for resolved/closed alerts for guards
            }
        }
    }

    /**
     * Update admin-specific action buttons
     */
    private fun updateAdminActions(alert: Alert) {
        when (alert.status) {
            AlertStatus.ACTIVE, AlertStatus.IN_PROGRESS, AlertStatus.RESOLVED -> {
                btnCloseAlert.visibility = View.VISIBLE
            }
            AlertStatus.CLOSED -> {
                btnReopenAlert.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Generate timeline items based on alert history
     */
    private fun generateTimeline(alert: Alert) {
        val timelineItems = mutableListOf<TimelineItem>()

        // Alert created
        timelineItems.add(
            TimelineItem(
                title = "Alert Created",
                description = "Emergency alert raised by ${alert.guardName}",
                timestamp = alert.timestamp,
                isActive = true
            )
        )

        // Alert accepted
        if (alert.acceptedAt != null && alert.acceptedBy != null) {
            timelineItems.add(
                TimelineItem(
                    title = "Alert Accepted",
                    description = "Alert accepted by guard",
                    timestamp = alert.acceptedAt,
                    isActive = true
                )
            )
        }

        // Alert resolved
        if (alert.resolvedAt != null) {
            timelineItems.add(
                TimelineItem(
                    title = "Alert Resolved",
                    description = "Alert marked as resolved",
                    timestamp = alert.resolvedAt,
                    isActive = true
                )
            )
        }

        // Alert closed
        if (alert.closedAt != null && alert.closedBy != null) {
            timelineItems.add(
                TimelineItem(
                    title = "Alert Closed",
                    description = "Alert closed by administrator",
                    timestamp = alert.closedAt,
                    isActive = true
                )
            )
        }

        // Sort timeline by timestamp (most recent first)
        timelineItems.sortByDescending { it.timestamp.seconds }
        
        timelineAdapter.updateTimelineItems(timelineItems)
    }

    /**
     * Accept the current alert
     */
    private fun acceptAlert() {
        val user = currentUser ?: return
        val alert = currentAlert ?: return

        showLoading(true)
        
        firebaseService.acceptAlert(alert.id, user.id, user.name)
            .addOnSuccessListener {
                Log.d(TAG, "Alert accepted successfully")
                Toast.makeText(this, "Alert accepted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to accept alert", exception)
                Toast.makeText(this, "Failed to accept alert", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    /**
     * Resolve the current alert
     */
    private fun resolveAlert() {
        val user = currentUser ?: return
        val alert = currentAlert ?: return

        showLoading(true)
        
        firebaseService.resolveAlert(alert.id, user.id)
            .addOnSuccessListener {
                Log.d(TAG, "Alert resolved successfully")
                Toast.makeText(this, "Alert resolved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to resolve alert", exception)
                Toast.makeText(this, "Failed to resolve alert", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    /**
     * Show confirmation dialog before closing alert
     */
    private fun showCloseAlertDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.close_alert_dialog_title))
            .setMessage(getString(R.string.close_alert_dialog_message))
            .setPositiveButton(getString(R.string.close_alert)) { _, _ ->
                closeAlert()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Close the current alert (admin action)
     */
    private fun closeAlert() {
        val user = currentUser ?: return
        val alert = currentAlert ?: return

        showLoading(true)
        
        firebaseService.closeAlert(alert.id, user.id, user.name)
            .addOnSuccessListener {
                Log.d(TAG, "Alert closed successfully")
                Toast.makeText(this, getString(R.string.alert_closed_successfully), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to close alert", exception)
                Toast.makeText(this, getString(R.string.failed_to_close_alert), Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    /**
     * Reopen the current alert (admin action)
     */
    private fun reopenAlert() {
        val user = currentUser ?: return
        val alert = currentAlert ?: return

        showLoading(true)
        
        firebaseService.reopenAlert(alert.id, user.id)
            .addOnSuccessListener {
                Log.d(TAG, "Alert reopened successfully")
                Toast.makeText(this, getString(R.string.alert_reopened_successfully), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to reopen alert", exception)
                Toast.makeText(this, getString(R.string.failed_to_reopen_alert), Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    /**
     * Show or hide loading indicator
     */
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        layoutActionButtons.visibility = if (show) View.GONE else View.VISIBLE
    }
}