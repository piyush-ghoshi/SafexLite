package com.campus.panicbutton.utils

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.firestore.ListenerRegistration
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Lifecycle manager for proper resource management and memory leak prevention
 * Handles activity lifecycle, Firestore listeners, and background/foreground transitions
 */
class LifecycleManager private constructor() : Application.ActivityLifecycleCallbacks, 
    DefaultLifecycleObserver, ComponentCallbacks2 {
    
    companion object {
        private const val TAG = "LifecycleManager"
        
        @Volatile
        private var INSTANCE: LifecycleManager? = null
        
        fun getInstance(): LifecycleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LifecycleManager().also { INSTANCE = it }
            }
        }
    }
    
    // Track active activities
    private val activeActivities = ConcurrentHashMap<String, WeakReference<Activity>>()
    private var activityCount = 0
    private var isAppInForeground = false
    
    // Track Firestore listeners for cleanup
    private val firestoreListeners = ConcurrentHashMap<String, ListenerRegistration>()
    
    // Track lifecycle observers
    private val lifecycleObservers = ConcurrentHashMap<String, LifecycleObserver>()
    
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d(TAG, "LifecycleManager initialized")
    }
    
    /**
     * Initialize with application context
     */
    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        application.registerComponentCallbacks(this)
        Log.d(TAG, "LifecycleManager registered with application")
    }
    
    // Activity Lifecycle Callbacks
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val activityName = activity::class.java.simpleName
        activeActivities[activityName] = WeakReference(activity)
        Log.d(TAG, "Activity created: $activityName")
    }
    
    override fun onActivityStarted(activity: Activity) {
        activityCount++
        val activityName = activity::class.java.simpleName
        Log.d(TAG, "Activity started: $activityName (count: $activityCount)")
    }
    
    override fun onActivityResumed(activity: Activity) {
        val activityName = activity::class.java.simpleName
        Log.d(TAG, "Activity resumed: $activityName")
        
        // Notify observers
        notifyActivityResumed(activityName)
    }
    
    override fun onActivityPaused(activity: Activity) {
        val activityName = activity::class.java.simpleName
        Log.d(TAG, "Activity paused: $activityName")
        
        // Notify observers
        notifyActivityPaused(activityName)
    }
    
    override fun onActivityStopped(activity: Activity) {
        activityCount--
        val activityName = activity::class.java.simpleName
        Log.d(TAG, "Activity stopped: $activityName (count: $activityCount)")
        
        if (activityCount == 0) {
            onAppMovedToBackground()
        }
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No action needed
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        val activityName = activity::class.java.simpleName
        activeActivities.remove(activityName)
        
        // Clean up any listeners associated with this activity
        cleanupActivityListeners(activityName)
        
        Log.d(TAG, "Activity destroyed: $activityName")
    }
    
    // Process Lifecycle Observer
    
    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        Log.d(TAG, "App moved to foreground")
        
        // Notify observers
        notifyAppForeground()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        Log.d(TAG, "App moved to background")
        
        // Perform background cleanup
        onAppMovedToBackground()
        
        // Notify observers
        notifyAppBackground()
    }
    
    // Component Callbacks for memory management
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "Configuration changed")
    }
    
    override fun onLowMemory() {
        Log.w(TAG, "Low memory warning received")
        performLowMemoryCleanup()
    }
    
    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "Trim memory requested: level $level")
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // App UI is hidden, perform moderate cleanup
                performModerateCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                // App is in background, perform aggressive cleanup
                performAggressiveCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // System is low on memory, perform complete cleanup
                performCompleteCleanup()
            }
        }
    }
    
    // Firestore Listener Management
    
    /**
     * Register a Firestore listener for automatic cleanup
     */
    fun registerFirestoreListener(key: String, listener: ListenerRegistration) {
        firestoreListeners[key] = listener
        Log.d(TAG, "Firestore listener registered: $key")
    }
    
    /**
     * Unregister a Firestore listener
     */
    fun unregisterFirestoreListener(key: String) {
        firestoreListeners.remove(key)?.remove()
        Log.d(TAG, "Firestore listener unregistered: $key")
    }
    
    /**
     * Clean up listeners for a specific activity
     */
    private fun cleanupActivityListeners(activityName: String) {
        val listenersToRemove = firestoreListeners.keys.filter { it.startsWith(activityName) }
        listenersToRemove.forEach { key ->
            unregisterFirestoreListener(key)
        }
        Log.d(TAG, "Cleaned up ${listenersToRemove.size} listeners for $activityName")
    }
    
    /**
     * Clean up all Firestore listeners
     */
    private fun cleanupAllListeners() {
        val listenerCount = firestoreListeners.size
        firestoreListeners.values.forEach { it.remove() }
        firestoreListeners.clear()
        Log.d(TAG, "Cleaned up $listenerCount Firestore listeners")
    }
    
    // Lifecycle Observer Management
    
    /**
     * Register a lifecycle observer
     */
    fun registerLifecycleObserver(key: String, observer: LifecycleObserver) {
        lifecycleObservers[key] = observer
        Log.d(TAG, "Lifecycle observer registered: $key")
    }
    
    /**
     * Unregister a lifecycle observer
     */
    fun unregisterLifecycleObserver(key: String) {
        lifecycleObservers.remove(key)
        Log.d(TAG, "Lifecycle observer unregistered: $key")
    }
    
    // Cleanup Methods
    
    private fun onAppMovedToBackground() {
        Log.d(TAG, "Performing background cleanup")
        
        // Stop location updates to save battery
        BatteryOptimizer.getInstance(getApplicationContext()).stopLocationUpdates()
        
        // Perform memory cleanup
        PerformanceManager.getInstance(getApplicationContext()).forceMemoryCleanup()
        
        // Clean up image cache
        ImageCompressionUtils.cleanupCache(getApplicationContext())
    }
    
    private fun performLowMemoryCleanup() {
        Log.d(TAG, "Performing low memory cleanup")
        
        // Clear location cache
        BatteryOptimizer.getInstance(getApplicationContext()).clearCache()
        
        // Force garbage collection
        PerformanceManager.getInstance(getApplicationContext()).forceMemoryCleanup()
        
        // Clean up image cache
        ImageCompressionUtils.cleanupCache(getApplicationContext())
    }
    
    private fun performModerateCleanup() {
        Log.d(TAG, "Performing moderate cleanup")
        performLowMemoryCleanup()
    }
    
    private fun performAggressiveCleanup() {
        Log.d(TAG, "Performing aggressive cleanup")
        performLowMemoryCleanup()
        
        // Remove non-essential Firestore listeners
        val nonEssentialListeners = firestoreListeners.keys.filter { 
            !it.contains("essential") && !it.contains("critical") 
        }
        nonEssentialListeners.forEach { key ->
            unregisterFirestoreListener(key)
        }
    }
    
    private fun performCompleteCleanup() {
        Log.d(TAG, "Performing complete cleanup")
        performAggressiveCleanup()
        
        // Clean up all listeners except critical ones
        val criticalListeners = firestoreListeners.keys.filter { it.contains("critical") }
        val listenersToRemove = firestoreListeners.keys - criticalListeners.toSet()
        listenersToRemove.forEach { key ->
            unregisterFirestoreListener(key)
        }
    }
    
    // Observer Notification Methods
    
    private fun notifyActivityResumed(activityName: String) {
        lifecycleObservers.values.forEach { observer ->
            observer.onActivityResumed(activityName)
        }
    }
    
    private fun notifyActivityPaused(activityName: String) {
        lifecycleObservers.values.forEach { observer ->
            observer.onActivityPaused(activityName)
        }
    }
    
    private fun notifyAppForeground() {
        lifecycleObservers.values.forEach { observer ->
            observer.onAppForeground()
        }
    }
    
    private fun notifyAppBackground() {
        lifecycleObservers.values.forEach { observer ->
            observer.onAppBackground()
        }
    }
    
    // Utility Methods
    
    /**
     * Check if app is in foreground
     */
    fun isAppInForeground(): Boolean = isAppInForeground
    
    /**
     * Get active activity count
     */
    fun getActiveActivityCount(): Int = activityCount
    
    /**
     * Get active activities
     */
    fun getActiveActivities(): List<String> {
        return activeActivities.keys.toList()
    }
    
    /**
     * Get lifecycle statistics
     */
    fun getLifecycleStats(): LifecycleStats {
        return LifecycleStats(
            activeActivityCount = activityCount,
            isAppInForeground = isAppInForeground,
            firestoreListenerCount = firestoreListeners.size,
            lifecycleObserverCount = lifecycleObservers.size,
            activeActivities = getActiveActivities()
        )
    }
    
    private fun getApplicationContext(): android.content.Context {
        // Get context from any active activity
        activeActivities.values.forEach { weakRef ->
            weakRef.get()?.let { activity ->
                return activity.applicationContext
            }
        }
        throw IllegalStateException("No active activities to get context from")
    }
    
    /**
     * Interface for lifecycle observers
     */
    interface LifecycleObserver {
        fun onActivityResumed(activityName: String) {}
        fun onActivityPaused(activityName: String) {}
        fun onAppForeground() {}
        fun onAppBackground() {}
    }
    
    /**
     * Data class for lifecycle statistics
     */
    data class LifecycleStats(
        val activeActivityCount: Int,
        val isAppInForeground: Boolean,
        val firestoreListenerCount: Int,
        val lifecycleObserverCount: Int,
        val activeActivities: List<String>
    )
}