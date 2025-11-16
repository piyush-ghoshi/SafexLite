package com.campus.panicbutton.utils

import android.util.Patterns
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.models.User
import com.google.firebase.firestore.GeoPoint

/**
 * Validation result data class
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Utility class for input validation and security checks
 */
object ValidationUtils {
    
    private const val MAX_MESSAGE_LENGTH = 500
    private const val MAX_NAME_LENGTH = 100
    private const val MAX_FCM_TOKEN_LENGTH = 152
    private const val MIN_PASSWORD_LENGTH = 6
    private const val MAX_PASSWORD_LENGTH = 128
    
    /**
     * Validate alert data before creation or update
     */
    fun validateAlert(alert: Alert): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate guard ID
        if (alert.guardId.isBlank()) {
            errors.add("Guard ID is required")
        } else if (alert.guardId.length > 50) {
            errors.add("Guard ID is too long")
        }

        // Validate guard name
        if (alert.guardName.isBlank()) {
            errors.add("Guard name is required")
        } else if (alert.guardName.length > MAX_NAME_LENGTH) {
            errors.add("Guard name is too long")
        }

        // Validate message if provided
        alert.message?.let { message ->
            if (message.length > MAX_MESSAGE_LENGTH) {
                errors.add("Message cannot exceed $MAX_MESSAGE_LENGTH characters")
            }
            // Check for potentially malicious content
            if (containsSuspiciousContent(message)) {
                errors.add("Message contains invalid content")
            }
        }

        // Validate location if provided
        alert.location?.let { location ->
            val locationValidation = validateCampusBlock(location)
            if (!locationValidation.isValid) {
                errors.addAll(locationValidation.errors.map { "Location: $it" })
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    /**
     * Validate user data before creation or update
     */
    fun validateUser(user: User): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate user ID
        if (user.id.isBlank()) {
            errors.add("User ID is required")
        }

        // Validate email
        if (user.email.isBlank()) {
            errors.add("Email is required")
        } else if (!Patterns.EMAIL_ADDRESS.matcher(user.email).matches()) {
            errors.add("Invalid email format")
        } else if (user.email.length > 254) { // RFC 5321 limit
            errors.add("Email is too long")
        }

        // Validate name
        if (user.name.isBlank()) {
            errors.add("Name is required")
        } else if (user.name.length > MAX_NAME_LENGTH) {
            errors.add("Name is too long")
        } else if (containsSuspiciousContent(user.name)) {
            errors.add("Name contains invalid characters")
        }

        // Validate FCM token if provided
        user.fcmToken?.let { token ->
            if (!isValidFcmToken(token)) {
                errors.add("Invalid FCM token")
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    /**
     * Validate campus block data
     */
    fun validateCampusBlock(block: CampusBlock): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate block ID
        if (block.id.isBlank()) {
            errors.add("Block ID is required")
        }

        // Validate name
        if (block.name.isBlank()) {
            errors.add("Block name is required")
        } else if (block.name.length > MAX_NAME_LENGTH) {
            errors.add("Block name is too long")
        }

        // Validate coordinates
        val lat = block.coordinates.latitude
        val lng = block.coordinates.longitude
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            errors.add("Invalid coordinates")
        }

        // Validate radius
        if (block.radius <= 0) {
            errors.add("Radius must be positive")
        } else if (block.radius > 1000) { // Max 1km radius
            errors.add("Radius is too large")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    /**
     * Validate password strength
     */
    fun validatePassword(password: String): ValidationResult {
        val errors = mutableListOf<String>()

        if (password.length < MIN_PASSWORD_LENGTH) {
            errors.add("Password must be at least $MIN_PASSWORD_LENGTH characters")
        }

        if (password.length > MAX_PASSWORD_LENGTH) {
            errors.add("Password is too long")
        }

        if (!password.any { it.isDigit() }) {
            errors.add("Password must contain at least one number")
        }

        if (!password.any { it.isLetter() }) {
            errors.add("Password must contain at least one letter")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    /**
     * Validate email format
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && 
               Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
               email.length <= 254
    }

    /**
     * Validate FCM token format and length
     */
    fun isValidFcmToken(token: String?): Boolean {
        return token != null && 
               token.isNotBlank() && 
               token.length <= MAX_FCM_TOKEN_LENGTH &&
               token.all { it.isLetterOrDigit() || it in "_-." }
    }

    /**
     * Validate alert status transition
     */
    fun isValidStatusTransition(from: AlertStatus, to: AlertStatus): Boolean {
        return when (from) {
            AlertStatus.ACTIVE -> to in listOf(AlertStatus.IN_PROGRESS, AlertStatus.CLOSED)
            AlertStatus.IN_PROGRESS -> to in listOf(AlertStatus.RESOLVED, AlertStatus.CLOSED)
            AlertStatus.RESOLVED -> to == AlertStatus.CLOSED
            AlertStatus.CLOSED -> false // Closed alerts cannot be transitioned
        }
    }

    /**
     * Validate coordinates are within reasonable bounds
     */
    fun isValidCoordinates(geoPoint: GeoPoint): Boolean {
        val lat = geoPoint.latitude
        val lng = geoPoint.longitude
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
    }

    /**
     * Sanitize input string by removing potentially dangerous characters
     */
    fun sanitizeInput(input: String): String {
        return input.trim()
            .replace(Regex("[<>\"'&]"), "") // Remove HTML/XML characters
            .replace(Regex("\\s+"), " ") // Normalize whitespace
    }

    /**
     * Check if text contains suspicious content that might be malicious
     */
    private fun containsSuspiciousContent(text: String): Boolean {
        val suspiciousPatterns = listOf(
            "<script", "</script>", "javascript:", "vbscript:",
            "onload=", "onerror=", "onclick=", "onmouseover=",
            "eval(", "document.cookie", "window.location"
        )
        
        val lowerText = text.lowercase()
        return suspiciousPatterns.any { pattern ->
            lowerText.contains(pattern.lowercase())
        }
    }

    /**
     * Validate user input for potential injection attacks
     */
    fun validateUserInput(input: String, maxLength: Int = 1000): ValidationResult {
        val errors = mutableListOf<String>()

        if (input.length > maxLength) {
            errors.add("Input exceeds maximum length of $maxLength characters")
        }

        if (containsSuspiciousContent(input)) {
            errors.add("Input contains potentially malicious content")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}