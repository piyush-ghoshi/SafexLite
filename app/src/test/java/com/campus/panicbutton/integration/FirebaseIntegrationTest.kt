package com.campus.panicbutton.integration

import com.campus.panicbutton.models.*
import com.campus.panicbutton.services.FirebaseService
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.After
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for Firebase operations using Firebase Emulator
 * 
 * To run these tests:
 * 1. Start Firebase emulator: firebase emulators:start --only firestore
 * 2. Run tests with: ./gradlew testDebugUnitTest --tests "*FirebaseIntegrationTest*"
 */
class FirebaseIntegrationTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseService: FirebaseService

    @Before
    fun setup() {
        // Configure Firestore to use emulator
        val settings = FirebaseFirestoreSettings.Builder()
            .setHost("10.0.2.2:8080") // Android emulator host
            .setSslEnabled(false)
            .setPersistenceEnabled(false)
            .build()

        firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = settings
        
        firebaseService = FirebaseService()
        
        // Clear test data
        clearTestData()
    }

    @After
    fun tearDown() {
        clearTestData()
    }

    @Test
    fun `createAlert integration test`() {
        val alert = Alert(
            guardId = "test_guard_123",
            guardName = "Test Guard",
            timestamp = Timestamp(Date()),
            message = "Test emergency alert"
        )

        val latch = CountDownLatch(1)
        var createdAlertId: String? = null
        var error: Exception? = null

        firebaseService.createAlert(alert)
            .addOnSuccessListener { documentRef ->
                createdAlertId = documentRef.id
                latch.countDown()
            }
            .addOnFailureListener { exception ->
                error = exception
                latch.countDown()
            }

        assertTrue("Operation should complete within 10 seconds", latch.await(10, TimeUnit.SECONDS))
        assertNull("Should not have error", error)
        assertNotNull("Should have created alert ID", createdAlertId)

        // Verify alert was stored correctly
        val verifyLatch = CountDownLatch(1)
        var retrievedAlert: Alert? = null

        firestore.collection("alerts").document(createdAlertId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    retrievedAlert = document.toObject(Alert::class.java)
                }
                verifyLatch.countDown()
            }

        assertTrue(verifyLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("Should retrieve created alert", retrievedAlert)
        assertEquals("Guard ID should match", alert.guardId, retrievedAlert?.guardId)
        assertEquals("Guard name should match", alert.guardName, retrievedAlert?.guardName)
        assertEquals("Message should match", alert.message, retrievedAlert?.message)
    }

    @Test
    fun `updateAlertStatus integration test`() {
        // First create an alert
        val alert = Alert(
            guardId = "test_guard_456",
            guardName = "Test Guard 2",
            status = AlertStatus.ACTIVE
        )

        val createLatch = CountDownLatch(1)
        var alertId: String? = null

        firebaseService.createAlert(alert)
            .addOnSuccessListener { documentRef ->
                alertId = documentRef.id
                createLatch.countDown()
            }

        assertTrue(createLatch.await(10, TimeUnit.SECONDS))
        assertNotNull(alertId)

        // Now update the status
        val updateLatch = CountDownLatch(1)
        var updateError: Exception? = null

        firebaseService.updateAlertStatus(alertId!!, AlertStatus.IN_PROGRESS, "accepting_guard_789")
            .addOnSuccessListener {
                updateLatch.countDown()
            }
            .addOnFailureListener { exception ->
                updateError = exception
                updateLatch.countDown()
            }

        assertTrue(updateLatch.await(10, TimeUnit.SECONDS))
        assertNull("Update should not have error", updateError)

        // Verify the update
        val verifyLatch = CountDownLatch(1)
        var updatedAlert: Alert? = null

        firestore.collection("alerts").document(alertId!!)
            .get()
            .addOnSuccessListener { document ->
                updatedAlert = document.toObject(Alert::class.java)
                verifyLatch.countDown()
            }

        assertTrue(verifyLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(updatedAlert)
        assertEquals("Status should be updated", AlertStatus.IN_PROGRESS, updatedAlert?.status)
        assertEquals("AcceptedBy should be set", "accepting_guard_789", updatedAlert?.acceptedBy)
        assertNotNull("AcceptedAt should be set", updatedAlert?.acceptedAt)
    }

    @Test
    fun `getUserRole integration test`() {
        // First create a test user
        val user = User(
            id = "test_user_123",
            email = "test@campus.edu",
            name = "Test User",
            role = UserRole.ADMIN
        )

        val createLatch = CountDownLatch(1)
        firestore.collection("users").document(user.id)
            .set(user)
            .addOnSuccessListener { createLatch.countDown() }

        assertTrue(createLatch.await(5, TimeUnit.SECONDS))

        // Now test getUserRole
        val getLatch = CountDownLatch(1)
        var retrievedRole: UserRole? = null
        var getError: Exception? = null

        firebaseService.getUserRole(user.id)
            .addOnSuccessListener { role ->
                retrievedRole = role
                getLatch.countDown()
            }
            .addOnFailureListener { exception ->
                getError = exception
                getLatch.countDown()
            }

        assertTrue(getLatch.await(5, TimeUnit.SECONDS))
        assertNull("Should not have error", getError)
        assertEquals("Should retrieve correct role", UserRole.ADMIN, retrievedRole)
    }

    @Test
    fun `getAllAlerts query integration test`() {
        // Create multiple test alerts
        val alerts = listOf(
            Alert(guardId = "guard1", guardName = "Guard 1", status = AlertStatus.ACTIVE),
            Alert(guardId = "guard2", guardName = "Guard 2", status = AlertStatus.IN_PROGRESS),
            Alert(guardId = "guard3", guardName = "Guard 3", status = AlertStatus.RESOLVED)
        )

        val createLatch = CountDownLatch(alerts.size)
        alerts.forEach { alert ->
            firebaseService.createAlert(alert)
                .addOnSuccessListener { createLatch.countDown() }
        }

        assertTrue("All alerts should be created", createLatch.await(15, TimeUnit.SECONDS))

        // Test getAllAlerts query
        val queryLatch = CountDownLatch(1)
        var retrievedAlerts: List<Alert>? = null

        firebaseService.getAllAlerts()
            .get()
            .addOnSuccessListener { querySnapshot ->
                retrievedAlerts = querySnapshot.toObjects(Alert::class.java)
                queryLatch.countDown()
            }

        assertTrue(queryLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("Should retrieve alerts", retrievedAlerts)
        assertTrue("Should have at least 3 alerts", retrievedAlerts!!.size >= 3)
    }

    @Test
    fun `getActiveAlerts query integration test`() {
        // Create alerts with different statuses
        val alerts = listOf(
            Alert(guardId = "guard1", guardName = "Guard 1", status = AlertStatus.ACTIVE),
            Alert(guardId = "guard2", guardName = "Guard 2", status = AlertStatus.IN_PROGRESS),
            Alert(guardId = "guard3", guardName = "Guard 3", status = AlertStatus.CLOSED)
        )

        val createLatch = CountDownLatch(alerts.size)
        alerts.forEach { alert ->
            firebaseService.createAlert(alert)
                .addOnSuccessListener { createLatch.countDown() }
        }

        assertTrue(createLatch.await(15, TimeUnit.SECONDS))

        // Test getActiveAlerts query (should only return ACTIVE and IN_PROGRESS)
        val queryLatch = CountDownLatch(1)
        var activeAlerts: List<Alert>? = null

        firebaseService.getActiveAlerts()
            .get()
            .addOnSuccessListener { querySnapshot ->
                activeAlerts = querySnapshot.toObjects(Alert::class.java)
                queryLatch.countDown()
            }

        assertTrue(queryLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("Should retrieve active alerts", activeAlerts)
        
        // Verify only active and in-progress alerts are returned
        activeAlerts!!.forEach { alert ->
            assertTrue(
                "Alert should be active or in-progress",
                alert.status == AlertStatus.ACTIVE || alert.status == AlertStatus.IN_PROGRESS
            )
        }
    }

    @Test
    fun `updateFcmToken integration test`() {
        val userId = "test_user_fcm"
        val user = User(
            id = userId,
            email = "fcm@campus.edu",
            name = "FCM Test User"
        )

        // Create user first
        val createLatch = CountDownLatch(1)
        firestore.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener { createLatch.countDown() }

        assertTrue(createLatch.await(5, TimeUnit.SECONDS))

        // Update FCM token
        val token = "test_fcm_token_123456"
        val updateLatch = CountDownLatch(1)
        var updateError: Exception? = null

        firebaseService.updateFcmToken(userId, token)
            .addOnSuccessListener { updateLatch.countDown() }
            .addOnFailureListener { exception ->
                updateError = exception
                updateLatch.countDown()
            }

        assertTrue(updateLatch.await(5, TimeUnit.SECONDS))
        assertNull("Update should not have error", updateError)

        // Verify token was updated
        val verifyLatch = CountDownLatch(1)
        var updatedUser: User? = null

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                updatedUser = document.toObject(User::class.java)
                verifyLatch.countDown()
            }

        assertTrue(verifyLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(updatedUser)
        assertEquals("FCM token should be updated", token, updatedUser?.fcmToken)
        assertNotNull("LastSeen should be updated", updatedUser?.lastSeen)
    }

    private fun clearTestData() {
        val collections = listOf("alerts", "users", "campus_blocks")
        val latch = CountDownLatch(collections.size)

        collections.forEach { collection ->
            firestore.collection(collection)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val batch = firestore.batch()
                    querySnapshot.documents.forEach { document ->
                        batch.delete(document.reference)
                    }
                    batch.commit().addOnCompleteListener { latch.countDown() }
                }
                .addOnFailureListener { latch.countDown() }
        }

        latch.await(10, TimeUnit.SECONDS)
    }
}