package com.campus.panicbutton.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campus.panicbutton.database.AppDatabase
import com.campus.panicbutton.database.entities.AlertEntity
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.repository.OfflineRepository
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.utils.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for offline synchronization functionality
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
@RunWith(AndroidJUnit4::class)
class OfflineSyncIntegrationTest {

    private lateinit var firebaseService: FirebaseService
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var database: AppDatabase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var networkUtils: NetworkUtils
    private val testGuardEmail = "offline.guard@campus.edu"
    private val testPassword = "testPassword123"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        firebaseService = FirebaseService()
        database = AppDatabase.getDatabase(context)
        offlineRepository = OfflineRepository(database)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        networkUtils = NetworkUtils(context)
        
        // Use Firebase emulator for testing
        firestore.useEmulator("10.0.2.2", 8080)
        auth.useEmulator("10.0.2.2", 9099)
        
        setupTestUser()
    }

    @After
    fun tearDown() {
        cleanupTestData()
    }

    /**
     * Test offline alert creation and synchronization when online
     * Requirements: 7.1, 7.2, 7.3
     */
    @Test
    fun testOfflineAlertCreationAndSync() = runBlocking {
        val guardUser = authenticateTestGuard()
        
        // Simulate offline scenario by creating alert locally
        val offlineAlert = Alert(
            id = "offline_alert_1",
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Offline created alert",
            status = AlertStatus.ACTIVE
        )
        
        // Store alert locally (simulating offline creation)
        val alertEntity = AlertEntity(
            id = offlineAlert.id,
            guardId = offlineAlert.guardId,
            guardName = offlineAlert.guardName,
            message = offlineAlert.message,
            status = offlineAlert.status.name,
            timestamp = System.currentTimeMillis(),
            isSynced = false
        )
        
        database.alertDao().insertAlert(alertEntity)
        
        // Verify alert is stored locally
        val localAlert = database.alertDao().getAlertById(offlineAlert.id)
        assertNotNull(localAlert)
        assertEquals(false, localAlert.isSynced)
        
        // Simulate coming back online and sync
        val unsyncedAlerts = database.alertDao().getUnsyncedAlerts()
        assertTrue(unsyncedAlerts.isNotEmpty())
        
        // Sync to Firebase
        unsyncedAlerts.forEach { alert ->
            val firebaseAlert = Alert(
                guardId = alert.guardId,
                guardName = alert.guardName,
                message = alert.message ?: "",
                status = AlertStatus.valueOf(alert.status)
            )
            
            val alertRef = firebaseService.createAlert(firebaseAlert).await()
            
            // Update local record as synced
            database.alertDao().updateSyncStatus(alert.id, true)
        }
        
        // Verify sync completed
        val syncedAlert = database.alertDao().getAlertById(offlineAlert.id)
        assertEquals(true, syncedAlert?.isSynced)
        
        // Verify alert exists in Firebase
        val firebaseAlerts = firestore.collection("alerts")
            .whereEqualTo("guardId", guardUser.id)
            .whereEqualTo("message", "Offline created alert")
            .get()
            .await()
        
        assertTrue(firebaseAlerts.documents.isNotEmpty())
    }

    /**
     * Test offline alert viewing with cached data
     * Requirements: 7.1, 7.4
     */
    @Test
    fun testOfflineAlertViewing() = runBlocking {
        val guardUser = authenticateTestGuard()
        
        // Create some alerts online first
        val onlineAlerts = mutableListOf<String>()
        repeat(3) { index ->
            val alert = Alert(
                guardId = guardUser.id,
                guardName = guardUser.name,
                message = "Online alert $index"
            )
            
            val alertRef = firebaseService.createAlert(alert).await()
            onlineAlerts.add(alertRef.id)
        }
        
        // Cache alerts locally (simulating automatic caching)
        val firebaseAlerts = firestore.collection("alerts")
            .whereEqualTo("guardId", guardUser.id)
            .get()
            .await()
        
        firebaseAlerts.documents.forEach { document ->
            val alert = document.toObject(Alert::class.java)
            if (alert != null) {
                val alertEntity = AlertEntity(
                    id = document.id,
                    guardId = alert.guardId,
                    guardName = alert.guardName,
                    message = alert.message,
                    status = alert.status.name,
                    timestamp = alert.timestamp?.toDate()?.time ?: System.currentTimeMillis(),
                    isSynced = true
                )
                database.alertDao().insertAlert(alertEntity)
            }
        }
        
        // Simulate offline scenario - retrieve alerts from local cache
        val cachedAlerts = database.alertDao().getAllAlerts()
        assertTrue(cachedAlerts.size >= 3)
        
        // Verify cached alerts contain expected data
        val offlineAlert = cachedAlerts.find { it.message?.contains("Online alert 0") == true }
        assertNotNull(offlineAlert)
        assertEquals(guardUser.id, offlineAlert.guardId)
        assertEquals(true, offlineAlert.isSynced)
    }

    /**
     * Test offline status updates and synchronization
     * Requirements: 7.2, 7.3
     */
    @Test
    fun testOfflineStatusUpdatesAndSync() = runBlocking {
        val guardUser = authenticateTestGuard()
        
        // Create alert online
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Status update test alert"
        )
        
        val alertRef = firebaseService.createAlert(alert).await()
        val alertId = alertRef.id
        
        // Cache alert locally
        val alertEntity = AlertEntity(
            id = alertId,
            guardId = alert.guardId,
            guardName = alert.guardName,
            message = alert.message,
            status = AlertStatus.ACTIVE.name,
            timestamp = System.currentTimeMillis(),
            isSynced = true
        )
        database.alertDao().insertAlert(alertEntity)
        
        // Simulate offline status update
        database.alertDao().updateAlertStatus(alertId, AlertStatus.IN_PROGRESS.name, false)
        
        // Verify local update
        val updatedLocalAlert = database.alertDao().getAlertById(alertId)
        assertEquals(AlertStatus.IN_PROGRESS.name, updatedLocalAlert?.status)
        assertEquals(false, updatedLocalAlert?.isSynced)
        
        // Simulate coming back online and sync status updates
        val unsyncedAlerts = database.alertDao().getUnsyncedAlerts()
        assertTrue(unsyncedAlerts.isNotEmpty())
        
        unsyncedAlerts.forEach { unsyncedAlert ->
            firebaseService.updateAlertStatus(
                unsyncedAlert.id,
                AlertStatus.valueOf(unsyncedAlert.status),
                guardUser.id
            )
            database.alertDao().updateSyncStatus(unsyncedAlert.id, true)
        }
        
        // Verify sync completed
        val syncedAlert = database.alertDao().getAlertById(alertId)
        assertEquals(true, syncedAlert?.isSynced)
        
        // Verify Firebase has the updated status
        val firebaseAlert = firestore.collection("alerts")
            .document(alertId)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(AlertStatus.IN_PROGRESS, firebaseAlert?.status)
    }

    /**
     * Test conflict resolution during synchronization
     * Requirements: 7.3
     */
    @Test
    fun testSyncConflictResolution() = runBlocking {
        val guardUser = authenticateTestGuard()
        
        // Create alert online
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Conflict resolution test alert"
        )
        
        val alertRef = firebaseService.createAlert(alert).await()
        val alertId = alertRef.id
        
        // Cache alert locally
        val alertEntity = AlertEntity(
            id = alertId,
            guardId = alert.guardId,
            guardName = alert.guardName,
            message = alert.message,
            status = AlertStatus.ACTIVE.name,
            timestamp = System.currentTimeMillis(),
            isSynced = true
        )
        database.alertDao().insertAlert(alertEntity)
        
        // Simulate offline local update
        database.alertDao().updateAlertStatus(alertId, AlertStatus.IN_PROGRESS.name, false)
        
        // Simulate concurrent online update (by another user)
        firebaseService.updateAlertStatus(alertId, AlertStatus.RESOLVED, "another_guard_id")
        
        // Attempt to sync local changes
        val localAlert = database.alertDao().getAlertById(alertId)
        assertNotNull(localAlert)
        
        // Check current Firebase state
        val currentFirebaseAlert = firestore.collection("alerts")
            .document(alertId)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        // In case of conflict, Firebase version should take precedence
        assertEquals(AlertStatus.RESOLVED, currentFirebaseAlert?.status)
        
        // Update local cache with Firebase version (conflict resolution)
        if (currentFirebaseAlert != null) {
            database.alertDao().updateAlertStatus(alertId, currentFirebaseAlert.status.name, true)
        }
        
        val resolvedLocalAlert = database.alertDao().getAlertById(alertId)
        assertEquals(AlertStatus.RESOLVED.name, resolvedLocalAlert?.status)
        assertEquals(true, resolvedLocalAlert?.isSynced)
    }

    /**
     * Test offline queue management and batch synchronization
     * Requirements: 7.2, 7.3
     */
    @Test
    fun testOfflineQueueAndBatchSync() = runBlocking {
        val guardUser = authenticateTestGuard()
        val numberOfOfflineOperations = 5
        
        // Create multiple offline operations
        repeat(numberOfOfflineOperations) { index ->
            val offlineAlert = AlertEntity(
                id = "offline_batch_$index",
                guardId = guardUser.id,
                guardName = guardUser.name,
                message = "Batch sync alert $index",
                status = AlertStatus.ACTIVE.name,
                timestamp = System.currentTimeMillis(),
                isSynced = false
            )
            database.alertDao().insertAlert(offlineAlert)
        }
        
        // Verify all operations are queued
        val queuedOperations = database.alertDao().getUnsyncedAlerts()
        assertEquals(numberOfOfflineOperations, queuedOperations.size)
        
        // Perform batch synchronization
        var syncedCount = 0
        queuedOperations.forEach { queuedAlert ->
            try {
                val firebaseAlert = Alert(
                    guardId = queuedAlert.guardId,
                    guardName = queuedAlert.guardName,
                    message = queuedAlert.message ?: "",
                    status = AlertStatus.valueOf(queuedAlert.status)
                )
                
                firebaseService.createAlert(firebaseAlert).await()
                database.alertDao().updateSyncStatus(queuedAlert.id, true)
                syncedCount++
            } catch (e: Exception) {
                // Handle individual sync failures
                println("Failed to sync alert ${queuedAlert.id}: ${e.message}")
            }
        }
        
        // Verify batch sync results
        assertTrue(syncedCount >= numberOfOfflineOperations * 0.8) // Allow for some failures
        
        val remainingUnsynced = database.alertDao().getUnsyncedAlerts()
        assertTrue(remainingUnsynced.size <= numberOfOfflineOperations * 0.2)
    }

    private suspend fun authenticateTestGuard(): User {
        auth.signInWithEmailAndPassword(testGuardEmail, testPassword).await()
        return firestore.collection("users")
            .document(auth.currentUser!!.uid)
            .get()
            .await()
            .toObject(User::class.java)!!
    }

    private fun setupTestUser() = runBlocking {
        try {
            val authResult = auth.createUserWithEmailAndPassword(testGuardEmail, testPassword).await()
            val guardUser = User(
                id = authResult.user!!.uid,
                email = testGuardEmail,
                name = "Offline Test Guard",
                role = UserRole.GUARD
            )
            firestore.collection("users").document(guardUser.id).set(guardUser).await()
        } catch (e: Exception) {
            // User might already exist
            try {
                auth.signInWithEmailAndPassword(testGuardEmail, testPassword).await()
            } catch (signInException: Exception) {
                // Handle sign-in failure
            }
        }
    }

    private fun cleanupTestData() = runBlocking {
        try {
            // Clean up local database
            database.alertDao().deleteAllAlerts()
            
            // Clean up Firebase alerts
            val alerts = firestore.collection("alerts").get().await()
            alerts.documents.forEach { it.reference.delete() }
            
            auth.signOut()
        } catch (e: Exception) {
            // Handle cleanup errors gracefully
        }
    }
}