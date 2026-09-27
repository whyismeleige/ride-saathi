package com.ridesaathi.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Fake search/location and injected transcripts; never books a ride or calls Ola. */
@RunWith(AndroidJUnit4::class)
class DestinationSearchFlowTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var context: Context
    private val home = SavedPlace("home", "Home", emptyList(), "Home address", 17.38, 78.48, true)
    private val candidates = listOf(
        PlaceCandidate("Apollo Hospital, Jubilee Hills", 17.4, 78.4),
        PlaceCandidate("Apollo Hospital, Secunderabad", 17.5, 78.5),
        PlaceCandidate("City Clinic, Hyderabad", 17.6, 78.6),
        PlaceCandidate("Fourth Clinic, Hyderabad", 17.7, 78.7)
    )
    private val releases = mutableListOf<CountDownLatch>()

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE).edit().clear().commit()
        LocalStore(context).apply { saveProfile(Profile("Search tester", "en", true)); savePlaces(listOf(home)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        location(PlaceCandidate("", 17.38, 78.48))
        provider { _, _ -> candidates }
    }

    @After fun cleanup() { releases.forEach { it.countDown() }; scenario.close() }

    private fun field(activity: MainActivity, name: String): Any? = try {
        MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    } catch (_: NoSuchFieldException) {
        (MainActivity::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }.get(activity) as MutableState<*>).value
    }
    @Suppress("UNCHECKED_CAST")
    private fun set(activity: MainActivity, name: String, value: Any?) {
        try {
            MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
        } catch (_: NoSuchFieldException) {
            (MainActivity::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }.get(activity) as MutableState<Any?>).value = value
        }
    }
    private fun call(activity: MainActivity, name: String) {
        MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }
    private fun location(candidate: PlaceCandidate?) {
        val lookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback -> callback(candidate); {} }
        scenario.onActivity { set(it, "destinationLocationLookup", lookup) }
    }
    private fun provider(search: (String, String) -> List<PlaceCandidate>) {
        val factory: (PlaceCandidate) -> PlaceSearchProvider = {
            object : PlaceSearchProvider { override fun search(query: String, language: String) = search(query, language) }
        }
        scenario.onActivity { set(it, "destinationSearchProvider", factory) }
    }
    private fun say(text: String) {
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("handleSpeech", String::class.java)
                .apply { isAccessible = true }.invoke(activity, text)
        }
    }
    private fun waitText(text: String) {
        ui.waitUntil(8_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tap(text: String) { ui.onNodeWithText(text).performScrollTo().performClick() }
    private fun start() { say("take me to Apollo Hospital"); waitText("1. ${candidates[0].address}") }

    @Test fun savedMatchAndSavedAmbiguityBypassSearch() {
        val calls = AtomicInteger()
        provider { _, _ -> calls.incrementAndGet(); candidates }
        say("Home")
        waitText(Words.get("en", "confirm"))
        say("no")
        scenario.onActivity { set(it, "places", listOf(home, home.copy(id = "other", name = "Other Home", isHome = false))) }
        say("Home or Other Home")
        waitText(Words.get("en", "ambiguous"))
        assertEquals(0, calls.get())
    }

    @Test fun numberedSelectionRequiresConfirmationAndDoesNotSave() {
        start()
        ui.onNodeWithText(Words.get("en", "confirm")).assertDoesNotExist()
        say("second one")
        waitText(Words.get("en", "confirm"))
        scenario.onActivity { assertEquals(candidates[1].address, (field(it, "selected") as SavedPlace).address) }
        assertEquals(listOf(home), LocalStore(context).places())
        say("no")
        waitText("1. ${candidates[0].address}")
        say("cancel")
        waitText(Words.get("en", "rideTo"))
    }

    @Test fun singletonStillRequiresTapAndBackRestoresChoices() {
        provider { _, _ -> candidates.take(1) }
        start()
        ui.onNodeWithText(Words.get("en", "confirm")).assertDoesNotExist()
        tap("1. ${candidates[0].address}")
        waitText(Words.get("en", "confirm"))
        tap(Words.get("en", "back"))
        waitText("1. ${candidates[0].address}")
    }

    @Test fun ambiguousAndOutOfRangeAnswersDoNotSelectAndPagesRenumber() {
        start()
        say("Apollo Hospital")
        waitText(Words.get("en", "searchChoiceUnclear"))
        ui.onNodeWithText(Words.get("en", "confirm")).assertDoesNotExist()
        say("more results")
        waitText("1. ${candidates[3].address}")
        say("two")
        waitText(Words.get("en", "searchChoiceUnclear"))
        say("one")
        waitText(Words.get("en", "confirm"))
        scenario.onActivity { assertEquals(candidates[3].address, (field(it, "selected") as SavedPlace).address) }
    }

    @Test fun hindiQueriesAndChoicesReachTheSameFlow() {
        scenario.onActivity { set(it, "profile", Profile("परीक्षक", "hi", true)) }
        provider { query, language ->
            assertEquals("अपोलो अस्पताल", query); assertEquals("hi", language); candidates
        }
        say("मुझे अपोलो अस्पताल जाना है")
        waitText("1. ${candidates[0].address}")
        say("दूसरा")
        waitText(Words.get("hi", "confirm"))
        say("नहीं")
        waitText("1. ${candidates[0].address}")
    }

    @Test fun currentLocationSetsBoundaryEvenWhenSavedHomeIsInAnotherCity() {
        val captured = mutableListOf<PlaceCandidate?>()
        val current = PlaceCandidate("", 19.076, 72.8777)
        val local = PlaceCandidate("Apollo Hospital, Mumbai", 19.08, 72.89)
        val factory: (PlaceCandidate) -> PlaceSearchProvider = { center ->
            captured.add(center)
            object : PlaceSearchProvider {
                override fun search(query: String, language: String) = candidates + local
            }
        }
        scenario.onActivity { set(it, "destinationSearchProvider", factory) }
        location(current)
        say("Apollo Hospital")
        waitText("1. ${local.address}")
        ui.onNodeWithText("1. ${candidates[0].address}").assertDoesNotExist()
        assertEquals(listOf(current), captured)
    }

    @Test fun unavailableLocationNeverFallsBackToHomeOrCallsProvider() {
        val calls = AtomicInteger()
        provider { _, _ -> calls.incrementAndGet(); candidates }
        location(null)
        say("Apollo Hospital")
        waitText(Words.get("en", "searchLocationRequired"))
        assertEquals(0, calls.get())
        location(PlaceCandidate("", 17.38, 78.48))
        tap(Words.get("en", "retry"))
        waitText("1. ${candidates[0].address}")
        assertEquals(1, calls.get())
    }

    @Test fun distantCitiesAreExcludedAndNoNearbyMatchesDoNotWidenSearch() {
        val distant = listOf(PlaceCandidate("Clinic, Delhi", 28.61, 77.21),
            PlaceCandidate("Clinic, Mumbai", 19.076, 72.8777))
        provider { _, _ -> distant + candidates.take(1) }
        start()
        ui.onNodeWithText("2. ${distant[0].address}").assertDoesNotExist()
        scenario.onActivity { assertEquals(candidates.take(1), (field(it, "destinationSearch") as DestinationSearchState).candidates) }
        say("cancel")
        provider { _, _ -> distant }
        say("Clinic, Delhi")
        waitText(Words.get("en", "searchNoResults"))
        scenario.onActivity { assertTrue((field(it, "destinationSearch") as DestinationSearchState).candidates.isEmpty()) }
    }

    @Test fun searchAgainSupportsSpokenAndTypedReplacementQueries() {
        val queries = mutableListOf<String>()
        provider { query, _ -> synchronized(queries) { queries.add(query) }; candidates }
        start()
        say("search again")
        waitText(Words.get("en", "searchQuery"))
        say("City Clinic jaana hai")
        waitText("City Clinic")
        waitText("1. ${candidates[0].address}")
        say("search again")
        waitText(Words.get("en", "searchQuery"))
        ui.onNode(hasSetTextAction()).performTextReplacement("Hospital, Chennai")
        tap(Words.get("en", "search"))
        waitText("1. ${candidates[0].address}")
        assertEquals(listOf("Apollo Hospital", "City Clinic", "Hospital, Chennai"), synchronized(queries) { queries.toList() })
        say("search again")
        say("Home")
        waitText(Words.get("en", "confirm"))
        assertEquals(3, synchronized(queries) { queries.size })
    }

    @Test fun spokenCorrectionReplacesResultsAndStillRequiresSelection() {
        val queries = mutableListOf<String>()
        val corrected = PlaceCandidate("City Clinic, Hyderabad", 17.42, 78.51)
        provider { query, _ ->
            synchronized(queries) { queries.add(query) }
            if (query == "City Clinic, Hyderabad") listOf(corrected) else candidates
        }
        start()
        say("more results")
        waitText("1. ${candidates[3].address}")
        say("No, sorry, actually I want to go to City Clinic, Hyderabad")
        waitText("1. ${corrected.address}")
        ui.onNodeWithText("1. ${candidates[3].address}").assertDoesNotExist()
        ui.onNodeWithText(Words.get("en", "confirm")).assertDoesNotExist()
        assertEquals(listOf("Apollo Hospital", "City Clinic, Hyderabad"), synchronized(queries) { queries.toList() })
        say("one")
        waitText(Words.get("en", "confirm"))
        scenario.onActivity {
            assertEquals(corrected.address, (field(it, "selected") as SavedPlace).address)
        }
    }

    @Test fun spokenCorrectionsMatchSavedAliasesAndClarifyAmbiguityBeforeSearching() {
        val calls = AtomicInteger()
        val clinic = home.copy(id = "clinic", name = "Family Clinic", aliases = listOf("my doctor"), isHome = false)
        scenario.onActivity { set(it, "places", listOf(home, clinic)) }
        provider { _, _ -> calls.incrementAndGet(); candidates }
        start()
        say("No, sorry, actually I want to go to my doctor")
        waitText(Words.get("en", "confirm"))
        scenario.onActivity { assertEquals(clinic, field(it, "selected")) }
        assertEquals(1, calls.get())

        say("no")
        waitText(Words.get("en", "rideTo"))
        start()
        say("No, sorry, actually I want to go to Home or my doctor")
        waitText(Words.get("en", "ambiguous"))
        scenario.onActivity { assertEquals(listOf(home, clinic), field(it, "choices")) }
        assertEquals(2, calls.get())
    }

    @Test fun emptyQueryAndNoResultsOfferRefinement() {
        val calls = AtomicInteger()
        provider { _, _ -> calls.incrementAndGet(); emptyList() }
        say("take me to")
        waitText(Words.get("en", "searchClearer"))
        assertEquals(0, calls.get())
        say("Missing Clinic")
        waitText(Words.get("en", "searchNoResults"))
        ui.onNodeWithContentDescription(Words.get("en", "speak")).assertIsDisplayed()
        say("search again")
        waitText(Words.get("en", "searchQuery"))
    }

    @Test fun providerFailuresHaveDistinctMessagesAndRetryPolicy() {
        val failures = listOf(
            PlaceSearchFailure.NOT_CONFIGURED to "configured",
            PlaceSearchFailure.ACCESS_DENIED to "searchAccessDenied",
            PlaceSearchFailure.QUOTA to "searchQuota",
            PlaceSearchFailure.UNAVAILABLE to "searchUnavailable"
        )
        failures.forEach { (failure, key) ->
            provider { _, _ -> throw PlaceSearchException(failure) }
            say("Missing Clinic")
            waitText(Words.get("en", key))
            if (failure == PlaceSearchFailure.UNAVAILABLE) ui.onNodeWithText(Words.get("en", "retry")).assertExists()
            else ui.onNodeWithText(Words.get("en", "retry")).assertDoesNotExist()
            say("cancel")
        }
        provider { _, _ -> throw java.io.IOException() }
        say("Missing Clinic")
        waitText(Words.get("en", "offline"))
        provider { _, _ -> candidates }
        tap(Words.get("en", "retry"))
        waitText("1. ${candidates[0].address}")
    }

    @Test fun cancelledSearchCannotReplaceNewerResults() {
        val release = CountDownLatch(1).also { releases.add(it) }
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        provider { query, _ ->
            if (query == "Old Clinic") {
                started.countDown()
                while (release.count > 0) try { release.await(1, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
                finished.countDown()
                listOf(PlaceCandidate("Old result", 1.0, 2.0))
            } else candidates
        }
        say("Old Clinic")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        tap(Words.get("en", "back"))
        start()
        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        ui.onNodeWithText("1. Old result").assertDoesNotExist()
        ui.onNodeWithText("1. ${candidates[0].address}").assertExists()
    }

    @Test fun cancelledLocationAndSharedDestinationCannotBeReplacedByLateCallback() {
        var callback: ((PlaceCandidate?) -> Unit)? = null
        val cancelled = AtomicInteger()
        val lookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback = it; { cancelled.incrementAndGet(); Unit } }
        scenario.onActivity { set(it, "destinationLocationLookup", lookup) }
        say("Some Clinic")
        scenario.onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }
                .invoke(activity, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://maps.google.com/?q=17.4,78.5"))
            callback?.invoke(PlaceCandidate("", 1.0, 2.0))
        }
        waitText(Words.get("en", "confirm"))
        assertEquals(1, cancelled.get())
        scenario.onActivity { assertEquals("shared-location", (field(it, "selected") as SavedPlace).id) }
    }

    @Test fun backgroundingLoadingSearchOffersRetryAndIgnoresLocationResponse() {
        var callback: ((PlaceCandidate?) -> Unit)? = null
        val lookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback = it; {} }
        scenario.onActivity { set(it, "destinationLocationLookup", lookup) }
        say("Some Clinic")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onActivity { callback?.invoke(null) }
        waitText(Words.get("en", "searchInterrupted"))
        ui.onNodeWithText(Words.get("en", "retry")).assertExists()
    }

    @Test fun utteranceCompletionRunsOnceAndStaleOrStoppedPlaybackNeverListens() {
        val completed = AtomicInteger()
        scenario.onActivity { activity ->
            fun finish(id: String, success: Boolean = true) {
                MainActivity::class.java.getDeclaredMethod("finishUtterance", String::class.java, Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(activity, id, success)
            }
            fun arm() { set(activity, "pendingUtterance", "current"); set(activity, "afterUtterance", { completed.incrementAndGet(); Unit }) }
            arm(); finish("old"); assertEquals(0, completed.get())
            finish("current"); finish("current"); assertEquals(1, completed.get())
            arm(); call(activity, "stopListening"); finish("current"); assertEquals(1, completed.get())
            arm(); finish("current", false); assertEquals(1, completed.get())
            arm(); set(activity, "foreground", false); finish("current"); assertEquals(1, completed.get())
        }
    }
}
