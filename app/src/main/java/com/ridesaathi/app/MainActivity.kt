package com.ridesaathi.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore
    private var profile by mutableStateOf(Profile())
    private var places by mutableStateOf<List<SavedPlace>>(emptyList())
    private var screen by mutableStateOf("onboarding")
    private var tutorialMode by mutableStateOf("intro")
    private var tutorialReturnScreen by mutableStateOf("onboarding")
    private var tutorialStep by mutableStateOf(0)
    private var message by mutableStateOf("")
    private var selected by mutableStateOf<SavedPlace?>(null)
    private var choices by mutableStateOf<List<SavedPlace>>(emptyList())
    private var pendingHome = false
    private var editingId: String? = null
    private var draftName by mutableStateOf("")
    private var pickingAddress by mutableStateOf(false)
    private var draftAddress by mutableStateOf("")
    private var draftPosition by mutableStateOf<PlaceCandidate?>(null)
    private var searchQuery by mutableStateOf("")
    private var searchResults by mutableStateOf<List<PlaceCandidate>>(emptyList())
    private var searching by mutableStateOf(false)
    private var searchPending by mutableStateOf(false)
    private var searchGeneration = 0
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var searchThread: Thread? = null
    private var cancelAddressLocation: (() -> Unit)? = null
    private var searchProvider: (PlaceCandidate) -> PlaceSearchProvider = { center ->
        RideSaathiPlaceSearchProvider(BuildConfig.API_BASE_URL, center)
    }
    private var destinationSearch by mutableStateOf<DestinationSearchState?>(null)
    private var destinationSearchGeneration = 0
    private var destinationSearchThread: Thread? = null
    private var cancelSearchLocation: (() -> Unit)? = null
    private var destinationSearchProvider: (PlaceCandidate) -> PlaceSearchProvider = { center ->
        RideSaathiPlaceSearchProvider(BuildConfig.API_BASE_URL, center)
    }
    private var destinationLocationLookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) = { callback ->
        lookupSearchLocation(callback)
    }
    private var searchSelection = false
    private var foreground = false
    private var utteranceSequence = 0L
    private var pendingUtterance: String? = null
    private var afterUtterance: (() -> Unit)? = null
    private var mapEndpoint by mutableStateOf(ProviderEndpoints.MAP)
    private var listening by mutableStateOf(false)
    private var speechTranscript by mutableStateOf("")
    private var transcriptIsFinal by mutableStateOf(false)
    private var handoffInProgress by mutableStateOf(false)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var speechGeneration = 0
    private val speechHandler = Handler(Looper.getMainLooper())
    private var speechTimeout: Runnable? = null
    private var locationCancellation: CancellationTokenSource? = null
    private var locationGeneration = 0
    private var showDeleteConfirmation by mutableStateOf(false)
    private var resolvingSharedLocation by mutableStateOf(false)
    private var sharedLocationGeneration = 0
    private var sharedLocationThread: Thread? = null
    private val sharedLocationHandler = Handler(Looper.getMainLooper())
    private var sharedLocationResolver = SharedLocationResolver()
    private var sharedAddress by mutableStateOf("")
    private var cancelSharedSearchLocation: (() -> Unit)? = null
    private var pendingSearchPermission: ((Boolean) -> Unit)? = null
    private var searchPermissionInFlight = false
    private val searchLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        searchPermissionInFlight = false
        val callback = pendingSearchPermission
        pendingSearchPermission = null
        callback?.invoke(result.values.any { it })
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!canListen()) return@registerForActivityResult
        if (granted) startListening() else {
            message = word("micDenied")
            speak(message)
        }
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (!handoffInProgress || screen != "confirm" || selected == null) return@registerForActivityResult
        if (result.values.any { it }) fetchLocation() else {
            handoffInProgress = false
            message = word("locationDenied")
            speak(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = LocalStore(this)
        mapEndpoint = store.mapEndpoint()
        profile = store.profile()
        places = store.places()
        screen = if (profile.completed && places.any { it.isHome }) "home" else "onboarding"
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = locale()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                runOnUiThread { finishUtterance(utteranceId, true) }
            }
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) {
                runOnUiThread { finishUtterance(utteranceId, false) }
            }
        })
        setContent {
            RideTheme {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
        handleSharedLocation(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedLocation(intent)
    }

    private fun handleSharedLocation(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        if (destinationSearch != null) cancelRide()
        cancelSharedLocation()
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (SharedLocationParser.supportedUris(sharedText.orEmpty()).isEmpty()) {
            message = word("invalidSharedLocation")
            return
        }
        if (!profile.completed || places.none { it.isHome }) {
            message = word("sharedLocationSetup")
            return
        }
        cancelAddressSearch()
        cancelRide()
        val location = SharedLocationParser.parse(sharedText.orEmpty())
        if (location != null) {
            showSharedLocation(location)
            return
        }
        resolvingSharedLocation = true
        message = word("resolvingSharedLocation")
        val generation = sharedLocationGeneration
        val language = profile.language
        sharedLocationThread = Thread {
            val result = runCatching { sharedLocationResolver.resolve(sharedText.orEmpty()) }
            sharedLocationHandler.post {
                if (generation != sharedLocationGeneration || isDestroyed) return@post
                sharedLocationThread = null
                result.onSuccess { destination ->
                    when (destination) {
                        is SharedDestination.Coordinates -> {
                            resolvingSharedLocation = false
                            showSharedLocation(destination.location)
                        }
                        is SharedDestination.Address -> searchSharedAddress(destination, generation, language)
                        null -> {
                            resolvingSharedLocation = false
                            message = word("sharedLocationUnresolved")
                        }
                    }
                }.onFailure {
                    resolvingSharedLocation = false
                    message = word("sharedLocationOffline")
                }
            }
        }.also { it.start() }
    }

    private fun searchSharedAddress(destination: SharedDestination.Address, generation: Int, language: String) {
        cancelSharedSearchLocation = destinationLocationLookup { center ->
            if (generation != sharedLocationGeneration || isDestroyed) return@destinationLocationLookup
            if (center == null || !SearchBoundary.valid(center)) {
                resolvingSharedLocation = false
                message = word("searchLocationRequired")
                return@destinationLocationLookup
            }
            val provider = searchProvider(center)
            sharedLocationThread = Thread {
                val result = runCatching { SearchBoundary.filter(center, provider.search(destination.query, language)) }
                sharedLocationHandler.post {
                    if (generation != sharedLocationGeneration || isDestroyed) return@post
                    sharedLocationThread = null
                    resolvingSharedLocation = false
                    result.onSuccess { matches ->
                        if (matches.isEmpty()) message = word("searchNoResults")
                        else {
                            sharedAddress = destination.sharedAddress
                            choices = matches.mapIndexed { index, place ->
                                SavedPlace("shared-location-$index", place.address.substringBefore(','), emptyList(),
                                    place.address, place.latitude, place.longitude)
                            }
                            screen = "sharedChoices"
                            message = ""
                            speak(word("selectSharedLocation"))
                        }
                    }.onFailure { message = word(searchFailureKey(it)) }
                }
            }.also { it.start() }
        }
    }

    private fun cancelSharedLocation() {
        sharedLocationGeneration++
        cancelSharedSearchLocation?.invoke()
        cancelSharedSearchLocation = null
        sharedLocationThread?.interrupt()
        sharedLocationThread = null
        sharedLocationHandler.removeCallbacksAndMessages(null)
        if (resolvingSharedLocation) message = ""
        resolvingSharedLocation = false
    }

    private fun showSharedLocation(location: SharedLocation) {
        val coordinates = SharedLocationParser.formattedCoordinates(location)
        choose(SavedPlace(
            id = "shared-location",
            name = word("sharedLocation"),
            aliases = emptyList(),
            address = location.label ?: coordinates,
            latitude = location.latitude,
            longitude = location.longitude
        ))
    }

    private fun hasSearchLocationPermission() =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    /** Permission and lookup share the caller's cancellation lifetime. */
    private fun lookupSearchLocation(callback: (PlaceCandidate?) -> Unit): () -> Unit {
        var active = true
        var cancelLookup: () -> Unit = {}
        val onPermission: (Boolean) -> Unit = { granted ->
            if (active) {
                if (granted) cancelLookup = SearchLocationLookup(this).lookup { if (active) callback(it) }
                else callback(null)
            }
        }
        if (hasSearchLocationPermission()) onPermission(true)
        else {
            pendingSearchPermission = onPermission
            if (!searchPermissionInFlight) {
                searchPermissionInFlight = true
                searchLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        return {
            active = false
            cancelLookup()
            if (pendingSearchPermission === onPermission) pendingSearchPermission = null
        }
    }

    private fun searchFailureKey(error: Throwable) = when ((error as? PlaceSearchException)?.reason) {
        PlaceSearchFailure.NOT_CONFIGURED -> "configured"
        PlaceSearchFailure.ACCESS_DENIED -> "searchAccessDenied"
        PlaceSearchFailure.QUOTA -> "searchQuota"
        PlaceSearchFailure.UNAVAILABLE, PlaceSearchFailure.INVALID_RESPONSE -> "searchUnavailable"
        PlaceSearchFailure.LOCATION_REQUIRED -> "searchLocationRequired"
        null -> "offline"
    }

    @Composable
    private fun SearchLocationActions() {
        TextButton(onClick = {
            if (hasSearchLocationPermission()) startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }) { Text(word(if (hasSearchLocationPermission()) "openLocation" else "openAppSettings")) }
        if (screen != "destinationSearch") TextButton(onClick = {
            if (screen == "editor") searchAddress() else handleSharedLocation(intent)
        }) { Text(word("retry")) }
    }

    private fun word(key: String) = Words.get(profile.language, key)
    private fun locale() = when (profile.language) {
        "hi" -> Locale.forLanguageTag("hi-IN")
        "te" -> Locale.forLanguageTag("te-IN")
        else -> Locale.forLanguageTag("en-IN")
    }
    private fun speak(value: String, after: (() -> Unit)? = null) {
        pendingUtterance = "ride-saathi-${++utteranceSequence}"
        afterUtterance = after
        tts?.language = locale()
        if (tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, pendingUtterance) != TextToSpeech.SUCCESS) {
            pendingUtterance = null
            afterUtterance = null
        }
    }

    private fun finishUtterance(id: String?, success: Boolean) {
        if (id == null || id != pendingUtterance) return
        val action = afterUtterance
        pendingUtterance = null
        afterUtterance = null
        if (success && foreground && !isDestroyed) action?.invoke()
    }

    private fun canListen() = screen in listOf("home", "confirm") ||
        (screen == "destinationSearch" && destinationSearch?.loading == false)

    private fun stopPrompt() {
        pendingUtterance = null
        afterUtterance = null
        tts?.stop()
    }

    private fun navigateBack() {
        cancelSharedLocation()
        when (screen) {
            "confirm" -> returnToChoices()
            "clarify", "sharedChoices", "destinationSearch" -> cancelRide()
            "tutorial" -> finishTutorial()
            "editor" -> {
                cancelAddressSearch()
                if (pickingAddress && draftPosition != null) {
                    pickingAddress = false
                    searchQuery = draftAddress
                    searchResults = emptyList()
                } else screen = if (profile.completed) "settings" else "onboarding"
            }
            "settings" -> screen = "home"
        }
        message = ""
    }

    @Composable
    private fun App() {
        BackHandler(enabled = resolvingSharedLocation || screen !in listOf("home", "onboarding")) {
            navigateBack()
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (screen != "editor") Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.padding(10.dp)) { RideIcon("pin") }
                }
                val title = if (screen != "editor") "Ride Saathi" else word(when {
                    pickingAddress -> "search"
                    editingId != null -> "editPlace"
                    pendingHome -> "addHome"
                    else -> "addPlace"
                })
                Text(title, modifier = Modifier.weight(1f).padding(start = if (screen == "editor") 0.dp else 12.dp),
                    style = MaterialTheme.typography.titleMedium)
                if (screen == "home") TextButton(onClick = { cancelSharedLocation(); stopListening(); message = ""; screen = "settings" }) {
                    Text(word("settings"))
                }
                else if (screen != "onboarding") TextButton(onClick = { navigateBack() }) {
                    RideIcon("back", Modifier.size(18.dp)); Text(word("back"))
                }
            }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = screenOrder(targetState) >= screenOrder(initialState)
                    val slideIn = slideInHorizontally(
                        animationSpec = tween(360, easing = FastOutSlowInEasing),
                        initialOffsetX = { width -> if (forward) width / 5 else -width / 5 }
                    )
                    val slideOut = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { width -> if (forward) -width / 6 else width / 6 }
                    )
                    (fadeIn(tween(260)) + slideIn + scaleIn(tween(360, easing = FastOutSlowInEasing), initialScale = 0.98f))
                        .togetherWith(fadeOut(tween(200)) + slideOut + scaleOut(tween(240), targetScale = 0.98f))
                        .using(SizeTransform(clip = false))
                },
                modifier = Modifier.weight(1f),
                label = "screenTransition"
            ) { activeScreen ->
                key(activeScreen, if (activeScreen == "destinationSearch") destinationSearch?.let { it.page to it.editing } else null) {
                    if (activeScreen == "editor") {
                        Editor(Modifier.fillMaxSize())
                    } else if (activeScreen == "settings") {
                        Settings(Modifier.fillMaxSize())
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        when (activeScreen) {
                            "onboarding" -> Onboarding()
                            "tutorial" -> Tutorial()
                            "home" -> Home()
                            "confirm" -> Confirmation()
                            "clarify", "sharedChoices" -> Clarification()
                            "destinationSearch" -> DestinationSearchScreen()
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = message.isNotBlank(),
                enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 2 }
            ) {
                Surface(
                    color = if (handoffInProgress || resolvingSharedLocation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp).animateContentSize(tween(260, easing = FastOutSlowInEasing))) {
                        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                        AnimatedVisibility(resolvingSharedLocation, enter = fadeIn(), exit = fadeOut()) {
                            Column {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(onClick = { cancelSharedLocation() }) { Text(word("cancel")) }
                            }
                        }
                        if (message == word("searchLocationRequired")) SearchLocationActions()
                        if (message == word("micDenied") || message == word("locationDenied")) {
                            TextButton(onClick = {
                                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:$packageName")))
                            }) { Text(word("openAppSettings")) }
                        }
                    }
                }
            }
            when (screen) {
                "destinationSearch" -> StickyMicrophone(enabled = destinationSearch?.loading == false)
                "confirm" -> StickyMicrophone(enabled = !handoffInProgress)
            }
            AnimatedVisibility(
                visible = screen == "onboarding",
                enter = fadeIn(tween(180)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it }
            ) {
                Surface(shadowElevation = 8.dp) { OnboardingActions() }
            }
        }
    }

    private fun screenOrder(value: String) = when (value) {
        "onboarding" -> 0
        "tutorial" -> 1
        "home" -> 2
        "settings" -> 3
        "editor" -> 4
        "destinationSearch" -> 5
        "clarify", "sharedChoices" -> 6
        "confirm" -> 7
        else -> 0
    }

    @Composable
    private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.animateContentSize(tween(260, easing = FastOutSlowInEasing))) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }

    private fun selectLanguage(code: String) {
        val shouldShowIntro = !profile.completed && !profile.introSeen && profile.language != code
        profile = profile.copy(language = code)
        store.saveProfile(profile)
        tts?.language = locale()
        message = ""
        if (shouldShowIntro) openTutorial("intro", "onboarding")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LanguagePicker(dropdown: Boolean = false) {
        val languages = listOf("en" to "English", "hi" to "हिन्दी", "te" to "తెలుగు")
        Text(word("language"), style = MaterialTheme.typography.titleLarge)
        if (dropdown) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = languages.firstOrNull { it.first == profile.language }?.second ?: "English",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(word("changeLanguage")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    languages.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                            onClick = { selectLanguage(code); expanded = false },
                            trailingIcon = { if (profile.language == code) RideIcon("check", Modifier.size(20.dp)) },
                            modifier = Modifier.heightIn(min = 52.dp)
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                languages.forEach { (code, label) ->
                    FilterChip(selected = profile.language == code, onClick = { selectLanguage(code) },
                        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        leadingIcon = { if (profile.language == code) RideIcon("check", Modifier.size(20.dp)) })
                }
            }
        }
    }

    @Composable
    private fun Onboarding() {
        Text(word("welcome"), style = MaterialTheme.typography.headlineMedium)
        Text(word("setupHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard { LanguagePicker(dropdown = true) }
        if (!profile.introSeen) {
            OutlinedButton(onClick = { openTutorial("intro", "onboarding") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                RideIcon("arrow", Modifier.size(18.dp))
                Text(word("watchIntro"))
            }
        }
        OutlinedTextField(value = profile.name, onValueChange = {
            profile = profile.copy(name = it)
            store.saveProfile(profile)
        }, label = { Text(word("name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(word("places"), style = MaterialTheme.typography.titleLarge)
        places.forEach { place -> PlaceRow(place, true) }
        if (!uberInstalled()) {
            Text(word("uberInstall"), style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    private fun OnboardingActions() {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (places.none { it.isHome }) {
                LargeButton(word("addHome")) { openEditor(null, true) }
            } else {
                LargeButton(word("finish")) {
                    if (profile.name.isBlank()) message = word("name")
                    else {
                        profile = profile.copy(completed = true)
                        store.saveProfile(profile)
                        message = ""
                        if (profile.tutorialSeen) screen = "home"
                        else openTutorial("full", "home")
                    }
                }
                OutlinedButton(onClick = { openEditor(null, false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(word("addPlace")) }
                if (!uberInstalled()) {
                    LargeButton(word("install")) { openStore() }
                }
            }
        }
    }

    private data class TutorialSlide(val icon: String, val titleKey: String, val bodyKey: String)

    private fun introSlides(): List<TutorialSlide> = listOf(
        TutorialSlide("home", "introFamilyTitle", "introFamilyBody"),
        TutorialSlide("pin", "introPlacesTitle", "introPlacesBody")
    )

    private fun fullTutorialSlides(): List<TutorialSlide> = listOf(
        TutorialSlide("mic", "tutorialSpeakTitle", "tutorialSpeakBody"),
        TutorialSlide("check", "tutorialConfirmTitle", "tutorialConfirmBody"),
        TutorialSlide("pin", "tutorialPickupTitle", "tutorialPickupBody"),
        TutorialSlide("arrow", "tutorialUberTitle", "tutorialUberBody")
    )

    private fun tutorialSlides(): List<TutorialSlide> =
        if (tutorialMode == "intro") introSlides() else fullTutorialSlides()

    private fun openTutorial(mode: String, returnScreen: String) {
        stopListening()
        stopPrompt()
        tutorialMode = mode
        tutorialReturnScreen = returnScreen
        tutorialStep = 0
        message = ""
        screen = "tutorial"
    }

    private fun finishTutorial() {
        stopPrompt()
        profile = if (tutorialMode == "intro") profile.copy(introSeen = true)
            else profile.copy(introSeen = true, tutorialSeen = true)
        store.saveProfile(profile)
        tutorialStep = 0
        message = ""
        screen = tutorialReturnScreen
    }

    @Composable
    private fun Tutorial() {
        val slides = tutorialSlides()
        val slide = slides.getOrNull(tutorialStep) ?: return
        val isLast = tutorialStep == slides.lastIndex
        val animatedAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300),
            label = "tutorialStepAlpha"
        )
        LaunchedEffect(tutorialMode, tutorialStep, profile.language) {
            speak("${word(slide.titleKey)}. ${word(slide.bodyKey)}")
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.alpha(animatedAlpha)) {
            LinearProgressIndicator(
                progress = { (tutorialStep + 1).toFloat() / slides.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                word(if (tutorialMode == "intro") "introTitle" else "tutorialTitle"),
                style = MaterialTheme.typography.headlineMedium
            )
            SectionCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.padding(28.dp)) { RideIcon(slide.icon, Modifier.size(56.dp)) }
                    }
                }
                Text(word(slide.titleKey), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                Text(word(slide.bodyKey), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = { speak("${word(slide.titleKey)}. ${word(slide.bodyKey)}") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                RideIcon("mic", Modifier.size(18.dp))
                Text(word("replayAudio"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (tutorialStep > 0) {
                    OutlinedButton(
                        onClick = { tutorialStep-- },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    ) { Text(word("back")) }
                }
                LargeButton(
                    label = word(if (isLast) {
                        if (tutorialMode == "intro") "continueSetup" else "startUsing"
                    } else "continue"),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLast) finishTutorial() else tutorialStep++
                }
            }
        }
    }

    @Composable
    private fun Home() {
        var entered by remember { mutableStateOf(false) }
        val contentAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(450), label = "homeContentAlpha")
        val heroScale by animateFloatAsState(if (entered) 1f else 0.96f, tween(450), label = "homeHeroScale")
        LaunchedEffect(Unit) { entered = true }
        Text("${word("hello")}, ${profile.name}", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(contentAlpha))
        Text(word("rideTo"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.alpha(contentAlpha))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().scale(heroScale).alpha(contentAlpha)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeMicrophone()
                Text(if (listening) word("listening") else word("voiceHint"),
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                SpeechTranscript()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.alpha(contentAlpha)) {
            Text(word("places"), style = MaterialTheme.typography.titleLarge)
            Text(word("tapHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        places.sortedByDescending { it.isHome }.forEachIndexed { index, place ->
            val rowAlpha by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(durationMillis = 350, delayMillis = 80 * index),
                label = "placeRowAlpha"
            )
            PlaceRow(place, false, Modifier.alpha(rowAlpha))
        }
        Text(word("handoffHint"), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(contentAlpha))
    }

    @Composable
    private fun HomeMicrophone() {
        val label = if (listening) word("stop") else word("speak")
        val transition = rememberInfiniteTransition(label = "homeMicPulse")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = if (listening) 1.32f else 1.08f,
            animationSpec = infiniteRepeatable(tween(if (listening) 950 else 1800), RepeatMode.Restart),
            label = "homeMicPulseScale"
        )
        val pulseAlpha by transition.animateFloat(
            initialValue = if (listening) 0.28f else 0.12f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(if (listening) 950 else 1800), RepeatMode.Restart),
            label = "homeMicPulseAlpha"
        )
        val micScale by animateFloatAsState(
            targetValue = if (listening) 1.08f else 1f,
            animationSpec = tween(220),
            label = "homeMicScale"
        )
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                modifier = Modifier.size(118.dp).scale(pulse)
            ) {}
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (listening) 0.72f else 0.42f),
                modifier = Modifier.size(126.dp)
            ) {}
            Surface(
                onClick = { if (listening) stopListening() else requestMicrophone() },
                shape = CircleShape,
                shadowElevation = 10.dp,
                color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp).scale(micScale).semantics { contentDescription = label }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RideIcon("mic", Modifier.size(42.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    @Composable
    private fun SpeechTranscript() {
        if (speechTranscript.isBlank()) return
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    word(if (transcriptIsFinal) "recognizedSpeech" else "heardSoFar"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(speechTranscript, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    @Composable
    private fun PlaceRow(place: SavedPlace, editing: Boolean, modifier: Modifier = Modifier) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.985f else 1f,
            animationSpec = spring(dampingRatio = 0.74f, stiffness = 420f),
            label = "placeRowPressScale"
        )
        val elevation by animateDpAsState(
            targetValue = if (pressed) 0.dp else 2.dp,
            animationSpec = tween(180),
            label = "placeRowElevation"
        )
        Surface(onClick = {
            if (editing) openEditor(place, place.isHome) else choose(place)
        }, interactionSource = interactionSource, modifier = modifier.fillMaxWidth().scale(scale), shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.padding(12.dp)) { RideIcon(if (place.isHome) "home" else "pin") }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (place.isHome) word("home") else place.name, style = MaterialTheme.typography.titleLarge)
                    ExpandableAddress(place)
                }
                RideIcon("arrow", Modifier.size(18.dp))
            }
        }
    }

    @Composable
    private fun ExpandableAddress(
        place: SavedPlace,
        style: TextStyle = MaterialTheme.typography.bodyMedium
    ) {
        var expanded by remember(place.id, place.address) { mutableStateOf(false) }
        var overflows by remember(place.id, place.address) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().animateContentSize(tween(260, easing = FastOutSlowInEasing))) {
            Text(
                place.address,
                modifier = Modifier.fillMaxWidth(),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded) overflows = result.hasVisualOverflow
                }
            )
            if (expanded || overflows) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(word(if (expanded) "showLess" else "showMore"))
                }
            }
        }
    }

    @Composable
    private fun Settings(modifier: Modifier = Modifier) {
        Column(modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(word("settings"), style = MaterialTheme.typography.headlineMedium)
                SectionCard { LanguagePicker(dropdown = true) }
                Text(word("manage"), style = MaterialTheme.typography.titleLarge)
                places.forEach { PlaceRow(it, true) }
            }
            Surface(shadowElevation = 4.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { openTutorial("full", "settings") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        RideIcon("arrow", Modifier.size(18.dp))
                        Text(word("watchTutorial"))
                    }
                    LargeButton(word("addPlace")) { openEditor(null, false) }
                    OutlinedButton(onClick = { screen = "home" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(word("done"))
                    }
                }
            }
        }
    }

    @Composable
    private fun Editor(modifier: Modifier = Modifier) {
        val focusManager = LocalFocusManager.current
        Column(modifier.fillMaxWidth()) {
            if (pickingAddress) {
                AddressPicker(Modifier.weight(1f))
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (pendingHome) {
                        Text(word("home"), style = MaterialTheme.typography.titleLarge)
                    } else {
                        OutlinedTextField(
                            value = draftName, onValueChange = { draftName = it },
                            label = { Text(word("placeName")) },
                            supportingText = { Text(word("placeNameHint")) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    SectionCard {
                        Text(word("address"), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(draftAddress, style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = {
                            focusManager.clearFocus()
                            searchQuery = draftAddress
                            searchResults = emptyList()
                            message = ""
                            pickingAddress = true
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(word("changeAddress"))
                        }
                    }
                    draftPosition?.let { point ->
                        MapPreview(OpenStreetMapPreviewProvider(mapEndpoint).url(point.latitude, point.longitude), profile.language)
                    }
                    if (editingId != null && !pendingHome) {
                        TextButton(onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(word("delete"))
                        }
                    }
                }
                Surface(shadowElevation = 4.dp) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        LargeButton(word("save"), enabled = draftPosition != null && draftName.isNotBlank()) {
                            focusManager.clearFocus()
                            savePlace()
                        }
                    }
                }
            }
        }
        if (showDeleteConfirmation) AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(word("delete")) }, text = { Text(draftName) },
            confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; deletePlace() }) { Text(word("delete")) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text(word("cancel")) } })
    }

    @Composable
    private fun AddressPicker(modifier: Modifier = Modifier) {
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            OutlinedTextField(value = searchQuery, onValueChange = {
                searchQuery = it
                searchResults = emptyList()
                message = ""
                scheduleAddressSearch()
            },
                label = { Text(word("address")) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    searchAddress()
                }),
                supportingText = {
                    Text(word(when {
                        searching -> "searchingAddress"
                        searchPending -> "searchPending"
                        searchResults.isNotEmpty() -> "selectAddress"
                        else -> "addressSearchHint"
                    }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            searchAddress()
                        }, enabled = searchQuery.trim().length >= 3,
                            modifier = Modifier.semantics { contentDescription = word("search") }) {
                            RideIcon("search")
                        }
                    }
                })
            Text(word("searchAttribution"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { result ->
                    OutlinedCard(onClick = {
                        focusManager.clearFocus()
                        cancelAddressSearch()
                        draftPosition = result
                        draftAddress = result.address
                        searchQuery = result.address
                        searchResults = emptyList()
                        message = ""
                        pickingAddress = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RideIcon("pin", Modifier.size(24.dp))
                            Text(result.address, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Confirmation() {
        val place = selected ?: return
        val mapUrl = OpenStreetMapPreviewProvider(mapEndpoint).url(place.latitude, place.longitude)
        Text(word("confirm"), style = MaterialTheme.typography.headlineMedium)
        SectionCard {
            RideIcon(if (place.isHome) "home" else "pin", Modifier.size(36.dp))
            Text(if (place.isHome) word("home") else place.name, style = MaterialTheme.typography.headlineLarge)
            ExpandableAddress(place, style = MaterialTheme.typography.bodyLarge)
        }
        if (handoffInProgress) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (!uberInstalled()) LargeButton(word("install")) { openStore() }
        if (message == word("locationUnavailable")) {
            OutlinedButton(onClick = {
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }, modifier = Modifier.fillMaxWidth()) { Text(word("openLocation")) }
        }
        MapPreview(mapUrl, profile.language)
        Text(word("handoffHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    private fun cancelDestinationSearch(clear: Boolean = true) {
        destinationSearchGeneration++
        destinationSearchThread?.interrupt()
        destinationSearchThread = null
        cancelSearchLocation?.invoke()
        cancelSearchLocation = null
        stopPrompt()
        destinationSearch = if (clear) null else destinationSearch?.copy(loading = false)
    }

    private fun beginDestinationSearch(raw: String, extractPhrase: Boolean = true) {
        cancelDestinationSearch()
        cancelSharedLocation()
        stopListening()
        searchSelection = false
        selected = null
        message = ""
        screen = "destinationSearch"
        val query = if (extractPhrase) DestinationQuery.extract(raw) else raw.trim().takeIf { it.length >= 3 }
        if (query == null) {
            destinationSearch = DestinationSearchState(query = raw, editing = true, error = "searchClearer")
            speak(word("searchClearer"))
            return
        }
        destinationSearch = DestinationSearchState(query = query, loading = true)
        speak("${word("searchingDestination")} $query")
        val generation = destinationSearchGeneration
        val language = profile.language
        cancelSearchLocation = destinationLocationLookup { location ->
            if (generation != destinationSearchGeneration || screen != "destinationSearch" || isDestroyed) return@destinationLocationLookup
            if (location == null || !SearchBoundary.valid(location)) {
                destinationSearch = destinationSearch?.copy(loading = false, error = "searchLocationRequired", retryable = true)
                speak(word("searchLocationRequired"))
                return@destinationLocationLookup
            }
            val provider = destinationSearchProvider(location)
            destinationSearchThread = Thread {
                val result = runCatching { SearchBoundary.filter(location, provider.search(query, language)) }
                runOnUiThread {
                    if (generation != destinationSearchGeneration || screen != "destinationSearch" || isDestroyed) return@runOnUiThread
                    destinationSearchThread = null
                    result.onSuccess { candidates ->
                        destinationSearch = destinationSearch?.copy(loading = false, candidates = candidates,
                            error = if (candidates.isEmpty()) "searchNoResults" else null)
                        if (candidates.isEmpty()) speak(word("searchNoResults")) else announceSearchChoices()
                    }.onFailure { error ->
                        val key = searchFailureKey(error)
                        destinationSearch = destinationSearch?.copy(loading = false, error = key,
                            retryable = key in listOf("searchUnavailable", "offline", "searchLocationRequired"))
                        speak(word(key))
                    }
                }
            }.also { it.start() }
        }
    }

    private fun announceSearchChoices() {
        val state = destinationSearch ?: return
        if (screen != "destinationSearch" || state.editing || state.loading || state.visible.isEmpty()) return
        stopListening()
        message = ""
        val generation = destinationSearchGeneration
        val prompt = buildString {
            append(word("searchChoose")).append(". ")
            state.visible.forEachIndexed { index, candidate ->
                append("${index + 1}. ${SpeechText.addressSummary(candidate.address)}. ")
            }
            append(word("searchChoiceHint"))
            if (state.hasMore) append(". ").append(word("searchMoreHint"))
        }
        speak(prompt) {
            if (generation == destinationSearchGeneration && screen == "destinationSearch" &&
                destinationSearch == state && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        }
    }

    private fun moreSearchChoices() {
        val state = destinationSearch ?: return
        stopListening()
        stopPrompt()
        if (state.hasMore) {
            destinationSearch = state.copy(page = state.page + 1)
            announceSearchChoices()
        } else { message = word("searchNoMore"); speak(message) }
    }

    private fun editDestinationQuery() {
        cancelDestinationSearch(clear = false)
        stopListening()
        message = ""
        destinationSearch = destinationSearch?.copy(editing = true, error = null)
        val generation = destinationSearchGeneration
        speak(word("searchClearer")) {
            if (generation == destinationSearchGeneration && screen == "destinationSearch" &&
                destinationSearch?.editing == true && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        }
    }

    private fun previousSearchChoices() {
        val state = destinationSearch ?: return
        destinationSearch = state.copy(page = (state.page - 1).coerceAtLeast(0))
        announceSearchChoices()
    }

    private fun chooseSearchResult(index: Int) {
        val candidate = destinationSearch?.visible?.getOrNull(index) ?: return
        choose(SavedPlace("searched-destination", candidate.address.substringBefore(','), emptyList(),
            candidate.address, candidate.latitude, candidate.longitude), fromSearch = true)
    }

    private fun handleSearchSpeech(raw: String) {
        val state = destinationSearch ?: return
        val choice = DestinationChoices.parse(raw, if (state.editing) emptyList() else state.visible)
        if (choice == SearchChoice.Cancel) { cancelRide(); return }
        if (choice == SearchChoice.Unknown) DestinationQuery.replacement(raw)?.let { query ->
            resolveDestination(query, queryIsExtracted = true)
            return
        }
        if (state.editing) {
            if (VoiceCommands.decision(raw) == VoiceDecision.No) { message = word("searchClearer"); return }
            resolveDestination(raw)
            return
        }
        when (choice) {
            is SearchChoice.Select -> chooseSearchResult(choice.index)
            SearchChoice.More -> moreSearchChoices()
            SearchChoice.Previous -> previousSearchChoices()
            SearchChoice.Again -> editDestinationQuery()
            SearchChoice.Repeat -> if (state.visible.isEmpty()) speak(word(state.error ?: "searchClearer")) else announceSearchChoices()
            else -> { message = word("searchChoiceUnclear"); speak(message) }
        }
    }

    private fun returnToChoices() {
        if (searchSelection && destinationSearch != null) {
            cancelLocation()
            stopListening()
            stopPrompt()
            selected = null
            searchSelection = false
            message = ""
            screen = "destinationSearch"
            announceSearchChoices()
        } else cancelRide()
    }

    @Composable
    private fun DestinationSearchScreen() {
        val state = destinationSearch ?: return
        val keyboard = LocalSoftwareKeyboardController.current
        Text(word("searchDestinations"), style = MaterialTheme.typography.headlineMedium)
        if (state.editing) {
            OutlinedTextField(value = state.query, onValueChange = {
                stopListening()
                stopPrompt()
                destinationSearch = state.copy(query = it, error = null)
            }, label = { Text(word("searchQuery")) }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide(); beginDestinationSearch(state.query, extractPhrase = false)
                }), singleLine = true)
            LargeButton(word("search"), enabled = state.query.trim().length >= 3) {
                keyboard?.hide(); beginDestinationSearch(state.query, extractPhrase = false)
            }
        } else Text(state.query, style = MaterialTheme.typography.titleLarge)
        AnimatedVisibility(state.loading, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word("searchingDestination"))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (state.error == "searchLocationRequired") SearchLocationActions()
        state.error?.let { Text(word(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (!state.loading && !state.editing) {
            state.visible.forEachIndexed { index, candidate ->
                LargeButton("${index + 1}. ${SpeechText.addressSummary(candidate.address)}", maxLines = 2) {
                    chooseSearchResult(index)
                }
            }
            if (state.retryable) LargeButton(word("retry")) { beginDestinationSearch(state.query, extractPhrase = false) }
        }
        Text(word("searchAttribution"), style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    private fun StickyMicrophone(enabled: Boolean) {
        val label = if (listening) word("stop") else word("speak")
        val micScale by animateFloatAsState(
            targetValue = if (listening) 1.08f else 1f,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
            label = "stickyMicScale"
        )
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 4.dp) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (listening) stopListening() else requestMicrophone() },
                    enabled = enabled,
                    shape = CircleShape, contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor =
                        if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(72.dp).scale(micScale).semantics { contentDescription = label }) {
                    RideIcon("mic", Modifier.size(32.dp), color = LocalContentColor.current)
                }
                AnimatedVisibility(listening, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                    Text(word("listening"), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }

    @Composable
    private fun Clarification() {
        Text(word(if (screen == "sharedChoices") "selectSharedLocation" else "ambiguous"),
            style = MaterialTheme.typography.headlineMedium)
        if (screen == "sharedChoices") {
            Text(word("sharedSearchHint"))
            Text(sharedAddress, style = MaterialTheme.typography.bodyLarge)
        } else SpeechTranscript()
        choices.forEach { PlaceRow(it, false) }
        OutlinedButton(onClick = { cancelRide() }, modifier = Modifier.fillMaxWidth()) { Text(word("cancel")) }
    }

    @Composable
    private fun LargeButton(
        label: String,
        enabled: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
        modifier: Modifier = Modifier.fillMaxWidth(),
        onClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed && enabled) 0.98f else 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
            label = "largeButtonPressScale"
        )
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp),
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = modifier.heightIn(min = 64.dp).scale(scale).animateContentSize(tween(220, easing = FastOutSlowInEasing))) {
            Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }

    private fun openEditor(place: SavedPlace?, home: Boolean) {
        stopListening()
        showDeleteConfirmation = false
        pendingHome = home
        editingId = place?.id
        draftName = if (home) word("home") else place?.name.orEmpty()
        pickingAddress = place == null
        draftAddress = place?.address.orEmpty()
        draftPosition = place?.let { PlaceCandidate(it.address, it.latitude, it.longitude) }
        searchQuery = place?.address.orEmpty()
        searchResults = emptyList()
        cancelAddressSearch()
        message = ""
        screen = "editor"
    }

    private fun cancelAddressSearch() {
        searchGeneration++
        cancelAddressLocation?.invoke()
        cancelAddressLocation = null
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
        searchThread?.interrupt()
        searchThread = null
        searchPending = false
        searching = false
    }

    private fun scheduleAddressSearch() {
        cancelAddressSearch()
        if (searchQuery.trim().length < 3) return
        searchPending = true
        pendingSearch = Runnable { searchAddress() }.also {
            searchHandler.postDelayed(it, 500)
        }
    }

    private fun searchAddress() {
        cancelAddressSearch()
        val query = searchQuery.trim()
        if (query.length < 3 || screen != "editor" || isDestroyed) return
        val generation = ++searchGeneration
        searching = true
        searchResults = emptyList()
        message = ""
        val language = locale().toLanguageTag()
        cancelAddressLocation = destinationLocationLookup { center ->
            if (generation != searchGeneration || isDestroyed || screen != "editor") return@destinationLocationLookup
            if (center == null || !SearchBoundary.valid(center)) {
                searching = false
                message = word("searchLocationRequired")
                return@destinationLocationLookup
            }
            val provider = searchProvider(center)
            searchThread = Thread {
                val result = runCatching { SearchBoundary.filter(center, provider.search(query, language)) }
                runOnUiThread {
                    if (generation != searchGeneration || isDestroyed || screen != "editor") return@runOnUiThread
                    searchThread = null
                    searching = false
                    result.onSuccess {
                        searchResults = it
                        if (it.isEmpty()) message = word("noResults")
                    }.onFailure { message = word(searchFailureKey(it)) }
                }
            }.also { it.start() }
        }
    }

    private fun savePlace() {
        val point = draftPosition ?: run { message = word("invalidPlace"); return }
        val name = draftName.trim()
        if (name.isBlank() || draftAddress.isBlank()) { message = word("invalidPlace"); return }
        val old = places.firstOrNull { it.id == editingId }
        val saved = SavedPlace(id = old?.id ?: java.util.UUID.randomUUID().toString(),
            name = name, aliases = old?.aliases.orEmpty(), address = draftAddress,
            latitude = point.latitude, longitude = point.longitude, isHome = pendingHome)
        if (DestinationResolver.conflicts(saved, places)) { message = word("duplicate"); return }
        cancelAddressSearch()
        places = places.filterNot { it.id == editingId } + saved
        store.savePlaces(places)
        message = ""
        screen = if (profile.completed) "settings" else "onboarding"
    }

    private fun deletePlace() {
        if (pendingHome) { message = word("replaceHome"); return }
        cancelAddressSearch()
        places = places.filterNot { it.id == editingId }
        store.savePlaces(places)
        message = ""
        screen = if (profile.completed) "settings" else "onboarding"
    }

    private fun choose(place: SavedPlace, fromSearch: Boolean = false) {
        cancelDestinationSearch(clear = !fromSearch)
        searchSelection = fromSearch
        cancelSharedLocation()
        stopListening()
        selected = place
        choices = emptyList()
        screen = "confirm"
        message = ""
        val name = if (place.isHome) word("home") else SpeechText.addressSummary(place.name)
        val address = SpeechText.addressSummary(place.address)
        speak("${word("confirm")} $name. $address")
    }

    private fun cancelRide() {
        cancelDestinationSearch()
        searchSelection = false
        cancelSharedLocation()
        stopListening()
        tts?.stop()
        cancelLocation()
        selected = null
        message = ""
        screen = "home"
    }

    private fun requestMicrophone() {
        cancelSharedLocation()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!canListen() || handoffInProgress) return
        cancelSharedLocation()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { message = word("voiceUnavailable"); return }
        tts?.stop()
        stopListening()
        val generation = speechGeneration
        speechTranscript = ""
        transcriptIsFinal = false
        listening = true
        message = ""
        fun active() = generation == speechGeneration && !isDestroyed
        fun failed() {
            if (!active()) return
            stopListening()
            message = word("speechFailed")
        }
        speechTimeout = Runnable { failed() }.also { speechHandler.postDelayed(it, 20_000) }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { service ->
                service.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit // Keep Stop available until the final result.
                    override fun onError(error: Int) { failed() }
                    override fun onResults(results: Bundle?) {
                        if (!active()) return
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        stopListening()
                        speechTranscript = heard
                        transcriptIsFinal = true
                        handleSpeech(heard)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!active()) return
                        val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!heard.isNullOrBlank()) { speechTranscript = heard; transcriptIsFinal = false }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
            }
        } catch (_: RuntimeException) { failed() }
    }

    private fun stopListening() {
        pendingUtterance = null
        afterUtterance = null
        speechGeneration++ // Invalidate callbacks before cancel/destroy can deliver an error.
        speechTimeout?.let(speechHandler::removeCallbacks)
        speechTimeout = null
        val old = recognizer
        recognizer = null
        old?.cancel()
        old?.destroy()
        listening = false
    }

    private fun handleSpeech(raw: String) {
        if (!canListen() || handoffInProgress) return
        if (raw.isBlank()) { message = word("speechFailed"); return }
        if (screen == "destinationSearch") { handleSearchSpeech(raw); return }
        val decision = if (screen == "confirm") VoiceCommands.decision(raw) else {
            // A place such as “Central bus stop” must not become a stop command.
            val command = SpeechText.tokens(raw).joinToString(" ")
            when {
                DestinationChoices.parse(raw, emptyList()) == SearchChoice.Cancel ||
                    command in listOf("please stop", "please cancel", "yes cancel", "बंद") -> VoiceDecision.Cancel
                command in listOf("no", "no thanks", "नहीं", "नही", "मत") -> VoiceDecision.No
                else -> VoiceDecision.Unknown
            }
        }
        if (decision == VoiceDecision.Cancel) { cancelRide(); return }
        if (screen == "confirm") {
            when (decision) {
                VoiceDecision.Yes -> confirmRide()
                VoiceDecision.No -> returnToChoices()
                else -> message = word("speechFailed")
            }
            return
        }
        if (decision == VoiceDecision.No) { message = word("unknown"); return }
        resolveDestination(raw)
    }

    private fun resolveDestination(raw: String, queryIsExtracted: Boolean = false) {
        val matched = DestinationResolver.matches(raw, places)
        when (matched.size) {
            0 -> beginDestinationSearch(raw, extractPhrase = !queryIsExtracted)
            1 -> choose(matched.first())
            else -> {
                cancelDestinationSearch()
                choices = matched; screen = "clarify"; message = ""; speak(word("ambiguous"))
            }
        }
    }

    private fun confirmRide() {
        if (handoffInProgress || screen != "confirm") return
        stopListening()
        tts?.stop()
        val place = selected ?: return
        if (place.address.isBlank()) { message = word("invalidPlace"); return }
        if (!uberInstalled()) { message = word("uberInstall"); return }
        handoffInProgress = true
        message = word("working")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun cancelLocation() {
        locationGeneration++
        locationCancellation?.cancel()
        locationCancellation = null
        handoffInProgress = false
    }

    private fun reportLocationUnavailable() {
        handoffInProgress = false
        message = word("locationUnavailable")
        speak(message)
    }

    private fun fetchLocation() {
        if (!handoffInProgress || screen != "confirm") return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            reportLocationUnavailable(); return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            handoffInProgress = false; message = word("locationDenied"); return
        }
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0).setDurationMillis(15_000).build()
        val generation = ++locationGeneration
        val cancellation = CancellationTokenSource()
        locationCancellation?.cancel()
        locationCancellation = cancellation
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(request, cancellation.token)
            .addOnSuccessListener { location ->
                if (generation != locationGeneration || !handoffInProgress || isDestroyed || screen != "confirm") return@addOnSuccessListener
                val place = selected
                if (location == null || place == null || (location.hasAccuracy() && location.accuracy > 250f)) {
                    reportLocationUnavailable(); return@addOnSuccessListener
                }
                try {
                    startActivity(UberHandoff.intent(place, location.latitude, location.longitude))
                    message = ""
                    selected = null
                    cancelDestinationSearch()
                    searchSelection = false
                    screen = "home"
                } catch (_: ActivityNotFoundException) { message = word("handoffFailed") }
                finally { handoffInProgress = false }
            }.addOnFailureListener {
                if (generation == locationGeneration && !isDestroyed) {
                    reportLocationUnavailable()
                }
            }
    }

    private fun uberInstalled(): Boolean = try {
        packageManager.getPackageInfo(UberHandoff.packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${UberHandoff.packageName}"))
        try { startActivity(market) }
        catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${UberHandoff.packageName}"))) }
            catch (_: ActivityNotFoundException) { message = word("storeUnavailable") }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        super.onPause()
        foreground = false
        if (destinationSearch?.loading == true && !searchPermissionInFlight) {
            cancelDestinationSearch(clear = false)
            destinationSearch = destinationSearch?.copy(error = "searchInterrupted", retryable = true)
        }
        stopListening()
        tts?.stop()
    }

    override fun onStop() {
        super.onStop()
        if (!searchPermissionInFlight) {
            cancelSharedLocation()
            cancelAddressSearch()
        }
        if (handoffInProgress) { cancelLocation(); message = word("rideInterrupted") }
    }

    override fun onDestroy() {
        cancelDestinationSearch()
        cancelSharedLocation()
        cancelAddressSearch()
        cancelLocation()
        stopListening()
        tts?.shutdown()
        super.onDestroy()
    }
}
