package com.campus.panicbutton.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Centralized error handling utility for the Campus Panic Button app
 * Provides user-friendly error messages and categorizes errors for appropriate handling
 */
class ErrorHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "ErrorHandler"
    }
    
    /**
     * Error categories for different types of errors
     */
    enum class ErrorCategory {
        NETWORK,
        AUTHENTICATION,
        PERMISSION,
        FIREBASE,
        LOCATION,
        VALIDATION,
        UNKNOWN
    }
    
    /**
     * Data class representing a handled error with user-friendly information
     */
    data class HandledError(
        val category: ErrorCategory,
        val userMessage: String,
        val technicalMessage: String,
        val isRetryable: Boolean,
        val requiresAction: Boolean = false,
        val actionText: String? = null
    )
    
    /**
     * Handle any exception and return user-friendly error information
     */
    fun handleError(exception: Throwable, context: String = ""): HandledError {
        Log.e(TAG, "Handling error in context: $context", exception)
        
        return when (exception) {
            // Network-related errors
            is UnknownHostException -> HandledError(
                category = ErrorCategory.NETWORK,
                userMessage = "No internet connection. Please check your network settings.",
                technicalMessage = "DNS resolution failed: ${exception.message}",
                isRetryable = true,
                requiresAction = true,
                actionText = "Check Connection"
            )
            
            is ConnectException -> HandledError(
                category = ErrorCategory.NETWORK,
                userMessage = "Unable to connect to server. Please try again.",
                technicalMessage = "Connection failed: ${exception.message}",
                isRetryable = true
            )
            
            is SocketTimeoutException -> HandledError(
                category = ErrorCategory.NETWORK,
                userMessage = "Connection timed out. Please check your internet connection.",
                technicalMessage = "Socket timeout: ${exception.message}",
                isRetryable = true
            )
            
            is TimeoutException -> HandledError(
                category = ErrorCategory.NETWORK,
                userMessage = "Request timed out. Please try again.",
                technicalMessage = "Operation timeout: ${exception.message}",
                isRetryable = true
            )
            
            // Firebase-specific errors
            is FirebaseNetworkException -> HandledError(
                category = ErrorCategory.NETWORK,
                userMessage = "Network error occurred. Please check your connection and try again.",
                technicalMessage = "Firebase network error: ${exception.message}",
                isRetryable = true
            )
            
            is FirebaseTooManyRequestsException -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Too many requests. Please wait a moment and try again.",
                technicalMessage = "Firebase rate limit exceeded: ${exception.message}",
                isRetryable = true
            )
            
            is FirebaseAuthException -> handleAuthError(exception)
            
            is FirebaseFirestoreException -> handleFirestoreError(exception)
            
            is FirebaseException -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Service temporarily unavailable. Please try again.",
                technicalMessage = "Firebase error: ${exception.message}",
                isRetryable = true
            )
            
            // Location-related errors
            is SecurityException -> {
                if (exception.message?.contains("location", ignoreCase = true) == true) {
                    HandledError(
                        category = ErrorCategory.PERMISSION,
                        userMessage = "Location permission is required for emergency alerts.",
                        technicalMessage = "Location permission denied: ${exception.message}",
                        isRetryable = false,
                        requiresAction = true,
                        actionText = "Grant Permission"
                    )
                } else {
                    HandledError(
                        category = ErrorCategory.PERMISSION,
                        userMessage = "Permission required to continue.",
                        technicalMessage = "Security exception: ${exception.message}",
                        isRetryable = false,
                        requiresAction = true,
                        actionText = "Grant Permission"
                    )
                }
            }
            
            // Validation errors
            is IllegalArgumentException -> HandledError(
                category = ErrorCategory.VALIDATION,
                userMessage = exception.message ?: "Invalid input provided.",
                technicalMessage = "Validation error: ${exception.message}",
                isRetryable = false
            )
            
            is IllegalStateException -> {
                val message = exception.message ?: ""
                if (message.contains("cooldown", ignoreCase = true)) {
                    HandledError(
                        category = ErrorCategory.VALIDATION,
                        userMessage = message,
                        technicalMessage = "Cooldown violation: $message",
                        isRetryable = true
                    )
                } else {
                    HandledError(
                        category = ErrorCategory.VALIDATION,
                        userMessage = "Operation not allowed at this time.",
                        technicalMessage = "State error: $message",
                        isRetryable = false
                    )
                }
            }
            
            // Generic errors
            else -> HandledError(
                category = ErrorCategory.UNKNOWN,
                userMessage = "An unexpected error occurred. Please try again.",
                technicalMessage = "Unknown error: ${exception.javaClass.simpleName} - ${exception.message}",
                isRetryable = true
            )
        }
    }
    
    /**
     * Handle Firebase Authentication specific errors
     */
    private fun handleAuthError(exception: FirebaseAuthException): HandledError {
        val errorCode = exception.errorCode
        
        return when (errorCode) {
            "ERROR_INVALID_EMAIL" -> HandledError(
                category = ErrorCategory.AUTHENTICATION,
                userMessage = "Please enter a valid email address.",
                technicalMessage = "Invalid email format: ${exception.message}",
                isRetryable = false
            )
            
            "ERROR_WRONG_PASSWORD" -> HandledError(
                category = ErrorCategory.AUTHENTICATION,
                userMessage = "Incorrect password. Please try again.",
                technicalMessage = "Wrong password: ${exception.message}",
                isRetryable = false
            )
            
            "ERROR_USER_NOT_FOUND" -> HandledError(
                category = ErrorCategory.AUTHENTICATION,
                userMessage = "No account found with this email address.",
                technicalMessage = "User not found: ${exception.message}",
                isRetryable = false
            )
            
            "ERROR_USER_DISABLED" -> HandledError(
                category = ErrorCategory.AUTHENTICATION,
                userMessage = "This account has been disabled. Please contact administrator.",
                technicalMessage = "User account disabled: ${exception.message}",
                isRetryable = false,
                requiresAction = true,
                actionText = "Contact Admin"
            )
            
            "ERROR_TOO_MANY_REQUESTS" -> HandledError(
                category = ErrorCategory.AUTHENTICATION,
                userMessage = "Too many failed attempts. Please try again later.",
                technicalMessage = "Too many auth requests: ${exception.message}",
                isRetryable = true
            )
            
            "ERROR_NETWORK_REQUEST_FAILED" -> HandledError(
                category = ErrorCategory.NETWORK,
                userMessage = "Network error. Please check your connection and try again.",
                technicalMessage = "Auth network error: ${exception.message}",
                isRetryable = true
            )
            
            else -> HandledError(
                category = ErrorCategory.AUTHENTICATION,
                userMessage = "Authentication failed. Please try again.",
                technicalMessage = "Auth error ($errorCode): ${exception.message}",
                isRetryable = true
            )
        }
    }
    
    /**
     * Handle Firebase Firestore specific errors
     */
    private fun handleFirestoreError(exception: FirebaseFirestoreException): HandledError {
        val code = exception.code
        
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Access denied. Please contact administrator.",
                technicalMessage = "Firestore permission denied: ${exception.message}",
                isRetryable = false,
                requiresAction = true,
                actionText = "Contact Admin"
            )
            
            FirebaseFirestoreException.Code.NOT_FOUND -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Requested data not found.",
                technicalMessage = "Firestore document not found: ${exception.message}",
                isRetryable = false
            )
            
            FirebaseFirestoreException.Code.ALREADY_EXISTS -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Data already exists.",
                technicalMessage = "Firestore document exists: ${exception.message}",
                isRetryable = false
            )
            
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Service temporarily overloaded. Please try again in a moment.",
                technicalMessage = "Firestore quota exceeded: ${exception.message}",
                isRetryable = true
            )
            
            FirebaseFirestoreException.Code.UNAVAILABLE -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Service temporarily unavailable. Please try again.",
                technicalMessage = "Firestore unavailable: ${exception.message}",
                isRetryable = true
            )
            
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Request timed out. Please try again.",
                technicalMessage = "Firestore deadline exceeded: ${exception.message}",
                isRetryable = true
            )
            
            else -> HandledError(
                category = ErrorCategory.FIREBASE,
                userMessage = "Database error occurred. Please try again.",
                technicalMessage = "Firestore error (${code.name}): ${exception.message}",
                isRetryable = true
            )
        }
    }
    
    /**
     * Get a short error message suitable for toasts or snackbars
     */
    fun getShortErrorMessage(exception: Throwable): String {
        val handled = handleError(exception)
        return when (handled.category) {
            ErrorCategory.NETWORK -> "Connection error"
            ErrorCategory.AUTHENTICATION -> "Login failed"
            ErrorCategory.PERMISSION -> "Permission required"
            ErrorCategory.FIREBASE -> "Service error"
            ErrorCategory.LOCATION -> "Location error"
            ErrorCategory.VALIDATION -> "Invalid input"
            ErrorCategory.UNKNOWN -> "Error occurred"
        }
    }
    
    /**
     * Check if an error should trigger offline mode
     */
    fun shouldTriggerOfflineMode(exception: Throwable): Boolean {
        return when (exception) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is FirebaseNetworkException -> true
            is FirebaseFirestoreException -> 
                exception.code == FirebaseFirestoreException.Code.UNAVAILABLE
            else -> false
        }
    }
    
    /**
     * Get retry delay in milliseconds based on error type
     */
    fun getRetryDelay(exception: Throwable, attemptNumber: Int): Long {
        val baseDelay = when (exception) {
            is FirebaseTooManyRequestsException -> 5000L // 5 seconds
            is SocketTimeoutException -> 2000L // 2 seconds
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> 10000L // 10 seconds
                    FirebaseFirestoreException.Code.UNAVAILABLE -> 3000L // 3 seconds
                    else -> 1000L // 1 second
                }
            }
            else -> 1000L // 1 second default
        }
        
        // Exponential backoff with jitter
        val exponentialDelay = baseDelay * (1L shl (attemptNumber - 1))
        val jitter = (Math.random() * 1000).toLong()
        return minOf(exponentialDelay + jitter, 30000L) // Max 30 seconds
    }
}