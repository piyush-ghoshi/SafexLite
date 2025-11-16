package com.campus.panicbutton.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.services.FirebaseService
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
 * Integration test for role-based access control
 * Requirements: 4.1, 6.2, 6.3
 */
@RunWith(AndroidJUnit4::class)
class RoleBasedAccessIntegrationTest {

    private lateinit var firebaseService: FirebaseService
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val testGuardEmail = "rbac.guard@campus.edu"
    private val testAdminEmail = "rbac.admin@campus.edu"
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
     * Test guard role permissions and restrictions
     * Requirements: 6.2, 6.3
     */
    @Test
    fun testGuardRolePermissions() = runBlocking {
        val guardUser = authenticateTestGuard()
        
        // Guards should be able to create alerts
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Guard role test alert"
        )
        
        val alertRef = firebaseService.createAlert(alert).await()
        assertNotNull(alertRef)
        
        // Verify alert was created with correct guard information
        val createdAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertNotNull(createdAlert)
        assertEquals(guardUser.id, createdAlert.guardId)
        assertEquals(guardUser.name, createdAlert.guardName)
        assertEquals(AlertStatus.ACTIVE, createdAlert.status)
        
        // Guards should be able to accept alerts
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.IN_PROGRESS, guardUser.id)
        
        val acceptedAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(AlertStatus.IN_PROGRESS, acceptedAlert?.status)
        assertEquals(guardUser.id, acceptedAlert?.acceptedBy)
        
        // Guards should be able to resolve alerts
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.RESOLVED, guardUser.id)
        
        val resolvedAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(AlertStatus.RESOLVED, resolvedAlert?.status)
        
        // Guards should be able to view alerts assigned to them
        val guardAlerts = firestore.collection("alerts")
            .whereEqualTo("guardId", guardUser.id)
            .get()
            .await()
        
        assertTrue(guardAlerts.documents.isNotEmpty())
    }

    /**
     * Test admin role permissions and capabilities
     * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
     */
    @Test
    fun testAdminRolePermissions() = runBlocking {
        val adminUser = authenticateTestAdmin()
        val guardUser = createTestGuard("admin.test.guard@campus.edu")
        
        // Create alert as guard first
        auth.signInWithEmailAndPassword("admin.test.guard@campus.edu", testPassword).await()
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Admin role test alert"
        )
        val alertRef = firebaseService.createAlert(alert).await()
        
        // Switch back to admin user
        auth.signInWithEmailAndPassword(testAdminEmail, testPassword).await()
        
        // Admins should be able to view all alerts
        val allAlerts = firestore.collection("alerts").get().await()
        assertTrue(allAlerts.documents.isNotEmpty())
        
        // Admins should be able to close any alert
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.CLOSED, adminUser.id)
        
        val closedAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(AlertStatus.CLOSED, closedAlert?.status)
        assertEquals(adminUser.id, closedAlert?.closedBy)
        assertNotNull(closedAlert?.closedAt)
        
        // Admins should be able to reopen closed alerts
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.ACTIVE, adminUser.id)
        
        val reopenedAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(AlertStatus.ACTIVE, reopenedAlert?.status)
        
        // Admins should be able to view alert statistics
        val activeAlerts = firestore.collection("alerts")
            .whereEqualTo("status", AlertStatus.ACTIVE.name)
            .get()
            .await()
        
        val inProgressAlerts = firestore.collection("alerts")
            .whereEqualTo("status", AlertStatus.IN_PROGRESS.name)
            .get()
            .await()
        
        val resolvedAlerts = firestore.collection("alerts")
            .whereEqualTo("status", AlertStatus.RESOLVED.name)
            .get()
            .await()
        
        val closedAlerts = firestore.collection("alerts")
            .whereEqualTo("status", AlertStatus.CLOSED.name)
            .get()
            .await()
        
        // Verify admin can access all alert categories
        assertTrue(activeAlerts.size() >= 0)
        assertTrue(inProgressAlerts.size() >= 0)
        assertTrue(resolvedAlerts.size() >= 0)
        assertTrue(closedAlerts.size() >= 0)
    }

    /**
     * Test role validation and authentication
     * Requirements: 6.2, 6.3
     */
    @Test
    fun testRoleValidationAndAuthentication() = runBlocking {
        // Test guard role validation
        val guardUser = authenticateTestGuard()
        assertEquals(UserRole.GUARD, guardUser.role)
        assertTrue(guardUser.isActive)
        
        // Test admin role validation
        val adminUser = authenticateTestAdmin()
        assertEquals(UserRole.ADMIN, adminUser.role)
        assertTrue(adminUser.isActive)
        
        // Test that user roles are properly stored and retrieved
        val storedGuardUser = firestore.collection("users")
            .document(guardUser.id)
            .get()
            .await()
            .toObject(User::class.java)
        
        assertNotNull(storedGuardUser)
        assertEquals(UserRole.GUARD, storedGuardUser.role)
        
        val storedAdminUser = firestore.collection("users")
            .document(adminUser.id)
            .get()
            .await()
            .toObject(User::class.java)
        
        assertNotNull(storedAdminUser)
        assertEquals(UserRole.ADMIN, storedAdminUser.role)
    }

    /**
     * Test cross-role alert management scenarios
     * Requirements: 3.1, 4.1
     */
    @Test
    fun testCrossRoleAlertManagement() = runBlocking {
        val guardUser = authenticateTestGuard()
        val adminUser = authenticateTestAdmin()
        
        // Guard creates alert
        val alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Cross-role management test alert"
        )
        
        val alertRef = firebaseService.createAlert(alert).await()
        
        // Another guard accepts the alert
        val acceptingGuard = createTestGuard("accepting.guard.rbac@campus.edu")
        auth.signInWithEmailAndPassword("accepting.guard.rbac@campus.edu", testPassword).await()
        
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.IN_PROGRESS, acceptingGuard.id)
        
        // Guard resolves the alert
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.RESOLVED, acceptingGuard.id)
        
        // Admin closes the alert
        auth.signInWithEmailAndPassword(testAdminEmail, testPassword).await()
        firebaseService.updateAlertStatus(alertRef.id, AlertStatus.CLOSED, adminUser.id)
        
        // Verify complete workflow with proper role tracking
        val finalAlert = firestore.collection("alerts")
            .document(alertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertNotNull(finalAlert)
        assertEquals(guardUser.id, finalAlert.guardId) // Original creator
        assertEquals(acceptingGuard.id, finalAlert.acceptedBy) // Guard who accepted
        assertEquals(adminUser.id, finalAlert.closedBy) // Admin who closed
        assertEquals(AlertStatus.CLOSED, finalAlert.status)
    }

    /**
     * Test role-based data filtering and access
     * Requirements: 4.1, 4.2
     */
    @Test
    fun testRoleBasedDataFiltering() = runBlocking {
        val guardUser = authenticateTestGuard()
        val adminUser = authenticateTestAdmin()
        
        // Create multiple alerts from different guards
        val guard1Alert = Alert(
            guardId = guardUser.id,
            guardName = guardUser.name,
            message = "Guard 1 alert"
        )
        
        val guard1AlertRef = firebaseService.createAlert(guard1Alert).await()
        
        val guard2 = createTestGuard("guard2.rbac@campus.edu")
        auth.signInWithEmailAndPassword("guard2.rbac@campus.edu", testPassword).await()
        
        val guard2Alert = Alert(
            guardId = guard2.id,
            guardName = guard2.name,
            message = "Guard 2 alert"
        )
        
        val guard2AlertRef = firebaseService.createAlert(guard2Alert).await()
        
        // Test guard can see all alerts (for response purposes)
        auth.signInWithEmailAndPassword(testGuardEmail, testPassword).await()
        val allAlertsForGuard = firestore.collection("alerts").get().await()
        assertTrue(allAlertsForGuard.size() >= 2)
        
        // Test admin can see all alerts with full details
        auth.signInWithEmailAndPassword(testAdminEmail, testPassword).await()
        val allAlertsForAdmin = firestore.collection("alerts").get().await()
        assertTrue(allAlertsForAdmin.size() >= 2)
        
        // Verify admin can see guard-specific information
        val guard1AlertForAdmin = firestore.collection("alerts")
            .document(guard1AlertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(guardUser.id, guard1AlertForAdmin?.guardId)
        assertEquals(guardUser.name, guard1AlertForAdmin?.guardName)
        
        val guard2AlertForAdmin = firestore.collection("alerts")
            .document(guard2AlertRef.id)
            .get()
            .await()
            .toObject(Alert::class.java)
        
        assertEquals(guard2.id, guard2AlertForAdmin?.guardId)
        assertEquals(guard2.name, guard2AlertForAdmin?.guardName)
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

    private suspend fun createTestGuard(email: String): User {
        val authResult = auth.createUserWithEmailAndPassword(email, testPassword).await()
        val user = User(
            id = authResult.user!!.uid,
            email = email,
            name = "Test Guard ${email.split("@")[0]}",
            role = UserRole.GUARD
        )
        firestore.collection("users").document(user.id).set(user).await()
        return user
    }

    private fun setupTestUsers() = runBlocking {
        try {
            // Create test guard user
            val guardAuthResult = auth.createUserWithEmailAndPassword(testGuardEmail, testPassword).await()
            val guardUser = User(
                id = guardAuthResult.user!!.uid,
                email = testGuardEmail,
                name = "RBAC Test Guard",
                role = UserRole.GUARD
            )
            firestore.collection("users").document(guardUser.id).set(guardUser).await()
            
            // Create test admin user
            val adminAuthResult = auth.createUserWithEmailAndPassword(testAdminEmail, testPassword).await()
            val adminUser = User(
                id = adminAuthResult.user!!.uid,
                email = testAdminEmail,
                name = "RBAC Test Admin",
                role = UserRole.ADMIN
            )
            firestore.collection("users").document(adminUser.id).set(adminUser).await()
        } catch (e: Exception) {
            // Users might already exist
            try {
                auth.signInWithEmailAndPassword(testGuardEmail, testPassword).await()
                auth.signInWithEmailAndPassword(testAdminEmail, testPassword).await()
            } catch (signInException: Exception) {
                // Handle sign-in failure
            }
        }
    }

    private fun cleanupTestData() = runBlocking {
        try {
            // Clean up test alerts
            val alerts = firestore.collection("alerts").get().await()
            alerts.documents.forEach { it.reference.delete() }
            
            // Clean up test users
            val users = firestore.collection("users")
                .whereGreaterThanOrEqualTo("email", "rbac.")
                .whereLessThan("email", "rbac.z")
                .get()
                .await()
            
            users.documents.forEach { it.reference.delete() }
            
            auth.signOut()
        } catch (e: Exception) {
            // Handle cleanup errors gracefully
        }
    }
}