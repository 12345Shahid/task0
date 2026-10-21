package com.rcl.androidstreamcontrol;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class RemoteAndroidTest {

    @Test
    public void testJarvisTextInputEndToEnd() {
        // Step 1: Launch the Main Jarvis UI Activity
        try (ActivityScenario<RemoteAndroid> scenario = ActivityScenario.launch(RemoteAndroid.class)) {
            
            // The command we want to test
            String testCommand = "Hello Jarvis, execute test protocol";

            // Step 2: Type the command into the input box
            onView(withId(R.id.command_input))
                    .perform(typeText(testCommand), closeSoftKeyboard());

            // Step 3: Click the Send (Pink Arrow) button
            onView(withId(R.id.send_command_button))
                    .perform(click());

            // Step 4: Verify the Hacker Console processed the text and printed it
            // We check if the status_console TextView contains our expected output
            onView(withId(R.id.status_console))
                    .check(matches(withText(containsString("> CMD_RCV: " + testCommand))));
        }
    }
}
