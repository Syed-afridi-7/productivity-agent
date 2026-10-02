package com.syedafridi.productivity_agent.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.syedafridi.productivity_agent.bus.NativeAgentBus

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ProductivityAgent"
    }

    private var lastPackageName: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            if (pkgName == lastPackageName) return

            if (PackageFilter.isTargetUserApp(pkgName, packageName)) {
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
            }
        }
    }

    override fun onInterrupt() {
        try {
            Log.w(TAG, "MyAccessibilityService interrupted")
        } catch (_: Throwable) {
            println("[$TAG] MyAccessibilityService interrupted")
        }
    }
}
