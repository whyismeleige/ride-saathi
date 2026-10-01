# Android UI — reference artwork with usability-led layouts

The PNGs establish the illustrations, palette and visual character. The latest user direction
replaces literal layout reproduction with clearer, roomier layouts. Existing AppSession,
controllers, persistence, localization, search, voice services, permissions, maps and Uber
handoff remain in use.

## Current layouts

- **Onboarding:** 24dp gutters, naturally wrapping headings/helpers, smaller contained artwork,
  and Continue outside the scroll area. Compact/keyboard-reduced pages hide artwork. Name entry
  requests focus when the field is composed; its action stays above the IME.
- **Opening:** voice-first introduction and replayable illustration, with a full-width CTA in
  the same predictable footer as other setup steps.
- **Home setup:** one explicit address-search action replaces controls that opened the same
  flow. Home remains compulsory. Address editing prioritizes the actual address and map.
- **Address search:** field directly below the header, roomy scrollable results, visible
  attribution and persistent Continue. Row selection invokes the existing callback; Continue
  accepts the first highlighted real result. No empty-result message during search debounce.
- **Booking:** greeting/prompt have their own space above the clipped illustration/microphone
  panel. Manual destination entry follows voice. Larger 164dp quick-place cards have readable
  names/localities and horizontal scrolling. The noninteractive Book ride footer is removed.
- **Saved places:** search, settings access and places share a scrollable area; Done stays in
  a footer. The illustration is smaller and omitted on short screens. Header content can
  scroll when larger text or the keyboard reduces available space.
- **Controls:** 56dp minimum inputs; 72dp minimum place/language rows; more internal padding;
  wrapping button labels with room for arrows; neutral, unraised disabled buttons.
- **Header:** 64dp minimum height and reserved action slots prevent Back/profile overlap with
  the wordmark. Repeated corner-decoration overlays are removed.

Layouts use flexible widths capped at 600dp, scrolling for smaller viewports/larger text, and
48dp-or-larger interactive targets. Existing screen transitions, press feedback and voice pulse
remain. The visual palette and screen-specific artwork are retained.

## Reference → implementation

| Reference | Compose implementation |
| --- | --- |
| `ride-saathi-opening-screen.png` | `OnboardingScreen(Introduction)`, `IntroIllustration` |
| `ride-saathi-choose-your-language-screen.png` | `OnboardingScreen(Language)`, `LanguageIllustration`, custom choices |
| `ride-saathi-user-name-enter-screen.png` | `OnboardingScreen(Name)`, `NameIllustration`, custom input |
| `ride-saathi-home-saving-screen.png` | `HomeSetupContent`, `HomeIllustration` |
| `ride-saathi-save-address-screen.png` | Native field/results in `AddressPicker`; search space takes priority over decoration |
| `saved-places-screen.png` | `SettingsScreen`, `SavedPlacesIllustration`, `PlaceRow` |
| `booking-screen.png` | `HomeScreen`, `BookingIllustration`, `RideVoiceButton` |
| `ride-saathi-entire-reference.png` | Shared card/input/button styling for intermediate states; real map and Uber handoff |

## Components and artwork

`tools/extract_artwork.py` extracts decorative-only crops. The booking alpha mask excludes
text, microphone, labels and controls. All controls and text remain Compose; there is no
full-screen screenshot background. Decorative images have null descriptions.

`RideActionFooter` serves onboarding, Home setup, address search, place editing and Saved
Places. `RidePlaceCard` remains the shared result/place row. `RideSearchField` uses a custom
`BasicTextField`; language choices retain custom checks with radio semantics.

The generic `RideScenicHeader`, onboarding progress UI, default outlined fields, stock radio
visuals and obsolete scenery tokens remain removed. The earlier `RidePageDecoration` overlay
and its four corner-art assets are also removed. Android sans-serif reflows without forced
screenshot-specific line breaks in the layout.

## Functional boundaries

No tracking, fares, payments or booking APIs were added. V1 has no History data model,
reverse-geocoding current-location action or interactive map picker; unsupported controls
are not fabricated. Text search, map verification, saved-place editing/deletion, language
selection, tutorial access and compulsory setup remain available.

## Review and validation

[LAYOUT_REVIEW.md](LAYOUT_REVIEW.md) records before/after evidence. `comparisons/` pairs the
original mockups with current Compose output to show visual lineage, not pixel equality.

```bash
cd android
./gradlew :app:updateDebugScreenshotTest :app:validateDebugScreenshotTest
cd ..
python docs/design/tools/compare_screens.py
```

The host renderer uses screenshot plugin alpha13 with AGP 8.13.2. Thirteen cases cover the
seven main screens, 320/360/390/430dp widths, 1.4× text, a reduced name-entry viewport and
an empty compulsory-name field. Baselines guard against later regressions.

See [VALIDATION.md](VALIDATION.md) for results. The local AVD has no system image; no connected-
device tests were run and the physical phone was not modified.
