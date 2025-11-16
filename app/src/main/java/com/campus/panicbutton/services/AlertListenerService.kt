package com.campus.panicbutton.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.campus.panicbutton.R
import com.campus.panicbutton.activities.GuardDashboardActivity
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.utils.SimpleNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * Foreground service that listens for new alerts even when app is in background
 * This enables notifications to work when app is not actively open
 */
class AlertListenerService : Service() {
    
    companion object {
        private const val TAG = "AlertListenerService"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "alert_listener_service"
        private const val CHANNEL_NAME = "Alert Monitoring"
        
        fun start(context: Context) {
            val intent = Intent(context, AlertListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, AlertListenerService::class.java)
            context.stopService(intent)
        }
    }
    
    private lateinit var firebaseService: FirebaseService
    private lateinit var simpleNotificationManager: SimpleNotificationManager
    private var alertsListener: ListenerRegistration? = null
    private var previousAlertIds = mutableSetOf<String>()
    private var currentUserId: String? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        firebaseService = FirebaseService()
        simpleNotificationManager = SimpleNotificationManager(this)
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        
        startListeningToAlerts()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY // Restart service if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopListeningToAlerts()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance for foreground service
            ).apply {
                description = "Keeps the app monitoring for emergency alerts"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, GuardDashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafexLite")
            .setContentText("Monitoring for emergency alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun startListeningToAlerts() {
        if (currentUserId == null) {
            Log.w(TAG, "No user logged in, stopping service")
            stopSelf()
            return
        }
        
        Log.d(TAG, "Starting to listen for alerts")
        
        alertsListener = firebaseService.addAlertsListener { alerts ->
            Log.d(TAG, "Received ${alerts.size} alerts in background service")
            
            // Filter only active and in-progress alerts
            val activeAlerts = alerts.filter { alert ->
                alert.status == com.campus.panicbutton.models.AlertStatus.ACTIVE ||
                alert.status == com.campus.panicbutton.models.AlertStatus.IN_PROGRESS
            }
            
            // Check for new alerts
            activeAlerts.forEach { alert ->
                if (!previousAlertIds.contains(alert.id) && alert.guardId != currentUserId) {
                    // This is a new alert from another user
                    Log.d(TAG, "New alert detected in background: ${alert.id}")
                    simpleNotificationManager.showAlertNotification(alert)
                }
            }
            
            // Update previous alert IDs (only track active alerts)
            previousAlertIds.clear()
            previousAlertIds.addAll(activeAlerts.map { it.id })
        }
    }
    
    private fun stopListeningToAlerts() {
        alertsListener?.remove()
        alertsListener = null
        Log.d(TAG, "Stopped listening to alerts")
    }
}
