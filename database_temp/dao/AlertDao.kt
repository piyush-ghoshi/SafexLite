package com.campus.panicbutton.database.dao

import androidx.room.*
import com.campus.panicbutton.database.entities.AlertEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Alert operations in Room database
 */
@Dao
interface AlertDao {
    
    /**
     * Get all alerts ordered by timestamp (most recent first)
     */
    @Query("SELECT * FROM alerts ORDER BY timestampSeconds DESC, timestampNanoseconds DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>
    
    /**
     * Get all alerts as a list (for one-time queries)
     */
    @Query("SELECT * FROM alerts ORDER BY timestampSeconds DESC, timestampNanoseconds DESC")
    suspend fun getAllAlertsOnce(): List<AlertEntity>
    
    /**
     * Get alerts by status
     */
    @Query("SELECT * FROM alerts WHERE status = :status ORDER BY timestampSeconds DESC, timestampNanoseconds DESC")
    fun getAlertsByStatus(status: String): Flow<List<AlertEntity>>
    
    /**
     * Get active alerts only
     */
    @Query("SELECT * FROM alerts WHERE status = 'ACTIVE' ORDER BY timestampSeconds DESC, timestampNanoseconds DESC")
    fun getActiveAlerts(): Flow<List<AlertEntity>>
    
    /**
     * Get alerts for a specific guard
     */
    @Query("SELECT * FROM alerts WHERE guardId = :guardId ORDER BY timestampSeconds DESC, timestampNanoseconds DESC")
    fun getAlertsForGuard(guardId: String): Flow<List<AlertEntity>>
    
    /**
     * Get a specific alert by ID
     */
    @Query("SELECT * FROM alerts WHERE id = :alertId")
    suspend fun getAlertById(alertId: String): AlertEntity?
    
    /**
     * Get unsynced alerts (for sync operations)
     */
    @Query("SELECT * FROM alerts WHERE isSynced = 0 ORDER BY lastModified ASC")
    suspend fun getUnsyncedAlerts(): List<AlertEntity>
    
    /**
     * Insert or replace an alert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)
    
    /**
     * Insert or replace multiple alerts
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<AlertEntity>)
    
    /**
     * Update an alert
     */
    @Update
    suspend fun updateAlert(alert: AlertEntity)
    
    /**
     * Update alert sync status
     */
    @Query("UPDATE alerts SET isSynced = :isSynced WHERE id = :alertId")
    suspend fun updateAlertSyncStatus(alertId: String, isSynced: Boolean)
    
    /**
     * Delete an alert
     */
    @Delete
    suspend fun deleteAlert(alert: AlertEntity)
    
    /**
     * Delete alert by ID
     */
    @Query("DELETE FROM alerts WHERE id = :alertId")
    suspend fun deleteAlertById(alertId: String)
    
    /**
     * Delete all alerts
     */
    @Query("DELETE FROM alerts")
    suspend fun deleteAllAlerts()
    
    /**
     * Get count of alerts
     */
    @Query("SELECT COUNT(*) FROM alerts")
    suspend fun getAlertCount(): Int
    
    /**
     * Get count of unsynced alerts
     */
    @Query("SELECT COUNT(*) FROM alerts WHERE isSynced = 0")
    suspend fun getUnsyncedAlertCount(): Int
    
    /**
     * Delete old alerts (older than specified timestamp)
     */
    @Query("DELETE FROM alerts WHERE timestampSeconds < :timestampSeconds")
    suspend fun deleteOldAlerts(timestampSeconds: Long)
}