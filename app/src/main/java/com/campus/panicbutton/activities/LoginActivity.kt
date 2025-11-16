package com.campus.panicbutton.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.campus.panicbutton.R
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.services.FirebaseService
import com.campus.panicbutton.utils.ErrorHandler
import com.campus.panicbutton.utils.LoadingManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.net.UnknownHostException

class LoginActivity : AppCompatActivity() {
    
    private lateinit var roleRadioGroup: RadioGroup
    private lateinit var radioGuard: RadioButton
    private lateinit var radioAdmin: RadioButton
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var nameEditText: TextInputEditText

    private lateinit var loginButton: Button
    private lateinit var signupButton: Button
    private lateinit var toggleModeText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var errorTextView: TextView
    
    private var isSignupMode = false
    
    private lateinit var firebaseService: FirebaseService
    private lateinit var errorHandler: ErrorHandler
    private lateinit var loadingManager: LoadingManager
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        initializeViews()
        initializeFirebaseService()
        setupClickListeners()
        
        // Check if user is already authenticated
        checkExistingAuthentication()
    }
    
    private fun initializeViews() {
        roleRadioGroup = findViewById(R.id.roleRadioGroup)
        radioGuard = findViewById(R.id.radioGuard)
        radioAdmin = findViewById(R.id.radioAdmin)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        emailEditText = findViewById(R.id.emailEditText)
        nameInputLayout = findViewById(R.id.nameInputLayout)
        nameEditText = findViewById(R.id.nameEditText)
        signupButton = findViewById(R.id.signupButton)
        toggleModeText = findViewById(R.id.toggleModeText)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        errorTextView = findViewById(R.id.errorTextView)
    }
    
    private fun initializeFirebaseService() {
        firebaseService = FirebaseService()
        errorHandler = ErrorHandler(this)
        loadingManager = LoadingManager(this)
        
        // Initialize campus blocks data if needed
        firebaseService.initializeAppData()
            .addOnSuccessListener {
                Log.d(TAG, "App data initialization completed")
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "App data initialization failed", exception)
                // Don't block login if campus blocks initialization fails
                val handledError = errorHandler.handleError(exception, "app data initialization")
                if (handledError.category == ErrorHandler.ErrorCategory.NETWORK) {
                    // Show a subtle warning about offline mode
                    showError("Limited functionality - some features may not be available")
                }
            }
    }
    
    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            if (isSignupMode) {
                attemptSignup()
            } else {
                attemptLogin()
            }
        }
        
        signupButton.setOnClickListener {
            attemptSignup()
        }
        
        toggleModeText.setOnClickListener {
            toggleMode()
        }
        
        // Clear error when user starts typing
        emailEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) clearError()
        }
        
        passwordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) clearError()
        }
        
        nameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) clearError()
        }
        
        roleRadioGroup.setOnCheckedChangeListener { _, _ ->
            clearError()
        }
    }
    
    private fun checkExistingAuthentication() {
        if (firebaseService.isUserAuthenticated()) {
            Log.d(TAG, "User already authenticated, validating role...")
            showLoading(true)
            
            val currentUser = firebaseService.getCurrentUser()
            if (currentUser != null) {
                firebaseService.validateUserRole(currentUser.uid)
                    .addOnSuccessListener { userRole ->
                        if (userRole != null) {
                            Log.d(TAG, "User role validated: $userRole")
                            // For existing authentication, we need to get the full user profile
                            firebaseService.getUserProfile(currentUser.uid)
                                .addOnSuccessListener { document ->
                                    if (document != null && document.exists()) {
                                        val user = document.toObject(User::class.java)
                                        if (user != null) {
                                            navigateToRoleDashboard(userRole, user)
                                        } else {
                                            Log.w(TAG, "Failed to parse user profile")
                                            firebaseService.signOut()
                                            showLoading(false)
                                        }
                                    } else {
                                        Log.w(TAG, "User profile document not found")
                                        firebaseService.signOut()
                                        showLoading(false)
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    Log.e(TAG, "Failed to get user profile", exception)
                                    firebaseService.signOut()
                                    showLoading(false)
                                }
                        } else {
                            Log.w(TAG, "User role not found, signing out")
                            firebaseService.signOut()
                            showLoading(false)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to validate user role", exception)
                        firebaseService.signOut()
                        showLoading(false)
                        showError(getString(R.string.error_user_not_found))
                    }
            } else {
                showLoading(false)
            }
        }
    }
    
    private fun attemptLogin() {
        if (!validateInput()) {
            return
        }
        
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val selectedRole = getSelectedRole()
        
        Log.d(TAG, "Attempting login for: $email with role: $selectedRole")
        
        showLoading(true)
        clearError()
        
        // Use enhanced authentication with comprehensive error handling
        firebaseService.authenticateUserWithErrorHandling(
            email = email,
            password = password,
            onSuccess = { user ->
                Log.d(TAG, "Login successful for user: ${user.email}")
                handleLoginSuccess(user, selectedRole)
            },
            onError = { message, isRetryable, actionText ->
                Log.e(TAG, "Login failed: $message")
                showLoading(false)
                handleEnhancedLoginError(message, isRetryable, actionText)
            }
        )
    }
    
    private fun attemptSignup() {
        if (!validateSignupInput()) {
            return
        }
        
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val name = nameEditText.text.toString().trim()
        val selectedRole = getSelectedRole()
        
        Log.d(TAG, "Attempting signup for: $email with role: $selectedRole")
        
        showLoading(true)
        clearError()
        
        // Create user account
        firebaseService.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    // Create user profile in Firestore
                    val user = User(
                        id = firebaseUser.uid,
                        email = email,
                        name = name,
                        role = selectedRole,
                        isActive = true
                    )
                    
                    firebaseService.createUserProfile(user)
                        .addOnSuccessListener {
                            Log.d(TAG, "User profile created successfully")
                            showLoading(false)
                            handleLoginSuccess(user, selectedRole)
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "Failed to create user profile", exception)
                            showLoading(false)
                            showError("Account created but profile setup failed. Please contact admin.")
                        }
                } else {
                    showLoading(false)
                    showError("Account creation failed. Please try again.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Signup failed", exception)
                showLoading(false)
                val errorHandler = ErrorHandler(this)
                val handledError = errorHandler.handleError(exception, "signup")
                showError(handledError.userMessage)
            }
    }
    
    private fun toggleMode() {
        isSignupMode = !isSignupMode
        updateUIForMode()
    }
    
    private fun updateUIForMode() {
        if (isSignupMode) {
            // Show signup mode
            nameInputLayout.visibility = View.VISIBLE
            loginButton.text = "Create Account"
            signupButton.visibility = View.GONE
            toggleModeText.text = "Already have an account? Sign in"
        } else {
            // Show login mode
            nameInputLayout.visibility = View.GONE
            loginButton.text = getString(R.string.login_button)
            signupButton.visibility = View.GONE
            toggleModeText.text = "Don't have an account? Sign up"
        }
        clearError()
    }
    
    private fun validateSignupInput(): Boolean {
        var isValid = true
        
        // Clear previous errors
        emailInputLayout.error = null
        passwordInputLayout.error = null
        nameInputLayout.error = null
        clearError()
        
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val name = nameEditText.text.toString().trim()
        
        // Validate email
        if (email.isEmpty()) {
            emailInputLayout.error = getString(R.string.error_empty_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = getString(R.string.error_invalid_email)
            isValid = false
        }
        
        // Validate password
        if (password.isEmpty()) {
            passwordInputLayout.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        }
        
        // Validate name
        if (name.isEmpty()) {
            nameInputLayout.error = "Name is required"
            isValid = false
        }
        
        return isValid
    }
    
    private fun validateInput(): Boolean {
        var isValid = true
        
        // Clear previous errors
        emailInputLayout.error = null
        passwordInputLayout.error = null
        clearError()
        
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        
        // Validate email
        if (email.isEmpty()) {
            emailInputLayout.error = getString(R.string.error_empty_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = getString(R.string.error_invalid_email)
            isValid = false
        }
        
        // Validate password
        if (password.isEmpty()) {
            passwordInputLayout.error = getString(R.string.error_empty_password)
            isValid = false
        }
        
        return isValid
    }
    
    private fun getSelectedRole(): UserRole {
        return when (roleRadioGroup.checkedRadioButtonId) {
            R.id.radioAdmin -> UserRole.ADMIN
            else -> UserRole.GUARD
        }
    }
    
    private fun handleLoginSuccess(user: User, selectedRole: UserRole) {
        // Verify that the selected role matches the user's actual role
        if (user.role != selectedRole) {
            Log.w(TAG, "Role mismatch: selected=$selectedRole, actual=${user.role}")
            showLoading(false)
            showError(getString(R.string.error_role_mismatch))
            return
        }
        
        // Check if user is active
        if (!user.isActive) {
            Log.w(TAG, "User account is inactive: ${user.email}")
            showLoading(false)
            showError(getString(R.string.error_account_inactive))
            firebaseService.signOut()
            return
        }
        
        Log.d(TAG, "Login validation successful, navigating to dashboard")
        navigateToRoleDashboard(user.role, user)
    }
    
    private fun handleLoginError(exception: Exception) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> getString(R.string.error_login_failed)
            is FirebaseAuthInvalidCredentialsException -> getString(R.string.error_login_failed)
            is UnknownHostException -> getString(R.string.error_network)
            else -> {
                if (exception.message?.contains("user profile not found", ignoreCase = true) == true) {
                    getString(R.string.error_user_not_found)
                } else {
                    getString(R.string.error_login_failed)
                }
            }
        }
        showError(errorMessage)
    }
    
    private fun navigateToRoleDashboard(role: UserRole, user: User) {
        Log.d(TAG, "Navigating to dashboard for role: $role")
        
        val intent = when (role) {
            UserRole.GUARD -> Intent(this, GuardDashboardActivity::class.java).apply {
                putExtra(GuardDashboardActivity.EXTRA_USER, user)
            }
            UserRole.ADMIN -> Intent(this, AdminDashboardActivity::class.java).apply {
                putExtra(AdminDashboardActivity.EXTRA_USER, user)
            }
        }
        
        // Clear the activity stack so user can't go back to login
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(show: Boolean) {
        runOnUiThread {
            if (show) {
                loadingManager.showButtonLoading(
                    button = loginButton,
                    progressBar = loadingProgressBar,
                    loadingText = getString(R.string.loading_authenticating),
                    operationId = "login"
                )
            } else {
                loadingManager.hideButtonLoading(
                    button = loginButton,
                    progressBar = loadingProgressBar,
                    originalText = getString(R.string.login_button),
                    operationId = "login"
                )
            }
        }
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            errorTextView.text = message
            errorTextView.visibility = View.VISIBLE
        }
    }
    
    private fun clearError() {
        errorTextView.visibility = View.GONE
        emailInputLayout.error = null
        passwordInputLayout.error = null
    }
    
    /**
     * Handle enhanced login errors with retry options and user guidance
     */
    private fun handleEnhancedLoginError(message: String, isRetryable: Boolean, actionText: String?) {
        showError(message)
        
        // Show retry option for retryable errors
        if (isRetryable) {
            val rootView = findViewById<View>(android.R.id.content)
            loadingManager.showErrorWithRetry(
                view = rootView,
                message = errorHandler.getShortErrorMessage(Exception(message)),
                retryAction = { attemptLogin() }
            )
        }
        
        // Handle specific actions
        actionText?.let { action ->
            when (action) {
                "Contact Admin" -> {
                    // Could show a dialog with admin contact information
                    showAdminContactDialog()
                }
                "Check Connection" -> {
                    // Could show network settings or connectivity check
                    showNetworkCheckDialog()
                }
            }
        }
    }
    
    /**
     * Show admin contact dialog for account issues
     */
    private fun showAdminContactDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Contact Administrator")
            .setMessage("Please contact your system administrator for account assistance.\n\nEmail: admin@campus.edu\nPhone: (555) 123-4567")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    /**
     * Show network connectivity check dialog
     */
    private fun showNetworkCheckDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Network Connection")
            .setMessage("Please check your internet connection and try again.\n\n• Ensure WiFi or mobile data is enabled\n• Check if you can access other apps\n• Try moving to a different location")
            .setPositiveButton("Retry") { _, _ -> attemptLogin() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        loadingManager.cleanup()
    }
}