package com.campus.panicbutton.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.campus.panicbutton.R
import com.google.firebase.auth.FirebaseAuth

/**
 * Splash Screen Activity
 * Shows SafexLite logo with animation and tagline
 * Checks if user is logged in and navigates accordingly
 */
class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val SPLASH_DELAY = 2500L // 2.5 seconds
    }
    
    private lateinit var auth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_splash)
            
            // Hide action bar
            supportActionBar?.hide()
            
            auth = FirebaseAuth.getInstance()
            
            // Start animations
            startAnimations()
            
            // Navigate after delay
            Handler(Looper.getMainLooper()).postDelayed({
                navigateToNextScreen()
            }, SPLASH_DELAY)
        } catch (e: Exception) {
            e.printStackTrace()
            // If splash screen fails, go directly to login
            try {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            } catch (ex: Exception) {
                ex.printStackTrace()
                finish()
            }
        }
    }
    
    private fun startAnimations() {
        try {
            val logo = findViewById<ImageView>(R.id.ivLogo)
            val appName = findViewById<TextView>(R.id.tvAppName)
            val tagline = findViewById<TextView>(R.id.tvTagline)
            
            // Load animations safely
            try {
                val scaleUpAnim = AnimationUtils.loadAnimation(this, R.anim.scale_up)
                val slideUpAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up)
                val fadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                
                // Apply animations with delays
                logo?.startAnimation(scaleUpAnim)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    appName?.startAnimation(slideUpAnim)
                }, 400)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    tagline?.startAnimation(fadeInAnim)
                }, 800)
            } catch (e: Exception) {
                // If animations fail, just show the views without animation
                logo?.alpha = 1f
                appName?.alpha = 1f
                tagline?.alpha = 1f
            }
        } catch (e: Exception) {
            // If anything fails, continue without animations
            e.printStackTrace()
        }
    }
    
    private fun navigateToNextScreen() {
        try {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            
            // Add transition animation
            try {
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } catch (e: Exception) {
                // Ignore transition animation errors
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If navigation fails, just finish this activity
            finish()
        }
    }
}
