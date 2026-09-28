package com.ridesaathi.app.feature.tutorial

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.LargeButton
import com.ridesaathi.app.core.ui.components.RideIcon
import com.ridesaathi.app.core.ui.components.SectionCard

@Composable
internal fun TutorialScreen(
    state: TutorialUiState,
    slides: List<TutorialController.TutorialSlide>,
    language: String,
    word: (String) -> String,
    onSpeak: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val slide = slides.getOrNull(state.tutorialStep) ?: return
    val isLast = state.tutorialStep == slides.lastIndex
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300),
        label = "tutorialStepAlpha"
    )
    LaunchedEffect(state.tutorialMode, state.tutorialStep, language) {
        onSpeak("${word(slide.titleKey)}. ${word(slide.bodyKey)}")
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.alpha(animatedAlpha)
    ) {
        LinearProgressIndicator(
            progress = { (state.tutorialStep + 1).toFloat() / slides.size.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            word(if (state.tutorialMode == TutorialMode.Intro) "introTitle" else "tutorialTitle"),
            style = MaterialTheme.typography.headlineMedium
        )
        SectionCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.padding(28.dp)) { RideIcon(slide.icon, Modifier.size(if (slide.icon == "uber") 72.dp else 56.dp)) }
                }
            }
            Text(
                word(slide.titleKey),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                word(slide.bodyKey),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedButton(
            onClick = { onSpeak("${word(slide.titleKey)}. ${word(slide.bodyKey)}") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            RideIcon("mic", Modifier.size(18.dp))
            Text(word("replayAudio"))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.tutorialStep > 0) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                ) { Text(word("back")) }
            }
            LargeButton(
                label = word(
                    if (isLast) {
                        if (state.tutorialMode == TutorialMode.Intro) "continueSetup" else "startUsing"
                    } else "continue"
                ),
                modifier = Modifier.weight(1f)
            ) {
                onNext()
            }
        }
    }
}

@Composable
internal fun AppSession.TutorialRoute() {
    TutorialScreen(
        tutorial.state,
        tutorial.tutorialSlides(),
        profile.language,
        ::word,
        { speak(it) },
        { tutorial.state.tutorialStep-- },
        {
            if (tutorial.state.tutorialStep == tutorial.tutorialSlides().lastIndex) tutorial.finishTutorial()
            else tutorial.state.tutorialStep++
        })
}
