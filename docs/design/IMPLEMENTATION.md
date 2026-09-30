# Ride Saathi v1 UI implementation

This branch is `feat/v1-ui-redesign`. It is a visual refactor of the existing Compose app:
the app is still one Gradle module with the same `AppSession`, feature controllers, typed
`AppScreen` navigation and `LocalStore` format. No architecture, storage or API changed.

## Visual design tokens

`core/ui/theme` owns the palette, typography, shapes and spacing.

| Token | Value | Use |
| --- | --- | --- |
| Navy | `#09285D` | Primary text, wordmark, icons |
| Emerald | `#078365` | Primary action fill, selected outlines, icon tint |
| Mint | `#DDF6E9` | Voice rings, selected/primary result tint |
| Cream | `#FFFAF1` | App background |
| Sky | `#E6F4FF` | Scenic sky, secondary container |
| Orange | `#C65B15` | Home badge icon and roof artwork |
| Peach | `#FFEBD9` | Home badge container, illustration accents |
| Lavender | `#EEE8FC` | Telugu language badge |
| Slate | `#526580` | Secondary/supporting text |
| Border | `#DCE8E2` | Very subtle 1dp card borders |
| Red | `#AC383E` | Destructive and error text |

Orange is a darker shade than the concept's `#FF922E` because that orange fails contrast as
text on cream; it is used for icon strokes and artwork instead.

- Typography: Android sans-serif. 34/42sp hero, 30/38sp heading, 22/30sp section title,
  18/26sp card title, 18sp and 16sp body, 17sp button label. All styles wrap and scale with
  the system font-size setting; no fixed heights on text containers.
- Shapes: 16dp extra small through 32dp extra large, plus `RidePill` for primary buttons.
- Spacing: `RideSpacing` provides Small/Medium/Screen/Control/Badge tokens; individual layouts
  use local spacing where that expresses the screen better.
- System chrome: transparent status and navigation bars with dark icons, `safeDrawingPadding`
  and `imePadding` on the shell root. No simulated status bar is drawn — the concept boards
  show iOS chrome, which this Android app deliberately does not imitate.

## Reusable components

| Component | File | Purpose |
| --- | --- | --- |
| `RideScreenHeader` | `RideScreenHeader.kt` | Screen-specific shell header; null `onBack` hides the control |
| `RideHeaderAction` | `RidePlaceCard.kt` | Circular header action (Settings entry point) |
| `RideSaathiLogo`, `CircularBackButton`, `RideIconBadge`, `RideSectionTitle` | `RidePrimitives.kt` | Wordmark, back control, circular badges, heading semantics |
| `RideLanguageChoices`, `RideSelectionCard`, `RideLanguageBadge` | `RidePrimitives.kt`, `RideButtons.kt` | Single-choice rows shared by onboarding and Settings |
| `RidePlaceCard`, `RideCandidateCard` | `RidePlaceCard.kt` | The single card treatment for saved places, address results and destination candidates |
| `RideSecondaryButton` | `RideButtons.kt` | Outlined pill secondary action, 56dp minimum |
| `RideSearchField` | `RideSearchField.kt` | Rounded search input with optional focus, supporting status line and trailing affordance |
| `RideLoadingState`, `RideErrorState`, `RideEmptyState` | `RideStates.kt` | Consistent loading/error/empty treatments |
| `RideVoiceButton`, `RideVoicePulse` | `RideVoiceButton.kt` | Dominant microphone and its concentric mint rings |
| `RideScenicHeader` | `RideScenicHeader.kt` | Decorative Canvas artwork |
| `LargeButton`, `PlaceRow`, `SectionCard`, `SpeechTranscript`, `ExpandableAddress`, `MapPreview`, `StickyMicrophone`, `RideIcon` | existing | Reused, restyled to the new palette and shapes |

`PlaceRow` is now a thin wrapper over `RidePlaceCard`, and `AddressPicker` results render
through the same card, so there is a single card treatment rather than three.

## Reference mapping

| Reference | Compose implementation |
| --- | --- |
| `ride-saathi-opening-screen.png` | `OnboardingScreen` introduction stage, `TutorialScreen` |
| `ride-saathi-choose-your-language-screen.png` | `OnboardingScreen` language stage, shared `RideLanguageChoices` |
| `ride-saathi-user-name-enter-screen.png` | `OnboardingScreen` name stage: required `profile.name`, person icon, clear action, IME Next |
| `ride-saathi-home-saving-screen.png` | `OnboardingScreen` Home stage, `PlaceEditorScreen` |
| `ride-saathi-save-address-screen.png` | `AddressPicker`: focused search, scrollable real results, badges, loading/empty states, attribution |
| `saved-places-screen.png` | `SettingsScreen`, onboarding places stage |
| `booking-screen.png` | `HomeScreen`: real profile name, central voice action, Home-first quick places, manual entry |
| `ride-saathi-entire-reference.png` | `DestinationSearchScreen`, `ClarificationScreen`, `RideConfirmationScreen`, tutorial styling |

## Illustrations

`RideScenicHeader` draws normalized Canvas artwork — sky gradient, clouds, buildings, mint
hills, road, trees, an optional house and a small car. No reference bitmap is used at runtime
and no screenshot is rendered behind invisible controls; every control and label is real
Compose UI. The component is decorative only, isolated from screen logic, and called through a
single function so final vector assets can replace it without touching screens.

Future assets that could replace the Canvas artwork: separate introduction (person + skyline),
name (friendly figure), Home (house + garden), city, and a simplified travel/road variant.

## Deliberate deviations from the mockups

- **Header.** The concept boards use per-screen headers, so the generic global top row was
  replaced by `RideScreenHeader`. Back appears only where Android Back is valid, and the
  wordmark is hidden on onboarding where the stage heading already carries the identity. This
  preserves the existing Back contract rather than the mockup's back button on the first
  language screen, which onboarding rules forbid.
- **Greeting.** `HomeScreen` uses the localized `hello` plus the saved profile name. The
  concept's "Good morning" is time-of-day logic with no existing support; the name is never
  hardcoded.
- **Bottom navigation.** The concept shows Book Ride and History. There is no Ride Saathi trip
  history feature in `AppScreen`, so no bottom bar and no History destination were invented.
- **Ride confirmation.** While the handoff is in progress the primary action stays labelled
  "Yes, open Uber" and disabled, with progress shown in a separate loading state, rather than
  the button relabelling itself with a loading string.
- **Saved places.** Quick cards and rows show real saved-place names with semantic Home/pin
  badges. The `SavedPlace` model has no Work/category field, so no Work, clinic or temple
  examples exist.
- **Address picking.** Result selection goes straight to the existing editor state, so cards
  use a chevron rather than a persistent radio selection. There is no current-location or
  map-picker action in `PlaceEditorController`, so neither was fabricated. Distances are
  omitted because `PlaceCandidate` does not supply them.
- **Language step.** The mockup shows a circular back button; the app does not, because Back
  must not unwind past the start of onboarding.

## Behaviour deliberately left unchanged

Speech recognition and TTS, language selection, persisted profile and places, the Home
requirement, saved-place editing, current-location lookups, address search, destination search
and paging, clarification and shared links, permission handling, map provider and WebView
lifecycle, Uber deep-link handoff and Play Store/web fallback, debounce and cancellation
contracts, `LocalStore` keys and JSON format, and the navigation and Back rules. Backend APIs,
storage format, DI and navigation frameworks are unchanged. No new runtime dependencies.

## Concept screens intentionally not implemented

Vehicle/fare selection, finding a driver, driver details and photos, driver tracking, trip
progress, arrival and trip history. Uber owns booking, tracking and payment in V1. These were
treated as style references only.

## Previews and accessibility

`app/src/debug/.../preview/RidePreviews.kt` covers intro, language, name, Home setup, address
results, saved places/Settings, booking, destination search, clarification and confirmation.
`RideComponentPreviews.kt` covers the header, buttons, selection rows, cards, voice states,
scenic artwork and the loading/error/empty states. Fixtures exist only in the debug source set.

Minimum touch targets are 48dp, with 56dp for secondary controls and 64dp for primary
buttons. The voice action exposes both an action label and a state description. Language rows
expose radio-button semantics. Headings expose heading semantics. Forms and result lists
scroll, the name input supports IME Next, and the shell handles safe-area and IME insets.

Not verified here: runtime TalkBack output, physical keyboard overlap, rendering at large font
scales on a device, and landscape on real hardware. Those need a device or emulator pass.

## Validation

Run from `android/`:

```sh
./gradlew test                                        # fails: see below
./gradlew :app:testDebugUnitTest :app:testQaUnitTest   # passed
./gradlew assembleDebug                                # passed
./gradlew assembleQa                                   # passed
./gradlew :app:assembleQaAndroidTest                   # passed
./gradlew :app:lintDebug :app:lintQa                   # passed
node --test app/src/test/js/map-preview.test.cjs        # passed (4 tests)
```

**`./gradlew test` cannot pass in this environment.** The aggregate task includes the `release`
variant, and `processReleaseGoogleServices` fails with:

```
> File google-services.json is missing.
  The Google Services Plugin cannot function without it.
```

`google-services.json` is listed in `.gitignore` and only `debug`, `qa`, `main`, `test` and
`androidTest` copies are committed — there is no `src/release` copy. This branch changes no
Gradle or manifest file, so the failure is pre-existing and unrelated to the redesign. The two
shipped variants are covered by the `testDebugUnitTest` and `testQaUnitTest` tasks above,
which pass.

Lint reports 21 warnings and 1 hint for both variants. All of them are in
`build.gradle.kts`, the manifest, `network_security_config.xml`, or pre-existing non-UI
sources; none are in `core/ui`, `feature/*` or `navigation/*`.

No connected-device test result is claimed. No emulator or device was available, so
`connectedQaAndroidTest` was not run.
