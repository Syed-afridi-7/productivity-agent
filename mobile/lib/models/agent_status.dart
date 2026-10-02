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
  quotaTick,
  appBlocked,
  nudgeTriggered,
  appOpened,
  screenStateChanged,
  unknown;

  static AgentEventType fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'STATE_CHANGED':
        return AgentEventType.stateChanged;
      case 'TICK':
        return AgentEventType.tick;
      case 'QUOTA_TICK':
        return AgentEventType.quotaTick;
      case 'APP_BLOCKED':
        return AgentEventType.appBlocked;
      case 'NUDGE_TRIGGERED':
        return AgentEventType.nudgeTriggered;
      case 'APP_OPENED':
        return AgentEventType.appOpened;
      case 'SCREEN_STATE_CHANGED':
        return AgentEventType.screenStateChanged;
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

  String? get appPackage => payload['packageName'] as String?;
  bool? get isScreenOn => payload['screenOn'] as bool?;
  int? get gamingUsed => (payload['gamingUsed'] as num?)?.toInt();
  int? get reelsUsed => (payload['reelsUsed'] as num?)?.toInt();

  factory AgentEvent.fromMap(Map<dynamic, dynamic> map) {
    final typeStr = map['type'] as String?;
    final AgentEventType type;
    if (typeStr == 'APP_OPENED') {
      type = AgentEventType.appOpened;
    } else if (typeStr == 'SCREEN_STATE_CHANGED') {
      type = AgentEventType.screenStateChanged;
    } else if (typeStr == 'QUOTA_TICK') {
      type = AgentEventType.quotaTick;
    } else {
      type = AgentEventType.fromString(typeStr);
    }

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
  final int gamingSecondsUsed;
  final int gamingLimitSeconds;
  final int reelsSecondsUsed;
  final int reelsLimitSeconds;
  final bool is24x7Active;

  const AgentStatus({
    this.isRunning = false,
    this.state = AgentState.idle,
    this.remainingSeconds = 0,
    this.todayFocusMinutes = 0,
    this.distractionsBlocked = 0,
    this.nudgesSent = 0,
    this.gamingSecondsUsed = 0,
    this.gamingLimitSeconds = 1800, // 30 minutes
    this.reelsSecondsUsed = 0,
    this.reelsLimitSeconds = 1200,  // 20 minutes
    this.is24x7Active = true,
  });

  int get gamingMinutesUsed => (gamingSecondsUsed / 60).floor();
  int get gamingLimitMinutes => (gamingLimitSeconds / 60).floor();
  double get gamingProgress => gamingLimitSeconds > 0
      ? (gamingSecondsUsed / gamingLimitSeconds).clamp(0.0, 1.0)
      : 0.0;

  int get reelsMinutesUsed => (reelsSecondsUsed / 60).floor();
  int get reelsLimitMinutes => (reelsLimitSeconds / 60).floor();
  double get reelsProgress => reelsLimitSeconds > 0
      ? (reelsSecondsUsed / reelsLimitSeconds).clamp(0.0, 1.0)
      : 0.0;

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
    int? gamingSecondsUsed,
    int? gamingLimitSeconds,
    int? reelsSecondsUsed,
    int? reelsLimitSeconds,
    bool? is24x7Active,
  }) {
    return AgentStatus(
      isRunning: isRunning ?? this.isRunning,
      state: state ?? this.state,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      todayFocusMinutes: todayFocusMinutes ?? this.todayFocusMinutes,
      distractionsBlocked: distractionsBlocked ?? this.distractionsBlocked,
      nudgesSent: nudgesSent ?? this.nudgesSent,
      gamingSecondsUsed: gamingSecondsUsed ?? this.gamingSecondsUsed,
      gamingLimitSeconds: gamingLimitSeconds ?? this.gamingLimitSeconds,
      reelsSecondsUsed: reelsSecondsUsed ?? this.reelsSecondsUsed,
      reelsLimitSeconds: reelsLimitSeconds ?? this.reelsLimitSeconds,
      is24x7Active: is24x7Active ?? this.is24x7Active,
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
      gamingSecondsUsed: (map['gamingSecondsUsed'] as num?)?.toInt() ?? 0,
      gamingLimitSeconds: (map['gamingLimitSeconds'] as num?)?.toInt() ?? 1800,
      reelsSecondsUsed: (map['reelsSecondsUsed'] as num?)?.toInt() ?? 0,
      reelsLimitSeconds: (map['reelsLimitSeconds'] as num?)?.toInt() ?? 1200,
      is24x7Active: map.containsKey('is24x7Active') ? map['is24x7Active'] == true : true,
    );
  }
}
