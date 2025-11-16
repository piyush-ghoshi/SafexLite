package com.campus.panicbutton.utils

/**
 * Constants for Firebase collections, app configuration, and system-wide
 * values used throughout the Campus Panic Button application.
 */
object Constants {
    
    // Firebase Collections
    const val USERS_COLLECTION = "users"
    const val ALERTS_COLLECTION = "alerts"
    const val CAMPUS_BLOCKS_COLLECTION = "campus_blocks"
    
    // Shared Preferences
    const val PREFS_NAME = "campus_panic_button_prefs"
    const val PREF_USER_ID = "user_id"
    const val PREF_USER_ROLE = "user_role"
    
    // Intent Extras
    const val EXTRA_ALERT_ID = "alert_id"
    const val EXTRA_USER_ROLE = "user_role"
    
    // Location
    const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    const val DEFAULT_LOCATION_RADIUS = 50.0 // meters
    
    // Notifications
    const val NOTIFICATION_ID_ALERT = 1001
}