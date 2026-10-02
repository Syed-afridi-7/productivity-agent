# Specification: Productivity Agent Core Engine (Phase 1)

**Date**: 2026-10-02  
**Status**: Approved for Planning  
**Subsystem**: Phase 1 — Core Agent Skeleton & Engine  

---

## 1. Executive Summary

This specification defines the architecture, data contracts, and implementation design for **Phase 1** of a personal AI productivity agent.

The agent operates on deterministic expert system principles:
- **No external LLMs or third-party cloud APIs.**
- **No transformer weights or pre-trained black-box models.**
- **Pure Python standard library (zero external pip packages).**
- Foundation built for seamless extension in future phases:
  - Phase 2: Android Phone Bridge (Accessibility / UsageStats)
  - Phase 3: Classical Planner & Constraint Solver
  - Phase 4: From-Scratch Neural Networks (pure Python matrix ops & backpropagation)
  - Phase 5: Autonomy & Daily Reflection Scoring

Phase 1 establishes the core deterministic brain: a thread-safe event-pipeline architecture combining a Finite State Machine (FSM), a hybrid Rule Engine, a persistent SQLite Memory Store, a background Heartbeat daemon, and an interactive CLI simulation shell.

---

## 2. Project Directory Structure

```text
D:\productivity-agent/
├── config/
│   └── rules.json              # User-tunable thresholds and app blocklists
├── core/
│   ├── __init__.py
│   ├── events.py               # Event dataclasses and EventType enums
│   ├── commands.py             # Command dataclasses and CommandType enums
│   ├── state_machine.py        # Finite State Machine & transition validation
│   ├── perception.py           # In-memory perception state tracker
│   ├── rule_engine.py          # Base rule class, rule catalog, and evaluator
│   ├── memory.py               # SQLite storage manager & analytics
│   ├── heartbeat.py            # Background periodic tick thread
│   └── pipeline.py             # 5-stage sequential event-processing pipeline
├── actuators/
│   ├── __init__.py
│   ├── base.py                 # Abstract base actuator interface
│   └── console_actuator.py     # Terminal visual alerts & sound feedback
├── tests/
│   ├── __init__.py
│   ├── test_fsm.py             # State machine unit tests
│   ├── test_rules.py           # Individual rule unit tests
│   ├── test_memory.py          # SQLite persistence unit tests
│   └── test_pipeline.py        # End-to-end event pipeline integration tests
├── cli.py                      # Interactive simulator & testing REPL
├── main.py                     # Main application bootstrap
└── docs/
    └── superpowers/
        └── specs/
            └── 2026-10-02-productivity-agent-core-design.md
```

---

## 3. Data Contracts (`core/events.py` & `core/commands.py`)

All communication within the system uses immutable Python dataclasses.

### 3.1 Event Contracts
```python
from dataclasses import dataclass, field
from enum import Enum
import time

class EventType(Enum):
    TICK = "TICK"
    APP_FOREGROUND = "APP_FOREGROUND"      # payload: {"app_name": str}
    SCREEN_STATE = "SCREEN_STATE"          # payload: {"is_on": bool}
    USER_COMMAND = "USER_COMMAND"          # payload: {"command": str, "args": dict}
    TIMER_EXPIRED = "TIMER_EXPIRED"        # payload: {"timer_id": str}
    SHUTDOWN = "SHUTDOWN"

@dataclass(frozen=True)
class Event:
    event_id: str
    event_type: EventType
    timestamp: float = field(default_factory=time.time)
    payload: dict = field(default_factory=dict)
```

### 3.2 Command Contracts
```python
class CommandType(Enum):
    NOTIFY = "NOTIFY"                      # payload: {"title": str, "body": str, "priority": str}
    FORCE_CLOSE = "FORCE_CLOSE"            # payload: {"app_name": str}
    START_TIMER = "START_TIMER"            # payload: {"timer_id": str, "duration_sec": int}
    CANCEL_TIMER = "CANCEL_TIMER"          # payload: {"timer_id": str}
    LOG_MESSAGE = "LOG_MESSAGE"            # payload: {"level": str, "message": str}

@dataclass(frozen=True)
class Command:
    command_id: str
    command_type: CommandType
    timestamp: float = field(default_factory=time.time)
    payload: dict = field(default_factory=dict)
```

---

## 4. Perception State & Finite State Machine

### 4.1 Perception State (`core/perception.py`)
Tracks the latest environment status in memory:
- `current_app`: Name of foreground application (e.g. `"launcher"`, `"com.instagram.android"`).
- `screen_on`: Boolean flag indicating display state.
- `last_screen_toggle_time`: Timestamp of last display state change.
- `daily_distraction_seconds`: Accumulated distraction seconds today (reset at midnight).
- `daily_focus_seconds`: Accumulated focus seconds today.
- `active_timer`: Optional dictionary tracking current countdown `{ "timer_id": str, "started_at": float, "ends_at": float }`.

### 4.2 Agent States (`AgentState`)
```python
class AgentState(Enum):
    IDLE = "IDLE"
    MORNING_PLANNING = "MORNING_PLANNING"
    DEEP_FOCUS = "DEEP_FOCUS"
    SHORT_BREAK = "SHORT_BREAK"
    DISTRACTION_DETECTED = "DISTRACTION_DETECTED"
    EVENING_REFLECTION = "EVENING_REFLECTION"
    SLEEP_OFFLINE = "SLEEP_OFFLINE"
```

### 4.3 Transition Matrix
| Current State | Permitted Next States | Trigger Conditions |
| :--- | :--- | :--- |
| `IDLE` | `DEEP_FOCUS`, `MORNING_PLANNING`, `SLEEP_OFFLINE` | User command, morning schedule, or night wind-down |
| `MORNING_PLANNING`| `IDLE`, `DEEP_FOCUS` | Plan finalized or manual start |
| `DEEP_FOCUS` | `SHORT_BREAK`, `DISTRACTION_DETECTED`, `IDLE` | Timer expiration, rule escalation, or manual cancel |
| `SHORT_BREAK` | `DEEP_FOCUS`, `IDLE` | Break timer expiration or manual resume |
| `DISTRACTION_DETECTED` | `DEEP_FOCUS`, `IDLE` | Cooldown elapsed or manual acknowledgment |
| `EVENING_REFLECTION` | `SLEEP_OFFLINE`, `IDLE` | Reflection complete or manual advance |
| `SLEEP_OFFLINE` | `MORNING_PLANNING`, `IDLE` | Wakeup schedule or morning unlock |

Invalid transitions are rejected and logged as warnings.

---

## 5. Rule Engine & Hybrid Configuration

### 5.1 Configuration (`config/rules.json`)
```json
{
  "app_blacklist": [
    "com.instagram.android",
    "com.google.android.youtube",
    "com.twitter.android",
    "com.zhiliaoapp.musically",
    "reddit",
    "netflix"
  ],
  "max_daily_distraction_minutes": 45,
  "night_winddown_time": "22:30",
  "morning_start_time": "07:00",
  "default_focus_duration_minutes": 25,
  "default_break_duration_minutes": 5,
  "cooldown_seconds": 60
}
```

### 5.2 Rule Definitions
1. **`BlacklistEnforcementRule`**:
   - Triggers when `current_state == AgentState.DEEP_FOCUS` and `perception.current_app` is present in `app_blacklist`.
   - Generates `FORCE_CLOSE` command for the offending app.
   - Emits a high-priority `NOTIFY` alert.
   - Logs a violation event to SQLite.
   - Triggers state transition to `DISTRACTION_DETECTED`.
2. **`CumulativeDistractionQuotaRule`**:
   - Triggers when `perception.daily_distraction_seconds > max_daily_distraction_minutes * 60`.
   - Generates escalating reminder notifications.
3. **`NightWindDownRule`**:
   - Triggers when current time >= `night_winddown_time` and `current_state == AgentState.IDLE`.
   - Emits wind-down notification and transitions to `SLEEP_OFFLINE`.
4. **`BreakOverstayRule`**:
   - Triggers when `current_state == AgentState.SHORT_BREAK` and break duration exceeds `default_break_duration_minutes`.
   - Emits audible/visual reminder to resume focus.

---

## 6. Memory Store (`core/memory.py`)

Uses standard library `sqlite3` at `data/agent.db`.

### 6.1 Relational Schema
```sql
CREATE TABLE IF NOT EXISTS events (
    event_id TEXT PRIMARY KEY,
    timestamp REAL NOT NULL,
    event_type TEXT NOT NULL,
    payload_json TEXT
);

CREATE TABLE IF NOT EXISTS state_transitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp REAL NOT NULL,
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    reason TEXT
);

CREATE TABLE IF NOT EXISTS violations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp REAL NOT NULL,
    rule_name TEXT NOT NULL,
    app_name TEXT,
    details_json TEXT
);

CREATE TABLE IF NOT EXISTS focus_sessions (
    session_id TEXT PRIMARY KEY,
    start_time REAL NOT NULL,
    end_time REAL,
    target_duration_min INTEGER NOT NULL,
    actual_duration_sec REAL DEFAULT 0,
    completed INTEGER DEFAULT 0,
    violations_count INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS daily_metrics (
    date TEXT PRIMARY KEY,
    total_focus_seconds REAL DEFAULT 0,
    total_distraction_seconds REAL DEFAULT 0,
    total_violations INTEGER DEFAULT 0,
    sessions_completed INTEGER DEFAULT 0,
    productivity_score REAL DEFAULT 0.0
);
```

### 6.2 Data Access Interface
- `log_event(event: Event)`: Records raw event.
- `log_transition(from_state, to_state, reason)`: Records FSM transition.
- `record_violation(rule_name, app_name, details)`: Inserts infraction record.
- `start_focus_session(target_minutes)` / `end_focus_session(completed)`: Session tracking.
- `get_daily_summary(date_str)`: Calculates focus-to-distraction ratio and score.

---

## 7. Event Pipeline (`core/pipeline.py`)

A single-threaded consumer processing a thread-safe `queue.Queue[Event]`:
1. **Stage 1 (Persist)**: Appends raw `Event` to SQLite `events` table.
2. **Stage 2 (Perception Update)**: Updates in-memory `PerceptionState` (app, timers, distraction accumulation).
3. **Stage 3 (FSM Process)**: Evaluates timer expiration, commands, and valid state transitions.
4. **Stage 4 (Rule Engine)**: Executes active rules against `(current_state, perception_state, config)`.
5. **Stage 5 (Actuator Dispatch)**: Sends generated commands to registered actuators.

---

## 8. Heartbeat & CLI Simulator

### 8.1 Background Heartbeat (`core/heartbeat.py`)
- Standard Python `threading.Thread` with `daemon=True`.
- Regularly pushes `EventType.TICK` into the pipeline event queue.
- Controlled via `threading.Event` stop flag for clean shutdown.

### 8.2 Interactive Simulator Shell (`cli.py`)
CLI REPL supporting:
- `status`: Live snapshot of state, current app, active timer, today's focus/distraction metrics.
- `focus [min]`: Starts deep focus session (default: 25).
- `break [min]`: Takes a short break.
- `stop`: Halts/aborts active session.
- `app <name>`: Injects app switch event (`APP_FOREGROUND`).
- `screen <on|off>`: Injects display toggle event.
- `tick [sec]`: Fast-forwards time.
- `violations`: Lists today's recorded infractions.
- `summary`: Prints daily performance scorecard.
- `rules`: Displays active rules and config thresholds.
- `quit`: Clean shutdown.

---

## 9. Verification & Testing Strategy

Full automated test suite in `tests/` using standard library `unittest`:
- `test_fsm.py`: Validates legal transitions, rejection of illegal jumps, and timeout handling.
- `test_rules.py`: Mocks state/perception to verify accurate firing of all 4 base rules.
- `test_memory.py`: Verifies schema creation and query calculations on `:memory:` SQLite.
- `test_pipeline.py`: Ingests simulated events and asserts exact sequence of emitted commands.
