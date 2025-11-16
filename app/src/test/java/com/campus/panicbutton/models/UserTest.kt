package com.campus.panicbutton.models

import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*
import java.util.Date

class UserTest {

    @Test
    fun `user creation with default values`() {
        val user = User()
        
        assertEquals("", user.id)
        assertEquals("", user.email)
        assertEquals("", user.name)
        assertEquals(UserRole.GUARD, user.role)
        assertTrue(user.isActive)
        assertNull(user.fcmToken)
        assertNull(user.lastSeen)
    }

    @Test
    fun `user creation with all parameters`() {
        val lastSeen = Timestamp(Date())
        
        val user = User(
            id = "user123",
            email = "john.doe@campus.edu",
            name = "John Doe",
            role = UserRole.ADMIN,
            isActive = false,
            fcmToken = "fcm_token_123",
            lastSeen = lastSeen
        )

        assertEquals("user123", user.id)
        assertEquals("john.doe@campus.edu", user.email)
        assertEquals("John Doe", user.name)
        assertEquals(UserRole.ADMIN, user.role)
        assertFalse(user.isActive)
        assertEquals("fcm_token_123", user.fcmToken)
        assertEquals(lastSeen, user.lastSeen)
    }

    @Test
    fun `user role enum values`() {
        assertEquals(2, UserRole.values().size)
        assertTrue(UserRole.values().contains(UserRole.GUARD))
        assertTrue(UserRole.values().contains(UserRole.ADMIN))
    }

    @Test
    fun `user email validation format`() {
        val validEmails = listOf(
            "user@campus.edu",
            "john.doe@university.org",
            "admin@school.com"
        )
        
        val invalidEmails = listOf(
            "",
            "invalid-email",
            "@campus.edu",
            "user@",
            "user.campus.edu"
        )

        validEmails.forEach { email ->
            assertTrue("$email should be valid", isValidEmail(email))
        }

        invalidEmails.forEach { email ->
            assertFalse("$email should be invalid", isValidEmail(email))
        }
    }

    @Test
    fun `user active status validation`() {
        val activeUser = User(isActive = true)
        val inactiveUser = User(isActive = false)
        
        assertTrue(activeUser.isActive)
        assertFalse(inactiveUser.isActive)
    }

    @Test
    fun `fcm token validation - optional field`() {
        val userWithToken = User(fcmToken = "valid_token_123")
        val userWithoutToken = User(fcmToken = null)
        
        assertEquals("valid_token_123", userWithToken.fcmToken)
        assertNull(userWithoutToken.fcmToken)
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}