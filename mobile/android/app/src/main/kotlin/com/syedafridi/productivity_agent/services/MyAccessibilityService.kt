package com.syedafridi.productivity_agent.services

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.syedafridi.productivity_agent.bus.AgentFocusState
import com.syedafridi.productivity_agent.bus.NativeAgentBus
import com.syedafridi.productivity_agent.ui.ShieldOverlayView

class MyAccessibilityService : AccessibilityService() {

    enum class ActiveTrackingMode {
        NONE,
        GAMING,
        REELS
    }

    companion object {
        private const val TAG = "ProductivityAgent"
    }

    private var lastPackageName: String? = null
    private var currentOverlayView: ShieldOverlayView? = null
    private var windowManager: WindowManager? = null

    private var activeTrackingMode = ActiveTrackingMode.NONE
    private var currentTrackedPackage: String? = null
    private val trackingHandler = Handler(Looper.getMainLooper())

    private val trackingTicker = object : Runnable {
        override fun run() {
            when (activeTrackingMode) {
                ActiveTrackingMode.GAMING -> {
                    AutonomousQuotaManager.recordGamingTick(1)
                    val snapshot = AutonomousQuotaManager.getSnapshot()
                    NativeAgentBus.emit(
                        mapOf(
                            "type" to "QUOTA_TICK",
                            "category" to "GAMING",
                            "gamingUsed" to snapshot.gamingSecondsUsed,
                            "reelsUsed" to snapshot.reelsSecondsUsed,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    if (AutonomousQuotaManager.isGamingExhausted()) {
                        try {
                            Log.w(TAG, "Gaming daily quota (30m) exhausted! Returning home.")
                        } catch (_: Throwable) {
                            println("[$TAG] Gaming daily quota (30m) exhausted! Returning home.")
                        }
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        NativeAgentBus.emit(
                            mapOf(
                                "type" to "APP_BLOCKED",
                                "packageName" to (currentTrackedPackage ?: "unknown_game"),
                                "reason" to "GAMING_QUOTA_EXHAUSTED",
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                        activeTrackingMode = ActiveTrackingMode.NONE
                    }
                }
                ActiveTrackingMode.REELS -> {
                    AutonomousQuotaManager.recordReelsTick(1)
                    val snapshot = AutonomousQuotaManager.getSnapshot()
                    NativeAgentBus.emit(
                        mapOf(
                            "type" to "QUOTA_TICK",
                            "category" to "REELS",
                            "gamingUsed" to snapshot.gamingSecondsUsed,
                            "reelsUsed" to snapshot.reelsSecondsUsed,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    if (AutonomousQuotaManager.isReelsExhausted()) {
                        try {
                            Log.w(TAG, "Reels daily quota (20m) exhausted! Returning home.")
                        } catch (_: Throwable) {
                            println("[$TAG] Reels daily quota (20m) exhausted! Returning home.")
                        }
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        NativeAgentBus.emit(
                            mapOf(
                                "type" to "APP_BLOCKED",
                                "packageName" to (currentTrackedPackage ?: "unknown_reels"),
                                "reason" to "REELS_QUOTA_EXHAUSTED",
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                        activeTrackingMode = ActiveTrackingMode.NONE
                    }
                }
                ActiveTrackingMode.NONE -> {}
            }
            trackingHandler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        AutonomousQuotaManager.init(applicationContext)
        trackingHandler.post(trackingTicker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            val className = event.className?.toString()
            val contentDesc = event.contentDescription?.toString()
            val text = event.text?.joinToString(" ")

            // 1. Zero-Tolerance Hard Block (Telegram, Netflix, Prime Video, Hotstar, Disney+)
            if (AutonomousQuotaManager.isHardBlocked(pkgName)) {
                try {
                    Log.w(TAG, "Hard-blocked app detected: $pkgName. Kicking to home.")
                } catch (_: Throwable) {
                    println("[$TAG] Hard-blocked app detected: $pkgName. Kicking to home.")
                }
                performGlobalAction(GLOBAL_ACTION_HOME)
                NativeAgentBus.emit(
                    mapOf(
                        "type" to "APP_BLOCKED",
                        "packageName" to pkgName,
                        "reason" to "HARD_BLOCKED",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                activeTrackingMode = ActiveTrackingMode.NONE
                return
            }

            // 2. Direct Messages & Chat Exception (Instagram / Facebook / Messaging)
            if (SubScreenClassifier.isDirectMessage(pkgName, className, contentDesc)) {
                try {
                    Log.i(TAG, "Direct Message / Chat active in $pkgName. Allowed without penalty.")
                } catch (_: Throwable) {}
                activeTrackingMode = ActiveTrackingMode.NONE
                return
            }

            // 3. Reels / Shorts Discrimination (Instagram / YouTube / Facebook)
            if (SubScreenClassifier.isReelsOrShorts(pkgName, className, contentDesc, text)) {
                if (AutonomousQuotaManager.isReelsExhausted()) {
                    try {
                        Log.w(TAG, "Reels quota exhausted! Blocking $pkgName.")
                    } catch (_: Throwable) {}
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    NativeAgentBus.emit(
                        mapOf(
                            "type" to "APP_BLOCKED",
                            "packageName" to pkgName,
                            "reason" to "REELS_QUOTA_EXHAUSTED",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    activeTrackingMode = ActiveTrackingMode.NONE
                } else {
                    currentTrackedPackage = pkgName
                    activeTrackingMode = ActiveTrackingMode.REELS
                }
                return
            }

            // 4. Game Detection (30m daily quota)
            if (SubScreenClassifier.isGame(this, pkgName)) {
                if (AutonomousQuotaManager.isGamingExhausted()) {
                    try {
                        Log.w(TAG, "Gaming quota exhausted! Blocking $pkgName.")
                    } catch (_: Throwable) {}
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    NativeAgentBus.emit(
                        mapOf(
                            "type" to "APP_BLOCKED",
                            "packageName" to pkgName,
                            "reason" to "GAMING_QUOTA_EXHAUSTED",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    activeTrackingMode = ActiveTrackingMode.NONE
                } else {
                    currentTrackedPackage = pkgName
                    activeTrackingMode = ActiveTrackingMode.GAMING
                }
                return
            }

            // 5. System Whitelist (Dialer, Contacts, ChatGPT, Browser Search)
            if (SubScreenClassifier.isWhitelistedActivity(pkgName)) {
                activeTrackingMode = ActiveTrackingMode.NONE
                return
            }

            // 6. User-defined Focus Mode & Blacklist (if manual deep focus session is active)
            if (!PackageFilter.isTargetUserApp(pkgName, packageName)) {
                activeTrackingMode = ActiveTrackingMode.NONE
                return
            }

            activeTrackingMode = ActiveTrackingMode.NONE

            if (pkgName == lastPackageName) return
            lastPackageName = pkgName

            NativeAgentBus.emit(
                mapOf(
                    "type" to "APP_OPENED",
                    "packageName" to pkgName,
                    "timestamp" to System.currentTimeMillis()
                )
            )

            if (AgentFocusState.isFocusActive && BlacklistManager.isBlacklisted(pkgName) && !BlacklistManager.hasActiveEmergencyPass(pkgName)) {
                try {
                    Log.w(TAG, "Blacklisted app intercepted: $pkgName")
                } catch (_: Throwable) {
                    println("[$TAG] Blacklisted app intercepted: $pkgName")
                }
                NativeAgentBus.emit(
                    mapOf(
                        "type" to "APP_BLOCKED",
                        "packageName" to pkgName,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                if (Settings.canDrawOverlays(this)) {
                    showOverlay(pkgName)
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun showOverlay(targetPackage: String) {
        dismissOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val overlay = ShieldOverlayView(
            context = this,
            targetPackage = targetPackage,
            onReturnToFocus = {
                dismissOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onEmergencyPass = {
                BlacklistManager.grantEmergencyPass(targetPackage, 60)
                NativeAgentBus.emit(
                    mapOf(
                        "type" to "APP_BLOCKED",
                        "packageName" to targetPackage,
                        "action" to "EMERGENCY_PASS_GRANTED",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                dismissOverlay()
            }
        )
        currentOverlayView = overlay
        try {
            windowManager?.addView(overlay, params)
        } catch (t: Throwable) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun dismissOverlay() {
        currentOverlayView?.let { overlay ->
            overlay.dismiss()
            try {
                windowManager?.removeView(overlay)
            } catch (_: Throwable) {}
            currentOverlayView = null
        }
    }

    override fun onInterrupt() {
        try {
            Log.w(TAG, "MyAccessibilityService interrupted")
        } catch (_: Throwable) {
            println("[$TAG] MyAccessibilityService interrupted")
        }
    }

    override fun onDestroy() {
        trackingHandler.removeCallbacks(trackingTicker)
        dismissOverlay()
        super.onDestroy()
    }
}
