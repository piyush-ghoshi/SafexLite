package com.campus.panicbutton.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.UserRole
import java.util.concurrent.TimeUnit

/**
 * Security manager for handling security measures like cooldowns, rate limiting, and access control
 */
class SecurityManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "SecurityManager"
        private const val PREFS_NAME = "campus_panic_security"
        
        // Alert creation cooldown: 5 seconds
        private const val ALERT_COOLDOWN_MS = 5000L
        
        // Failed login attempt tracking
        private const val MAX_LOGIN_ATTEMPTS = 5
        private const val LOGIN_LOCKOUT_DURATION_MS = TimeUnit.MINUTES.toMillis(15)
        
        // Rate limiting for various operations
        private const val MAX_STATUS_UPDATES_PER_MINUTE = 10
        private const val STATUS_UPDATE_WINDOW_MS = TimeUnit.MINUTES.toMillis(1)
        
        // Keys for SharedPreferences
        private const val KEY_LAST_ALERT_TIME = "last_alert_time_"
        private const val KEY_FAILED_LOGIN_COUNT = "failed_login_count_"
        private const val KEY_LOGIN_LOCKOUT_TIME = "login_lockout_time_"
        private const val KEY_STATUS_UPDATE_TIMES = "status_update_times_"
    }
    
    /**
     * Check if user can create an alert (cooldown protection)
     */
    fun canCreateAlert(userId: String): AlertCreationResult {
        val lastAlertTime = prefs.getLong(KEY_LAST_ALERT_TIME + userId, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAlert = currentTime - lastAlertTime
        
        return if (timeSinceLastAlert >= ALERT_COOLDOWN_MS) {
            AlertCreationResult(true, 0)
        } else {
            val remainingCooldown = ALERT_COOLDOWN_MS - timeSinceLastAlert
            AlertCreationResult(false, remainingCooldown)
        }
    }
    
    /**
     * Record alert creation time for cooldown tracking
     */
    fun recordAlertCreation(userId: String) {
        Log.d(TAG, "Recording alert creation for user: $userId")
        prefs.edit().putLong(KEY_LAST_ALERT_TIME + userId, System.currentTimeMillis()).apply()
    }
    
    /**
     * Check if user can perform status update (rate limiting)
     */
    fun canUpdateStatus(userId: String): StatusUpdateResult {
        val currentTime = System.currentTimeMillis()
        val updateTimesJson = prefs.getString(KEY_STATUS_UPDATE_TIMES + userId, "[]")
        
        try {
            val updateTimes = parseUpdateTimes(updateTimesJson)
            
            // Remove old update times outside the window
            val recentUpdates = updateTimes.filter { 
                currentTime - it <= STATUS_UPDATE_WINDOW_MS 
            }
            
            return if (recentUpdates.size < MAX_STATUS_UPDATES_PER_MINUTE) {
                StatusUpdateResult(true, MAX_STATUS_UPDATES_PER_MINUTE - recentUpdates.size)
            } else {
                val oldestUpdate = recentUpdates.minOrNull() ?: 0
                val resetTime = oldestUpdate + STATUS_UPDATE_WINDOW_MS - currentTime
                StatusUpdateResult(false, 0, resetTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing update times", e)
            return StatusUpdateResult(true, MAX_STATUS_UPDATES_PER_MINUTE)
        }
    }
    
    /**
     * Record status update for rate limiting
     */
    fun recordStatusUpdate(userId: String) {
        Log.d(TAG, "Recording status update for user: $userId")
        val currentTime = System.currentTimeMillis()
        val updateTimesJson = prefs.getString(KEY_STATUS_UPDATE_TIMES + userId, "[]")
        
        try {
            val updateTimes = parseUpdateTimes(updateTimesJson).toMutableList()
            updateTimes.add(currentTime)
            
            // Keep only recent updates
            val recentUpdates = updateTimes.filter { 
                currentTime - it <= STATUS_UPDATE_WINDOW_MS 
            }
            
            val newJson = recentUpdates.joinToString(",", "[", "]")
            prefs.edit().putString(KEY_STATUS_UPDATE_TIMES + userId, newJson).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error recording status update", e)
        }
    }
    
    /**
     * Record failed login attempt
     */
    fun recordFailedLogin(email: String) {
        Log.d(TAG, "Recording failed login for: $email")
        val key = KEY_FAILED_LOGIN_COUNT + email.hashCode()
        val currentCount = prefs.getInt(key, 0)
        val newCount = currentCount + 1
        
        prefs.edit().apply {
            putInt(key, newCount)
            if (newCount >= MAX_LOGIN_ATTEMPTS) {
                putLong(KEY_LOGIN_LOCKOUT_TIME + email.hashCode(), System.currentTimeMillis())
            }
            apply()
        }
    }
    
    /**
     * Clear failed login attempts after successful login
     */
    fun clearFailedLogins(email: String) {
        Log.d(TAG, "Clearing failed logins for: $email")
        val hashCode = email.hashCode()
        prefs.edit().apply {
            remove(KEY_FAILED_LOGIN_COUNT + hashCode)
            remove(KEY_LOGIN_LOCKOUT_TIME + hashCode)
            apply()
        }
    }
    
    /**
     * Check if user is locked out from login attempts
     */
    fun isLoginLocked(email: String): LoginLockResult {
        val hashCode = email.hashCode()
        val failedCount = prefs.getInt(KEY_FAILED_LOGIN_COUNT + hashCode, 0)
        
        if (failedCount < MAX_LOGIN_ATTEMPTS) {
            return LoginLockResult(false, 0, MAX_LOGIN_ATTEMPTS - failedCount)
        }
        
        val lockoutTime = prefs.getLong(KEY_LOGIN_LOCKOUT_TIME + hashCode, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLockout = currentTime - lockoutTime
        
        return if (timeSinceLockout >= LOGIN_LOCKOUT_DURATION_MS) {
            // Lockout period has expired, clear the lockout
            clearFailedLogins(email)
            LoginLockResult(false, 0, MAX_LOGIN_ATTEMPTS)
        } else {
            val remainingLockout = LOGIN_LOCKOUT_DURATION_MS - timeSinceLockout
            LoginLockResult(true, remainingLockout, 0)
        }
    }
    
    /**
     * Validate user permission for alert operation
     */
    fun validateAlertPermission(userRole: UserRole, operation: AlertOperation, alertStatus: AlertStatus): PermissionResult {
        return when (operation) {
            AlertOperation.CREATE -> {
                if (userRole == UserRole.GUARD || userRole == UserRole.ADMIN) {
                    PermissionResult(true)
                } else {
                    PermissionResult(false, "Only guards and admins can create alerts")
                }
            }
            
            AlertOperation.ACCEPT -> {
                if (userRole == UserRole.GUARD || userRole == UserRole.ADMIN) {
                    if (alertStatus == AlertStatus.ACTIVE) {
                        PermissionResult(true)
                    } else {
                        PermissionResult(false, "Only active alerts can be accepted")
                    }
                } else {
                    PermissionResult(false, "Only guards and admins can accept alerts")
                }
            }
            
            AlertOperation.RESOLVE -> {
                if (userRole == UserRole.GUARD || userRole == UserRole.ADMIN) {
                    if (alertStatus == AlertStatus.IN_PROGRESS) {
                        PermissionResult(true)
                    } else {
                        PermissionResult(false, "Only in-progress alerts can be resolved")
                    }
                } else {
                    PermissionResult(false, "Only guards and admins can resolve alerts")
                }
            }
            
            AlertOperation.CLOSE -> {
                if (userRole == UserRole.ADMIN) {
                    if (alertStatus != AlertStatus.CLOSED) {
                        PermissionResult(true)
                    } else {
                        PermissionResult(false, "Alert is already closed")
                    }
                } else {
                    PermissionResult(false, "Only admins can close alerts")
                }
            }
            
            AlertOperation.REOPEN -> {
                if (userRole == UserRole.ADMIN) {
                    if (alertStatus == AlertStatus.CLOSED) {
                        PermissionResult(true)
                    } else {
                        PermissionResult(false, "Only closed alerts can be reopened")
                    }
                } else {
                    PermissionResult(false, "Only admins can reopen alerts")
                }
            }
        }
    }
    
    /**
     * Validate status transition
     */
    fun validateStatusTransition(currentStatus: AlertStatus, newStatus: AlertStatus): Boolean {
        return ValidationUtils.isValidStatusTransition(currentStatus, newStatus)
    }
    
    /**
     * Clear all security data for a user (for testing or admin purposes)
     */
    fun clearUserSecurityData(userId: String) {
        Log.d(TAG, "Clearing security data for user: $userId")
        prefs.edit().apply {
            remove(KEY_LAST_ALERT_TIME + userId)
            remove(KEY_STATUS_UPDATE_TIMES + userId)
            apply()
        }
    }
    
    /**
     * Parse update times from JSON string
     */
    private fun parseUpdateTimes(json: String): List<Long> {
        return try {
            json.removeSurrounding("[", "]")
                .split(",")
                .filter { it.isNotBlank() }
                .map { it.trim().toLong() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Result of alert creation permission check
 */
data class AlertCreationResult(
    val canCreate: Boolean,
    val remainingCooldownMs: Long
)

/**
 * Result of status update permission check
 */
data class StatusUpdateResult(
    val canUpdate: Boolean,
    val remainingUpdates: Int,
    val resetTimeMs: Long = 0
)

/**
 * Result of login lock check
 */
data class LoginLockResult(
    val isLocked: Boolean,
    val remainingLockoutMs: Long,
    val remainingAttempts: Int
)

/**
 * Result of permission validation
 */
data class PermissionResult(
    val hasPermission: Boolean,
    val errorMessage: String? = null
)

/**
 * Alert operations for permission checking
 */
enum class AlertOperation {
    CREATE, ACCEPT, RESOLVE, CLOSE, REOPEN
}