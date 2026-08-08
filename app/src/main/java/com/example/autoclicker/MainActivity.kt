package com.example.autoclicker

// ─── IMPORTS ──────────────────────────────────────────────────────────────────
// Intent       — a message that can start a new screen or system action
// Bundle       — a container for saved state (passed to onCreate)
// Settings     — provides system Settings screen URI constants
// Button       — the clickable button widget
// TextView     — a widget that displays text (read-only)
// AppCompatActivity — the base class for activities with backwards-compatible features
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity
 * ════════════
 * The one and only screen for Milestone 1.
 *
 * Purpose: provide the bare minimum UI to test that TapAccessibilityService
 * is running and can dispatch a real tap gesture to the screen.
 *
 * What's on screen:
 *   1. Status label  — shows "Service: ON" or "Service: OFF" at a glance
 *   2. Settings button — deep-links to Settings > Accessibility so the user
 *                        can enable the service (we can't do this for them)
 *   3. Tap button    — calls TapAccessibilityService.instance?.performTap()
 *
 * There is NO floating overlay, NO recording mode, NO interval editor.
 * All of that comes in later milestones. M1 = smallest proof the tap works.
 */
class MainActivity : AppCompatActivity() {

    // ─── TEST TAP COORDINATES ─────────────────────────────────────────────────
    // These are the HARDCODED screen pixel coordinates for the M1 test tap.
    //
    // x = 500  →  roughly centre-horizontal on a 1080px-wide screen
    // y = 1000 →  a little below the vertical mid-point on most phones
    //
    // HOW TO SEE THE TAP:
    //   Open a drawing app (Google Keep, Samsung Notes, etc.) so there's
    //   something to tap ON. Then come back to this app and press "Send Tap."
    //   The tap fires at these coordinates even though a different app is visible.
    //   You should see a touch ripple at roughly the centre of the screen.
    //
    // These will be configurable in Milestone 3 (multi-point recording mode).
    private val TAP_X = 500f
    private val TAP_Y = 1000f

    /**
     * Called once when this screen is first created (or re-created after rotation).
     * This is where we set up the layout and wire up button click listeners.
     *
     * @param savedInstanceState  If the activity was previously destroyed and
     *                            recreated (e.g. screen rotation), Android passes
     *                            any previously saved state here. We don't use it
     *                            in M1 but it's a required parameter.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tell Android which XML file describes this screen's layout.
        // This inflates res/layout/activity_main.xml and sets it as the view.
        setContentView(R.layout.activity_main)

        // ── Grab widget references ─────────────────────────────────────────
        // findViewById() searches the inflated layout for a view with the given ID.
        // The IDs are declared in activity_main.xml (android:id="@+id/...").
        // We cast to the correct type with the generic parameter <T>.
        val statusText      = findViewById<TextView>(R.id.textViewStatus)
        val btnOpenSettings = findViewById<Button>(R.id.buttonOpenSettings)
        val btnTap          = findViewById<Button>(R.id.buttonSendTap)

        // ── BUTTON 1: Open Accessibility Settings ─────────────────────────
        // Android deliberately prevents apps from enabling their own accessibility
        // service programmatically — it's a security gate. Any app that could
        // silently self-enable would be a serious privacy and security risk.
        //
        // So instead, we send the user to the system Settings screen and let
        // them flip the switch manually. This is the expected UX pattern.
        btnOpenSettings.setOnClickListener {
            // Settings.ACTION_ACCESSIBILITY_SETTINGS is a constant string that equals
            // "android.settings.ACCESSIBILITY_SETTINGS" — the deep-link URI for the
            // Accessibility settings page.
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // ── BUTTON 2: Send Tap ─────────────────────────────────────────────
        btnTap.setOnClickListener {

            // Check if the service is currently running.
            // TapAccessibilityService.instance is set in onServiceConnected()
            // and cleared in onDestroy(), so null = service not running.
            val service = TapAccessibilityService.instance

            if (service == null) {
                // Service is off — tell the user to enable it first.
                statusText.text = getString(R.string.status_service_off)
            } else {
                // Service is live — fire the tap at our hardcoded test coordinates.
                service.performTap(TAP_X, TAP_Y)

                // Update the label to confirm the tap was sent, including the coordinates.
                // %1$d and %2$d are format placeholders — see strings.xml.
                statusText.text = getString(
                    R.string.status_tap_sent,
                    TAP_X.toInt(),
                    TAP_Y.toInt()
                )
            }
        }
    }

    /**
     * onResume() fires every time this screen becomes visible:
     *   • First launch (after onCreate)
     *   • Returning from another app (like the Accessibility Settings screen)
     *   • Returning from the back stack
     *
     * We refresh the status label here so it updates instantly when the user
     * comes back after enabling or disabling the service in Settings.
     * If we only updated in onCreate(), the label would be stale until the
     * activity was fully recreated.
     */
    override fun onResume() {
        super.onResume()

        val statusText = findViewById<TextView>(R.id.textViewStatus)

        // Show current service state. This check is instant — it's just a
        // null-check on a static variable, not a slow system query.
        statusText.text = if (TapAccessibilityService.instance != null) {
            getString(R.string.status_service_on)
        } else {
            getString(R.string.status_service_off)
        }
    }
}
