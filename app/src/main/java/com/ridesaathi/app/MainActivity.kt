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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    private var searchProvider: () -> PlaceSearchProvider = {
        val home = places.firstOrNull { it.isHome }
        OlaPlaceSearchProvider(BuildConfig.OLA_MAPS_API_KEY,
            home?.let { PlaceCandidate(it.address, it.latitude, it.longitude) })
    }
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

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (screen !in listOf("home", "confirm")) return@registerForActivityResult
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
        if (profile.language == "te") {
            profile = profile.copy(language = "en")
            store.saveProfile(profile)
        }
        places = store.places()
        screen = if (profile.completed && places.any { it.isHome }) "home" else "onboarding"
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = locale()
        }
        setContent {
            RideTheme {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }

    private fun word(key: String) = Words.get(profile.language, key)
    private fun locale() = when (profile.language) {
        "hi" -> Locale.forLanguageTag("hi-IN")
        else -> Locale.forLanguageTag("en-IN")
    }
    private fun speak(value: String) {
        tts?.language = locale()
        tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "ride-saathi")
    }

    private fun navigateBack() {
        when (screen) {
            "confirm", "clarify" -> cancelRide()
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
        BackHandler(enabled = screen !in listOf("home", "onboarding")) {
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
                if (screen == "home") TextButton(onClick = { stopListening(); message = ""; screen = "settings" }) {
                    Text(word("settings"))
                }
                else if (screen != "onboarding") TextButton(onClick = { navigateBack() }) {
                    RideIcon("back", Modifier.size(18.dp)); Text(word("back"))
                }
            }
            key(screen) {
                if (screen == "editor") {
                    Editor(Modifier.weight(1f))
                } else if (screen == "settings") {
                    Settings(Modifier.weight(1f))
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (screen) {
                        "onboarding" -> Onboarding()
                        "home" -> Home()
                        "confirm" -> Confirmation()
                        "clarify" -> Clarification()
                    }
                }
            }
            if (message.isNotBlank()) Surface(
                color = if (handoffInProgress) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                    if (message == word("micDenied") || message == word("locationDenied")) {
                        TextButton(onClick = {
                            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:$packageName")))
                        }) { Text(word("openAppSettings")) }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }

    @Composable
    private fun LanguagePicker() {
        Text(word("language"), style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("en" to "English", "hi" to "हिन्दी").forEach { (code, label) ->
                FilterChip(selected = profile.language == code, onClick = {
                    profile = profile.copy(language = code)
                    store.saveProfile(profile)
                    tts?.language = locale()
                    message = ""
                }, label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    leadingIcon = { if (profile.language == code) RideIcon("check", Modifier.size(20.dp)) })
            }
        }
    }

    @Composable
    private fun Onboarding() {
        Text(word("welcome"), style = MaterialTheme.typography.headlineMedium)
        Text(word("setupHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard { LanguagePicker() }
        OutlinedTextField(value = profile.name, onValueChange = {
            profile = profile.copy(name = it)
            store.saveProfile(profile)
        }, label = { Text(word("name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(word("places"), style = MaterialTheme.typography.titleLarge)
        places.forEach { place -> PlaceRow(place, true) }
        if (places.none { it.isHome }) {
            LargeButton(word("addHome")) { openEditor(null, true) }
        } else {
            OutlinedButton(onClick = { openEditor(null, false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(word("addPlace")) }
            Text(word("demo"), style = MaterialTheme.typography.bodyLarge)
            if (!uberInstalled()) {
                Text(word("uberInstall"))
                LargeButton(word("install")) { openStore() }
            }
            LargeButton(word("finish")) {
                if (profile.name.isBlank()) message = word("name")
                else {
                    profile = profile.copy(completed = true)
                    store.saveProfile(profile)
                    message = ""
                    screen = "home"
                }
            }
        }
    }

    @Composable
    private fun Home() {
        Text("${word("hello")}, ${profile.name}", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(word("rideTo"), style = MaterialTheme.typography.headlineLarge)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.padding(20.dp)) { RideIcon("mic", Modifier.size(40.dp)) }
                }
                Text(if (listening) word("listening") else word("voiceHint"),
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                SpeechTranscript()
                LargeButton(if (listening) word("stop") else word("speak")) {
                    if (listening) stopListening() else requestMicrophone()
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(word("places"), style = MaterialTheme.typography.titleLarge)
            Text(word("tapHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        places.sortedByDescending { it.isHome }.forEach { place -> PlaceRow(place, false) }
        Text(word("handoffHint"), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    private fun PlaceRow(place: SavedPlace, editing: Boolean) {
        Surface(onClick = {
            if (editing) openEditor(place, place.isHome) else choose(place)
        }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface) {
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
        Column(Modifier.fillMaxWidth()) {
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
                SectionCard { LanguagePicker() }
                Text(word("manage"), style = MaterialTheme.typography.titleLarge)
                places.forEach { PlaceRow(it, true) }
            }
            Surface(shadowElevation = 4.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
        Text(word("handoffHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (handoffInProgress) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LargeButton(if (handoffInProgress) word("wait") else word("yes"), enabled = !handoffInProgress) { confirmRide() }
        if (!uberInstalled()) LargeButton(word("install")) { openStore() }
        if (message == word("locationUnavailable")) {
            OutlinedButton(onClick = {
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }, modifier = Modifier.fillMaxWidth()) { Text(word("openLocation")) }
        }
        OutlinedButton(onClick = { cancelRide() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(word("no"))
        }
        OutlinedButton(onClick = { if (listening) stopListening() else requestMicrophone() }, enabled = !handoffInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (listening) word("stop") else word("speak"))
        }
        MapPreview(mapUrl, profile.language)
    }

    @Composable
    private fun Clarification() {
        Text(word("ambiguous"), style = MaterialTheme.typography.headlineMedium)
        SpeechTranscript()
        choices.forEach { PlaceRow(it, false) }
        OutlinedButton(onClick = { cancelRide() }, modifier = Modifier.fillMaxWidth()) { Text(word("cancel")) }
    }

    @Composable
    private fun LargeButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
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
        val provider = searchProvider()
        searchThread = Thread {
            val result = runCatching { provider.search(query, language) }
            runOnUiThread {
                if (generation != searchGeneration || isDestroyed || screen != "editor") return@runOnUiThread
                searchThread = null
                searching = false
                result.onSuccess {
                    searchResults = it
                    if (it.isEmpty()) message = word("noResults")
                }.onFailure { error ->
                    message = word(when ((error as? PlaceSearchException)?.reason) {
                        PlaceSearchFailure.NOT_CONFIGURED -> "configured"
                        PlaceSearchFailure.ACCESS_DENIED -> "searchAccessDenied"
                        PlaceSearchFailure.QUOTA -> "searchQuota"
                        PlaceSearchFailure.UNAVAILABLE, PlaceSearchFailure.INVALID_RESPONSE -> "searchUnavailable"
                        null -> "offline"
                    })
                }
            }
        }.also { it.start() }
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

    private fun choose(place: SavedPlace) {
        stopListening()
        selected = place
        choices = emptyList()
        screen = "confirm"
        message = ""
        speak("${word("confirm")} ${if (place.isHome) word("home") else place.name}. ${place.address}")
    }

    private fun cancelRide() {
        stopListening()
        tts?.stop()
        cancelLocation()
        selected = null
        message = ""
        screen = "home"
    }

    private fun requestMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (screen !in listOf("home", "confirm") || handoffInProgress) return
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
        if (screen !in listOf("home", "confirm") || handoffInProgress) return
        if (raw.isBlank()) { message = word("speechFailed"); return }
        val decision = VoiceCommands.decision(raw)
        if (decision == VoiceDecision.Cancel) { cancelRide(); return }
        if (screen == "confirm") {
            when (decision) {
                VoiceDecision.Yes -> confirmRide()
                VoiceDecision.No -> cancelRide()
                else -> message = word("speechFailed")
            }
            return
        }
        if (decision == VoiceDecision.No) { message = word("unknown"); return }
        val matched = DestinationResolver.matches(raw, places)
        when (matched.size) {
            0 -> { message = word("unknown"); speak(message) }
            1 -> choose(matched.first())
            else -> { choices = matched; screen = "clarify"; message = ""; speak(word("ambiguous")) }
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

    private fun fetchLocation() {
        if (!handoffInProgress || screen != "confirm") return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            handoffInProgress = false; message = word("locationUnavailable"); return
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
                    handoffInProgress = false; message = word("locationUnavailable"); return@addOnSuccessListener
                }
                try {
                    startActivity(UberHandoff.intent(place, location.latitude, location.longitude))
                    message = ""
                    selected = null
                    screen = "home"
                } catch (_: ActivityNotFoundException) { message = word("handoffFailed") }
                finally { handoffInProgress = false }
            }.addOnFailureListener {
                if (generation == locationGeneration && !isDestroyed) {
                    handoffInProgress = false; message = word("locationUnavailable")
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

    override fun onPause() {
        super.onPause()
        stopListening()
        tts?.stop()
    }

    override fun onStop() {
        super.onStop()
        if (handoffInProgress) { cancelLocation(); message = word("rideInterrupted") }
    }

    override fun onDestroy() {
        cancelAddressSearch()
        cancelLocation()
        stopListening()
        tts?.shutdown()
        super.onDestroy()
    }
}
