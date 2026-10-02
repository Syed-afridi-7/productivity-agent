# Native Android Kotlin Foundation (Phase 2) Design Specification

## 1. Overview & Objectives
Phase 2 establishes the core native Android subsystem for the AI Productivity Agent companion app. It provides:
1. An in-memory thread-safe event bus (`NativeAgentBus`) connecting Android components to Flutter.
2. An Android `AccessibilityService` (`MyAccessibilityService`) detecting foreground app switches with system package filtering.
3. A persistent Foreground Service (`AgentForegroundService`) with an ongoing notification and dynamic screen state (ON/OFF) tracking.
4. Android 14+ (API 34) compliant permissions and manifest configuration.
5. Integration with `MainActivity.kt` and Flutter `EventChannel` for end-to-end telemetry.

---

## 2. Architecture & Communication Flow

```
┌────────────────────────────────────────────────────────┐
│                   Flutter UI Layer                     │
│         (DashboardScreen / AgentController)            │
└───────────────────────────▲────────────────────────────┘
                            │  EventChannel (".../events")
┌───────────────────────────┴────────────────────────────┐
│                    MainActivity.kt                     │
│  - MethodChannel: startAgent / stopAgent               │
│  - Subscribes to NativeAgentBus & pipes to EventChannel│
└───────────────▲────────────────────────▲───────────────┘
                │                        │
       Thread-safe Event Sink   Thread-safe Event Sink
                │                        │
┌───────────────┴───────────────┐ ┌──────┴───────────────┐
│     MyAccessibilityService    │ │ AgentForegroundService│
│                               │ │                      │
│ - TYPE_WINDOW_STATE_CHANGED   │ │ - Notification LOW   │
│ - System package filtering    │ │ - START_STICKY       │
│ - Real-time app switch detect │ │ - Screen ON/OFF rx   │
│ - Logcat [ProductivityAgent]  │ │ - Logcat logging     │
└───────────────▲───────────────┘ └──────────────────────┘
                │
   accessibility_service_config.xml
```

---

## 3. Component Specifications

### 3.1 Thread-Safe Event Bus (`NativeAgentBus.kt`)
- **Package:** `com.syedafridi.productivity_agent.bus`
- **Responsibilities:**
  - Maintains thread-safe subscription list: `CopyOnWriteArrayList<(Map<String, Any>) -> Unit>`.
  - Dispatches events to all registered listeners.
  - Logs all events to Logcat under tag `"ProductivityAgent"`.
  - Event payloads:
    - App switch: `{"type": "APP_OPENED", "packageName": String, "timestamp": Long}`
    - Screen state: `{"type": "SCREEN_STATE_CHANGED", "screenOn": Boolean, "timestamp": Long}`
    - Session state: `{"type": "STATE_CHANGED", "from": String, "to": String, "remainingSeconds": Int}`

### 3.2 App Detector Accessibility Service (`MyAccessibilityService.kt`)
- **Package:** `com.syedafridi.productivity_agent.services`
- **Inheritance:** `android.accessibilityservice.AccessibilityService`
- **Config XML:** `res/xml/accessibility_service_config.xml`
  - Event types: `typeWindowStateChanged | typeWindowContentChanged`
  - Feedback: `feedbackGeneric`
  - Flags: `flagRetrieveInteractiveWindows | flagIncludeNotImportantViews`
  - Timeout: `100ms`
- **Filtering Logic:**
  - Ignores null or empty package names.
  - Filters out common system packages:
    - `com.android.systemui`
    - Launcher packages (`com.google.android.apps.nexuslauncher`, `com.sec.android.app.launcher`, etc.)
    - Input method packages (keyboards)
    - Self package: `com.syedafridi.productivity_agent`
  - For valid third-party applications (e.g. `com.instagram.android`, `com.google.android.youtube`):
    - Emits `APP_OPENED` event to `NativeAgentBus`.
    - Logs info: `App switch detected: $pkgName`.

### 3.3 Persistent Guardian Service (`AgentForegroundService.kt`)
- **Package:** `com.syedafridi.productivity_agent.services`
- **Inheritance:** `android.app.Service`
- **Notification Configuration:**
  - Channel ID: `productivity_guardian_channel`
  - Channel Name: `Productivity Guardian Service`
  - Importance: `NotificationManager.IMPORTANCE_LOW` (silent, persistent)
  - Small icon: `@mipmap/ic_launcher`
  - Ongoing: `true`
  - Title: `Productivity Guardian Active`
  - Content Text: `Monitoring focus session (Deep Focus)`
- **Lifecycle & Actions:**
  - `ACTION_START`: creates notification channel, starts foreground with notification, registers screen broadcast receiver.
  - `ACTION_STOP`: unregisters receiver, stops foreground, calls `stopSelf()`.
  - `onStartCommand`: returns `START_STICKY`.
- **Screen State Tracking:**
  - Registers dynamic `BroadcastReceiver` for:
    - `Intent.ACTION_SCREEN_ON`
    - `Intent.ACTION_SCREEN_OFF`
  - Dispatches `SCREEN_STATE_CHANGED` event to `NativeAgentBus`.

### 3.4 AndroidManifest Configuration
- Permissions:
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` (Android 14 API 34 requirement)
  - `android.permission.POST_NOTIFICATIONS`
  - `android.permission.WAKE_LOCK`
  - `android.permission.BIND_ACCESSIBILITY_SERVICE`
- Service Declarations:
  - `MyAccessibilityService` with BIND_ACCESSIBILITY_SERVICE permission and meta-data referencing `@xml/accessibility_service_config`.
  - `AgentForegroundService` with `foregroundServiceType="specialUse"` and property `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.

### 3.5 MainActivity Bridge Integration
- Registers listener on `NativeAgentBus` inside `configureFlutterEngine`.
- Forwards bus events directly into Flutter `eventSink` on UI thread.
- `startAgent`: Starts `AgentForegroundService` via `ContextCompat.startForegroundService`.
- `stopAgent`: Sends `ACTION_STOP` intent to `AgentForegroundService`.
- Maintains permission check and intent launch for `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

---

## 4. Verification Plan
1. **JVM Unit Tests (`mobile/android/app/src/test/kotlin/...`)**:
   - `NativeAgentBusTest`: verifies subscription, unsubscription, and multi-thread safe dispatch.
   - `AppFilterTest`: verifies system package blacklist filtering and acceptance of target apps.
2. **Flutter Integration Tests (`mobile/test/...`)**:
   - Verify `AgentBridge` and `AgentController` receive and handle `APP_OPENED` and `SCREEN_STATE_CHANGED` event maps cleanly.
3. **Flutter Test Suite**:
   - Run `flutter test` across all unit/widget tests.
