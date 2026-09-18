package com.ridesaathi.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    private var draftAliases by mutableStateOf("")
    private var draftAddress by mutableStateOf("")
    private var draftPosition by mutableStateOf<PlaceCandidate?>(null)
    private var searchQuery by mutableStateOf("")
    private var searchResults by mutableStateOf<List<PlaceCandidate>>(emptyList())
    private var searching by mutableStateOf(false)
    private var mapReady by mutableStateOf(false)
    private var mapFailed by mutableStateOf(false)
    private var mapReload by mutableIntStateOf(0)
    private var searchGeneration = 0
    private var searchEndpoint by mutableStateOf(ProviderEndpoints.SEARCH)
    private var mapEndpoint by mutableStateOf(ProviderEndpoints.MAP)
    private var endpointError by mutableStateOf(false)
    private var mapChecked by mutableStateOf(false)
    private var speakingConfirmation = false
    private var listening by mutableStateOf(false)
    private var speechFailures = 0
    private var handoffInProgress by mutableStateOf(false)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else message = word("micDenied")
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) fetchLocation() else { handoffInProgress = false; message = word("locationDenied") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = LocalStore(this)
        searchEndpoint = store.searchEndpoint()
        mapEndpoint = store.mapEndpoint()
        profile = store.profile()
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
        "te" -> Locale.forLanguageTag("te-IN")
        else -> Locale.forLanguageTag("en-IN")
    }
    private fun speak(value: String) {
        tts?.language = locale()
        tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "ride-saathi")
    }

    private fun navigateBack() {
        when (screen) {
            "confirm", "clarify" -> cancelRide()
            "editor" -> { searchGeneration++; searching = false; screen = if (profile.completed) "settings" else "onboarding" }
            "settings" -> screen = "home"
        }
        message = ""
    }

    @Composable
    private fun App() {
        BackHandler(enabled = screen !in listOf("home", "onboarding")) {
            if (!handoffInProgress) navigateBack()
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.padding(10.dp)) { RideIcon("pin") }
                }
                Text("Ride Saathi", modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium)
                if (screen == "home") TextButton(onClick = { stopListening(); message = ""; screen = "settings" }) {
                    Text(word("settings"))
                }
                else if (screen != "onboarding") TextButton(onClick = { navigateBack() }, enabled = !handoffInProgress) {
                    RideIcon("back", Modifier.size(18.dp)); Text(word("back"))
                }
            }
            key(screen) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (screen) {
                        "onboarding" -> Onboarding()
                        "home" -> Home()
                        "editor" -> Editor()
                        "settings" -> Settings()
                        "confirm" -> Confirmation()
                        "clarify" -> Clarification()
                    }
                }
            }
            if (message.isNotBlank()) Surface(
                color = if (handoffInProgress || message == word("servicesSaved")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()) {
                Text(message, modifier = Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
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
            listOf("en" to "English", "hi" to "हिन्दी", "te" to "తెలుగు").forEach { (code, label) ->
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
                    Text(place.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (editing) Text(word("edit"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                RideIcon("arrow", Modifier.size(18.dp))
            }
        }
    }

    @Composable
    private fun Settings() {
        Text(word("settings"), style = MaterialTheme.typography.headlineMedium)
        SectionCard { LanguagePicker() }
        Text(word("manage"), style = MaterialTheme.typography.titleLarge)
        places.forEach { PlaceRow(it, true) }
        LargeButton(word("addPlace")) { openEditor(null, false) }
        var showServices by remember { mutableStateOf(false) }
        TextButton(onClick = { showServices = !showServices }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(word("providerSettings"))
        }
        if (showServices) {
            SectionCard {
                OutlinedTextField(value = searchEndpoint, onValueChange = { searchEndpoint = it; endpointError = false },
                    label = { Text(word("searchService")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = mapEndpoint, onValueChange = { mapEndpoint = it; endpointError = false },
                    label = { Text(word("mapService")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (endpointError) Text(word("invalidService"), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    if (ProviderEndpoints.valid(searchEndpoint) && ProviderEndpoints.valid(mapEndpoint)) {
                        store.saveProviderEndpoints(searchEndpoint, mapEndpoint)
                        endpointError = false
                        message = word("servicesSaved")
                    } else endpointError = true
                }, modifier = Modifier.fillMaxWidth()) { Text(word("saveServices")) }
            }
        }
        OutlinedButton(onClick = { screen = "home" }, modifier = Modifier.fillMaxWidth()) { Text(word("done")) }
    }

    @Composable
    private fun Editor() {
        Text(if (pendingHome) word("addHome") else word("addPlace"), style = MaterialTheme.typography.headlineMedium)
        Text(word("editorHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = draftName, onValueChange = { draftName = it },
            label = { Text(word("placeName")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            enabled = !pendingHome)
        OutlinedTextField(value = draftAliases, onValueChange = { draftAliases = it },
            label = { Text(word("aliases")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = searchQuery, onValueChange = {
            searchQuery = it
            draftPosition = null
            draftAddress = ""
            mapChecked = false
            searchResults = emptyList()
        },
            label = { Text(word("address")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        LargeButton(if (searching) word("wait") else word("search"), enabled = !searching && searchQuery.isNotBlank()) { searchAddress() }
        searchResults.forEach { result ->
            ElevatedCard(onClick = {
                draftPosition = result
                draftAddress = result.address
                mapChecked = false
                mapReady = false
                mapFailed = false
                searchResults = emptyList()
                message = ""
            }, modifier = Modifier.fillMaxWidth()) {
                Text(result.address, modifier = Modifier.padding(16.dp))
            }
        }
        if (draftPosition != null) {
            Text("${word("address")}: $draftAddress", style = MaterialTheme.typography.bodyLarge)
            val point = draftPosition!!
            val mapUrl = OpenStreetMapPreviewProvider(mapEndpoint).url(point.latitude, point.longitude)
            key(mapUrl, mapReload) {
                AndroidView(factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.userAgentString = "${settings.userAgentString} RideSaathi/0.1 (com.ridesaathi.app)"
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) { mapReady = true }
                            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?) {
                                if (request?.isForMainFrame == true) mapFailed = true
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = true
                        }
                        loadUrl(mapUrl)
                    }
                }, onRelease = { it.destroy() }, modifier = Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium))
            }
            Text(word("mapAttribution"), style = MaterialTheme.typography.bodySmall)
            if (mapFailed) {
                Text(word("mapUnavailable"), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    mapReady = false
                    mapFailed = false
                    mapChecked = false
                    mapReload++
                }, modifier = Modifier.fillMaxWidth()) { Text(word("retry")) }
            }
            Row(Modifier.fillMaxWidth().toggleable(value = mapChecked, enabled = mapReady && !mapFailed,
                role = Role.Checkbox, onValueChange = { mapChecked = it }).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = mapChecked, enabled = mapReady && !mapFailed, onCheckedChange = null)
                Text(word("checkMap"), modifier = Modifier.padding(start = 12.dp))
            }
        }
        LargeButton(word("save"), enabled = draftPosition != null && mapChecked && mapReady && !mapFailed) { savePlace() }
        if (editingId != null && !pendingHome) {
            OutlinedButton(onClick = { deletePlace() }, modifier = Modifier.fillMaxWidth()) { Text(word("delete")) }
        }
        OutlinedButton(onClick = { screen = if (profile.completed) "settings" else "onboarding"; message = "" },
            modifier = Modifier.fillMaxWidth()) { Text(word("back")) }
    }

    @Composable
    private fun Confirmation() {
        val place = selected ?: return
        Text(word("confirm"), style = MaterialTheme.typography.headlineMedium)
        SectionCard {
            RideIcon(if (place.isHome) "home" else "pin", Modifier.size(36.dp))
            Text(if (place.isHome) word("home") else place.name, style = MaterialTheme.typography.headlineLarge)
            Text(place.address, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        OutlinedButton(onClick = { cancelRide() }, enabled = !handoffInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(word("no"))
        }
        OutlinedButton(onClick = { if (listening) stopListening() else requestMicrophone() }, enabled = !handoffInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (listening) word("stop") else word("speak"))
        }
    }

    @Composable
    private fun Clarification() {
        Text(word("ambiguous"), style = MaterialTheme.typography.headlineMedium)
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
        pendingHome = home
        editingId = place?.id
        draftName = if (home) word("home") else place?.name.orEmpty()
        draftAliases = place?.aliases?.joinToString(", ").orEmpty()
        draftAddress = place?.address.orEmpty()
        draftPosition = place?.let { PlaceCandidate(it.address, it.latitude, it.longitude) }
        searchQuery = place?.address.orEmpty()
        searchResults = emptyList()
        mapReady = false
        mapFailed = false
        searchGeneration++
        searching = false
        mapChecked = false
        message = ""
        screen = "editor"
    }

    private fun searchAddress() {
        val query = searchQuery.trim()
        if (query.isBlank()) return
        if (!ProviderEndpoints.valid(searchEndpoint)) { message = word("invalidService"); return }
        val generation = ++searchGeneration
        searching = true
        searchResults = emptyList()
        message = ""
        val endpoint = searchEndpoint
        val language = locale().toLanguageTag()
        Thread {
            val result = runCatching { NominatimPlaceSearchProvider(endpoint).search(query, language) }
            runOnUiThread {
                if (generation != searchGeneration || isDestroyed) return@runOnUiThread
                searching = false
                result.onSuccess {
                    searchResults = it
                    if (it.isEmpty()) message = word("noResults")
                }.onFailure { message = word("offline") }
            }
        }.start()
    }

    private fun savePlace() {
        val point = draftPosition ?: run { message = word("invalidPlace"); return }
        val name = draftName.trim()
        if (name.isBlank() || draftAddress.isBlank() || !mapChecked) { message = word("invalidPlace"); return }
        val aliases = draftAliases.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val names = listOf(name) + aliases
        val collision = places.filterNot { it.id == editingId }.any { other ->
            (listOf(other.name) + other.aliases).any { existing -> names.any { it.equals(existing, true) } }
        }
        if (collision) { message = word("duplicate"); return }
        val old = places.firstOrNull { it.id == editingId }
        val saved = SavedPlace(id = old?.id ?: java.util.UUID.randomUUID().toString(),
            name = name, aliases = aliases, address = draftAddress,
            latitude = point.latitude, longitude = point.longitude, isHome = pendingHome)
        places = places.filterNot { it.id == editingId } + saved
        store.savePlaces(places)
        message = ""
        screen = if (profile.completed) "settings" else "onboarding"
    }

    private fun deletePlace() {
        if (pendingHome) { message = word("replaceHome"); return }
        places = places.filterNot { it.id == editingId }
        store.savePlaces(places)
        message = ""
        screen = if (profile.completed) "settings" else "onboarding"
    }

    private fun choose(place: SavedPlace) {
        stopListening()
        selected = place
        choices = emptyList()
        speakingConfirmation = true
        screen = "confirm"
        message = ""
        speak("${word("confirm")} ${if (place.isHome) word("home") else place.name}. ${place.address}")
    }

    private fun cancelRide() {
        stopListening()
        tts?.stop()
        handoffInProgress = false
        selected = null
        speakingConfirmation = false
        message = ""
        screen = "home"
    }

    private fun requestMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { message = word("voiceUnavailable"); return }
        tts?.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { service ->
            service.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; message = "" }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) { listening = false; message = word("speechFailed"); speechFailures++ }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    handleSpeech(heard)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            service.startListening(intent)
        }
    }

    private fun stopListening() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun handleSpeech(raw: String) {
        val text = raw.trim().lowercase()
        if (text.isBlank()) { message = word("speechFailed"); return }
        val cancelWords = listOf("cancel", "stop", "रद्द", "बंद", "రద్దు", "ఆపు")
        if (cancelWords.any { text.contains(it) }) { cancelRide(); return }
        if (screen == "confirm") {
            if (listOf("yes", "yeah", "हाँ", "हां", "అవును").any { text.contains(it) }) confirmRide()
            else if (listOf("no", "नहीं", "కాదు", "వద్దు").any { text.contains(it) }) cancelRide()
            else message = word("speechFailed")
            return
        }
        val matched = DestinationResolver.matches(text, places)
        when (matched.size) {
            0 -> { message = word("unknown"); speak(message) }
            1 -> choose(matched.first())
            else -> { choices = matched; screen = "clarify"; message = ""; speak(word("ambiguous")) }
        }
    }

    private fun confirmRide() {
        if (handoffInProgress) return
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

    private fun fetchLocation() {
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
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(request, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
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
            }.addOnFailureListener { handoffInProgress = false; message = word("locationUnavailable") }
    }

    private fun uberInstalled(): Boolean = try {
        packageManager.getPackageInfo(UberHandoff.packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${UberHandoff.packageName}"))
        try { startActivity(market) }
        catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${UberHandoff.packageName}")))
        }
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        tts?.stop()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
