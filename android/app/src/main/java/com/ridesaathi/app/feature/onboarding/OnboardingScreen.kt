package com.ridesaathi.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.theme.*
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.feature.tutorial.TutorialController

@Composable
internal fun OnboardingScreen(
    step: OnboardingStep, introPage: Int, slides: List<TutorialController.TutorialSlide>,
    profile: Profile, places: List<SavedPlace>, uberInstalled: Boolean,
    word: (String) -> String, onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit, onSpeak: (String) -> Unit,
    onNext: () -> Unit, onEdit: (SavedPlace) -> Unit,
    onAddPlace: () -> Unit, onInstall: () -> Unit, modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val next = { focus.clearFocus(); onNext() }
    val home = places.firstOrNull { it.isHome }
    LaunchedEffect(step, introPage, profile.language) {
        if (step == OnboardingStep.Introduction) slides.getOrNull(introPage)?.let {
            onSpeak("${word(it.titleKey)}. ${word(it.bodyKey)}")
        }
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 480.dp || maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        AnimatedContent(step, label = "setupPage", modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()) { active ->
            if (active == OnboardingStep.Home && home == null) {
                HomeSetupContent(word, next)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = if (compact) 16.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)
                    ) {
                        if (active == OnboardingStep.Introduction) {
                            val slide = slides[introPage]
                            SetupHeading(
                                word(slide.titleKey).replace('\n', ' '), word(slide.bodyKey).replace('\n', ' '),
                                Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp), titleSize = 32
                            )
                            IntroIllustration(Modifier.widthIn(max = 420.dp).clickable(onClickLabel = word("replayAudio")) {
                                onSpeak("${word(slide.titleKey)}. ${word(slide.bodyKey)}")
                            })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                slides.indices.forEach { index ->
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (index == introPage) RideColors.Emerald else RideColors.Border))
                                }
                            }
                        } else {
                            if (!compact) {
                                Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    val artwork = Modifier.widthIn(max = 320.dp).clip(RideShapes.large)
                                    when (active) {
                                        OnboardingStep.Language -> LanguageIllustration(artwork)
                                        OnboardingStep.Name -> NameIllustration(artwork)
                                        OnboardingStep.Home -> HomeIllustration(artwork)
                                        else -> SavedPlacesIllustration(artwork)
                                    }
                                }
                            }
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = if (compact) 16.dp else 0.dp),
                                verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)
                            ) {
                                SetupHeading(word(active.titleKey).replace('\n', ' '), word(active.hintKey).replace('\n', ' '),
                                    titleSize = 28, bodySize = 16, textAlign = TextAlign.Start)
                                when (active) {
                                    OnboardingStep.Language -> RideLanguageChoices(profile.language, onLanguageChange)
                                    OnboardingStep.Name -> NameEntry(profile.name, word, onNameChange, next)
                                    OnboardingStep.Home -> if (home != null) PlaceRow(home, word) { onEdit(home) }
                                    OnboardingStep.Places -> {
                                        places.sortedByDescending { it.isHome }.forEach { place -> PlaceRow(place, word) { onEdit(place) } }
                                        RidePlaceCard(word("addPlace"), "plus", onAddPlace)
                                        if (!uberInstalled) RideSecondaryButton(word("install"), onInstall, icon = "uber")
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    RideActionFooter(word(if (active == OnboardingStep.Introduction && introPage == 0) "getStarted" else "continue"),
                        onClick = next, enabled = active != OnboardingStep.Name || profile.name.isNotBlank())
                }
            }
        }
    }
}

@Composable
private fun NameEntry(name: String, word: (String) -> String, onChange: (String) -> Unit, onNext: () -> Unit) {
    val requester = remember { FocusRequester() }
    val inspection = LocalInspectionMode.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!inspection) { requester.requestFocus(); keyboard?.show() }
    }
    RideSearchField(name, onChange, word("name"), icon = "person", focusRequester = requester, imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { if (name.isNotBlank()) onNext() }),
        trailing = {
            if (name.isNotEmpty()) IconButton(onClick = { onChange("") }, modifier = Modifier.semantics { contentDescription = word("clear") }) {
                RideIcon("close", Modifier.size(18.dp), RideColors.Slate)
            }
        })
}

@Composable
internal fun SetupHeading(title: String, body: String, modifier: Modifier = Modifier, titleSize: Int = 32, bodySize: Int = 17, textAlign: TextAlign = TextAlign.Center) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = titleSize.sp, lineHeight = (titleSize + 3).sp), textAlign = textAlign, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = bodySize.sp, lineHeight = (bodySize + 6).sp), color = RideColors.Slate, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
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
