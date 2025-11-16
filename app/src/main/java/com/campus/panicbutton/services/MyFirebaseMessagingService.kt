package com.campus.panicbutton.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service for handling push notifications
 * Handles incoming alert notifications and token updates
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMService"
        private const val ALERT_TYPE = "alert"
        private const val STATUS_UPDATE_TYPE = "status_update"
    }
    
    private lateinit var notificationService: NotificationService
    private lateinit var firebaseService: FirebaseService
    
    override fun onCreate() {
        super.onCreate()
        notificationService = NotificationService(this)
        firebaseService = FirebaseService()
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
        
        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // For foreground notifications, we handle them through data payload
            // Background notifications are handled automatically by FCM
        }
    }
    
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        
        when (type) {
            ALERT_TYPE -> {
                handleAlertNotification(data)
            }
            STATUS_UPDATE_TYPE -> {
                handleStatusUpdateNotification(data)
            }
            else -> {
                Log.w(TAG, "Unknown notification type: $type")
            }
        }
    }
    
    private fun handleAlertNotification(data: Map<String, String>) {
        val alertId = data["alertId"] ?: return
        val guardName = data["guardName"] ?: "Unknown Guard"
        val location = data["location"] ?: "Unknown Location"
        val message = data["message"]
        val timestamp = data["timestamp"] ?: ""
        
        Log.d(TAG, "Handling alert notification for alert: $alertId")
        
        notificationService.showAlertNotification(
            alertId = alertId,
            title = "Emergency Alert",
            body = "Alert from $guardName at $location",
            message = message,
            timestamp = timestamp
        )
    }
    
    private fun handleStatusUpdateNotification(data: Map<String, String>) {
        val alertId = data["alertId"] ?: return
        val status = data["status"] ?: return
        val updatedBy = data["updatedBy"] ?: "Unknown"
        
        Log.d(TAG, "Handling status update notification for alert: $alertId")
        
        val title = when (status) {
            "IN_PROGRESS" -> "Alert Accepted"
            "RESOLVED" -> "Alert Resolved"
            "CLOSED" -> "Alert Closed"
            else -> "Alert Updated"
        }
        
        val body = "Alert status changed to $status by $updatedBy"
        
        notificationService.showStatusUpdateNotification(
            alertId = alertId,
            title = title,
            body = body
        )
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Update the user's FCM token in Firestore
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            firebaseService.updateUserFCMToken(user.uid, token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token updated successfully")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to update FCM token", exception)
                }
        }
    }
}