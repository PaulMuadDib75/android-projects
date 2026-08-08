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
- Milestone 1 in progress
- Android Studio auto-upgraded the build tooling: AGP 8.2.2 → 8.13.2,
  Gradle wrapper 8.6 → 8.13. Kotlin plugin stayed at 1.9.22.

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