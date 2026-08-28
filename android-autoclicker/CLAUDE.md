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
- Milestone 2 complete and verified on-device — OverlayService draws a
  small draggable button (TYPE_APPLICATION_OVERLAY) that renders on top
  of other apps, runs as a foreground service
  (foregroundServiceType="specialUse"), and toggles a fixed 1-second
  repeating tap loop on/off (reuses M1's hardcoded TAP_X/TAP_Y and
  TapAccessibilityService.performTap()). MainActivity gained a
  permission flow for SYSTEM_ALERT_WINDOW (settings deep-link, like
  accessibility) and POST_NOTIFICATIONS (API 33+ runtime prompt). All 9
  verification steps passed, including the overlay surviving a task-kill
  from Recents (confirms the foreground service is genuinely independent
  of MainActivity's lifecycle, as designed).

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
- The same layout-relative-vs-absolute-screen coordinate trap from M1
  shows up again in a different form for a floating overlay's own drag
  handling: `MotionEvent.getX()/getY()` inside a `WindowManager`-hosted
  view's touch listener are VIEW-LOCAL (0,0 = the view's own corner),
  which shifts every frame *during* a drag, so using them for delta math
  compounds into drift. Always use `event.getRawX()/getRawY()` (absolute
  screen coordinates — the same space `WindowManager.LayoutParams.x/y`
  use, given `Gravity.TOP or Gravity.START`), measured against the single
  `ACTION_DOWN` starting point, never accumulated frame-to-frame.
  → **This is directly relevant to Milestone 3's tap-recording feature.**
  Two coordinate systems will be in play there and must not be mixed:
  the coordinates of wherever the user taps to *record* a point (an
  in-app touch, or a touch on the overlay itself — capture via
  `getRawX()/getRawY()`, same reasoning as this M2 drag bug and the
  original M1 lesson above) versus the coordinates `dispatchGesture()`
  needs to *replay* that point (absolute screen space, same space
  `getLocationOnScreen()`/`getRawX()/getRawY()` already report). As long
  as recording always captures raw/absolute coordinates and never a
  view-local or layout-relative value, replay will land correctly — this
  is now the third time this exact category of bug has shown up
  (M1 status bar offset, M2 drag jitter, M3 risk), so treat "is this
  coordinate raw/absolute or view/layout-relative?" as a mandatory
  question for any new code that reads or writes a screen position.
- This project has no `mipmap`/launcher-icon resources at all (no
  `android:icon` set anywhere) — anything needing an icon (e.g. a
  notification's `setSmallIcon()`) needs its own drawable; `R.mipmap.
  ic_launcher` does not exist and won't compile.
- The command-line Gradle wrapper (`gradlew`/`gradlew.bat`) does not work
  in this environment: `gradle-wrapper.jar` was never committed (not
  covered by `.gitignore`, just never added), and `gradlew.bat` fails
  under both Git Bash and PowerShell 5.1 with garbled batch-script output
  (likely LF-only line endings confusing `cmd.exe`'s parser). Working
  build command until the wrapper jar is regenerated: set `JAVA_HOME` to
  Android Studio's bundled JBR (`C:\Program Files\Android\Android
  Studio\jbr`) and invoke the Gradle 8.13 distribution Android Studio
  already cached directly, e.g.
  `C:\Users\Chris\.gradle\wrapper\dists\gradle-8.13-bin\<hash>\gradle-8.13\bin\gradle
  assembleDebug` (the `<hash>` segment is machine-specific — find it with
  `ls ~/.gradle/wrapper/dists/gradle-8.13-bin/`).

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