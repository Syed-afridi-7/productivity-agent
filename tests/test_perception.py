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

    def test_clear_timer(self):
        state = PerceptionState()
        now = time.time()
        state.set_timer("break_timer", duration_sec=5, start_time=now)
        self.assertIsNotNone(state.active_timer)
        state.clear_timer()
        self.assertIsNone(state.active_timer)
        self.assertFalse(state.is_timer_expired(now + 10))

    def test_no_distraction_when_screen_off(self):
        state = PerceptionState()
        evt_app = Event(event_id="e1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "instagram"})
        state.update(evt_app, blacklist=["instagram"])

        # Turn screen off
        evt_screen = Event(event_id="e2", event_type=EventType.SCREEN_STATE, payload={"is_on": False})
        state.update(evt_screen, blacklist=["instagram"])

        # Tick with screen off
        tick_evt = Event(event_id="e3", event_type=EventType.TICK, payload={"delta_seconds": 10})
        state.update(tick_evt, blacklist=["instagram"])
        self.assertEqual(state.daily_distraction_seconds, 0.0)

    def test_no_distraction_for_whitelisted_app(self):
        state = PerceptionState()
        evt_app = Event(event_id="e1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "vscode"})
        state.update(evt_app, blacklist=["instagram"])

        tick_evt = Event(event_id="e2", event_type=EventType.TICK, payload={"delta_seconds": 10})
        state.update(tick_evt, blacklist=["instagram"])
        self.assertEqual(state.daily_distraction_seconds, 0.0)

    def test_tick_delta_fallback_from_timestamp(self):
        state = PerceptionState()
        state.current_app = "instagram"
        t0 = time.time()
        state.last_tick_time = t0

        # Tick without delta_seconds payload
        tick_evt = Event(event_id="e1", event_type=EventType.TICK, timestamp=t0 + 7, payload={})
        state.update(tick_evt, blacklist=["instagram"])
        self.assertAlmostEqual(state.daily_distraction_seconds, 7.0, places=2)

if __name__ == "__main__":
    unittest.main()
