package com.campus.panicbutton.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.campus.panicbutton.R
import com.campus.panicbutton.activities.AlertDetailsActivity

/**
 * Service for managing notifications
 * Handles creation and display of emergency alert notifications
 */
class NotificationService(private val context: Context) {
    
    companion object {
        const val ALERT_CHANNEL_ID = "emergency_alerts"
        const val ALERT_CHANNEL_NAME = "Emergency Alerts"
        const val STATUS_CHANNEL_ID = "status_updates"
        const val STATUS_CHANNEL_NAME = "Status Updates"
        
        const val EXTRA_ALERT_ID = "alert_id"
        
        private const val ALERT_NOTIFICATION_ID_BASE = 1000
        private const val STATUS_NOTIFICATION_ID_BASE = 2000
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Emergency alerts channel - high priority with sound and vibration
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency alert notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }
            
            // Status updates channel - normal priority
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                STATUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alert status update notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500)
            }
            
            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }
    
    /**
     * Display notification for new emergency alert
     */
    fun showAlertNotification(
        alertId: String,
        title: String,
        body: String,
        message: String? = null,
        timestamp: String
    ) {
        val intent = Intent(context, AlertDetailsActivity::class.java).apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        // Add expanded text if message is provided
        if (!message.isNullOrBlank()) {
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText("$body\n\nMessage: $message\nTime: $timestamp")
                .setBigContentTitle(title)
            notificationBuilder.setStyle(bigTextStyle)
        } else {
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText("$body\nTime: $timestamp")
                .setBigContentTitle(title)
            notificationBuilder.setStyle(bigTextStyle)
        }
        
        // Add action buttons for quick response
        val acceptIntent = createActionIntent(alertId, "ACCEPT")
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            (alertId + "accept").hashCode(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationBuilder.addAction(
            R.drawable.ic_check,
            "Accept",
            acceptPendingIntent
        )
        
        val viewIntent = createActionIntent(alertId, "VIEW")
        val viewPendingIntent = PendingIntent.getBroadcast(
            context,
            (alertId + "view").hashCode(),
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationBuilder.addAction(
            R.drawable.ic_visibility,
            "View Details",
            viewPendingIntent
        )
        
        val notificationId = ALERT_NOTIFICATION_ID_BASE + alertId.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
    
    /**
     * Display notification for alert status updates
     */
    fun showStatusUpdateNotification(
        alertId: String,
        title: String,
        body: String
    ) {
        val intent = Intent(context, AlertDetailsActivity::class.java).apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        val notificationId = STATUS_NOTIFICATION_ID_BASE + alertId.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
    
    /**
     * Cancel notification for a specific alert
     */
    fun cancelAlertNotification(alertId: String) {
        val alertNotificationId = ALERT_NOTIFICATION_ID_BASE + alertId.hashCode()
        val statusNotificationId = STATUS_NOTIFICATION_ID_BASE + alertId.hashCode()
        
        notificationManager.cancel(alertNotificationId)
        notificationManager.cancel(statusNotificationId)
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
    
    private fun createActionIntent(alertId: String, action: String): Intent {
        return Intent("com.campus.panicbutton.NOTIFICATION_ACTION").apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra("action", action)
        }
    }
}