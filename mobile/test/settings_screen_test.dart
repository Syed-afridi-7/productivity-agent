import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:productivity_agent/controllers/agent_controller.dart';
import 'package:productivity_agent/core/bridge.dart';
import 'package:productivity_agent/core/theme.dart';
import 'package:productivity_agent/screens/settings_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late List<String> updatedBlacklistPayload;
  bool openAccessibilityCalled = false;
  bool openOverlayCalled = false;

  setUp(() {
    updatedBlacklistPayload = [];
    openAccessibilityCalled = false;
    openOverlayCalled = false;

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
              'todayFocusMinutes': 40,
              'distractionsBlocked': 5,
              'nudgesSent': 2,
            };
          case 'isAccessibilityEnabled':
            return true;
          case 'isOverlayPermissionEnabled':
            return false;
          case 'openAccessibilitySettings':
            openAccessibilityCalled = true;
            return null;
          case 'openOverlaySettings':
            openOverlayCalled = true;
            return null;
          case 'updateBlacklist':
            final args = methodCall.arguments as Map?;
            final pkgs = (args?['packages'] as List?)?.map((e) => e.toString()).toList();
            if (pkgs != null) {
              updatedBlacklistPayload = pkgs;
            }
            return true;
          case 'getBlacklist':
            return [
              'com.instagram.android',
              'com.zhiliaoapp.musically',
              'com.twitter.android',
            ];
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

  Widget buildSettingsApp(AgentController controller) {
    return MaterialApp(
      theme: CyberTheme.darkTheme,
      home: SettingsScreen(controller: controller),
    );
  }

  testWidgets(
      'SettingsScreen renders AppBar title, System Permissions section, and Restricted Apps section',
      (tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = AgentController();
    await controller.init();

    await tester.pumpWidget(buildSettingsApp(controller));
    await tester.pumpAndSettle();

    // Verify AppBar Title
    expect(find.text('GUARDIAN SETTINGS'), findsOneWidget);

    // Verify System Permissions Section
    expect(find.text('SYSTEM PERMISSIONS'), findsOneWidget);
    expect(find.text('Accessibility Guardian'), findsOneWidget);
    expect(find.text('Detects real-time distracting app launches'), findsOneWidget);
    expect(find.text('Screen Overlay Shield'), findsOneWidget);
    expect(find.text('Displays OLED friction overlay over blocked apps'), findsOneWidget);

    // Accessibility is true -> ACTIVE chip
    expect(find.widgetWithText(Chip, 'ACTIVE'), findsOneWidget);
    // Overlay is false -> GRANT button
    expect(find.widgetWithText(OutlinedButton, 'GRANT'), findsOneWidget);

    // Verify Restricted Apps Section
    expect(find.text('RESTRICTED APPS'), findsOneWidget);
    expect(find.text('+ ADD PACKAGE'), findsOneWidget);

    // Verify Known Apps
    expect(find.text('Instagram'), findsOneWidget);
    expect(find.text('com.instagram.android'), findsOneWidget);
    expect(find.text('TikTok'), findsOneWidget);
    expect(find.text('com.zhiliaoapp.musically'), findsOneWidget);
    expect(find.text('X / Twitter'), findsOneWidget);
    expect(find.text('com.twitter.android'), findsOneWidget);
    expect(find.text('YouTube'), findsOneWidget);
    expect(find.text('com.google.android.youtube'), findsOneWidget);
    expect(find.text('Facebook'), findsOneWidget);
    expect(find.text('com.facebook.katana'), findsOneWidget);
    expect(find.text('Reddit'), findsOneWidget);
    expect(find.text('com.reddit.frontpage'), findsOneWidget);

    // Verify Default Focus Duration Section
    expect(find.text('DEFAULT FOCUS DURATION'), findsOneWidget);
    expect(find.text('25m'), findsOneWidget);
    expect(find.text('45m'), findsOneWidget);
    expect(find.text('60m'), findsOneWidget);
  });

  testWidgets('Toggling an app switch calls toggleAppBlacklist and updates state',
      (tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = AgentController();
    await controller.init();

    await tester.pumpWidget(buildSettingsApp(controller));
    await tester.pumpAndSettle();

    // Initially Instagram is blacklisted
    expect(controller.isPackageBlacklisted('com.instagram.android'), isTrue);

    // Find the switch for Instagram by its explicit Key
    final instaSwitch = find.byKey(const Key('switch_com.instagram.android'));
    expect(instaSwitch, findsOneWidget);

    await tester.ensureVisible(instaSwitch);
    await tester.pumpAndSettle();

    // Tap switch (Instagram)
    await tester.tap(instaSwitch);
    await tester.pumpAndSettle();

    // Instagram should now be disabled (removed from blacklist)
    expect(controller.isPackageBlacklisted('com.instagram.android'), isFalse);
    expect(updatedBlacklistPayload.contains('com.instagram.android'), isFalse);

    // Toggle it back on
    await tester.tap(instaSwitch);
    await tester.pumpAndSettle();

    expect(controller.isPackageBlacklisted('com.instagram.android'), isTrue);
    expect(updatedBlacklistPayload.contains('com.instagram.android'), isTrue);
  });

  testWidgets(
      'Tapping + ADD PACKAGE displays dialog and submitting adds package',
      (tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = AgentController();
    await controller.init();

    await tester.pumpWidget(buildSettingsApp(controller));
    await tester.pumpAndSettle();

    // Tap "+ ADD PACKAGE"
    await tester.tap(find.text('+ ADD PACKAGE'));
    await tester.pumpAndSettle();

    // Verify Dialog rendered
    expect(find.text('ADD RESTRICTED PACKAGE'), findsOneWidget);
    expect(find.byKey(const Key('custom_package_input')), findsOneWidget);
    expect(find.byKey(const Key('add_package_submit')), findsOneWidget);

    // Enter custom package
    const customPkg = 'com.netflix.mediaclient';
    await tester.enterText(find.byKey(const Key('custom_package_input')), customPkg);
    await tester.pumpAndSettle();

    // Tap "ADD" submit button
    await tester.tap(find.byKey(const Key('add_package_submit')));
    await tester.pumpAndSettle();

    // Dialog should be dismissed
    expect(find.text('ADD RESTRICTED PACKAGE'), findsNothing);

    // Package should be in controller blacklist
    expect(controller.isPackageBlacklisted(customPkg), isTrue);
    expect(updatedBlacklistPayload.contains(customPkg), isTrue);

    // Verify custom package tile is displayed with delete icon
    expect(find.text(customPkg), findsOneWidget);
    expect(find.text('Custom Package'), findsOneWidget);
    expect(find.byIcon(Icons.delete_outline_rounded), findsOneWidget);

    // Remove the custom package
    await tester.tap(find.byIcon(Icons.delete_outline_rounded));
    await tester.pumpAndSettle();

    expect(controller.isPackageBlacklisted(customPkg), isFalse);
    expect(find.text(customPkg), findsNothing);
  });

  testWidgets(
      'Tapping GRANT button calls permission request and duration selector updates controller',
      (tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = AgentController();
    await controller.init();

    await tester.pumpWidget(buildSettingsApp(controller));
    await tester.pumpAndSettle();

    // Overlay is not granted, tap GRANT
    final grantBtn = find.widgetWithText(OutlinedButton, 'GRANT');
    expect(grantBtn, findsOneWidget);
    await tester.tap(grantBtn);
    await tester.pumpAndSettle();

    expect(openOverlayCalled, isTrue);

    // Select duration 45m
    await tester.tap(find.text('45m'));
    await tester.pumpAndSettle();
    expect(controller.selectedDurationMinutes, equals(45));

    // Select duration 60m
    await tester.tap(find.text('60m'));
    await tester.pumpAndSettle();
    expect(controller.selectedDurationMinutes, equals(60));
  });
}
