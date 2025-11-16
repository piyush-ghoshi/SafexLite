package com.campus.panicbutton.services

import android.content.Context
import android.util.Log
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.utils.FirestoreQueryOptimizer
import com.campus.panicbutton.utils.PerformanceManager
import com.campus.panicbutton.utils.LifecycleManager
import com.campus.panicbutton.utils.ErrorHandler
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit


/**
 * Service class for Firebase operations including authentication,
 * user management, and FCM token handling with performance optimizations
 */
class FirebaseService(private val context: Context? = null) {
    
    private val auth = FirebaseAuth.getInstance()
    private val _firestore = FirebaseFirestore.getInstance()
    private val messaging = FirebaseMessaging.getInstance()
    
    // Performance managers
    private val performanceManager = context?.let { PerformanceManager.getInstance(it) }
    private val lifecycleManager = LifecycleManager.getInstance()
    
    // Public getter for Firestore instance
    val firestore: FirebaseFirestore get() = _firestore
    
    companion object {
        private const val TAG = "FirebaseService"
        private const val USERS_COLLECTION = "users"
        private const val CAMPUS_BLOCKS_COLLECTION = "campus_blocks"
        private const val ALERTS_COLLECTION = "alerts"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val ALERT_COOLDOWN_MS = 5000L // 5 seconds cooldown between alerts
    }
    
    // Track last alert creation time for cooldown
    private var lastAlertCreationTime = 0L
    
    // Authentication Methods
    
    /**
     * Sign in user with email and password
     * @param email User's email address
     * @param password User's password
     * @return Task<AuthResult> Firebase authentication result
     */
    fun signInWithEmailAndPassword(email: String, password: String): Task<AuthResult> {
        Log.d(TAG, "Attempting to sign in user: $email")
        return auth.signInWithEmailAndPassword(email, password)
    }
    
    /**
     * Create new user account with email and password
     * @param email User's email address
     * @param password User's password
     * @return Task<AuthResult> Firebase authentication result
     */
    fun createUserWithEmailAndPassword(email: String, password: String): Task<AuthResult> {
        Log.d(TAG, "Creating new user account: $email")
        return auth.createUserWithEmailAndPassword(email, password)
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        Log.d(TAG, "Signing out current user")
        auth.signOut()
    }
    
    /**
     * Get current authenticated user
     * @return FirebaseUser? Current user or null if not authenticated
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Check if user is currently authenticated
     * @return Boolean true if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
    
    // User Management Methods
    
    /**
     * Create user profile in Firestore after successful authentication
     * @param user User object to store
     * @return Task<Void> Firestore write operation result
     */
    fun createUserProfile(user: User): Task<Void> {
        Log.d(TAG, "Creating user profile for: ${user.email}")
        return _firestore.collection(USERS_COLLECTION)
            .document(user.id)
            .set(user)
    }
    
    /**
     * Get user profile from Firestore
     * @param userId User's unique ID
     * @return Task<DocumentSnapshot> Firestore document containing user data
     */
    fun getUserProfile(userId: String): Task<DocumentSnapshot> {
        Log.d(TAG, "Fetching user profile for ID: $userId")
        return _firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
    }
    
    /**
     * Update user profile in Firestore
     * @param userId User's unique ID
     * @param updates Map of fields to update
     * @return Task<Void> Firestore update operation result
     */
    fun updateUserProfile(userId: String, updates: Map<String, Any>): Task<Void> {
        Log.d(TAG, "Updating user profile for ID: $userId")
        return _firestore.collection(USERS_COLLECTION)
            .document(userId)
            .update(updates)
    }
    
    /**
     * Validate user role from Firestore
     * @param userId User's unique ID
     * @return Task<UserRole?> User's role or null if not found
     */
    fun validateUserRole(userId: String): Task<UserRole?> {
        Log.d(TAG, "Validating user role for ID: $userId")
        return getUserProfile(userId).continueWith { task ->
            if (task.isSuccessful) {
                val document = task.result
                if (document != null && document.exists()) {
                    val roleString = document.getString("role")
                    try {
                        UserRole.valueOf(roleString ?: "GUARD")
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Invalid role found for user $userId: $roleString")
                        UserRole.GUARD // Default to GUARD if invalid role
                    }
                } else {
                    Log.w(TAG, "User profile not found for ID: $userId")
                    null
                }
            } else {
                Log.e(TAG, "Failed to fetch user profile", task.exception)
                null
            }
        }
    }
    
    /**
     * Update user's last seen timestamp
     * @param userId User's unique ID
     * @return Task<Void> Firestore update operation result
     */
    fun updateLastSeen(userId: String): Task<Void> {
        Log.d(TAG, "Updating last seen for user: $userId")
        val updates = mapOf("lastSeen" to Timestamp.now())
        return updateUserProfile(userId, updates)
    }
    
    /**
     * Set user active status
     * @param userId User's unique ID
     * @param isActive Boolean indicating if user is active
     * @return Task<Void> Firestore update operation result
     */
    fun setUserActiveStatus(userId: String, isActive: Boolean): Task<Void> {
        Log.d(TAG, "Setting active status for user $userId: $isActive")
        val updates = mapOf("isActive" to isActive)
        return updateUserProfile(userId, updates)
    }
    
    // FCM Token Management Methods
    
    /**
     * Get current FCM token for push notifications
     * @return Task<String> FCM token
     */
    fun getFCMToken(): Task<String> {
        Log.d(TAG, "Retrieving FCM token")
        return messaging.token
    }
    
    /**
     * Update user's FCM token in Firestore
     * @param userId User's unique ID
     * @param token FCM token string
     * @return Task<Void> Firestore update operation result
     */
    fun updateFCMToken(userId: String, token: String): Task<Void> {
        Log.d(TAG, "Updating FCM token for user: $userId")
        val updates = mapOf("fcmToken" to token)
        return updateUserProfile(userId, updates)
    }
    
    /**
     * Update user's FCM token in Firestore (alias for updateFCMToken)
     * @param userId User's unique ID
     * @param token FCM token string
     * @return Task<Void> Firestore update operation result
     */
    fun updateUserFCMToken(userId: String, token: String): Task<Void> {
        return updateFCMToken(userId, token)
    }
    
    /**
     * Initialize FCM token for current user
     * This should be called after successful authentication
     * @param userId User's unique ID
     * @return Task<Void> Token update operation result
     */
    fun initializeFCMToken(userId: String): Task<Void> {
        Log.d(TAG, "Initializing FCM token for user: $userId")
        return getFCMToken().continueWithTask { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "FCM token retrieved: $token")
                updateFCMToken(userId, token)
            } else {
                Log.e(TAG, "Failed to retrieve FCM token", task.exception)
                throw task.exception ?: Exception("Failed to retrieve FCM token")
            }
        }
    }
    
    /**
     * Remove FCM token from user profile (useful for sign out)
     * @param userId User's unique ID
     * @return Task<Void> Firestore update operation result
     */
    fun removeFCMToken(userId: String): Task<Void> {
        Log.d(TAG, "Removing FCM token for user: $userId")
        val updates: Map<String, Any> = mapOf("fcmToken" to com.google.firebase.firestore.FieldValue.delete())
        return updateUserProfile(userId, updates)
    }
    
    // Utility Methods
    
    /**
     * Register a new user with complete profile setup
     * @param email User's email
     * @param password User's password
     * @param name User's display name
     * @param role User's role (GUARD or ADMIN)
     * @return Task<User> Complete user registration result
     */
    fun registerUser(email: String, password: String, name: String, role: UserRole): Task<User> {
        Log.d(TAG, "Registering new user: $email with role: $role")
        
        return createUserWithEmailAndPassword(email, password)
            .continueWithTask { authTask ->
                if (authTask.isSuccessful) {
                    val firebaseUser = authTask.result?.user
                    if (firebaseUser != null) {
                        val user = User(
                            id = firebaseUser.uid,
                            email = email,
                            name = name,
                            role = role,
                            isActive = true,
                            lastSeen = Timestamp.now()
                        )
                        
                        // Create user profile in Firestore
                        createUserProfile(user).continueWithTask { profileTask ->
                            if (profileTask.isSuccessful) {
                                // Initialize FCM token (non-blocking)
                                initializeFCMToken(user.id).addOnCompleteListener { tokenTask ->
                                    if (tokenTask.isSuccessful) {
                                        Log.d(TAG, "FCM token initialized successfully")
                                    } else {
                                        Log.w(TAG, "FCM token initialization failed", tokenTask.exception)
                                    }
                                }
                                Log.d(TAG, "User registration completed successfully")
                                Tasks.forResult(user)
                            } else {
                                Log.e(TAG, "Failed to create user profile", profileTask.exception)
                                Tasks.forException(profileTask.exception ?: Exception("Failed to create user profile"))
                            }
                        }
                    } else {
                        Tasks.forException(Exception("Firebase user is null after successful authentication"))
                    }
                } else {
                    Log.e(TAG, "User registration failed", authTask.exception)
                    Tasks.forException(authTask.exception ?: Exception("User registration failed"))
                }
            }
    }
    
    /**
     * Complete login process with profile validation and FCM token update
     * @param email User's email
     * @param password User's password
     * @return Task<User> Complete login result with user profile
     */
    fun loginUser(email: String, password: String): Task<User> {
        Log.d(TAG, "Logging in user: $email")
        
        return signInWithEmailAndPassword(email, password)
            .continueWithTask { authTask ->
                if (authTask.isSuccessful) {
                    val firebaseUser = authTask.result?.user
                    if (firebaseUser != null) {
                        // Get user profile from Firestore
                        getUserProfile(firebaseUser.uid).continueWith { profileTask ->
                            if (profileTask.isSuccessful) {
                                val document = profileTask.result
                                if (document != null && document.exists()) {
                                    val user = document.toObject(User::class.java)
                                    if (user != null) {
                                        // Update last seen and FCM token (non-blocking)
                                        updateLastSeen(user.id).addOnCompleteListener { lastSeenTask ->
                                            if (!lastSeenTask.isSuccessful) {
                                                Log.w(TAG, "Failed to update last seen", lastSeenTask.exception)
                                            }
                                        }
                                        initializeFCMToken(user.id).addOnCompleteListener { tokenTask ->
                                            if (!tokenTask.isSuccessful) {
                                                Log.w(TAG, "Failed to initialize FCM token", tokenTask.exception)
                                            }
                                        }
                                        
                                        Log.d(TAG, "User login completed successfully")
                                        user
                                    } else {
                                        throw Exception("Failed to parse user profile")
                                    }
                                } else {
                                    throw Exception("User profile not found in Firestore")
                                }
                            } else {
                                Log.e(TAG, "Failed to fetch user profile", profileTask.exception)
                                throw profileTask.exception ?: Exception("Failed to fetch user profile")
                            }
                        }
                    } else {
                        Tasks.forException(Exception("Firebase user is null after successful authentication"))
                    }
                } else {
                    Log.e(TAG, "User login failed", authTask.exception)
                    Tasks.forException(authTask.exception ?: Exception("User login failed"))
                }
            }
    }
    
    // Campus Block Management Methods
    
    /**
     * Seed initial campus blocks data to Firestore
     * This should be called once during app setup or when blocks need to be initialized
     * @return Task<Void> Seeding operation result
     */
    fun seedCampusBlocks(): Task<Void> {
        Log.d(TAG, "Seeding campus blocks data to Firestore")
        
        val campusBlocks = getDefaultCampusBlocks()
        val batch = _firestore.batch()
        
        campusBlocks.forEach { block ->
            val docRef = _firestore.collection(CAMPUS_BLOCKS_COLLECTION).document(block.id)
            batch.set(docRef, block)
        }
        
        return batch.commit().continueWith { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Successfully seeded ${campusBlocks.size} campus blocks")
            } else {
                Log.e(TAG, "Failed to seed campus blocks", task.exception)
                throw task.exception ?: Exception("Failed to seed campus blocks")
            }
            null
        }
    }
    
    /**
     * Check if campus blocks exist in Firestore
     * @return Task<Boolean> true if blocks exist, false otherwise
     */
    fun campusBlocksExist(): Task<Boolean> {
        Log.d(TAG, "Checking if campus blocks exist in Firestore")
        return _firestore.collection(CAMPUS_BLOCKS_COLLECTION)
            .limit(1)
            .get()
            .continueWith { task ->
                if (task.isSuccessful) {
                    val exists = !task.result.isEmpty
                    Log.d(TAG, "Campus blocks exist: $exists")
                    exists
                } else {
                    Log.e(TAG, "Failed to check campus blocks existence", task.exception)
                    false
                }
            }
    }
    
    /**
     * Initialize campus blocks if they don't exist
     * @return Task<Void> Initialization result
     */
    fun initializeCampusBlocksIfNeeded(): Task<Void> {
        Log.d(TAG, "Initializing campus blocks if needed")
        return campusBlocksExist().continueWithTask { task ->
            if (task.isSuccessful) {
                val exist = task.result
                if (!exist) {
                    Log.d(TAG, "Campus blocks don't exist, seeding data")
                    seedCampusBlocks()
                } else {
                    Log.d(TAG, "Campus blocks already exist, skipping seeding")
                    Tasks.forResult(null)
                }
            } else {
                Log.e(TAG, "Failed to check campus blocks existence", task.exception)
                Tasks.forException(task.exception ?: Exception("Failed to check campus blocks"))
            }
        }
    }
    
    /**
     * Initialize app data including campus blocks
     * Call this method during app startup (e.g., in Application class or main activity)
     * @return Task<Void> Initialization result
     */
    fun initializeAppData(): Task<Void> {
        Log.d(TAG, "Initializing app data")
        return initializeCampusBlocksIfNeeded()
    }
    
    /**
     * Get all campus blocks from Firestore
     * @return Task<List<CampusBlock>> List of campus blocks
     */
    fun getAllCampusBlocks(): Task<List<CampusBlock>> {
        Log.d(TAG, "Fetching all campus blocks from Firestore")
        return _firestore.collection(CAMPUS_BLOCKS_COLLECTION)
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
                    Log.d(TAG, "Fetched ${blocks.size} campus blocks")
                    blocks
                } else {
                    Log.e(TAG, "Failed to fetch campus blocks", task.exception)
                    emptyList()
                }
            }
    }
    
    /**
     * Add a new campus block to Firestore
     * @param block CampusBlock to add
     * @return Task<Void> Add operation result
     */
    fun addCampusBlock(block: CampusBlock): Task<Void> {
        Log.d(TAG, "Adding campus block: ${block.name}")
        return _firestore.collection(CAMPUS_BLOCKS_COLLECTION)
            .document(block.id)
            .set(block)
    }
    
    /**
     * Update an existing campus block in Firestore
     * @param blockId Block ID to update
     * @param updates Map of fields to update
     * @return Task<Void> Update operation result
     */
    fun updateCampusBlock(blockId: String, updates: Map<String, Any>): Task<Void> {
        Log.d(TAG, "Updating campus block: $blockId")
        return _firestore.collection(CAMPUS_BLOCKS_COLLECTION)
            .document(blockId)
            .update(updates)
    }
    
    /**
     * Delete a campus block from Firestore
     * @param blockId Block ID to delete
     * @return Task<Void> Delete operation result
     */
    fun deleteCampusBlock(blockId: String): Task<Void> {
        Log.d(TAG, "Deleting campus block: $blockId")
        return _firestore.collection(CAMPUS_BLOCKS_COLLECTION)
            .document(blockId)
            .delete()
    }
    
    // Alert Management Methods
    
    /**
     * Create an emergency alert with validation and cooldown protection
     * @param guardId ID of the guard creating the alert
     * @param guardName Name of the guard creating the alert
     * @param location Campus block where the alert is being raised
     * @param message Optional message describing the emergency
     * @return Task<DocumentReference> Reference to the created alert document
     */
    fun createEmergencyAlert(
        guardId: String,
        guardName: String,
        location: CampusBlock?,
        message: String? = null
    ): Task<DocumentReference> {
        Log.d(TAG, "Creating emergency alert for guard: $guardName at location: ${location?.name}")
        
        // Validate input parameters
        if (guardId.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Guard ID cannot be empty"))
        }
        if (guardName.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Guard name cannot be empty"))
        }
        
        // Check cooldown period to prevent spam
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertCreationTime < ALERT_COOLDOWN_MS) {
            val remainingCooldown = ALERT_COOLDOWN_MS - (currentTime - lastAlertCreationTime)
            return Tasks.forException(
                IllegalStateException("Please wait ${remainingCooldown / 1000} seconds before creating another alert")
            )
        }
        
        // Create alert object
        val alert = Alert(
            guardId = guardId,
            guardName = guardName,
            timestamp = Timestamp.now(),
            location = location,
            message = message?.trim()?.takeIf { it.isNotEmpty() },
            status = AlertStatus.ACTIVE
        )
        
        // Store alert in Firestore with retry logic
        return createAlertWithRetry(alert, 0).continueWith { task ->
            if (task.isSuccessful) {
                lastAlertCreationTime = currentTime
                Log.d(TAG, "Emergency alert created successfully: ${task.result?.id}")
                task.result
            } else {
                Log.e(TAG, "Failed to create emergency alert", task.exception)
                throw task.exception ?: Exception("Failed to create emergency alert")
            }
        }
    }
    
    /**
     * Create an alert (simplified version for offline repository)
     * @param alert Alert object to create
     * @return Task<DocumentReference> Reference to the created alert document
     */
    fun createAlert(alert: Alert): Task<DocumentReference> {
        return createEmergencyAlert(
            guardId = alert.guardId,
            guardName = alert.guardName,
            location = alert.location,
            message = alert.message
        )
    }
    
    /**
     * Update alert status (simplified version for offline repository)
     * @param alertId ID of the alert to update
     * @param status New status for the alert
     * @param userId ID of the user making the update (optional)
     * @return Task<Void> Update operation result
     */
    fun updateAlertStatus(alertId: String, status: AlertStatus, userId: String? = null): Task<Void> {
        val updates = mutableMapOf<String, Any?>(
            "status" to status.name
        )
        
        when (status) {
            AlertStatus.IN_PROGRESS -> {
                updates["acceptedBy"] = userId
                updates["acceptedAt"] = Timestamp.now()
            }
            AlertStatus.RESOLVED -> {
                updates["resolvedAt"] = Timestamp.now()
            }
            AlertStatus.CLOSED -> {
                updates["closedBy"] = userId
                updates["closedAt"] = Timestamp.now()
            }
            else -> { /* No additional fields needed */ }
        }
        
        return updateAlertWithRetry(alertId, updates, 0)
    }
    
    /**
     * Accept an alert (simplified version for offline repository)
     * @param alertId ID of the alert to accept
     * @param userId ID of the user accepting the alert
     * @return Task<Void> Update operation result
     */
    fun acceptAlert(alertId: String, userId: String): Task<Void> {
        return updateAlertStatus(alertId, AlertStatus.IN_PROGRESS, userId)
    }
    
    /**
     * Resolve an alert (simplified version for offline repository)
     * @param alertId ID of the alert to resolve
     * @return Task<Void> Update operation result
     */
    fun resolveAlert(alertId: String): Task<Void> {
        return updateAlertStatus(alertId, AlertStatus.RESOLVED)
    }
    
    /**
     * Close an alert (simplified version for offline repository)
     * @param alertId ID of the alert to close
     * @param userId ID of the user closing the alert
     * @return Task<Void> Update operation result
     */
    fun closeAlert(alertId: String, userId: String): Task<Void> {
        return updateAlertStatus(alertId, AlertStatus.CLOSED, userId)
    }

    /**
     * Accept an active alert and assign it to a guard
     * @param alertId ID of the alert to accept
     * @param guardId ID of the guard accepting the alert
     * @param guardName Name of the guard accepting the alert
     * @return Task<Void> Update operation result
     */
    fun acceptAlert(alertId: String, guardId: String, guardName: String): Task<Void> {
        Log.d(TAG, "Guard $guardName accepting alert: $alertId")
        
        if (alertId.isBlank() || guardId.isBlank() || guardName.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Alert ID, guard ID, and guard name cannot be empty"))
        }
        
        val updates = mapOf(
            "status" to AlertStatus.IN_PROGRESS.name,
            "acceptedBy" to guardId,
            "acceptedByName" to guardName,
            "acceptedAt" to Timestamp.now()
        )
        
        return updateAlertWithRetry(alertId, updates, 0)
    }
    
    /**
     * Resolve an in-progress alert
     * @param alertId ID of the alert to resolve
     * @param guardId ID of the guard resolving the alert
     * @return Task<Void> Update operation result
     */
    fun resolveAlert(alertId: String, guardId: String): Task<Void> {
        Log.d(TAG, "Guard $guardId resolving alert: $alertId")
        
        if (alertId.isBlank() || guardId.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Alert ID and guard ID cannot be empty"))
        }
        
        val updates = mapOf(
            "status" to AlertStatus.RESOLVED.name,
            "resolvedAt" to Timestamp.now()
        )
        
        return updateAlertWithRetry(alertId, updates, 0)
    }
    
    /**
     * Close an alert (admin action)
     * @param alertId ID of the alert to close
     * @param adminId ID of the admin closing the alert
     * @param adminName Name of the admin closing the alert
     * @return Task<Void> Update operation result
     */
    fun closeAlert(alertId: String, adminId: String, adminName: String): Task<Void> {
        Log.d(TAG, "Admin $adminName closing alert: $alertId")
        
        if (alertId.isBlank() || adminId.isBlank() || adminName.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Alert ID, admin ID, and admin name cannot be empty"))
        }
        
        val updates = mapOf(
            "status" to AlertStatus.CLOSED.name,
            "closedBy" to adminId,
            "closedByName" to adminName,
            "closedAt" to Timestamp.now()
        )
        
        return updateAlertWithRetry(alertId, updates, 0)
    }
    
    /**
     * Reopen a closed alert (admin action)
     * @param alertId ID of the alert to reopen
     * @param adminId ID of the admin reopening the alert
     * @return Task<Void> Update operation result
     */
    fun reopenAlert(alertId: String, adminId: String): Task<Void> {
        Log.d(TAG, "Admin $adminId reopening alert: $alertId")
        
        if (alertId.isBlank() || adminId.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Alert ID and admin ID cannot be empty"))
        }
        
        val updates = mapOf(
            "status" to AlertStatus.ACTIVE.name,
            "closedBy" to null,
            "closedByName" to null,
            "closedAt" to null
        )
        
        return updateAlertWithRetry(alertId, updates, 0)
    }
    
    /**
     * Get all alerts ordered by timestamp (most recent first)
     * @return Query for all alerts
     */
    fun getAllAlerts(): Query {
        Log.d(TAG, "Getting all alerts query")
        return _firestore.collection(ALERTS_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }
    
    /**
     * Get active alerts only
     * @return Query for active alerts
     */
    fun getActiveAlerts(): Query {
        Log.d(TAG, "Getting active alerts query")
        return _firestore.collection(ALERTS_COLLECTION)
            .whereEqualTo("status", AlertStatus.ACTIVE.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }
    
    /**
     * Get alerts by status
     * @param status Alert status to filter by
     * @return Query for alerts with specified status
     */
    fun getAlertsByStatus(status: AlertStatus): Query {
        Log.d(TAG, "Getting alerts by status: $status")
        return _firestore.collection(ALERTS_COLLECTION)
            .whereEqualTo("status", status.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }
    
    /**
     * Get alerts for a specific guard
     * @param guardId ID of the guard
     * @return Query for guard's alerts
     */
    fun getAlertsForGuard(guardId: String): Query {
        Log.d(TAG, "Getting alerts for guard: $guardId")
        return _firestore.collection(ALERTS_COLLECTION)
            .whereEqualTo("guardId", guardId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }
    
    /**
     * Get a specific alert by ID
     * @param alertId ID of the alert
     * @return Task<DocumentSnapshot> Alert document
     */
    fun getAlert(alertId: String): Task<DocumentSnapshot> {
        Log.d(TAG, "Getting alert: $alertId")
        return _firestore.collection(ALERTS_COLLECTION)
            .document(alertId)
            .get()
    }
    
    /**
     * Add real-time listener for all alerts
     * @param onAlertsChanged Callback function called when alerts change
     * @return ListenerRegistration to remove the listener
     */
    fun addAlertsListener(onAlertsChanged: (List<Alert>) -> Unit): ListenerRegistration {
        Log.d(TAG, "Adding real-time listener for all alerts")
        return getAllAlerts().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to alerts", error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val alerts = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(Alert::class.java)?.copy(id = document.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse alert: ${document.id}", e)
                        null
                    }
                }
                Log.d(TAG, "Alerts updated: ${alerts.size} alerts")
                onAlertsChanged(alerts)
            }
        }
    }
    
    /**
     * Add real-time listener for active alerts only
     * @param onActiveAlertsChanged Callback function called when active alerts change
     * @return ListenerRegistration to remove the listener
     */
    fun addActiveAlertsListener(onActiveAlertsChanged: (List<Alert>) -> Unit): ListenerRegistration {
        Log.d(TAG, "Adding real-time listener for active alerts")
        return getActiveAlerts().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to active alerts", error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val alerts = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(Alert::class.java)?.copy(id = document.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse active alert: ${document.id}", e)
                        null
                    }
                }
                Log.d(TAG, "Active alerts updated: ${alerts.size} alerts")
                onActiveAlertsChanged(alerts)
            }
        }
    }
    
    /**
     * Add real-time listener for a specific alert
     * @param alertId ID of the alert to listen to
     * @param onAlertChanged Callback function called when the alert changes
     * @return ListenerRegistration to remove the listener
     */
    fun addAlertListener(alertId: String, onAlertChanged: (Alert?) -> Unit): ListenerRegistration {
        Log.d(TAG, "Adding real-time listener for alert: $alertId")
        return _firestore.collection(ALERTS_COLLECTION)
            .document(alertId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to alert $alertId", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val alert = snapshot.toObject(Alert::class.java)?.copy(id = snapshot.id)
                        Log.d(TAG, "Alert $alertId updated: ${alert?.status}")
                        onAlertChanged(alert)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse alert $alertId", e)
                        onAlertChanged(null)
                    }
                } else {
                    Log.d(TAG, "Alert $alertId not found or deleted")
                    onAlertChanged(null)
                }
            }
    }
    
    /**
     * Create alert with retry logic for network failures
     * @param alert Alert object to create
     * @param attemptCount Current retry attempt
     * @return Task<DocumentReference> Creation result
     */
    private fun createAlertWithRetry(alert: Alert, attemptCount: Int): Task<DocumentReference> {
        return _firestore.collection(ALERTS_COLLECTION)
            .add(alert)
            .continueWithTask { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Alert created successfully on attempt ${attemptCount + 1}")
                    Tasks.forResult(task.result)
                } else {
                    if (attemptCount < MAX_RETRY_ATTEMPTS - 1) {
                        Log.w(TAG, "Alert creation failed, retrying in ${RETRY_DELAY_MS}ms (attempt ${attemptCount + 1})", task.exception)
                        // Simulate delay and retry
                        Tasks.call {
                            Thread.sleep(RETRY_DELAY_MS * (attemptCount + 1)) // Exponential backoff
                            null
                        }.continueWithTask {
                            createAlertWithRetry(alert, attemptCount + 1)
                        }
                    } else {
                        Log.e(TAG, "Alert creation failed after $MAX_RETRY_ATTEMPTS attempts", task.exception)
                        Tasks.forException(task.exception ?: Exception("Failed to create alert after retries"))
                    }
                }
            }
    }
    
    /**
     * Update alert with retry logic for network failures
     * @param alertId ID of the alert to update
     * @param updates Map of fields to update
     * @param attemptCount Current retry attempt
     * @return Task<Void> Update result
     */
    private fun updateAlertWithRetry(alertId: String, updates: Map<String, Any?>, attemptCount: Int): Task<Void> {
        return _firestore.collection(ALERTS_COLLECTION)
            .document(alertId)
            .update(updates)
            .continueWithTask { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Alert $alertId updated successfully on attempt ${attemptCount + 1}")
                    Tasks.forResult(null)
                } else {
                    if (attemptCount < MAX_RETRY_ATTEMPTS - 1) {
                        Log.w(TAG, "Alert update failed, retrying in ${RETRY_DELAY_MS}ms (attempt ${attemptCount + 1})", task.exception)
                        // Simulate delay and retry
                        Tasks.call {
                            Thread.sleep(RETRY_DELAY_MS * (attemptCount + 1)) // Exponential backoff
                            null
                        }.continueWithTask {
                            updateAlertWithRetry(alertId, updates, attemptCount + 1)
                        }
                    } else {
                        Log.e(TAG, "Alert update failed after $MAX_RETRY_ATTEMPTS attempts", task.exception)
                        Tasks.forException(task.exception ?: Exception("Failed to update alert after retries"))
                    }
                }
            }
    }
    
    /**
     * Get default campus blocks for seeding
     * This represents a typical university campus layout
     * @return List<CampusBlock> Default campus blocks
     */
    private fun getDefaultCampusBlocks(): List<CampusBlock> {
        return listOf(
            CampusBlock(
                id = "main_entrance",
                name = "Main Entrance",
                description = "Primary campus entrance and visitor center",
                coordinates = GeoPoint(40.7589, -73.9851), // Example coordinates (near Central Park, NYC)
                radius = 75.0
            ),
            CampusBlock(
                id = "library_complex",
                name = "Library Complex",
                description = "Main library building and study areas",
                coordinates = GeoPoint(40.7614, -73.9776),
                radius = 100.0
            ),
            CampusBlock(
                id = "student_center",
                name = "Student Center",
                description = "Student activities and dining facilities",
                coordinates = GeoPoint(40.7505, -73.9934),
                radius = 80.0
            ),
            CampusBlock(
                id = "academic_quad",
                name = "Academic Quad",
                description = "Central academic buildings and classrooms",
                coordinates = GeoPoint(40.7549, -73.9840),
                radius = 120.0
            ),
            CampusBlock(
                id = "science_building",
                name = "Science Building",
                description = "Laboratories and science departments",
                coordinates = GeoPoint(40.7580, -73.9820),
                radius = 90.0
            ),
            CampusBlock(
                id = "dormitory_east",
                name = "East Dormitory Complex",
                description = "Eastern residential buildings",
                coordinates = GeoPoint(40.7620, -73.9800),
                radius = 110.0
            ),
            CampusBlock(
                id = "dormitory_west",
                name = "West Dormitory Complex",
                description = "Western residential buildings",
                coordinates = GeoPoint(40.7520, -73.9900),
                radius = 110.0
            ),
            CampusBlock(
                id = "athletics_center",
                name = "Athletics Center",
                description = "Gymnasium and sports facilities",
                coordinates = GeoPoint(40.7490, -73.9860),
                radius = 95.0
            ),
            CampusBlock(
                id = "parking_north",
                name = "North Parking Lot",
                description = "Northern campus parking area",
                coordinates = GeoPoint(40.7640, -73.9830),
                radius = 60.0
            ),
            CampusBlock(
                id = "parking_south",
                name = "South Parking Lot",
                description = "Southern campus parking area",
                coordinates = GeoPoint(40.7470, -73.9880),
                radius = 60.0
            ),
            CampusBlock(
                id = "administration",
                name = "Administration Building",
                description = "Administrative offices and services",
                coordinates = GeoPoint(40.7560, -73.9870),
                radius = 70.0
            ),
            CampusBlock(
                id = "engineering_hall",
                name = "Engineering Hall",
                description = "Engineering departments and labs",
                coordinates = GeoPoint(40.7590, -73.9790),
                radius = 85.0
            )
        )
    }
    
    /**
     * Enhanced error handling and retry logic methods
     */
    
    /**
     * Determine if an operation should be retried based on the exception
     */
    private fun shouldRetryOperation(exception: Throwable?): Boolean {
        return when (exception) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.util.concurrent.TimeoutException -> true
            is com.google.firebase.FirebaseNetworkException -> true
            is com.google.firebase.FirebaseTooManyRequestsException -> true
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (exception.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> true
                    else -> false
                }
            }
            else -> false
        }
    }
    
    /**
     * Calculate retry delay with exponential backoff
     */
    private fun calculateRetryDelay(attemptNumber: Int): Long {
        val baseDelay = RETRY_DELAY_MS
        val exponentialDelay = baseDelay * (1L shl (attemptNumber - 1))
        val jitter = (Math.random() * baseDelay * 0.1).toLong()
        return minOf(exponentialDelay + jitter, 30000L) // Max 30 seconds
    }
    
    /**
     * Enhanced create alert with comprehensive error handling
     */
    fun createAlertWithErrorHandling(
        guardId: String,
        guardName: String,
        location: CampusBlock?,
        message: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String, Boolean) -> Unit // message, isRetryable
    ) {
        createEmergencyAlert(guardId, guardName, location, message)
            .addOnSuccessListener { documentRef ->
                Log.d(TAG, "Alert created successfully: ${documentRef.id}")
                onSuccess(documentRef.id)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to create alert", exception)
                val errorHandler = ErrorHandler(null) // Context not needed for error categorization
                val handledError = errorHandler.handleError(exception, "createAlert")
                onError(handledError.userMessage, handledError.isRetryable)
            }
    }
    
    /**
     * Enhanced update alert status with comprehensive error handling
     */
    fun updateAlertStatusWithErrorHandling(
        alertId: String,
        status: AlertStatus,
        userId: String? = null,
        onSuccess: () -> Unit,
        onError: (String, Boolean) -> Unit
    ) {
        updateAlertStatus(alertId, status, userId)
            .addOnSuccessListener {
                Log.d(TAG, "Alert status updated successfully: $alertId -> $status")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to update alert status", exception)
                val errorHandler = ErrorHandler(null)
                val handledError = errorHandler.handleError(exception, "updateAlertStatus")
                onError(handledError.userMessage, handledError.isRetryable)
            }
    }
    
    /**
     * Enhanced user authentication with comprehensive error handling
     */
    fun authenticateUserWithErrorHandling(
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String, Boolean, String?) -> Unit // message, isRetryable, actionText
    ) {
        loginUser(email, password)
            .addOnSuccessListener { user ->
                Log.d(TAG, "User authentication successful: ${user.email}")
                onSuccess(user)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "User authentication failed", exception)
                val errorHandler = ErrorHandler(null)
                val handledError = errorHandler.handleError(exception, "authentication")
                onError(handledError.userMessage, handledError.isRetryable, handledError.actionText)
            }
    }
    
    /**
     * Enhanced FCM token initialization with error handling
     */
    fun initializeFCMTokenWithErrorHandling(
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        initializeFCMToken(userId)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token initialized successfully for user: $userId")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "FCM token initialization failed for user: $userId", exception)
                val errorHandler = ErrorHandler(null)
                val handledError = errorHandler.handleError(exception, "FCM token initialization")
                onError(handledError.userMessage)
            }
    }
    
    // Performance Optimized Query Methods
    
    /**
     * Get optimized alerts query with caching and performance enhancements
     */
    fun getOptimizedAlertsQuery(
        limit: Int = 50,
        includeResolved: Boolean = false
    ): Query {
        performanceManager?.registerReference("alerts_query", this)
        return FirestoreQueryOptimizer.getOptimizedAlertsQuery(_firestore, limit, includeResolved)
    }
    
    /**
     * Get optimized active alerts with real-time listener
     */
    fun getOptimizedActiveAlertsWithListener(
        listenerKey: String,
        onResult: (List<Alert>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val query = FirestoreQueryOptimizer.getOptimizedActiveAlertsQuery(_firestore)
        
        val listener = FirestoreQueryOptimizer.executeOptimizedListener(
            query = query,
            listenerKey = listenerKey,
            config = FirestoreQueryOptimizer.QueryConfig(useCache = true, maxResults = 20),
            onResult = { snapshot ->
                val alerts = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Alert::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse alert: ${doc.id}", e)
                        null
                    }
                }
                onResult(alerts)
            },
            onError = onError
        )
        
        // Register listener for lifecycle management
        lifecycleManager.registerFirestoreListener(listenerKey, listener)
        
        return listener
    }
    
    /**
     * Get optimized user alerts with caching
     */
    fun getOptimizedUserAlerts(
        userId: String,
        limit: Int = 30
    ): Task<List<Alert>> {
        val query = FirestoreQueryOptimizer.getOptimizedUserAlertsQuery(_firestore, userId, limit)
        val cacheKey = "user_alerts_$userId"
        
        return FirestoreQueryOptimizer.executeOptimizedQuery(
            query = query,
            cacheKey = cacheKey,
            config = FirestoreQueryOptimizer.QueryConfig(useCache = true, maxResults = limit)
        ).continueWith { task ->
            if (task.isSuccessful) {
                task.result.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Alert::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse alert: ${doc.id}", e)
                        null
                    }
                }
            } else {
                emptyList()
            }
        }
    }
    
    /**
     * Get optimized campus blocks with caching
     */
    fun getOptimizedCampusBlocks(): Task<List<CampusBlock>> {
        val query = FirestoreQueryOptimizer.getOptimizedCampusBlocksQuery(_firestore)
        val cacheKey = "campus_blocks"
        
        return FirestoreQueryOptimizer.executeOptimizedQuery(
            query = query,
            cacheKey = cacheKey,
            config = FirestoreQueryOptimizer.QueryConfig(useCache = true, maxResults = 100)
        ).continueWith { task ->
            if (task.isSuccessful) {
                task.result.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(CampusBlock::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse campus block: ${doc.id}", e)
                        null
                    }
                }
            } else {
                emptyList()
            }
        }
    }
    
    /**
     * Batch update multiple alerts for better performance
     */
    fun batchUpdateAlerts(
        updates: List<Pair<String, Map<String, Any>>>
    ): Task<Void> {
        val operations = updates.map { (alertId, updateData) ->
            FirestoreQueryOptimizer.BatchOperation(
                type = FirestoreQueryOptimizer.BatchOperationType.UPDATE,
                documentRef = _firestore.collection(ALERTS_COLLECTION).document(alertId),
                data = updateData
            )
        }
        
        return FirestoreQueryOptimizer.executeBatchWrite(_firestore, operations)
    }
    
    /**
     * Clean up performance resources
     */
    fun cleanup() {
        performanceManager?.unregisterReference("firebase_service")
        FirestoreQueryOptimizer.removeAllListeners()
        FirestoreQueryOptimizer.clearCache()
        Log.d(TAG, "FirebaseService cleanup completed")
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        performanceManager?.let { pm ->
            val perfStats = pm.getPerformanceStats()
            stats["memory"] = perfStats.memoryInfo
            stats["activeReferences"] = perfStats.activeReferences
        }
        
        val cacheStats = FirestoreQueryOptimizer.getCacheStats()
        stats["cache"] = cacheStats
        
        return stats
    }
}