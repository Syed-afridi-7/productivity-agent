enum AgentState {
  idle,
  deepFocus,
  shortBreak;

  static AgentState fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'DEEP_FOCUS':
        return AgentState.deepFocus;
      case 'SHORT_BREAK':
        return AgentState.shortBreak;
      case 'IDLE':
      default:
        return AgentState.idle;
    }
  }

  String toPayloadString() {
    switch (this) {
      case AgentState.deepFocus:
        return 'DEEP_FOCUS';
      case AgentState.shortBreak:
        return 'SHORT_BREAK';
      case AgentState.idle:
        return 'IDLE';
    }
  }
}

enum AgentEventType {
  stateChanged,
  tick,
  appBlocked,
  nudgeTriggered,
  unknown;

  static AgentEventType fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'STATE_CHANGED':
        return AgentEventType.stateChanged;
      case 'TICK':
        return AgentEventType.tick;
      case 'APP_BLOCKED':
        return AgentEventType.appBlocked;
      case 'NUDGE_TRIGGERED':
        return AgentEventType.nudgeTriggered;
      default:
        return AgentEventType.unknown;
    }
  }
}

class AgentEvent {
  final AgentEventType type;
  final AgentState? fromState;
  final AgentState? toState;
  final int? remainingSeconds;
  final Map<String, dynamic> payload;

  const AgentEvent({
    required this.type,
    this.fromState,
    this.toState,
    this.remainingSeconds,
    this.payload = const {},
  });

  factory AgentEvent.fromMap(Map<dynamic, dynamic> map) {
    final typeStr = map['type'] as String?;
    final type = AgentEventType.fromString(typeStr);

    AgentState? from;
    AgentState? to;
    if (map.containsKey('from')) {
      from = AgentState.fromString(map['from'] as String?);
    }
    if (map.containsKey('to')) {
      to = AgentState.fromString(map['to'] as String?);
    }

    final remaining = (map['remainingSeconds'] as num?)?.toInt();

    return AgentEvent(
      type: type,
      fromState: from,
      toState: to,
      remainingSeconds: remaining,
      payload: Map<String, dynamic>.from(map),
    );
  }
}

class AgentStatus {
  final bool isRunning;
  final AgentState state;
  final int remainingSeconds;
  final int todayFocusMinutes;
  final int distractionsBlocked;
  final int nudgesSent;

  const AgentStatus({
    this.isRunning = false,
    this.state = AgentState.idle,
    this.remainingSeconds = 0,
    this.todayFocusMinutes = 0,
    this.distractionsBlocked = 0,
    this.nudgesSent = 0,
  });

  String get formattedTime {
    final minutes = (remainingSeconds / 60).floor().toString().padLeft(2, '0');
    final seconds = (remainingSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  String get stateDisplayName {
    switch (state) {
      case AgentState.deepFocus:
        return 'DEEP FOCUS';
      case AgentState.shortBreak:
        return 'SHORT BREAK';
      case AgentState.idle:
        return 'IDLE';
    }
  }

  AgentStatus copyWith({
    bool? isRunning,
    AgentState? state,
    int? remainingSeconds,
    int? todayFocusMinutes,
    int? distractionsBlocked,
    int? nudgesSent,
  }) {
    return AgentStatus(
      isRunning: isRunning ?? this.isRunning,
      state: state ?? this.state,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      todayFocusMinutes: todayFocusMinutes ?? this.todayFocusMinutes,
      distractionsBlocked: distractionsBlocked ?? this.distractionsBlocked,
      nudgesSent: nudgesSent ?? this.nudgesSent,
    );
  }

  factory AgentStatus.fromMap(Map<dynamic, dynamic> map) {
    return AgentStatus(
      isRunning: map['isRunning'] == true,
      state: AgentState.fromString(map['state'] as String?),
      remainingSeconds: (map['remainingSeconds'] as num?)?.toInt() ?? 0,
      todayFocusMinutes: (map['todayFocusMinutes'] as num?)?.toInt() ?? 0,
      distractionsBlocked: (map['distractionsBlocked'] as num?)?.toInt() ?? 0,
      nudgesSent: (map['nudgesSent'] as num?)?.toInt() ?? 0,
    );
  }
}
