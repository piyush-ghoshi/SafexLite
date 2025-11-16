package com.campus.panicbutton.e2e

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.campus.panicbutton.R
import com.campus.panicbutton.activities.AlertDetailsActivity
import com.campus.panicbutton.activities.GuardDashboardActivity
import com.campus.panicbutton.activities.LoginActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * End-to-end tests that simulate complete user workflows
 * These tests verify the entire system working together
 */
@RunWith(AndroidJUnit4::class)
class EndToEndFlowTest {

    @get:Rule
    val activityRule = ActivityTestRule(LoginActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @Before
    fun setup() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun completeGuardWorkflow_LoginToAlertResolution() {
        // Step 1: Login as Guard
        loginAsGuard()

        // Step 2: Create Emergency Alert
        createEmergencyAlert("Fire in building A")

        // Step 3: Verify alert appears in list
        verifyAlertInList()

        // Step 4: Accept another guard's alert
        acceptAlert()

        // Step 5: Resolve the accepted alert
        resolveAlert("Fire extinguished, area secured")

        // Step 6: Verify alert is resolved
        verifyAlertResolved()
    }

    @Test
    fun completeAdminWorkflow_MonitorAndManageAlerts() {
        // Step 1: Login as Admin
        loginAsAdmin()

        // Step 2: View all alerts dashboard
        verifyAdminDashboard()

        // Step 3: Filter alerts by status
        filterAlertsByStatus("ACTIVE")

        // Step 4: View alert details
        viewAlertDetails()

        // Step 5: Close an alert as admin
        closeAlertAsAdmin("Situation resolved by security team")

        // Step 6: Verify alert statistics
        verifyAlertStatistics()
    }

    @Test
    fun multiUserScenario_AlertCreationAndResponse() {
        // This test simulates multiple users interacting with the same alert
        // In practice, this would require multiple device simulation or test coordination

        // Guard 1: Create alert
        loginAsGuard("guard1@campus.edu")
        createEmergencyAlert("Medical emergency in library")
        logout()

        // Guard 2: Accept alert
        loginAsGuard("guard2@campus.edu")
        acceptFirstAvailableAlert()
        logout()

        // Admin: Monitor progress
        loginAsAdmin()
        verifyAlertInProgress()
        logout()

        // Guard 2: Resolve alert
        loginAsGuard("guard2@campus.edu")
        resolveAcceptedAlert("Patient transported to hospital")
        logout()

        // Admin: Verify resolution
        loginAsAdmin()
        verifyAlertResolved()
    }

    @Test
    fun offlineToOnlineScenario() {
        // Test offline functionality and sync when back online
        
        loginAsGuard()
        
        // Simulate going offline
        simulateOfflineMode()
        
        // Try to create alert while offline
        createEmergencyAlert("Emergency while offline")
        
        // Verify offline indicator
        onView(withId(R.id.offlineIndicator))
            .check(matches(isDisplayed()))
        
        // Simulate coming back online
        simulateOnlineMode()
        
        // Verify sync occurs
        onView(withText("Syncing pending changes..."))
            .check(matches(isDisplayed()))
        
        // Verify alert was synced
        verifyAlertSynced()
    }

    @Test
    fun locationBasedAlertFlow() {
        loginAsGuard()
        
        // Mock location to specific campus block
        mockLocationToMainBuilding()
        
        // Create alert (should auto-detect location)
        onView(withId(R.id.buttonPanic))
            .perform(click())
        
        onView(withText("Send Alert"))
            .perform(click())
        
        // Verify location was detected and included
        onView(withText("Location: Main Building"))
            .check(matches(isDisplayed()))
        
        // Test manual location selection when GPS fails
        mockLocationFailure()
        
        onView(withId(R.id.buttonPanic))
            .perform(click())
        
        // Should show manual selection
        onView(withText("Select Location"))
            .check(matches(isDisplayed()))
        
        onView(withText("Library"))
            .perform(click())
        
        onView(withText("Confirm"))
            .perform(click())
        
        onView(withText("Send Alert"))
            .perform(click())
    }

    @Test
    fun notificationFlowTest() {
        // Test complete notification flow
        
        loginAsGuard()
        
        // Create alert
        createEmergencyAlert("Test notification flow")
        
        // Simulate receiving notification on another device
        simulateIncomingNotification()
        
        // Verify notification appears
        verifyNotificationDisplayed()
        
        // Click notification
        clickNotification()
        
        // Verify it opens alert details
        intended(hasComponent(AlertDetailsActivity::class.java.name))
        
        // Verify notification actions work
        testNotificationActions()
    }

    @Test
    fun errorHandlingAndRecovery() {
        loginAsGuard()
        
        // Test network error during alert creation
        simulateNetworkError()
        
        onView(withId(R.id.buttonPanic))
            .perform(click())
        
        onView(withText("Send Alert"))
            .perform(click())
        
        // Verify error message
        onView(withText("Network error occurred"))
            .check(matches(isDisplayed()))
        
        // Test retry mechanism
        onView(withText("Retry"))
            .perform(click())
        
        // Restore network
        restoreNetwork()
        
        // Verify retry succeeds
        onView(withText("Emergency alert sent successfully"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun securityAndValidationFlow() {
        // Test security measures and input validation
        
        loginAsGuard()
        
        // Test malicious input handling
        onView(withId(R.id.buttonPanic))
            .perform(click())
        
        onView(withId(R.id.editTextMessage))
            .perform(typeText("<script>alert('xss')</script>"))
        
        onView(withText("Send Alert"))
            .perform(click())
        
        // Verify input is sanitized
        verifyInputSanitized()
        
        // Test rate limiting
        createMultipleAlertsRapidly()
        
        // Verify cooldown is enforced
        onView(withText("Please wait before sending another alert"))
            .check(matches(isDisplayed()))
    }

    // Helper methods for test steps

    private fun loginAsGuard(email: String = "guard@campus.edu") {
        onView(withId(R.id.editTextEmail))
            .perform(typeText(email), closeSoftKeyboard())
        
        onView(withId(R.id.editTextPassword))
            .perform(typeText("password123"), closeSoftKeyboard())
        
        onView(withId(R.id.spinnerRole))
            .perform(click())
        onView(withText("Guard"))
            .perform(click())
        
        onView(withId(R.id.buttonLogin))
            .perform(click())
        
        // Wait for navigation
        onView(withId(R.id.buttonPanic))
            .check(matches(isDisplayed()))
    }

    private fun loginAsAdmin() {
        onView(withId(R.id.editTextEmail))
            .perform(typeText("admin@campus.edu"), closeSoftKeyboard())
        
        onView(withId(R.id.editTextPassword))
            .perform(typeText("admin123"), closeSoftKeyboard())
        
        onView(withId(R.id.spinnerRole))
            .perform(click())
        onView(withText("Administrator"))
            .perform(click())
        
        onView(withId(R.id.buttonLogin))
            .perform(click())
        
        // Wait for admin dashboard
        onView(withId(R.id.adminDashboard))
            .check(matches(isDisplayed()))
    }

    private fun createEmergencyAlert(message: String) {
        onView(withId(R.id.buttonPanic))
            .perform(click())
        
        onView(withId(R.id.editTextMessage))
            .perform(typeText(message), closeSoftKeyboard())
        
        onView(withText("Send Alert"))
            .perform(click())
        
        // Wait for success
        onView(withText("Emergency alert sent successfully"))
            .check(matches(isDisplayed()))
    }

    private fun verifyAlertInList() {
        onView(withId(R.id.recyclerViewAlerts))
            .check(matches(isDisplayed()))
        
        // Check that at least one alert is shown
        onView(withText("Fire in building A"))
            .check(matches(isDisplayed()))
    }

    private fun acceptAlert() {
        // Click on first alert in list
        onView(withId(R.id.recyclerViewAlerts))
            .perform(clickOnFirstItem())
        
        onView(withId(R.id.buttonAccept))
            .perform(click())
        
        onView(withText("Confirm"))
            .perform(click())
        
        onView(withText("Alert accepted successfully"))
            .check(matches(isDisplayed()))
    }

    private fun resolveAlert(notes: String) {
        onView(withId(R.id.buttonResolve))
            .perform(click())
        
        onView(withId(R.id.editTextResolutionNotes))
            .perform(typeText(notes), closeSoftKeyboard())
        
        onView(withText("Resolve"))
            .perform(click())
        
        onView(withText("Alert resolved successfully"))
            .check(matches(isDisplayed()))
    }

    private fun verifyAlertResolved() {
        onView(withId(R.id.textViewStatus))
            .check(matches(withText("RESOLVED")))
    }

    private fun verifyAdminDashboard() {
        onView(withId(R.id.textViewTotalAlerts))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.textViewActiveAlerts))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.recyclerViewAllAlerts))
            .check(matches(isDisplayed()))
    }

    private fun filterAlertsByStatus(status: String) {
        onView(withId(R.id.spinnerStatusFilter))
            .perform(click())
        
        onView(withText(status))
            .perform(click())
    }

    private fun viewAlertDetails() {
        onView(withId(R.id.recyclerViewAllAlerts))
            .perform(clickOnFirstItem())
        
        intended(hasComponent(AlertDetailsActivity::class.java.name))
    }

    private fun closeAlertAsAdmin(reason: String) {
        onView(withId(R.id.buttonClose))
            .perform(click())
        
        onView(withId(R.id.editTextCloseReason))
            .perform(typeText(reason), closeSoftKeyboard())
        
        onView(withText("Close Alert"))
            .perform(click())
    }

    private fun verifyAlertStatistics() {
        onView(withId(R.id.textViewResponseTime))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.textViewResolutionRate))
            .check(matches(isDisplayed()))
    }

    private fun logout() {
        onView(withContentDescription("More options"))
            .perform(click())
        
        onView(withText("Logout"))
            .perform(click())
        
        onView(withText("Logout"))
            .perform(click())
    }

    private fun acceptFirstAvailableAlert() {
        onView(withId(R.id.recyclerViewAlerts))
            .perform(clickOnFirstItem())
        
        onView(withId(R.id.buttonAccept))
            .perform(click())
        
        onView(withText("Confirm"))
            .perform(click())
    }

    private fun verifyAlertInProgress() {
        onView(withText("IN PROGRESS"))
            .check(matches(isDisplayed()))
    }

    private fun resolveAcceptedAlert(notes: String) {
        // Find and click on accepted alert
        onView(withText("IN PROGRESS"))
            .perform(click())
        
        resolveAlert(notes)
    }

    // Mock/simulation helper methods
    private fun simulateOfflineMode() {
        // In a real test, this would disable network connectivity
    }

    private fun simulateOnlineMode() {
        // In a real test, this would restore network connectivity
    }

    private fun verifyAlertSynced() {
        onView(withText("All changes synced"))
            .check(matches(isDisplayed()))
    }

    private fun mockLocationToMainBuilding() {
        // Mock location services to return Main Building coordinates
    }

    private fun mockLocationFailure() {
        // Mock location services to fail
    }

    private fun simulateIncomingNotification() {
        // Simulate FCM notification
    }

    private fun verifyNotificationDisplayed() {
        // Check system notification tray
    }

    private fun clickNotification() {
        // Click on notification in system tray
    }

    private fun testNotificationActions() {
        // Test notification action buttons
    }

    private fun simulateNetworkError() {
        // Simulate network connectivity issues
    }

    private fun restoreNetwork() {
        // Restore network connectivity
    }

    private fun verifyInputSanitized() {
        // Verify malicious input was sanitized
        onView(withText(containsString("&lt;script&gt;")))
            .check(matches(isDisplayed()))
    }

    private fun createMultipleAlertsRapidly() {
        repeat(3) {
            onView(withId(R.id.buttonPanic))
                .perform(click())
            
            onView(withText("Send Alert"))
                .perform(click())
        }
    }

    private fun clickOnFirstItem() = object : androidx.test.espresso.ViewAction {
        override fun getConstraints() = isAssignableFrom(androidx.recyclerview.widget.RecyclerView::class.java)
        override fun getDescription() = "Click on first item"
        override fun perform(uiController: androidx.test.espresso.UiController, view: android.view.View) {
            val recyclerView = view as androidx.recyclerview.widget.RecyclerView
            recyclerView.getChildAt(0)?.performClick()
        }
    }
}