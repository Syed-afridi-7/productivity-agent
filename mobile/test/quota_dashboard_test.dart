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
    reelsLimitSeconds: 3600,  // 60 minutes
    morningReelsUsed: 300,    // 5 minutes
    afternoonReelsUsed: 0,
    eveningReelsUsed: 0,
    currentWindow: 'Morning',
    currentWindowRemainingSeconds: 900, // 15 minutes
    windowLimitSeconds: 1200, // 20 minutes
    isCurrentWindowExhausted: false,
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
      'reelsLimitSeconds': 3600,
      'morningReelsUsed': 600,
      'afternoonReelsUsed': 300,
      'eveningReelsUsed': 0,
      'currentWindow': 'Afternoon',
      'currentWindowRemaining': 900,
      'windowLimitSeconds': 1200,
      'isCurrentWindowExhausted': false,
      'is24x7Active': true,
    };

    final status = AgentStatus.fromMap(map);
    expect(status.gamingSecondsUsed, 900);
    expect(status.gamingMinutesUsed, 15);
    expect(status.gamingLimitMinutes, 30);
    expect(status.gamingProgress, 0.5);

    expect(status.reelsSecondsUsed, 600);
    expect(status.reelsMinutesUsed, 10);
    expect(status.reelsLimitMinutes, 60);

    expect(status.morningMinutesUsed, 10);
    expect(status.afternoonMinutesUsed, 5);
    expect(status.eveningMinutesUsed, 0);
    expect(status.windowLimitMinutes, 20);
    expect(status.currentWindow, 'Afternoon');
    expect(status.currentWindowRemainingMinutes, 15);
    expect(status.morningProgress, 0.5);
    expect(status.afternoonProgress, 0.25);
    expect(status.eveningProgress, 0.0);
    expect(status.isCurrentWindowExhausted, false);
    expect(status.is24x7Active, true);
  });

  test('AgentController updates quota usage and windows on QUOTA_TICK event', () async {
    final eventController = StreamController<AgentEvent>.broadcast();
    final fakeBridge = FakeAgentBridge();
    final controller = AgentController(
      bridge: fakeBridge,
      eventStreamOverride: eventController.stream,
    );

    await controller.init();
    expect(controller.status.gamingSecondsUsed, 600);
    expect(controller.status.reelsSecondsUsed, 300);
    expect(controller.status.morningReelsUsed, 300);

    // Emit a QUOTA_TICK event with updated window values
    eventController.add(
      AgentEvent.fromMap({
        'type': 'QUOTA_TICK',
        'category': 'REELS',
        'gamingUsed': 600,
        'reelsUsed': 360,
        'morningReelsUsed': 360,
        'afternoonReelsUsed': 0,
        'eveningReelsUsed': 0,
        'currentWindow': 'Morning',
        'currentWindowRemaining': 840,
      }),
    );

    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(controller.status.reelsSecondsUsed, 360);
    expect(controller.status.morningReelsUsed, 360);
    expect(controller.status.currentWindowRemainingSeconds, 840);

    eventController.close();
  });

  testWidgets('DashboardScreen renders 24/7 Guardian badge, window cards, and quota progress', (tester) async {
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

    // Verify 3 Window Cards
    expect(find.text('Morning'), findsOneWidget);
    expect(find.text('Afternoon'), findsOneWidget);
    expect(find.text('Evening'), findsOneWidget);
    expect(find.text('ACTIVE'), findsOneWidget); // Morning is active

    // Verify Gaming Quota section
    expect(find.textContaining('Gaming Budget'), findsOneWidget);
    expect(find.textContaining('10 / 30m'), findsOneWidget);

    // Verify Reels/Shorts Quota section
    expect(find.textContaining('Reels & Shorts'), findsOneWidget);
    expect(find.textContaining('5 / 20m'), findsOneWidget);

    // Verify Whitelisted indicators
    expect(find.textContaining('DMs & Study: UNLIMITED'), findsOneWidget);

    // Verify Autonomous Lockdown card
    expect(find.textContaining('AUTONOMOUS LOCKDOWN ACTIVE'), findsOneWidget);
  });
}
