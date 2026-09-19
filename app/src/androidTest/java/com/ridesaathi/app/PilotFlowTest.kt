package com.ridesaathi.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File

/** Runs only in the separate .qa app: never touches the pilot app's saved addresses. */
@RunWith(AndroidJUnit4::class)
class PilotFlowTest {
    @get:Rule val ui = createEmptyComposeRule()
    @get:Rule val testName = TestName()
    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null
    private val home = SavedPlace(id = "home", name = "ghar", aliases = emptyList(),
        address = "Test Home, Hyderabad, Telangana, India", latitude = 17.385, longitude = 78.4867, isHome = true)
    private val son = home.copy(id = "son", name = "beta ka ghar", isHome = false,
        address = "Test Son Address, Hyderabad, Telangana, India")

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa")) { "Tests must run in the isolated QA app" }
        context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun launch(language: String = "en", completed: Boolean = true, saved: List<SavedPlace> = listOf(home, son)) {
        LocalStore(context).apply {
            saveProfile(Profile("Pilot tester", language, completed))
            savePlaces(saved)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            InstrumentationRegistry.getArguments().getString("fontScale")?.toFloatOrNull()?.let {
                assertEquals(it, activity.resources.configuration.fontScale, 0.01f)
            }
        }
        ui.waitForIdle()
    }

    // Exercise the same final-transcript entry point as Android's RecognitionListener.
    // This is deterministic text injection, not a test of the microphone/STT service.
    private fun transcript(text: String) {
        scenario!!.onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("handleSpeech", String::class.java).apply {
                isAccessible = true
            }.invoke(activity, text)
        }
        ui.waitForIdle()
    }

    private fun tap(label: String) { ui.onNodeWithText(label).performScrollTo().performClick() }

    @After fun cleanup() {
        try {
            if (scenario != null) {
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "${testName.methodName}.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
        } finally { scenario?.close() }
    }

    @Test fun hindiSpecificNameWinsAndNegativeReturnsHome() {
        launch("hi")
        transcript("मुझे बेटे के घर जाना है")
        ui.onNodeWithText(Words.get("hi", "confirm")).assertExists()
        ui.onNodeWithText("beta ka ghar").assertExists()
        ui.onNodeWithText(Words.get("hi", "yes")).performScrollTo().assertIsDisplayed()
        transcript("हाँ नहीं")
        ui.onNodeWithText(Words.get("hi", "rideTo")).assertExists()
    }

    @Test fun englishTouchFlowConfirmsAndCancels() {
        launch()
        tap("beta ka ghar")
        ui.onNodeWithText(Words.get("en", "confirm")).assertExists()
        ui.onNodeWithText(Words.get("en", "yes")).performScrollTo().assertIsDisplayed()
        tap(Words.get("en", "no"))
        ui.onNodeWithText(Words.get("en", "rideTo")).assertExists()
    }

    @Test fun separateDestinationsNeedClarification() {
        launch("hi")
        transcript("घर या बेटे के घर")
        ui.onNodeWithText(Words.get("hi", "ambiguous")).assertExists()
        tap("beta ka ghar")
        ui.onNodeWithText(Words.get("hi", "confirm")).assertExists()
    }

    @Test fun changingLanguageKeepsEnglishSavedNamesUsable() {
        launch(saved = listOf(home, son.copy(name = "Doctor")))
        ui.onNodeWithText(Words.get("en", "settings")).performClick()
        tap("हिन्दी")
        ui.onNodeWithText(Words.get("hi", "done")).assertIsDisplayed().performClick()
        transcript("डॉक्टर के पास जाना है")
        ui.onNodeWithText(Words.get("hi", "confirm")).assertExists()
        ui.onNodeWithText("Doctor").assertExists()
    }

    @Test fun onboardingRequiresHomeAndOffersHindi() {
        launch(completed = false, saved = emptyList())
        ui.onNodeWithText(Words.get("en", "finish")).assertDoesNotExist()
        tap("हिन्दी")
        tap(Words.get("hi", "addHome"))
        ui.onNodeWithContentDescription(Words.get("hi", "search")).assertExists()
        ui.onNodeWithText(Words.get("hi", "save")).assertDoesNotExist()
        ui.onNode(hasSetTextAction() and hasText(Words.get("hi", "address"))).assertIsDisplayed()
    }

    @Test fun rotationDoesNotResumePendingRide() {
        launch()
        tap("beta ka ghar")
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitUntil(10_000) { ui.onAllNodesWithText(Words.get("en", "rideTo")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(Words.get("en", "speak")).performScrollTo().assertIsDisplayed()
    }

    @Test fun storageRoundTripAndEncodedUberDestination() {
        val place = son.copy(name = "बेटे का घर & clinic", aliases = listOf("Son's house", "बेटे का घर"))
        val store = LocalStore(context)
        store.savePlaces(listOf(home, place))
        assertEquals(listOf(home, place), store.places())
        val uri = UberHandoff.uri(place, 17.4, 78.5)
        assertEquals("uber", uri.scheme)
        assertEquals(place.name, uri.getQueryParameter("dropoff[nickname]"))
        assertEquals(place.address, uri.getQueryParameter("dropoff[formatted_address]"))
        assertEquals("17.4", uri.getQueryParameter("pickup[latitude]"))
        assertEquals(place.longitude.toString(), uri.getQueryParameter("dropoff[longitude]"))
        assertEquals(UberHandoff.packageName, UberHandoff.intent(place, 17.4, 78.5).`package`)
    }
}
