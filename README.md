# Dial

Android home-screen widget app with realistic watch dials. Pick a dial, put it on your home screen — the widget ticks every minute, with a live second hand on analog dials.

## What it has

- **17 realistic watch dials** — each keeps its own case shape from its photo (round stays round, squircle stays squircle), all the same size
- **Digital section** — 38 digital dials in different fonts, styles, colours and textures
- **Hybrid clocks** — analog disc and digital time in separate zones that never overlap
- **Device, Battery, Calendar and Quick-tools widgets**
- **Live second tick** on analog dials (needs unrestricted battery permission)
- **Responsive sizing**, date window on all analog dials, word clocks with glowing letters
- Tap the widget only opens the app — dials change inside the app

## Widget stays on time

v2.2 fixed a frozen-clock bug: the widget now re-renders every minute through two independent paths (a minute alarm plus a foreground tick service), and opening the app repairs a stalled update chain. Needs unrestricted battery + exact alarms for the most reliable ticking.

## Build

Open in Android Studio (minSdk 26, targetSdk 33, compileSdk 37) or build with Gradle. No network permissions, no accounts, no data leaves the phone.
