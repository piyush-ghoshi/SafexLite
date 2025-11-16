package com.campus.panicbutton.database.dao

import androidx.room.*
import com.campus.panicbutton.database.entities.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PendingOperation operations in Room database
 */
@Dao
interface PendingOperationDao {
    
    /**
     * Get all pending operations ordered by timestamp
     */
    @Query("SELECT * FROM pending_operations ORDER BY timestamp ASC")
    suspend fun getAllPendingOperations(): List<PendingOperationEntity>
    
    /**
     * Get pending operations as Flow for real-time updates
     */
    @Query("SELECT * FROM pending_operations ORDER BY timestamp ASC")
    fun getPendingOperationsFlow(): Flow<List<PendingOperationEntity>>
    
    /**
     * Get pending operations by type
     */
    @Query("SELECT * FROM pending_operations WHERE operationType = :operationType ORDER BY timestamp ASC")
    suspend fun getPendingOperationsByType(operationType: String): List<PendingOperationEntity>
    
    /**
     * Get pending operations for a specific alert
     */
    @Query("SELECT * FROM pending_operations WHERE alertId = :alertId ORDER BY timestamp ASC")
    suspend fun getPendingOperationsForAlert(alertId: String): List<PendingOperationEntity>
    
    /**
     * Get operations that haven't exceeded max retries
     */
    @Query("SELECT * FROM pending_operations WHERE retryCount < maxRetries ORDER BY timestamp ASC")
    suspend fun getRetryableOperations(): List<PendingOperationEntity>
    
    /**
     * Insert a pending operation
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOperation(operation: PendingOperationEntity): Long
    
    /**
     * Insert multiple pending operations
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOperations(operations: List<PendingOperationEntity>)
    
    /**
     * Update a pending operation
     */
    @Update
    suspend fun updatePendingOperation(operation: PendingOperationEntity)
    
    /**
     * Increment retry count for an operation
     */
    @Query("UPDATE pending_operations SET retryCount = retryCount + 1 WHERE id = :operationId")
    suspend fun incrementRetryCount(operationId: Long)
    
    /**
     * Delete a pending operation
     */
    @Delete
    suspend fun deletePendingOperation(operation: PendingOperationEntity)
    
    /**
     * Delete pending operation by ID
     */
    @Query("DELETE FROM pending_operations WHERE id = :operationId")
    suspend fun deletePendingOperationById(operationId: Long)
    
    /**
     * Delete pending operations for a specific alert
     */
    @Query("DELETE FROM pending_operations WHERE alertId = :alertId")
    suspend fun deletePendingOperationsForAlert(alertId: String)
    
    /**
     * Delete all pending operations
     */
    @Query("DELETE FROM pending_operations")
    suspend fun deleteAllPendingOperations()
    
    /**
     * Get count of pending operations
     */
    @Query("SELECT COUNT(*) FROM pending_operations")
    suspend fun getPendingOperationCount(): Int
    
    /**
     * Delete operations that have exceeded max retries
     */
    @Query("DELETE FROM pending_operations WHERE retryCount >= maxRetries")
    suspend fun deleteFailedOperations()
}