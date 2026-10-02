import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/controllers/agent_controller.dart';
import 'package:productivity_agent/main.dart';
import 'package:productivity_agent/widgets/duration_picker.dart';
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
    await controller.init();
    await tester.pumpWidget(ProductivityApp(controller: controller));
    await tester.pumpAndSettle();

    // Verify Header
    expect(find.text('⚡ PRODUCTIVITY AGENT'), findsOneWidget);

    // Verify Duration Pills
    expect(find.text('25m'), findsOneWidget);
    expect(find.descendant(of: find.byType(DurationPicker), matching: find.text('45m')), findsOneWidget);
    expect(find.text('60m'), findsOneWidget);

    // Verify Action Button in IDLE state
    expect(find.text('START GUARDIAN'), findsOneWidget);

    // Verify Metric Cards
    expect(find.text('Focus Today'), findsOneWidget);
    expect(find.descendant(of: find.byType(MetricsCard), matching: find.text('45m')), findsOneWidget);
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
