# Ride Saathi v1 UI implementation

Work lives on `feat/v1-ui-redesign`, in `.worktrees/v1-ui-redesign`. The app remains a single Compose module with the existing `AppSession`, feature controllers, typed screens and storage contracts.

## Visual tokens

`core/ui/theme` owns the palette, typography, shapes and spacing:

- Cream `#FFFAF1` background, navy `#09285D` foreground, emerald `#078365` primary action, mint `#DDF6E9`, sky `#E6F4FF`, peach and lavender accents. Orange text/icons use a darker shade for contrast. Slate supplies secondary text; errors use soft red surfaces and dark red text.
- Android sans-serif; 34/42sp hero, 30/38sp screen heading, 22/30sp section title, 18/26sp card title and 16–18sp body text. Text can wrap and scale.
- 20–28dp rounded surfaces, pill primary buttons, circular 48dp icon badges/back controls, 64dp minimum primary buttons. Shared spacing tokens cover screen margins, cards and controls; local spacing expresses individual layouts.
- Transparent Android system bars with dark icons for this deliberately light theme. The root uses the cream background, `safeDrawingPadding` and `imePadding`; no simulated status bar is drawn.

## Reusable components

- `RideSaathiLogo`, `CircularBackButton`, `RideIconBadge`, `RideSectionTitle`, `RideCandidateCard` in `RidePrimitives.kt`.
- `RideScenicHeader`: decorative normalized Canvas sky, clouds, buildings, trees, road, car and optional home. No reference bitmap is used at runtime.
- `RideVoiceButton`: large emerald microphone, concentric mint rings, restrained idle/listening pulse, press feedback, action and state semantics.
- `LanguageChoices`: the same full-row radio selections in onboarding and Settings.
- Existing `LargeButton`, `PlaceRow`, `SectionCard`, `StickyMicrophone`, `SpeechTranscript`, `ExpandableAddress` and `MapPreview` remain the common primitives, using the new palette and shapes. Full addresses stay available.

## Reference mapping

| Reference | Compose implementation |
| --- | --- |
| `ride-saathi-opening-screen.png` | `OnboardingScreen` introduction and `TutorialScreen` |
| `ride-saathi-choose-your-language-screen.png` | `OnboardingScreen`, shared `LanguageChoices` |
| `ride-saathi-user-name-enter-screen.png` | `OnboardingScreen` name stage, required profile name, clear action and IME Next |
| `ride-saathi-home-saving-screen.png` | `OnboardingScreen` Home stage and `PlaceEditorScreen` |
| `ride-saathi-save-address-screen.png` | `AddressPicker`: focused search, scrollable real results, badges, loading and attribution |
| `saved-places-screen.png` | `SettingsScreen` and optional onboarding places stage |
| `booking-screen.png` | `HomeScreen`: actual profile, central voice action, Home-first quick places, existing manual search |
| `ride-saathi-entire-reference.png` | Destination cards, shared-location clarification, ride confirmation and consistent tutorial styling |

## Preserved behavior and deliberate deviations

Language remains the first existing onboarding stage, followed by the existing intro/tutorial pages, name, required Home and optional places. No onboarding state or persistence migration is introduced to reorder those stages. The introduction uses the opening reference's hero copy and Get started action.

The illustration is intentionally simpler than the painted references and does not include the person illustration. It is decorative; all controls and text are real Compose. Final vector illustrations can replace `RideScenicHeader` at its existing call sites, ideally with separate city, home and friendly person variants. No network font or illustration dependency was added.

The Home greeting uses localized Hello and the saved profile name rather than time-of-day logic. Quick cards show real names and semantic Home/pin badges: the model does not have a Work/category field. Saved-place editing remains in Settings, reachable through the profile action and See all. A decorative bottom navigation bar and History destination are omitted.

Address picking continues to use the current search provider and its location-aware search. There is no address-picker current-location selection action or map picker in the existing controller, so neither is fabricated. Map previews and provider attribution remain intact. Result selection goes directly to the existing confirmation/editor state, so cards use a chevron rather than a misleading persistent radio selection. Distances are omitted because the candidate model does not supply them.

Manual destination entry, result paging, search correction and touch Uber confirmation expose existing controller methods. No fare, driver or booking data is created. Speech/TTS, permissions, location lookup, generation cancellation, shared links, Back handling, map WebView lifecycle and Uber fallback remain in their original owners. Backend APIs, local storage, DI and navigation frameworks are unchanged.

Concept screens intentionally not implemented: vehicle/fare selection, finding a driver, driver details, driver tracking, trip progress, arrival and trip history. Uber still owns booking, tracking and payment.

## Previews and accessibility review

`app/src/debug/java/com/ridesaathi/app/preview/RidePreviews.kt` supplies nine screen groups: intro, language, name, Home setup, address results, saved places/Settings, booking, clarification and confirmation. Name/booking include 1.6x font previews; booking also includes landscape. Fixtures exist only in the debug preview source set. Map WebView creation is replaced only in Android Studio inspection mode; runtime maps are unchanged.

Primary controls have minimum heights rather than fixed text heights. Forms and result content scroll, Settings secondary actions scroll with its content, name input supports IME Next, and the shell handles Android safe/IME insets. Language rows expose radio semantics; the voice action exposes listening state; screen headings expose heading semantics. Runtime TalkBack, keyboard overlap, large-font rendering and device screenshots still require device validation.

## Validation

Commands are run from `android/`:

```sh
./gradlew test
./gradlew assembleDebug
./gradlew assembleQa
./gradlew :app:lintDebug :app:lintQa
node --test app/src/test/js/map-preview.test.cjs
./gradlew :app:assembleQaAndroidTest
```

Logs are retained under the ignored `android/app/build/redesign-validation/` directory. The initial unit test run found the dictionary-size assertion still expected 137 keys; it was updated to 145 for eight additions in all three dictionaries. Dictionary equality, nonblank values and fallback checks remain active.

Final results are recorded below after validation completes.

No connected-device test result is claimed. The installed `Small_Phone` AVD cannot launch because its Android 37.1 system image is absent (`Broken AVD system path`); no emulator image was downloaded or device data reset.
