package com.ridesaathi.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ridesaathi.app.core.deeplink.UberHandoff
import com.ridesaathi.app.data.local.LocalStore
import com.ridesaathi.app.domain.model.PlaceCandidate
import com.ridesaathi.app.feature.places.PlaceEditorAction
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.localization.Words
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/** Runs only in the separate .qa app: never touches the pilot app's saved addresses. */
@RunWith(AndroidJUnit4::class)
class PilotFlowTest {
    @get:Rule
    val ui = createEmptyComposeRule()
    @get:Rule
    val testName = TestName()
    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null
    private val home = SavedPlace(
        id = "home",
        name = "ghar",
        aliases = emptyList(),
        address = "Test Home, Hyderabad, Telangana, India",
        latitude = 17.385,
        longitude = 78.4867,
        isHome = true
    )
    private val son = home.copy(
        id = "son", name = "beta ka ghar", isHome = false,
        address = "Test Son Address, Hyderabad, Telangana, India"
    )

    @Before
    fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa")) { "Tests must run in the isolated QA app" }
        context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun launch(
        language: String = "en",
        completed: Boolean = true,
        saved: List<SavedPlace> = listOf(home, son)
    ) {
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
            activity.session.voice.handleSpeech(text)
        }
        ui.waitForIdle()
    }

    private fun tap(label: String) {
        ui.onNodeWithText(label).performScrollTo().performClick()
    }

    @After
    fun cleanup() {
        try {
            if (scenario != null) {
                val screenshot =
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "${testName.methodName}.png").outputStream()
                    .use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                screenshot.recycle()
            }
        } finally {
            scenario?.close()
        }
    }

    @Test
    fun hindiSpecificNameWinsAndNegativeReturnsHome() {
        launch("hi")
        transcript("मुझे बेटे के घर जाना है")
        ui.onNodeWithText(Words.get("hi", "confirm")).assertExists()
        ui.onNodeWithText("beta ka ghar").assertExists()
        ui.onNodeWithContentDescription(Words.get("hi", "speak")).assertIsDisplayed()
        transcript("हाँ नहीं")
        ui.onNodeWithText(Words.get("hi", "rideTo")).assertExists()
    }

    @Test
    fun englishTouchSelectionAllowsVoiceCancellation() {
        launch()
        tap("beta ka ghar")
        ui.onNodeWithText(Words.get("en", "confirm")).assertExists()
        ui.onNodeWithContentDescription(Words.get("en", "speak")).assertIsDisplayed()
        transcript("no")
        ui.onNodeWithText(Words.get("en", "rideTo")).assertExists()
    }

    @Test
    fun separateDestinationsNeedClarification() {
        launch("hi")
        transcript("घर या बेटे के घर")
        ui.onNodeWithText(Words.get("hi", "ambiguous")).assertExists()
        tap("beta ka ghar")
        ui.onNodeWithText(Words.get("hi", "confirm")).assertExists()
    }

    @Test
    fun changingLanguageKeepsEnglishSavedNamesUsable() {
        launch(saved = listOf(home, son.copy(name = "Doctor")))
        ui.onNodeWithText(Words.get("en", "settings")).performClick()
        tap("English")
        ui.onNodeWithText("हिन्दी").performClick()
        ui.onNodeWithText(Words.get("hi", "done")).assertIsDisplayed().performClick()
        transcript("डॉक्टर के पास जाना है")
        ui.onNodeWithText(Words.get("hi", "confirm")).assertExists()
        ui.onNodeWithText("Doctor").assertExists()
    }

    @Test
    fun settingsDropdownPersistsTeluguAndCanSwitchBackToEnglish() {
        launch()
        ui.onNodeWithText(Words.get("en", "settings")).performClick()
        ui.onNodeWithText("తెలుగు").assertDoesNotExist()
        tap("English")
        ui.onNodeWithText("తెలుగు").performClick()
        ui.onNodeWithText(Words.get("te", "language")).assertExists()
        ui.onNodeWithText("English").assertDoesNotExist()
        assertEquals("te", LocalStore(context).profile().language)
        scenario!!.recreate()
        ui.waitForIdle()
        ui.onNodeWithText(Words.get("te", "rideTo")).assertExists()
        transcript("నన్ను ఇంటికి తీసుకెళ్లండి")
        ui.onNodeWithText(Words.get("te", "confirm")).assertExists()
        transcript("కాదు")
        ui.onNodeWithText(Words.get("te", "settings")).performClick()
        tap("తెలుగు")
        ui.onNodeWithText("English").performClick()
        ui.onNodeWithText(Words.get("en", "language")).assertExists()
        assertEquals("en", LocalStore(context).profile().language)
    }

    private fun completeIntroduction(language: String) {
        val titles = listOf("introAboutTitle", "introFamilyTitle", "introPlacesTitle", "tutorialSpeakTitle",
            "tutorialConfirmTitle", "tutorialPickupTitle", "tutorialUberTitle")
        titles.forEach { title ->
            ui.onNodeWithText(Words.get(language, title)).assertExists()
            ui.onNodeWithText(Words.get(language, "continue")).performClick()
            ui.waitForIdle()
        }
        ui.onNodeWithText(Words.get(language, "onboardingNameTitle")).assertExists()
    }

    private fun captureSetup(stage: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "onboarding-$stage.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun onboardingOffersTelugu() {
        launch(completed = false, saved = emptyList())
        tap("తెలుగు")
        ui.onNodeWithText(Words.get("te", "language")).assertExists()
        assertEquals("te", LocalStore(context).profile().language)
        ui.onNodeWithText(Words.get("te", "continue")).performClick()
        completeIntroduction("te")
        ui.onNodeWithText(Words.get("te", "continue")).performClick()
        ui.onNodeWithText(Words.get("te", "addHome")).performClick()
        ui.onNodeWithContentDescription(Words.get("te", "search")).assertExists()
    }

    @Test
    fun onboardingRequiresHomeAndOffersHindi() {
        launch(completed = false, saved = emptyList())
        ui.onNodeWithText(Words.get("en", "finish")).assertDoesNotExist()
        ui.onNodeWithText(Words.get("en", "name")).assertDoesNotExist()
        tap("हिन्दी")
        ui.onNodeWithText(Words.get("hi", "continue")).performClick()
        completeIntroduction("hi")
        ui.onNodeWithText(Words.get("hi", "continue")).performClick()
        ui.onNodeWithText(Words.get("hi", "addHome")).performClick()
        ui.onNodeWithContentDescription(Words.get("hi", "search")).assertExists()
        ui.onNodeWithText(Words.get("hi", "save")).assertDoesNotExist()
        ui.onNode(hasSetTextAction() and hasText(Words.get("hi", "address"))).assertIsDisplayed()
    }

    @Test
    fun guidedSetupPreservesProgressAndSavedPlaces() {
        launch(completed = false, saved = emptyList())
        captureSetup("language")
        ui.onNodeWithText(Words.get("en", "continue")).performClick()
        captureSetup("introduction")
        // Both system and visible Back traverse setup without marking it complete.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText(Words.get("en", "language")).assertExists()
        ui.onNodeWithText(Words.get("en", "continue")).performClick()
        completeIntroduction("en")
        assertTrue(LocalStore(context).profile().tutorialSeen)
        ui.onNode(hasSetTextAction()).performTextClearance()
        ui.onNodeWithText(Words.get("en", "continue")).assertIsNotEnabled()
        ui.onNode(hasSetTextAction()).performTextInput("Ananya")
        scenario!!.recreate()
        ui.waitForIdle()
        ui.onNodeWithText(Words.get("en", "onboardingNameTitle")).assertExists()
        ui.onNode(hasSetTextAction()).assertTextContains("Ananya")
        captureSetup("name")
        ui.onNodeWithText(Words.get("en", "continue")).performClick()
        captureSetup("home")
        ui.onNodeWithText(Words.get("en", "addHome")).performClick()
        ui.onNodeWithText(Words.get("en", "back")).performClick()
        ui.onNodeWithText(Words.get("en", "onboardingHomeTitle")).assertExists()
        ui.onNodeWithText(Words.get("en", "addHome")).performClick()
        scenario!!.onActivity {
            it.session.editor.onAction(PlaceEditorAction.SelectAddress(PlaceCandidate(home.address, home.latitude, home.longitude)))
            it.session.editor.onAction(PlaceEditorAction.Save)
        }
        ui.onNodeWithText(Words.get("en", "onboardingHomeSaved")).assertExists()
        ui.onNodeWithText(Words.get("en", "continue")).performClick()
        captureSetup("places")
        assertFalse(LocalStore(context).profile().completed)
        tap(Words.get("en", "addPlace"))
        scenario!!.onActivity {
            it.session.editor.onAction(PlaceEditorAction.SelectAddress(PlaceCandidate(son.address, son.latitude, son.longitude)))
            it.session.editor.onAction(PlaceEditorAction.Rename("Family"))
            it.session.editor.onAction(PlaceEditorAction.Save)
        }
        ui.onNodeWithText("Family").assertExists()
        scenario!!.recreate()
        ui.waitForIdle()
        ui.onNodeWithText(Words.get("en", "onboardingPlacesTitle")).assertExists()
        ui.onNodeWithText("Family").assertExists()
        ui.onNodeWithText(Words.get("en", "finish")).performClick()
        ui.onNodeWithText(Words.get("en", "rideTo")).assertExists()
        assertTrue(LocalStore(context).profile().completed)
        assertEquals(2, LocalStore(context).places().size)
    }

    @Test
    fun extraPlacesAreOptional() {
        launch(completed = false, saved = listOf(home))
        ui.onNodeWithText(Words.get("en", "continue")).performClick()
        completeIntroduction("en")
        repeat(2) { ui.onNodeWithText(Words.get("en", "continue")).performClick(); ui.waitForIdle() }
        ui.onNodeWithText(Words.get("en", "finish")).performClick()
        ui.onNodeWithText(Words.get("en", "rideTo")).assertExists()
        assertEquals(listOf(home), LocalStore(context).places())
    }

    @Test
    fun completedProfileWithMissingHomeReturnsToSetupAfterSaving() {
        launch(completed = true, saved = emptyList())
        ui.onNodeWithText(Words.get("en", "addHome")).performClick()
        scenario!!.onActivity {
            it.session.editor.onAction(PlaceEditorAction.SelectAddress(PlaceCandidate(home.address, home.latitude, home.longitude)))
            it.session.editor.onAction(PlaceEditorAction.Save)
        }
        ui.onNodeWithText(Words.get("en", "onboardingHomeSaved")).assertExists()
        ui.onNodeWithText(Words.get("en", "continue")).performClick()
        ui.onNodeWithText(Words.get("en", "finish")).performClick()
        // Existing profiles that never saw the ride tutorial still receive it.
        ui.onNodeWithText(Words.get("en", "tutorialSpeakTitle")).assertExists()
    }

    @Test
    fun rotationDoesNotResumePendingRide() {
        launch()
        tap("beta ka ghar")
        scenario!!.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText(Words.get("en", "rideTo")).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription(Words.get("en", "speak")).assertIsDisplayed()
    }

    @Test
    fun storageRoundTripAndEncodedUberDestination() {
        val place =
            son.copy(name = "बेटे का घर & clinic", aliases = listOf("Son's house", "बेटे का घर"))
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
