import io
import queue
import sys
import threading
import time
import unittest
from unittest.mock import patch

from core.commands import Command, CommandType
from core.events import Event, EventType
from core.memory import MemoryStore
from core.perception import PerceptionState
from core.rule_engine import RuleEngine
from core.state_machine import AgentState, StateMachine
from actuators.base import BaseActuator
from actuators.console_actuator import ConsoleActuator
from core.pipeline import EventPipeline


class MockActuator(BaseActuator):
    def __init__(self):
        self.dispatched_commands = []

    def handle(self, command: Command) -> None:
        self.dispatched_commands.append(command)


class TestPipeline(unittest.TestCase):
    def setUp(self):
        self.memory = MemoryStore(db_path=":memory:")
        self.perception = PerceptionState()
        self.fsm = StateMachine(memory=self.memory)
        self.rules = RuleEngine(config={"app_blacklist": ["instagram"], "default_break_duration_minutes": 5}, memory=self.memory)
        self.actuator = MockActuator()
        self.pipeline = EventPipeline(
            memory=self.memory,
            perception=self.perception,
            fsm=self.fsm,
            rule_engine=self.rules,
            actuators=[self.actuator]
        )

    def tearDown(self):
        self.memory.close()

    def test_pipeline_execution_and_enforcement(self):
        """Test starting focus session and enforcing app blacklist with force close."""
        t0 = 1000.0
        # 1. Start focus session
        start_evt = Event(
            event_id="e1",
            event_type=EventType.USER_COMMAND,
            timestamp=t0,
            payload={"command": "START_FOCUS", "duration_min": 25, "session_id": "sess-test-1"}
        )
        self.pipeline.process_event(start_evt)
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)
        self.assertIsNotNone(self.perception.active_timer)
        self.assertEqual(self.perception.active_timer["timer_id"], "focus_timer")

        # Verify session logged in memory
        sessions = self.memory.get_focus_sessions()
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["session_id"], "sess-test-1")
        self.assertEqual(sessions[0]["completed"], 0)

        # 2. Ingest blacklisted app switch
        app_evt = Event(
            event_id="e2",
            event_type=EventType.APP_FOREGROUND,
            timestamp=t0 + 10.0,
            payload={"app_name": "instagram"}
        )
        self.pipeline.process_event(app_evt)

        # 3. Assert actuator received FORCE_CLOSE and NOTIFY
        types = [c.command_type for c in self.actuator.dispatched_commands]
        self.assertIn(CommandType.FORCE_CLOSE, types)
        self.assertIn(CommandType.NOTIFY, types)
        self.assertEqual(self.fsm.current_state, AgentState.DISTRACTION_DETECTED)

        # Verify violation recorded in memory
        violations = self.memory.get_recent_violations()
        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0]["app_name"], "instagram")

    def test_session_stop(self):
        """Test stopping a focus session cleanly transitions to IDLE and clears timer."""
        t0 = 1000.0
        start_evt = Event(
            event_id="e1",
            event_type=EventType.USER_COMMAND,
            timestamp=t0,
            payload={"command": "START_FOCUS", "duration_min": 25, "session_id": "sess-test-stop"}
        )
        self.pipeline.process_event(start_evt)
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        stop_evt = Event(
            event_id="e2",
            event_type=EventType.USER_COMMAND,
            timestamp=t0 + 300.0,
            payload={"command": "STOP_FOCUS"}
        )
        self.pipeline.process_event(stop_evt)
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)
        self.assertIsNone(self.perception.active_timer)

        # Session should be marked not completed in memory
        sessions = self.memory.get_focus_sessions()
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["completed"], 0)
        self.assertIsNotNone(sessions[0]["end_time"])

    def test_timer_expiration(self):
        """Test TICK event expiring focus timer initiates SHORT_BREAK and notifies."""
        t0 = 1000.0
        start_evt = Event(
            event_id="e1",
            event_type=EventType.USER_COMMAND,
            timestamp=t0,
            payload={"command": "START_FOCUS", "duration_min": 25, "session_id": "sess-test-expire"}
        )
        self.pipeline.process_event(start_evt)
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        # TICK after 25 minutes (1500 seconds)
        tick_evt = Event(
            event_id="e2",
            event_type=EventType.TICK,
            timestamp=t0 + 1500.0,
            payload={"delta_seconds": 1.0}
        )
        self.pipeline.process_event(tick_evt)

        # State should now be SHORT_BREAK
        self.assertEqual(self.fsm.current_state, AgentState.SHORT_BREAK)
        self.assertIsNotNone(self.perception.active_timer)
        self.assertEqual(self.perception.active_timer["timer_id"], "break_timer")

        # Session marked completed in memory
        sessions = self.memory.get_focus_sessions()
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["completed"], 1)

        # Notification command dispatched
        notify_cmds = [c for c in self.actuator.dispatched_commands if c.command_type == CommandType.NOTIFY]
        self.assertTrue(any("Completed" in c.payload.get("title", "") for c in notify_cmds))

    def test_process_next_stepping(self):
        """Test process_next steps single events from queue."""
        t0 = 1000.0
        # Initially empty queue returns False
        self.assertFalse(self.pipeline.process_next(timeout=0.01))

        # Post an event
        evt = Event(
            event_id="e1",
            event_type=EventType.USER_COMMAND,
            timestamp=t0,
            payload={"command": "START_FOCUS", "duration_min": 25}
        )
        self.pipeline.post_event(evt)

        # Process next should return True and transition FSM
        handled = self.pipeline.process_next(timeout=0.1)
        self.assertTrue(handled)
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        # Queue should now be empty
        self.assertFalse(self.pipeline.process_next(timeout=0.01))

    def test_run_worker_and_shutdown(self):
        """Test worker loop processes events and shuts down cleanly on SHUTDOWN event."""
        worker_thread = threading.Thread(target=self.pipeline.run_worker, daemon=True)
        worker_thread.start()

        # Post start focus event
        self.pipeline.post_event(Event(
            event_id="e1",
            event_type=EventType.USER_COMMAND,
            timestamp=1000.0,
            payload={"command": "START_FOCUS", "duration_min": 25}
        ))

        # Wait until state changes to DEEP_FOCUS
        timeout_at = time.time() + 2.0
        while self.fsm.current_state != AgentState.DEEP_FOCUS and time.time() < timeout_at:
            time.sleep(0.02)
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        # Post SHUTDOWN event
        self.pipeline.post_event(Event(
            event_id="e2",
            event_type=EventType.SHUTDOWN,
            timestamp=1001.0
        ))

        worker_thread.join(timeout=2.0)
        self.assertFalse(worker_thread.is_alive())
        self.assertFalse(self.pipeline.running)

    def test_console_actuator(self):
        """Test ConsoleActuator prints notification and force-close messages without error."""
        actuator = ConsoleActuator()
        cmd_notify = Command(
            command_id="c1",
            command_type=CommandType.NOTIFY,
            payload={"title": "Test Title", "body": "Test Body", "priority": "warning"}
        )
        cmd_close = Command(
            command_id="c2",
            command_type=CommandType.FORCE_CLOSE,
            payload={"app_name": "distraction_app"}
        )

        stdout_buf = io.StringIO()
        with patch("sys.stdout", stdout_buf):
            actuator.handle(cmd_notify)
            actuator.handle(cmd_close)

        output = stdout_buf.getvalue()
        self.assertIn("AGENT NOTIFICATION: Test Title", output)
        self.assertIn("FORCE-CLOSING APP: distraction_app", output)


if __name__ == "__main__":
    unittest.main()
