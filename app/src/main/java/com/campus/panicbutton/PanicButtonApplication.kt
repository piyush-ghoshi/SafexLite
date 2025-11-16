package com.campus.panicbutton

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.campus.panicbutton.utils.PerformanceManager
import com.campus.panicbutton.utils.LifecycleManager
import com.campus.panicbutton.utils.BatteryOptimizer
import com.campus.panicbutton.utils.MemoryLeakDetector

/**
 * Application class for initializing performance optimizations and global services
 */
class PanicButtonApplication : Application() {
    
    companion object {
        private const val TAG = "PanicButtonApp"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME = "selected_theme"
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing Campus Panic Button Application")
        
        // Apply saved theme
        applySavedTheme()
        
        // Initialize performance manager
        PerformanceManager.getInstance(this)
        
        // Initialize lifecycle manager
        val lifecycleManager = LifecycleManager.getInstance()
        lifecycleManager.initialize(this)
        
        // Initialize battery optimizer
        BatteryOptimizer.getInstance(this)
        
        Log.d(TAG, "Performance optimizations initialized")
        
        // Set up memory leak detection in debug builds
        if (BuildConfig.DEBUG) {
            setupMemoryLeakDetection()
        }
        
        // Register lifecycle callbacks for automatic cleanup
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                if (BuildConfig.DEBUG) {
                    MemoryLeakDetector.trackActivity(activity)
                }
            }
            
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (BuildConfig.DEBUG) {
                    // Check for memory leaks after activity destruction
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val leakResult = MemoryLeakDetector.detectLeaks()
                        if (leakResult.hasLeaks) {
                            Log.w(TAG, "Memory leaks detected after ${activity::class.java.simpleName} destruction")
                        }
                    }, 5000) // Check after 5 seconds
                }
            }
        })
        
        Log.d(TAG, "Campus Panic Button Application initialized successfully")
    }
    
    /**
     * Set up memory leak detection for debug builds
     */
    private fun setupMemoryLeakDetection() {
        Log.d(TAG, "Setting up memory leak detection for debug build")
        
        // Periodic leak detection
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val leakCheckRunnable = object : Runnable {
            override fun run() {
                val leakResult = MemoryLeakDetector.detectLeaks()
                if (leakResult.hasLeaks) {
                    Log.w(TAG, "Periodic leak check found ${leakResult.leakedObjects.size} potential leaks")
                    leakResult.leakedObjects.forEach { leak ->
                        Log.w(TAG, "Leaked object: ${leak.className} (duration: ${leak.trackingDuration}ms)")
                    }
                }
                
                // Schedule next check in 30 seconds
                handler.postDelayed(this, 30000)
            }
        }
        
        // Start periodic leak checking after 30 seconds
        handler.postDelayed(leakCheckRunnable, 30000)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning - performing cleanup")
        
        // Trigger aggressive cleanup
        PerformanceManager.getInstance(this).forceMemoryCleanup()
        BatteryOptimizer.getInstance(this).clearCache()
        
        // Clear image compression cache
        com.campus.panicbutton.utils.ImageCompressionUtils.cleanupCache(this)
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "Trim memory requested: level $level")
        
        when (level) {
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_BACKGROUND -> {
                // App is in background, perform moderate cleanup
                PerformanceManager.getInstance(this).forceMemoryCleanup()
                BatteryOptimizer.getInstance(this).stopLocationUpdates()
            }
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                // System is low on memory, perform aggressive cleanup
                PerformanceManager.getInstance(this).forceMemoryCleanup()
                BatteryOptimizer.getInstance(this).clearCache()
                com.campus.panicbutton.utils.ImageCompressionUtils.cleanupCache(this)
                
                // Clear Firestore cache
                com.campus.panicbutton.utils.FirestoreQueryOptimizer.clearCache()
            }
        }
    }
    
    /**
     * Apply saved theme preference
     */
    private fun applySavedTheme() {
        val savedTheme = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_SYSTEM)
        
        when (savedTheme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        
        Log.d(TAG, "Applied saved theme: $savedTheme")
    }
}