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
    """Finite State Machine governing agent lifecycle and state transitions."""

    TRANSITIONS: Dict[AgentState, Set[AgentState]] = {
        AgentState.IDLE: {AgentState.DEEP_FOCUS, AgentState.MORNING_PLANNING, AgentState.SLEEP_OFFLINE},
        AgentState.MORNING_PLANNING: {AgentState.IDLE, AgentState.DEEP_FOCUS},
        AgentState.DEEP_FOCUS: {AgentState.SHORT_BREAK, AgentState.DISTRACTION_DETECTED, AgentState.IDLE},
        AgentState.SHORT_BREAK: {AgentState.DEEP_FOCUS, AgentState.IDLE},
        AgentState.DISTRACTION_DETECTED: {AgentState.DEEP_FOCUS, AgentState.IDLE},
        AgentState.EVENING_REFLECTION: {AgentState.SLEEP_OFFLINE, AgentState.IDLE},
        AgentState.SLEEP_OFFLINE: {AgentState.MORNING_PLANNING, AgentState.IDLE}
    }

    def __init__(
        self,
        memory: Optional[MemoryStore] = None,
        initial_state: AgentState = AgentState.IDLE,
        start_time: Optional[float] = None
    ) -> None:
        self.current_state: AgentState = initial_state
        self.previous_state: Optional[AgentState] = None
        self.state_entered_at: float = start_time if start_time is not None else time.time()
        self.memory = memory

    def can_transition(self, new_state: AgentState) -> bool:
        """Check whether transition to new_state is valid from current_state."""
        allowed = self.TRANSITIONS.get(self.current_state, set())
        return new_state in allowed

    def transition_to(self, new_state: AgentState, reason: str, timestamp: Optional[float] = None) -> bool:
        """Attempt to transition to new_state with reason, optionally logging to MemoryStore.

        Returns True if transition succeeded, False if rejected by transition matrix.
        """
        if not self.can_transition(new_state):
            return False

        t = timestamp if timestamp is not None else time.time()
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
        """Return elapsed seconds in current_state, clamped to at least 0.0."""
        now = current_time if current_time is not None else time.time()
        return max(0.0, now - self.state_entered_at)
