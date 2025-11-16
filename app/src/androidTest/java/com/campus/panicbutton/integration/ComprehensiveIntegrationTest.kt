package com.campus.panicbutton.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.campus.panicbutton.activities.LoginActivity
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.utils.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive integration test covering complete alert workflow
 * Requirements: 1.1, 1.5, 2.1, 3.1, 4.1, 7.1
 */
@RunWith(AndroidJUnit4::class)
class ComprehensiveIntegrationTest {

    @get:Rule
    val activityRule = ActivityTestRule(LoginActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private lateinit var firebaseService: FirebaseService
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val testGuardEmail = "test.guard@campus.edu"
    private val testAdminEmail = "test.admin@campus.edu"
    private val testPassword = "testPassword123"

    @Before
    fun setUp() {
        firebaseService = FirebaseService()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        // Use Firebase emulator for testing
        firestore.useEmulator("10.0.2.2", 8080)
        auth.useEmulator("10.0.2.2", 9099)
        
        setupTestUsers()
    }

    @After
    fun tearDown() {
        cleanupTestData()
    }

    /**
     * Test complete alert workflow from creation to resolution
     * Requirements: 1.1, 1.5, 3.1
     */
    @Test
    fun testCompleteAlertWorkflow() = runBlocking {
        // Step 1: Guard creates alert
        val guardUser = authenticateTestGuard()
        assertNotNull(guardUser)
        
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Test emergency alert",
            status = AlertStatus.ACTIVE
        )
        
        val alertRef = firebaseService.createAlert(alert).await()
        assertNotNull(alertRef)
        val alertId = alertRef.id
        
        // Step 2: Verify alert is stored correctly
        val storedAlert = firestore.collection("alerts")
            .document(alertId)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertNotNull(storedAlert)
        assertEquals(AlertStatus.ACTIVE, storedAlert.status)
        assertEquals(guardUser.id, storedAlert.guardId)
        
        // Step 3: Another guard accepts the alert
        val acceptingGuard = createTestUser("accepting.guard@campus.edu", UserRole.GUARD)
        firebaseService.updateAlertStatus(alertId, AlertStatus.IN_PROGRESS, acceptingGuard.id)
        
        // Step 4: Verify alert status update
        val updatedAlert = firestore.collection("alerts")
            .document(alertId)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertNotNull(updatedAlert)
        assertEquals(AlertStatus.IN_PROGRESS, updatedAlert.status)
        assertEquals(acceptingGuard.id, updatedAlert.acceptedBy)
        
        // Step 5: Guard resolves the alert
        firebaseService.updateAlertStatus(alertId, AlertStatus.RESOLVED, acceptingGuard.id)
        
        // Step 6: Admin closes the alert
        val adminUser = authenticateTestAdmin()
        firebaseService.updateAlertStatus(alertId, AlertStatus.CLOSED, adminUser.id)
        
        // Step 7: Verify final alert state
        val finalAlert = firestore.collection("alerts")
            .document(alertId)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertNotNull(finalAlert)
        assertEquals(AlertStatus.CLOSED, finalAlert.status)
        assertEquals(adminUser.id, finalAlert.closedBy)
        assertNotNull(finalAlert.closedAt)
    }

    /**
     * Test real-time notifications across multiple devices simulation
     * Requirements: 2.1
     */
    @Test
    fun testRealTimeNotifications() = runBlocking {
        val latch = CountDownLatch(2) // Expecting 2 notifications
        val receivedNotifications = mutableListOf<Alert>()
        
        // Simulate multiple device listeners
        val listener1 = firestore.collection("alerts")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val alert = change.document.toObject(Alert::class.java)
                        receivedNotifications.add(alert)
                        latch.countDown()
                    }
                }
            }
        
        val listener2 = firestore.collection("alerts")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        latch.countDown()
                    }
                }
            }
        
        // Create alert to trigger notifications
        val guardUser = authenticateTestGuard()
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Real-time test alert"
        )
        
        firebaseService.createAlert(alert).await()
        
        // Wait for notifications to be received
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertTrue(receivedNotifications.isNotEmpty())
        
        listener1.remove()
        listener2.remove()
    }

    /**
     * Test role-based functionality for guards and administrators
     * Requirements: 4.1
     */
    @Test
    fun testRoleBasedFunctionality() = runBlocking {
        // Test Guard functionality
        val guardUser = authenticateTestGuard()
        
        // Guard should be able to create alerts
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Role test alert"
        )
        
        val alertRef = firebaseService.createAlert(alert).await()
        assertNotNull(alertRef)
        
        // Guard should be able to accept and resolve alerts
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.IN_PROGRESS, guardUser.id)
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.RESOLVED, guardUser.id)
        
        // Test Admin functionality
        val adminUser = authenticateTestAdmin()
        
        // Admin should be able to view all alerts
        val allAlerts = firestore.collection("alerts")
            .get()
            .await()
            .toObjects(Alert::class.java)
        
        assertTrue(allAlerts.isNotEmpty())
        
        // Admin should be able to close alerts
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.CLOSED, adminUser.id)
        
        val closedAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(AlertStatus.CLOSED, closedAlert?.status)
        assertEquals(adminUser.id, closedAlert?.closedBy)
    }

    /**
     * Test offline/online synchronization scenarios
     * Requirements: 7.1
     */
    @Test
    fun testOfflineOnlineSynchronization() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val networkUtils = NetworkUtils(context)
        
        // Simulate offline scenario
        val guardUser = authenticateTestGuard()
        
        // Create alert while "offline" (using local storage)
        val offlineAlert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Offline test alert"
        )
        
        // Simulate network coming back online and sync
        val alertRef = firebaseService.createAlert(offlineAlert).await()
        assertNotNull(alertRef)
        
        // Verify alert was synced to Firestore
        val syncedAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertNotNull(syncedAlert)
        assertEquals(offlineAlert.message, syncedAlert.message)
        assertEquals(offlineAlert.guardId, syncedAlert.guardId)
    }

    /**
     * Test load testing with multiple concurrent alerts
     * Requirements: 1.1, 2.1
     */
    @Test
    fun testMultipleConcurrentAlerts() = runBlocking {
        val guardUser = authenticateTestGuard()
        val numberOfAlerts = 10
        val alertRefs = mutableListOf<String>()
        
        // Create multiple alerts concurrently
        repeat(numberOfAlerts) { index ->
            val alert = Alert(
                guardId = guardUser.id,
                guardName = guardUser.name,
                message = "Concurrent alert #$index"
            )
            
            val alertRef = firebaseService.createAlert(alert).await()
            alertRefs.add(alertRef.id)
        }
        
        // Verify all alerts were created
        assertEquals(numberOfAlerts, alertRefs.size)
        
        // Verify all alerts exist in Firestore
        alertRefs.forEach { alertId ->
            val alert = firestore.collection("alerts")
                .document(alertId)
                .get()
                .await()
                .toObject(Alert::class.java)
            
            assertNotNull(alert)
            assertEquals(AlertStatus.ACTIVE, alert.status)
        }
        
        // Test concurrent status updates
        alertRefs.forEachIndexed { index, alertId ->
            if (index % 2 == 0) {
                firebaseService.updateAlertStatus(alertId, AlertStatus.IN_PROGRESS, guardUser.id)
            }
        }
        
        // Verify status updates
        var inProgressCount = 0
        alertRefs.forEachIndexed { index, alertId ->
            val alert = firestore.collection("alerts")
                .document(alertId)
                .get()
                .await()
                .toObject(Alert::class.java)
            
            if (index % 2 == 0) {
                assertEquals(AlertStatus.IN_PROGRESS, alert?.status)
                inProgressCount++
            }
        }
        
        assertEquals(numberOfAlerts / 2, inProgressCount)
    }

    private suspend fun authenticateTestGuard(): User {
        auth.signInWithEmailAndPassword(testGuardEmail, testPassword).await()
        return firestore.collection("users")
            .document(auth.currentUser!!.uid)
            .get()
            .await()
            .toObject(User::class.java)!!
    }

    private suspend fun authenticateTestAdmin(): User {
        auth.signInWithEmailAndPassword(testAdminEmail, testPassword).await()
        return firestore.collection("users")
            .document(auth.currentUser!!.uid)
            .get()
            .await()
            .toObject(User::class.java)!!
    }

    private fun setupTestUsers() = runBlocking {
        // Create test guard user
        val guardAuthResult = auth.createUserWithEmailAndPassword(testGuardEmail, testPassword).await()
        val guardUser = User(
            id = guardAuthResult.user!!.uid,
            email = testGuardEmail,
            name = "Test Guard",
            role = UserRole.GUARD
        )
        firestore.collection("users").document(guardUser.id).set(guardUser).await()
        
        // Create test admin user
        val adminAuthResult = auth.createUserWithEmailAndPassword(testAdminEmail, testPassword).await()
        val adminUser = User(
            id = adminAuthResult.user!!.uid,
            email = testAdminEmail,
            name = "Test Admin",
            role = UserRole.ADMIN
        )
        firestore.collection("users").document(adminUser.id).set(adminUser).await()
    }

    private suspend fun createTestUser(email: String, role: UserRole): User {
        val authResult = auth.createUserWithEmailAndPassword(email, "testPassword123").await()
        val user = User(
            id = authResult.user!!.uid,
            email = email,
            name = "Test User",
            role = role
        )
        firestore.collection("users").document(user.id).set(user).await()
        return user
    }

    private fun cleanupTestData() = runBlocking {
        // Clean up test alerts
        val alerts = firestore.collection("alerts").get().await()
        alerts.documents.forEach { it.reference.delete() }
        
        // Clean up test users
        val users = firestore.collection("users").get().await()
        users.documents.forEach { it.reference.delete() }
        
        auth.signOut()
    }
}