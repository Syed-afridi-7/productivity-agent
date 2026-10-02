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
          case 'isOverlayPermissionEnabled':
            return true;
          case 'openOverlaySettings':
            return null;
          case 'updateBlacklist':
            return true;
          case 'getBlacklist':
            return ['com.instagram.android', 'com.twitter.android'];
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

  test('AgentBridge overlay permission checks and open settings', () async {
    final isEnabled = await bridge.isOverlayPermissionEnabled();
    expect(isEnabled, isTrue);
    expect(methodCalls.last.method, equals('isOverlayPermissionEnabled'));

    await bridge.openOverlaySettings();
    expect(methodCalls.last.method, equals('openOverlaySettings'));
  });

  test('AgentBridge updateBlacklist and getBlacklist invoke channel properly', () async {
    final list = ['com.instagram.android', 'com.twitter.android'];
    final updateResult = await bridge.updateBlacklist(list);
    expect(updateResult, isTrue);
    expect(methodCalls.last.method, equals('updateBlacklist'));
    expect(methodCalls.last.arguments, equals({'packages': list}));

    final retrieved = await bridge.getBlacklist();
    expect(retrieved, equals(list));
    expect(methodCalls.last.method, equals('getBlacklist'));
  });
}
