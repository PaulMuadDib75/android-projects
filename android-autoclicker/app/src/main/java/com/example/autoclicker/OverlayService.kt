package com.example.autoclicker

// ─── IMPORTS ──────────────────────────────────────────────────────────────────
// Service              — a background component with no UI of its own. Unlike
//                         AccessibilityService, this is a PLAIN service: it has
//                         no special system privileges, it's just a way to run
//                         code that outlives any one screen (Activity).
// WindowManager         — the system service that lets an app add a "window"
//                         (a floating view) directly onto the screen, on top
//                         of whatever else is showing — this is how the
//                         floating button gets drawn over other apps.
// NotificationChannel/Manager, NotificationCompat, PendingIntent
//                       — required plumbing for the persistent notification a
//                         foreground service must show (see class doc below).
// Handler / Looper      — Android's basic "run this again after N milliseconds"
//                         mechanism. This is what drives the repeating tap loop.
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * OverlayService
 * ═══════════════
 * Milestone 2's second core piece (alongside TapAccessibilityService from M1).
 * This service:
 *   1. Draws a small, draggable floating button on top of every other app,
 *      using WindowManager + TYPE_APPLICATION_OVERLAY.
 *   2. Runs as a FOREGROUND service (a persistent notification, Android
 *      won't casually kill it) so the button stays up reliably.
 *   3. Owns a simple repeating tap loop: tapping the floating button toggles
 *      it on/off. While "on", it calls TapAccessibilityService.instance
 *      ?.performTap() once per second at the same hardcoded M1 test
 *      coordinates (MainActivity.TAP_X / TAP_Y).
 *
 * Electrician analogy: TapAccessibilityService is the licensed technician who
 * can actually pull the lever at the panel (dispatchGesture()). OverlayService
 * is more like a remote pushbutton station wired up somewhere convenient —
 * it doesn't do the switching itself, it just tells the technician when to.
 *
 * WHY A SEPARATE SERVICE FROM TapAccessibilityService:
 *   TapAccessibilityService only exists/runs because the user enabled it as
 *   an Accessibility Service in system Settings — a slow, deliberate,
 *   heavyweight permission most users grant once. The floating button's
 *   visibility, on the other hand, is something the user will want to freely
 *   show/hide throughout normal use (Show Overlay / Hide Overlay in
 *   MainActivity). Bundling that into the accessibility service would mean
 *   "hiding the button" and "disabling tap injection entirely" become the
 *   same action — which they should not be. Two focused components, one job
 *   each, is simpler to reason about than one that does both.
 *
 * WHY A FOREGROUND SERVICE INSTEAD OF JUST HOLDING THE VIEW IN MainActivity:
 *   The overlay must keep working even after the user leaves this app and
 *   switches to the game/app they actually want to auto-tap. An Activity is
 *   killed/backgrounded the moment the user switches away; a foreground
 *   service is specifically designed to keep running through that.
 */
class OverlayService : Service() {

    companion object {
        // Same static-reference pattern as TapAccessibilityService.instance in
        // M1 — lets MainActivity check "is the overlay currently showing?"
        // with a simple null-check, no bound-service/AIDL complexity needed.
        @Volatile
        var instance: OverlayService? = null
            private set

        // Notification channel/id — arbitrary but must be unique within the app.
        private const val CHANNEL_ID = "autoclicker_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        // Fixed cadence for the M2 tap loop. Per-point/adjustable intervals
        // are Milestone 4 — this is intentionally a single hardcoded number.
        private const val TAP_INTERVAL_MS = 1000L
    }

    // ─── WINDOW / VIEW STATE ────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var toggleButtonView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // ─── TAP LOOP STATE ─────────────────────────────────────────────────────
    // Handler tied to the MAIN looper: performTap() ultimately calls
    // dispatchGesture(), which (like all UI/input APIs) must be driven from
    // the main thread.
    private val tapHandler = Handler(Looper.getMainLooper())
    private var isTapLoopRunning = false

    // A self-rescheduling Runnable: each run() both fires a tap AND queues
    // its own next run TAP_INTERVAL_MS later. This is the standard Android
    // pattern for "repeat until cancelled" without needing a Timer/Thread.
    private val tapRunnable = object : Runnable {
        override fun run() {
            TapAccessibilityService.instance?.performTap(MainActivity.TAP_X, MainActivity.TAP_Y)
            tapHandler.postDelayed(this, TAP_INTERVAL_MS)
        }
    }


    // ─── LIFECYCLE: CREATE ──────────────────────────────────────────────────

    /**
     * Called once when the service is first started (MainActivity's
     * ContextCompat.startForegroundService() call). This is where we:
     *   1. Register ourselves as the running instance (so MainActivity can
     *      find us).
     *   2. Promote to a foreground service (required within seconds, or
     *      Android throws a fatal ForegroundServiceDidNotStartInTimeException).
     *   3. Add the floating button to the screen.
     */
    override fun onCreate() {
        super.onCreate()

        Log.d("TapDebug", "OverlayService onCreate()")
        instance = this

        // startForeground() FIRST, before any other setup — see the "must
        // happen within seconds" note above. Nothing before this line does
        // slow work, so there's no risk of missing the deadline.
        startForegroundWithNotification()

        // Defensive guard: MainActivity already checks Settings.canDrawOverlays()
        // before starting this service, but permissions can theoretically be
        // revoked in the brief gap between that check and this service
        // actually starting. Failing safely here (stop instead of crashing
        // with a SecurityException from WindowManager.addView) is cheap
        // insurance.
        if (!Settings.canDrawOverlays(this)) {
            Log.d("TapDebug", "OverlayService started without overlay permission - stopping")
            stopSelf()
            return
        }

        addOverlayView()
    }

    /**
     * Called every time startForegroundService()/startService() is invoked
     * on an already-running instance — unlike onCreate(), this CAN fire more
     * than once. We don't need any per-start work here (the overlay view is
     * only ever created once, in onCreate()), so we just tell Android not to
     * bother restarting us automatically if the system kills the process.
     *
     * START_NOT_STICKY: if OverlayService is killed (e.g. extreme low
     * memory), it simply stays stopped. The user can press "Show Overlay"
     * again from MainActivity to bring it back. This keeps the service's
     * lifecycle fully explicit and user-driven, rather than having Android
     * silently restart it in the background with no button/notification
     * context to explain why it's running again.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }


    // ─── LIFECYCLE: DESTROY ─────────────────────────────────────────────────

    /**
     * Called when the service is stopping — either MainActivity's
     * "Hide Overlay" button called stopService(), or the system is killing
     * us. MUST clean up the tap loop and the WindowManager view, or we leak
     * both a dangling repeating tap and an orphaned floating button.
     */
    override fun onDestroy() {
        Log.d("TapDebug", "OverlayService onDestroy()")

        stopTapLoop()
        removeOverlayView()
        instance = null

        super.onDestroy()
    }

    /**
     * This is a plain started service (see class doc) — nothing ever binds
     * to it, so there is no interface to hand back here.
     */
    override fun onBind(intent: Intent?): IBinder? = null


    // ─── NOTIFICATION SETUP ─────────────────────────────────────────────────

    /**
     * Foreground services are legally required to show a notification — it's
     * how the user always knows something is running in the background even
     * when no app screen is open. IMPORTANCE_LOW means it shows quietly in
     * the notification shade with no sound or heads-up popup, appropriate
     * for a background utility rather than something urgent.
     */
    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // Tapping the notification reopens MainActivity. FLAG_IMMUTABLE is
        // required from API 31 onward (and harmless/recommended below it) —
        // it tells Android this PendingIntent's contents can't be modified
        // by whoever receives it, a security hardening requirement.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true) // user cannot swipe it away while the service is running
            .setContentIntent(pendingIntent)
            .build()

        // The foreground service TYPE (specialUse) comes from the manifest's
        // android:foregroundServiceType attribute on this service's
        // declaration — it is not passed again here.
        startForeground(NOTIFICATION_ID, notification)
    }


    // ─── OVERLAY VIEW SETUP ─────────────────────────────────────────────────

    /**
     * Inflates overlay_panel.xml and hands it to WindowManager to draw on
     * top of every other app.
     */
    private fun addOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        val toggleButton = view.findViewById<View>(R.id.overlayToggleButton)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_APPLICATION_OVERLAY is the modern (API 26+) window type for
            // "draw over other apps." Safe to use unconditionally here since
            // this project's minSdk is 26 — no older TYPE_PHONE/TYPE_SYSTEM_ALERT
            // fallback branch is needed.
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE: the overlay must NEVER steal keyboard/input
            // focus from whatever app (game) is running underneath it.
            // FLAG_NOT_TOUCH_MODAL: touches outside the button's own small
            // bounds pass through to the app underneath, instead of being
            // swallowed by our window.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT // lets the round button's transparent corners show the app behind it
        ).apply {
            // TOP|START gravity is NOT cosmetic — it makes x/y plain top-left
            // screen offsets, which is what the drag math below assumes.
            gravity = Gravity.TOP or Gravity.START
            x = 100 // arbitrary fixed starting position
            y = 300
        }

        setupDragAndToggle(toggleButton)

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
            toggleButtonView = toggleButton
        } catch (e: Exception) {
            // WindowManager can throw if overlay permission was revoked in
            // the gap between MainActivity's check and this call, or on some
            // OEM skins under unusual states. Log and stop rather than crash.
            Log.e("TapDebug", "Failed to add overlay view", e)
            stopSelf()
        }
    }

    /**
     * Removes the floating button from the screen. Safe to call even if the
     * view was never successfully added.
     */
    private fun removeOverlayView() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e("TapDebug", "Failed to remove overlay view", e)
        }
        overlayView = null
        toggleButtonView = null
    }


    // ─── TOUCH HANDLING: DRAG-TO-MOVE vs TAP-TO-TOGGLE ─────────────────────

    /**
     * A single touch listener drives BOTH dragging the button around the
     * screen AND tapping it to toggle the tap loop on/off. There is no
     * separate click listener — that would risk firing twice for the same
     * gesture, since every touch event here is consumed (returns true).
     *
     * THE COORDINATE-SPACE TRAP (same category of bug as the M1 lesson
     * documented in CLAUDE.md about layout-relative vs absolute-screen
     * coordinates — worth calling out again here since it shows up in a new
     * spot):
     *   event.getX()/getY() are VIEW-LOCAL coordinates (0,0 = this view's own
     *   top-left corner). Using them for drag math would be wrong, because
     *   the view's on-screen position changes DURING the drag — so its local
     *   coordinate space shifts under you every frame, and deltas computed
     *   from it silently drift/jitter.
     *   event.getRawX()/getRawY() are ABSOLUTE SCREEN coordinates — the same
     *   coordinate space WindowManager.LayoutParams.x/y uses (given our
     *   TOP|START gravity above). Always using raw coordinates, measured
     *   against the single ACTION_DOWN starting point (never accumulated
     *   frame-to-frame), keeps the math correct and drift-free.
     */
    private fun setupDragAndToggle(view: View) {
        // Android's own standard "how far is a drag, not a tap" threshold —
        // the same constant used internally by scrolling views. Not an
        // invented magic number.
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        var startParamsX = 0
        var startParamsY = 0
        var startRawX = 0f
        var startRawY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startParamsX = layoutParams.x
                    startParamsY = layoutParams.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    isDragging = false
                    true // consume, so we keep receiving MOVE/UP for this gesture
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY

                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams.x = startParamsX + dx.toInt()
                        layoutParams.y = startParamsY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(overlayView, layoutParams)
                        } catch (e: Exception) {
                            Log.e("TapDebug", "Failed to update overlay position", e)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Only treat this as a tap (toggle) if the finger never
                    // moved past the drag threshold during this gesture.
                    if (!isDragging) {
                        toggleTapLoop()
                    }
                    true
                }

                else -> false
            }
        }
    }


    // ─── TAP LOOP CONTROL ───────────────────────────────────────────────────

    private fun toggleTapLoop() {
        if (isTapLoopRunning) stopTapLoop() else startTapLoop()
    }

    private fun startTapLoop() {
        if (isTapLoopRunning) return
        Log.d("TapDebug", "Tap loop started")
        isTapLoopRunning = true
        // .post() (not .postDelayed()) so the FIRST tap fires immediately;
        // every subsequent tap is scheduled from inside tapRunnable itself.
        tapHandler.post(tapRunnable)
        toggleButtonView?.setBackgroundResource(R.drawable.overlay_toggle_on)
    }

    private fun stopTapLoop() {
        Log.d("TapDebug", "Tap loop stopped")
        isTapLoopRunning = false
        // MUST remove the pending callback — tapRunnable re-posts itself
        // every cycle, so without this it would keep firing taps forever,
        // even after the button is toggled "off" or the service is destroyed.
        tapHandler.removeCallbacks(tapRunnable)
        toggleButtonView?.setBackgroundResource(R.drawable.overlay_toggle_off)
    }
}
