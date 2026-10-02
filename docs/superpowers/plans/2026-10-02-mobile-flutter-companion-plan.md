# Mobile Flutter Companion & Native Android Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the mobile companion app in `mobile/` with a Cyber Minimalist OLED dark theme, reactive state management using pure standard Flutter (`ChangeNotifier`), two-way platform channels (`MethodChannel` & `EventChannel`), and native Android Kotlin stubs in `MainActivity.kt` with Android minSdk 28.

**Architecture:** A hybrid mobile architecture where the Flutter UI layer communicates exclusively through `MethodChannel` (`commands`) and `EventChannel` (`events`) via a dedicated `AgentBridge`. State is held in `AgentController` (`ChangeNotifier`) and observed by reactive widgets (`ListenableBuilder`). Native Android (`MainActivity.kt`) implements channel listeners, accessibility permission checks, and an active timer event streamer.

**Tech Stack:** Flutter 3.47+ (Dart 3.13+), Kotlin, Android SDK (minSdk 28, targetSdk 34, compileSdk 34), standard Flutter test framework (`flutter_test`). Zero third-party state management dependencies.

**Spec:** `docs/superpowers/specs/2026-10-02-mobile-flutter-companion-design.md`

## Global Constraints

- **Monorepo Directory:** All mobile code lives under `D:\productivity-agent\mobile/`.
- **Package ID:** `com.syedafridi.productivity_agent`
- **Android Target:** `minSdkVersion 28`, `compileSdkVersion 34`, `targetSdkVersion 34`.
- **Dependencies:** 100% pure Flutter SDK standard libraries. Zero third-party state packages (no Riverpod, no Bloc, no Provider; use standard `ChangeNotifier` & `ListenableBuilder`).
- **Aesthetic:** Cyber Minimalist / OLED Dark (`#07080D` void background, `#11131F` card surface, neon emerald `#00F5A0`, neon crimson `#FF3366`, cyan `#00D2FF`, amber `#FFB800`).
- **Testing:** Strict TDD workflow for every component with `flutter test`.

---

### Task 1: Flutter Project Scaffolding & Android Configuration

**Files:**
- Create: `mobile/` (generated via `flutter create`)
- Modify: `mobile/android/app/build.gradle.kts` (or `build.gradle`)
- Modify: `mobile/android/app/src/main/AndroidManifest.xml`
- Modify: `mobile/pubspec.yaml`
- Test: `mobile/test/smoke_test.dart`

**Interfaces:**
- Consumes: None (base scaffolding)
- Produces: Runnable Flutter Android project targeting SDK 28+ with package `com.syedafridi.productivity_agent`

- [ ] **Step 1: Scaffold Flutter project with target org and package**

Run command from repo root `D:\productivity-agent`:
```bash
flutter create --org com.syedafridi --project-name productivity_agent --platforms android mobile
```

- [ ] **Step 2: Update Android SDK versions in `mobile/android/app/build.gradle.kts` (or `build.gradle`)**

Inspect `mobile/android/app/build.gradle` or `mobile/android/app/build.gradle.kts`. Ensure `minSdk = 28`, `compileSdk = 34`, `targetSdk = 34`.
If Kotlin DSL (`build.gradle.kts`):
```kotlin
android {
    namespace = "com.syedafridi.productivity_agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.syedafridi.productivity_agent"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
```
If Groovy DSL (`build.gradle`):
```groovy
android {
    namespace "com.syedafridi.productivity_agent"
    compileSdkVersion 34

    defaultConfig {
        applicationId "com.syedafridi.productivity_agent"
        minSdkVersion 28
        targetSdkVersion 34
        versionCode 1
        versionName "1.0.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
}
```

- [ ] **Step 3: Update `mobile/android/app/src/main/AndroidManifest.xml` with permissions**

Add the required permissions inside `<manifest>`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:label="Productivity Agent"
        android:name="${applicationName}"
        android:icon="@mipmap/ic_launcher">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:taskAffinity=""
            android:theme="@style/LaunchTheme"
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
            android:hardwareAccelerated="true"
            android:windowSoftInputMode="adjustResize">
            <meta-data
              android:name="io.flutter.embedding.android.NormalTheme"
              android:resource="@style/NormalTheme"
              />
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <meta-data
            android:name="flutterEmbedding"
            android:value="2" />
    </application>
</manifest>
```

- [ ] **Step 4: Create a smoke test `mobile/test/smoke_test.dart`**

```dart
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Sanity smoke test confirms test harness runs', () {
    expect(1 + 1, equals(2));
  });
}
```

- [ ] **Step 5: Run tests to verify project scaffolding passes**

Run from `mobile/`:
```bash
flutter test test/smoke_test.dart
```
Expected: PASS (All tests passed!)

- [ ] **Step 6: Commit**

```bash
git add mobile/
git commit -m "chore(mobile): scaffold Flutter project with minSdk 28 Android configuration"
```

---

### Task 2: Cyber Minimalist OLED Theme & Agent Data Models

**Files:**
- Create: `mobile/lib/core/theme.dart`
- Create: `mobile/lib/models/agent_status.dart`
- Test: `mobile/test/agent_status_test.dart`

**Interfaces:**
- Consumes: Flutter Foundation, standard Dart SDK
- Produces:
  - `CyberTheme`: Color constants (`bgOled`, `cardBg`, `borderSubtle`, `neonEmerald`, `amberGlow`, `crimsonNeon`, `electricCyan`, `textPrimary`, `textMuted`) and `ThemeData darkTheme`.
  - `AgentState`: `enum AgentState { idle, deepFocus, shortBreak }`
  - `AgentEventType`: `enum AgentEventType { stateChanged, tick, appBlocked, nudgeTriggered, unknown }`
  - `AgentEvent`: Immutable dataclass representing events received from native stream.
  - `AgentStatus`: Immutable dataclass representing current status snapshot.

- [ ] **Step 1: Write failing test in `mobile/test/agent_status_test.dart`**

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/models/agent_status.dart';

void main() {
  group('AgentStatus and AgentEvent models', () {
    test('AgentState serialization and parsing', () {
      expect(AgentState.fromString('DEEP_FOCUS'), equals(AgentState.deepFocus));
      expect(AgentState.fromString('SHORT_BREAK'), equals(AgentState.shortBreak));
      expect(AgentState.fromString('IDLE'), equals(AgentState.idle));
      expect(AgentState.fromString('UNKNOWN_XYZ'), equals(AgentState.idle));

      expect(AgentState.deepFocus.toPayloadString(), equals('DEEP_FOCUS'));
      expect(AgentState.idle.toPayloadString(), equals('IDLE'));
    });

    test('AgentStatus defaults and copyWith', () {
      const status = AgentStatus(
        isRunning: false,
        state: AgentState.idle,
        remainingSeconds: 0,
        todayFocusMinutes: 0,
        distractionsBlocked: 0,
        nudgesSent: 0,
      );

      expect(status.formattedTime, equals('00:00'));
      expect(status.stateDisplayName, equals('IDLE'));

      final running = status.copyWith(
        isRunning: true,
        state: AgentState.deepFocus,
        remainingSeconds: 1500,
        todayFocusMinutes: 25,
      );

      expect(running.isRunning, isTrue);
      expect(running.formattedTime, equals('25:00'));
      expect(running.stateDisplayName, equals('DEEP FOCUS'));
      expect(running.todayFocusMinutes, equals(25));
    });

    test('AgentStatus.fromMap parses native JSON payload', () {
      final map = {
        'isRunning': true,
        'state': 'DEEP_FOCUS',
        'remainingSeconds': 1495,
        'todayFocusMinutes': 45,
        'distractionsBlocked': 3,
        'nudgesSent': 2,
      };

      final status = AgentStatus.fromMap(map);
      expect(status.isRunning, isTrue);
      expect(status.state, equals(AgentState.deepFocus));
      expect(status.remainingSeconds, equals(1495));
      expect(status.formattedTime, equals('24:55'));
      expect(status.todayFocusMinutes, equals(45));
      expect(status.distractionsBlocked, equals(3));
      expect(status.nudgesSent, equals(2));
    });

    test('AgentEvent.fromMap parses stream events', () {
      final tickEvent = AgentEvent.fromMap({
        'type': 'TICK',
        'remainingSeconds': 1499,
      });
      expect(tickEvent.type, equals(AgentEventType.tick));
      expect(tickEvent.remainingSeconds, equals(1499));

      final stateEvent = AgentEvent.fromMap({
        'type': 'STATE_CHANGED',
        'from': 'IDLE',
        'to': 'DEEP_FOCUS',
        'remainingSeconds': 1500,
      });
      expect(stateEvent.type, equals(AgentEventType.stateChanged));
      expect(stateEvent.toState, equals(AgentState.deepFocus));

      final blockEvent = AgentEvent.fromMap({
        'type': 'APP_BLOCKED',
        'app': 'com.instagram.android',
        'action': 'LOCK_SCREEN',
      });
      expect(blockEvent.type, equals(AgentEventType.appBlocked));
      expect(blockEvent.payload['app'], equals('com.instagram.android'));
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `mobile/`:
```bash
flutter test test/agent_status_test.dart
```
Expected: FAIL (Target of URI doesn't exist: `package:productivity_agent/models/agent_status.dart`)

- [ ] **Step 3: Implement `mobile/lib/core/theme.dart`**

```dart
import 'package:flutter/material.dart';

class CyberTheme {
  // OLED & Surface Colors
  static const Color bgOled = Color(0xFF07080D);
  static const Color cardBg = Color(0xFF11131F);
  static const Color borderSubtle = Color(0xFF1E2235);
  static const Color borderActive = Color(0xFF2E344D);

  // Neon Accents
  static const Color neonEmerald = Color(0xFF00F5A0);
  static const Color amberGlow = Color(0xFFFFB800);
  static const Color crimsonNeon = Color(0xFFFF3366);
  static const Color electricCyan = Color(0xFF00D2FF);

  // Text Colors
  static const Color textPrimary = Color(0xFFFFFFFF);
  static const Color textMuted = Color(0xFF8E95A5);

  static ThemeData get darkTheme {
    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: bgOled,
      primaryColor: neonEmerald,
      cardColor: cardBg,
      fontFamily: 'monospace',
      appBarTheme: const AppBarTheme(
        backgroundColor: bgOled,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: textPrimary,
          fontSize: 16,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.5,
          fontFamily: 'monospace',
        ),
      ),
      colorScheme: const ColorScheme.dark(
        background: bgOled,
        surface: cardBg,
        primary: neonEmerald,
        secondary: electricCyan,
        error: crimsonNeon,
        onBackground: textPrimary,
        onSurface: textPrimary,
      ),
    );
  }
}
```

- [ ] **Step 4: Implement `mobile/lib/models/agent_status.dart`**

```dart
enum AgentState {
  idle,
  deepFocus,
  shortBreak;

  static AgentState fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'DEEP_FOCUS':
        return AgentState.deepFocus;
      case 'SHORT_BREAK':
        return AgentState.shortBreak;
      case 'IDLE':
      default:
        return AgentState.idle;
    }
  }

  String toPayloadString() {
    switch (this) {
      case AgentState.deepFocus:
        return 'DEEP_FOCUS';
      case AgentState.shortBreak:
        return 'SHORT_BREAK';
      case AgentState.idle:
        return 'IDLE';
    }
  }
}

enum AgentEventType {
  stateChanged,
  tick,
  appBlocked,
  nudgeTriggered,
  unknown;

  static AgentEventType fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'STATE_CHANGED':
        return AgentEventType.stateChanged;
      case 'TICK':
        return AgentEventType.tick;
      case 'APP_BLOCKED':
        return AgentEventType.appBlocked;
      case 'NUDGE_TRIGGERED':
        return AgentEventType.nudgeTriggered;
      default:
        return AgentEventType.unknown;
    }
  }
}

class AgentEvent {
  final AgentEventType type;
  final AgentState? fromState;
  final AgentState? toState;
  final int? remainingSeconds;
  final Map<String, dynamic> payload;

  const AgentEvent({
    required this.type,
    this.fromState,
    this.toState,
    this.remainingSeconds,
    this.payload = const {},
  });

  factory AgentEvent.fromMap(Map<dynamic, dynamic> map) {
    final typeStr = map['type'] as String?;
    final type = AgentEventType.fromString(typeStr);

    AgentState? from;
    AgentState? to;
    if (map.containsKey('from')) {
      from = AgentState.fromString(map['from'] as String?);
    }
    if (map.containsKey('to')) {
      to = AgentState.fromString(map['to'] as String?);
    }

    final remaining = (map['remainingSeconds'] as num?)?.toInt();

    return AgentEvent(
      type: type,
      fromState: from,
      toState: to,
      remainingSeconds: remaining,
      payload: Map<String, dynamic>.from(map),
    );
  }
}

class AgentStatus {
  final bool isRunning;
  final AgentState state;
  final int remainingSeconds;
  final int todayFocusMinutes;
  final int distractionsBlocked;
  final int nudgesSent;

  const AgentStatus({
    this.isRunning = false,
    this.state = AgentState.idle,
    this.remainingSeconds = 0,
    this.todayFocusMinutes = 0,
    this.distractionsBlocked = 0,
    this.nudgesSent = 0,
  });

  String get formattedTime {
    final minutes = (remainingSeconds / 60).floor().toString().padLeft(2, '0');
    final seconds = (remainingSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  String get stateDisplayName {
    switch (state) {
      case AgentState.deepFocus:
        return 'DEEP FOCUS';
      case AgentState.shortBreak:
        return 'SHORT BREAK';
      case AgentState.idle:
        return 'IDLE';
    }
  }

  AgentStatus copyWith({
    bool? isRunning,
    AgentState? state,
    int? remainingSeconds,
    int? todayFocusMinutes,
    int? distractionsBlocked,
    int? nudgesSent,
  }) {
    return AgentStatus(
      isRunning: isRunning ?? this.isRunning,
      state: state ?? this.state,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      todayFocusMinutes: todayFocusMinutes ?? this.todayFocusMinutes,
      distractionsBlocked: distractionsBlocked ?? this.distractionsBlocked,
      nudgesSent: nudgesSent ?? this.nudgesSent,
    );
  }

  factory AgentStatus.fromMap(Map<dynamic, dynamic> map) {
    return AgentStatus(
      isRunning: map['isRunning'] == true,
      state: AgentState.fromString(map['state'] as String?),
      remainingSeconds: (map['remainingSeconds'] as num?)?.toInt() ?? 0,
      todayFocusMinutes: (map['todayFocusMinutes'] as num?)?.toInt() ?? 0,
      distractionsBlocked: (map['distractionsBlocked'] as num?)?.toInt() ?? 0,
      nudgesSent: (map['nudgesSent'] as num?)?.toInt() ?? 0,
    );
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run from `mobile/`:
```bash
flutter test test/agent_status_test.dart
```
Expected: PASS (All tests passed!)

- [ ] **Step 6: Commit**

```bash
git add mobile/lib/core/theme.dart mobile/lib/models/agent_status.dart mobile/test/agent_status_test.dart
git commit -m "feat(mobile): add OLED Cyber Minimalist theme and agent data models"
```

---

### Task 3: Platform Channels AgentBridge (Native ↔ Flutter)

**Files:**
- Create: `mobile/lib/core/bridge.dart`
- Test: `mobile/test/bridge_test.dart`

**Interfaces:**
- Consumes: `AgentStatus`, `AgentEvent`, `AgentState`, Flutter Services (`MethodChannel`, `EventChannel`)
- Produces: `AgentBridge` interface with methods:
  - `Future<bool> startAgent({required int durationMinutes})`
  - `Future<bool> stopAgent()`
  - `Future<AgentStatus> getAgentStatus()`
  - `Future<bool> isAccessibilityEnabled()`
  - `Future<void> openAccessibilitySettings()`
  - `Stream<AgentEvent> get eventStream`

- [ ] **Step 1: Write failing test in `mobile/test/bridge_test.dart`**

```dart
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/core/bridge.dart';
import 'package:productivity_agent/models/agent_status.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AgentBridge bridge;
  final List<MethodCall> methodCalls = [];

  setUp(() {
    methodCalls.clear();
    bridge = AgentBridge();

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('com.syedafridi.productivity_agent/commands'),
      (MethodCall methodCall) async {
        methodCalls.add(methodCall);
        switch (methodCall.method) {
          case 'startAgent':
            return {'success': true, 'state': 'DEEP_FOCUS', 'durationSec': 1500};
          case 'stopAgent':
            return {'success': true, 'state': 'IDLE'};
          case 'getAgentStatus':
            return {
              'isRunning': true,
              'state': 'DEEP_FOCUS',
              'remainingSeconds': 1400,
              'todayFocusMinutes': 10,
              'distractionsBlocked': 1,
              'nudgesSent': 0,
            };
          case 'isAccessibilityEnabled':
            return true;
          case 'openAccessibilitySettings':
            return null;
          default:
            return null;
        }
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('com.syedafridi.productivity_agent/commands'),
      null,
    );
  });

  test('AgentBridge startAgent sends correct method and argument', () async {
    final success = await bridge.startAgent(durationMinutes: 25);
    expect(success, isTrue);
    expect(methodCalls.length, equals(1));
    expect(methodCalls.first.method, equals('startAgent'));
    expect(methodCalls.first.arguments, equals({'durationMinutes': 25}));
  });

  test('AgentBridge stopAgent sends correct method', () async {
    final success = await bridge.stopAgent();
    expect(success, isTrue);
    expect(methodCalls.first.method, equals('stopAgent'));
  });

  test('AgentBridge getAgentStatus parses returned data', () async {
    final status = await bridge.getAgentStatus();
    expect(status.isRunning, isTrue);
    expect(status.state, equals(AgentState.deepFocus));
    expect(status.remainingSeconds, equals(1400));
  });

  test('AgentBridge accessibility checks and open settings', () async {
    final isEnabled = await bridge.isAccessibilityEnabled();
    expect(isEnabled, isTrue);

    await bridge.openAccessibilitySettings();
    expect(methodCalls.last.method, equals('openAccessibilitySettings'));
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `mobile/`:
```bash
flutter test test/bridge_test.dart
```
Expected: FAIL (Target of URI doesn't exist: `package:productivity_agent/core/bridge.dart`)

- [ ] **Step 3: Implement `mobile/lib/core/bridge.dart`**

```dart
import 'dart:async';
import 'package:flutter/services.dart';
import '../models/agent_status.dart';

class AgentBridge {
  static const MethodChannel _cmdChannel =
      MethodChannel('com.syedafridi.productivity_agent/commands');
  static const EventChannel _eventChannel =
      EventChannel('com.syedafridi.productivity_agent/events');

  final MethodChannel commandChannel;
  final EventChannel eventChannel;

  AgentBridge({
    MethodChannel? commandChannel,
    EventChannel? eventChannel,
  })  : commandChannel = commandChannel ?? _cmdChannel,
        eventChannel = eventChannel ?? _eventChannel;

  Future<bool> startAgent({required int durationMinutes}) async {
    try {
      final dynamic result = await commandChannel.invokeMethod(
        'startAgent',
        {'durationMinutes': durationMinutes},
      );
      if (result is Map && result['success'] == true) {
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> stopAgent() async {
    try {
      final dynamic result = await commandChannel.invokeMethod('stopAgent');
      if (result is Map && result['success'] == true) {
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<AgentStatus> getAgentStatus() async {
    try {
      final dynamic result = await commandChannel.invokeMethod('getAgentStatus');
      if (result is Map) {
        return AgentStatus.fromMap(result);
      }
      return const AgentStatus();
    } catch (_) {
      return const AgentStatus();
    }
  }

  Future<bool> isAccessibilityEnabled() async {
    try {
      final dynamic result =
          await commandChannel.invokeMethod('isAccessibilityEnabled');
      return result == true;
    } catch (_) {
      return false;
    }
  }

  Future<void> openAccessibilitySettings() async {
    try {
      await commandChannel.invokeMethod('openAccessibilitySettings');
    } catch (_) {
      // Ignored if platform rejects intent
    }
  }

  Stream<AgentEvent> get eventStream {
    return eventChannel.receiveBroadcastStream().map((dynamic event) {
      if (event is Map) {
        return AgentEvent.fromMap(event);
      }
      return const AgentEvent(type: AgentEventType.unknown);
    });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run from `mobile/`:
```bash
flutter test test/bridge_test.dart
```
Expected: PASS (All tests passed!)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/core/bridge.dart mobile/test/bridge_test.dart
git commit -m "feat(mobile): implement AgentBridge with MethodChannel and EventChannel communication"
```

---

### Task 4: AgentController (Reactive State Management)

**Files:**
- Create: `mobile/lib/controllers/agent_controller.dart`
- Test: `mobile/test/controller_test.dart`

**Interfaces:**
- Consumes: `AgentBridge`, `AgentStatus`, `AgentEvent`, `AgentState`
- Produces: `AgentController extends ChangeNotifier`
  - Getters: `AgentStatus status`, `int selectedDurationMinutes`, `bool isAccessibilityGranted`, `bool isLoading`
  - Actions:
    - `Future<void> init()`
    - `void selectDuration(int minutes)`
    - `Future<void> toggleAgent()`
    - `Future<void> refreshAccessibility()`
    - `Future<void> requestAccessibility()`

- [ ] **Step 1: Write failing test in `mobile/test/controller_test.dart`**

```dart
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/controllers/agent_controller.dart';
import 'package:productivity_agent/core/bridge.dart';
import 'package:productivity_agent/models/agent_status.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late StreamController<dynamic> streamController;
  late AgentBridge bridge;
  late AgentController controller;
  bool isAgentRunning = false;

  setUp(() {
    streamController = StreamController<dynamic>.broadcast();

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('com.syedafridi.productivity_agent/commands'),
      (MethodCall methodCall) async {
        switch (methodCall.method) {
          case 'startAgent':
            isAgentRunning = true;
            return {'success': true, 'state': 'DEEP_FOCUS', 'durationSec': 1500};
          case 'stopAgent':
            isAgentRunning = false;
            return {'success': true, 'state': 'IDLE'};
          case 'getAgentStatus':
            return {
              'isRunning': isAgentRunning,
              'state': isAgentRunning ? 'DEEP_FOCUS' : 'IDLE',
              'remainingSeconds': isAgentRunning ? 1500 : 0,
              'todayFocusMinutes': 30,
              'distractionsBlocked': 2,
              'nudgesSent': 1,
            };
          case 'isAccessibilityEnabled':
            return true;
          default:
            return null;
        }
      },
    );

    bridge = AgentBridge();
    controller = AgentController(
      bridge: bridge,
      eventStreamOverride: streamController.stream.map((e) => AgentEvent.fromMap(e as Map)),
    );
  });

  tearDown(() {
    controller.dispose();
    streamController.close();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('com.syedafridi.productivity_agent/commands'),
      null,
    );
  });

  test('AgentController initial state and duration selection', () {
    expect(controller.status.isRunning, isFalse);
    expect(controller.selectedDurationMinutes, equals(25));

    controller.selectDuration(45);
    expect(controller.selectedDurationMinutes, equals(45));
  });

  test('AgentController init syncs initial status and accessibility', () async {
    await controller.init();
    expect(controller.isAccessibilityGranted, isTrue);
    expect(controller.status.todayFocusMinutes, equals(30));
  });

  test('AgentController toggleAgent starts and stops session', () async {
    await controller.init();

    await controller.toggleAgent();
    expect(controller.status.isRunning, isTrue);
    expect(controller.status.state, equals(AgentState.deepFocus));

    await controller.toggleAgent();
    expect(controller.status.isRunning, isFalse);
    expect(controller.status.state, equals(AgentState.idle));
  });

  test('AgentController handles incoming stream events (TICK, STATE_CHANGED)', () async {
    await controller.init();

    // Emit tick event over stream
    streamController.add({
      'type': 'TICK',
      'remainingSeconds': 1450,
    });
    await pumpEventQueue();

    expect(controller.status.remainingSeconds, equals(1450));

    // Emit app blocked event over stream
    streamController.add({
      'type': 'APP_BLOCKED',
      'app': 'com.instagram.android',
      'action': 'LOCK_SCREEN',
    });
    await pumpEventQueue();

    expect(controller.status.distractionsBlocked, equals(3));
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `mobile/`:
```bash
flutter test test/controller_test.dart
```
Expected: FAIL (Target of URI doesn't exist: `package:productivity_agent/controllers/agent_controller.dart`)

- [ ] **Step 3: Implement `mobile/lib/controllers/agent_controller.dart`**

```dart
import 'dart:async';
import 'package:flutter/foundation.dart';
import '../core/bridge.dart';
import '../models/agent_status.dart';

class AgentController extends ChangeNotifier {
  final AgentBridge _bridge;
  final Stream<AgentEvent>? _eventStreamOverride;

  AgentStatus _status = const AgentStatus();
  int _selectedDurationMinutes = 25;
  bool _isAccessibilityGranted = true;
  bool _isLoading = false;

  StreamSubscription<AgentEvent>? _streamSubscription;

  AgentController({
    AgentBridge? bridge,
    Stream<AgentEvent>? eventStreamOverride,
  })  : _bridge = bridge ?? AgentBridge(),
        _eventStreamOverride = eventStreamOverride;

  AgentStatus get status => _status;
  int get selectedDurationMinutes => _selectedDurationMinutes;
  bool get isAccessibilityGranted => _isAccessibilityGranted;
  bool get isLoading => _isLoading;

  Future<void> init() async {
    _isLoading = true;
    notifyListeners();

    try {
      _isAccessibilityGranted = await _bridge.isAccessibilityEnabled();
      _status = await _bridge.getAgentStatus();
    } finally {
      _isLoading = false;
      notifyListeners();
    }

    _subscribeToEvents();
  }

  void _subscribeToEvents() {
    _streamSubscription?.cancel();
    final stream = _eventStreamOverride ?? _bridge.eventStream;
    _streamSubscription = stream.listen(_handleNativeEvent);
  }

  void _handleNativeEvent(AgentEvent event) {
    switch (event.type) {
      case AgentEventType.tick:
        if (event.remainingSeconds != null) {
          _status = _status.copyWith(remainingSeconds: event.remainingSeconds);
          notifyListeners();
        }
        break;

      case AgentEventType.stateChanged:
        if (event.toState != null) {
          final isRunning = event.toState != AgentState.idle;
          _status = _status.copyWith(
            state: event.toState,
            isRunning: isRunning,
            remainingSeconds: event.remainingSeconds ?? _status.remainingSeconds,
          );
          notifyListeners();
        }
        break;

      case AgentEventType.appBlocked:
        _status = _status.copyWith(
          distractionsBlocked: _status.distractionsBlocked + 1,
        );
        notifyListeners();
        break;

      case AgentEventType.nudgeTriggered:
        _status = _status.copyWith(
          nudgesSent: _status.nudgesSent + 1,
        );
        notifyListeners();
        break;

      case AgentEventType.unknown:
        break;
    }
  }

  void selectDuration(int minutes) {
    if (_status.isRunning) return;
    _selectedDurationMinutes = minutes;
    notifyListeners();
  }

  Future<void> toggleAgent() async {
    _isLoading = true;
    notifyListeners();

    try {
      if (_status.isRunning) {
        final success = await _bridge.stopAgent();
        if (success) {
          _status = _status.copyWith(
            isRunning: false,
            state: AgentState.idle,
            remainingSeconds: 0,
          );
        }
      } else {
        final success = await _bridge.startAgent(
          durationMinutes: _selectedDurationMinutes,
        );
        if (success) {
          _status = _status.copyWith(
            isRunning: true,
            state: AgentState.deepFocus,
            remainingSeconds: _selectedDurationMinutes * 60,
          );
        }
      }
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshAccessibility() async {
    _isAccessibilityGranted = await _bridge.isAccessibilityEnabled();
    notifyListeners();
  }

  Future<void> requestAccessibility() async {
    await _bridge.openAccessibilitySettings();
  }

  @override
  void dispose() {
    _streamSubscription?.cancel();
    super.dispose();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run from `mobile/`:
```bash
flutter test test/controller_test.dart
```
Expected: PASS (All tests passed!)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/controllers/agent_controller.dart mobile/test/controller_test.dart
git commit -m "feat(mobile): add AgentController reactive state management"
```

---

### Task 5: Cyber Minimalist Dashboard UI Widgets & Screens

**Files:**
- Create: `mobile/lib/widgets/status_ring.dart`
- Create: `mobile/lib/widgets/duration_picker.dart`
- Create: `mobile/lib/widgets/metrics_card.dart`
- Create: `mobile/lib/screens/dashboard_screen.dart`
- Modify: `mobile/lib/main.dart`
- Test: `mobile/test/widget_test.dart`

**Interfaces:**
- Consumes: `CyberTheme`, `AgentController`, `AgentStatus`, `AgentState`
- Produces: Complete Cyber Minimalist OLED dashboard UI rendered with zero third-party dependencies

- [ ] **Step 1: Write failing widget test in `mobile/test/widget_test.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/controllers/agent_controller.dart';
import 'package:productivity_agent/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('com.syedafridi.productivity_agent/commands'),
      (MethodCall methodCall) async {
        switch (methodCall.method) {
          case 'getAgentStatus':
            return {
              'isRunning': false,
              'state': 'IDLE',
              'remainingSeconds': 0,
              'todayFocusMinutes': 45,
              'distractionsBlocked': 3,
              'nudgesSent': 2,
            };
          case 'isAccessibilityEnabled':
            return true;
          case 'startAgent':
            return {'success': true, 'state': 'DEEP_FOCUS', 'durationSec': 1500};
          case 'stopAgent':
            return {'success': true, 'state': 'IDLE'};
          default:
            return null;
        }
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('com.syedafridi.productivity_agent/commands'),
      null,
    );
  });

  testWidgets('Dashboard displays header, status ring, duration picker, and metrics', (tester) async {
    final controller = AgentController();
    await tester.pumpWidget(ProductivityApp(controller: controller));
    await tester.pumpAndSettle();

    // Verify Header
    expect(find.text('⚡ PRODUCTIVITY AGENT'), findsOneWidget);

    // Verify Duration Pills
    expect(find.text('25m'), findsOneWidget);
    expect(find.text('45m'), findsOneWidget);
    expect(find.text('60m'), findsOneWidget);

    // Verify Action Button in IDLE state
    expect(find.text('START GUARDIAN'), findsOneWidget);

    // Verify Metric Cards
    expect(find.text('Focus Today'), findsOneWidget);
    expect(find.text('45m'), findsOneWidget);
    expect(find.text('Blocked'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    expect(find.text('Nudges'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);

    // Tap START GUARDIAN
    await tester.tap(find.text('START GUARDIAN'));
    await tester.pumpAndSettle();

    // Verify state transitioned to HALT SESSION
    expect(find.text('HALT SESSION'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `mobile/`:
```bash
flutter test test/widget_test.dart
```
Expected: FAIL (ProductivityApp or parameters not defined)

- [ ] **Step 3: Implement `mobile/lib/widgets/status_ring.dart`**

```dart
import 'package:flutter/material.dart';
import '../core/theme.dart';
import '../models/agent_status.dart';

class StatusRing extends StatelessWidget {
  final AgentStatus status;

  const StatusRing({
    Key? key,
    required this.status,
  }) : super(key: key);

  Color get _accentColor {
    switch (status.state) {
      case AgentState.deepFocus:
        return CyberTheme.neonEmerald;
      case AgentState.shortBreak:
        return CyberTheme.amberGlow;
      case AgentState.idle:
        return CyberTheme.borderActive;
    }
  }

  @override
  Widget build(BuildContext context) {
    final displayText = status.isRunning ? status.formattedTime : 'READY';

    return Center(
      child: Container(
        width: 220,
        height: 220,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: CyberTheme.cardBg,
          border: Border.all(
            color: _accentColor.withOpacity(0.8),
            width: 3.0,
          ),
          boxShadow: [
            if (status.isRunning)
              BoxShadow(
                color: _accentColor.withOpacity(0.35),
                blurRadius: 28,
                spreadRadius: 2,
              ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              displayText,
              style: TextStyle(
                color: CyberTheme.textPrimary,
                fontSize: status.isRunning ? 40 : 34,
                fontWeight: FontWeight.w900,
                letterSpacing: 2.0,
              ),
            ),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: _accentColor.withOpacity(0.15),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                status.stateDisplayName,
                style: TextStyle(
                  color: _accentColor,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 4: Implement `mobile/lib/widgets/duration_picker.dart`**

```dart
import 'package:flutter/material.dart';
import '../core/theme.dart';

class DurationPicker extends StatelessWidget {
  final int selectedMinutes;
  final bool isEnabled;
  final ValueChanged<int> onDurationSelected;

  const DurationPicker({
    Key? key,
    required this.selectedMinutes,
    required this.isEnabled,
    required this.onDurationSelected,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final durations = [25, 45, 60];

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: durations.map((mins) {
        final isSelected = selectedMinutes == mins;
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: InkWell(
            onTap: isEnabled ? () => onDurationSelected(mins) : null,
            borderRadius: BorderRadius.circular(8),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
              decoration: BoxDecoration(
                color: isSelected ? CyberTheme.neonEmerald.withOpacity(0.15) : CyberTheme.cardBg,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isSelected ? CyberTheme.neonEmerald : CyberTheme.borderSubtle,
                  width: 1.5,
                ),
              ),
              child: Text(
                '${mins}m',
                style: TextStyle(
                  color: isSelected ? CyberTheme.neonEmerald : CyberTheme.textMuted,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  fontSize: 14,
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}
```

- [ ] **Step 5: Implement `mobile/lib/widgets/metrics_card.dart`**

```dart
import 'package:flutter/material.dart';
import '../core/theme.dart';

class MetricsCard extends StatelessWidget {
  final String label;
  final String value;
  final Color accentColor;
  final IconData icon;

  const MetricsCard({
    Key? key,
    required this.label,
    required this.value,
    required this.accentColor,
    required this.icon,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
        decoration: BoxDecoration(
          color: CyberTheme.cardBg,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: CyberTheme.borderSubtle),
        ),
        child: Column(
          children: [
            Icon(icon, size: 20, color: accentColor),
            const SizedBox(height: 6),
            Text(
              value,
              style: TextStyle(
                color: CyberTheme.textPrimary,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(
                color: CyberTheme.textMuted,
                fontSize: 11,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 6: Implement `mobile/lib/screens/dashboard_screen.dart`**

```dart
import 'package:flutter/material.dart';
import '../controllers/agent_controller.dart';
import '../core/theme.dart';
import '../widgets/duration_picker.dart';
import '../widgets/metrics_card.dart';
import '../widgets/status_ring.dart';

class DashboardScreen extends StatelessWidget {
  final AgentController controller;

  const DashboardScreen({
    Key? key,
    required this.controller,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final status = controller.status;
        final isRunning = status.isRunning;

        return Scaffold(
          appBar: AppBar(
            title: const Text('⚡ PRODUCTIVITY AGENT'),
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 16),
                child: Center(
                  child: Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isRunning ? CyberTheme.neonEmerald : CyberTheme.borderActive,
                      boxShadow: [
                        if (isRunning)
                          const BoxShadow(
                            color: CyberTheme.neonEmerald,
                            blurRadius: 6,
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
          body: SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Accessibility Warning Banner
                  if (!controller.isAccessibilityGranted)
                    Container(
                      margin: const EdgeInsets.only(bottom: 16),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: CyberTheme.amberGlow.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: CyberTheme.amberGlow),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.warning_amber_rounded, color: CyberTheme.amberGlow),
                          const SizedBox(width: 10),
                          const Expanded(
                            child: Text(
                              'Accessibility permission needed to enforce focus mode.',
                              style: TextStyle(color: CyberTheme.textPrimary, fontSize: 12),
                            ),
                          ),
                          TextButton(
                            onPressed: () => controller.requestAccessibility(),
                            child: const Text(
                              'ENABLE',
                              style: TextStyle(color: CyberTheme.amberGlow, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ],
                      ),
                    ),

                  const SizedBox(height: 16),

                  // Hero Status Ring
                  StatusRing(status: status),

                  const SizedBox(height: 32),

                  // Duration Selector
                  DurationPicker(
                    selectedMinutes: controller.selectedDurationMinutes,
                    isEnabled: !isRunning,
                    onDurationSelected: (mins) => controller.selectDuration(mins),
                  ),

                  const SizedBox(height: 28),

                  // Primary Action Button
                  SizedBox(
                    height: 54,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isRunning
                            ? CyberTheme.bgOled
                            : CyberTheme.neonEmerald,
                        foregroundColor: isRunning
                            ? CyberTheme.crimsonNeon
                            : CyberTheme.bgOled,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                          side: BorderSide(
                            color: isRunning
                                ? CyberTheme.crimsonNeon
                                : CyberTheme.neonEmerald,
                            width: 2.0,
                          ),
                        ),
                        elevation: isRunning ? 0 : 8,
                      ),
                      onPressed: controller.isLoading ? null : () => controller.toggleAgent(),
                      child: controller.isLoading
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(
                              isRunning ? 'HALT SESSION' : 'START GUARDIAN',
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1.5,
                              ),
                            ),
                    ),
                  ),

                  const SizedBox(height: 36),

                  // Daily Metrics Row
                  Row(
                    children: [
                      MetricsCard(
                        label: 'Focus Today',
                        value: '${status.todayFocusMinutes}m',
                        accentColor: CyberTheme.neonEmerald,
                        icon: Icons.timer_outlined,
                      ),
                      const SizedBox(width: 8),
                      MetricsCard(
                        label: 'Blocked',
                        value: '${status.distractionsBlocked}',
                        accentColor: CyberTheme.crimsonNeon,
                        icon: Icons.block_outlined,
                      ),
                      const SizedBox(width: 8),
                      MetricsCard(
                        label: 'Nudges',
                        value: '${status.nudgesSent}',
                        accentColor: CyberTheme.electricCyan,
                        icon: Icons.explore_outlined,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
```

- [ ] **Step 7: Implement `mobile/lib/main.dart`**

```dart
import 'package:flutter/material.dart';
import 'controllers/agent_controller.dart';
import 'core/theme.dart';
import 'screens/dashboard_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final controller = AgentController();
  await controller.init();
  runApp(ProductivityApp(controller: controller));
}

class ProductivityApp extends StatelessWidget {
  final AgentController controller;

  const ProductivityApp({
    Key? key,
    required this.controller,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Productivity Agent',
      debugShowCheckedModeBanner: false,
      theme: CyberTheme.darkTheme,
      home: DashboardScreen(controller: controller),
    );
  }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run from `mobile/`:
```bash
flutter test test/widget_test.dart
```
Expected: PASS (All tests passed!)

- [ ] **Step 9: Commit**

```bash
git add mobile/lib/widgets/ mobile/lib/screens/ mobile/lib/main.dart mobile/test/widget_test.dart
git commit -m "feat(mobile): implement Cyber Minimalist dashboard screen and UI widgets"
```

---

### Task 6: Native Android Platform Channel Stubs & Session Engine (`MainActivity.kt`)

**Files:**
- Modify: `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/MainActivity.kt`
- Test: Full Flutter test verification (`flutter test`)

**Interfaces:**
- Consumes: Flutter Engine, Android Platform Services
- Produces: Complete Kotlin implementation of `MethodChannel("com.syedafridi.productivity_agent/commands")` and `EventChannel("com.syedafridi.productivity_agent/events")`

- [ ] **Step 1: Implement `MainActivity.kt` with platform channels and timer streamer**

Write to `mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/MainActivity.kt`:

```kotlin
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
```

- [ ] **Step 2: Run all Flutter unit and widget tests to verify complete suite passes**

Run from `mobile/`:
```bash
flutter test
```
Expected: PASS (All test files passing: smoke_test, agent_status_test, bridge_test, controller_test, widget_test)

- [ ] **Step 3: Commit**

```bash
git add mobile/android/app/src/main/kotlin/com/syedafridi/productivity_agent/MainActivity.kt
git commit -m "feat(mobile): configure Android MainActivity platform channel handlers and mock session engine"
```

---

## Plan Self-Review Verification Checklist

1. **Spec Coverage:**
   - Flutter companion app in `mobile/`: covered in Task 1.
   - Target Android SDK 28+ up to 34: covered in Task 1.
   - Cyber Minimalist OLED dark theme (`#07080D`, `#11131F`, neon emerald/crimson/cyan/amber): covered in Task 2.
   - Immutable data models & serialization (`AgentState`, `AgentStatus`, `AgentEvent`): covered in Task 2.
   - `AgentBridge` platform channels (`commands` MethodChannel, `events` EventChannel): covered in Task 3.
   - Zero third-party state (`AgentController extends ChangeNotifier`): covered in Task 4.
   - Dashboard UI (Header, StatusRing, DurationPicker, Action Button, MetricsCard): covered in Task 5.
   - Native Kotlin channel handler stubs with active countdown ticker & accessibility check: covered in Task 6.
2. **Placeholder Scan:** No "TBD", "TODO", or pseudo-code steps. Every step has concrete code, commands, and expectations.
3. **Type Consistency:** Method names, channel strings (`com.syedafridi.productivity_agent/commands`), and signatures match between `AgentBridge`, `AgentController`, `MainActivity.kt`, and tests.
