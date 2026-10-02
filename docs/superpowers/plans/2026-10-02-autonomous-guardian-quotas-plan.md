# Autonomous 24/7 Context-Aware Guardian & Daily Quotas (Phase 5) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform the Productivity Agent on Android (`com.syedafridi.productivity_agent`) into an autonomous, 24/7 always-on on-device guardian featuring automated boot vigilance, cumulative 30m gaming and 20m Reels/Shorts daily quotas with midnight reset, surgical in-app sub-screen discrimination (allowing DMs & educational search, blocking Reels/Shorts), zero-tolerance hard-blocking for streaming/Telegram, and real-time Flutter dashboard quota indicators.

**Architecture:** 
1. `BootReceiver` listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` to auto-launch `AgentForegroundService` 24/7.
2. `AutonomousQuotaManager` persists daily gaming (max 30m) and Reels (max 20m) usage in `SharedPreferences`, auto-resetting at 00:00 midnight daily, and hard-blocks streaming apps + Telegram.
3. `SubScreenClassifier` discriminates between Direct Messages/chats (unlimited) and Reels/Shorts tabs/players in Instagram, YouTube, and Facebook, and queries `CATEGORY_GAME` for installed games.
4. `MyAccessibilityService` continuously inspects window state changes and ticks quota timers, calling `performGlobalAction(GLOBAL_ACTION_HOME)` immediately upon quota exhaustion or hard-block detection.
5. `AgentBridge`, `AgentStatus`, and `DashboardScreen` expose and render 24/7 guardian status, real-time gaming & reels progress bars, and whitelisted utility indicators.

**Tech Stack:** Kotlin, Android AccessibilityService, SharedPreferences, BroadcastReceiver, Flutter, Dart, Platform Channels (`MethodChannel`, `EventChannel`), JUnit 4, Robolectric-compatible unit testing, Flutter Test.

---

### Task 1: Quota Persistence & Boot Vigilance Engine

**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/AutonomousQuotaManager.kt`
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/receivers/BootReceiver.kt`
- Create: `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/AutonomousQuotaManagerTest.kt`
- Modify: `mobile/android/app/src/main/AndroidManifest.xml`

**Step 1: Write the failing test**
Create `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/AutonomousQuotaManagerTest.kt` testing:
- Default quotas: 1800s gaming (30m), 1200s reels (20m).
- Recording ticks increments usage.
- Quotas exhaust when reaching or exceeding limit.
- Hard-blocked package recognition (`org.telegram.messenger`, `com.netflix.mediaclient`, etc.).
- Midnight reset simulation when date string changes.

**Step 2: Run test to verify it fails**
Run: `cd mobile/android && .\gradlew testDebugUnitTest --tests com.syedafridi.productivity_agent.AutonomousQuotaManagerTest --no-daemon`
Expected: FAIL compilation / class not found.

**Step 3: Write minimal implementation**
- Create `AutonomousQuotaManager.kt`:
  - Singleton object with `init(context: Context)` or fallback memory store.
  - `MAX_GAMING_SECONDS = 1800L` (30 mins)
  - `MAX_REELS_SECONDS = 1200L` (20 mins)
  - Date check logic `getTodayDateString()` (`SimpleDateFormat("yyyy-MM-dd", Locale.US)`).
  - `recordGamingTick(seconds: Long = 1)`
  - `recordReelsTick(seconds: Long = 1)`
  - `isGamingExhausted(): Boolean`
  - `isReelsExhausted(): Boolean`
  - `isHardBlocked(packageName: String?): Boolean`
  - `getSnapshot(): QuotaSnapshot`
  - `reset()`
- Create `BootReceiver.kt`:
  - Inherits from `BroadcastReceiver`.
  - Checks action `ACTION_BOOT_COMPLETED` or `ACTION_MY_PACKAGE_REPLACED`.
  - Calls `ContextCompat.startForegroundService(context, Intent(context, AgentForegroundService::class.java).apply { action = AgentForegroundService.ACTION_START })`.
- Update `AndroidManifest.xml`:
  - Add `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`
  - Register `<receiver android:name=".receivers.BootReceiver" android:exported="true">` with `<intent-filter>` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.

**Step 4: Run test to verify it passes**
Run: `cd mobile/android && .\gradlew testDebugUnitTest --tests com.syedafridi.productivity_agent.AutonomousQuotaManagerTest --no-daemon`
Expected: PASS

---

### Task 2: Sub-Screen & Game Classifier Engine

**Files:**
- Create: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/SubScreenClassifier.kt`
- Create: `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/SubScreenClassifierTest.kt`

**Step 1: Write the failing test**
Create `mobile/android/app/src/test/kotlin/com/syedafridi/productivity_agent/SubScreenClassifierTest.kt` testing:
- Instagram Reels detection: `"ReelViewer"`, `"ClipsViewer"`, `"reel"` -> returns `true`.
- Instagram DM bypass: `"DirectInbox"`, `"DirectThread"`, `"chat"` -> returns `isDirectMessage = true` and `isReelsOrShorts = false`.
- YouTube Shorts detection: `"ShortsPlayer"`, `"pivot_shorts"` -> returns `true`.
- YouTube regular video / search: `"WatchWhileActivity"`, `"SearchActivity"` -> returns `false`.
- Facebook Reels detection: `"ReelsFeed"`, `"watch_tab"` -> returns `true`.
- Facebook Messenger bypass: `"MessengerActivity"`, `"thread"` -> returns `isDirectMessage = true` and `isReelsOrShorts = false`.
- Whitelisted apps (Dialer, Settings, ChatGPT, Browser).

**Step 2: Run test to verify it fails**
Run: `cd mobile/android && .\gradlew testDebugUnitTest --tests com.syedafridi.productivity_agent.SubScreenClassifierTest --no-daemon`
Expected: FAIL compilation / class not found.

**Step 3: Write minimal implementation**
- Create `SubScreenClassifier.kt`:
  - `isGame(context: Context, packageName: String?): Boolean`: checks `ApplicationInfo.CATEGORY_GAME` via PackageManager, with exception handling.
  - `isReelsOrShorts(packageName: String, className: String?, contentDescription: String?, text: String?): Boolean`:
    - Checks for Reels / Shorts tokens across package, class, description, and text, strictly ensuring DM/chat tokens are absent.
  - `isDirectMessage(packageName: String, className: String?, contentDescription: String?): Boolean`:
    - Checks for messaging and direct inbox tokens (`direct`, `inbox`, `thread`, `chat`, `composer`, `message`).
  - `isWhitelistedActivity(packageName: String?): Boolean`:
    - Whitelist for system phone, dialer, Google search, ChatGPT.

**Step 4: Run test to verify it passes**
Run: `cd mobile/android && .\gradlew testDebugUnitTest --tests com.syedafridi.productivity_agent.SubScreenClassifierTest --no-daemon`
Expected: PASS

---

### Task 3: Accessibility 24/7 Autonomous Loop Integration

**Files:**
- Modify: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/MyAccessibilityService.kt`
- Modify: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/MainActivity.kt`

**Step 1: Write / verify accessibility service logic**
- In `MyAccessibilityService.kt`:
  - Initialize `AutonomousQuotaManager.init(applicationContext)`.
  - Maintain a 1-second tracking ticker `Handler(Looper.getMainLooper())`:
    - If `currentActiveMode == ActiveMode.GAMING`:
      - `AutonomousQuotaManager.recordGamingTick(1)`
      - If `AutonomousQuotaManager.isGamingExhausted()`:
        - `performGlobalAction(GLOBAL_ACTION_HOME)`
        - Emit `APP_BLOCKED` (`reason: "GAMING_QUOTA_EXHAUSTED"`).
    - If `currentActiveMode == ActiveMode.REELS`:
      - `AutonomousQuotaManager.recordReelsTick(1)`
      - If `AutonomousQuotaManager.isReelsExhausted()`:
        - `performGlobalAction(GLOBAL_ACTION_HOME)`
        - Emit `APP_BLOCKED` (`reason: "REELS_QUOTA_EXHAUSTED"`).
  - In `onAccessibilityEvent(event)`:
    - If `AutonomousQuotaManager.isHardBlocked(pkgName)`:
      - `performGlobalAction(GLOBAL_ACTION_HOME)`
      - Emit `APP_BLOCKED` (`reason: "HARD_BLOCKED"`).
      - Return.
    - If `SubScreenClassifier.isGame(this, pkgName)`:
      - If `AutonomousQuotaManager.isGamingExhausted()`:
        - `performGlobalAction(GLOBAL_ACTION_HOME)`
        - Emit `APP_BLOCKED` (`reason: "GAMING_QUOTA_EXHAUSTED"`).
      - Else set `currentActiveMode = ActiveMode.GAMING`.
      - Return.
    - For Instagram (`com.instagram.android`), YouTube (`com.google.android.youtube`), Facebook (`com.facebook.katana`):
      - If `SubScreenClassifier.isDirectMessage(pkgName, className, contentDesc)`:
        - Set `currentActiveMode = ActiveMode.NONE` (Direct messages permitted anytime!).
      - Else if `SubScreenClassifier.isReelsOrShorts(pkgName, className, contentDesc, text)`:
        - If `AutonomousQuotaManager.isReelsExhausted()`:
          - `performGlobalAction(GLOBAL_ACTION_HOME)`
          - Emit `APP_BLOCKED` (`reason: "REELS_QUOTA_EXHAUSTED"`).
        - Else set `currentActiveMode = ActiveMode.REELS`.
      - Else:
        - Set `currentActiveMode = ActiveMode.NONE` (Regular videos/browsing/search allowed).
    - If user switches to any other app / home:
      - If not game or reels, set `currentActiveMode = ActiveMode.NONE`.
- In `MainActivity.kt`:
  - Connect `getAgentStatus` to return `gamingSecondsUsed`, `reelsSecondsUsed`, `gamingLimitSeconds: 1800`, `reelsLimitSeconds: 1200`, `is24x7Active: true`.
  - Listen for quota updates and emit `TICK` / `QUOTA_UPDATED` events over `eventSink`.

**Step 2: Run all Android unit tests**
Run: `cd mobile/android && .\gradlew testDebugUnitTest --no-daemon`
Expected: ALL PASS.

---

### Task 4: Flutter Models, Controller & 24/7 Dashboard UI

**Files:**
- Modify: `mobile/lib/models/agent_status.dart`
- Modify: `mobile/lib/controllers/agent_controller.dart`
- Modify: `mobile/lib/screens/dashboard_screen.dart`
- Modify: `mobile/test/widget_test.dart` (and add `mobile/test/quota_dashboard_test.dart`)

**Step 1: Write the failing Flutter test**
Create `mobile/test/quota_dashboard_test.dart` verifying:
- `AgentStatus` deserializes `gamingSecondsUsed`, `reelsSecondsUsed`, and `is24x7Active`.
- `DashboardScreen` displays the "24/7 GUARDIAN: ACTIVE" badge.
- `DashboardScreen` displays gaming budget (`XX / 30m`) and reels budget (`XX / 20m`) progress bars.
- `DashboardScreen` displays "DMs & Search: UNLIMITED".

**Step 2: Run Flutter test to verify it fails**
Run: `cd mobile && flutter test test/quota_dashboard_test.dart`
Expected: FAIL (missing fields / UI elements).

**Step 3: Update Flutter implementation**
- In `agent_status.dart`:
  - Add fields `gamingSecondsUsed`, `reelsSecondsUsed`, `gamingLimitSeconds`, `reelsLimitSeconds`, `is24x7Active`.
  - Add getters `gamingMinutesUsed`, `reelsMinutesUsed`, `gamingProgress`, `reelsProgress`.
- In `agent_controller.dart`:
  - Handle `QUOTA_UPDATED` or `TICK` with quota values.
- In `dashboard_screen.dart`:
  - Add Cyberpunk styled 24/7 Guardian Status Card.
  - Add Daily Quotas section with neon green / amber progress bars for Gaming (30m) and Reels/Shorts (20m).
  - Add Whitelisted status badge ("Direct Messages & Study Search: UNLIMITED").

**Step 4: Run Flutter test to verify it passes**
Run: `cd mobile && flutter test`
Expected: ALL PASS.

---

### Task 5: End-to-End Build, Test Suite & Wireless ADB Deployment

**Files:**
- Entire repository

**Step 1: Full test run**
- Run: `cd mobile/android && .\gradlew testDebugUnitTest --no-daemon`
- Run: `cd mobile && flutter test`
- Run: `python -m unittest discover -s tests -p "test_*.py"`

**Step 2: Flutter APK Build**
- Run: `cd mobile && flutter build apk --debug`

**Step 3: Wireless ADB Installation on Samsung Galaxy M15 5G**
- Verify device: `adb devices`
- Install: `adb install -r -d mobile/build/app/outputs/flutter-apk/app-debug.apk`
- Launch: `adb shell am start -n com.syedafridi.productivity_agent/.MainActivity`

**Step 4: Commit & Push**
- `git add mobile/ docs/`
- `git commit -m "feat(mobile): implement autonomous 24/7 guardian with sub-screen detection and daily quotas"`
- `git push origin main`
