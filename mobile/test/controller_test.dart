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
  bool isOverlayGrantedMock = false;
  bool openOverlaySettingsCalled = false;

  setUp(() {
    streamController = StreamController<dynamic>.broadcast();
    isOverlayGrantedMock = false;
    openOverlaySettingsCalled = false;

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
          case 'isOverlayPermissionEnabled':
            return isOverlayGrantedMock;
          case 'openOverlaySettings':
            openOverlaySettingsCalled = true;
            return null;
          case 'updateBlacklist':
            return true;
          case 'getBlacklist':
            return ['com.instagram.android'];
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

  test('AgentController refreshOverlayPermission and requestOverlayPermission update isOverlayGranted', () async {
    isOverlayGrantedMock = false;
    await controller.init();
    expect(controller.isOverlayGranted, isFalse);

    isOverlayGrantedMock = true;
    await controller.refreshOverlayPermission();
    expect(controller.isOverlayGranted, isTrue);

    await controller.requestOverlayPermission();
    expect(openOverlaySettingsCalled, isTrue);

    final updateSuccess = await controller.updateBlacklist(['com.instagram.android']);
    expect(updateSuccess, isTrue);

    final blacklist = await controller.getBlacklist();
    expect(blacklist, contains('com.instagram.android'));
  });
}
