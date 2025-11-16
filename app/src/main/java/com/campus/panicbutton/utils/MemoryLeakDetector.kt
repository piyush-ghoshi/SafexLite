package com.campus.panicbutton.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory leak detector for identifying and preventing memory leaks
 * Tracks activities, fragments, and other objects for potential leaks
 */
object MemoryLeakDetector {
    
    private const val TAG = "MemoryLeakDetector"
    private const val LEAK_CHECK_DELAY_MS = 5000L // 5 seconds after object should be GC'd
    private const val MAX_TRACKED_OBJECTS = 100
    
    // Track objects that should be garbage collected
    private val trackedObjects = ConcurrentHashMap<String, TrackedObject>()
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Data class for tracked objects
     */
    private data class TrackedObject(
        val weakRef: WeakReference<Any>,
        val className: String,
        val trackingTime: Long,
        val expectedGcTime: Long
    )
    
    /**
     * Leak detection result
     */
    data class LeakDetectionResult(
        val hasLeaks: Boolean,
        val leakedObjects: List<LeakInfo>,
        val totalTrackedObjects: Int
    )
    
    /**
     * Information about a leaked object
     */
    data class LeakInfo(
        val className: String,
        val trackingDuration: Long,
        val expectedGcTime: Long
    )
    
    /**
     * Track an activity for potential memory leaks
     */
    fun trackActivity(activity: Activity) {
        val key = "${activity::class.java.simpleName}_${System.identityHashCode(activity)}"
        trackObject(key, activity, activity::class.java.simpleName)
        Log.d(TAG, "Tracking activity: ${activity::class.java.simpleName}")
    }
    
    /**
     * Track a fragment for potential memory leaks
     */
    fun trackFragment(fragment: Fragment) {
        val key = "${fragment::class.java.simpleName}_${System.identityHashCode(fragment)}"
        trackObject(key, fragment, fragment::class.java.simpleName)
        Log.d(TAG, "Tracking fragment: ${fragment::class.java.simpleName}")
    }
    
    /**
     * Track any object for potential memory leaks
     */
    fun trackObject(key: String, obj: Any, className: String = obj::class.java.simpleName) {
        // Clean up old entries if we're tracking too many objects
        if (trackedObjects.size >= MAX_TRACKED_OBJECTS) {
            cleanupOldEntries()
        }
        
        val currentTime = System.currentTimeMillis()
        val trackedObject = TrackedObject(
            weakRef = WeakReference(obj),
            className = className,
            trackingTime = currentTime,
            expectedGcTime = currentTime + LEAK_CHECK_DELAY_MS
        )
        
        trackedObjects[key] = trackedObject
        
        // Schedule leak check
        handler.postDelayed({
            checkForLeak(key)
        }, LEAK_CHECK_DELAY_MS)
        
        Log.d(TAG, "Tracking object: $className (key: $key)")
    }
    
    /**
     * Stop tracking an object (call when object is properly cleaned up)
     */
    fun stopTracking(key: String) {
        trackedObjects.remove(key)?.let {
            Log.d(TAG, "Stopped tracking: ${it.className} (key: $key)")
        }
    }
    
    /**
     * Stop tracking an object by instance
     */
    fun stopTracking(obj: Any) {
        val key = findKeyForObject(obj)
        if (key != null) {
            stopTracking(key)
        }
    }
    
    /**
     * Check for a specific leak
     */
    private fun checkForLeak(key: String) {
        val trackedObject = trackedObjects[key] ?: return
        
        // Force garbage collection to ensure weak references are cleared
        System.gc()
        
        // Wait a bit for GC to complete
        handler.postDelayed({
            val obj = trackedObject.weakRef.get()
            if (obj != null) {
                // Object still exists - potential memory leak
                val duration = System.currentTimeMillis() - trackedObject.trackingTime
                Log.w(TAG, "Potential memory leak detected: ${trackedObject.className} " +
                        "(tracked for ${duration}ms)")
                
                // Keep tracking for a bit longer in case it's just a delayed cleanup
                if (duration < LEAK_CHECK_DELAY_MS * 3) {
                    handler.postDelayed({
                        checkForLeak(key)
                    }, LEAK_CHECK_DELAY_MS)
                } else {
                    Log.e(TAG, "Confirmed memory leak: ${trackedObject.className} " +
                            "(tracked for ${duration}ms)")
                }
            } else {
                // Object was garbage collected - no leak
                trackedObjects.remove(key)
                Log.d(TAG, "Object properly garbage collected: ${trackedObject.className}")
            }
        }, 100) // Small delay to allow GC to complete
    }
    
    /**
     * Perform comprehensive leak detection
     */
    fun detectLeaks(): LeakDetectionResult {
        // Force garbage collection
        System.gc()
        
        val currentTime = System.currentTimeMillis()
        val leakedObjects = mutableListOf<LeakInfo>()
        
        // Check all tracked objects
        val iterator = trackedObjects.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val trackedObject = entry.value
            
            if (currentTime > trackedObject.expectedGcTime) {
                val obj = trackedObject.weakRef.get()
                if (obj != null) {
                    // Object still exists after expected GC time - potential leak
                    val duration = currentTime - trackedObject.trackingTime
                    leakedObjects.add(
                        LeakInfo(
                            className = trackedObject.className,
                            trackingDuration = duration,
                            expectedGcTime = trackedObject.expectedGcTime
                        )
                    )
                } else {
                    // Object was garbage collected
                    iterator.remove()
                }
            }
        }
        
        val hasLeaks = leakedObjects.isNotEmpty()
        if (hasLeaks) {
            Log.w(TAG, "Memory leak detection found ${leakedObjects.size} potential leaks")
        } else {
            Log.d(TAG, "No memory leaks detected")
        }
        
        return LeakDetectionResult(
            hasLeaks = hasLeaks,
            leakedObjects = leakedObjects,
            totalTrackedObjects = trackedObjects.size
        )
    }
    
    /**
     * Clean up old tracking entries
     */
    private fun cleanupOldEntries() {
        val currentTime = System.currentTimeMillis()
        val iterator = trackedObjects.iterator()
        var cleanedCount = 0
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val trackedObject = entry.value
            
            // Remove entries older than 5 minutes or with null references
            if (currentTime - trackedObject.trackingTime > 300000L || 
                trackedObject.weakRef.get() == null) {
                iterator.remove()
                cleanedCount++
            }
        }
        
        Log.d(TAG, "Cleaned up $cleanedCount old tracking entries")
    }
    
    /**
     * Find tracking key for an object
     */
    private fun findKeyForObject(obj: Any): String? {
        trackedObjects.forEach { (key, trackedObject) ->
            if (trackedObject.weakRef.get() === obj) {
                return key
            }
        }
        return null
    }
    
    /**
     * Get memory leak statistics
     */
    fun getLeakStats(): LeakStats {
        val currentTime = System.currentTimeMillis()
        var potentialLeaks = 0
        var confirmedLeaks = 0
        
        trackedObjects.values.forEach { trackedObject ->
            val duration = currentTime - trackedObject.trackingTime
            if (duration > LEAK_CHECK_DELAY_MS) {
                if (trackedObject.weakRef.get() != null) {
                    if (duration > LEAK_CHECK_DELAY_MS * 3) {
                        confirmedLeaks++
                    } else {
                        potentialLeaks++
                    }
                }
            }
        }
        
        return LeakStats(
            totalTrackedObjects = trackedObjects.size,
            potentialLeaks = potentialLeaks,
            confirmedLeaks = confirmedLeaks
        )
    }
    
    /**
     * Clear all tracking (use with caution)
     */
    fun clearAllTracking() {
        val count = trackedObjects.size
        trackedObjects.clear()
        Log.d(TAG, "Cleared all tracking ($count objects)")
    }
    
    /**
     * Enable automatic activity tracking
     */
    fun enableAutoActivityTracking(context: Context) {
        if (context is Activity) {
            trackActivity(context)
        }
    }
    
    /**
     * Data class for leak statistics
     */
    data class LeakStats(
        val totalTrackedObjects: Int,
        val potentialLeaks: Int,
        val confirmedLeaks: Int
    )
}