package com.ridesaathi.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.theme.RideShapes
import com.ridesaathi.app.core.ui.theme.RideColors
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.feature.tutorial.TutorialController

@Composable
internal fun OnboardingScreen(
    step: OnboardingStep,
    introPage: Int,
    slides: List<TutorialController.TutorialSlide>,
    profile: Profile,
    places: List<SavedPlace>,
    uberInstalled: Boolean,
    word: (String) -> String,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onNext: () -> Unit,
    onEdit: (SavedPlace) -> Unit,
    onAddPlace: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val next = { focus.clearFocus(); onNext() }
    val home = places.firstOrNull { it.isHome }
    LaunchedEffect(step, introPage, profile.language) {
        if (step == OnboardingStep.Introduction) {
            val slide = slides[introPage]
            onSpeak("${word(slide.titleKey)}. ${word(slide.bodyKey)}")
        }
    }
    Column(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                word("onboardingStep").replace("{current}", "${step.ordinal + 1}")
                    .replace("{total}", "${OnboardingStep.entries.size}"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                Modifier.fillMaxWidth().semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        (step.ordinal + 1).toFloat(), 0f..OnboardingStep.entries.size.toFloat()
                    )
                },
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OnboardingStep.entries.forEach { stage ->
                    val color by animateColorAsState(
                        if (stage.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        label = "setupProgress"
                    )
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
                }
            }
        }
        AnimatedContent(
            targetState = step to if (step == OnboardingStep.Introduction) introPage else 0,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState.first.ordinal > initialState.first.ordinal ||
                    (targetState.first == initialState.first && targetState.second > initialState.second)
                (fadeIn(tween(240)) + slideInHorizontally(tween(300)) { if (forward) it / 8 else -it / 8 })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { if (forward) -it / 8 else it / 8 })
            },
            label = "onboardingStep"
        ) { (activeStep, page) ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val slide = if (activeStep == OnboardingStep.Introduction) slides[page] else null
                if (activeStep != OnboardingStep.Introduction) {
                    RideScenicHeader(home = activeStep == OnboardingStep.Home || activeStep == OnboardingStep.Places)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        word(slide?.titleKey ?: activeStep.titleKey),
                        style = if (activeStep == OnboardingStep.Language) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                        textAlign = TextAlign.Center
                    )
                    Text(
                        word(slide?.bodyKey ?: activeStep.hintKey),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
                when (activeStep) {
                    OnboardingStep.Language -> RideLanguageChoices(profile.language, onLanguageChange)
                    OnboardingStep.Introduction -> {
                        RideScenicHeader(modifier = Modifier.height(220.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            slides.indices.forEach { index ->
                                Box(Modifier.size(if (index == page) 10.dp else 8.dp)
                                    .clip(CircleShape).background(if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                        RideSecondaryButton(
                            label = word("replayAudio"),
                            onClick = { slide?.let { onSpeak("${word(it.titleKey)}. ${word(it.bodyKey)}") } },
                            icon = "mic"
                        )
                    }
                    OnboardingStep.Name -> OutlinedTextField(
                        value = profile.name,
                        onValueChange = onNameChange,
                        label = { Text(word("name")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RideShapes.medium,
                        leadingIcon = { RideIcon("person") },
                        trailingIcon = { if (profile.name.isNotEmpty()) IconButton(onClick = { onNameChange("") }, modifier = Modifier.semantics { contentDescription = word("clear") }) { RideIcon("close") } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { if (profile.name.isNotBlank()) next() })
                    )
                    OnboardingStep.Home -> {
                        if (home != null) {
                            SetupNote("check", word("onboardingHomeSaved"))
                            PlaceRow(home, word) { onEdit(home) }
                        } else {
                            Text(word("homeRequired"), color = RideColors.Emerald)
                            RideCandidateCard(word("search"), word("onboardingHomeSearch"), onClick = next)
                        }
                    }
                    OnboardingStep.Places -> {
                        val additional = places.filterNot { it.isHome }
                        additional.forEach { place -> PlaceRow(place, word) { onEdit(place) } }
                        RideSecondaryButton(word("addPlace"), onClick = onAddPlace, icon = "pin")
                        if (!uberInstalled) {
                            RideEmptyState(word("uberInstall"))
                            RideSecondaryButton(word("install"), onClick = onInstall, icon = "uber")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                LargeButton(
                    label = word(when {
                        step == OnboardingStep.Introduction && introPage == 0 -> "getStarted"
                        step == OnboardingStep.Places -> "finish"
                        step == OnboardingStep.Home && home == null -> "addHome"
                        else -> "continue"
                    }),
                    enabled = step != OnboardingStep.Name || profile.name.isNotBlank(),
                    onClick = next
                )
            }
        }
    }
}

@Composable
private fun SetupNote(icon: String, text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(icon, Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun AppSession.OnboardingRoute(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { stopPrompt() } }
    OnboardingScreen(
        step = onboarding.step,
        introPage = onboarding.introPage,
        slides = tutorial.introSlides() + tutorial.fullTutorialSlides(),
        profile = profile,
        places = places,
        uberInstalled = uberInstalled(),
        word = ::word,
        onNameChange = onboarding::updateName,
        onLanguageChange = ::selectLanguage,
        onSpeak = { speak(it) },
        onNext = onboarding::next,
        onEdit = { editor.openEditor(it, it.isHome) },
        onAddPlace = { editor.openEditor(null, false) },
        onInstall = ::openStore,
        modifier = modifier
    )
}
