package com.ridesaathi.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

// Real feature screens at normal, compact, keyboard-reduced and large-text viewports.

@PreviewTest
@Preview(widthDp = 390, heightDp = 780, fontScale = 1f)
@Composable fun IntroReference() = IntroPreview()

@PreviewTest
@Preview(widthDp = 390, heightDp = 780, fontScale = 1f)
@Composable fun LanguageReference() = LanguagePreview()

@PreviewTest
@Preview(widthDp = 390, heightDp = 424, fontScale = 1f)
@Composable fun NameReference() = NamePreview()

@PreviewTest
@Preview(widthDp = 390, heightDp = 780, fontScale = 1f)
@Composable fun SaveHomeReference() = SaveHomePreview()

@PreviewTest
@Preview(widthDp = 390, heightDp = 780, fontScale = 1f)
@Composable fun AddressReference() = AddressPreview()

@PreviewTest
@Preview(widthDp = 390, heightDp = 780, fontScale = 1f)
@Composable fun SettingsReference() = SettingsPreview()

@PreviewTest
@Preview(widthDp = 390, heightDp = 780, fontScale = 1f)
@Composable fun HomeReference() = HomePreview()

@PreviewTest
@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Composable fun HomeSmallPhone() = HomePreview()

@PreviewTest
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.4f)
@Composable fun LanguageLargeText() = LanguagePreview()

@PreviewTest
@Preview(widthDp = 360, heightDp = 424, fontScale = 1.4f)
@Composable fun NameLargeText() = NamePreview()

@PreviewTest
@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Composable fun SettingsSmallPhone() = SettingsPreview()

@PreviewTest
@Preview(widthDp = 430, heightDp = 860, fontScale = 1f)
@Composable fun HomeWidePhone() = HomePreview()

@PreviewTest
@Preview(widthDp = 360, heightDp = 424, fontScale = 1f)
@Composable fun NameEmpty() = EmptyNamePreview()
