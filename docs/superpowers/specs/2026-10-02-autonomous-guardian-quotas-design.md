# Autonomous 24/7 Context-Aware Guardian & Daily Quotas (Phase 5) Architecture Design Spec

- **Status:** Approved
- **Author:** Antigravity / Pair Programming
- **Date:** 2026-10-02
- **Monorepo Target:** `mobile/` (`mobile/android/`, `mobile/lib/`)
- **Package:** `com.syedafridi.productivity_agent`

---

## 1. Goal & Requirements

Transform the Productivity Agent from a manual timer-based session app into an **always-on, 24/7 autonomous on-device AI guardian**:

1. **24/7 Boot-Start Vigilance**:
   - Automatically starts when the device boots (`android.intent.action.BOOT_COMPLETED`) or when the app is updated. Runs persistently with a low-importance foreground service.
   - Zero manual timer settings: the user does not need to start or stop sessions manually.

2. **Automated Daily Quota Engine**:
   - **Gaming Quota**: Cumulative **30 minutes per day** across all games (auto-detected via Android `ApplicationInfo.CATEGORY_GAME` + custom game packages). Exiting to Home when exhausted.
   - **Reels / Shorts Quota**: Cumulative **20 minutes per day** across Instagram Reels, YouTube Shorts, and Facebook Reels. Exiting to Home when exhausted.
   - Quotas automatically reset at **00:00 midnight** daily.

3. **Surgical In-App Sub-Screen Discrimination**:
   - **Instagram**: Direct Messages, chat threads, and notification replies are **unlimited and permitted at any time**. The Reels tab / video viewer is tracked against the 20-minute daily Reels quota.
   - **YouTube**: Standard video search, educational content, and long-form video playback are **unlimited**. YouTube Shorts is tracked against the 20-minute daily Reels quota.
   - **Facebook**: Messenger and direct chats are **unlimited**. Facebook Watch / Reels feed is tracked against the Reels quota.

4. **Strict Zero-Tolerance Hard Block (0 Minutes)**:
   - Telegram and movie/video streaming apps (Netflix, Prime Video, Hotstar, Disney+, Torrent clients) are **strictly hard-blocked 24/7**; opening them immediately exits to the Home screen (`GLOBAL_ACTION_HOME`).

5. **Whitelisted Unlimited Productive Activities**:
   - Phone calls, system dialer, contact management.
   - Asking doubts, search engines, Google, web browsers, ChatGPT/AI search tools.
   - WhatsApp, messaging, and productivity utilities.

---

## 2. Architecture & Components

```
+--------------------------------------------------------------------------------+
|                        Android OS Lifecycle & Events                           |
|  - BOOT_COMPLETED -> BootReceiver -> AgentForegroundService.ACTION_START      |
|  - AccessibilityEvent -> MyAccessibilityService                                |
+----------------------------------------+---------------------------------------+
                                         |
                                         v
+--------------------------------------------------------------------------------+
|                         MyAccessibilityService                                 |
|  - Receives TYPE_WINDOW_STATE_CHANGED & TYPE_WINDOW_CONTENT_CHANGED            |
|  - Consults SubScreenClassifier:                                               |
|      * Is Hard-Blocked (Telegram / Streaming)? -> HOME                         |
|      * Is Game (CATEGORY_GAME)? -> Track gaming time -> HOME if > 30m          |
|      * Is Instagram / YouTube? -> Classify sub-screen:                         |
|          - If DM / Search / Video: ALLOW                                       |
|          - If Reels / Shorts: Track reels time -> HOME if > 20m                |
+----------------------------------------+---------------------------------------+
                                         | Updates & Queries
                                         v
+--------------------------------------------------------------------------------+
|                  AutonomousQuotaManager (SharedPreferences)                    |
|  - Tracks daily usage:                                                         |
|      * gamingSecondsUsed (Limit: 1800s / 30m)                                  |
|      * reelsSecondsUsed (Limit: 1200s / 20m)                                   |
|  - Auto-resets at midnight                                                     |
|  - Broadcasts usage updates to NativeAgentBus                                  |
+----------------------------------------+---------------------------------------+
                                         | Streams Telemetry
                                         v
+--------------------------------------------------------------------------------+
|                         Flutter Companion Dashboard                            |
|  - Shows "24/7 GUARDIAN: ACTIVE" status                                        |
|  - Real-time progress bars:                                                    |
|      * Gaming: [======    ] 15m / 30m                                          |
|      * Reels:  [===       ] 6m / 20m                                           |
|  - Whitelisted indicators: "DMs: UNLIMITED", "Search: UNLIMITED"               |
+--------------------------------------------------------------------------------+
```

---

## 3. Detailed Component Specifications

### 3.1 `BootReceiver.kt`
- **Location:** `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/receivers/BootReceiver.kt`
- **Manifest Filter:** `android.intent.action.BOOT_COMPLETED`, `android.intent.action.MY_PACKAGE_REPLACED`.
- **Action:** Starts `AgentForegroundService` with `ACTION_START`.

### 3.2 `AutonomousQuotaManager.kt`
- **Location:** `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/AutonomousQuotaManager.kt`
- **Limits:**
  - `MAX_GAMING_SECONDS = 30 * 60L` (1800s / 30 mins)
  - `MAX_REELS_SECONDS = 20 * 60L` (1200s / 20 mins)
- **Hard-Blocked Packages:**
  - `org.telegram.messenger`
  - `org.thunderdog.challegram`
  - `com.netflix.mediaclient`
  - `com.amazon.avod.thirdpartyclient`
  - `in.startv.hotstar`
  - `com.disney.disneyplus`
- **Persistence:** Android `SharedPreferences` storing:
  - `quota_date`: current date string (`"YYYY-MM-DD"`). If different from today, resets counters to 0.
  - `gaming_seconds_used`: Long
  - `reels_seconds_used`: Long
- **API:**
  - `fun recordGamingTick(seconds: Long = 1)`
  - `fun recordReelsTick(seconds: Long = 1)`
  - `fun isGamingExhausted(): Boolean`
  - `fun isReelsExhausted(): Boolean`
  - `fun isHardBlocked(packageName: String?): Boolean`
  - `fun getSnapshot(): QuotaSnapshot` (gamingUsed, gamingMax, reelsUsed, reelsMax)

### 3.3 `SubScreenClassifier.kt`
- **Location:** `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/services/SubScreenClassifier.kt`
- **Capabilities:**
  - `fun isGame(context: Context, packageName: String?): Boolean`:
    - Checks `packageManager.getApplicationInfo(packageName, 0).category == ApplicationInfo.CATEGORY_GAME`.
  - `fun isReelsOrShorts(packageName: String, className: String?, contentDescription: String?, text: String?): Boolean`:
    - **Instagram**: Checks if class or node contains `"ReelViewer"`, `"Clips"`, `"reel"`, `"clips_viewer"` AND does NOT contain `"direct"`, `"inbox"`, `"thread"`.
    - **YouTube**: Checks if class or node contains `"Shorts"`, `"shorts_player"`, `"pivot_shorts"`.
    - **Facebook**: Checks if class or node contains `"reel"`, `"watch_tab"` AND does NOT contain `"message"`, `"thread"`.
  - `fun isDirectMessage(packageName: String, className: String?, contentDescription: String?): Boolean`:
    - Returns `true` if node/class contains `"direct"`, `"inbox"`, `"thread"`, `"chat"`, `"composer"`.

### 3.4 `MyAccessibilityService.kt` Updates
- Operates 24/7.
- On `TYPE_WINDOW_STATE_CHANGED` & periodic timer:
  1. If `AutonomousQuotaManager.isHardBlocked(pkgName)`:
     - Immediately calls `performGlobalAction(GLOBAL_ACTION_HOME)`.
     - Emits `APP_BLOCKED` to `NativeAgentBus`.
  2. If `SubScreenClassifier.isGame(this, pkgName)`:
     - Ticks gaming timer.
     - If `AutonomousQuotaManager.isGamingExhausted()`:
       - Immediately calls `performGlobalAction(GLOBAL_ACTION_HOME)`.
       - Emits `APP_BLOCKED` (`reason: "GAMING_QUOTA_EXHAUSTED"`).
  3. If Instagram / YouTube / Facebook:
     - If `SubScreenClassifier.isDirectMessage(...)`: ALLOW without interference.
     - If `SubScreenClassifier.isReelsOrShorts(...)`:
       - Ticks reels timer.
       - If `AutonomousQuotaManager.isReelsExhausted()`:
         - Calls `performGlobalAction(GLOBAL_ACTION_HOME)`.
         - Emits `APP_BLOCKED` (`reason: "REELS_QUOTA_EXHAUSTED"`).

### 3.5 Flutter Bridge & Companion UI Updates
- `AgentEvent` / `AgentStatus`:
  - Contains `gamingSecondsUsed`, `reelsSecondsUsed`, `is24x7Active`.
- `DashboardScreen`:
  - Displays **"24/7 GUARDIAN: ACTIVE"**.
  - Displays Gaming Budget Progress Bar: `XX / 30 mins`.
  - Displays Reels/Shorts Budget Progress Bar: `XX / 20 mins`.
  - Displays "Messaging & Search: UNLIMITED".

---

## 4. Verification Plan
1. Kotlin JVM Unit Tests:
   - `AutonomousQuotaManagerTest`: Quota accumulation, midnight reset, limit exhaustion.
   - `SubScreenClassifierTest`: Accurate classification of Instagram Reels vs DMs, YouTube Shorts vs Videos, games.
2. Flutter Unit Tests:
   - Parsing of 24/7 status, quota progress, and widget rendering.
3. Wireless ADB Verification on Samsung Galaxy M15:
   - Deploy build and verify real-time logcat events on device.
