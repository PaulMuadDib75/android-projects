# Android Auto-Clicker

## What this is
Android app: multi-point tap sequences with adjustable intervals,
controlled via a floating overlay so it works over other apps (games).
Kotlin, Android Studio/Gradle project.

## Core mechanism
- AccessibilityService + dispatchGesture() performs the actual taps
  (required — regular apps can't inject touch events without it)
- Foreground service keeps it running reliably
- TYPE_APPLICATION_OVERLAY window for the floating control panel

## Built so far
- Milestone 1 complete — AccessibilityService dispatches a tap that is
  confirmed to register as a real UI click (verified via a target test
  button placed at the tap's exact screen coordinates)
- Android Studio auto-upgraded the build tooling: AGP 8.2.2 → 8.13.2,
  Gradle wrapper 8.6 → 8.13. Kotlin plugin stayed at 1.9.22.

## Lessons Learned
- `dispatchGesture()` operates in **absolute screen coordinates** — the
  same space as `View.getLocationOnScreen()` — not coordinates relative to
  an Activity's layout/content view. A status bar and/or ActionBar (as used
  here, via `Theme.MaterialComponents.DayNight.DarkActionBar`) shifts a
  view's layout-relative position away from its absolute screen position;
  in M1 this was a 239px vertical offset that made an otherwise-correct
  tap miss its target entirely.
  → **Any future coordinate-recording feature (Milestone 3, tap-to-record)
  must capture true screen position** via `getLocationOnScreen()` (or the
  touch event's raw screen coordinates, e.g. `MotionEvent.getRawX/getRawY`)
  — never layout-relative coordinates (`view.left`/`view.top`, or
  view-local touch coordinates) — since those will replay the same
  status-bar/ActionBar offset bug once recorded sequences are dispatched.
- A `Path` with a true zero-movement stroke (`moveTo()` only, no
  `lineTo()`) can have its DOWN/UP `MotionEvent`s coalesced or dropped by
  the input pipeline on some devices, so `dispatchGesture()` reports
  `onCompleted` successfully even though the View system never saw a
  click. Fix: give the stroke a tiny (~1px) movement via `lineTo()` —
  well inside touch slop, so it's still recognised as a tap, not a drag.

## Milestones (build in this order)
1. Accessibility Service dispatches one tap at a hardcoded point (button-triggered, no overlay)
2. Floating overlay with start/stop toggle
3. Multi-point recording mode (tap-to-record)
4. Per-point interval editing + global speed control
5. Save/load sequences
6. Polish: drag overlay, minimize state, loop-count limits

## Rules
- Chris is an industrial electrician learning Kotlin/Claude Code from scratch — explain concepts simply, trades analogies where useful
- Always explain what was built after building it
- Comment thoroughly — Chris reads every line before approving
- One milestone per session where possible — don't jump ahead
- Never touch AndroidManifest.xml permissions without flagging it first (accessibility + overlay permissions are sensitive, walk through why each is needed)