# Productivity Agent Core Engine (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete deterministic core engine (Phase 1) for a personal productivity agent from scratch using Python standard library alone, featuring an Event-Pipeline architecture, Finite State Machine, hybrid Rule Engine, SQLite persistence, and an interactive CLI simulator.

**Architecture:** An event-pipeline architecture where periodic heartbeat ticks and asynchronous events (CLI commands, simulated app launches, screen toggles) enter a thread-safe `queue.Queue`. A single-threaded worker sequentially processes events across 5 deterministic stages: Persist to SQLite, Update Perception State, Update FSM, Evaluate Rules, and Dispatch Actuator Commands.

**Tech Stack:** Python 3.10+ Standard Library (`sqlite3`, `dataclasses`, `enum`, `json`, `threading`, `queue`, `unittest`, `time`, `winsound`). Zero third-party pip dependencies.

**Spec:** [docs/superpowers/specs/2026-10-02-productivity-agent-core-design.md](file:///D:/productivity-agent/docs/superpowers/specs/2026-10-02-productivity-agent-core-design.md)

## Global Constraints

- Pure Python Standard Library only (`sqlite3`, `dataclasses`, `enum`, `json`, `threading`, `queue`, `unittest`, `time`, `winsound`).
- Absolute zero third-party pip dependencies.
- All code must run on Windows PowerShell.
- Strict TDD workflow: every task must write a failing test first, verify failure, implement minimal code, verify pass, and commit.
- Path root: `D:\productivity-agent`.

---

### Task 1: Core Events & Commands Contracts

**Files:**
- Create: `core/events.py`
- Create: `core/commands.py`
- Create: `tests/test_events_commands.py`

**Interfaces:**
- Consumes: Standard library `dataclasses`, `enum`, `time`
- Produces:
  - `EventType` enum: `TICK`, `APP_FOREGROUND`, `SCREEN_STATE`, `USER_COMMAND`, `TIMER_EXPIRED`, `SHUTDOWN`
  - `Event` dataclass: `(event_id: str, event_type: EventType, timestamp: float, payload: dict)`
  - `CommandType` enum: `NOTIFY`, `FORCE_CLOSE`, `START_TIMER`, `CANCEL_TIMER`, `LOG_MESSAGE`
  - `Command` dataclass: `(command_id: str, command_type: CommandType, timestamp: float, payload: dict)`

- [ ] **Step 1: Write the failing test**

```python
# tests/test_events_commands.py
import unittest
import time
from core.events import Event, EventType
from core.commands import Command, CommandType

class TestEventsAndCommands(unittest.TestCase):
    def test_event_instantiation_and_defaults(self):
        t0 = time.time()
        evt = Event(event_id="evt-1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "instagram"})
        self.assertEqual(evt.event_id, "evt-1")
        self.assertEqual(evt.event_type, EventType.APP_FOREGROUND)
        self.assertEqual(evt.payload["app_name"], "instagram")
        self.assertGreaterEqual(evt.timestamp, t0)

    def test_command_instantiation(self):
        cmd = Command(command_id="cmd-1", command_type=CommandType.FORCE_CLOSE, payload={"app_name": "instagram"})
        self.assertEqual(cmd.command_id, "cmd-1")
        self.assertEqual(cmd.command_type, CommandType.FORCE_CLOSE)
        self.assertEqual(cmd.payload["app_name"], "instagram")

    def test_frozen_immutability(self):
        evt = Event(event_id="evt-2", event_type=EventType.TICK)
        with self.assertRaises(Exception):
            evt.event_id = "modified"

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_events_commands.py`  
Expected: `ModuleNotFoundError: No module named 'core.events'`

- [ ] **Step 3: Write minimal implementation**

```python
# core/events.py
from dataclasses import dataclass, field
from enum import Enum
import time

class EventType(Enum):
    TICK = "TICK"
    APP_FOREGROUND = "APP_FOREGROUND"
    SCREEN_STATE = "SCREEN_STATE"
    USER_COMMAND = "USER_COMMAND"
    TIMER_EXPIRED = "TIMER_EXPIRED"
    SHUTDOWN = "SHUTDOWN"

@dataclass(frozen=True)
class Event:
    event_id: str
    event_type: EventType
    timestamp: float = field(default_factory=time.time)
    payload: dict = field(default_factory=dict)
```

```python
# core/commands.py
from dataclasses import dataclass, field
from enum import Enum
import time

class CommandType(Enum):
    NOTIFY = "NOTIFY"
    FORCE_CLOSE = "FORCE_CLOSE"
    START_TIMER = "START_TIMER"
    CANCEL_TIMER = "CANCEL_TIMER"
    LOG_MESSAGE = "LOG_MESSAGE"

@dataclass(frozen=True)
class Command:
    command_id: str
    command_type: CommandType
    timestamp: float = field(default_factory=time.time)
    payload: dict = field(default_factory=dict)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_events_commands.py`  
Expected: `Ran 3 tests in ... OK`

- [ ] **Step 5: Commit**

```bash
git add core/events.py core/commands.py tests/test_events_commands.py
git commit -m "feat(core): add Event and Command immutable data contracts"
```

---

### Task 2: Perception State Tracker

**Files:**
- Create: `core/perception.py`
- Create: `tests/test_perception.py`

**Interfaces:**
- Consumes: `core/events.py` (`Event`, `EventType`)
- Produces:
  - `PerceptionState` dataclass:
    - `current_app: str`
    - `screen_on: bool`
    - `last_screen_toggle_time: float`
    - `daily_distraction_seconds: float`
    - `daily_focus_seconds: float`
    - `active_timer: dict | None`
    - `update(event: Event, blacklist: list[str]) -> list[Event]` (may emit `TIMER_EXPIRED` event when timers lapse)

- [ ] **Step 1: Write the failing test**

```python
# tests/test_perception.py
import unittest
import time
from core.events import Event, EventType
from core.perception import PerceptionState

class TestPerceptionState(unittest.TestCase):
    def test_initial_state(self):
        state = PerceptionState()
        self.assertEqual(state.current_app, "launcher")
        self.assertTrue(state.screen_on)
        self.assertEqual(state.daily_distraction_seconds, 0.0)
        self.assertEqual(state.daily_focus_seconds, 0.0)
        self.assertIsNone(state.active_timer)

    def test_app_switch_and_screen_toggle(self):
        state = PerceptionState()
        evt_app = Event(event_id="e1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "vscode"})
        state.update(evt_app, blacklist=["instagram"])
        self.assertEqual(state.current_app, "vscode")

        evt_screen = Event(event_id="e2", event_type=EventType.SCREEN_STATE, payload={"is_on": False})
        state.update(evt_screen, blacklist=["instagram"])
        self.assertFalse(state.screen_on)

    def test_distraction_accumulation_on_tick(self):
        state = PerceptionState()
        evt_app = Event(event_id="e1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "instagram"})
        state.update(evt_app, blacklist=["instagram"])
        
        # Advance with a tick 5 seconds later
        tick_evt = Event(event_id="e2", event_type=EventType.TICK, timestamp=time.time() + 5, payload={"delta_seconds": 5})
        state.update(tick_evt, blacklist=["instagram"])
        self.assertEqual(state.daily_distraction_seconds, 5.0)

    def test_timer_countdown_and_expiration(self):
        state = PerceptionState()
        now = time.time()
        state.set_timer("focus_timer", duration_sec=10, start_time=now)
        self.assertIsNotNone(state.active_timer)
        self.assertFalse(state.is_timer_expired(now + 5))
        self.assertTrue(state.is_timer_expired(now + 11))

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_perception.py`  
Expected: `ModuleNotFoundError: No module named 'core.perception'`

- [ ] **Step 3: Write minimal implementation**

```python
# core/perception.py
from dataclasses import dataclass, field
import time
from typing import Optional, List
from core.events import Event, EventType

@dataclass
class PerceptionState:
    current_app: str = "launcher"
    screen_on: bool = True
    last_screen_toggle_time: float = field(default_factory=time.time)
    daily_distraction_seconds: float = 0.0
    daily_focus_seconds: float = 0.0
    active_timer: Optional[dict] = None  # {"timer_id": str, "started_at": float, "duration_sec": int, "ends_at": float}
    last_tick_time: float = field(default_factory=time.time)

    def set_timer(self, timer_id: str, duration_sec: int, start_time: Optional[float] = None) -> None:
        t0 = start_time or time.time()
        self.active_timer = {
            "timer_id": timer_id,
            "started_at": t0,
            "duration_sec": duration_sec,
            "ends_at": t0 + duration_sec
        }

    def clear_timer(self) -> None:
        self.active_timer = None

    def is_timer_expired(self, current_time: Optional[float] = None) -> bool:
        if not self.active_timer:
            return False
        now = current_time or time.time()
        return now >= self.active_timer["ends_at"]

    def update(self, event: Event, blacklist: Optional[List[str]] = None) -> None:
        blacklist = blacklist or []
        if event.event_type == EventType.APP_FOREGROUND:
            self.current_app = event.payload.get("app_name", self.current_app)
        elif event.event_type == EventType.SCREEN_STATE:
            self.screen_on = event.payload.get("is_on", True)
            self.last_screen_toggle_time = event.timestamp
        elif event.event_type == EventType.TICK:
            delta = event.payload.get("delta_seconds", 0)
            if delta <= 0 and self.last_tick_time:
                delta = max(0.0, event.timestamp - self.last_tick_time)
            self.last_tick_time = event.timestamp

            if self.screen_on and self.current_app in blacklist:
                self.daily_distraction_seconds += delta
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_perception.py`  
Expected: `Ran 4 tests in ... OK`

- [ ] **Step 5: Commit**

```bash
git add core/perception.py tests/test_perception.py
git commit -m "feat(core): add PerceptionState tracking apps, screen, timers, and distraction time"
```

---

### Task 3: SQLite Memory Store

**Files:**
- Create: `core/memory.py`
- Create: `tests/test_memory.py`

**Interfaces:**
- Consumes: Standard library `sqlite3`, `json`, `core/events.py` (`Event`)
- Produces:
  - `MemoryStore(db_path: str = "data/agent.db")`
    - `log_event(event: Event) -> None`
    - `log_transition(from_state: str, to_state: str, reason: str, timestamp: float) -> None`
    - `record_violation(rule_name: str, app_name: str, details: dict, timestamp: float) -> None`
    - `start_focus_session(session_id: str, target_min: int, start_time: float) -> None`
    - `end_focus_session(session_id: str, completed: bool, end_time: float) -> None`
    - `get_daily_summary(date_str: str) -> dict`
    - `get_recent_violations(limit: int) -> list[dict]`

- [ ] **Step 1: Write the failing test**

```python
# tests/test_memory.py
import unittest
import time
from core.events import Event, EventType
from core.memory import MemoryStore

class TestMemoryStore(unittest.TestCase):
    def setUp(self):
        # In-memory SQLite for tests
        self.memory = MemoryStore(db_path=":memory:")

    def tearDown(self):
        self.memory.close()

    def test_log_event_and_retrieve(self):
        evt = Event(event_id="e-1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "vscode"})
        self.memory.log_event(evt)
        events = self.memory.get_recent_events(limit=10)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]["event_id"], "e-1")
        self.assertEqual(events[0]["event_type"], "APP_FOREGROUND")
        self.assertEqual(events[0]["payload"]["app_name"], "vscode")

    def test_log_transition(self):
        self.memory.log_transition(from_state="IDLE", to_state="DEEP_FOCUS", reason="user command", timestamp=time.time())
        transitions = self.memory.get_recent_transitions(limit=5)
        self.assertEqual(len(transitions), 1)
        self.assertEqual(transitions[0]["from_state"], "IDLE")
        self.assertEqual(transitions[0]["to_state"], "DEEP_FOCUS")

    def test_record_violation_and_daily_summary(self):
        t0 = time.time()
        self.memory.record_violation(rule_name="BlacklistRule", app_name="instagram", details={"strike": 1}, timestamp=t0)
        violations = self.memory.get_recent_violations(limit=5)
        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0]["app_name"], "instagram")

    def test_focus_session_lifecycle(self):
        t0 = time.time()
        self.memory.start_focus_session(session_id="s-1", target_min=25, start_time=t0)
        self.memory.end_focus_session(session_id="s-1", completed=True, end_time=t0 + 1500)
        sessions = self.memory.get_focus_sessions()
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["completed"], 1)
        self.assertEqual(sessions[0]["actual_duration_sec"], 1500)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_memory.py`  
Expected: `ModuleNotFoundError: No module named 'core.memory'`

- [ ] **Step 3: Write minimal implementation**

```python
# core/memory.py
import sqlite3
import json
import time
import os
from typing import Optional, List, Dict, Any
from core.events import Event, EventType

class MemoryStore:
    def __init__(self, db_path: str = "data/agent.db"):
        self.db_path = db_path
        if db_path != ":memory:":
            os.makedirs(os.path.dirname(os.path.abspath(db_path)), exist_ok=True)
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self._init_schema()

    def _init_schema(self) -> None:
        with self.conn:
            self.conn.executescript("""
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
            """)

    def log_event(self, event: Event) -> None:
        with self.conn:
            self.conn.execute(
                "INSERT OR REPLACE INTO events (event_id, timestamp, event_type, payload_json) VALUES (?, ?, ?, ?)",
                (event.event_id, event.timestamp, event.event_type.value, json.dumps(event.payload))
            )

    def log_transition(self, from_state: str, to_state: str, reason: str, timestamp: Optional[float] = None) -> None:
        t = timestamp or time.time()
        with self.conn:
            self.conn.execute(
                "INSERT INTO state_transitions (timestamp, from_state, to_state, reason) VALUES (?, ?, ?, ?)",
                (t, from_state, to_state, reason)
            )

    def record_violation(self, rule_name: str, app_name: str, details: Optional[Dict[str, Any]] = None, timestamp: Optional[float] = None) -> None:
        t = timestamp or time.time()
        with self.conn:
            self.conn.execute(
                "INSERT INTO violations (timestamp, rule_name, app_name, details_json) VALUES (?, ?, ?, ?)",
                (t, rule_name, app_name, json.dumps(details or {}))
            )

    def start_focus_session(self, session_id: str, target_min: int, start_time: Optional[float] = None) -> None:
        t0 = start_time or time.time()
        with self.conn:
            self.conn.execute(
                "INSERT OR REPLACE INTO focus_sessions (session_id, start_time, target_duration_min, completed) VALUES (?, ?, ?, 0)",
                (session_id, t0, target_min)
            )

    def end_focus_session(self, session_id: str, completed: bool, end_time: Optional[float] = None) -> None:
        t1 = end_time or time.time()
        with self.conn:
            cur = self.conn.execute("SELECT start_time FROM focus_sessions WHERE session_id = ?", (session_id,))
            row = cur.fetchone()
            duration = t1 - row["start_time"] if row else 0.0
            self.conn.execute(
                "UPDATE focus_sessions SET end_time = ?, actual_duration_sec = ?, completed = ? WHERE session_id = ?",
                (t1, duration, 1 if completed else 0, session_id)
            )

    def get_recent_events(self, limit: int = 20) -> List[Dict[str, Any]]:
        cur = self.conn.execute("SELECT event_id, timestamp, event_type, payload_json FROM events ORDER BY timestamp DESC LIMIT ?", (limit,))
        return [
            {
                "event_id": row["event_id"],
                "timestamp": row["timestamp"],
                "event_type": row["event_type"],
                "payload": json.loads(row["payload_json"] or "{}")
            }
            for row in cur.fetchall()
        ]

    def get_recent_transitions(self, limit: int = 20) -> List[Dict[str, Any]]:
        cur = self.conn.execute("SELECT timestamp, from_state, to_state, reason FROM state_transitions ORDER BY timestamp DESC LIMIT ?", (limit,))
        return [dict(row) for row in cur.fetchall()]

    def get_recent_violations(self, limit: int = 20) -> List[Dict[str, Any]]:
        cur = self.conn.execute("SELECT timestamp, rule_name, app_name, details_json FROM violations ORDER BY timestamp DESC LIMIT ?", (limit,))
        return [
            {
                "timestamp": row["timestamp"],
                "rule_name": row["rule_name"],
                "app_name": row["app_name"],
                "details": json.loads(row["details_json"] or "{}")
            }
            for row in cur.fetchall()
        ]

    def get_focus_sessions(self) -> List[Dict[str, Any]]:
        cur = self.conn.execute("SELECT * FROM focus_sessions ORDER BY start_time DESC")
        return [dict(row) for row in cur.fetchall()]

    def close(self) -> None:
        self.conn.close()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_memory.py`  
Expected: `Ran 4 tests in ... OK`

- [ ] **Step 5: Commit**

```bash
git add core/memory.py tests/test_memory.py
git commit -m "feat(core): add SQLite MemoryStore for events, transitions, violations, and sessions"
```

---

### Task 4: Finite State Machine (FSM)

**Files:**
- Create: `core/state_machine.py`
- Create: `tests/test_fsm.py`

**Interfaces:**
- Consumes: `core/events.py`, `core/perception.py`, `core/memory.py`
- Produces:
  - `AgentState` enum: `IDLE`, `MORNING_PLANNING`, `DEEP_FOCUS`, `SHORT_BREAK`, `DISTRACTION_DETECTED`, `EVENING_REFLECTION`, `SLEEP_OFFLINE`
  - `StateMachine(memory: Optional[MemoryStore] = None)`
    - `current_state: AgentState`
    - `previous_state: AgentState`
    - `transition_to(new_state: AgentState, reason: str, timestamp: float) -> bool`
    - `can_transition(new_state: AgentState) -> bool`
    - `time_in_current_state(current_time: float) -> float`

- [ ] **Step 1: Write the failing test**

```python
# tests/test_fsm.py
import unittest
import time
from core.state_machine import StateMachine, AgentState
from core.memory import MemoryStore

class TestStateMachine(unittest.TestCase):
    def setUp(self):
        self.memory = MemoryStore(db_path=":memory:")
        self.fsm = StateMachine(memory=self.memory)

    def tearDown(self):
        self.memory.close()

    def test_initial_state(self):
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)

    def test_valid_transitions(self):
        self.assertTrue(self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="start work"))
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)
        self.assertEqual(self.fsm.previous_state, AgentState.IDLE)

        self.assertTrue(self.fsm.transition_to(AgentState.SHORT_BREAK, reason="pomodoro elapsed"))
        self.assertEqual(self.fsm.current_state, AgentState.SHORT_BREAK)

        self.assertTrue(self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="break finished"))
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        self.assertTrue(self.fsm.transition_to(AgentState.IDLE, reason="manual stop"))
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)

    def test_invalid_transition_rejection(self):
        # IDLE to SHORT_BREAK is not a valid direct transition
        self.assertFalse(self.fsm.can_transition(AgentState.SHORT_BREAK))
        self.assertFalse(self.fsm.transition_to(AgentState.SHORT_BREAK, reason="illegal jump"))
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)

    def test_memory_logging_on_transition(self):
        self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="start focus session")
        logs = self.memory.get_recent_transitions(limit=1)
        self.assertEqual(len(logs), 1)
        self.assertEqual(logs[0]["to_state"], "DEEP_FOCUS")
        self.assertEqual(logs[0]["reason"], "start focus session")

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_fsm.py`  
Expected: `ModuleNotFoundError: No module named 'core.state_machine'`

- [ ] **Step 3: Write minimal implementation**

```python
# core/state_machine.py
from enum import Enum
import time
from typing import Optional, Dict, Set
from core.memory import MemoryStore

class AgentState(Enum):
    IDLE = "IDLE"
    MORNING_PLANNING = "MORNING_PLANNING"
    DEEP_FOCUS = "DEEP_FOCUS"
    SHORT_BREAK = "SHORT_BREAK"
    DISTRACTION_DETECTED = "DISTRACTION_DETECTED"
    EVENING_REFLECTION = "EVENING_REFLECTION"
    SLEEP_OFFLINE = "SLEEP_OFFLINE"

class StateMachine:
    TRANSITIONS: Dict[AgentState, Set[AgentState]] = {
        AgentState.IDLE: {AgentState.DEEP_FOCUS, AgentState.MORNING_PLANNING, AgentState.SLEEP_OFFLINE},
        AgentState.MORNING_PLANNING: {AgentState.IDLE, AgentState.DEEP_FOCUS},
        AgentState.DEEP_FOCUS: {AgentState.SHORT_BREAK, AgentState.DISTRACTION_DETECTED, AgentState.IDLE},
        AgentState.SHORT_BREAK: {AgentState.DEEP_FOCUS, AgentState.IDLE},
        AgentState.DISTRACTION_DETECTED: {AgentState.DEEP_FOCUS, AgentState.IDLE},
        AgentState.EVENING_REFLECTION: {AgentState.SLEEP_OFFLINE, AgentState.IDLE},
        AgentState.SLEEP_OFFLINE: {AgentState.MORNING_PLANNING, AgentState.IDLE}
    }

    def __init__(self, memory: Optional[MemoryStore] = None):
        self.current_state: AgentState = AgentState.IDLE
        self.previous_state: Optional[AgentState] = None
        self.state_entered_at: float = time.time()
        self.memory = memory

    def can_transition(self, new_state: AgentState) -> bool:
        allowed = self.TRANSITIONS.get(self.current_state, set())
        return new_state in allowed

    def transition_to(self, new_state: AgentState, reason: str, timestamp: Optional[float] = None) -> bool:
        if not self.can_transition(new_state):
            return False
        
        t = timestamp or time.time()
        self.previous_state = self.current_state
        self.current_state = new_state
        self.state_entered_at = t

        if self.memory:
            self.memory.log_transition(
                from_state=self.previous_state.value,
                to_state=self.current_state.value,
                reason=reason,
                timestamp=t
            )
        return True

    def time_in_current_state(self, current_time: Optional[float] = None) -> float:
        now = current_time or time.time()
        return max(0.0, now - self.state_entered_at)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_fsm.py`  
Expected: `Ran 4 tests in ... OK`

- [ ] **Step 5: Commit**

```bash
git add core/state_machine.py tests/test_fsm.py
git commit -m "feat(core): add StateMachine with transition validation and transition history logging"
```

---

### Task 5: Configuration Loader & Hybrid Rule Engine

**Files:**
- Create: `config/rules.json`
- Create: `core/rule_engine.py`
- Create: `tests/test_rules.py`

**Interfaces:**
- Consumes: `core/events.py`, `core/commands.py`, `core/perception.py`, `core/state_machine.py`
- Produces:
  - `BaseRule` (abstract base class)
  - Concrete Rules: `BlacklistEnforcementRule`, `CumulativeDistractionQuotaRule`, `NightWindDownRule`, `BreakOverstayRule`
  - `RuleEngine(config_path: str = "config/rules.json", memory: Optional[MemoryStore] = None)`
    - `evaluate_all(current_state: AgentState, perception: PerceptionState, current_time: float) -> list[Command]`

- [ ] **Step 1: Write the failing test**

```python
# tests/test_rules.py
import unittest
import time
from core.state_machine import AgentState
from core.perception import PerceptionState
from core.commands import CommandType
from core.rule_engine import RuleEngine, BlacklistEnforcementRule, CumulativeDistractionQuotaRule

class TestRuleEngine(unittest.TestCase):
    def setUp(self):
        self.config = {
            "app_blacklist": ["com.instagram.android", "reddit"],
            "max_daily_distraction_minutes": 30,
            "night_winddown_time": "22:30",
            "default_break_duration_minutes": 5
        }
        self.engine = RuleEngine(config=self.config)

    def test_blacklist_triggered_only_in_deep_focus(self):
        perception = PerceptionState(current_app="com.instagram.android")
        
        # When in IDLE, blacklist should NOT fire enforcement
        commands_idle = self.engine.evaluate_all(AgentState.IDLE, perception)
        self.assertEqual(len(commands_idle), 0)

        # When in DEEP_FOCUS, blacklist MUST fire FORCE_CLOSE and NOTIFY
        commands_focus = self.engine.evaluate_all(AgentState.DEEP_FOCUS, perception)
        command_types = [c.command_type for c in commands_focus]
        self.assertIn(CommandType.FORCE_CLOSE, command_types)
        self.assertIn(CommandType.NOTIFY, command_types)

    def test_cumulative_distraction_quota(self):
        # Exceed 30 mins (1800s)
        perception = PerceptionState(daily_distraction_seconds=1850)
        commands = self.engine.evaluate_all(AgentState.IDLE, perception)
        command_types = [c.command_type for c in commands]
        self.assertIn(CommandType.NOTIFY, command_types)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_rules.py`  
Expected: `ModuleNotFoundError: No module named 'core.rule_engine'`

- [ ] **Step 3: Write minimal implementation**

```json
// config/rules.json
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

```python
# core/rule_engine.py
from abc import ABC, abstractmethod
import json
import os
import time
import uuid
from typing import List, Dict, Any, Optional
from core.state_machine import AgentState
from core.perception import PerceptionState
from core.commands import Command, CommandType
from core.memory import MemoryStore

class BaseRule(ABC):
    def __init__(self, name: str, description: str):
        self.name = name
        self.description = description

    @abstractmethod
    def evaluate(
        self,
        current_state: AgentState,
        perception: PerceptionState,
        config: Dict[str, Any],
        current_time: float
    ) -> List[Command]:
        pass

class BlacklistEnforcementRule(BaseRule):
    def __init__(self):
        super().__init__(
            name="BlacklistEnforcementRule",
            description="Force-closes blacklisted apps and alerts during Deep Focus."
        )

    def evaluate(
        self,
        current_state: AgentState,
        perception: PerceptionState,
        config: Dict[str, Any],
        current_time: float
    ) -> List[Command]:
        if current_state != AgentState.DEEP_FOCUS:
            return []
        
        blacklist = config.get("app_blacklist", [])
        if perception.current_app in blacklist:
            return [
                Command(
                    command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                    command_type=CommandType.FORCE_CLOSE,
                    timestamp=current_time,
                    payload={"app_name": perception.current_app}
                ),
                Command(
                    command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                    command_type=CommandType.NOTIFY,
                    timestamp=current_time,
                    payload={
                        "title": "Focus Mode Violation",
                        "body": f"Blocked app '{perception.current_app}' closed. Return to focus!",
                        "priority": "high"
                    }
                )
            ]
        return []

class CumulativeDistractionQuotaRule(BaseRule):
    def __init__(self):
        super().__init__(
            name="CumulativeDistractionQuotaRule",
            description="Alerts when daily distraction limit is exceeded."
        )
        self.alerted_today = False

    def evaluate(
        self,
        current_state: AgentState,
        perception: PerceptionState,
        config: Dict[str, Any],
        current_time: float
    ) -> List[Command]:
        limit_sec = config.get("max_daily_distraction_minutes", 45) * 60
        if perception.daily_distraction_seconds >= limit_sec and not self.alerted_today:
            self.alerted_today = True
            return [
                Command(
                    command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                    command_type=CommandType.NOTIFY,
                    timestamp=current_time,
                    payload={
                        "title": "Daily Distraction Quota Exceeded",
                        "body": f"You have reached {perception.daily_distraction_seconds // 60:.0f} mins of distraction today.",
                        "priority": "warning"
                    }
                )
            ]
        return []

class BreakOverstayRule(BaseRule):
    def __init__(self):
        super().__init__(
            name="BreakOverstayRule",
            description="Reminds user when short break duration has elapsed."
        )
        self.reminded = False

    def evaluate(
        self,
        current_state: AgentState,
        perception: PerceptionState,
        config: Dict[str, Any],
        current_time: float
    ) -> List[Command]:
        if current_state != AgentState.SHORT_BREAK:
            self.reminded = False
            return []
        
        break_limit_sec = config.get("default_break_duration_minutes", 5) * 60
        if perception.active_timer and perception.is_timer_expired(current_time) and not self.reminded:
            self.reminded = True
            return [
                Command(
                    command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                    command_type=CommandType.NOTIFY,
                    timestamp=current_time,
                    payload={
                        "title": "Break Over",
                        "body": "Break time has finished. Ready to resume Deep Focus?",
                        "priority": "normal"
                    }
                )
            ]
        return []

class NightWindDownRule(BaseRule):
    def __init__(self):
        super().__init__(
            name="NightWindDownRule",
            description="Suggests winding down for sleep at night."
        )
        self.prompted_today = False

    def evaluate(
        self,
        current_state: AgentState,
        perception: PerceptionState,
        config: Dict[str, Any],
        current_time: float
    ) -> List[Command]:
        if current_state not in (AgentState.IDLE, AgentState.SHORT_BREAK):
            return []
        
        local_time = time.localtime(current_time)
        target_str = config.get("night_winddown_time", "22:30")
        try:
            target_h, target_m = map(int, target_str.split(":"))
        except Exception:
            target_h, target_m = 22, 30

        if (local_time.tm_hour > target_h or (local_time.tm_hour == target_h and local_time.tm_min >= target_m)) and not self.prompted_today:
            self.prompted_today = True
            return [
                Command(
                    command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                    command_type=CommandType.NOTIFY,
                    timestamp=current_time,
                    payload={
                        "title": "Night Wind-Down",
                        "body": "It's past your wind-down time. Time to prepare for restful sleep.",
                        "priority": "normal"
                    }
                )
            ]
        return []

class RuleEngine:
    def __init__(
        self,
        config_path: str = "config/rules.json",
        config: Optional[Dict[str, Any]] = None,
        memory: Optional[MemoryStore] = None
    ):
        self.config_path = config_path
        self.config = config or self._load_config(config_path)
        self.memory = memory
        self.rules: List[BaseRule] = [
            BlacklistEnforcementRule(),
            CumulativeDistractionQuotaRule(),
            BreakOverstayRule(),
            NightWindDownRule()
        ]

    def _load_config(self, path: str) -> Dict[str, Any]:
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        return {
            "app_blacklist": ["com.instagram.android", "reddit"],
            "max_daily_distraction_minutes": 45,
            "night_winddown_time": "22:30",
            "default_break_duration_minutes": 5
        }

    def evaluate_all(
        self,
        current_state: AgentState,
        perception: PerceptionState,
        current_time: Optional[float] = None
    ) -> List[Command]:
        t = current_time or time.time()
        all_commands = []
        for rule in self.rules:
            cmds = rule.evaluate(current_state, perception, self.config, t)
            if cmds:
                all_commands.extend(cmds)
                if self.memory and rule.name == "BlacklistEnforcementRule":
                    self.memory.record_violation(
                        rule_name=rule.name,
                        app_name=perception.current_app,
                        details={"state": current_state.value},
                        timestamp=t
                    )
        return all_commands
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_rules.py`  
Expected: `Ran 2 tests in ... OK`

- [ ] **Step 5: Commit**

```bash
git add config/rules.json core/rule_engine.py tests/test_rules.py
git commit -m "feat(core): add RuleEngine and built-in rules with editable config/rules.json"
```

---

### Task 6: Actuators & 5-Stage Event Pipeline

**Files:**
- Create: `actuators/base.py`
- Create: `actuators/console_actuator.py`
- Create: `core/pipeline.py`
- Create: `tests/test_pipeline.py`

**Interfaces:**
- Consumes: All `core/` components (`events`, `commands`, `memory`, `perception`, `state_machine`, `rule_engine`)
- Produces:
  - `BaseActuator` abstract class: `handle(command: Command) -> None`
  - `ConsoleActuator(BaseActuator)`
  - `EventPipeline`
    - `post_event(event: Event) -> None`
    - `process_next(timeout: float = 0.1) -> bool`
    - `run_worker()` (loop running until `EventType.SHUTDOWN`)

- [ ] **Step 1: Write the failing test**

```python
# tests/test_pipeline.py
import unittest
import queue
import time
from core.events import Event, EventType
from core.commands import CommandType
from core.memory import MemoryStore
from core.perception import PerceptionState
from core.state_machine import StateMachine, AgentState
from core.rule_engine import RuleEngine
from core.pipeline import EventPipeline
from actuators.base import BaseActuator

class MockActuator(BaseActuator):
    def __init__(self):
        self.dispatched_commands = []

    def handle(self, command):
        self.dispatched_commands.append(command)

class TestPipeline(unittest.TestCase):
    def setUp(self):
        self.memory = MemoryStore(db_path=":memory:")
        self.perception = PerceptionState()
        self.fsm = StateMachine(memory=self.memory)
        self.rules = RuleEngine(config={"app_blacklist": ["instagram"]}, memory=self.memory)
        self.actuator = MockActuator()
        self.pipeline = EventPipeline(
            memory=self.memory,
            perception=self.perception,
            fsm=self.fsm,
            rule_engine=self.rules,
            actuators=[self.actuator]
        )

    def test_pipeline_execution_and_enforcement(self):
        # 1. Start focus session
        start_evt = Event(event_id="e1", event_type=EventType.USER_COMMAND, payload={"command": "START_FOCUS", "duration_min": 25})
        self.pipeline.process_event(start_evt)
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        # 2. Ingest blacklisted app switch
        app_evt = Event(event_id="e2", event_type=EventType.APP_FOREGROUND, payload={"app_name": "instagram"})
        self.pipeline.process_event(app_evt)

        # 3. Assert actuator received FORCE_CLOSE and NOTIFY
        types = [c.command_type for c in self.actuator.dispatched_commands]
        self.assertIn(CommandType.FORCE_CLOSE, types)
        self.assertIn(CommandType.NOTIFY, types)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_pipeline.py`  
Expected: `ModuleNotFoundError: No module named 'actuators'`

- [ ] **Step 3: Write minimal implementation**

```python
# actuators/base.py
from abc import ABC, abstractmethod
from core.commands import Command

class BaseActuator(ABC):
    @abstractmethod
    def handle(self, command: Command) -> None:
        pass
```

```python
# actuators/console_actuator.py
import sys
from core.commands import Command, CommandType
from actuators.base import BaseActuator

class ConsoleActuator(BaseActuator):
    def handle(self, command: Command) -> None:
        if command.command_type == CommandType.NOTIFY:
            title = command.payload.get("title", "ALERT")
            body = command.payload.get("body", "")
            priority = command.payload.get("priority", "normal")
            color = "\033[93m" if priority == "warning" else ("\033[91m" if priority == "high" else "\033[96m")
            reset = "\033[0m"
            print(f"\n{color}[AGENT NOTIFICATION: {title}] {body}{reset}")
            # Terminal beep on high priority if supported
            if priority == "high" and sys.platform == "win32":
                try:
                    import winsound
                    winsound.MessageBeep(winsound.MB_ICONEXCLAMATION)
                except Exception:
                    pass

        elif command.command_type == CommandType.FORCE_CLOSE:
            app_name = command.payload.get("app_name", "")
            print(f"\n\033[91m[ACTUATOR ACTION] 🛑 FORCE-CLOSING APP: {app_name}\033[0m")
```

```python
# core/pipeline.py
import queue
import time
import uuid
from typing import List, Optional
from core.events import Event, EventType
from core.commands import Command, CommandType
from core.memory import MemoryStore
from core.perception import PerceptionState
from core.state_machine import StateMachine, AgentState
from core.rule_engine import RuleEngine
from actuators.base import BaseActuator

class EventPipeline:
    def __init__(
        self,
        memory: MemoryStore,
        perception: PerceptionState,
        fsm: StateMachine,
        rule_engine: RuleEngine,
        actuators: Optional[List[BaseActuator]] = None
    ):
        self.memory = memory
        self.perception = perception
        self.fsm = fsm
        self.rule_engine = rule_engine
        self.actuators = actuators or []
        self.queue: queue.Queue[Event] = queue.Queue()
        self.running = False

    def post_event(self, event: Event) -> None:
        self.queue.put(event)

    def process_event(self, event: Event) -> None:
        # Stage 1: Persist
        self.memory.log_event(event)

        # Stage 2: Perception Update
        blacklist = self.rule_engine.config.get("app_blacklist", [])
        self.perception.update(event, blacklist=blacklist)

        # Stage 3: FSM Process & Transition Logic
        self._handle_fsm_triggers(event)

        # Stage 4: Rule Engine Evaluation
        commands = self.rule_engine.evaluate_all(
            current_state=self.fsm.current_state,
            perception=self.perception,
            current_time=event.timestamp
        )

        # If violation detected during focus, transition to DISTRACTION_DETECTED
        if any(c.command_type == CommandType.FORCE_CLOSE for c in commands):
            if self.fsm.current_state == AgentState.DEEP_FOCUS:
                self.fsm.transition_to(AgentState.DISTRACTION_DETECTED, reason="Blacklisted app violation", timestamp=event.timestamp)

        # Stage 5: Actuator Dispatch
        for cmd in commands:
            for actuator in self.actuators:
                actuator.handle(cmd)

    def _handle_fsm_triggers(self, event: Event) -> None:
        if event.event_type == EventType.USER_COMMAND:
            cmd = event.payload.get("command")
            if cmd == "START_FOCUS":
                dur = event.payload.get("duration_min", 25)
                session_id = f"sess-{uuid.uuid4().hex[:8]}"
                if self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="User initiated focus", timestamp=event.timestamp):
                    self.perception.set_timer("focus_timer", duration_sec=dur * 60, start_time=event.timestamp)
                    self.memory.start_focus_session(session_id, target_min=dur, start_time=event.timestamp)
            elif cmd == "STOP_FOCUS":
                if self.fsm.transition_to(AgentState.IDLE, reason="User stopped focus", timestamp=event.timestamp):
                    self.perception.clear_timer()
            elif cmd == "START_BREAK":
                dur = event.payload.get("duration_min", 5)
                if self.fsm.transition_to(AgentState.SHORT_BREAK, reason="User took break", timestamp=event.timestamp):
                    self.perception.set_timer("break_timer", duration_sec=dur * 60, start_time=event.timestamp)
        elif event.event_type == EventType.TICK:
            # Check if active timer expired
            if self.perception.active_timer and self.perception.is_timer_expired(event.timestamp):
                timer_id = self.perception.active_timer.get("timer_id")
                if timer_id == "focus_timer" and self.fsm.current_state in (AgentState.DEEP_FOCUS, AgentState.DISTRACTION_DETECTED):
                    self.fsm.transition_to(AgentState.SHORT_BREAK, reason="Focus timer completed", timestamp=event.timestamp)
                    break_min = self.rule_engine.config.get("default_break_duration_minutes", 5)
                    self.perception.set_timer("break_timer", duration_sec=break_min * 60, start_time=event.timestamp)
                    # Notify completion
                    for act in self.actuators:
                        act.handle(Command(
                            command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                            command_type=CommandType.NOTIFY,
                            payload={"title": "Focus Session Completed!", "body": f"Great job. Starting {break_min}m break.", "priority": "normal"}
                        ))

    def run_worker(self) -> None:
        self.running = True
        while self.running:
            try:
                event = self.queue.get(timeout=0.2)
                if event.event_type == EventType.SHUTDOWN:
                    self.running = False
                    break
                self.process_event(event)
                self.queue.task_done()
            except queue.Empty:
                continue
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_pipeline.py`  
Expected: `Ran 1 test in ... OK`

- [ ] **Step 5: Commit**

```bash
git add actuators/ core/pipeline.py tests/test_pipeline.py
git commit -m "feat(core): add Actuators and EventPipeline 5-stage sequential event processor"
```

---

### Task 7: Background Heartbeat Daemon

**Files:**
- Create: `core/heartbeat.py`
- Create: `tests/test_heartbeat.py`

**Interfaces:**
- Consumes: `core/events.py`, `core/pipeline.py`, standard library `threading`
- Produces:
  - `HeartbeatRunner(pipeline: EventPipeline, interval_sec: float = 5.0)`
    - `start()`
    - `stop()`

- [ ] **Step 1: Write the failing test**

```python
# tests/test_heartbeat.py
import unittest
import time
import queue
from core.events import Event, EventType
from core.heartbeat import HeartbeatRunner

class MockPipeline:
    def __init__(self):
        self.received = []

    def post_event(self, event):
        self.received.append(event)

class TestHeartbeat(unittest.TestCase):
    def test_heartbeat_ticks_and_stops(self):
        mock_p = MockPipeline()
        runner = HeartbeatRunner(pipeline=mock_p, interval_sec=0.1)
        runner.start()
        time.sleep(0.35)
        runner.stop()
        self.assertGreaterEqual(len(mock_p.received), 2)
        self.assertEqual(mock_p.received[0].event_type, EventType.TICK)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_heartbeat.py`  
Expected: `ModuleNotFoundError: No module named 'core.heartbeat'`

- [ ] **Step 3: Write minimal implementation**

```python
# core/heartbeat.py
import threading
import time
import uuid
from typing import Any
from core.events import Event, EventType

class HeartbeatRunner:
    def __init__(self, pipeline: Any, interval_sec: float = 5.0):
        self.pipeline = pipeline
        self.interval_sec = interval_sec
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> None:
        self.thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        self.thread.join(timeout=1.0)

    def _run(self) -> None:
        last_tick = time.time()
        while not self.stop_event.is_set():
            if self.stop_event.wait(self.interval_sec):
                break
            now = time.time()
            delta = now - last_tick
            last_tick = now
            tick_event = Event(
                event_id=f"tick-{uuid.uuid4().hex[:8]}",
                event_type=EventType.TICK,
                timestamp=now,
                payload={"delta_seconds": delta}
            )
            self.pipeline.post_event(tick_event)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_heartbeat.py`  
Expected: `Ran 1 test in ... OK`

- [ ] **Step 5: Commit**

```bash
git add core/heartbeat.py tests/test_heartbeat.py
git commit -m "feat(core): add HeartbeatRunner background ticker thread"
```

---

### Task 8: Interactive CLI Simulator Shell & Main Entrypoint

**Files:**
- Create: `cli.py`
- Create: `main.py`
- Create: `tests/test_cli.py`

**Interfaces:**
- Consumes: All modules
- Produces:
  - Interactive simulator shell supporting commands: `status`, `focus [min]`, `break [min]`, `stop`, `app <name>`, `screen <on|off>`, `tick [sec]`, `violations`, `summary`, `rules`, `help`, `quit`
  - Clean headless and interactive startup via `main.py`

- [ ] **Step 1: Write the failing test**

```python
# tests/test_cli.py
import unittest
from core.memory import MemoryStore
from core.perception import PerceptionState
from core.state_machine import StateMachine, AgentState
from core.rule_engine import RuleEngine
from core.pipeline import EventPipeline
from cli import AgentCLI

class TestCLI(unittest.TestCase):
    def setUp(self):
        self.memory = MemoryStore(db_path=":memory:")
        self.perception = PerceptionState()
        self.fsm = StateMachine(memory=self.memory)
        self.rules = RuleEngine(config={"app_blacklist": ["instagram"]}, memory=self.memory)
        self.pipeline = EventPipeline(self.memory, self.perception, self.fsm, self.rules)
        self.cli = AgentCLI(self.pipeline, self.fsm, self.perception, self.memory, self.rules)

    def test_cli_command_dispatch(self):
        output = self.cli.execute_command("status")
        self.assertIn("State:", output)

        self.cli.execute_command("focus 30")
        # Drain queue
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_cli.py`  
Expected: `ModuleNotFoundError: No module named 'cli'`

- [ ] **Step 3: Write minimal implementation**

```python
# cli.py
import sys
import time
import uuid
from typing import Optional
from core.events import Event, EventType
from core.state_machine import StateMachine, AgentState
from core.perception import PerceptionState
from core.memory import MemoryStore
from core.rule_engine import RuleEngine
from core.pipeline import EventPipeline

class AgentCLI:
    def __init__(
        self,
        pipeline: EventPipeline,
        fsm: StateMachine,
        perception: PerceptionState,
        memory: MemoryStore,
        rule_engine: RuleEngine
    ):
        self.pipeline = pipeline
        self.fsm = fsm
        self.perception = perception
        self.memory = memory
        self.rule_engine = rule_engine

    def execute_command(self, raw_input: str) -> str:
        parts = raw_input.strip().split()
        if not parts:
            return ""
        cmd = parts[0].lower()
        args = parts[1:]

        if cmd == "status":
            timer_str = "None"
            if self.perception.active_timer:
                rem = max(0.0, self.perception.active_timer["ends_at"] - time.time())
                timer_str = f"{self.perception.active_timer['timer_id']} ({rem:.0f}s remaining)"
            return (
                f"\n=== AGENT STATUS ===\n"
                f"State:        {self.fsm.current_state.value}\n"
                f"Time in state:{self.fsm.time_in_current_state():.0f}s\n"
                f"Current App:  {self.perception.current_app}\n"
                f"Screen On:    {self.perception.screen_on}\n"
                f"Active Timer: {timer_str}\n"
                f"Distraction:  {self.perception.daily_distraction_seconds / 60:.1f} mins\n"
                f"===================="
            )

        elif cmd == "focus":
            min_dur = int(args[0]) if args else 25
            evt = Event(
                event_id=f"cli-{uuid.uuid4().hex[:8]}",
                event_type=EventType.USER_COMMAND,
                payload={"command": "START_FOCUS", "duration_min": min_dur}
            )
            self.pipeline.post_event(evt)
            return f"Initiated {min_dur}m Deep Focus session."

        elif cmd == "break":
            min_dur = int(args[0]) if args else 5
            evt = Event(
                event_id=f"cli-{uuid.uuid4().hex[:8]}",
                event_type=EventType.USER_COMMAND,
                payload={"command": "START_BREAK", "duration_min": min_dur}
            )
            self.pipeline.post_event(evt)
            return f"Taking a {min_dur}m break."

        elif cmd == "stop":
            evt = Event(
                event_id=f"cli-{uuid.uuid4().hex[:8]}",
                event_type=EventType.USER_COMMAND,
                payload={"command": "STOP_FOCUS"}
            )
            self.pipeline.post_event(evt)
            return "Stopped active session."

        elif cmd == "app":
            if not args:
                return "Usage: app <app_name>"
            app_name = args[0]
            evt = Event(
                event_id=f"cli-{uuid.uuid4().hex[:8]}",
                event_type=EventType.APP_FOREGROUND,
                payload={"app_name": app_name}
            )
            self.pipeline.post_event(evt)
            return f"Simulated switch to app: {app_name}"

        elif cmd == "screen":
            if not args:
                return "Usage: screen <on|off>"
            is_on = args[0].lower() in ("on", "true", "1")
            evt = Event(
                event_id=f"cli-{uuid.uuid4().hex[:8]}",
                event_type=EventType.SCREEN_STATE,
                payload={"is_on": is_on}
            )
            self.pipeline.post_event(evt)
            return f"Simulated screen state: {'ON' if is_on else 'OFF'}"

        elif cmd == "tick":
            sec = float(args[0]) if args else 5.0
            evt = Event(
                event_id=f"cli-{uuid.uuid4().hex[:8]}",
                event_type=EventType.TICK,
                timestamp=time.time() + sec,
                payload={"delta_seconds": sec}
            )
            self.pipeline.post_event(evt)
            return f"Simulated time jump of {sec}s."

        elif cmd == "violations":
            records = self.memory.get_recent_violations(limit=10)
            if not records:
                return "No violations recorded today."
            lines = ["\nRecent Violations:"]
            for r in records:
                t_str = time.strftime("%H:%M:%S", time.localtime(r["timestamp"]))
                lines.append(f" - [{t_str}] Rule: {r['rule_name']} | App: {r['app_name']}")
            return "\n".join(lines)

        elif cmd == "rules":
            lines = ["\nActive Rules:"]
            for r in self.rule_engine.rules:
                lines.append(f" - {r.name}: {r.description}")
            lines.append(f"\nApp Blacklist: {self.rule_engine.config.get('app_blacklist', [])}")
            return "\n".join(lines)

        elif cmd == "help":
            return (
                "\nAvailable commands:\n"
                "  status              - Show current state, active app, timers, and metrics\n"
                "  focus [minutes]     - Start a Deep Focus session (default: 25)\n"
                "  break [minutes]     - Take a short break (default: 5)\n"
                "  stop                - Cancel or complete current session\n"
                "  app <app_name>      - Simulate opening an app (e.g. app instagram)\n"
                "  screen <on|off>     - Simulate turning screen on or off\n"
                "  tick [seconds]      - Fast-forward time\n"
                "  violations          - List recent violations\n"
                "  rules               - Show active rules and blacklist\n"
                "  quit / exit         - Exit simulator\n"
            )
        return f"Unknown command: '{cmd}'. Type 'help' for options."

    def run_loop(self) -> None:
        print("\n=== Productivity Agent Core Engine CLI (Phase 1) ===")
        print("Type 'help' for commands, 'quit' to exit.\n")
        while True:
            try:
                user_input = input("agent> ").strip()
                if user_input.lower() in ("quit", "exit"):
                    break
                if user_input:
                    res = self.execute_command(user_input)
                    if res:
                        print(res)
            except (KeyboardInterrupt, EOFError):
                break
        print("\nShutting down CLI...")
```

```python
# main.py
import threading
import time
import os
from core.events import Event, EventType
from core.memory import MemoryStore
from core.perception import PerceptionState
from core.state_machine import StateMachine
from core.rule_engine import RuleEngine
from core.pipeline import EventPipeline
from core.heartbeat import HeartbeatRunner
from actuators.console_actuator import ConsoleActuator
from cli import AgentCLI

def main():
    print("Initializing Productivity Agent Core (Phase 1)...")
    db_path = os.path.join("data", "agent.db")
    memory = MemoryStore(db_path=db_path)
    perception = PerceptionState()
    fsm = StateMachine(memory=memory)
    rule_engine = RuleEngine(config_path="config/rules.json", memory=memory)
    actuator = ConsoleActuator()

    pipeline = EventPipeline(
        memory=memory,
        perception=perception,
        fsm=fsm,
        rule_engine=rule_engine,
        actuators=[actuator]
    )

    # Start worker thread
    worker_thread = threading.Thread(target=pipeline.run_worker, daemon=True)
    worker_thread.start()

    # Start heartbeat ticker (every 5 seconds)
    heartbeat = HeartbeatRunner(pipeline=pipeline, interval_sec=5.0)
    heartbeat.start()

    # Launch CLI
    cli = AgentCLI(pipeline, fsm, perception, memory, rule_engine)
    try:
        cli.run_loop()
    finally:
        print("Stopping heartbeat and worker pipeline...")
        heartbeat.stop()
        pipeline.post_event(Event(event_id="shutdown", event_type=EventType.SHUTDOWN))
        worker_thread.join(timeout=1.0)
        memory.close()
        print("Agent cleanly stopped.")

if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_cli.py`  
Expected: `Ran 1 test in ... OK`

- [ ] **Step 5: Commit**

```bash
git add cli.py main.py tests/test_cli.py
git commit -m "feat(cli): add interactive CLI simulator shell and main bootstrap runner"
```

---

### Task 9: Full Test Suite Verification

**Files:**
- Test all components: `tests/`

- [ ] **Step 1: Run complete test suite**

Run: `python -m unittest discover -s tests -p "test_*.py" -v`  
Expected: All tests pass (8+ tests across 6 suites).

- [ ] **Step 2: Commit complete Phase 1 test suite passing**

```bash
git commit --allow-empty -m "test: verify entire Phase 1 test suite passes"
```
