package com.campus.panicbutton.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.campus.panicbutton.R
import com.campus.panicbutton.models.User
import com.campus.panicbutton.services.FirebaseService

class ProfileActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_USER = "extra_user"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME = "selected_theme"
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
    }
    
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var tvUserStatus: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnClose: ImageButton
    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var radioLightTheme: RadioButton
    private lateinit var radioDarkTheme: RadioButton
    private lateinit var radioSystemTheme: RadioButton
    
    private lateinit var currentUser: User
    private lateinit var firebaseService: FirebaseService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // Get user data
        currentUser = intent.getSerializableExtra(EXTRA_USER) as? User 
            ?: run {
                finish()
                return
            }
        
        firebaseService = FirebaseService()
        
        initializeViews()
        setupClickListeners()
        displayUserInfo()
    }
    
    private fun initializeViews() {
        tvUserName = findViewById(R.id.tvUserName)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvUserStatus = findViewById(R.id.tvUserStatus)
        btnLogout = findViewById(R.id.btnLogout)
        btnClose = findViewById(R.id.btnClose)
        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        radioLightTheme = findViewById(R.id.radioLightTheme)
        radioDarkTheme = findViewById(R.id.radioDarkTheme)
        radioSystemTheme = findViewById(R.id.radioSystemTheme)
        
        // Set current theme selection
        when (getSavedTheme()) {
            THEME_LIGHT -> radioLightTheme.isChecked = true
            THEME_DARK -> radioDarkTheme.isChecked = true
            THEME_SYSTEM -> radioSystemTheme.isChecked = true
        }
    }
    
    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            finish()
        }
        
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
        
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioLightTheme -> applyTheme(THEME_LIGHT)
                R.id.radioDarkTheme -> applyTheme(THEME_DARK)
                R.id.radioSystemTheme -> applyTheme(THEME_SYSTEM)
            }
        }
    }
    
    private fun displayUserInfo() {
        tvUserName.text = currentUser.name
        tvUserEmail.text = currentUser.email
        tvUserRole.text = currentUser.role.name
        tvUserStatus.text = if (currentUser.isActive) "Active" else "Inactive"
    }
    
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performLogout() {
        // Stop background alert service
        try {
            com.campus.panicbutton.services.AlertListenerService.stop(this)
        } catch (e: Exception) {
            // Service might not be running
        }
        
        // Sign out from Firebase
        firebaseService.signOut()
        
        // Navigate back to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun applyTheme(theme: Int) {
        saveTheme(theme)
        
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    private fun saveTheme(theme: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, theme)
            .apply()
    }
    
    private fun getSavedTheme(): Int {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_SYSTEM)
    }
}