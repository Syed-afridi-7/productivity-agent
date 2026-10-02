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
    """5-stage sequential event pipeline coordinating perception, state, rules, memory, and actuators."""

    def __init__(
        self,
        memory: Optional[MemoryStore] = None,
        perception: Optional[PerceptionState] = None,
        fsm: Optional[StateMachine] = None,
        rule_engine: Optional[RuleEngine] = None,
        actuators: Optional[List[BaseActuator]] = None
    ) -> None:
        self.memory = memory or MemoryStore(db_path=":memory:")
        self.perception = perception or PerceptionState()
        self.fsm = fsm or StateMachine(memory=self.memory)
        self.rule_engine = rule_engine or RuleEngine(memory=self.memory)
        self.actuators: List[BaseActuator] = actuators if actuators is not None else []
        self.queue: queue.Queue[Event] = queue.Queue()
        self.running: bool = False
        self.current_session_id: Optional[str] = None

    def post_event(self, event: Event) -> None:
        """Enqueue an incoming event for asynchronous pipeline processing."""
        self.queue.put(event)

    def process_next(self, timeout: float = 0.1) -> bool:
        """Process a single event from the queue within timeout seconds.

        Returns True if an event was processed, False if queue was empty,
        timed out, or if a SHUTDOWN event was processed.
        """
        try:
            event = self.queue.get(timeout=timeout)
        except queue.Empty:
            return False

        if event.event_type == EventType.SHUTDOWN:
            self.running = False
            self.queue.task_done()
            return False

        self.process_event(event)
        self.queue.task_done()
        return True

    def process_event(self, event: Event) -> None:
        """Execute the 5-stage sequential event processing lifecycle.

        Stage 1: Persist event into MemoryStore.
        Stage 2: Update PerceptionState.
        Stage 3: FSM transition handling based on event triggers.
        Stage 4: RuleEngine evaluation and potential distraction state transition.
        Stage 5: Actuator command dispatch.
        """
        # Stage 1: Persist
        if self.memory:
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
                self.fsm.transition_to(
                    AgentState.DISTRACTION_DETECTED,
                    reason="Blacklisted app violation",
                    timestamp=event.timestamp
                )

        # Stage 5: Actuator Dispatch
        for cmd in commands:
            for actuator in self.actuators:
                actuator.handle(cmd)

    def _handle_fsm_triggers(self, event: Event) -> None:
        """Evaluate events against FSM state transitions and session tracking."""
        if event.event_type == EventType.USER_COMMAND:
            cmd = event.payload.get("command")
            if cmd in ("START_FOCUS", "RESUME_FOCUS"):
                dur = event.payload.get("duration_min", 25)
                session_id = event.payload.get("session_id") or f"sess-{uuid.uuid4().hex[:8]}"

                if self.fsm.current_state == AgentState.DISTRACTION_DETECTED:
                    self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="User resumed focus", timestamp=event.timestamp)
                elif self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="User initiated focus", timestamp=event.timestamp):
                    self.current_session_id = session_id
                    self.perception.set_timer("focus_timer", duration_sec=int(dur * 60), start_time=event.timestamp)
                    if self.memory:
                        self.memory.start_focus_session(session_id, target_min=int(dur), start_time=event.timestamp)

            elif cmd == "STOP_FOCUS":
                if self.fsm.transition_to(AgentState.IDLE, reason="User stopped focus", timestamp=event.timestamp):
                    self.perception.clear_timer()
                    if self.current_session_id and self.memory:
                        self.memory.end_focus_session(self.current_session_id, completed=False, end_time=event.timestamp)
                        self.current_session_id = None

            elif cmd == "START_BREAK":
                dur = event.payload.get("duration_min", 5)
                if self.fsm.transition_to(AgentState.SHORT_BREAK, reason="User took break", timestamp=event.timestamp):
                    self.perception.set_timer("break_timer", duration_sec=int(dur * 60), start_time=event.timestamp)

            elif cmd == "STOP_BREAK":
                if self.fsm.transition_to(AgentState.IDLE, reason="User stopped break", timestamp=event.timestamp):
                    self.perception.clear_timer()

        elif event.event_type in (EventType.TICK, EventType.TIMER_EXPIRED):
            # Check if active timer expired or explicit TIMER_EXPIRED event
            is_expired = False
            timer_id = None
            if self.perception.active_timer and self.perception.is_timer_expired(event.timestamp):
                is_expired = True
                timer_id = self.perception.active_timer.get("timer_id")
            elif event.event_type == EventType.TIMER_EXPIRED:
                is_expired = True
                timer_id = event.payload.get("timer_id") or (
                    self.perception.active_timer.get("timer_id") if self.perception.active_timer else None
                )

            if is_expired and timer_id == "focus_timer":
                if self.fsm.current_state in (AgentState.DEEP_FOCUS, AgentState.DISTRACTION_DETECTED):
                    if self.current_session_id and self.memory:
                        self.memory.end_focus_session(self.current_session_id, completed=True, end_time=event.timestamp)
                        self.current_session_id = None

                    if self.fsm.current_state == AgentState.DISTRACTION_DETECTED:
                        self.fsm.transition_to(
                            AgentState.DEEP_FOCUS,
                            reason="Focus timer completed while in distraction",
                            timestamp=event.timestamp
                        )

                    if self.fsm.transition_to(AgentState.SHORT_BREAK, reason="Focus timer completed", timestamp=event.timestamp):
                        break_min = self.rule_engine.config.get("default_break_duration_minutes", 5)
                        self.perception.set_timer("break_timer", duration_sec=int(break_min * 60), start_time=event.timestamp)
                        # Notify completion
                        completion_cmd = Command(
                            command_id=f"cmd-{uuid.uuid4().hex[:8]}",
                            command_type=CommandType.NOTIFY,
                            timestamp=event.timestamp,
                            payload={
                                "title": "Focus Session Completed!",
                                "body": f"Great job. Starting {break_min}m break.",
                                "priority": "normal"
                            }
                        )
                        for act in self.actuators:
                            act.handle(completion_cmd)

        elif event.event_type == EventType.APP_FOREGROUND:
            if self.fsm.current_state == AgentState.DISTRACTION_DETECTED:
                app = event.payload.get("app_name")
                blacklist = self.rule_engine.config.get("app_blacklist", [])
                if app and app not in blacklist:
                    self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="Allowed app in foreground", timestamp=event.timestamp)

    def run_worker(self) -> None:
        """Run the worker loop continuously until a SHUTDOWN event is received."""
        self.running = True
        while self.running:
            try:
                event = self.queue.get(timeout=0.2)
                if event.event_type == EventType.SHUTDOWN:
                    self.running = False
                    self.queue.task_done()
                    break
                self.process_event(event)
                self.queue.task_done()
            except queue.Empty:
                continue
