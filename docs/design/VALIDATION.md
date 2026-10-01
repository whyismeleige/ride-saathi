# Validation — visual implementation and layout usability

Executed on branch `feat/v1-ui-redesign`, 2026-10-01.

All required Android commands were executed from `android/` in combined Gradle runs.
The full validation passed for the layout implementation. After restoring the shared
onboarding illustration, Kotlin compilation and all 13 screenshot checks passed again.

| Command | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | PASS — 57 tests, 0 failures |
| `./gradlew :app:testQaUnitTest` | PASS — 57 tests, 0 failures |
| `./gradlew assembleDebug` | PASS |
| `./gradlew assembleQa` | PASS |
| `./gradlew :app:lintDebug` | PASS — 0 errors, 22 warnings, 1 hint |
| `./gradlew :app:lintQa` | PASS — 0 errors, 22 warnings, 1 hint |
| `node --test app/src/test/js/map-preview.test.cjs` | PASS |
| `./gradlew :app:updateDebugScreenshotTest` | PASS — 13 real Compose renders |
| `./gradlew :app:validateDebugScreenshotTest` | PASS — 13 tests, 0 failures |
| `git diff --check` | PASS |

Lint warnings concern target/dependency versions, existing manifest/launcher configuration,
and Kotlin extension suggestions. The screenshot plugin version is intentionally pinned
for AGP 8.13.2 compatibility. The new Modifier-parameter warning was fixed.

The localization test's fixed key count was updated from 145 to 152 for the seven new
translated labels. Its equality checks across English/Hindi/Telugu and nonblank-value
checks remain intact.

## Visual evidence

The seven directly referenced screens were rendered and reviewed through multiple passes.
The latest usability pass also covers 360dp and 430dp phone widths, 1.4× text, and empty-name
validation. Thirteen current baseline images are in `android/app/src/screenshotTestDebug/reference`.
The [layout review](LAYOUT_REVIEW.md) includes before/after evidence and explains deliberate
spacing and hierarchy changes requested after the initial reference reproduction.

The paired comparisons below show the design source on the left and current Compose output
on the right. They document visual lineage; current layouts intentionally prioritize usability.
Reference device chrome and the name reference's OS keyboard are excluded from the source crop.

- [Opening](comparisons/Intro.png)
- [Language](comparisons/Language.png)
- [Name](comparisons/Name.png)
- [Home setup](comparisons/SaveHome.png)
- [Address search](comparisons/Address.png)
- [Saved places](comparisons/Settings.png)
- [Booking](comparisons/Home.png)

Corrections across passes included wordmark size, title and helper line heights, card
radii and spacing, input height, matching icon crops, Home shortcut proportions, microphone
size/gradient, CTA visibility, and sampled button-gradient colors. The usability pass additionally corrected
artwork overflow, header crowding at larger text sizes, keyboard-height form spacing,
and saved-place readability.

Screenshot tests compare renders with generated regression baselines. They do **not**
assert pixel equality with the supplied design PNGs. See [IMPLEMENTATION.md](IMPLEMENTATION.md)
for the remaining functional and visual differences.

No connected-device tests were run. The local emulator failed to boot because its configured
system image is absent. Host-side Compose rendering was used; the connected physical phone
was not modified. STT/TTS, permissions, keyboard interaction and live network/map behavior
were not device-tested in this task.
