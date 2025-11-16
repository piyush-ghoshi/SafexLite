package com.campus.panicbutton.models

import com.google.firebase.Timestamp

/**
 * Data class representing an emergency alert with all required fields
 * for tracking emergency incidents from creation to resolution.
 */
data class Alert(
    val id: String = "",
    val guardId: String = "",
    val guardName: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val location: CampusBlock? = null,
    val message: String? = null,
    val status: AlertStatus = AlertStatus.ACTIVE,
    val acceptedBy: String? = null,
    val acceptedAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null,
    val closedBy: String? = null,
    val closedAt: Timestamp? = null
)

enum class AlertStatus {
    ACTIVE, IN_PROGRESS, RESOLVED, CLOSED
}