# Native Android Kotlin Foundation (Phase 2) Implementation Plan

> **Plan File:** `docs/superpowers/plans/2026-10-02-native-kotlin-foundation-plan.md`  
> **Spec File:** `docs/superpowers/specs/2026-10-02-native-kotlin-foundation-design.md`  
> **Target Package:** `com.syedafridi.productivity_agent`

---

## Architecture & Work Breakdown

We break Phase 2 into 5 independent, disjoint tasks suitable for parallel execution:

```
Task 1: NativeAgentBus & Event Distribution (Kotlin Bus + Unit Tests)
Task 2: PackageFilter & MyAccessibilityService (AccessibilityService + XML Config + Unit Tests)
Task 3: AgentForegroundService (Silent Persistent Notification + Screen On/Off BroadcastReceiver)
Task 4: AndroidManifest.xml (API 34 Foreground Service & Accessibility Declarations)
Task 5: MainActivity & Flutter Bridge Alignment (Wiring Services to Start/Stop & Flutter Event Models)
```

---

## Detailed Tasks

### Task 1: NativeAgentBus & Event Distribution
**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/bus/NativeAgentBus.kt`
- Create: `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/NativeAgentBusTest.kt`

**Details:**
- `NativeAgentBus` object with `addListener`, `removeListener`, `emit(event: Map<String, Any>)`.
- Thread-safe listener list with `CopyOnWriteArrayList`.
- Logs `Log.d("ProductivityAgent", ...)` or `println` (for JVM test runner compatibility).
- Unit test in `NativeAgentBusTest.kt` testing subscribe, emit, unsubscribe.

---

### Task 2: PackageFilter & MyAccessibilityService
**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/PackageFilter.kt`
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/MyAccessibilityService.kt`
- Create: `mobile/android/app/src/main/res/xml/accessibility_service_config.xml`
- Create: `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/PackageFilterTest.kt`

**Details:**
- `PackageFilter.isTargetUserApp(packageName: String?, selfPackage: String): Boolean`:
  - Returns `false` for null, empty, self package, system UI (`com.android.systemui`), common launchers, and input methods.
  - Returns `true` for third party apps (e.g. `com.instagram.android`).
- `MyAccessibilityService`:
  - `onAccessibilityEvent`: checks `TYPE_WINDOW_STATE_CHANGED`, filters via `PackageFilter`, emits `APP_OPENED` to `NativeAgentBus`.
- `accessibility_service_config.xml`: standard XML config.
- Unit test in `PackageFilterTest.kt` testing filter behavior.

---

### Task 3: AgentForegroundService
**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/AgentForegroundService.kt`

**Details:**
- `AgentForegroundService` extending `android.app.Service`.
- Constants: `ACTION_START = "com.syedafridi.productivity_agent.action.START"`, `ACTION_STOP = "com.syedafridi.productivity_agent.action.STOP"`.
- Notification channel `productivity_guardian_channel` (Importance LOW).
- Dynamic `BroadcastReceiver` for `ACTION_SCREEN_ON` and `ACTION_SCREEN_OFF` emitting `SCREEN_STATE_CHANGED` to `NativeAgentBus`.
- `onStartCommand` returns `START_STICKY`.

---

### Task 4: AndroidManifest.xml Configuration
**Files:**
- Modify: `mobile/android/app/src/main/AndroidManifest.xml`

**Details:**
- Adds `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
- Declares `AgentForegroundService` with `foregroundServiceType="specialUse"` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- Declares `MyAccessibilityService` with `BIND_ACCESSIBILITY_SERVICE` permission and meta-data referencing `@xml/accessibility_service_config`.

---

### Task 5: MainActivity & Flutter Bridge Alignment
**Files:**
- Modify: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/MainActivity.kt`
- Modify: `mobile/lib/models/agent_status.dart`
- Modify: `mobile/test/agent_status_test.dart`

**Details:**
- In `MainActivity.kt`:
  - On `startAgent`: Starts `AgentForegroundService`.
  - On `stopAgent`: Sends `ACTION_STOP` to `AgentForegroundService`.
  - Attaches `NativeAgentBus` listener to push events to Flutter `eventSink`.
- In `agent_status.dart`:
  - Adds `appOpened`, `screenStateChanged` to `AgentEventType` enum.
  - Adds `appPackage` and `isScreenOn` helper getters on `AgentEvent`.
- In `agent_status_test.dart`:
  - Tests parsing of `APP_OPENED` and `SCREEN_STATE_CHANGED`.

---

## Verification & Results
- [x] Task 1: `NativeAgentBus` & unit tests (`NativeAgentBusTest.kt`) verified passing via `.\gradlew testDebugUnitTest`.
- [x] Task 2: `PackageFilter`, `MyAccessibilityService`, `accessibility_service_config.xml`, and `PackageFilterTest.kt` passing.
- [x] Task 3: `AgentForegroundService` with persistent low-priority notification channel & dynamic screen ON/OFF broadcast receiver.
- [x] Task 4: `AndroidManifest.xml` and `strings.xml` configured for API 34 compliance with `specialUse` foreground service and accessibility service.
- [x] Task 5: `MainActivity.kt` and Flutter bridge aligned; `agent_controller.dart` exhaustively matches all event types; 15/15 Flutter tests passing.
- [x] Python core test suite passing (61/61 tests).
- [x] Committed cleanly to `main` branch (`125c796`).
