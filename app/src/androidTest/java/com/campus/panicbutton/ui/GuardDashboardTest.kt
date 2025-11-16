package com.campus.panicbutton.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.campus.panicbutton.R
import com.campus.panicbutton.activities.GuardDashboardActivity
import com.campus.panicbutton.adapters.AlertsAdapter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuardDashboardTest {

    @get:Rule
    val activityRule = ActivityTestRule(GuardDashboardActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun guardDashboard_displaysCorrectly() {
        // Check that main UI elements are displayed
        onView(withId(R.id.buttonPanic))
            .check(matches(isDisplayed()))
            .check(matches(withText("EMERGENCY")))

        onView(withId(R.id.recyclerViewAlerts))
            .check(matches(isDisplayed()))

        onView(withId(R.id.textViewLocationStatus))
            .check(matches(isDisplayed()))
    }

    @Test
    fun guardDashboard_panicButtonIsProminent() {
        // Check panic button styling and size
        onView(withId(R.id.buttonPanic))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
            .check(matches(isEnabled()))
    }

    @Test
    fun guardDashboard_panicButtonClick() {
        // Click the panic button
        onView(withId(R.id.buttonPanic))
            .perform(click())

        // Check that confirmation dialog appears
        onView(withText("Confirm Emergency Alert"))
            .check(matches(isDisplayed()))

        onView(withText("Are you sure you want to send an emergency alert?"))
            .check(matches(isDisplayed()))

        // Check dialog buttons
        onView(withText("Cancel"))
            .check(matches(isDisplayed()))

        onView(withText("Send Alert"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun guardDashboard_panicButtonConfirmation() {
        // Click panic button and confirm
        onView(withId(R.id.buttonPanic))
            .perform(click())

        onView(withText("Send Alert"))
            .perform(click())

        // Check that loading indicator appears
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun guardDashboard_panicButtonCancel() {
        // Click panic button and cancel
        onView(withId(R.id.buttonPanic))
            .perform(click())

        onView(withText("Cancel"))
            .perform(click())

        // Check that we're back to normal state
        onView(withId(R.id.buttonPanic))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun guardDashboard_alertsList() {
        // Check that alerts RecyclerView is present
        onView(withId(R.id.recyclerViewAlerts))
            .check(matches(isDisplayed()))

        // Check empty state message when no alerts
        onView(withId(R.id.textViewEmptyState))
            .check(matches(withText("No active alerts")))
    }

    @Test
    fun guardDashboard_alertItemClick() {
        // This test assumes there are alerts in the list
        // In a real test, you would mock the data or use test data

        // Click on first alert item (if exists)
        onView(withId(R.id.recyclerViewAlerts))
            .perform(RecyclerViewActions.actionOnItemAtPosition<AlertsAdapter.AlertViewHolder>(0, click()))

        // This would navigate to AlertDetailsActivity
        // Check would depend on your navigation implementation
    }

    @Test
    fun guardDashboard_acceptAlertAction() {
        // Test accepting an alert from the list
        // This assumes alerts are displayed with action buttons

        onView(withId(R.id.recyclerViewAlerts))
            .perform(RecyclerViewActions.actionOnItemAtPosition<AlertsAdapter.AlertViewHolder>(
                0, 
                clickChildViewWithId(R.id.buttonAccept)
            ))

        // Check confirmation dialog
        onView(withText("Accept Alert"))
            .check(matches(isDisplayed()))

        onView(withText("Confirm"))
            .perform(click())

        // Check loading state
        onView(withId(R.id.progressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun guardDashboard_locationStatus() {
        // Check location status display
        onView(withId(R.id.textViewLocationStatus))
            .check(matches(isDisplayed()))

        // Location status should show one of: "Getting location...", "Location: [Block Name]", "Location unavailable"
        onView(withId(R.id.textViewLocationStatus))
            .check(matches(anyOf(
                withText(containsString("Getting location")),
                withText(containsString("Location:")),
                withText(containsString("Location unavailable"))
            )))
    }

    @Test
    fun guardDashboard_refreshAlerts() {
        // Test pull-to-refresh functionality
        onView(withId(R.id.swipeRefreshLayout))
            .perform(swipeDown())

        // Check that refresh indicator appears
        onView(withId(R.id.swipeRefreshLayout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun guardDashboard_menuOptions() {
        // Test menu options
        onView(withContentDescription("More options"))
            .perform(click())

        // Check menu items
        onView(withText("Logout"))
            .check(matches(isDisplayed()))

        onView(withText("Settings"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun guardDashboard_logoutFlow() {
        // Test logout functionality
        onView(withContentDescription("More options"))
            .perform(click())

        onView(withText("Logout"))
            .perform(click())

        // Check logout confirmation
        onView(withText("Confirm Logout"))
            .check(matches(isDisplayed()))

        onView(withText("Logout"))
            .perform(click())

        // This would navigate back to LoginActivity
        // Verification depends on your navigation implementation
    }

    // Helper function to click child view in RecyclerView item
    private fun clickChildViewWithId(id: Int) = object : ViewAction {
        override fun getConstraints() = null
        override fun getDescription() = "Click child view with id $id"
        override fun perform(uiController: UiController, view: View) {
            val childView = view.findViewById<View>(id)
            childView.performClick()
        }
    }
}