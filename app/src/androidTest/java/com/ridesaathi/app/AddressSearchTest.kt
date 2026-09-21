package com.ridesaathi.app

import android.content.Context
import android.os.SystemClock
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses fake search responses and the separate QA app; no address service requests. */
@RunWith(AndroidJUnit4::class)
class AddressSearchTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val queries = CopyOnWriteArrayList<String>()
    private val releases = mutableListOf<CountDownLatch>()
    private val home = SavedPlace("home", "Home", emptyList(), "Existing home address", 17.385, 78.4867, true)

    @Before fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE).edit().clear().commit()
        LocalStore(context).apply {
            saveProfile(Profile("Search tester", "en", true))
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
        ui.onNodeWithText("Settings").performClick()
    }

    @After fun cleanup() {
        releases.forEach { it.countDown() }
        scenario.close()
    }

    private fun provider(search: (String) -> List<PlaceCandidate>) {
        val factory: (PlaceCandidate) -> PlaceSearchProvider = {
            object : PlaceSearchProvider {
                override fun search(query: String, language: String): List<PlaceCandidate> {
                    queries.add(query)
                    return search(query)
                }
            }
        }
        scenario.onActivity {
            MainActivity::class.java.getDeclaredField("searchProvider").apply {
                isAccessible = true
                set(it, factory)
            }
        }
    }

    private fun addPlace() = ui.onNodeWithText("Add saved place").assertIsDisplayed().performClick()
    private fun address() = ui.onNode(hasSetTextAction() and hasText("Address"))
    private fun type(query: String) = address().performClick().performTextReplacement(query)
    private fun waitForText(text: String) {
        ui.waitUntil(5_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun addressSearchUsesCurrentLocationAndRejectsDistantResults() {
        val current = PlaceCandidate("", 19.076, 72.8777)
        val local = PlaceCandidate("Clinic, Mumbai", 19.08, 72.89)
        val lookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback -> callback(current); {} }
        val factory: (PlaceCandidate) -> PlaceSearchProvider = { center ->
            assertEquals(current, center)
            object : PlaceSearchProvider {
                override fun search(query: String, language: String) = listOf(
                    PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5), local,
                    PlaceCandidate("Clinic, Delhi", 28.61, 77.21))
            }
        }
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredField("destinationLocationLookup").apply {
                isAccessible = true; set(activity, lookup)
            }
            MainActivity::class.java.getDeclaredField("searchProvider").apply {
                isAccessible = true; set(activity, factory)
            }
        }
        addPlace()
        type("Clinic")
        waitForText(local.address)
        ui.onNodeWithText("Clinic, Hyderabad").assertDoesNotExist()
        ui.onNodeWithText("Clinic, Delhi").assertDoesNotExist()
    }

    @Test fun missingLocationDoesNotStartAddressSearch() {
        val lookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback -> callback(null); {} }
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredField("destinationLocationLookup").apply {
                isAccessible = true; set(activity, lookup)
            }
        }
        provider { error("Search must wait for a current location") }
        addPlace()
        type("Clinic")
        waitForText(Words.get("en", "searchLocationRequired"))
        assertTrue(queries.isEmpty())
        ui.onNodeWithText(Words.get("en", "retry")).assertExists()
    }

    @Test fun typingDebouncesAndShowsLoadingThenSelectableResults() {
        val release = CountDownLatch(1).also { releases.add(it) }
        provider { query ->
            check(release.await(5, TimeUnit.SECONDS))
            listOf(PlaceCandidate("$query result", 17.4, 78.5))
        }
        addPlace()
        ui.onNodeWithText("Search address").assertIsDisplayed()
        ui.onNode(hasSetTextAction() and hasText("Place name")).assertDoesNotExist()
        ui.onNodeWithText("Voice names (comma separated)").assertDoesNotExist()
        type("Hy")
        SystemClock.sleep(650)
        assertTrue(queries.isEmpty())
        address().performTextReplacement("Hyd")
        address().performTextReplacement("Hyderabad")
        ui.waitUntil(5_000) { queries.isNotEmpty() }
        assertEquals(listOf("Hyderabad"), queries.toList())
        ui.onNodeWithText("Searching addresses…").assertExists()
        ui.onNodeWithText("Save place").assertDoesNotExist()
        release.countDown()
        waitForText("Hyderabad result")
        ui.onNodeWithText("Hyderabad result").assertIsDisplayed().performClick()
        ui.onNodeWithText("Hyderabad result").assertIsDisplayed()
        ui.onNodeWithText("Save place").assertIsNotEnabled()
        ui.onNode(hasSetTextAction() and hasText("Place name")).performTextInput("Clinic")
        ui.onNodeWithText("Save place").assertIsEnabled()
        SystemClock.sleep(650)
        assertEquals(1, queries.size)
    }

    @Test fun clearingAndLeavingEditorCancelPendingSearches() {
        provider { emptyList() }
        addPlace()
        type("Hyderabad")
        address().performTextClearance()
        SystemClock.sleep(650)
        assertTrue(queries.isEmpty())
        ui.onNodeWithText("Searching addresses…").assertDoesNotExist()
        type("Mumbai")
        ui.onAllNodesWithText("Back")[0].performClick()
        SystemClock.sleep(650)
        assertTrue(queries.isEmpty())
    }

    @Test fun cancellingAddressChangeKeepsPreviousSelection() {
        provider { emptyList() }
        ui.onNodeWithText("Home").performScrollTo().performClick()
        SystemClock.sleep(650)
        assertTrue(queries.isEmpty())
        ui.onNodeWithText("Save place").assertIsEnabled()
        ui.onNodeWithText("Change address").performScrollTo().performClick()
        type("New home")
        ui.onNodeWithText("Save place").assertDoesNotExist()
        waitForText(Words.get("en", "noResults"))
        assertEquals(listOf("New home"), queries.toList())
        ui.onNodeWithText("Searching addresses…").assertDoesNotExist()
        ui.onNodeWithText("Back").performClick()
        ui.onNodeWithText(home.address).assertIsDisplayed()
        ui.onNodeWithText("Save place").assertIsEnabled()
    }

    @Test fun failedSearchCanRetryFromSmallIcon() {
        provider { if (queries.size == 1) throw IOException("Offline") else emptyList() }
        addPlace()
        type("Hyderabad")
        waitForText(Words.get("en", "offline"))
        ui.onNodeWithContentDescription("Search address").performClick()
        waitForText(Words.get("en", "noResults"))
        assertEquals(listOf("Hyderabad", "Hyderabad"), queries.toList())
        ui.onNodeWithText("Searching addresses…").assertDoesNotExist()
    }

    @Test fun providerSetupAndQuotaErrorsHaveActionableMessages() {
        var reason = PlaceSearchFailure.NOT_CONFIGURED
        provider { throw PlaceSearchException(reason) }
        addPlace()
        type("Clinic")
        waitForText(Words.get("en", "configured"))
        reason = PlaceSearchFailure.ACCESS_DENIED
        ui.onNodeWithContentDescription("Search address").performClick()
        waitForText(Words.get("en", "searchAccessDenied"))
        reason = PlaceSearchFailure.QUOTA
        ui.onNodeWithContentDescription("Search address").performClick()
        waitForText(Words.get("en", "searchQuota"))
        ui.onNodeWithText(Words.get("en", "offline")).assertDoesNotExist()
    }

    @Test fun selectedAddressShowsMapAndCanBeSavedWithoutScrollingToSave() {
        provider { listOf(PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5)) }
        addPlace()
        type("Clinic")
        waitForText("Clinic, Hyderabad")
        ui.onNodeWithText("Clinic, Hyderabad").assertIsDisplayed().performClick()
        ui.onNodeWithText("Save place").assertIsDisplayed().assertIsNotEnabled()
        ui.onNode(hasSetTextAction() and hasText("Place name")).performTextInput("Doctor")
        ui.onNodeWithText("Voice names (comma separated)").assertDoesNotExist()
        ui.onNodeWithText("Map data © OpenStreetMap").assertExists()
        ui.onNodeWithText("Save place").assertIsDisplayed().assertIsEnabled().performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = LocalStore(context).places().single { !it.isHome }
        assertEquals("Doctor", saved.name)
        assertEquals("Clinic, Hyderabad", saved.address)
        assertTrue(saved.aliases.isEmpty())
    }

    @Test fun editingPlacePreservesLegacyVoiceAliases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clinic = home.copy(id = "clinic", name = "Doctor", isHome = false,
            aliases = listOf("Hospital", "अस्पताल"))
        LocalStore(context).savePlaces(listOf(home, clinic))
        scenario.recreate()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Doctor").performScrollTo().performClick()
        ui.onNode(hasSetTextAction() and hasText("Place name")).performTextReplacement("Clinic")
        ui.onNodeWithText("Save place").assertIsDisplayed().performClick()
        val saved = LocalStore(context).places().single { it.id == clinic.id }
        assertEquals("Clinic", saved.name)
        assertEquals(clinic.aliases, saved.aliases)
    }

    @Test fun lateResponseCannotReplaceNewQueryResults() {
        val release = CountDownLatch(1).also { releases.add(it) }
        val oldFinished = CountDownLatch(1)
        provider { query ->
            if (query == "Old address") {
                // Simulate a network operation that does not honor cancellation.
                while (release.count > 0) {
                    try { release.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
                }
                oldFinished.countDown()
            }
            listOf(PlaceCandidate("$query result", 17.4, 78.5))
        }
        addPlace()
        type("Old address")
        ui.waitUntil(5_000) { queries.contains("Old address") }
        type("New address")
        waitForText("New address result")
        release.countDown()
        assertTrue(oldFinished.await(5, TimeUnit.SECONDS))
        ui.waitForIdle()
        ui.onNodeWithText("New address result").assertExists()
        ui.onNodeWithText("Old address result").assertDoesNotExist()
    }
}
