package com.campus.panicbutton.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.campus.panicbutton.R
import kotlinx.coroutines.*

/**
 * Utility class for managing progress feedback and visual indicators
 * Provides smooth animations and consistent progress reporting
 */
class ProgressFeedbackManager(private val context: Context) {
    
    private val progressJobs = mutableMapOf<String, Job>()
    
    companion object {
        private const val TAG = "ProgressFeedbackManager"
        private const val ANIMATION_DURATION = 300L
    }
    
    /**
     * Show progress with smooth animation
     */
    fun showProgress(
        progressBar: ProgressBar,
        textView: TextView? = null,
        operationId: String,
        message: String = "Loading...",
        animate: Boolean = true
    ) {
        // Cancel any existing progress job for this operation
        progressJobs[operationId]?.cancel()
        
        progressBar.visibility = View.VISIBLE
        textView?.let {
            it.visibility = View.VISIBLE
            it.text = message
        }
        
        if (animate) {
            progressBar.alpha = 0f
            ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            
            textView?.let {
                it.alpha = 0f
                ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                    duration = ANIMATION_DURATION
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }
    
    /**
     * Hide progress with smooth animation
     */
    fun hideProgress(
        progressBar: ProgressBar,
        textView: TextView? = null,
        operationId: String,
        animate: Boolean = true
    ) {
        progressJobs[operationId]?.cancel()
        progressJobs.remove(operationId)
        
        if (animate) {
            ObjectAnimator.ofFloat(progressBar, "alpha", 1f, 0f).apply {
                duration = ANIMATION_DURATION
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }.also { animator ->
                animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        progressBar.visibility = View.GONE
                    }
                })
            }
            
            textView?.let { tv ->
                ObjectAnimator.ofFloat(tv, "alpha", 1f, 0f).apply {
                    duration = ANIMATION_DURATION
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }.also { animator ->
                    animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            tv.visibility = View.GONE
                        }
                    })
                }
            }
        } else {
            progressBar.visibility = View.GONE
            textView?.visibility = View.GONE
        }
    }
    
    /**
     * Show determinate progress with percentage
     */
    fun showDeterminateProgress(
        progressBar: ProgressBar,
        textView: TextView? = null,
        operationId: String,
        progress: Int,
        message: String? = null,
        animate: Boolean = true
    ) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        
        if (animate) {
            ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, progress).apply {
                duration = 200L
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        } else {
            progressBar.progress = progress
        }
        
        textView?.let {
            it.visibility = View.VISIBLE
            it.text = message ?: "$progress%"
        }
    }
    
    /**
     * Show indeterminate progress with pulsing animation
     */
    fun showIndeterminateProgress(
        progressBar: ProgressBar,
        textView: TextView? = null,
        operationId: String,
        message: String = "Loading...",
        pulseAnimation: Boolean = true
    ) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        
        textView?.let {
            it.visibility = View.VISIBLE
            it.text = message
        }
        
        if (pulseAnimation) {
            startPulseAnimation(textView, operationId)
        }
    }
    
    /**
     * Start pulsing animation for text view
     */
    private fun startPulseAnimation(textView: TextView?, operationId: String) {
        textView ?: return
        
        progressJobs[operationId] = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                ObjectAnimator.ofFloat(textView, "alpha", 1f, 0.5f, 1f).apply {
                    duration = 1500L
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
                delay(1500L)
            }
        }
    }
    
    /**
     * Show success feedback with green color and checkmark animation
     */
    fun showSuccessFeedback(
        view: View,
        textView: TextView? = null,
        message: String = "Success!",
        duration: Long = 2000L
    ) {
        // Change background color to success color
        val successColor = ContextCompat.getColor(context, android.R.color.holo_green_light)
        val originalColor = ContextCompat.getColor(context, R.color.colorPrimary)
        
        // Animate background color change
        ObjectAnimator.ofArgb(view, "backgroundColor", originalColor, successColor).apply {
            duration = ANIMATION_DURATION
            start()
        }
        
        textView?.let {
            it.text = message
            it.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        
        // Revert color after duration
        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            ObjectAnimator.ofArgb(view, "backgroundColor", successColor, originalColor).apply {
                duration = ANIMATION_DURATION
                start()
            }
            textView?.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
    }
    
    /**
     * Show error feedback with red color and shake animation
     */
    fun showErrorFeedback(
        view: View,
        textView: TextView? = null,
        message: String = "Error occurred",
        duration: Long = 3000L
    ) {
        // Change background color to error color
        val errorColor = ContextCompat.getColor(context, android.R.color.holo_red_light)
        val originalColor = ContextCompat.getColor(context, R.color.colorPrimary)
        
        // Animate background color change
        ObjectAnimator.ofArgb(view, "backgroundColor", originalColor, errorColor).apply {
            duration = ANIMATION_DURATION
            start()
        }
        
        // Shake animation
        ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f).apply {
            duration = 600L
            start()
        }
        
        textView?.let {
            it.text = message
            it.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        
        // Revert color after duration
        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            ObjectAnimator.ofArgb(view, "backgroundColor", errorColor, originalColor).apply {
                duration = ANIMATION_DURATION
                start()
            }
            textView?.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
    }
    
    /**
     * Show warning feedback with orange color
     */
    fun showWarningFeedback(
        view: View,
        textView: TextView? = null,
        message: String = "Warning",
        duration: Long = 2500L
    ) {
        val warningColor = ContextCompat.getColor(context, android.R.color.holo_orange_light)
        val originalColor = ContextCompat.getColor(context, R.color.colorPrimary)
        
        ObjectAnimator.ofArgb(view, "backgroundColor", originalColor, warningColor).apply {
            duration = ANIMATION_DURATION
            start()
        }
        
        textView?.let {
            it.text = message
            it.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            ObjectAnimator.ofArgb(view, "backgroundColor", warningColor, originalColor).apply {
                duration = ANIMATION_DURATION
                start()
            }
        }
    }
    
    /**
     * Show retry countdown with visual feedback
     */
    fun showRetryCountdown(
        textView: TextView,
        operationId: String,
        countdownSeconds: Int,
        onCountdownComplete: () -> Unit
    ) {
        progressJobs[operationId]?.cancel()
        
        progressJobs[operationId] = CoroutineScope(Dispatchers.Main).launch {
            for (i in countdownSeconds downTo 1) {
                textView.text = "Retrying in $i seconds..."
                textView.visibility = View.VISIBLE
                
                // Pulse animation for each second
                ObjectAnimator.ofFloat(textView, "scaleX", 1f, 1.1f, 1f).apply {
                    duration = 200L
                    start()
                }
                ObjectAnimator.ofFloat(textView, "scaleY", 1f, 1.1f, 1f).apply {
                    duration = 200L
                    start()
                }
                
                delay(1000L)
            }
            
            if (isActive) {
                textView.text = "Retrying..."
                onCountdownComplete()
            }
        }
    }
    
    /**
     * Cancel all progress operations
     */
    fun cancelAllProgress() {
        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
    }
    
    /**
     * Cancel specific progress operation
     */
    fun cancelProgress(operationId: String) {
        progressJobs[operationId]?.cancel()
        progressJobs.remove(operationId)
    }
    
    /**
     * Check if operation is in progress
     */
    fun isInProgress(operationId: String): Boolean {
        return progressJobs[operationId]?.isActive == true
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelAllProgress()
    }
}