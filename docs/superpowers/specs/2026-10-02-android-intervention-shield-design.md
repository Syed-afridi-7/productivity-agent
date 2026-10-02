# Android Intervention & Overlay Shield (Phase 3) Architecture Design Spec

- **Status:** Approved (Approach 1: Direct AccessibilityService WindowManager Overlay + Dynamic Blacklist)
- **Author:** Antigravity / Pair Programming
- **Date:** 2026-10-02
- **Monorepo Target:** `mobile/` (`mobile/android/`, `mobile/lib/`)
- **Package:** `com.syedafridi.productivity_agent`

---

## 1. Goal & Requirements

Build real-time intervention capabilities on Android when distracting applications are launched during an active **Deep Focus** session:

1. **App Interception**: Intercept blacklisted applications (social media, entertainment) within milliseconds of launch using `MyAccessibilityService`.
2. **Hybrid Dual-Layer Intervention**:
   - **Layer 1 (Visual Friction Overlay)**: If `Settings.canDrawOverlays` is granted, display a full-screen Cyber Minimalist OLED dark overlay (`TYPE_APPLICATION_OVERLAY`) with a 5-second mandatory breathing countdown, blocked app info, "Return to Focus" button, and "Emergency 60s Pass" button.
   - **Layer 2 (Instant Fallback)**: If overlay permission is not granted, immediately trigger `performGlobalAction(GLOBAL_ACTION_HOME)` to minimize the distracting app.
3. **Dynamic Blacklist Configuration**: Thread-safe Kotlin `BlacklistManager` initialized with default packages (Instagram, TikTok, Twitter/X, YouTube, Reddit, Facebook), configurable dynamically at runtime from Flutter via `MethodChannel`.
4. **Emergency Grace Window**: A 60-second emergency pass can be granted, temporarily suspending blocking for that specific package while recording an infraction telemetry event.
5. **Observability & Bridge**: Every intervention emits an `APP_BLOCKED` event into `NativeAgentBus` and Flutter `EventChannel`, updating the distraction counters in real time.

---

## 2. Architecture & Components

```
+--------------------------------------------------------------------------------+
|                             Flutter Application UI                             |
|  - Dashboard: Metrics & Overlay Permission Warning                             |
|  - AgentBridge / AgentController: MethodChannel + EventChannel                 |
+----------------------------------------+---------------------------------------+
                                         | MethodChannel (updateBlacklist, checkPermission)
                                         v
+--------------------------------------------------------------------------------+
|                         MainActivity / Native Host                             |
|  - Manages Focus State (AgentFocusState)                                       |
|  - Synchronizes Blacklist with BlacklistManager                                |
+----------------------------------------+---------------------------------------+
                                         |
+----------------------------------------v---------------------------------------+
|                        BlacklistManager (Singleton)                           |
|  - Maintains default & customized blacklist Set<String>                        |
|  - Tracks temporary emergency passes (packageName -> expiryTimestamp)          |
+----------------------------------------+---------------------------------------+
                                         | Checked by
                                         v
+--------------------------------------------------------------------------------+
|                   MyAccessibilityService (Elevated Service)                    |
|  - Receives TYPE_WINDOW_STATE_CHANGED                                          |
|  - Queries PackageFilter & BlacklistManager                                    |
|  - If Focus Active & Blacklisted:                                              |
|      * Emits APP_BLOCKED to NativeAgentBus                                     |
|      * If canDrawOverlays: WindowManager.addView(ShieldOverlayView)            |
|      * Else: performGlobalAction(GLOBAL_ACTION_HOME)                           |
+----------------------------------------+---------------------------------------+
                                         |
                                         v
+--------------------------------------------------------------------------------+
|                        ShieldOverlayView (Native View)                         |
|  - Pure OLED dark background (#07080D)                                         |
|  - 5-second breathing countdown timer                                         |
|  - "Return to Focus" -> dismiss overlay & performGlobalAction(HOME)            |
|  - "Emergency 60s Pass" -> grant pass, dismiss overlay, emit infraction       |
+--------------------------------------------------------------------------------+
```

---

## 3. Detailed Component Specifications

### 3.1 `BlacklistManager.kt`
- **Location:** `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/BlacklistManager.kt`
- **Thread Safety:** `AtomicReference` or `@Synchronized` collections.
- **Default Blacklist:**
  - `com.instagram.android`
  - `com.zhiliaoapp.musically` (TikTok)
  - `com.twitter.android`
  - `com.google.android.youtube`
  - `com.facebook.katana`
  - `com.reddit.frontpage`
- **API:**
  - `fun isBlacklisted(packageName: String?): Boolean`
  - `fun setBlacklist(packages: Collection<String>)`
  - `fun getBlacklist(): Set<String>`
  - `fun grantEmergencyPass(packageName: String, durationSeconds: Long = 60)`
  - `fun hasActiveEmergencyPass(packageName: String?): Boolean`
  - `fun clearEmergencyPasses()`

### 3.2 `AgentFocusState.kt`
- **Location:** `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/bus/AgentFocusState.kt`
- **Thread Safety:** `AtomicBoolean`.
- **API:**
  - `var isFocusActive: Boolean`
  - Synchronized directly when `startAgent` (sets `true`) and `stopAgent` (sets `false`) are invoked.

### 3.3 `ShieldOverlayView.kt`
- **Location:** `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/ui/ShieldOverlayView.kt`
- **Design Guidelines:** Cyber Minimalist / OLED Dark:
  - Background: Solid OLED Dark `#07080D`.
  - Accent colors: Emerald `#00F5A0` for focus, Crimson `#FF3366` for shield warnings, Cyan `#00D2FF` for countdown.
- **Components:**
  - Status indicator: `SHIELD ACTIVE // FOCUS RESTRICTED`
  - Target application notice: `[packageName] is locked during Deep Focus`
  - Countdown text: `Mandatory breath: 5s` (decrements every second 5 -> 0)
  - Primary button: `RETURN TO FOCUS` (disabled during 5s countdown; on click triggers `onReturnToFocus()`)
  - Secondary button: `EMERGENCY 60s PASS` (on click triggers `onEmergencyPass()`)
- **Lifecycle & Dismissal:**
  - `fun dismiss()`: Removes view cleanly from `WindowManager`.

### 3.4 `MyAccessibilityService.kt` Updates
- **On `TYPE_WINDOW_STATE_CHANGED`**:
  ```kotlin
  val pkgName = event.packageName?.toString() ?: return
  if (!PackageFilter.isTargetUserApp(pkgName, packageName)) return
  if (!AgentFocusState.isFocusActive) return
  if (!BlacklistManager.isBlacklisted(pkgName)) return
  if (BlacklistManager.hasActiveEmergencyPass(pkgName)) return

  // App is blocked!
  NativeAgentBus.emit(mapOf(
      "type" to "APP_BLOCKED",
      "packageName" to pkgName,
      "timestamp" to System.currentTimeMillis()
  ))

  if (Settings.canDrawOverlays(this)) {
      showOverlay(pkgName)
  } else {
      performGlobalAction(GLOBAL_ACTION_HOME)
  }
  ```
- **Overlay Management**:
  - `showOverlay(targetPackage: String)`:
    - Instantiates `ShieldOverlayView(this)`.
    - Configures `WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY, FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN, TRANSLUCENT)`.
    - Handles "Return to Focus": removes view and invokes `performGlobalAction(GLOBAL_ACTION_HOME)`.
    - Handles "Emergency Pass": grants 60s pass, removes view, emits `APP_BLOCKED` event with `emergency_pass: true`.

### 3.5 `MainActivity.kt` & Platform Channels
- Methods added to `COMMAND_CHANNEL`:
  - `isOverlayPermissionEnabled`: returns `Settings.canDrawOverlays(this)`.
  - `openOverlaySettings`: starts `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))`.
  - `updateBlacklist`: accepts `List<String>`, calls `BlacklistManager.setBlacklist(...)`.
  - `getBlacklist`: returns current blacklist list.
- Synchronization:
  - `startAgent`: sets `AgentFocusState.isFocusActive = true`.
  - `stopAgent`: sets `AgentFocusState.isFocusActive = false`.

### 3.6 Flutter Bridge & Controller Alignment
- In `AgentBridge`:
  - `Future<bool> isOverlayPermissionEnabled()`
  - `Future<void> openOverlaySettings()`
  - `Future<void> updateBlacklist(List<String> packages)`
  - `Future<List<String>> getBlacklist()`
- In `AgentController`:
  - `bool isOverlayGranted` tracking.
  - Automatically queries overlay permission on init and provides request action.

---

## 4. Testing & Verification

1. **Native Kotlin Unit Tests**:
   - `BlacklistManagerTest`:
     - Default packages are blacklisted.
     - Custom blacklist replacement works.
     - Emergency pass suppresses blacklist check until expired.
     - Non-blacklisted apps return false.
   - `AgentFocusStateTest`:
     - Focus state toggling and thread safety.
2. **Flutter Unit & Integration Tests**:
   - `bridge_test.dart`:
     - `isOverlayPermissionEnabled`, `openOverlaySettings`, `updateBlacklist`, `getBlacklist`.
   - `controller_test.dart`:
     - `isOverlayGranted` state management and refresh.
3. **End-to-End Suite**:
   - Run `flutter test`.
   - Run `.\gradlew testDebugUnitTest`.
   - Run `python -m unittest discover -s tests -p "test_*.py"`.
