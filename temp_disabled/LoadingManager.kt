package com.campus.panicbutton.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar

/**
 * Utility class for managing loading states and progress feedback
 * Provides consistent loading indicators across the app
 */
class LoadingManager(private val context: Context) {
    
    private var loadingDialog: AlertDialog? = null
    private val loadingStates = mutableMapOf<String, Boolean>()
    
    companion object {
        private const val TAG = "LoadingManager"
    }
    
    /**
     * Show loading state for a button with progress bar
     */
    fun showButtonLoading(
        button: Button,
        progressBar: ProgressBar? = null,
        loadingText: String = "Loading...",
        operationId: String = button.id.toString()
    ) {
        if (loadingStates[operationId] == true) return
        
        loadingStates[operationId] = true
        button.isEnabled = false
        button.text = loadingText
        progressBar?.visibility = View.VISIBLE
    }
    
    /**
     * Hide loading state for a button
     */
    fun hideButtonLoading(
        button: Button,
        progressBar: ProgressBar? = null,
        originalText: String,
        operationId: String = button.id.toString()
    ) {
        loadingStates[operationId] = false
        button.isEnabled = true
        button.text = originalText
        progressBar?.visibility = View.GONE
    }
    
    /**
     * Show a full-screen loading dialog
     */
    fun showLoadingDialog(
        message: String = "Loading...",
        cancelable: Boolean = false
    ) {
        hideLoadingDialog() // Hide any existing dialog
        
        if (context is Activity && !context.isFinishing) {
            loadingDialog = AlertDialog.Builder(context)
                .setView(createLoadingView(message))
                .setCancelable(cancelable)
                .create()
            
            loadingDialog?.show()
        }
    }
    
    /**
     * Hide the loading dialog
     */
    fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
    
    /**
     * Update loading dialog message
     */
    fun updateLoadingMessage(message: String) {
        loadingDialog?.let { dialog ->
            val textView = dialog.findViewById<TextView>(android.R.id.message)
            textView?.text = message
        }
    }
    
    /**
     * Show progress with percentage
     */
    fun showProgress(
        progressBar: ProgressBar,
        textView: TextView? = null,
        progress: Int,
        message: String? = null
    ) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = progress
        
        textView?.let { tv ->
            tv.visibility = View.VISIBLE
            tv.text = message ?: "$progress%"
        }
    }
    
    /**
     * Hide progress indicators
     */
    fun hideProgress(
        progressBar: ProgressBar,
        textView: TextView? = null
    ) {
        progressBar.visibility = View.GONE
        textView?.visibility = View.GONE
    }
    
    /**
     * Show indeterminate progress
     */
    fun showIndeterminateProgress(
        progressBar: ProgressBar,
        textView: TextView? = null,
        message: String = "Loading..."
    ) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        
        textView?.let { tv ->
            tv.visibility = View.VISIBLE
            tv.text = message
        }
    }
    
    /**
     * Show loading state for a view group
     */
    fun showViewLoading(
        contentView: View,
        loadingView: View,
        progressBar: ProgressBar? = null,
        messageView: TextView? = null,
        message: String = "Loading..."
    ) {
        contentView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        progressBar?.visibility = View.VISIBLE
        messageView?.let {
            it.visibility = View.VISIBLE
            it.text = message
        }
    }
    
    /**
     * Hide loading state for a view group
     */
    fun hideViewLoading(
        contentView: View,
        loadingView: View,
        progressBar: ProgressBar? = null,
        messageView: TextView? = null
    ) {
        contentView.visibility = View.VISIBLE
        loadingView.visibility = View.GONE
        progressBar?.visibility = View.GONE
        messageView?.visibility = View.GONE
    }
    
    /**
     * Show a snackbar with loading state
     */
    fun showLoadingSnackbar(
        view: View,
        message: String = "Loading...",
        duration: Int = Snackbar.LENGTH_INDEFINITE
    ): Snackbar {
        return Snackbar.make(view, message, duration)
            .setAction("Cancel") { /* Handle cancellation if needed */ }
            .also { it.show() }
    }
    
    /**
     * Show error with retry option
     */
    fun showErrorWithRetry(
        view: View,
        message: String,
        retryAction: () -> Unit,
        duration: Int = Snackbar.LENGTH_LONG
    ): Snackbar {
        return Snackbar.make(view, message, duration)
            .setAction("Retry") { retryAction() }
            .also { it.show() }
    }
    
    /**
     * Show success message
     */
    fun showSuccess(
        view: View,
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT
    ): Snackbar {
        return Snackbar.make(view, message, duration)
            .also { it.show() }
    }
    
    /**
     * Check if any operation is currently loading
     */
    fun isLoading(): Boolean {
        return loadingStates.values.any { it }
    }
    
    /**
     * Check if specific operation is loading
     */
    fun isLoading(operationId: String): Boolean {
        return loadingStates[operationId] == true
    }
    
    /**
     * Clear all loading states
     */
    fun clearAllLoadingStates() {
        loadingStates.clear()
        hideLoadingDialog()
    }
    
    /**
     * Create a loading view for dialogs
     */
    private fun createLoadingView(message: String): View {
        val context = this.context
        val view = View.inflate(context, android.R.layout.simple_list_item_1, null)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = message
        return view
    }
    
    /**
     * Show operation feedback with automatic timeout
     */
    fun showOperationFeedback(
        view: View,
        message: String,
        isSuccess: Boolean,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        val snackbar = Snackbar.make(view, message, duration)
        
        if (isSuccess) {
            // You can customize colors here if needed
            snackbar.show()
        } else {
            snackbar.setAction("Dismiss") { snackbar.dismiss() }
                .show()
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        hideLoadingDialog()
        loadingStates.clear()
    }
}

/**
 * Extension functions for easier usage
 */
fun Button.showLoading(
    loadingManager: LoadingManager,
    progressBar: ProgressBar? = null,
    loadingText: String = "Loading..."
) {
    loadingManager.showButtonLoading(this, progressBar, loadingText)
}

fun Button.hideLoading(
    loadingManager: LoadingManager,
    progressBar: ProgressBar? = null,
    originalText: String
) {
    loadingManager.hideButtonLoading(this, progressBar, originalText)
}

fun View.showErrorWithRetry(
    loadingManager: LoadingManager,
    message: String,
    retryAction: () -> Unit
) {
    loadingManager.showErrorWithRetry(this, message, retryAction)
}

fun View.showSuccess(
    loadingManager: LoadingManager,
    message: String
) {
    loadingManager.showSuccess(this, message)
}