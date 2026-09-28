package com.ridesaathi.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.RideIcon
import com.ridesaathi.app.core.ui.components.StickyMicrophone
import com.ridesaathi.app.feature.destination.DestinationSearchScreen
import com.ridesaathi.app.feature.home.HomeRoute
import com.ridesaathi.app.feature.onboarding.OnboardingStep
import com.ridesaathi.app.feature.onboarding.OnboardingRoute
import com.ridesaathi.app.feature.places.PlaceEditorRoute
import com.ridesaathi.app.feature.ride.RideConfirmationRoute
import com.ridesaathi.app.feature.settings.SettingsRoute
import com.ridesaathi.app.feature.sharedlocation.ClarificationRoute
import com.ridesaathi.app.feature.tutorial.TutorialRoute
import com.ridesaathi.app.navigation.SearchLocationActions

@Composable
internal fun AppSession.AppNavigation() {
    BackHandler(
        enabled = (screen == AppScreen.Onboarding && onboarding.step != OnboardingStep.Language) ||
            shared.state.resolvingSharedLocation || screen !in listOf(
            AppScreen.Home,
            AppScreen.Onboarding
        )
    ) {
        navigateBack()
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (screen != AppScreen.PlaceEditor) Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.padding(10.dp)) { RideIcon("pin") }
            }
            val title = if (screen != AppScreen.PlaceEditor) "Ride Saathi" else word(
                when {
                    editor.state.pickingAddress -> "search"
                    editor.state.editingId != null -> "editPlace"
                    editor.state.pendingHome -> "addHome"
                    else -> "addPlace"
                }
            )
            Text(
                title,
                modifier = Modifier.weight(1f)
                    .padding(start = if (screen == AppScreen.PlaceEditor) 0.dp else 12.dp),
                style = MaterialTheme.typography.titleMedium
            )
            if (screen == AppScreen.Home) TextButton(onClick = {
                shared.cancelSharedLocation(); voice.stopListening(); message = ""; screen =
                AppScreen.Settings
            }) {
                Text(word("settings"))
            }
            else if (screen != AppScreen.Onboarding || onboarding.step != OnboardingStep.Language) TextButton(onClick = { navigateBack() }) {
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
                (fadeIn(tween(260)) + slideIn + scaleIn(
                    tween(360, easing = FastOutSlowInEasing),
                    initialScale = 0.98f
                ))
                    .togetherWith(
                        fadeOut(tween(200)) + slideOut + scaleOut(
                            tween(240),
                            targetScale = 0.98f
                        )
                    )
                    .using(SizeTransform(clip = false))
            },
            modifier = Modifier.weight(1f),
            label = "screenTransition"
        ) { activeScreen ->
            key(
                activeScreen,
                if (activeScreen == AppScreen.DestinationSearch) destination.destinationSearch?.let { it.page to it.editing } else null) {
                if (activeScreen == AppScreen.PlaceEditor) {
                    PlaceEditorRoute(Modifier.fillMaxSize())
                } else if (activeScreen == AppScreen.Settings) {
                    SettingsRoute(Modifier.fillMaxSize())
                } else if (activeScreen == AppScreen.Onboarding) {
                    OnboardingRoute(Modifier.fillMaxSize())
                } else Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    when (activeScreen) {
                        AppScreen.Tutorial -> TutorialRoute()
                        AppScreen.Home -> HomeRoute()
                        AppScreen.RideConfirmation -> RideConfirmationRoute()
                        AppScreen.Clarification, AppScreen.SharedChoices -> ClarificationRoute()
                        AppScreen.DestinationSearch -> DestinationSearchScreen()
                        else -> Unit
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = message.isNotBlank(),
            enter = fadeIn(tween(180)) + slideInVertically(
                tween(
                    260,
                    easing = FastOutSlowInEasing
                )
            ) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(
                tween(
                    220,
                    easing = FastOutSlowInEasing
                )
            ) { it / 2 }
        ) {
            Surface(
                color = if (ride.state.handoffInProgress || shared.state.resolvingSharedLocation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp)
                        .animateContentSize(tween(260, easing = FastOutSlowInEasing))
                ) {
                    Text(
                        message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AnimatedVisibility(
                        shared.state.resolvingSharedLocation,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = { shared.cancelSharedLocation() }) { Text(word("cancel")) }
                        }
                    }
                    if (message == word("searchLocationRequired")) SearchLocationActions()
                    if (message == word("micDenied") || message == word("locationDenied")) {
                        TextButton(onClick = {
                            openAppSettings()
                        }) { Text(word("openAppSettings")) }
                    }
                }
            }
        }
        when (screen) {
            AppScreen.DestinationSearch -> StickyMicrophone(
                destination.destinationSearch?.loading == false,
                voice.state.listening,
                ::word,
                voice::toggleListening
            )

            AppScreen.RideConfirmation -> StickyMicrophone(
                !ride.state.handoffInProgress,
                voice.state.listening,
                ::word,
                voice::toggleListening
            )

            else -> Unit
        }
    }
}
