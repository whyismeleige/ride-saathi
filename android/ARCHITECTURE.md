# Android architecture

Ride Saathi remains one Gradle application module (`:app`, namespace/application ID
`com.ridesaathi.app`). The refactor follows the existing flows rather than adding a
navigation framework, dependency-injection framework, or storage migration.

## Package map

Under `app/src/main/java/com/ridesaathi/app/`:

| Package / entry point | Responsibility |
| --- | --- |
| `MainActivity` | Edge-to-edge setup, Compose entry point, forwarding new intents |
| `RideSaathiApp` | Theme and root surface |
| `AppSession`, `AppDependencies` | Activity-lifetime composition, shared profile/places, lifecycle cleanup, injectable search factories/location lookup |
| `navigation` | Typed `AppScreen`, startup/animation order, app shell, system Back routing and shared error actions |
| `feature/onboarding` | Setup UI, profile-name persistence, completion rules |
| `feature/home` | Home UI, observable speech state, interpreting transcripts and dispatching destination/ride commands |
| `feature/places` | Saved-place editor, address picker, debouncing, selection, validation and persistence coordination |
| `feature/destination` | Unsaved-destination search, paging, correction/retry, spoken choices and return from confirmation |
| `feature/ride` | Confirmation UI/state, fresh pickup request and ride handoff coordination |
| `feature/tutorial` | Intro/full tutorial state, steps and completion flags |
| `feature/settings` | Saved-place management and runtime language selection |
| `feature/sharedlocation` | Shared-link resolution lifetime, address-result selection and clarification UI |
| `domain/model` | Framework-free profiles, saved places, candidates and shared-location models |
| `domain/search` | Provider contract, matching/transliteration, travel phrases, choices and 50 km boundary |
| `domain/sharedlocation` | Pure supported-link/coordinate/address parsing |
| `data/local` | Existing SharedPreferences/JSON storage |
| `data/places` | Backend HTTP implementation and map-preview URL provider |
| `data/speech` | Bounded, cancellable backend POST returning in-memory speech audio |
| `data/sharedlocation` | Bounded, host-restricted Google Maps redirect resolution |
| `core/location` | Android location requests: recent search position versus fresh pickup position |
| `core/permissions` | Activity Result permission requests, cancellation and system settings intents |
| `core/speech` | AudioRecord capture, WebRTC VAD/AEC barge-in, online audio playback with device TTS fallback, and utterance completion/lifecycle |
| `core/deeplink` | Uber URI construction, installed check, launching and Play Store/web fallback |
| `core/ui` | Stateless shared components, WebView map lifecycle and theme |
| `localization` | English/Hindi/Telugu dictionaries, fallback, speech locales and search error messages |

The former Activity's responsibilities are divided between feature controllers and
platform owners. `Domain.kt` was separated into models, local storage and Uber
integration. `Words.kt` retains its lookup API but delegates to three dictionaries.

## State and navigation

`AppSession` owns shared profile/places and an `AppScreen` enum. Feature controllers
own their observable `*UiState` objects (destination results use an immutable
`DestinationSearchState`) and private asynchronous handles. Screens receive state
and callbacks; small `*Route` adapters in their feature files connect the session
to those screens. Address editing and destination search use explicit actions.

This is deliberately an **Activity-lifetime session, not a retained ViewModel**.
The existing rotation contract abandons unfinished rides and reloads persisted
setup/home data. Onboarding alone restores its current stage and tutorial page
through the Activity saved-state registry; name, language and places still use
LocalStore. Retaining a pending confirmation or handoff would change that
behavior. A `DefaultLifecycleObserver` stops recognition/TTS on pause, cancels
work on stop, and releases resources on destroy. Permission dialogs retain the
existing exceptions for search work. New features needing retention can add a
ViewModel without retaining this Activity or its platform controllers.

Navigation remains the existing animated single-screen flow. Back returns from
searched-destination confirmation to the same result page; other confirmations
return home. Editor Back first dismisses address replacement when a prior address
exists, then returns to the screen that opened it. Onboarding has five stages:
language, the existing intro and ride tutorial, name, Home, and optional places.
Its Back action moves through stages and tutorial pages without completing setup. Tutorial Back records completion and returns to its typed return screen.

## Data flow and compatibility

- Touch/voice actions enter a feature controller; pure domain functions resolve
  saved names, commands or search boundaries.
- Address/destination/share searches request a permission-aware current position,
  then call the injected `PlaceSearchProvider` on a worker thread. Results update
  Compose state on the main thread only if their generation is still current.
- Shared coordinates go directly to confirmation. Address-only links require
  explicit result selection. Neither flow saves a place implicitly.
- Confirmation requests fresh pickup coordinates, then calls the Uber launcher.
  Uber still owns booking and payment.
- Profile/place mutations go through `LocalStore`. Preference file `ride_saathi`,
  keys `profile`, `places`, `map_endpoint`, JSON fields, aliases, IDs, language and
  tutorial defaults are unchanged. No existing data migration is needed.
- All existing localization keys are retained, with setup copy added in each language. Runtime language selection and
  English/unknown-key fallback are unchanged.

Keep the distinct timing/quality contracts: address debounce 500 ms; voice utterances
20 s; search location up to 120 s old, 5 km accuracy, 8 s timeout; pickup fresh,
250 m accuracy, 15 s request. Interrupting HTTP alone is insufficient: generation
checks protect against providers that ignore interruption. TTS completion checks
utterance identity, foreground state and destruction before restarting listening.

## Adding or changing a feature

1. Put its screen, state/actions and workflow controller in `feature/<name>`.
2. Keep screen rendering driven by state and callbacks. Put framework lifecycle
   and intent operations in `core`; HTTP and storage implementation in `data`.
3. Put reusable pure rules/models in `domain`; do not import Android, Compose,
   platform implementations or feature state into that layer.
4. Wire dependencies/lifecycle in `AppSession`; extend typed navigation and Back
   behavior explicitly. Reuse the provider seams for tests.
5. Mirror ownership in JVM/instrumentation test packages. Keep cross-feature
   pilot tests and the configured `PilotTestRunner` at the application root.
6. Add every localization key in all three dictionaries and run the checks in
   [README.md](README.md).

## Deliberate tradeoffs

Controllers currently coordinate through the small session/composition root;
this keeps cross-feature cancellation explicit without dozens of one-method
interfaces. Those references are the main boundary to replace with narrow
callbacks/interfaces before splitting Gradle modules. SharedPreferences and
bounded worker threads remain: this refactor preserves their existing format,
debounce and cancellation behavior. No new runtime dependencies were needed.

Azure voice turns and interruption rules are documented in [Azure voice pipeline](../docs/AZURE_VOICE_PIPELINE.md). Microphone capture uses one worker and bounded memory;
turn and playback cancellation invalidate late results independently. The pinned
WebRTC VAD dependency is resolved from a group-restricted JitPack repository.
