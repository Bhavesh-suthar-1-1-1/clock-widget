# Clock Widget

Android home-screen widget app with realistic watch dials. Pick a dial, put it on your home screen — the widget ticks every minute, with a live second hand on analog dials.

## What it has

- **17 realistic watch dials** — each keeps its own case shape from its photo (round stays round, squircle stays squircle), all the same size
- **Digital section** — 10 digital dials in the gallery; the other 9 designs live in the Studio (Design → Digital, tagged STUDIO) and can be applied to the widget from there
- **Hybrid clocks** — analog disc and digital time in separate zones that never overlap
- **Slats** — featured slat clock: shaded vertical slats over rotating number rings, lacquered rim, white capsule window with the time. Recolour it from the Studio
- **In-app updates** — on open, the app checks GitHub releases and offers new versions to download and install without leaving the app
- **Live second tick** on analog dials (needs unrestricted battery permission)
- **Responsive sizing**, date window on all analog dials, word clocks with glowing letters
- Tap the widget only opens the app — dials change inside the app

## Widget stays on time

v2.2 fixed a frozen-clock bug: the widget now re-renders every minute through two independent paths (a minute alarm plus a foreground tick service), and opening the app repairs a stalled update chain. Needs unrestricted battery + exact alarms for the most reliable ticking.

v2.3 added a soft frosted-glass look to the digital and hybrid widget backgrounds: each style keeps its own background colour at ~60% opacity, so the home-screen wallpaper faintly shows through, with a soft top light and a bright rim. Analog dials are unchanged.

v2.4 cut the Digital section from 38 dials to the 19 most distinct ones, removed the Device, Battery, Calendar and Quick-tools widgets, added the featured Slats clock (red slats over rotating number rings), and added in-app update checks: when a new release is published here, opening the app offers it to download and install without leaving the app.

## Build

Open in Android Studio (minSdk 26, targetSdk 33, compileSdk 37) or build with Gradle. Internet is used only to check GitHub releases for app updates; no accounts, no data leaves the phone otherwise.

v3.2 — fixes: a White colour picked in the Studio was ignored on Apply (white is -1, the old "none" marker); the Slats minute ring showed 00 instead of 60; wallpaper detection failed on phones that return hardware bitmaps; the Studio preview could crash while swapping frames; system Back now returns to the gallery. New: a redesigned app (bottom navigation, gallery filters, tabbed Studio), nicer Slats with Studio colour control, and half of the digital designs moved into the Studio.
