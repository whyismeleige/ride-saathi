package com.ridesaathi.app

import android.content.Context
import android.content.Intent
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
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fake redirects and address responses in the isolated QA app; never opens Uber. */
@RunWith(AndroidJUnit4::class)
class SharedLocationFlowTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val releases = mutableListOf<CountDownLatch>()
    private val home = SavedPlace("home", "Home", emptyList(), "Existing home address", 17.385, 78.4867, true)

    @Before fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE).edit().clear().commit()
        LocalStore(context).apply {
            saveProfile(Profile("Share tester", "en", true))
            savePlaces(listOf(home))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val lookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback ->
            callback(PlaceCandidate("", 17.385, 78.4867)); {}
        }
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredField("destinationLocationLookup").apply {
                isAccessible = true
                set(activity, lookup)
            }
        }
    }

    @After fun cleanup() {
        releases.forEach { it.countDown() }
        scenario.close()
    }

    private fun resolver(redirect: (String) -> String?) {
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredField("sharedLocationResolver").apply {
                isAccessible = true
                set(activity, SharedLocationResolver(redirect))
            }
        }
    }

    private fun share(text: String) {
        scenario.onActivity { activity ->
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply {
                isAccessible = true
            }.invoke(activity, intent)
        }
    }

    private fun waitForText(text: String) {
        ui.waitUntil(5_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun shortenedCoordinateLinkOpensDestinationConfirmation() {
        resolver { "https://www.google.com/maps/place/Clinic/@17.4,78.5,15z/data=!3d17.43!4d78.53" }
        share("Clinic\nhttps://maps.app.goo.gl/example")
        waitForText("17.430000, 78.530000")
        ui.onNodeWithText(Words.get("en", "confirm")).assertExists()
        ui.onNodeWithText(Words.get("en", "invalidSharedLocation")).assertDoesNotExist()
    }

    @Test fun addressLinkRequiresChoosingSearchResultBeforeConfirmation() {
        resolver { "https://www.google.com/maps/place/Charminar,+Hyderabad/data=!4m2!3m1!1splace-id" }
        val factory: (PlaceCandidate) -> PlaceSearchProvider = {
            object : PlaceSearchProvider {
                override fun search(query: String, language: String): List<PlaceCandidate> {
                    assertEquals("Charminar", query)
                    return listOf(PlaceCandidate("Charminar monument address", 17.361561, 78.474628))
                }
            }
        }
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredField("searchProvider").apply {
                isAccessible = true
                set(activity, factory)
            }
        }
        share("https://maps.app.goo.gl/example")
        waitForText(Words.get("en", "selectSharedLocation"))
        ui.onNodeWithText("Charminar, Hyderabad").assertExists()
        ui.onNodeWithText(Words.get("en", "confirm")).assertDoesNotExist()
        ui.onAllNodesWithText("Charminar monument address")[0].performClick()
        ui.onNodeWithText(Words.get("en", "confirm")).assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(listOf(home), LocalStore(context).places())
    }

    @Test fun newerShareWinsOverLateRedirectResponse() {
        val release = CountDownLatch(1).also { releases.add(it) }
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        resolver {
            started.countDown()
            while (release.count > 0) {
                try { release.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
            }
            finished.countDown()
            "https://maps.google.com/?q=17.4,78.5"
        }
        share("https://maps.app.goo.gl/old")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        waitForText(Words.get("en", "resolvingSharedLocation"))
        share("https://maps.google.com/?q=18.4,79.5")
        waitForText("18.400000, 79.500000")
        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        ui.waitForIdle()
        ui.onNodeWithText("18.400000, 79.500000").assertExists()
        ui.onNodeWithText("17.400000, 78.500000").assertDoesNotExist()
    }

    @Test fun offlineLinkHasActionableError() {
        resolver { throw IOException("Offline") }
        share("https://maps.app.goo.gl/example")
        waitForText(Words.get("en", "sharedLocationOffline"))
        ui.onNodeWithText(Words.get("en", "invalidSharedLocation")).assertDoesNotExist()
    }
}
