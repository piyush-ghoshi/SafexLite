package com.campus.panicbutton.models

import com.google.firebase.Timestamp
import java.io.Serializable

/**
 * Data class representing a user (Guard or Admin) with role-based
 * access control and FCM token management for notifications.
 */
data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: UserRole = UserRole.GUARD,
    val isActive: Boolean = true,
    val fcmToken: String? = null,
    val lastSeen: Timestamp? = null
) : Serializable

enum class UserRole {
    GUARD, ADMIN
}