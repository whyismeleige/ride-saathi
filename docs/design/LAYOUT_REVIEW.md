# Layout usability pass

The user approved the visual style and requested more comfortable layouts instead of literal
reproduction of the image positions. Illustrations/colors are retained while spacing,
primary-action placement, scrolling and keyboard handling have been revised.

| Area | Before | After |
| --- | --- | --- |
| Forms | Artwork and tight spacing compete with fields; CTA scrolls | Smaller/optional art, 24dp gutters, persistent action footer |
| Name | Small helper text and tightly packed controls | Readable helper, 56dp input, hidden art in keyboard viewport |
| Booking | Four tiny shortcuts and a noninteractive footer | Larger scrolling cards, manual entry near voice, no redundant footer |
| Home setup | Multiple controls open the same search | One explicit address-search action |
| Saved places | Settings and Continue after the list | Settings by the title, scrollable list, persistent Done |
| Header | Overlaid brand/actions can crowd at larger text sizes | Reserved slots for Back, wordmark and profile |

Actual Compose renders:

- Booking: [before](layout-review/Home-before.png) · [after](layout-review/Home-after.png)
- Language: [before](layout-review/Language-before.png) · [after](layout-review/Language-after.png)
- Name: [before](layout-review/Name-before.png) · [after](layout-review/Name-after.png)
- Saved places: [before](layout-review/Settings-before.png) · [after](layout-review/Settings-after.png)

Before images use the previous reference-shaped viewports. Standard after images use 390×780dp;
Name uses 390×424dp to model keyboard-reduced space. Additional renders inspect smaller and
wider phones, 1.4× text and the empty-name disabled state. Content can scroll on small screens;
primary form actions stay in their footers.

The first render review caught booking-art overflow and large-text header crowding. The hero
now clips/fades within its own panel; the header reserves space for each action.
