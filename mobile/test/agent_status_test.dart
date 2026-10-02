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

    test('AgentEvent.fromMap parses APP_OPENED and SCREEN_STATE_CHANGED events', () {
      final appOpenedEvent = AgentEvent.fromMap({
        'type': 'APP_OPENED',
        'packageName': 'com.instagram.android',
      });
      expect(appOpenedEvent.type, equals(AgentEventType.appOpened));
      expect(appOpenedEvent.appPackage, equals('com.instagram.android'));

      final screenOnEvent = AgentEvent.fromMap({
        'type': 'SCREEN_STATE_CHANGED',
        'screenOn': true,
      });
      expect(screenOnEvent.type, equals(AgentEventType.screenStateChanged));
      expect(screenOnEvent.isScreenOn, isTrue);

      final screenOffEvent = AgentEvent.fromMap({
        'type': 'SCREEN_STATE_CHANGED',
        'screenOn': false,
      });
      expect(screenOffEvent.type, equals(AgentEventType.screenStateChanged));
      expect(screenOffEvent.isScreenOn, isFalse);
    });
  });
}
