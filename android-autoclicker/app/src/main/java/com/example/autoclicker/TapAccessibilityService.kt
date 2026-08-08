package com.example.autoclicker

// ─── IMPORTS ──────────────────────────────────────────────────────────────────
// AccessibilityService    — the base class we must extend; gives us dispatchGesture()
// GestureDescription      — container that describes a touch gesture (tap, swipe, etc.)
// Path                    — Android's class for describing a 2D shape or movement line
// AccessibilityEvent      — fired by the system when UI changes happen (we receive these)
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * TapAccessibilityService
 * ═══════════════════════
 * This is the HEART of the auto-clicker. It extends Android's AccessibilityService,
 * which is the only way a regular (non-root, non-system) app can programmatically
 * inject touch events into the screen.
 *
 * WHY AN ACCESSIBILITY SERVICE?
 * ─────────────────────────────
 * Normal apps are sandboxed — they can only interact with their own UI.
 * Accessibility services are a special class of service (designed originally to
 * help people with disabilities interact with their phone) that the system grants
 * the ability to observe and control ANY app's UI, including injecting taps.
 *
 * Electrician analogy: regular apps are wired in their own isolated circuit.
 * AccessibilityService is a licensed technician with a key to the main panel —
 * once the homeowner (user) grants access, they can work across all circuits.
 *
 * LIFECYCLE:
 * ──────────
 * 1. Android starts this service when the user enables it in Settings > Accessibility
 * 2. onServiceConnected() fires → we store a static reference so MainActivity can reach us
 * 3. The service runs until the user disables it or the device restarts
 * 4. onDestroy() fires → we clear the static reference
 */
class TapAccessibilityService : AccessibilityService() {

    // ─── COMPANION OBJECT ─────────────────────────────────────────────────────
    //
    // A companion object in Kotlin is the equivalent of Java's 'static' members.
    // It belongs to the CLASS itself, not to any particular running instance.
    //
    // We use it to hold a reference to the one running instance of this service,
    // so that MainActivity can reach it without needing a complex binding mechanism.
    //
    //   TapAccessibilityService.instance != null  → service is running, ready to tap
    //   TapAccessibilityService.instance?.performTap(x, y)  → fire a tap
    //
    // @Volatile ensures that when one thread (the service thread) writes to 'instance',
    // another thread (the UI thread in MainActivity) sees the update immediately.
    // Without @Volatile, threads can hold stale cached values of the variable.
    //
    // NOTE: This static reference pattern is fine for a proof-of-concept (M1).
    //       In a production app you'd typically use a bound service or LiveData.
    companion object {
        @Volatile
        var instance: TapAccessibilityService? = null
            private set   // External code can READ this; only this class can WRITE it.
    }


    // ─── LIFECYCLE: SERVICE CONNECTED ─────────────────────────────────────────

    /**
     * Called by Android once the service is fully initialised and connected.
     * This is the right place for setup work — the service is ready to use here.
     *
     * Compare to a circuit coming live: this fires when the breaker is switched ON
     * and the system has confirmed the service is properly wired up.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()

        // Store a reference to ourselves so MainActivity can find and call us.
        instance = this
    }


    // ─── LIFECYCLE: DESTROY ───────────────────────────────────────────────────

    /**
     * Called when the service is shutting down:
     *   • User disables it in Settings > Accessibility
     *   • Device is restarting
     *
     * We MUST clear 'instance' here. If we don't, MainActivity might try to call
     * performTap() on a dead, disconnected service object — resulting in a crash
     * or a silent no-op at best.
     */
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }


    // ─── REQUIRED OVERRIDES ───────────────────────────────────────────────────
    //
    // AccessibilityService is abstract (it cannot be instantiated directly).
    // Kotlin requires us to implement all its abstract methods, even if we
    // don't use them yet. Think of these like mandatory fields on a permit form —
    // even the "N/A" boxes need a signature.

    /**
     * Fired whenever an accessibility event occurs on the device:
     * an app opens, a button is clicked, text changes, a notification arrives, etc.
     *
     * For M1 we are only SENDING taps — we don't need to REACT to events.
     * This is intentionally empty.
     *
     * Future milestone use: detect when a target game/app is in the foreground,
     * then start the auto-tap sequence automatically.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // M1: intentionally empty. We produce taps; we don't consume events.
    }

    /**
     * Called when the system needs to interrupt this service —
     * for example, when a phone call comes in and takes over accessibility focus.
     *
     * For M1 we have nothing ongoing to interrupt, so this is empty.
     */
    override fun onInterrupt() {
        // M1: intentionally empty.
    }


    // ─── CORE FUNCTION: PERFORM TAP ───────────────────────────────────────────

    /**
     * Sends a single tap gesture to the screen at the specified pixel coordinates.
     *
     * HOW A TAP WORKS IN dispatchGesture():
     * ────────────────────────────────────
     * Android's gesture system represents touch input as a series of "strokes".
     * A stroke is a movement described by a Path (a line drawn on the screen)
     * with a start time and duration.
     *
     * A TAP is the simplest stroke:
     *   • Path starts and ends at the same point (no movement)
     *   • Duration is short enough to be a tap, not a long-press (~50ms)
     *
     * Compare to electrical pulse: it's a quick make-and-break on one point —
     * just long enough for the system to register "finger down, finger up."
     *
     * @param x  Horizontal screen coordinate in pixels (0 = left edge of screen)
     * @param y  Vertical screen coordinate in pixels  (0 = top edge, below status bar)
     */
    fun performTap(x: Float, y: Float) {

        // ── Step 1: Define WHERE the tap lands ────────────────────────────────
        // Path is Android's class for describing a 2D line or shape.
        // moveTo(x, y) moves the "pen" to our target coordinate without drawing.
        // We DON'T call lineTo() — a tap doesn't travel anywhere.
        val tapPath = Path().apply {
            moveTo(x, y)
        }

        // ── Step 2: Describe WHEN and HOW LONG the tap lasts ─────────────────
        // StrokeDescription wraps the Path with timing information.
        //
        //   path      = the movement to simulate (our stationary tap point)
        //   startTime = delay in ms before this stroke begins (0 = start immediately)
        //   duration  = how long the finger is "pressed" in milliseconds
        //               50ms is a crisp, unmistakeable tap.
        //               < 10ms may be filtered out as noise by some apps.
        //               > 500ms starts to look like a long-press.
        val stroke = GestureDescription.StrokeDescription(
            tapPath,
            /* startTime = */ 0L,
            /* duration  = */ 50L
        )

        // ── Step 3: Bundle the stroke into a GestureDescription ──────────────
        // GestureDescription is the final package handed to dispatchGesture().
        // It can contain multiple simultaneous strokes (e.g., a two-finger pinch),
        // but for a single tap we only need one.
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        // ── Step 4: Dispatch the gesture to the system ────────────────────────
        // dispatchGesture() is the key API — it injects the touch event into
        // the Android input pipeline as if a real finger touched the screen.
        // This API is ONLY available on AccessibilityService. No other class
        // in the Android SDK can do this without root/system privileges.
        //
        //   gesture  = what to do (our tap)
        //   callback = listener called when the gesture finishes (null = don't care)
        //   handler  = which thread to invoke the callback on (null = main thread)
        dispatchGesture(gesture, null, null)
    }
}
