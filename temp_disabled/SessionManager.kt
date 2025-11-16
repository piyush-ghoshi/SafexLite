package com.campus.panicbutton.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

/**
 * Session manager for handling user authentication state and token management
 */
class SessionManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "campus_panic_session"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_TOKEN_REFRESH_TIME = "token_refresh_time"
        
        // Session timeout: 24 hours
        private const val SESSION_TIMEOUT_MS = TimeUnit.HOURS.toMillis(24)
        // Token refresh interval: 1 hour
        private const val TOKEN_REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(1)
        // Inactivity timeout: 2 hours
        private const val INACTIVITY_TIMEOUT_MS = TimeUnit.HOURS.toMillis(2)
    }
    
    /**
     * Save user session data after successful login
     */
    fun saveUserSession(user: User) {
        Log.d(TAG, "Saving user session for: ${user.email}")
        val currentTime = System.currentTimeMillis()
        
        prefs.edit().apply {
            putString(KEY_USER_DATA, gson.toJson(user))
            putLong(KEY_LOGIN_TIME, currentTime)
            putLong(KEY_LAST_ACTIVITY, currentTime)
            apply()
        }
    }
    
    /**
     * Get current user from session
     */
    fun getCurrentUser(): User? {
        return try {
            val userJson = prefs.getString(KEY_USER_DATA, null)
            if (userJson != null) {
                gson.fromJson(userJson, User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user data from session", e)
            null
        }
    }
    
    /**
     * Check if user session is valid
     */
    fun isSessionValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0)
        val lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0)
        
        // Check if session has expired
        if (currentTime - loginTime > SESSION_TIMEOUT_MS) {
            Log.d(TAG, "Session expired due to timeout")
            return false
        }
        
        // Check if user has been inactive too long
        if (currentTime - lastActivity > INACTIVITY_TIMEOUT_MS) {
            Log.d(TAG, "Session expired due to inactivity")
            return false
        }
        
        // Check if Firebase user is still authenticated
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Log.d(TAG, "Firebase user is null")
            return false
        }
        
        return true
    }
    
    /**
     * Update last activity timestamp
     */
    fun updateLastActivity() {
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }
    
    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return getCurrentUser() != null && isSessionValid()
    }
    
    /**
     * Get user role from session
     */
    fun getUserRole(): UserRole? {
        return getCurrentUser()?.role
    }
    
    /**
     * Check if current user has admin role
     */
    fun isAdmin(): Boolean {
        return getUserRole() == UserRole.ADMIN
    }
    
    /**
     * Check if current user has guard role
     */
    fun isGuard(): Boolean {
        return getUserRole() == UserRole.GUARD
    }
    
    /**
     * Save FCM token with timestamp
     */
    fun saveFCMToken(token: String) {
        Log.d(TAG, "Saving FCM token")
        prefs.edit().apply {
            putString(KEY_FCM_TOKEN, token)
            putLong(KEY_TOKEN_REFRESH_TIME, System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * Get saved FCM token
     */
    fun getFCMToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }
    
    /**
     * Check if FCM token needs refresh
     */
    fun needsTokenRefresh(): Boolean {
        val lastRefresh = prefs.getLong(KEY_TOKEN_REFRESH_TIME, 0)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastRefresh > TOKEN_REFRESH_INTERVAL_MS
    }
    
    /**
     * Clear all session data (logout)
     */
    fun clearSession() {
        Log.d(TAG, "Clearing user session")
        prefs.edit().clear().apply()
    }
    
    /**
     * Get session duration in milliseconds
     */
    fun getSessionDuration(): Long {
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0)
        return if (loginTime > 0) {
            System.currentTimeMillis() - loginTime
        } else {
            0
        }
    }
    
    /**
     * Get time since last activity in milliseconds
     */
    fun getTimeSinceLastActivity(): Long {
        val lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0)
        return if (lastActivity > 0) {
            System.currentTimeMillis() - lastActivity
        } else {
            0
        }
    }
    
    /**
     * Check if session will expire soon (within 1 hour)
     */
    fun isSessionExpiringSoon(): Boolean {
        val currentTime = System.currentTimeMillis()
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0)
        val timeRemaining = SESSION_TIMEOUT_MS - (currentTime - loginTime)
        return timeRemaining <= TimeUnit.HOURS.toMillis(1)
    }
    
    /**
     * Extend session by updating login time (for token refresh)
     */
    fun extendSession() {
        Log.d(TAG, "Extending user session")
        prefs.edit().putLong(KEY_LOGIN_TIME, System.currentTimeMillis()).apply()
    }
    
    /**
     * Validate current Firebase user matches session user
     */
    fun validateFirebaseUser(): Boolean {
        val sessionUser = getCurrentUser()
        val firebaseUser = auth.currentUser
        
        if (sessionUser == null || firebaseUser == null) {
            return false
        }
        
        return sessionUser.id == firebaseUser.uid && sessionUser.email == firebaseUser.email
    }
    
    /**
     * Force session refresh by clearing and requiring re-authentication
     */
    fun forceSessionRefresh() {
        Log.d(TAG, "Forcing session refresh")
        clearSession()
        auth.signOut()
    }
}