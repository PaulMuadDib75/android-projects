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
import android.util.Log
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
    private val TAP_X = 504f
    private val TAP_Y = 1277f

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

        Log.d("TapDebug", "Screen size: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")

        // ── Grab widget references ─────────────────────────────────────────
        // findViewById() searches the inflated layout for a view with the given ID.
        // The IDs are declared in activity_main.xml (android:id="@+id/...").
        // We cast to the correct type with the generic parameter <T>.
        val statusText      = findViewById<TextView>(R.id.textViewStatus)
        val btnOpenSettings = findViewById<Button>(R.id.buttonOpenSettings)
        val btnTap          = findViewById<Button>(R.id.buttonSendTap)
        val btnTargetTest   = findViewById<Button>(R.id.buttonTargetTest)

        // ── VERIFY: does the target button's actual rendered position match ──
        // the hardcoded tap coordinates (TAP_X, TAP_Y)?
        //
        // WHY THIS CAN DIFFER EVEN THOUGH THE XML SAYS "444px, 978px, 120px":
        // Those margins are relative to the ConstraintLayout's own origin,
        // NOT necessarily the screen's absolute (0,0). If the activity isn't
        // laid out edge-to-edge, the status bar (and on some devices, a
        // display cutout) pushes the content view's origin down from the
        // true top of the screen. dispatchGesture(), on the other hand,
        // always uses ABSOLUTE SCREEN coordinates. So a view whose layout
        // margins say "978px from the top of my parent" can easily be
        // several dozen pixels away from screen y=978 — enough to miss the
        // button's clickable bounds entirely.
        //
        // getLocationOnScreen() returns the view's actual top-left corner in
        // that same absolute-screen coordinate space that dispatchGesture()
        // uses, so we can compare directly against TAP_X/TAP_Y.
        //
        // post{} is required here: at this point in onCreate() the view has
        // been created but not yet MEASURED or LAID OUT, so width/height/
        // location would all read as 0. post{} queues this block to run
        // after the first layout pass completes.
        btnTargetTest.post {
            val location = IntArray(2)
            btnTargetTest.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val centerX = left + btnTargetTest.width / 2
            val centerY = top + btnTargetTest.height / 2
            Log.d(
                "TapDebug",
                "Target button actual screen bounds: left=$left, top=$top, " +
                    "width=${btnTargetTest.width}, height=${btnTargetTest.height}, " +
                    "center=($centerX, $centerY)  |  hardcoded tap target=($TAP_X, $TAP_Y)"
            )
        }

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

        // ── TARGET TEST BUTTON: proves the injected tap is functionally real ──
        // This button sits exactly under the (TAP_X, TAP_Y) point (see
        // activity_main.xml). "Show taps" only proves a ripple was drawn on
        // screen — it does NOT prove the accessibility-injected gesture
        // actually reached the Android input pipeline as a real touch event.
        // If performTap() genuinely works, the OS delivers the synthetic tap
        // the same way it would a real finger tap, which means THIS button's
        // own onClick fires — not just a visual effect at that location.
        btnTargetTest.setOnClickListener {
            Log.d("TapDebug", "Target button onClick fired - injected gesture is functionally real!")
            statusText.text = getString(R.string.status_target_tapped)
            statusText.setBackgroundColor(getColor(R.color.target_tapped_green))
        }

        // ── BUTTON 2: Send Tap ─────────────────────────────────────────────
        btnTap.setOnClickListener {

            // Check if the service is currently running.
            // TapAccessibilityService.instance is set in onServiceConnected()
            // and cleared in onDestroy(), so null = service not running.
            val service = TapAccessibilityService.instance

            Log.d("TapDebug", "Button pressed. Service instance is ${if (service == null) "NULL" else "NOT NULL"}")

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
