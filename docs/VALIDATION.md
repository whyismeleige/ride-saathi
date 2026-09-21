# V0 validation — 19 September 2026

## Unsaved voice destinations — 20 September 2026

- Final debug, unsigned release, QA application, and QA instrumentation APK builds passed.
- All 42 JVM tests passed, including 8 new tests for destination-query extraction, bilingual numbered choices, ambiguous replies, result commands, and paging.
- Debug lint passed with 0 errors, 19 warnings, and 1 hint; `git diff --check` passed.
- Added 13 QA device tests covering saved-place precedence, selection/confirmation, Hindi, current-location/Home preference, query replacement, errors/retry, cancellation, lifecycle interruption, and utterance completion guards.
- Device validation is incomplete. The first run passed the location-preference test, exposed a test-helper reflection error (fixed), then stalled between tests and was stopped. The corrected test APK compiles, but the next installation was rejected by the phone with `INSTALL_FAILED_USER_RESTRICTED` after it locked. Do not count this as a passing UI suite.
- Live Ola relevance, real microphone/TTS timing, TalkBack, location fallback on the target phone, and the actual Uber screen remain hands-on checks. Automated searches use fixtures and do not call Ola or book rides. The regular app and its saved places were not replaced.

## Ola Maps integration — previous run

- Debug and unsigned release APK builds passed, as did QA test compilation.
- All 17 JVM tests and the map-preview JavaScript regression check passed.
- Debug lint passed with 0 errors, 19 warnings, and 1 hint.
- All 6 `OlaPlaceSearchProviderTest` contract tests passed on the connected Xiaomi Android 15 phone. Fixtures verify request encoding, Hindi language codes, Home location bias, no invented first-Home bias, missing-key handling, valid coordinates, duplicate results, zero results, malformed responses, and API errors. No live Ola API requests were made.
- The full 21-test device suite stalled in the first existing UI test (`editingPlacePreservesLegacyVoiceAliases`), while Espresso waited for the main thread to become idle on Home. The QA process was stopped after the stall; that run is incomplete, not a passing UI check. The focused provider suite passed separately. The earlier device results below are historical, not verification of this integration.
- No Ola API key was configured in the environment or `local.properties`, so live search accuracy and quota behavior remain unverified. Add a key using the README instructions, rebuild, and compare previously failing addresses before the pilot.
- The regular installed app and its saved places were not replaced or cleared by this run; device tests use the separate `.qa` package.


## Passed

- Debug APK build: `app/build/outputs/apk/debug/app-debug.apk`.
- Release APK build: `app/build/outputs/apk/release/app-release-unsigned.apk` (unsigned).
- 17 JVM tests: 13 destination tests and 4 voice-command tests. These include English/Hindi names, `ghar` versus `beta ka ghar`, possessive variants, mixed scripts, substring rejection, ambiguity, duplicate names, and negative confirmation.
- Map JavaScript regression suite: `node --test app/src/test/js/map-preview.test.cjs`.
- Debug lint: 0 errors, 20 warnings, 1 hint. Warnings cover newer dependency/target SDK versions, backup configuration, launcher icon shape, and KTX style suggestions. The blocking Fragment/Activity Result compatibility errors were fixed by upgrading the transitive Fragment dependency.
- QA application and instrumentation test APKs compile.
- `git diff --check`.

## Device checks passed

On the connected Xiaomi 2310FPCA4I running Android 15:

- All 7 instrumentation tests passed at the phone's normal text size.
- All 7 passed again with a QA-only font-scale override of 2.0 (200%). The tests assert that the requested scale is active. The phone's system font setting remains 1.0.
- Covered onboarding's required Home, Hindi/English navigation, `beta ka ghar` versus Home, distinct-destination clarification, touch confirmation/cancellation, negative spoken confirmation, switching language with an English saved name, rotation, storage round-trip, and encoded Uber links.
- Visually inspected screenshots of English/Hindi Home, Hindi confirmation and address setup, and landscape navigation. At enlarged text sizes some actions require scrolling; the device tests verified they remain reachable.
- Installed the tested debug APK over the existing app with `adb install -r`; installation succeeded without clearing app data.
- Screenshots: `app/build/qa-screenshots/normal/files/` and `app/build/qa-screenshots/font200/files/`.

The initial device installation restriction was resolved after the user unlocked the phone and allowed installation. An initial test-script scroll action on the fixed Settings button and the QA font-override setup were corrected; these were test-harness issues. The passing runs use the separate `.qa` application, so existing Ride Saathi addresses were not used or cleared.

## Remaining hands-on checks

Live microphone recognition and TTS, TalkBack, real address search/map tile loading, permission recovery, GPS, and the actual Uber pickup/drop-off screen still require checks on the target phones. The automated speech tests inject final transcripts; they do not measure elderly speakers' transcription accuracy. See [PILOT_TESTING.md](PILOT_TESTING.md).
