package com.campus.panicbutton.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.campus.panicbutton.R
import com.campus.panicbutton.activities.LoginActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @get:Rule
    val activityRule = ActivityTestRule(LoginActivity::class.java)

    @Test
    fun loginActivity_displaysCorrectly() {
        // Check that all UI elements are displayed
        onView(withId(R.id.editTextEmail))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.editTextPassword))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.spinnerRole))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.buttonLogin))
            .check(matches(isDisplayed()))
            .check(matches(withText("Login")))
    }

    @Test
    fun loginActivity_emptyFieldsShowError() {
        // Try to login with empty fields
        onView(withId(R.id.buttonLogin))
            .perform(click())

        // Check that error messages are shown
        onView(withId(R.id.editTextEmail))
            .check(matches(hasErrorText("Email is required")))
    }

    @Test
    fun loginActivity_invalidEmailShowsError() {
        // Enter invalid email
        onView(withId(R.id.editTextEmail))
            .perform(typeText("invalid-email"), closeSoftKeyboard())
        
        onView(withId(R.id.editTextPassword))
            .perform(typeText("password123"), closeSoftKeyboard())

        onView(withId(R.id.buttonLogin))
            .perform(click())

        // Check that email error is shown
        onView(withId(R.id.editTextEmail))
            .check(matches(hasErrorText("Invalid email format")))
    }

    @Test
    fun loginActivity_validInputsAttemptLogin() {
        // Enter valid credentials
        onView(withId(R.id.editTextEmail))
            .perform(typeText("test@campus.edu"), closeSoftKeyboard())
        
        onView(withId(R.id.editTextPassword))
            .perform(typeText("password123"), closeSoftKeyboard())

        // Select role
        onView(withId(R.id.spinnerRole))
            .perform(click())
        onView(withText("Guard"))
            .perform(click())

        // Attempt login
        onView(withId(R.id.buttonLogin))
            .perform(click())

        // Check that loading indicator appears
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_roleSelectionWorks() {
        // Test Guard role selection
        onView(withId(R.id.spinnerRole))
            .perform(click())
        onView(withText("Guard"))
            .perform(click())

        // Test Admin role selection
        onView(withId(R.id.spinnerRole))
            .perform(click())
        onView(withText("Administrator"))
            .perform(click())
    }

    @Test
    fun loginActivity_passwordVisibilityToggle() {
        // Enter password
        onView(withId(R.id.editTextPassword))
            .perform(typeText("password123"))

        // Check password is hidden by default
        onView(withId(R.id.editTextPassword))
            .check(matches(hasInputType(129))) // PASSWORD input type

        // Toggle password visibility
        onView(withId(R.id.togglePasswordVisibility))
            .perform(click())

        // Check password is now visible
        onView(withId(R.id.editTextPassword))
            .check(matches(hasInputType(1))) // TEXT input type
    }

    @Test
    fun loginActivity_loadingStateDisablesButton() {
        // Enter valid credentials
        onView(withId(R.id.editTextEmail))
            .perform(typeText("test@campus.edu"), closeSoftKeyboard())
        
        onView(withId(R.id.editTextPassword))
            .perform(typeText("password123"), closeSoftKeyboard())

        // Click login to trigger loading state
        onView(withId(R.id.buttonLogin))
            .perform(click())

        // Check that button is disabled during loading
        onView(withId(R.id.buttonLogin))
            .check(matches(isNotEnabled()))
    }
}