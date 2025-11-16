package com.campus.panicbutton.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.campus.panicbutton.R
import com.campus.panicbutton.activities.GuardDashboardActivity
import com.campus.panicbutton.models.Alert

/**
 * Simple notification manager for local notifications
 * when new alerts are detected via Firestore listeners
 */
class SimpleNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "emergency_alerts"
        private const val CHANNEL_NAME = "Emergency Alerts"
        private const val NOTIFICATION_ID = 1001
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Delete existing channel to force update
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
                
                // Custom sound URI
                val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.emergency_alert}")
                
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Emergency alert notifications with custom sound"
                    enableVibration(true)
                    enableLights(true)
                    setSound(soundUri, audioAttributes)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d("SimpleNotificationManager", "Notification channel created with custom sound")
            } catch (e: Exception) {
                Log.e("SimpleNotificationManager", "Error creating notification channel", e)
            }
        }
    }
    
    fun showAlertNotification(alert: Alert) {
        try {
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w("SimpleNotificationManager", "Notification permission not granted")
                    return
                }
            }
            
            val intent = Intent(context, GuardDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(), // Unique request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Custom sound URI
            val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.emergency_alert}")
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("🚨 Emergency Alert")
                .setContentText("Alert from ${alert.guardName}: ${alert.message ?: "Emergency situation"}")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Alert from ${alert.guardName}: ${alert.message ?: "Emergency situation"}"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .setSound(soundUri)
                .setDefaults(0) // Don't use default sound
                .setOnlyAlertOnce(false) // Always alert
                .build()
            
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d("SimpleNotificationManager", "Notification shown for alert: ${alert.id}")
            
        } catch (e: Exception) {
            Log.e("SimpleNotificationManager", "Error showing notification", e)
        }
    }
}