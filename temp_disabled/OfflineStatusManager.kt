package com.campus.panicbutton.utils

import android.view.View
import android.widget.TextView
import com.campus.panicbutton.repository.OfflineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Utility class to manage offline status UI components
 */
class OfflineStatusManager(
    private val offlineRepository: OfflineRepository,
    private val layoutOfflineStatus: View,
    private val tvOfflineStatus: TextView,
    private val tvSyncStatus: TextView
) {
    
    /**
     * Initialize offline status monitoring
     */
    fun initialize() {
        observeConnectivity()
        updateSyncStatus()
    }
    
    /**
     * Observe network connectivity and update UI accordingly
     */
    private fun observeConnectivity() {
        CoroutineScope(Dispatchers.Main).launch {
            offlineRepository.observeConnectivity().collect { isOnline ->
                updateOfflineStatus(isOnline)
            }
        }
    }
    
    /**
     * Update offline status UI
     */
    private fun updateOfflineStatus(isOnline: Boolean) {
        if (isOnline) {
            layoutOfflineStatus.visibility = View.GONE
        } else {
            layoutOfflineStatus.visibility = View.VISIBLE
            tvOfflineStatus.text = "Offline - Data will sync when connection is restored"
        }
        updateSyncStatus()
    }
    
    /**
     * Update sync status information
     */
    fun updateSyncStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            val syncStatus = offlineRepository.getSyncStatus()
            val statusText = if (syncStatus.isOnline) {
                "Online"
            } else {
                val pendingCount = syncStatus.pendingOperations + syncStatus.unsyncedAlerts
                if (pendingCount > 0) {
                    "$pendingCount pending"
                } else {
                    "Offline"
                }
            }
            tvSyncStatus.text = statusText
        }
    }
    
    /**
     * Show a temporary status message
     */
    fun showStatusMessage(message: String, isOnline: Boolean) {
        if (!isOnline) {
            layoutOfflineStatus.visibility = View.VISIBLE
            tvOfflineStatus.text = message
        }
    }
}