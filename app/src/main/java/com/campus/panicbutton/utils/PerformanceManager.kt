package com.campus.panicbutton.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Performance manager for optimizing app performance and battery usage
 * Handles memory management, Firestore optimization, and lifecycle management
 */
class PerformanceManager private constructor(private val context: Context) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "PerformanceManager"
        private const val MEMORY_THRESHOLD_MB = 50 // Trigger cleanup at 50MB
        private const val GC_INTERVAL_MS = 30000L // 30 seconds
        
        @Volatile
        private var INSTANCE: PerformanceManager? = null
        
        fun getInstance(context: Context): PerformanceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceManager(context.applicationContext).also { 
                    INSTANCE = it
                    ProcessLifecycleOwner.get().lifecycle.addObserver(it)
                }
            }
        }
    }
    
    // Track active references to prevent memory leaks
    private val activeReferences = ConcurrentHashMap<String, WeakReference<Any>>()
    private var lastGcTime = 0L
    private var isOptimized = false
    
    init {
        optimizeFirestore()
        Log.d(TAG, "PerformanceManager initialized")
    }
    
    /**
     * Optimize Firestore settings for better performance
     */
    private fun optimizeFirestore() {
        if (!isOptimized) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true) // Enable offline persistence
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Unlimited cache
                    .build()
                
                firestore.firestoreSettings = settings
                isOptimized = true
                Log.d(TAG, "Firestore optimized for performance")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to optimize Firestore", e)
            }
        }
    }
    
    /**
     * Register a reference for memory leak tracking
     */
    fun registerReference(key: String, obj: Any) {
        activeReferences[key] = WeakReference(obj)
        checkMemoryUsage()
    }
    
    /**
     * Unregister a reference
     */
    fun unregisterReference(key: String) {
        activeReferences.remove(key)
    }
    
    /**
     * Check current memory usage and trigger cleanup if needed
     */
    fun checkMemoryUsage() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGcTime > GC_INTERVAL_MS) {
            val memoryInfo = getMemoryInfo()
            if (memoryInfo.usedMemoryMB > MEMORY_THRESHOLD_MB) {
                performMemoryCleanup()
                lastGcTime = currentTime
            }
        }
    }
    
    /**
     * Get current memory usage information
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        return MemoryInfo(
            usedMemoryMB = (usedMemory / 1024 / 1024).toInt(),
            maxMemoryMB = (maxMemory / 1024 / 1024).toInt(),
            availableMemoryMB = (availableMemory / 1024 / 1024).toInt(),
            usedPercentage = ((usedMemory.toFloat() / maxMemory) * 100).toInt()
        )
    }
    
    /**
     * Perform memory cleanup operations
     */
    private fun performMemoryCleanup() {
        Log.d(TAG, "Performing memory cleanup")
        
        // Clean up null references
        val iterator = activeReferences.iterator()
        var cleanedCount = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get() == null) {
                iterator.remove()
                cleanedCount++
            }
        }
        
        // Suggest garbage collection
        System.gc()
        
        Log.d(TAG, "Memory cleanup completed. Cleaned $cleanedCount null references")
    }
    
    /**
     * Force memory cleanup (use sparingly)
     */
    fun forceMemoryCleanup() {
        performMemoryCleanup()
        lastGcTime = System.currentTimeMillis()
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): PerformanceStats {
        val memoryInfo = getMemoryInfo()
        val activeRefCount = activeReferences.size
        
        return PerformanceStats(
            memoryInfo = memoryInfo,
            activeReferences = activeRefCount,
            isFirestoreOptimized = isOptimized,
            lastCleanupTime = lastGcTime
        )
    }
    
    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "App moved to foreground")
        checkMemoryUsage()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "App moved to background")
        performMemoryCleanup()
    }
    
    /**
     * Data class for memory information
     */
    data class MemoryInfo(
        val usedMemoryMB: Int,
        val maxMemoryMB: Int,
        val availableMemoryMB: Int,
        val usedPercentage: Int
    )
    
    /**
     * Data class for performance statistics
     */
    data class PerformanceStats(
        val memoryInfo: MemoryInfo,
        val activeReferences: Int,
        val isFirestoreOptimized: Boolean,
        val lastCleanupTime: Long
    )
}