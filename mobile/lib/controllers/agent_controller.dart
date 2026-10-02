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

      case AgentEventType.appOpened:
      case AgentEventType.screenStateChanged:
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
