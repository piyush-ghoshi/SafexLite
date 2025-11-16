package com.campus.panicbutton.ui

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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertFlowTest {

    @get:Rule
    val activityRule = ActivityTestRule(GuardDashboardActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
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
    fun completeAlertCreationFlow() {
        // Step 1: Click panic button
        onView(withId(R.id.buttonPanic))
            .perform(click())

        // Step 2: Verify confirmation dialog
        onView(withText("Confirm Emergency Alert"))
            .check(matches(isDisplayed()))

        // Step 3: Add optional message
        onView(withId(R.id.editTextMessage))
            .perform(typeText("Fire in building"), closeSoftKeyboard())

        // Step 4: Confirm alert creation
        onView(withText("Send Alert"))
            .perform(click())

        // Step 5: Verify loading state
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))

        // Step 6: Verify success message (after loading completes)
        onView(withText("Emergency alert sent successfully"))
            .check(matches(isDisplayed()))

        // Step 7: Verify button returns to normal state
        onView(withId(R.id.buttonPanic))
            .check(matches(isEnabled()))
    }

    @Test
    fun alertCreationWithLocationFailure() {
        // Simulate location failure scenario
        // This would require mocking location services

        onView(withId(R.id.buttonPanic))
            .perform(click())

        // Should show manual block selection dialog
        onView(withText("Select Location"))
            .check(matches(isDisplayed()))

        // Select a campus block manually
        onView(withText("Main Building"))
            .perform(click())

        onView(withText("Confirm"))
            .perform(click())

        // Continue with alert creation
        onView(withText("Send Alert"))
            .perform(click())

        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun alertAcceptanceFlow() {
        // This test assumes there's an active alert in the list
        // In practice, you'd set up test data first

        // Step 1: Click on an alert item to view details
        onView(withId(R.id.recyclerViewAlerts))
            .perform(clickOnFirstItem())

        // Step 2: Verify navigation to AlertDetailsActivity
        intended(hasComponent(AlertDetailsActivity::class.java.name))

        // Step 3: Accept the alert
        onView(withId(R.id.buttonAccept))
            .check(matches(isDisplayed()))
            .perform(click())

        // Step 4: Confirm acceptance
        onView(withText("Accept Alert"))
            .check(matches(isDisplayed()))

        onView(withText("Confirm"))
            .perform(click())

        // Step 5: Verify loading state
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))

        // Step 6: Verify success and status change
        onView(withText("Alert accepted successfully"))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewStatus))
            .check(matches(withText("IN PROGRESS")))
    }

    @Test
    fun alertResolutionFlow() {
        // Test resolving an accepted alert
        
        // Navigate to an in-progress alert
        onView(withId(R.id.recyclerViewAlerts))
            .perform(clickOnFirstItem())

        // Resolve the alert
        onView(withId(R.id.buttonResolve))
            .check(matches(isDisplayed()))
            .perform(click())

        // Add resolution notes
        onView(withId(R.id.editTextResolutionNotes))
            .perform(typeText("Situation resolved, all clear"), closeSoftKeyboard())

        onView(withText("Resolve"))
            .perform(click())

        // Verify loading and success
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))

        onView(withText("Alert resolved successfully"))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewStatus))
            .check(matches(withText("RESOLVED")))
    }

    @Test
    fun alertCreationFailureHandling() {
        // Test network failure scenario
        
        onView(withId(R.id.buttonPanic))
            .perform(click())

        onView(withText("Send Alert"))
            .perform(click())

        // Simulate network error (would require mocking)
        // Verify error handling
        onView(withText("Failed to send alert"))
            .check(matches(isDisplayed()))

        onView(withText("Retry"))
            .check(matches(isDisplayed()))
            .perform(click())

        // Verify retry attempt
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun alertCreationCooldownPrevention() {
        // Test that multiple rapid alert creations are prevented
        
        // Create first alert
        onView(withId(R.id.buttonPanic))
            .perform(click())

        onView(withText("Send Alert"))
            .perform(click())

        // Wait for completion
        onView(withText("Emergency alert sent successfully"))
            .check(matches(isDisplayed()))

        // Try to create another alert immediately
        onView(withId(R.id.buttonPanic))
            .perform(click())

        // Should show cooldown message
        onView(withText("Please wait before sending another alert"))
            .check(matches(isDisplayed()))

        // Button should be disabled
        onView(withId(R.id.buttonPanic))
            .check(matches(isNotEnabled()))
    }

    @Test
    fun alertDetailsViewFlow() {
        // Test viewing alert details without actions
        
        onView(withId(R.id.recyclerViewAlerts))
            .perform(clickOnFirstItem())

        // Verify alert details are displayed
        onView(withId(R.id.textViewGuardName))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewTimestamp))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewLocation))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewMessage))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewStatus))
            .check(matches(isDisplayed()))

        // Verify timeline is shown
        onView(withId(R.id.recyclerViewTimeline))
            .check(matches(isDisplayed()))
    }

    @Test
    fun alertNotificationHandling() {
        // Test handling incoming alert notifications
        // This would require simulating FCM notifications
        
        // Simulate receiving a notification
        // In practice, you'd use Firebase Test Lab or mock FCM
        
        // Verify notification appears in system tray
        // Verify clicking notification opens AlertDetailsActivity
        // Verify notification content is correct
    }

    private fun clickOnFirstItem() = object : ViewAction {
        override fun getConstraints() = isAssignableFrom(androidx.recyclerview.widget.RecyclerView::class.java)
        override fun getDescription() = "Click on first item in RecyclerView"
        override fun perform(uiController: UiController, view: View) {
            val recyclerView = view as androidx.recyclerview.widget.RecyclerView
            if (recyclerView.adapter?.itemCount ?: 0 > 0) {
                val firstChild = recyclerView.getChildAt(0)
                firstChild?.performClick()
            }
        }
    }
}