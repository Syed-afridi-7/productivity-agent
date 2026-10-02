package com.syedafridi.productivity_agent.services

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.syedafridi.productivity_agent.bus.AgentFocusState
import com.syedafridi.productivity_agent.bus.NativeAgentBus
import com.syedafridi.productivity_agent.services.BlacklistManager
import com.syedafridi.productivity_agent.ui.ShieldOverlayView

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ProductivityAgent"
    }

    private var lastPackageName: String? = null
    private var currentOverlayView: ShieldOverlayView? = null
    private var windowManager: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            if (!PackageFilter.isTargetUserApp(pkgName, packageName)) return
            if (pkgName == lastPackageName) return

            lastPackageName = pkgName
            try {
                Log.i(TAG, "App switch detected: $pkgName")
            } catch (_: Throwable) {
                println("[$TAG] App switch detected: $pkgName")
            }

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
        dismissOverlay()
        super.onDestroy()
    }
}
