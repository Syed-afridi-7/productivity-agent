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
  bool _isOverlayGranted = false;
  bool _isLoading = false;
  List<String> _blacklist = const [];

  StreamSubscription<AgentEvent>? _streamSubscription;

  AgentController({
    AgentBridge? bridge,
    Stream<AgentEvent>? eventStreamOverride,
  })  : _bridge = bridge ?? AgentBridge(),
        _eventStreamOverride = eventStreamOverride;

  AgentStatus get status => _status;
  int get selectedDurationMinutes => _selectedDurationMinutes;
  bool get isAccessibilityGranted => _isAccessibilityGranted;
  bool get isOverlayGranted => _isOverlayGranted;
  bool get isLoading => _isLoading;
  List<String> get blacklist => List.unmodifiable(_blacklist);

  Future<void> init() async {
    _isLoading = true;
    notifyListeners();

    try {
      _isAccessibilityGranted = await _bridge.isAccessibilityEnabled();
      _isOverlayGranted = await _bridge.isOverlayPermissionEnabled();
      _status = await _bridge.getAgentStatus();

      final initialBlacklist = await _bridge.getBlacklist();
      if (initialBlacklist.isNotEmpty) {
        _blacklist = List.from(initialBlacklist);
      } else {
        _blacklist = [
          'com.instagram.android',
          'com.zhiliaoapp.musically',
          'com.twitter.android',
          'com.google.android.youtube',
          'com.facebook.katana',
          'com.reddit.frontpage',
        ];
      }
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

  Future<void> refreshOverlayPermission() async {
    _isOverlayGranted = await _bridge.isOverlayPermissionEnabled();
    notifyListeners();
  }

  Future<void> requestOverlayPermission() async {
    await _bridge.openOverlaySettings();
  }

  bool isPackageBlacklisted(String packageName) => _blacklist.contains(packageName);

  Future<void> toggleAppBlacklist(String packageName, bool enable) async {
    final updated = List<String>.from(_blacklist);
    if (enable) {
      if (!updated.contains(packageName)) updated.add(packageName);
    } else {
      updated.remove(packageName);
    }
    _blacklist = updated;
    notifyListeners();
    await _bridge.updateBlacklist(_blacklist);
  }

  Future<void> addCustomPackage(String packageName) async {
    final trimmed = packageName.trim();
    if (trimmed.isEmpty || _blacklist.contains(trimmed)) return;
    final updated = List<String>.from(_blacklist)..add(trimmed);
    _blacklist = updated;
    notifyListeners();
    await _bridge.updateBlacklist(_blacklist);
  }

  Future<void> removeCustomPackage(String packageName) async {
    final updated = List<String>.from(_blacklist)..remove(packageName);
    _blacklist = updated;
    notifyListeners();
    await _bridge.updateBlacklist(_blacklist);
  }

  Future<bool> updateBlacklist(List<String> packages) async {
    return await _bridge.updateBlacklist(packages);
  }

  Future<List<String>> getBlacklist() async {
    return await _bridge.getBlacklist();
  }

  @override
  void dispose() {
    _streamSubscription?.cancel();
    super.dispose();
  }
}
