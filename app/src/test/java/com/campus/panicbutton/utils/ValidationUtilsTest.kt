package com.campus.panicbutton.utils

import com.campus.panicbutton.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Test
import org.junit.Assert.*
import java.util.Date

class ValidationUtilsTest {

    @Test
    fun `validateAlert valid alert`() {
        val alert = Alert(
            guardId = "guard123",
            guardName = "John Doe",
            timestamp = Timestamp(Date()),
            status = AlertStatus.ACTIVE
        )

        val result = ValidationUtils.validateAlert(alert)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateAlert missing guard id`() {
        val alert = Alert(
            guardId = "",
            guardName = "John Doe"
        )

        val result = ValidationUtils.validateAlert(alert)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Guard ID is required"))
    }

    @Test
    fun `validateAlert missing guard name`() {
        val alert = Alert(
            guardId = "guard123",
            guardName = ""
        )

        val result = ValidationUtils.validateAlert(alert)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Guard name is required"))
    }

    @Test
    fun `validateAlert message too long`() {
        val longMessage = "a".repeat(501)
        val alert = Alert(
            guardId = "guard123",
            guardName = "John Doe",
            message = longMessage
        )

        val result = ValidationUtils.validateAlert(alert)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Message cannot exceed 500 characters"))
    }

    @Test
    fun `validateAlert guard id too long`() {
        val longId = "a".repeat(51)
        val alert = Alert(
            guardId = longId,
            guardName = "John Doe"
        )

        val result = ValidationUtils.validateAlert(alert)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Guard ID is too long"))
    }

    @Test
    fun `validateAlert guard name too long`() {
        val longName = "a".repeat(101)
        val alert = Alert(
            guardId = "guard123",
            guardName = longName
        )

        val result = ValidationUtils.validateAlert(alert)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Guard name is too long"))
    }

    @Test
    fun `validateAlert message with suspicious content`() {
        val alert = Alert(
            guardId = "guard123",
            guardName = "John Doe",
            message = "Emergency <script>alert('hack')</script>"
        )

        val result = ValidationUtils.validateAlert(alert)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Message contains invalid content"))
    }

    @Test
    fun `validateUser valid user`() {
        val user = User(
            id = "user123",
            email = "john.doe@campus.edu",
            name = "John Doe",
            role = UserRole.GUARD
        )

        val result = ValidationUtils.validateUser(user)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateUser invalid email format`() {
        val user = User(
            id = "user123",
            email = "invalid-email",
            name = "John Doe"
        )

        val result = ValidationUtils.validateUser(user)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Invalid email format"))
    }

    @Test
    fun `validateUser missing name`() {
        val user = User(
            id = "user123",
            email = "john.doe@campus.edu",
            name = ""
        )

        val result = ValidationUtils.validateUser(user)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Name is required"))
    }

    @Test
    fun `validateUser missing user id`() {
        val user = User(
            id = "",
            email = "john.doe@campus.edu",
            name = "John Doe"
        )

        val result = ValidationUtils.validateUser(user)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("User ID is required"))
    }

    @Test
    fun `validateUser email too long`() {
        val longEmail = "a".repeat(250) + "@example.com"
        val user = User(
            id = "user123",
            email = longEmail,
            name = "John Doe"
        )

        val result = ValidationUtils.validateUser(user)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Email is too long"))
    }

    @Test
    fun `validateUser name with suspicious content`() {
        val user = User(
            id = "user123",
            email = "john.doe@campus.edu",
            name = "John <script>alert('hack')</script> Doe"
        )

        val result = ValidationUtils.validateUser(user)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Name contains invalid characters"))
    }

    @Test
    fun `validateCampusBlock valid block`() {
        val block = CampusBlock(
            id = "block123",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5), // Within campus bounds
            radius = 50.0
        )

        val result = ValidationUtils.validateCampusBlock(block)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateCampusBlock invalid coordinates`() {
        val block = CampusBlock(
            id = "block123",
            name = "Main Building",
            coordinates = GeoPoint(91.0, 181.0), // Invalid lat/lng
            radius = 50.0
        )

        val result = ValidationUtils.validateCampusBlock(block)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Invalid coordinates"))
    }

    @Test
    fun `validateCampusBlock invalid radius`() {
        val block = CampusBlock(
            id = "block123",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5),
            radius = -10.0
        )

        val result = ValidationUtils.validateCampusBlock(block)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Radius must be positive"))
    }

    @Test
    fun `validateCampusBlock radius too large`() {
        val block = CampusBlock(
            id = "block123",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5),
            radius = 1500.0
        )

        val result = ValidationUtils.validateCampusBlock(block)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Radius is too large"))
    }

    @Test
    fun `validatePassword valid password`() {
        val result = ValidationUtils.validatePassword("Password123")
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validatePassword too short`() {
        val result = ValidationUtils.validatePassword("Pass1")
        
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Password must be at least 6 characters"))
    }

    @Test
    fun `validatePassword no number`() {
        val result = ValidationUtils.validatePassword("Password")
        
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Password must contain at least one number"))
    }

    @Test
    fun `validatePassword no letter`() {
        val result = ValidationUtils.validatePassword("123456")
        
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Password must contain at least one letter"))
    }

    @Test
    fun `isValidEmail valid emails`() {
        val validEmails = listOf(
            "test@example.com",
            "user.name@domain.co.uk",
            "user+tag@example.org"
        )

        validEmails.forEach { email ->
            assertTrue("Email $email should be valid", ValidationUtils.isValidEmail(email))
        }
    }

    @Test
    fun `isValidEmail invalid emails`() {
        val invalidEmails = listOf(
            "",
            "invalid-email",
            "@example.com",
            "user@",
            "a".repeat(250) + "@example.com" // Too long
        )

        invalidEmails.forEach { email ->
            assertFalse("Email $email should be invalid", ValidationUtils.isValidEmail(email))
        }
    }

    @Test
    fun `validateAlertStatusTransition valid transitions`() {
        val validTransitions = mapOf(
            AlertStatus.ACTIVE to listOf(AlertStatus.IN_PROGRESS, AlertStatus.CLOSED),
            AlertStatus.IN_PROGRESS to listOf(AlertStatus.RESOLVED, AlertStatus.CLOSED),
            AlertStatus.RESOLVED to listOf(AlertStatus.CLOSED),
            AlertStatus.CLOSED to emptyList<AlertStatus>()
        )

        validTransitions.forEach { (from, toList) ->
            toList.forEach { to ->
                assertTrue(
                    "Transition from $from to $to should be valid",
                    ValidationUtils.isValidStatusTransition(from, to)
                )
            }
        }
    }

    @Test
    fun `validateAlertStatusTransition invalid transitions`() {
        val invalidTransitions = listOf(
            AlertStatus.IN_PROGRESS to AlertStatus.ACTIVE,
            AlertStatus.RESOLVED to AlertStatus.ACTIVE,
            AlertStatus.RESOLVED to AlertStatus.IN_PROGRESS,
            AlertStatus.CLOSED to AlertStatus.ACTIVE,
            AlertStatus.CLOSED to AlertStatus.IN_PROGRESS,
            AlertStatus.CLOSED to AlertStatus.RESOLVED
        )

        invalidTransitions.forEach { (from, to) ->
            assertFalse(
                "Transition from $from to $to should be invalid",
                ValidationUtils.isValidStatusTransition(from, to)
            )
        }
    }

    @Test
    fun `validateFcmToken valid tokens`() {
        val validTokens = listOf(
            "dGVzdF90b2tlbl8xMjM",
            "fcm_token_with_underscores_123",
            "token.with.dots",
            "token-with-dashes",
            "a".repeat(152) // FCM tokens can be up to 152 characters
        )

        validTokens.forEach { token ->
            assertTrue(
                "Token $token should be valid",
                ValidationUtils.isValidFcmToken(token)
            )
        }
    }

    @Test
    fun `validateFcmToken invalid tokens`() {
        val invalidTokens = listOf(
            "",
            "   ",
            "a".repeat(153), // Too long
            "token with spaces",
            "token@with#special!chars",
            null
        )

        invalidTokens.forEach { token ->
            assertFalse(
                "Token $token should be invalid",
                ValidationUtils.isValidFcmToken(token)
            )
        }
    }

    @Test
    fun `isValidCoordinates valid coordinates`() {
        assertTrue(ValidationUtils.isValidCoordinates(GeoPoint(40.5, -74.5)))
        assertTrue(ValidationUtils.isValidCoordinates(GeoPoint(0.0, 0.0)))
        assertTrue(ValidationUtils.isValidCoordinates(GeoPoint(90.0, 180.0)))
        assertTrue(ValidationUtils.isValidCoordinates(GeoPoint(-90.0, -180.0)))
    }

    @Test
    fun `isValidCoordinates invalid coordinates`() {
        assertFalse(ValidationUtils.isValidCoordinates(GeoPoint(91.0, 0.0)))
        assertFalse(ValidationUtils.isValidCoordinates(GeoPoint(-91.0, 0.0)))
        assertFalse(ValidationUtils.isValidCoordinates(GeoPoint(0.0, 181.0)))
        assertFalse(ValidationUtils.isValidCoordinates(GeoPoint(0.0, -181.0)))
    }

    @Test
    fun `sanitizeInput removes dangerous characters`() {
        val input = "  Test <script>alert('hack')</script> message  "
        val sanitized = ValidationUtils.sanitizeInput(input)
        
        assertEquals("Test scriptalert('hack')/script message", sanitized)
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
    }

    @Test
    fun `validateUserInput accepts valid input`() {
        val result = ValidationUtils.validateUserInput("Valid message", 100)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateUserInput rejects input too long`() {
        val longInput = "a".repeat(1001)
        val result = ValidationUtils.validateUserInput(longInput, 1000)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Input exceeds maximum length of 1000 characters"))
    }

    @Test
    fun `validateUserInput rejects malicious content`() {
        val maliciousInput = "Test <script>alert('hack')</script>"
        val result = ValidationUtils.validateUserInput(maliciousInput)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Input contains potentially malicious content"))
    }
}

