# V0 validation — 19 September 2026

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
