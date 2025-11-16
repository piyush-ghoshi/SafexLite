package com.campus.panicbutton.models

import com.google.firebase.Timestamp

/**
 * Data class representing a timeline item for alert history display
 */
data class TimelineItem(
    val title: String,
    val description: String,
    val timestamp: Timestamp,
    val isActive: Boolean = true
)