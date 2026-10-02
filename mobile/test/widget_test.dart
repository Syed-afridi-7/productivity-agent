import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/controllers/agent_controller.dart';
import 'package:productivity_agent/main.dart';
import 'package:productivity_agent/screens/settings_screen.dart';
import 'package:productivity_agent/widgets/metrics_card.dart';

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
              'isRunning': true,
              'state': 'DEEP_FOCUS',
              'remainingSeconds': 0,
              'todayFocusMinutes': 45,
              'distractionsBlocked': 3,
              'nudgesSent': 2,
              'gamingSecondsUsed': 600,
              'gamingLimitSeconds': 1800,
              'reelsSecondsUsed': 300,
              'reelsLimitSeconds': 3600,
              'morningReelsUsed': 300,
              'afternoonReelsUsed': 0,
              'eveningReelsUsed': 0,
              'currentWindow': 'Morning',
              'currentWindowRemaining': 900,
              'windowLimitSeconds': 1200,
              'isCurrentWindowExhausted': false,
              'is24x7Active': true,
            };
          case 'isAccessibilityEnabled':
            return true;
          case 'isOverlayPermissionEnabled':
            return true;
          case 'getBlacklist':
            return ['com.instagram.android', 'com.zhiliaoapp.musically'];
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

  testWidgets('Dashboard displays header, 24/7 Guardian badge, window quota cards, and metrics', (tester) async {
    final controller = AgentController();
    await controller.init();
    await tester.pumpWidget(ProductivityApp(controller: controller));
    await tester.pumpAndSettle();

    // Verify Header
    expect(find.text('⚡ PRODUCTIVITY AGENT'), findsOneWidget);

    // Verify 24/7 Guardian Active badge
    expect(find.text('24/7 GUARDIAN: ACTIVE'), findsOneWidget);

    // Verify 3 Window Quota Cards
    expect(find.text('Morning'), findsOneWidget);
    expect(find.text('Afternoon'), findsOneWidget);
    expect(find.text('Evening'), findsOneWidget);

    // Verify Autonomous Lockdown Active card
    expect(find.text('AUTONOMOUS LOCKDOWN ACTIVE'), findsOneWidget);

    // Verify Metric Cards
    expect(find.text('Focus Today'), findsOneWidget);
    expect(find.descendant(of: find.byType(MetricsCard), matching: find.text('45m')), findsOneWidget);
    expect(find.text('Blocked'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    expect(find.text('Nudges'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);

    // Verify Settings Button
    expect(find.byKey(const Key('dashboard_settings_button')), findsOneWidget);
  });

  testWidgets('Settings icon button exists on DashboardScreen and tapping navigates to SettingsScreen', (tester) async {
    final controller = AgentController();
    await controller.init();
    await tester.pumpWidget(ProductivityApp(controller: controller));
    await tester.pumpAndSettle();

    // Verify settings button exists with key 'dashboard_settings_button'
    final settingsButton = find.byKey(const Key('dashboard_settings_button'));
    expect(settingsButton, findsOneWidget);

    // Tap settings button and verify navigation to SettingsScreen
    await tester.tap(settingsButton);
    await tester.pumpAndSettle();

    expect(find.byType(SettingsScreen), findsOneWidget);
    expect(find.text('GUARDIAN SETTINGS'), findsOneWidget);
  });
}
