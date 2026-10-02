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
        self.assertIsNone(self.fsm.previous_state)
        self.assertGreater(self.fsm.state_entered_at, 0)

    def test_valid_transitions(self):
        self.assertTrue(self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="start work"))
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)
        self.assertEqual(self.fsm.previous_state, AgentState.IDLE)

        self.assertTrue(self.fsm.transition_to(AgentState.SHORT_BREAK, reason="pomodoro elapsed"))
        self.assertEqual(self.fsm.current_state, AgentState.SHORT_BREAK)
        self.assertEqual(self.fsm.previous_state, AgentState.DEEP_FOCUS)

        self.assertTrue(self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="break finished"))
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)
        self.assertEqual(self.fsm.previous_state, AgentState.SHORT_BREAK)

        self.assertTrue(self.fsm.transition_to(AgentState.IDLE, reason="manual stop"))
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)
        self.assertEqual(self.fsm.previous_state, AgentState.DEEP_FOCUS)

    def test_invalid_transition_rejection(self):
        # IDLE to SHORT_BREAK is not a valid direct transition
        self.assertFalse(self.fsm.can_transition(AgentState.SHORT_BREAK))
        self.assertFalse(self.fsm.transition_to(AgentState.SHORT_BREAK, reason="illegal jump"))
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)
        self.assertIsNone(self.fsm.previous_state)

    def test_memory_logging_on_transition(self):
        t0 = 1000.0
        self.assertTrue(self.fsm.transition_to(AgentState.DEEP_FOCUS, reason="start focus session", timestamp=t0))
        logs = self.memory.get_recent_transitions(limit=1)
        self.assertEqual(len(logs), 1)
        self.assertEqual(logs[0]["from_state"], "IDLE")
        self.assertEqual(logs[0]["to_state"], "DEEP_FOCUS")
        self.assertEqual(logs[0]["reason"], "start focus session")
        self.assertEqual(logs[0]["timestamp"], t0)

    def test_transition_without_memory(self):
        fsm_no_mem = StateMachine(memory=None)
        self.assertTrue(fsm_no_mem.transition_to(AgentState.DEEP_FOCUS, reason="start focus"))
        self.assertEqual(fsm_no_mem.current_state, AgentState.DEEP_FOCUS)
        self.assertEqual(fsm_no_mem.previous_state, AgentState.IDLE)

    def test_time_in_current_state(self):
        t0 = 1000.0
        fsm = StateMachine(start_time=t0)
        self.assertEqual(fsm.time_in_current_state(current_time=t0), 0.0)
        self.assertEqual(fsm.time_in_current_state(current_time=t0 + 45.5), 45.5)
        # In the past should clamp to 0.0
        self.assertEqual(fsm.time_in_current_state(current_time=t0 - 10.0), 0.0)

        # Transition updates state_entered_at
        t1 = 1200.0
        fsm.transition_to(AgentState.DEEP_FOCUS, reason="start", timestamp=t1)
        self.assertEqual(fsm.time_in_current_state(current_time=t1 + 60.0), 60.0)

    def test_all_state_transitions(self):
        # Test full cycles covering all states in the matrix
        fsm = StateMachine()

        # IDLE -> MORNING_PLANNING -> DEEP_FOCUS -> DISTRACTION_DETECTED -> DEEP_FOCUS -> IDLE
        self.assertTrue(fsm.transition_to(AgentState.MORNING_PLANNING, reason="morning"))
        self.assertTrue(fsm.transition_to(AgentState.DEEP_FOCUS, reason="plan done"))
        self.assertTrue(fsm.transition_to(AgentState.DISTRACTION_DETECTED, reason="distraction"))
        self.assertTrue(fsm.transition_to(AgentState.DEEP_FOCUS, reason="back to work"))
        self.assertTrue(fsm.transition_to(AgentState.IDLE, reason="done"))

        # IDLE -> SLEEP_OFFLINE -> MORNING_PLANNING -> IDLE
        self.assertTrue(fsm.transition_to(AgentState.SLEEP_OFFLINE, reason="sleep"))
        self.assertTrue(fsm.transition_to(AgentState.MORNING_PLANNING, reason="wake"))
        self.assertTrue(fsm.transition_to(AgentState.IDLE, reason="idle"))

        # SLEEP_OFFLINE -> IDLE
        self.assertTrue(fsm.transition_to(AgentState.SLEEP_OFFLINE, reason="sleep"))
        self.assertTrue(fsm.transition_to(AgentState.IDLE, reason="wake directly to idle"))

        # EVENING_REFLECTION -> SLEEP_OFFLINE and EVENING_REFLECTION -> IDLE
        fsm_reflection = StateMachine(initial_state=AgentState.EVENING_REFLECTION)
        self.assertTrue(fsm_reflection.can_transition(AgentState.SLEEP_OFFLINE))
        self.assertTrue(fsm_reflection.can_transition(AgentState.IDLE))
        self.assertFalse(fsm_reflection.can_transition(AgentState.DEEP_FOCUS))


if __name__ == "__main__":
    unittest.main()
