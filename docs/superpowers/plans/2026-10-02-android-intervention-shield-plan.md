# Android Intervention & Overlay Shield (Phase 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:dispatching-parallel-agents to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build real-time intervention capabilities on Android: blacklist detection during Deep Focus, sub-second `SYSTEM_ALERT_WINDOW` OLED shield overlay injection, automatic Home navigation fallback, emergency grace passes, and Flutter platform channel management.

**Architecture:** Approach 1 (Direct AccessibilityService WindowManager Overlay + Dynamic Blacklist Singleton).

**Tech Stack:** Kotlin (Android API 28-34, WindowManager), Flutter (Dart 3.x, MethodChannel, ChangeNotifier), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-android-intervention-shield-design.md`

## Global Constraints
- Target package: `com.syedafridi.productivity_agent`
- Zero third-party runtime dependencies (pure Android Framework + Flutter stdlib)
- Android 14+ (API 34) compatible
- Styling adheres strictly to Cyber Minimalist OLED palette (`#07080D` base, `#11131F` card, `#00F5A0` Emerald, `#FF3366` Crimson, `#00D2FF` Cyan)
- Gradle builds and tests run without daemon contention

---

## Tasks Breakdown

### Task 1: BlacklistManager & AgentFocusState (Kotlin Core Singletons & Tests)

**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/BlacklistManager.kt`
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/bus/AgentFocusState.kt`
- Create: `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/BlacklistManagerTest.kt`
- Create: `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/AgentFocusStateTest.kt`

**Interfaces:**
- Consumes: None
- Produces:
  - `BlacklistManager.isBlacklisted(packageName: String?): Boolean`
  - `BlacklistManager.setBlacklist(packages: Collection<String>)`
  - `BlacklistManager.getBlacklist(): Set<String>`
  - `BlacklistManager.grantEmergencyPass(packageName: String, durationSeconds: Long = 60)`
  - `BlacklistManager.hasActiveEmergencyPass(packageName: String?): Boolean`
  - `BlacklistManager.clearEmergencyPasses()`
  - `AgentFocusState.isFocusActive: Boolean`

- [ ] **Step 1: Write Unit Tests**
  - In `BlacklistManagerTest.kt`:
    - `testDefaultBlacklistContainsExpectedSocialApps()`
    - `testCustomBlacklistOverridesDefaults()`
    - `testEmergencyPassSuppressesBlacklistTemporarily()`
    - `testUnblacklistedAppReturnsFalse()`
  - In `AgentFocusStateTest.kt`:
    - `testDefaultStateIsFalse()`
    - `testStateToggle()`

- [ ] **Step 2: Implement Singletons**
  - Implement `BlacklistManager` with default packages:
    - `com.instagram.android`, `com.zhiliaoapp.musically`, `com.twitter.android`, `com.google.android.youtube`, `com.facebook.katana`, `com.reddit.frontpage`.
    - Concurrent map for emergency passes `ConcurrentHashMap<String, Long>`.
  - Implement `AgentFocusState` with `AtomicBoolean(false)`.

- [ ] **Step 3: Run & Verify JVM Tests**
  - Run: `.\gradlew testDebugUnitTest`

---

### Task 2: ShieldOverlayView (Native Cyber Minimalist OLED View)

**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/ui/ShieldOverlayView.kt`

**Interfaces:**
- Consumes: None (pure Android View hierarchy constructed programmatically)
- Produces:
  - `class ShieldOverlayView(context: Context, val targetPackage: String, val onReturnToFocus: () -> Unit, val onEmergencyPass: () -> Unit) : FrameLayout`
  - `fun dismiss()`: halts timer runnable and cleans up view references

- [ ] **Step 1: Implement ShieldOverlayView**
  - OLED Dark background (`#07080D`).
  - Warning tag: `SHIELD ACTIVE // FOCUS RESTRICTED` (Crimson `#FF3366`).
  - Target notice: `"$targetPackage is restricted during Deep Focus"`.
  - Countdown: `5s` mindful breathing countdown using `Handler(Looper.getMainLooper())`.
  - Buttons:
    - Primary "RETURN TO FOCUS" (`#00F5A0` background, disabled until 5s countdown ends).
    - Secondary "EMERGENCY 60s PASS" (invokes `onEmergencyPass()`).

---

### Task 3: MyAccessibilityService Overlay Injection & Fallback Intervention

**Files:**
- Modify: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/MyAccessibilityService.kt`

**Interfaces:**
- Consumes: `BlacklistManager`, `AgentFocusState`, `ShieldOverlayView`, `NativeAgentBus`
- Produces: Real-time window interception with overlay injection or fallback to `GLOBAL_ACTION_HOME`.

- [ ] **Step 1: Implement Overlay Window Management**
  - Check `Settings.canDrawOverlays(this)`:
    - If true: instantiate `ShieldOverlayView`, add to `WindowManager` with `LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY, ...)`.
    - If false: call `performGlobalAction(GLOBAL_ACTION_HOME)`.
  - On "Return to Focus": remove view, call `performGlobalAction(GLOBAL_ACTION_HOME)`.
  - On "Emergency Pass": call `BlacklistManager.grantEmergencyPass(targetPackage)`, remove view.
  - Emit `APP_BLOCKED` to `NativeAgentBus`.

---

### Task 4: MainActivity Platform Channels & Flutter Bridge Alignment

**Files:**
- Modify: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/MainActivity.kt`
- Modify: `mobile/lib/core/bridge.dart`
- Modify: `mobile/lib/controllers/agent_controller.dart`
- Modify: `mobile/test/bridge_test.dart`
- Modify: `mobile/test/controller_test.dart`

**Interfaces:**
- MethodChannel methods:
  - `isOverlayPermissionEnabled` -> `Boolean`
  - `openOverlaySettings` -> `void`
  - `updateBlacklist` (arg: `packages: List<String>`) -> `Boolean`
  - `getBlacklist` -> `List<String>`
- `AgentController.isOverlayGranted: Boolean`

- [ ] **Step 1: Update Native MainActivity.kt**
  - Set `AgentFocusState.isFocusActive = true` in `startAgent`.
  - Set `AgentFocusState.isFocusActive = false` in `stopAgent`.
  - Implement handlers for `isOverlayPermissionEnabled`, `openOverlaySettings`, `updateBlacklist`, `getBlacklist`.

- [ ] **Step 2: Update Flutter Bridge & Controller**
  - Implement Dart methods in `AgentBridge`.
  - Add `isOverlayGranted` getter and `requestOverlayPermission()` in `AgentController`.

- [ ] **Step 3: Update and Run Flutter Unit Tests**
  - In `bridge_test.dart` & `controller_test.dart`.
  - Run: `flutter test`.

---

## Verification & Execution Protocol
- [x] Task 1: `BlacklistManager`, `AgentFocusState`, and unit tests passing.
- [x] Task 2: `ShieldOverlayView` programmatic OLED layout with 5s timer and buttons complete.
- [x] Task 3: `MyAccessibilityService` overlay injection and Home fallback complete.
- [x] Task 4: `MainActivity.kt` and Flutter bridge aligned with unit tests.
- [x] Run `.\gradlew testDebugUnitTest` for all Kotlin unit tests (BUILD SUCCESSFUL).
- [x] Run `flutter test` for all Flutter unit and widget tests (18/18 passed).
- [x] Run `python -m unittest discover -s tests -p "test_*.py"` to ensure core Python engine tests remain green (61/61 passed).
- [x] Stage and commit Phase 3 implementation (`ea46ae7`).
