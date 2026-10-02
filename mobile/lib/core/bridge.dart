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

  Future<bool> isOverlayPermissionEnabled() async {
    try {
      return await commandChannel.invokeMethod<bool>('isOverlayPermissionEnabled') ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<void> openOverlaySettings() async {
    try {
      await commandChannel.invokeMethod('openOverlaySettings');
    } catch (_) {}
  }

  Future<bool> updateBlacklist(List<String> packages) async {
    try {
      return await commandChannel.invokeMethod<bool>(
            'updateBlacklist',
            {'packages': packages},
          ) ??
          false;
    } catch (_) {
      return false;
    }
  }

  Future<List<String>> getBlacklist() async {
    try {
      final result =
          await commandChannel.invokeMethod<List<dynamic>>('getBlacklist');
      return result?.map((e) => e.toString()).toList() ?? [];
    } catch (_) {
      return [];
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
