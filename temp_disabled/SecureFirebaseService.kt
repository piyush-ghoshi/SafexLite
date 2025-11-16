package com.campus.panicbutton.services

import android.content.Context
import android.util.Log
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.utils.SecurityManager
import com.campus.panicbutton.utils.SessionManager
import com.campus.panicbutton.utils.ValidationUtils
import com.campus.panicbutton.utils.AlertOperation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint

/**
 * Secure wrapper for FirebaseService that adds validation and security measures
 */
class SecureFirebaseService(
    private val context: Context,
    private val firebaseService: FirebaseService = FirebaseService()
) {
    
    private val securityManager = SecurityManager(context)
    private val sessionManager = SessionManager(context)
    
    companion object {
        private const val TAG = "SecureFirebaseService"
    }
    
    /**
     * Create emergency alert with security validation
     */
    fun createEmergencyAlert(
        guardId: String,
        guardName: String,
        location: CampusBlock?,
        message: String? = null
    ): Task<DocumentReference> {
        Log.d(TAG, "Creating secure emergency alert for guard: $guardName")
        
        // Validate user session
        if (!sessionManager.isUserAuthenticated()) {
            return Tasks.forException(SecurityException("User session is invalid"))
        }
        
        // Check user role permission
        val userRole = sessionManager.getUserRole()
        if (userRole == null) {
            return Tasks.forException(SecurityException("User role not found"))
        }
        
        val permissionResult = securityManager.validateAlertPermission(
            userRole, AlertOperation.CREATE, AlertStatus.ACTIVE
        )
        if (!permissionResult.hasPermission) {
            return Tasks.forException(SecurityException(permissionResult.errorMessage))
        }
        
        // Check cooldown
        val cooldownResult = securityManager.canCreateAlert(guardId)
        if (!cooldownResult.canCreate) {
            val remainingSeconds = cooldownResult.remainingCooldownMs / 1000
            return Tasks.forException(
                SecurityException("Please wait $remainingSeconds seconds before creating another alert")
            )
        }
        
        // Validate alert data
        val alert = Alert(
            guardId = guardId,
            guardName = guardName,
            location = location,
            message = message,
            status = AlertStatus.ACTIVE
        )
        
        val validationResult = ValidationUtils.validateAlert(alert)
        if (!validationResult.isValid) {
            return Tasks.forException(
                IllegalArgumentException("Invalid alert data: ${validationResult.errors.joinToString(", ")}")
            )
        }
        
        // Validate location if provided
        location?.let { loc ->
            if (!ValidationUtils.isValidCoordinates(loc.coordinates)) {
                return Tasks.forException(
                    IllegalArgumentException("Invalid location coordinates")
                )
            }
        }
        
        // Sanitize message if provided
        val sanitizedMessage = message?.let { ValidationUtils.sanitizeInput(it) }
        
        // Update session activity
        sessionManager.updateLastActivity()
        
        // Create alert and record security event
        return firebaseService.createEmergencyAlert(guardId, guardName, location, sanitizedMessage)
            .continueWith { task ->
                if (task.isSuccessful) {
                    // Record alert creation for cooldown tracking
                    securityManager.recordAlertCreation(guardId)
                    Log.d(TAG, "Emergency alert created successfully with security validation")
                } else {
                    Log.e(TAG, "Failed to create emergency alert", task.exception)
                }
                task.result
            }
    }
    
    /**
     * Accept alert with security validation
     */
    fun acceptAlert(alertId: String, guardId: String, guardName: String): Task<Void> {
        Log.d(TAG, "Accepting alert with security validation: $alertId")
        
        return validateAlertOperation(guardId, AlertOperation.ACCEPT, AlertStatus.ACTIVE) { userRole ->
            // Check rate limiting
            val updateResult = securityManager.canUpdateStatus(guardId)
            if (!updateResult.canUpdate) {
                throw SecurityException("Too many status updates. Try again later.")
            }
            
            firebaseService.acceptAlert(alertId, guardId, guardName).continueWith { task ->
                if (task.isSuccessful) {
                    securityManager.recordStatusUpdate(guardId)
                    sessionManager.updateLastActivity()
                }
                task.result
            }
        }
    }
    
    /**
     * Resolve alert with security validation
     */
    fun resolveAlert(alertId: String, guardId: String): Task<Void> {
        Log.d(TAG, "Resolving alert with security validation: $alertId")
        
        return validateAlertOperation(guardId, AlertOperation.RESOLVE, AlertStatus.IN_PROGRESS) { userRole ->
            val updateResult = securityManager.canUpdateStatus(guardId)
            if (!updateResult.canUpdate) {
                throw SecurityException("Too many status updates. Try again later.")
            }
            
            firebaseService.resolveAlert(alertId, guardId).continueWith { task ->
                if (task.isSuccessful) {
                    securityManager.recordStatusUpdate(guardId)
                    sessionManager.updateLastActivity()
                }
                task.result
            }
        }
    }
    
    /**
     * Close alert with security validation (admin only)
     */
    fun closeAlert(alertId: String, adminId: String, adminName: String): Task<Void> {
        Log.d(TAG, "Closing alert with security validation: $alertId")
        
        return validateAlertOperation(adminId, AlertOperation.CLOSE, AlertStatus.RESOLVED) { userRole ->
            if (userRole != UserRole.ADMIN) {
                throw SecurityException("Only administrators can close alerts")
            }
            
            firebaseService.closeAlert(alertId, adminId, adminName).continueWith { task ->
                if (task.isSuccessful) {
                    sessionManager.updateLastActivity()
                }
                task.result
            }
        }
    }
    
    /**
     * Reopen alert with security validation (admin only)
     */
    fun reopenAlert(alertId: String, adminId: String): Task<Void> {
        Log.d(TAG, "Reopening alert with security validation: $alertId")
        
        return validateAlertOperation(adminId, AlertOperation.REOPEN, AlertStatus.CLOSED) { userRole ->
            if (userRole != UserRole.ADMIN) {
                throw SecurityException("Only administrators can reopen alerts")
            }
            
            firebaseService.reopenAlert(alertId, adminId).continueWith { task ->
                if (task.isSuccessful) {
                    sessionManager.updateLastActivity()
                }
                task.result
            }
        }
    }
    
    /**
     * Login user with security validation
     */
    fun loginUser(email: String, password: String): Task<User> {
        Log.d(TAG, "Logging in user with security validation: $email")
        
        // Validate input
        if (!ValidationUtils.isValidEmail(email)) {
            return Tasks.forException(IllegalArgumentException("Invalid email format"))
        }
        
        val passwordValidation = ValidationUtils.validatePassword(password)
        if (!passwordValidation.isValid) {
            return Tasks.forException(
                IllegalArgumentException("Invalid password: ${passwordValidation.errors.joinToString(", ")}")
            )
        }
        
        // Check login lockout
        val lockResult = securityManager.isLoginLocked(email)
        if (lockResult.isLocked) {
            val remainingMinutes = lockResult.remainingLockoutMs / (1000 * 60)
            return Tasks.forException(
                SecurityException("Account locked. Try again in $remainingMinutes minutes.")
            )
        }
        
        // Attempt login
        return firebaseService.loginUser(email, password)
            .continueWith { task ->
                if (task.isSuccessful) {
                    val user = task.result
                    // Clear failed login attempts
                    securityManager.clearFailedLogins(email)
                    // Save user session
                    sessionManager.saveUserSession(user)
                    Log.d(TAG, "User logged in successfully with security validation")
                    user
                } else {
                    // Record failed login attempt
                    securityManager.recordFailedLogin(email)
                    Log.w(TAG, "Login failed for user: $email")
                    throw task.exception ?: Exception("Login failed")
                }
            }
    }
    
    /**
     * Register user with security validation
     */
    fun registerUser(email: String, password: String, name: String, role: UserRole): Task<User> {
        Log.d(TAG, "Registering user with security validation: $email")
        
        // Validate input
        if (!ValidationUtils.isValidEmail(email)) {
            return Tasks.forException(IllegalArgumentException("Invalid email format"))
        }
        
        val passwordValidation = ValidationUtils.validatePassword(password)
        if (!passwordValidation.isValid) {
            return Tasks.forException(
                IllegalArgumentException("Invalid password: ${passwordValidation.errors.joinToString(", ")}")
            )
        }
        
        val sanitizedName = ValidationUtils.sanitizeInput(name)
        if (sanitizedName.isBlank()) {
            return Tasks.forException(IllegalArgumentException("Name is required"))
        }
        
        // Create user
        return firebaseService.registerUser(email, password, sanitizedName, role)
            .continueWith { task ->
                if (task.isSuccessful) {
                    val user = task.result
                    sessionManager.saveUserSession(user)
                    Log.d(TAG, "User registered successfully with security validation")
                    user
                } else {
                    Log.e(TAG, "User registration failed", task.exception)
                    throw task.exception ?: Exception("Registration failed")
                }
            }
    }
    
    /**
     * Add campus block with validation (admin only)
     */
    fun addCampusBlock(block: CampusBlock): Task<Void> {
        Log.d(TAG, "Adding campus block with security validation: ${block.name}")
        
        // Validate user session and role
        if (!sessionManager.isUserAuthenticated() || !sessionManager.isAdmin()) {
            return Tasks.forException(SecurityException("Only administrators can add campus blocks"))
        }
        
        // Validate block data
        val validationResult = ValidationUtils.validateCampusBlock(block)
        if (!validationResult.isValid) {
            return Tasks.forException(
                IllegalArgumentException("Invalid campus block: ${validationResult.errors.joinToString(", ")}")
            )
        }
        
        // Sanitize block name
        val sanitizedBlock = block.copy(
            name = ValidationUtils.sanitizeInput(block.name),
            description = ValidationUtils.sanitizeInput(block.description)
        )
        
        sessionManager.updateLastActivity()
        return firebaseService.addCampusBlock(sanitizedBlock)
    }
    
    /**
     * Validate location data before mapping to campus blocks
     */
    fun validateAndMapLocation(geoPoint: GeoPoint): Task<CampusBlock?> {
        Log.d(TAG, "Validating and mapping location")
        
        // Validate coordinates
        if (!ValidationUtils.isValidCoordinates(geoPoint)) {
            return Tasks.forException(IllegalArgumentException("Invalid coordinates"))
        }
        
        // Check if coordinates are within reasonable bounds for campus
        if (!isWithinCampusBounds(geoPoint)) {
            Log.w(TAG, "Location is outside campus bounds")
            return Tasks.forResult(null)
        }
        
        // Get campus blocks and find matching one
        return firebaseService.getAllCampusBlocks()
            .continueWith { task ->
                if (task.isSuccessful) {
                    val blocks = task.result
                    findNearestBlock(geoPoint, blocks)
                } else {
                    null
                }
            }
    }
    
    /**
     * Refresh FCM token with security validation
     */
    fun refreshFCMToken(userId: String): Task<Void> {
        Log.d(TAG, "Refreshing FCM token with security validation")
        
        if (!sessionManager.isUserAuthenticated()) {
            return Tasks.forException(SecurityException("User session is invalid"))
        }
        
        return firebaseService.getFCMToken()
            .continueWithTask { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (ValidationUtils.isValidFcmToken(token)) {
                        sessionManager.saveFCMToken(token)
                        firebaseService.updateFCMToken(userId, token)
                    } else {
                        Tasks.forException(SecurityException("Invalid FCM token received"))
                    }
                } else {
                    Tasks.forException(task.exception ?: Exception("Failed to get FCM token"))
                }
            }
    }
    
    /**
     * Validate alert operation with common security checks
     */
    private fun validateAlertOperation(
        userId: String,
        operation: AlertOperation,
        expectedStatus: AlertStatus,
        action: (UserRole) -> Task<Void>
    ): Task<Void> {
        // Validate user session
        if (!sessionManager.isUserAuthenticated()) {
            return Tasks.forException(SecurityException("User session is invalid"))
        }
        
        // Get user role
        val userRole = sessionManager.getUserRole()
        if (userRole == null) {
            return Tasks.forException(SecurityException("User role not found"))
        }
        
        // Validate permission
        val permissionResult = securityManager.validateAlertPermission(userRole, operation, expectedStatus)
        if (!permissionResult.hasPermission) {
            return Tasks.forException(SecurityException(permissionResult.errorMessage))
        }
        
        return try {
            action(userRole)
        } catch (e: Exception) {
            Tasks.forException(e)
        }
    }
    
    /**
     * Check if coordinates are within campus bounds
     */
    private fun isWithinCampusBounds(geoPoint: GeoPoint): Boolean {
        // This should be configured based on actual campus coordinates
        // For now, using a simple bounding box check
        val lat = geoPoint.latitude
        val lng = geoPoint.longitude
        
        // Example bounds - should be configured for actual campus
        return lat >= 40.0 && lat <= 41.0 && lng >= -75.0 && lng <= -74.0
    }
    
    /**
     * Find nearest campus block to given coordinates
     */
    private fun findNearestBlock(geoPoint: GeoPoint, blocks: List<CampusBlock>): CampusBlock? {
        return blocks.minByOrNull { block ->
            val distance = calculateDistance(geoPoint, block.coordinates)
            if (distance <= block.radius) distance else Double.MAX_VALUE
        }
    }
    
    /**
     * Calculate distance between two points in meters
     */
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLngRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
}