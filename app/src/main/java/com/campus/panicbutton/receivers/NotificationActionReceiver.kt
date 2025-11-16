package com.campus.panicbutton.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.campus.panicbutton.activities.AlertDetailsActivity
import com.campus.panicbutton.services.NotificationService

/**
 * Broadcast receiver for handling notification action clicks
 * Handles quick actions from notification buttons
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received notification action: ${intent.action}")
        
        if (intent.action == "com.campus.panicbutton.NOTIFICATION_ACTION") {
            val alertId = intent.getStringExtra(NotificationService.EXTRA_ALERT_ID)
            val action = intent.getStringExtra("action")
            
            Log.d(TAG, "Processing action: $action for alert: $alertId")
            
            when (action) {
                "ACCEPT" -> {
                    // Handle accept action - could trigger Firebase update
                    // For now, just open the alert details
                    openAlertDetails(context, alertId)
                }
                "VIEW" -> {
                    // Handle view action
                    openAlertDetails(context, alertId)
                }
                else -> {
                    Log.w(TAG, "Unknown action: $action")
                }
            }
        }
    }
    
    private fun openAlertDetails(context: Context, alertId: String?) {
        if (alertId != null) {
            val intent = Intent(context, AlertDetailsActivity::class.java).apply {
                putExtra(NotificationService.EXTRA_ALERT_ID, alertId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}