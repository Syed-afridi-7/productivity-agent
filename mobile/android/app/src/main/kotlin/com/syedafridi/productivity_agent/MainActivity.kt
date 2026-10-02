package com.syedafridi.productivity_agent

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val COMMAND_CHANNEL = "com.syedafridi.productivity_agent/commands"
    private val EVENT_CHANNEL = "com.syedafridi.productivity_agent/events"

    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRunning = false
    private var currentState = "IDLE"
    private var remainingSeconds = 0
    private var todayFocusMinutes = 45
    private var distractionsBlocked = 3
    private var nudgesSent = 2

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (isRunning && remainingSeconds > 0) {
                remainingSeconds -= 1
                val event = mapOf(
                    "type" to "TICK",
                    "remainingSeconds" to remainingSeconds
                )
                eventSink?.success(event)
                mainHandler.postDelayed(this, 1000)
            } else if (isRunning && remainingSeconds <= 0) {
                isRunning = false
                currentState = "IDLE"
                val event = mapOf(
                    "type" to "STATE_CHANGED",
                    "from" to "DEEP_FOCUS",
                    "to" to "IDLE",
                    "remainingSeconds" to 0
                )
                eventSink?.success(event)
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // EventChannel for live streaming
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            }
        )

        // MethodChannel for Flutter commands
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, COMMAND_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startAgent" -> {
                    val durationMinutes = call.argument<Int>("durationMinutes") ?: 25
                    remainingSeconds = durationMinutes * 60
                    isRunning = true
                    currentState = "DEEP_FOCUS"

                    mainHandler.removeCallbacks(tickerRunnable)
                    mainHandler.postDelayed(tickerRunnable, 1000)

                    val event = mapOf(
                        "type" to "STATE_CHANGED",
                        "from" to "IDLE",
                        "to" to "DEEP_FOCUS",
                        "remainingSeconds" to remainingSeconds
                    )
                    eventSink?.success(event)

                    result.success(
                        mapOf(
                            "success" to true,
                            "state" to currentState,
                            "durationSec" to remainingSeconds
                        )
                    )
                }

                "stopAgent" -> {
                    isRunning = false
                    currentState = "IDLE"
                    mainHandler.removeCallbacks(tickerRunnable)

                    val event = mapOf(
                        "type" to "STATE_CHANGED",
                        "from" to "DEEP_FOCUS",
                        "to" to "IDLE",
                        "remainingSeconds" to 0
                    )
                    eventSink?.success(event)

                    result.success(
                        mapOf(
                            "success" to true,
                            "state" to "IDLE"
                        )
                    )
                }

                "getAgentStatus" -> {
                    result.success(
                        mapOf(
                            "isRunning" to isRunning,
                            "state" to currentState,
                            "remainingSeconds" to remainingSeconds,
                            "todayFocusMinutes" to todayFocusMinutes,
                            "distractionsBlocked" to distractionsBlocked,
                            "nudgesSent" to nudgesSent
                        )
                    )
                }

                "isAccessibilityEnabled" -> {
                    val isEnabled = checkAccessibilityPermission()
                    result.success(isEnabled)
                }

                "openAccessibilitySettings" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedServiceName = "$packageName/.MyAccessibilityService"
        return enabledServices.contains(expectedServiceName) || enabledServices.contains(packageName)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickerRunnable)
        super.onDestroy()
    }
}
