package com.campus.panicbutton.utils

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Utility class for implementing retry logic with exponential backoff
 * Supports both Firebase Tasks and suspend functions
 */
class RetryManager {
    
    companion object {
        private const val TAG = "RetryManager"
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
    }
    
    /**
     * Retry configuration
     */
    data class RetryConfig(
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        val maxDelayMs: Long = MAX_DELAY_MS,
        val shouldRetry: (Throwable) -> Boolean = { true }
    )
    
    /**
     * Retry a Firebase Task operation with exponential backoff
     */
    fun <T> retryTask(
        operation: () -> Task<T>,
        config: RetryConfig = RetryConfig(),
        operationName: String = "operation"
    ): Task<T> {
        return retryTaskInternal(operation, config, operationName, 1)
    }
    
    private fun <T> retryTaskInternal(
        operation: () -> Task<T>,
        config: RetryConfig,
        operationName: String,
        attempt: Int
    ): Task<T> {
        Log.d(TAG, "Attempting $operationName (attempt $attempt/${config.maxRetries})")
        
        return operation().continueWithTask { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "$operationName succeeded on attempt $attempt")
                Tasks.forResult(task.result)
            } else {
                val exception = task.exception ?: Exception("Unknown error")
                
                if (attempt >= config.maxRetries || !config.shouldRetry(exception)) {
                    Log.e(TAG, "$operationName failed after $attempt attempts", exception)
                    Tasks.forException(exception)
                } else {
                    val delay = calculateDelay(config.baseDelayMs, attempt, config.maxDelayMs)
                    Log.w(TAG, "$operationName failed on attempt $attempt, retrying in ${delay}ms", exception)
                    
                    // Create a delayed retry task
                    val delayedTask = Tasks.forResult(Unit).continueWithTask {
                        Thread.sleep(delay)
                        retryTaskInternal(operation, config, operationName, attempt + 1)
                    }
                    delayedTask
                }
            }
        }
    }
    
    /**
     * Retry a suspend function with exponential backoff
     */
    suspend fun <T> retrySuspend(
        operation: suspend () -> T,
        config: RetryConfig = RetryConfig(),
        operationName: String = "operation"
    ): T {
        var lastException: Throwable? = null
        
        repeat(config.maxRetries) { attempt ->
            try {
                Log.d(TAG, "Attempting $operationName (attempt ${attempt + 1}/${config.maxRetries})")
                val result = operation()
                Log.d(TAG, "$operationName succeeded on attempt ${attempt + 1}")
                return result
            } catch (exception: Throwable) {
                lastException = exception
                
                if (attempt + 1 >= config.maxRetries || !config.shouldRetry(exception)) {
                    Log.e(TAG, "$operationName failed after ${attempt + 1} attempts", exception)
                    throw exception
                } else {
                    val delay = calculateDelay(config.baseDelayMs, attempt + 1, config.maxDelayMs)
                    Log.w(TAG, "$operationName failed on attempt ${attempt + 1}, retrying in ${delay}ms", exception)
                    delay(delay)
                }
            }
        }
        
        throw lastException ?: Exception("Retry failed with unknown error")
    }
    
    /**
     * Convert a Firebase Task to a suspend function for use with retrySuspend
     */
    suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: Exception("Task failed"))
            }
        }
        
        continuation.invokeOnCancellation {
            // Cancel the task if possible (Firebase tasks don't support cancellation directly)
            Log.d(TAG, "Task operation was cancelled")
        }
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     */
    private fun calculateDelay(baseDelayMs: Long, attempt: Int, maxDelayMs: Long): Long {
        val exponentialDelay = baseDelayMs * (1L shl (attempt - 1))
        val jitter = (Math.random() * baseDelayMs * 0.1).toLong()
        return minOf(exponentialDelay + jitter, maxDelayMs)
    }
    
    /**
     * Create a retry configuration for network-related operations
     */
    fun networkRetryConfig(): RetryConfig {
        return RetryConfig(
            maxRetries = 3,
            baseDelayMs = 2000L,
            maxDelayMs = 10000L,
            shouldRetry = { exception ->
                ErrorHandler.isNetworkError(exception)
            }
        )
    }
    
    /**
     * Create a retry configuration for Firebase operations
     */
    fun firebaseRetryConfig(): RetryConfig {
        return RetryConfig(
            maxRetries = 3,
            baseDelayMs = 1000L,
            maxDelayMs = 15000L,
            shouldRetry = { exception ->
                ErrorHandler.isRetryableFirebaseError(exception)
            }
        )
    }
    
    /**
     * Create a retry configuration for location operations
     */
    fun locationRetryConfig(): RetryConfig {
        return RetryConfig(
            maxRetries = 2,
            baseDelayMs = 3000L,
            maxDelayMs = 8000L,
            shouldRetry = { exception ->
                // Retry location operations except for permission errors
                !(exception is SecurityException)
            }
        )
    }
}

/**
 * Extension functions for ErrorHandler to support RetryManager
 */
private object ErrorHandler {
    
    fun isNetworkError(exception: Throwable): Boolean {
        return when (exception) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.util.concurrent.TimeoutException -> true
            is com.google.firebase.FirebaseNetworkException -> true
            is com.google.firebase.firestore.FirebaseFirestoreException -> 
                exception.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE
            else -> false
        }
    }
    
    fun isRetryableFirebaseError(exception: Throwable): Boolean {
        return when (exception) {
            is com.google.firebase.FirebaseNetworkException -> true
            is com.google.firebase.FirebaseTooManyRequestsException -> true
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (exception.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> true
                    else -> false
                }
            }
            else -> isNetworkError(exception)
        }
    }
}