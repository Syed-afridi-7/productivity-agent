import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/controllers/agent_controller.dart';
import 'package:productivity_agent/core/bridge.dart';
import 'package:productivity_agent/models/agent_status.dart';
import 'package:productivity_agent/screens/dashboard_screen.dart';

class FakeAgentBridge extends AgentBridge {
  AgentStatus fakeStatus = const AgentStatus(
    isRunning: true,
    state: AgentState.deepFocus,
    remainingSeconds: 1500,
    todayFocusMinutes: 45,
    distractionsBlocked: 4,
    nudgesSent: 2,
    gamingSecondsUsed: 600,   // 10 minutes
    gamingLimitSeconds: 1800, // 30 minutes
    reelsSecondsUsed: 300,    // 5 minutes
    reelsLimitSeconds: 1200,  // 20 minutes
    is24x7Active: true,
  );

  @override
  Future<AgentStatus> getAgentStatus() async => fakeStatus;

  @override
  Future<bool> isAccessibilityEnabled() async => true;

  @override
  Future<bool> isOverlayPermissionEnabled() async => true;

  @override
  Future<List<String>> getBlacklist() async => [];
}

void main() {
  test('AgentStatus properly parses quota telemetry and computes progress', () {
    final map = {
      'isRunning': true,
      'state': 'DEEP_FOCUS',
      'remainingSeconds': 1200,
      'todayFocusMinutes': 50,
      'distractionsBlocked': 5,
      'nudgesSent': 1,
      'gamingSecondsUsed': 900,
      'gamingLimitSeconds': 1800,
      'reelsSecondsUsed': 600,
      'reelsLimitSeconds': 1200,
      'is24x7Active': true,
    };

    final status = AgentStatus.fromMap(map);
    expect(status.gamingSecondsUsed, 900);
    expect(status.gamingMinutesUsed, 15);
    expect(status.gamingLimitMinutes, 30);
    expect(status.gamingProgress, 0.5);

    expect(status.reelsSecondsUsed, 600);
    expect(status.reelsMinutesUsed, 10);
    expect(status.reelsLimitMinutes, 20);
    expect(status.reelsProgress, 0.5);

    expect(status.is24x7Active, true);
  });

  test('AgentController updates quota usage on QUOTA_TICK event', () async {
    final eventController = StreamController<AgentEvent>.broadcast();
    final fakeBridge = FakeAgentBridge();
    final controller = AgentController(
      bridge: fakeBridge,
      eventStreamOverride: eventController.stream,
    );

    await controller.init();
    expect(controller.status.gamingSecondsUsed, 600);
    expect(controller.status.reelsSecondsUsed, 300);

    // Emit a QUOTA_TICK event
    eventController.add(
      AgentEvent.fromMap({
        'type': 'QUOTA_TICK',
        'category': 'GAMING',
        'gamingUsed': 605,
        'reelsUsed': 300,
      }),
    );

    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(controller.status.gamingSecondsUsed, 605);

    eventController.close();
  });

  testWidgets('DashboardScreen renders 24/7 Guardian badge and quota progress', (tester) async {
    final fakeBridge = FakeAgentBridge();
    final controller = AgentController(bridge: fakeBridge);
    await controller.init();

    await tester.pumpWidget(
      MaterialApp(
        home: DashboardScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();

    // Verify 24/7 Guardian Active badge
    expect(find.textContaining('24/7 GUARDIAN: ACTIVE'), findsOneWidget);

    // Verify Gaming Quota section
    expect(find.textContaining('Gaming Budget'), findsOneWidget);
    expect(find.textContaining('10 / 30m'), findsOneWidget);

    // Verify Reels/Shorts Quota section
    expect(find.textContaining('Reels & Shorts'), findsOneWidget);
    expect(find.textContaining('5 / 20m'), findsOneWidget);

    // Verify Whitelisted indicators
    expect(find.textContaining('DMs & Study: UNLIMITED'), findsOneWidget);
  });
}
