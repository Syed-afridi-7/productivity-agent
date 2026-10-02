# Specification: Mobile Flutter Companion & Native Android Bridge (Phase 0 & 1)

**Date**: 2026-10-02  
**Status**: Approved for Planning  
**Subsystem**: Mobile Subsystem (Phase 0 & Phase 1) — Flutter UI & Native Platform Channel Bridge  

---

## 1. Executive Summary

This specification defines the architecture, project structure, platform channels communication protocol, and UI implementation for the mobile companion app of the personal AI productivity agent.

The mobile app implements a hybrid architecture:
- **Flutter Framework (`mobile/`)**: Provides an OLED-optimized Cyber Minimalist UI, real-time status monitoring, duration controls, metrics visualization, and settings.
- **Native Android Layer (Kotlin in `mobile/android/`)**: Manages the platform channels interface, accessibility permission checks, and lays the direct foundation for Phase 2's native `AccessibilityService` (for real-time app detection, `GLOBAL_ACTION_LOCK_SCREEN` screen shut-offs, and educational search intent redirects).
- **Zero Third-Party State Frameworks**: Pure standard Flutter reactive state (`ChangeNotifier` / `ListenableBuilder`).
- **Target OS**: Android SDK 28+ (Android 9.0 Pie) up to SDK 34 (Android 14).

---

## 2. Directory Layout (`mobile/`)

```text
D:\productivity-agent/
├── mobile/
│   ├── android/
│   │   ├── app/
│   │   │   ├── build.gradle.kts           # minSdk: 28, targetSdk: 34, compileSdk: 34
│   │   │   └── src/main/
│   │   │       ├── AndroidManifest.xml    # Permissions, Service declarations
│   │   │       └── kotlin/com/syedafridi/productivity_agent/
│   │   │           └── MainActivity.kt    # MethodChannel & EventChannel handlers
│   ├── lib/
│   │   ├── core/
│   │   │   ├── theme.dart                 # Cyber Minimalist OLED Dark theme
│   │   │   └── bridge.dart                # AgentBridge (MethodChannel & EventChannel)
│   │   ├── models/
│   │   │   └── agent_status.dart          # AgentState, timer, and metrics data models
│   │   ├── controllers/
│   │   │   └── agent_controller.dart      # ChangeNotifier coordinating state & bridge
│   │   ├── widgets/
│   │   │   ├── status_ring.dart           # Glowing circular timer & state indicator
│   │   │   ├── metrics_card.dart          # Today's focus, blocks, and nudges cards
│   │   │   └── duration_picker.dart       # Quick duration selector (25m / 45m / 60m)
│   │   ├── screens/
│   │   │   └── dashboard_screen.dart      # Main dashboard with big action toggle
│   │   └── main.dart                      # Flutter app bootstrap
│   ├── test/
│   │   ├── widget_test.dart
│   │   └── controller_test.dart
│   └── pubspec.yaml
```

---

## 3. Platform Channel Specifications

### 3.1 Identifiers
- **Command Channel (`MethodChannel`)**: `com.syedafridi.productivity_agent/commands`
- **Event Channel (`EventChannel`)**: `com.syedafridi.productivity_agent/events`

### 3.2 MethodChannel API (Flutter ➔ Android Native)

#### `startAgent`
- **Payload**: `{"durationMinutes": int}`
- **Response**: `{"success": bool, "state": "DEEP_FOCUS", "durationSec": int}`
- **Behavior**: Transitions state to `DEEP_FOCUS`, initiates active countdown timer, and begins monitoring.

#### `stopAgent`
- **Payload**: `{}`
- **Response**: `{"success": bool, "state": "IDLE"}`
- **Behavior**: Cancels active countdown timer and transitions state to `IDLE`.

#### `getAgentStatus`
- **Payload**: `{}`
- **Response**:
  ```json
  {
    "isRunning": true,
    "state": "DEEP_FOCUS",
    "remainingSeconds": 1495,
    "todayFocusMinutes": 45,
    "distractionsBlocked": 3,
    "nudgesSent": 2
  }
  ```

#### `isAccessibilityEnabled`
- **Payload**: `{}`
- **Response**: `bool` (whether user has enabled our Accessibility Service in Android Settings).

#### `openAccessibilitySettings`
- **Payload**: `{}`
- **Response**: `void` (launches `android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS`).

### 3.3 EventChannel API (Android Native ➔ Flutter)
Emits continuous live events over a reactive stream:
- `{"type": "STATE_CHANGED", "from": "IDLE", "to": "DEEP_FOCUS", "remainingSeconds": 1500}`
- `{"type": "TICK", "remainingSeconds": 1499}`
- `{"type": "APP_BLOCKED", "app": "com.instagram.android", "action": "LOCK_SCREEN"}`
- `{"type": "NUDGE_TRIGGERED", "query": "Learn Rust concurrency patterns"}`

---

## 4. UI Specification: Cyber Minimalist (OLED Dark)

### 4.1 Color System
- **OLED Void (`bgOled`)**: `#07080D`
- **Card Surface (`cardBg`)**: `#11131F`
- **Border Subtle (`borderSubtle`)**: `#1E2235`
- **Electric Emerald (`neonEmerald`)**: `#00F5A0` (Active Focus State)
- **Amber Glow (`amberGlow`)**: `#FFB800` (Break State)
- **Crimson Neon (`crimsonNeon`)**: `#FF3366` (Distraction / Intervention Alert)
- **Electric Cyan (`electricCyan`)**: `#00D2FF` (Productive Nudge Accent)
- **Text Primary**: `#FFFFFF`
- **Text Muted**: `#8E95A5`

### 4.2 Dashboard Screen Components
1. **Header**:
   - Title: `⚡ PRODUCTIVITY AGENT`
   - Active status badge (pulsing green dot when running).
   - Accessibility Warning Banner: Appears conditionally if accessibility permissions are not yet granted, with a 1-tap "Enable Service" button.
2. **Hero Status Ring**:
   - Circular countdown timer with pulsing neon border matching current agent state.
   - Large digital countdown (`24:59` or `READY`).
   - State title (`IDLE` / `DEEP FOCUS` / `SHORT BREAK`).
3. **Duration Picker Pills**:
   - Quick selectable pills: `25 min`, `45 min`, `60 min`.
4. **Primary Action Toggle**:
   - Elevated button with dynamic neon gradient.
   - State = `IDLE`: Displays `START GUARDIAN` in emerald green.
   - State = `DEEP_FOCUS`: Displays `HALT SESSION` in outlined neon crimson.
5. **Daily Metrics Grid**:
   - 3 clean stat cards:
     - **Focus Today**: Cumulative focus minutes.
     - **Blocked Apps**: Total distraction interceptions.
     - **Nudges Sent**: Total productive educational redirects.

---

## 5. Native Android Configuration (`MainActivity.kt`)

In Phase 1:
- Configures `MethodChannel` and `EventChannel` inside `configureFlutterEngine`.
- Maintains an in-memory session timer and mock event dispatcher.
- Exposes permission checking via `Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)`.
- Prepares hooks for Phase 2 when `AccessibilityService` and `ForegroundService` are instantiated.

---

## 6. Verification & Automated Testing Plan

1. **Unit & Controller Tests (`mobile/test/controller_test.dart`)**:
   - Verify `AgentController` state mutations on `startAgent()`, `stopAgent()`, and `selectDuration()`.
   - Verify stream consumption from `AgentBridge`.
2. **Widget Tests (`mobile/test/widget_test.dart`)**:
   - Verify dashboard renders all components: Hero status ring, duration selector, toggle button, and metric cards.
   - Verify clicking `START GUARDIAN` updates UI to `HALT SESSION`.
3. **Android Build Verification**:
   - Verify clean Gradle synchronization and build check (`flutter build apk --debug`).
