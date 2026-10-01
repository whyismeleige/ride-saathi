# Changed files

Visual implementation and layout usability pass on `feat/v1-ui-redesign`.

## UI, navigation and localization

- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/HomeSetupContent.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/LargeButton.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/MapPreview.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/PlaceRow.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideActionFooter.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideButtons.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideIcon.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideIllustrations.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RidePlaceCard.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RidePrimitives.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideScenicHeader.kt` — removed
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideScreenHeader.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideSearchField.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/components/RideVoiceButton.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/theme/RideColors.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/theme/RideShapes.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/theme/RideSpacing.kt`
- `android/app/src/main/java/com/ridesaathi/app/core/ui/theme/RideTypography.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/home/HomeScreen.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/onboarding/OnboardingScreen.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/places/AddressPicker.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/places/PlaceEditorScreen.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/settings/SettingsScreen.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/sharedlocation/ClarificationScreen.kt`
- `android/app/src/main/java/com/ridesaathi/app/feature/tutorial/TutorialScreen.kt`
- `android/app/src/main/java/com/ridesaathi/app/localization/English.kt`
- `android/app/src/main/java/com/ridesaathi/app/localization/Hindi.kt`
- `android/app/src/main/java/com/ridesaathi/app/localization/Telugu.kt`
- `android/app/src/main/java/com/ridesaathi/app/navigation/AppNavigation.kt`

## Artwork

- `android/app/src/main/res/drawable-nodpi/address_art.webp`
- `android/app/src/main/res/drawable-nodpi/booking_art.webp`
- `android/app/src/main/res/drawable-nodpi/brand_car.webp`
- `android/app/src/main/res/drawable-nodpi/home_art.webp`
- `android/app/src/main/res/drawable-nodpi/intro_art.webp`
- `android/app/src/main/res/drawable-nodpi/language_art.webp`
- `android/app/src/main/res/drawable-nodpi/name_art.webp`
- `android/app/src/main/res/drawable-nodpi/place_home.webp`
- `android/app/src/main/res/drawable-nodpi/place_hospital.webp`
- `android/app/src/main/res/drawable-nodpi/place_temple.webp`
- `android/app/src/main/res/drawable-nodpi/place_work.webp`
- `android/app/src/main/res/drawable-nodpi/saved_places_art.webp`

## Previews, screenshot baselines and tests

- `android/app/src/debug/java/com/ridesaathi/app/preview/RideComponentPreviews.kt`
- `android/app/src/debug/java/com/ridesaathi/app/preview/RidePreviews.kt`
- `android/app/src/screenshotTest/kotlin/com/ridesaathi/app/preview/ReferenceScreens.kt`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/AddressReference_4ea545b6_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/HomeReference_4ea545b6_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/HomeSmallPhone_b748b7d9_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/HomeWidePhone_428b312c_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/IntroReference_4ea545b6_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/LanguageLargeText_d67ff883_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/LanguageReference_4ea545b6_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/NameEmpty_4b8c9868_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/NameLargeText_8cdebcf2_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/NameReference_b913b642_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/SaveHomeReference_4ea545b6_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/SettingsReference_4ea545b6_0.png`
- `android/app/src/screenshotTestDebug/reference/com/ridesaathi/app/preview/ReferenceScreensKt/SettingsSmallPhone_b748b7d9_0.png`
- `android/app/src/test/java/com/ridesaathi/app/localization/WordsTest.kt`

## Build configuration

- `android/app/build.gradle.kts`
- `android/build.gradle.kts`
- `android/gradle.properties`

## Design documentation and comparison tools

- `docs/design/CHANGED_FILES.md`
- `docs/design/IMPLEMENTATION.md`
- `docs/design/LAYOUT_REVIEW.md`
- `docs/design/VALIDATION.md`
- `docs/design/comparisons/Address.png`
- `docs/design/comparisons/Home.png`
- `docs/design/comparisons/Intro.png`
- `docs/design/comparisons/Language.png`
- `docs/design/comparisons/Name.png`
- `docs/design/comparisons/SaveHome.png`
- `docs/design/comparisons/Settings.png`
- `docs/design/layout-review/Home-after.png`
- `docs/design/layout-review/Home-before.png`
- `docs/design/layout-review/Language-after.png`
- `docs/design/layout-review/Language-before.png`
- `docs/design/layout-review/Name-after.png`
- `docs/design/layout-review/Name-before.png`
- `docs/design/layout-review/Settings-after.png`
- `docs/design/layout-review/Settings-before.png`
- `docs/design/tools/compare_screens.py`
- `docs/design/tools/extract_artwork.py`
