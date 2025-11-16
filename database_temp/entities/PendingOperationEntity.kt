package com.campus.panicbutton.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracking operations that need to be synced when connectivity is restored
 */
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationType: String, // CREATE_ALERT, UPDATE_ALERT, ACCEPT_ALERT, RESOLVE_ALERT, CLOSE_ALERT
    val alertId: String,
    val operationData: String, // JSON string containing operation-specific data
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)

/**
 * Enum for different types of operations that can be queued
 */
enum class OperationType {
    CREATE_ALERT,
    UPDATE_ALERT,
    ACCEPT_ALERT,
    RESOLVE_ALERT,
    CLOSE_ALERT
}