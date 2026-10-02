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
import android.view.accessibility.AccessibilityNodeInfo
import com.syedafridi.productivity_agent.bus.AgentFocusState
import com.syedafridi.productivity_agent.bus.NativeAgentBus
import com.syedafridi.productivity_agent.ui.ProductivityWarningOverlay
import com.syedafridi.productivity_agent.ui.ShieldOverlayView

class MyAccessibilityService : AccessibilityService() {

    enum class ActiveTrackingMode {
        NONE,
        GAMING,
        REELS,
        CONTENT_MONITORING  // YouTube long-form / Browser — evaluation or warning active
    }

    companion object {
        private const val TAG = "ProductivityAgent"
    }

    private var lastPackageName: String? = null
    private var currentOverlayView: ShieldOverlayView? = null
    private var currentWarningOverlay: ProductivityWarningOverlay? = null
    private var windowManager: WindowManager? = null

    private var activeTrackingMode = ActiveTrackingMode.NONE
    private var currentTrackedPackage: String? = null
    private val trackingHandler = Handler(Looper.getMainLooper())

    private var lastHierarchyExtractionTime = 0L
    private var cachedHierarchyText: String = ""

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
                        kickToHome(currentTrackedPackage ?: "unknown_game", "GAMING_QUOTA_EXHAUSTED")
                    }
                }
                ActiveTrackingMode.CONTENT_MONITORING -> {
                    // Check if 3s loading buffer has expired with no productive confirmation → show warning
                    if (ContentGraceMonitor.currentState == GraceState.GRACE_PERIOD &&
                        ContentGraceMonitor.isGracePeriodExpired()) {
                        try {
                            Log.w(TAG, "Inspection buffer expired for ${currentTrackedPackage}. Showing 12s auto-kick warning.")
                        } catch (_: Throwable) {}
                        ContentGraceMonitor.startWarningPhase()
                        NativeAgentBus.emit(
                            mapOf(
                                "type" to "CONTENT_WARNING",
                                "packageName" to (currentTrackedPackage ?: "unknown"),
                                "message" to "Unproductive Content Detected - Closing in 12s",
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                        if (Settings.canDrawOverlays(this@MyAccessibilityService)) {
                            showProductivityWarningOverlay()
                        } else {
                            kickToHome(currentTrackedPackage ?: "unknown", "CONTENT_BUFFER_EXPIRED")
                        }
                    }
                    // Check if 12s warning phase has expired → kick out to Home!
                    if (ContentGraceMonitor.currentState == GraceState.WARNING_PHASE &&
                        ContentGraceMonitor.isWarningExpired()) {
                        try {
                            Log.w(TAG, "12s countdown expired. Blocking ${currentTrackedPackage}.")
                        } catch (_: Throwable) {}
                        dismissProductivityWarning()
                        ContentGraceMonitor.deactivate()
                        kickToHome(currentTrackedPackage ?: "unknown", "CONTENT_UNPRODUCTIVE_EXPIRED")
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
                            "morningUsed" to snapshot.morningReelsUsed,
                            "afternoonUsed" to snapshot.afternoonReelsUsed,
                            "eveningUsed" to snapshot.eveningReelsUsed,
                            "currentWindow" to snapshot.currentWindow.label,
                            "currentWindowRemaining" to snapshot.currentWindowRemaining,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    if (AutonomousQuotaManager.isCurrentWindowExhausted()) {
                        try {
                            Log.w(TAG, "Reels current window quota (20m) exhausted! Returning home.")
                        } catch (_: Throwable) {
                            println("[$TAG] Reels current window quota (20m) exhausted! Returning home.")
                        }
                        kickToHome(currentTrackedPackage ?: "unknown_reels", "REELS_WINDOW_EXHAUSTED")
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

    /**
     * Packages requiring sub-screen inspection on EVERY window event.
     * These must NEVER be skipped by lastPackageName deduplication because
     * internal tab switches (e.g. Search → Shorts) share the same package.
     */
    private val SUB_SCREEN_PACKAGES = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana"
    )

    /** Centralised Home kickout — always resets tracking state. */
    private fun kickToHome(pkgName: String, reason: String) {
        dismissProductivityWarning()
        ContentGraceMonitor.deactivate()
        performGlobalAction(GLOBAL_ACTION_HOME)
        lastPackageName = null                       // ← fixes second-launch bypass
        activeTrackingMode = ActiveTrackingMode.NONE
        cachedHierarchyText = ""
        lastHierarchyExtractionTime = 0L
        NativeAgentBus.emit(
            mapOf(
                "type" to "APP_BLOCKED",
                "packageName" to pkgName,
                "reason" to reason,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    /**
     * Traverses the active window node hierarchy (up to maxNodes) to extract
     * video titles, channel names, descriptions, and interactive text.
     */
    private fun extractHierarchyText(maxNodes: Int = 40): String {
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return ""

        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++

            val text = node.text
            if (!text.isNullOrBlank()) {
                sb.append(text).append(" ")
            }
            val desc = node.contentDescription
            if (!desc.isNullOrBlank()) {
                sb.append(desc).append(" ")
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                } catch (_: Throwable) {}
            }
        }
        return sb.toString().trim()
    }

    private fun getOrRefreshHierarchyText(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (forceRefresh || (now - lastHierarchyExtractionTime > 800L) || cachedHierarchyText.isEmpty()) {
            cachedHierarchyText = extractHierarchyText()
            lastHierarchyExtractionTime = now
        }
        return cachedHierarchyText
    }

    private fun handleContentIntelligence(
        pkgName: String,
        contentDesc: String?,
        className: String?,
        text: String?,
        forceRefreshHierarchy: Boolean = false
    ) {
        val hierarchyText = getOrRefreshHierarchyText(forceRefreshHierarchy)
        val verdict = ContentIntelligenceEngine.classify(pkgName, contentDesc, className, text, hierarchyText)

        when (verdict) {
            ContentVerdict.PRODUCTIVE -> {
                try {
                    Log.i(TAG, "Content confirmed PRODUCTIVE in $pkgName. Allowing uninterrupted.")
                } catch (_: Throwable) {}
                dismissProductivityWarning()
                ContentGraceMonitor.markProductive()
                activeTrackingMode = ActiveTrackingMode.NONE
            }
            ContentVerdict.UNPRODUCTIVE -> {
                try {
                    Log.w(TAG, "Content detected UNPRODUCTIVE in $pkgName. Showing 12s auto-kick.")
                } catch (_: Throwable) {}
                currentTrackedPackage = pkgName
                activeTrackingMode = ActiveTrackingMode.CONTENT_MONITORING
                if (ContentGraceMonitor.currentState != GraceState.WARNING_PHASE) {
                    ContentGraceMonitor.startWarningPhase()
                }
                if (currentWarningOverlay == null && Settings.canDrawOverlays(this@MyAccessibilityService)) {
                    showProductivityWarningOverlay()
                } else if (!Settings.canDrawOverlays(this@MyAccessibilityService)) {
                    kickToHome(pkgName, "CONTENT_UNPRODUCTIVE")
                }
            }
            ContentVerdict.UNKNOWN -> {
                // If not currently in grace or warning phase, start the 3s inspection buffer
                if (!ContentGraceMonitor.isGracePeriodActive()) {
                    try {
                        Log.i(TAG, "Content UNKNOWN in $pkgName. Starting 3s inspection buffer.")
                    } catch (_: Throwable) {}
                    ContentGraceMonitor.startGracePeriod(pkgName)
                    activeTrackingMode = ActiveTrackingMode.CONTENT_MONITORING
                    currentTrackedPackage = pkgName
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        val pkgName = event.packageName?.toString() ?: return

        // Handle TYPE_WINDOW_CONTENT_CHANGED for active content monitoring in single-activity apps (e.g. YouTube)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (ContentIntelligenceEngine.isContentMonitoredApp(pkgName)) {
                val contentDesc = event.contentDescription?.toString()
                val text = event.text?.joinToString(" ")
                val className = event.className?.toString()
                handleContentIntelligence(pkgName, contentDesc, className, text, forceRefreshHierarchy = false)
            }
            return
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString()
            val contentDesc = event.contentDescription?.toString()
            val text = event.text?.joinToString(" ")

            // Check if user navigated away from grace-monitored package
            if (ContentGraceMonitor.isGracePeriodActive() &&
                ContentGraceMonitor.trackedPackage != pkgName &&
                !ContentIntelligenceEngine.isContentMonitoredApp(pkgName)) {
                dismissProductivityWarning()
                ContentGraceMonitor.deactivate()
                activeTrackingMode = ActiveTrackingMode.NONE
            }

            // 1. Zero-Tolerance Hard Block (Telegram, Netflix, Music apps, etc.)
            if (AutonomousQuotaManager.isHardBlocked(pkgName)) {
                try {
                    Log.w(TAG, "Hard-blocked app detected: $pkgName. Kicking to home.")
                } catch (_: Throwable) {
                    println("[$TAG] Hard-blocked app detected: $pkgName. Kicking to home.")
                }
                kickToHome(pkgName, "HARD_BLOCKED")
                return
            }

            // 2. Direct Messages & Chat Exception (Instagram / Facebook / Messaging)
            if (SubScreenClassifier.isDirectMessage(pkgName, className, contentDesc)) {
                try {
                    Log.i(TAG, "Direct Message / Chat active in $pkgName. Allowed without penalty.")
                } catch (_: Throwable) {}
                dismissProductivityWarning()
                activeTrackingMode = ActiveTrackingMode.NONE
                return
            }

            // 3. Reels / Shorts (Instagram / YouTube / Facebook) — Windowed 20m Quota
            if (SubScreenClassifier.isReelsOrShorts(pkgName, className, contentDesc, text)) {
                if (AutonomousQuotaManager.isCurrentWindowExhausted()) {
                    try {
                        Log.w(TAG, "Reels current window quota exhausted! Blocking $pkgName.")
                    } catch (_: Throwable) {}
                    kickToHome(pkgName, "REELS_WINDOW_EXHAUSTED")
                } else {
                    currentTrackedPackage = pkgName
                    activeTrackingMode = ActiveTrackingMode.REELS
                }
                return
            }

            // 3.5. Content Intelligence — YouTube long-form & Browsers
            if (ContentIntelligenceEngine.isContentMonitoredApp(pkgName)) {
                handleContentIntelligence(pkgName, contentDesc, className, text, forceRefreshHierarchy = true)
                return
            }

            // 4. Game Detection (30m daily quota)
            if (SubScreenClassifier.isGame(this, pkgName)) {
                if (AutonomousQuotaManager.isGamingExhausted()) {
                    try {
                        Log.w(TAG, "Gaming quota exhausted! Blocking $pkgName.")
                    } catch (_: Throwable) {}
                    kickToHome(pkgName, "GAMING_QUOTA_EXHAUSTED")
                } else {
                    currentTrackedPackage = pkgName
                    activeTrackingMode = ActiveTrackingMode.GAMING
                }
                return
            }

            // 5. System Whitelist (Dialer, Contacts, ChatGPT, Google Search box)
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

            // Skip dedup for social apps that need sub-screen inspection every time
            if (pkgName in SUB_SCREEN_PACKAGES) {
                lastPackageName = pkgName
                return
            }

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
                    kickToHome(pkgName, "BLACKLISTED")
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

    private fun showProductivityWarningOverlay() {
        if (currentWarningOverlay != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        val overlay = ProductivityWarningOverlay(
            context = this,
            onTimeExpired = {
                dismissProductivityWarning()
                ContentGraceMonitor.deactivate()
                kickToHome(currentTrackedPackage ?: "unknown", "CONTENT_UNPRODUCTIVE_EXPIRED")
            },
            onDismiss = { /* cleanup handled by dismissProductivityWarning */ }
        )
        currentWarningOverlay = overlay
        try {
            windowManager?.addView(overlay, params)
        } catch (_: Throwable) {}
    }

    private fun dismissProductivityWarning() {
        currentWarningOverlay?.let { overlay ->
            overlay.dismiss()
            try {
                windowManager?.removeView(overlay)
            } catch (_: Throwable) {}
            currentWarningOverlay = null
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
        dismissProductivityWarning()
        super.onDestroy()
    }
}
