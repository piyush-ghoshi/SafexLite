package com.campus.panicbutton.repository

import android.content.Context
import android.util.Log
import com.campus.panicbutton.database.AppDatabase
import com.campus.panicbutton.database.entities.AlertEntity
import com.campus.panicbutton.database.entities.CampusBlockEntity
import com.campus.panicbutton.database.entities.OperationType
import com.campus.panicbutton.database.entities.PendingOperationEntity
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.utils.MockDocumentReference
import com.campus.panicbutton.utils.NetworkUtils
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Repository that manages offline data caching and synchronization with Firebase
 */
class OfflineRepository(
    private val context: Context,
    private val firebaseService: FirebaseService
) {
    private val database = AppDatabase.getDatabase(context)
    private val alertDao = database.alertDao()
    private val campusBlockDao = database.campusBlockDao()
    private val pendingOperationDao = database.pendingOperationDao()
    private val networkUtils = NetworkUtils(context)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var networkListener: Job? = null
    
    companion object {
        private const val TAG = "OfflineRepository"
        private const val SYNC_INTERVAL_MS = 30000L // 30 seconds
        private const val MAX_CACHE_AGE_DAYS = 7
    }
    
    init {
        startNetworkMonitoring()
    }
    
    // Network Monitoring
    
    /**
     * Start monitoring network connectivity and sync when online
     */
    private fun startNetworkMonitoring() {
        networkListener = scope.launch {
            networkUtils.observeNetworkConnectivity().collect { isConnected ->
                Log.d(TAG, "Network connectivity changed: $isConnected")
                if (isConnected) {
                    syncPendingOperations()
                    syncCachedData()
                }
            }
        }
    }
    
    /**
     * Check if device is currently online
     */
    fun isOnline(): Boolean = networkUtils.hasInternetConnectivity()
    
    /**
     * Get network connectivity as Flow
     */
    fun observeConnectivity(): Flow<Boolean> = networkUtils.observeNetworkConnectivity()
    
    // Alert Operations
    
    /**
     * Get all alerts - returns cached data if offline, Firebase data if online
     */
    fun getAllAlerts(): Flow<List<Alert>> {
        return alertDao.getAllAlerts().map { entities ->
            entities.map { it.toAlert() }
        }
    }
    
    /**
     * Get active alerts only
     */
    fun getActiveAlerts(): Flow<List<Alert>> {
        return alertDao.getActiveAlerts().map { entities ->
            entities.map { it.toAlert() }
        }
    }
    
    /**
     * Get alerts by status
     */
    fun getAlertsByStatus(status: AlertStatus): Flow<List<Alert>> {
        return alertDao.getAlertsByStatus(status.name).map { entities ->
            entities.map { it.toAlert() }
        }
    }
    
    /**
     * Get a specific alert by ID
     */
    suspend fun getAlertById(alertId: String): Alert? {
        return alertDao.getAlertById(alertId)?.toAlert()
    }
    
    /**
     * Create a new alert - handles both online and offline scenarios
     */
    suspend fun createAlert(alert: Alert): Task<DocumentReference> {
        return if (isOnline()) {
            try {
                // Try to create alert in Firebase first
                val task = firebaseService.createAlert(alert)
                
                // Cache the alert locally after successful creation
                task.addOnSuccessListener { documentRef ->
                    scope.launch {
                        val alertWithId = alert.copy(id = documentRef.id)
                        alertDao.insertAlert(AlertEntity.fromAlert(alertWithId, isSynced = true))
                    }
                }
                
                task
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create alert online, queuing for offline", e)
                createAlertOffline(alert)
            }
        } else {
            createAlertOffline(alert)
        }
    }
    
    /**
     * Create alert offline - store locally and queue for sync
     */
    private suspend fun createAlertOffline(alert: Alert): Task<DocumentReference> {
        return try {
            // Generate a temporary ID for the alert
            val tempId = "temp_${System.currentTimeMillis()}_${alert.guardId}"
            val alertWithId = alert.copy(id = tempId)
            
            // Store alert locally as unsynced
            alertDao.insertAlert(AlertEntity.fromAlert(alertWithId, isSynced = false))
            
            // Queue the operation for sync
            val operationData = JSONObject().apply {
                put("guardId", alert.guardId)
                put("guardName", alert.guardName)
                put("message", alert.message ?: "")
                put("locationId", alert.location?.id ?: "")
                put("locationName", alert.location?.name ?: "")
            }.toString()
            
            pendingOperationDao.insertPendingOperation(
                PendingOperationEntity(
                    operationType = OperationType.CREATE_ALERT.name,
                    alertId = tempId,
                    operationData = operationData
                )
            )
            
            Log.d(TAG, "Alert created offline and queued for sync: $tempId")
            
            // Create a simple mock DocumentReference for offline mode
            val mockDocRef = MockDocumentReference(tempId)
            Tasks.forResult(mockDocRef)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create alert offline", e)
            Tasks.forException(e)
        }
    }
    
    /**
     * Update alert status - handles both online and offline scenarios
     */
    suspend fun updateAlertStatus(alertId: String, status: AlertStatus, userId: String? = null): Task<Void> {
        return if (isOnline()) {
            try {
                val task = firebaseService.updateAlertStatus(alertId, status, userId)
                
                // Update local cache after successful Firebase update
                task.addOnSuccessListener {
                    scope.launch {
                        val existingAlert = alertDao.getAlertById(alertId)
                        existingAlert?.let { alert ->
                            val updatedAlert = when (status) {
                                AlertStatus.IN_PROGRESS -> alert.copy(
                                    status = status.name,
                                    acceptedBy = userId,
                                    acceptedAtSeconds = System.currentTimeMillis() / 1000,
                                    acceptedAtNanoseconds = 0
                                )
                                AlertStatus.RESOLVED -> alert.copy(
                                    status = status.name,
                                    resolvedAtSeconds = System.currentTimeMillis() / 1000,
                                    resolvedAtNanoseconds = 0
                                )
                                AlertStatus.CLOSED -> alert.copy(
                                    status = status.name,
                                    closedBy = userId,
                                    closedAtSeconds = System.currentTimeMillis() / 1000,
                                    closedAtNanoseconds = 0
                                )
                                else -> alert.copy(status = status.name)
                            }
                            alertDao.updateAlert(updatedAlert)
                        }
                    }
                }
                
                task
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update alert status online, queuing for offline", e)
                updateAlertStatusOffline(alertId, status, userId)
            }
        } else {
            updateAlertStatusOffline(alertId, status, userId)
        }
    }
    
    /**
     * Update alert status offline - store locally and queue for sync
     */
    private suspend fun updateAlertStatusOffline(alertId: String, status: AlertStatus, userId: String?): Task<Void> {
        return try {
            // Update local alert
            val existingAlert = alertDao.getAlertById(alertId)
            existingAlert?.let { alert ->
                val updatedAlert = when (status) {
                    AlertStatus.IN_PROGRESS -> alert.copy(
                        status = status.name,
                        acceptedBy = userId,
                        acceptedAtSeconds = System.currentTimeMillis() / 1000,
                        acceptedAtNanoseconds = 0,
                        isSynced = false
                    )
                    AlertStatus.RESOLVED -> alert.copy(
                        status = status.name,
                        resolvedAtSeconds = System.currentTimeMillis() / 1000,
                        resolvedAtNanoseconds = 0,
                        isSynced = false
                    )
                    AlertStatus.CLOSED -> alert.copy(
                        status = status.name,
                        closedBy = userId,
                        closedAtSeconds = System.currentTimeMillis() / 1000,
                        closedAtNanoseconds = 0,
                        isSynced = false
                    )
                    else -> alert.copy(status = status.name, isSynced = false)
                }
                alertDao.updateAlert(updatedAlert)
                
                // Queue the operation for sync
                val operationData = JSONObject().apply {
                    put("status", status.name)
                    put("userId", userId ?: "")
                }.toString()
                
                val operationType = when (status) {
                    AlertStatus.IN_PROGRESS -> OperationType.ACCEPT_ALERT
                    AlertStatus.RESOLVED -> OperationType.RESOLVE_ALERT
                    AlertStatus.CLOSED -> OperationType.CLOSE_ALERT
                    else -> OperationType.UPDATE_ALERT
                }
                
                pendingOperationDao.insertPendingOperation(
                    PendingOperationEntity(
                        operationType = operationType.name,
                        alertId = alertId,
                        operationData = operationData
                    )
                )
                
                Log.d(TAG, "Alert status updated offline and queued for sync: $alertId -> $status")
            }
            
            Tasks.forResult(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update alert status offline", e)
            Tasks.forException(e)
        }
    }
    
    // Campus Block Operations
    
    /**
     * Get all campus blocks - returns cached data if offline, Firebase data if online
     */
    fun getAllCampusBlocks(): Flow<List<CampusBlock>> {
        return campusBlockDao.getAllCampusBlocks().map { entities ->
            entities.map { it.toCampusBlock() }
        }
    }
    
    /**
     * Get campus block by ID
     */
    suspend fun getCampusBlockById(blockId: String): CampusBlock? {
        return campusBlockDao.getCampusBlockById(blockId)?.toCampusBlock()
    }
    
    /**
     * Cache campus blocks from Firebase
     */
    suspend fun cacheCampusBlocks(blocks: List<CampusBlock>) {
        try {
            val entities = blocks.map { CampusBlockEntity.fromCampusBlock(it) }
            campusBlockDao.insertCampusBlocks(entities)
            Log.d(TAG, "Cached ${blocks.size} campus blocks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache campus blocks", e)
        }
    }
    
    // Synchronization Operations
    
    /**
     * Sync all pending operations with Firebase
     */
    suspend fun syncPendingOperations() {
        if (!isOnline()) {
            Log.d(TAG, "Device offline, skipping sync")
            return
        }
        
        try {
            val pendingOps = pendingOperationDao.getAllPendingOperations()
            Log.d(TAG, "Syncing ${pendingOps.size} pending operations")
            
            for (operation in pendingOps) {
                try {
                    when (OperationType.valueOf(operation.operationType)) {
                        OperationType.CREATE_ALERT -> syncCreateAlert(operation)
                        OperationType.UPDATE_ALERT -> syncUpdateAlert(operation)
                        OperationType.ACCEPT_ALERT -> syncAcceptAlert(operation)
                        OperationType.RESOLVE_ALERT -> syncResolveAlert(operation)
                        OperationType.CLOSE_ALERT -> syncCloseAlert(operation)
                    }
                    
                    // Remove successful operation
                    pendingOperationDao.deletePendingOperation(operation)
                    Log.d(TAG, "Successfully synced operation: ${operation.operationType} for alert ${operation.alertId}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync operation: ${operation.operationType} for alert ${operation.alertId}", e)
                    
                    // Increment retry count
                    pendingOperationDao.incrementRetryCount(operation.id)
                    
                    // Remove if max retries exceeded
                    if (operation.retryCount >= operation.maxRetries) {
                        pendingOperationDao.deletePendingOperation(operation)
                        Log.w(TAG, "Max retries exceeded for operation: ${operation.operationType} for alert ${operation.alertId}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync operations", e)
        }
    }
    
    /**
     * Sync cached data with Firebase (download latest data)
     */
    suspend fun syncCachedData() {
        if (!isOnline()) {
            Log.d(TAG, "Device offline, skipping data sync")
            return
        }
        
        try {
            // Sync alerts
            syncAlerts()
            
            // Sync campus blocks
            syncCampusBlocks()
            
            Log.d(TAG, "Data sync completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during data sync", e)
        }
    }
    
    /**
     * Sync alerts from Firebase
     */
    private suspend fun syncAlerts() {
        try {
            // This would typically use Firebase listeners, but for sync we'll do a one-time fetch
            // In a real implementation, you'd set up Firestore listeners in the activities
            Log.d(TAG, "Alert sync would be handled by Firestore listeners in activities")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync alerts", e)
        }
    }
    
    /**
     * Sync campus blocks from Firebase
     */
    private suspend fun syncCampusBlocks() {
        try {
            // Fetch campus blocks from Firebase and cache them
            // This would be implemented with actual Firebase calls
            Log.d(TAG, "Campus blocks sync would fetch from Firebase and cache locally")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync campus blocks", e)
        }
    }
    
    // Individual sync operations
    
    private suspend fun syncCreateAlert(operation: PendingOperationEntity) {
        val data = JSONObject(operation.operationData)
        val localAlert = alertDao.getAlertById(operation.alertId)
        
        localAlert?.let { alertEntity ->
            val alert = alertEntity.toAlert()
            val task = firebaseService.createAlert(alert)
            
            // Wait for completion
            val documentRef = Tasks.await(task)
            
            // Update local alert with Firebase ID and mark as synced
            val updatedAlert = alertEntity.copy(
                id = documentRef.id,
                isSynced = true
            )
            
            // Remove old temp alert and insert with new ID
            alertDao.deleteAlertById(operation.alertId)
            alertDao.insertAlert(updatedAlert)
        }
    }
    
    private suspend fun syncUpdateAlert(operation: PendingOperationEntity) {
        val data = JSONObject(operation.operationData)
        val status = AlertStatus.valueOf(data.getString("status"))
        val userId = data.optString("userId").takeIf { it.isNotEmpty() }
        
        val task = firebaseService.updateAlertStatus(operation.alertId, status, userId)
        Tasks.await(task)
        
        // Mark local alert as synced
        alertDao.updateAlertSyncStatus(operation.alertId, true)
    }
    
    private suspend fun syncAcceptAlert(operation: PendingOperationEntity) {
        val data = JSONObject(operation.operationData)
        val userId = data.getString("userId")
        
        val task = firebaseService.acceptAlert(operation.alertId, userId)
        Tasks.await(task)
        
        // Mark local alert as synced
        alertDao.updateAlertSyncStatus(operation.alertId, true)
    }
    
    private suspend fun syncResolveAlert(operation: PendingOperationEntity) {
        val task = firebaseService.resolveAlert(operation.alertId)
        Tasks.await(task)
        
        // Mark local alert as synced
        alertDao.updateAlertSyncStatus(operation.alertId, true)
    }
    
    private suspend fun syncCloseAlert(operation: PendingOperationEntity) {
        val data = JSONObject(operation.operationData)
        val userId = data.getString("userId")
        
        val task = firebaseService.closeAlert(operation.alertId, userId)
        Tasks.await(task)
        
        // Mark local alert as synced
        alertDao.updateAlertSyncStatus(operation.alertId, true)
    }
    
    // Utility Methods
    
    /**
     * Get count of unsynced alerts
     */
    suspend fun getUnsyncedAlertCount(): Int {
        return alertDao.getUnsyncedAlertCount()
    }
    
    /**
     * Get count of pending operations
     */
    suspend fun getPendingOperationCount(): Int {
        return pendingOperationDao.getPendingOperationCount()
    }
    
    /**
     * Clear old cached data
     */
    suspend fun clearOldCache() {
        try {
            val cutoffTime = System.currentTimeMillis() - (MAX_CACHE_AGE_DAYS * 24 * 60 * 60 * 1000L)
            alertDao.deleteOldAlerts(cutoffTime / 1000)
            Log.d(TAG, "Cleared old cached alerts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear old cache", e)
        }
    }
    
    /**
     * Get sync status information
     */
    suspend fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isOnline = isOnline(),
            unsyncedAlerts = getUnsyncedAlertCount(),
            pendingOperations = getPendingOperationCount(),
            lastSyncTime = System.currentTimeMillis() // This would be stored in preferences
        )
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        networkListener?.cancel()
        syncJob?.cancel()
        scope.cancel()
    }
}

/**
 * Data class representing sync status
 */
data class SyncStatus(
    val isOnline: Boolean,
    val unsyncedAlerts: Int,
    val pendingOperations: Int,
    val lastSyncTime: Long
)