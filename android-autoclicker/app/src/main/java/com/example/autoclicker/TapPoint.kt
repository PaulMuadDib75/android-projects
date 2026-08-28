package com.example.autoclicker

/**
 * TapPoint
 * ════════
 * One recorded point in a tap sequence: a single (x, y) screen coordinate.
 *
 * WHY ITS OWN FILE (not nested inside OverlayService):
 *   Milestone 4 (per-point interval editing) and Milestone 5 (save/load
 *   sequences) both need this exact same shape — a list of coordinates is
 *   the data a saved sequence IS. Keeping it as a small, standalone file now
 *   means those later milestones can import and reuse it directly instead
 *   of untangling it out of OverlayService's internals.
 *
 * WHY Float (not Int or Double):
 *   This matches, byte for byte, the two APIs it sits between:
 *     - MotionEvent.getRawX()/getRawY() — what RECORDS a point — return Float.
 *     - TapAccessibilityService.performTap(x: Float, y: Float) — what REPLAYS
 *       a point — takes Float.
 *   Using Float here means a recorded point can be handed straight to
 *   performTap() with zero conversion, and therefore zero risk of a
 *   rounding/truncation bug quietly nudging a replayed tap off target.
 *
 * WHY A data class:
 *   Kotlin generates equals()/hashCode()/toString()/copy() for free from the
 *   two properties below. None of that is used yet, but it costs nothing to
 *   have — e.g. toString() alone makes TapPoint print usefully in Log.d()
 *   calls and debugger views without any extra code.
 *
 * ABSOLUTE SCREEN COORDINATES ONLY:
 *   Per this project's recurring coordinate-space lesson (see CLAUDE.md —
 *   this is the third time it's come up): x and y here must ALWAYS be
 *   absolute screen coordinates, the same space as View.getLocationOnScreen()
 *   and MotionEvent.getRawX()/getRawY(). Never a view-local or
 *   layout-relative value. Every TapPoint created in this codebase must be
 *   built from event.rawX/event.rawY, not event.x/event.y.
 */
data class TapPoint(val x: Float, val y: Float)
