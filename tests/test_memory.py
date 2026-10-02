import unittest
import time
import os
import tempfile
import shutil
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

        date_str = time.strftime("%Y-%m-%d", time.localtime(t0))
        summary = self.memory.get_daily_summary(date_str)
        self.assertEqual(summary["total_violations"], 1)
        self.assertEqual(summary["total_focus_seconds"], 0.0)
        self.assertEqual(summary["completed_sessions"], 0)

    def test_focus_session_lifecycle(self):
        t0 = time.time()
        self.memory.start_focus_session(session_id="s-1", target_min=25, start_time=t0)
        self.memory.end_focus_session(session_id="s-1", completed=True, end_time=t0 + 1500)
        sessions = self.memory.get_focus_sessions()
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["completed"], 1)
        self.assertEqual(sessions[0]["actual_duration_sec"], 1500)

    def test_daily_summary_comprehensive(self):
        t0 = time.time()
        date_str = time.strftime("%Y-%m-%d", time.localtime(t0))

        # Start and complete a 25 min focus session (1500 sec)
        self.memory.start_focus_session(session_id="s-focus-1", target_min=25, start_time=t0)
        self.memory.end_focus_session(session_id="s-focus-1", completed=True, end_time=t0 + 1500)

        # Start an incomplete 10 min session (ended after 300 sec, completed=False)
        self.memory.start_focus_session(session_id="s-focus-2", target_min=10, start_time=t0 + 2000)
        self.memory.end_focus_session(session_id="s-focus-2", completed=False, end_time=t0 + 2300)

        # Record violations on this day
        self.memory.record_violation(rule_name="BlacklistRule", app_name="tiktok", details={"strike": 1}, timestamp=t0 + 100)
        self.memory.record_violation(rule_name="BlacklistRule", app_name="reddit", details={"strike": 2}, timestamp=t0 + 200)

        summary = self.memory.get_daily_summary(date_str)
        self.assertEqual(summary["date"], date_str)
        self.assertEqual(summary["total_focus_seconds"], 1800.0)
        self.assertEqual(summary["completed_sessions"], 1)
        self.assertEqual(summary["sessions_completed"], 1)
        self.assertEqual(summary["total_violations"], 2)
        self.assertEqual(summary["total_sessions"], 2)
        self.assertEqual(summary["productivity_score"], 100.0)

    def test_daily_metrics_persistence(self):
        date_str = "2026-10-02"
        self.memory.record_daily_metrics(
            date_str=date_str,
            total_focus_seconds=3600.0,
            total_distraction_seconds=600.0,
            total_violations=3,
            sessions_completed=2,
            productivity_score=85.71
        )
        metrics = self.memory.get_daily_metrics(date_str)
        self.assertIsNotNone(metrics)
        self.assertEqual(metrics["date"], date_str)
        self.assertEqual(metrics["total_focus_seconds"], 3600.0)
        self.assertEqual(metrics["total_distraction_seconds"], 600.0)
        self.assertEqual(metrics["total_violations"], 3)
        self.assertEqual(metrics["sessions_completed"], 2)
        self.assertEqual(metrics["productivity_score"], 85.71)

        summary = self.memory.get_daily_summary(date_str)
        self.assertEqual(summary["total_focus_seconds"], 3600.0)
        self.assertEqual(summary["total_distraction_seconds"], 600.0)
        self.assertEqual(summary["productivity_score"], 85.71)

    def test_disk_database_directory_creation(self):
        tmp_dir = tempfile.mkdtemp()
        try:
            nested_db = os.path.join(tmp_dir, "nested", "path", "test.db")
            store = MemoryStore(db_path=nested_db)
            evt = Event(event_id="e-disk", event_type=EventType.TICK, payload={"delta": 1})
            store.log_event(evt)
            events = store.get_recent_events(limit=1)
            self.assertEqual(len(events), 1)
            store.close()
            self.assertTrue(os.path.exists(nested_db))
        finally:
            shutil.rmtree(tmp_dir, ignore_errors=True)


    def test_daily_summary_cqs_and_save_daily_metrics(self):
        t0 = time.time()
        date_str = time.strftime("%Y-%m-%d", time.localtime(t0))
        self.memory.start_focus_session(session_id="s-cqs", target_min=25, start_time=t0)
        self.memory.end_focus_session(session_id="s-cqs", completed=True, end_time=t0 + 1500)

        # 1. Pure query without side-effects (persist=False)
        summary = self.memory.get_daily_summary(date_str, persist=False)
        self.assertEqual(summary["total_focus_seconds"], 1500.0)
        # Verify daily_metrics table was NOT written to
        self.assertIsNone(self.memory.get_daily_metrics(date_str))

        # 2. Explicit save_daily_metrics call
        self.memory.save_daily_metrics(summary)
        metrics = self.memory.get_daily_metrics(date_str)
        self.assertIsNotNone(metrics)
        self.assertEqual(metrics["total_focus_seconds"], 1500.0)

        # 3. get_daily_summary with persist=True
        summary2 = self.memory.get_daily_summary(date_str, persist=True)
        metrics2 = self.memory.get_daily_metrics(date_str)
        self.assertEqual(metrics2["total_focus_seconds"], 1500.0)

    def test_get_focus_sessions_limit(self):
        t0 = time.time()
        for i in range(5):
            self.memory.start_focus_session(session_id=f"s-lim-{i}", target_min=10, start_time=t0 + i * 100)
            self.memory.end_focus_session(session_id=f"s-lim-{i}", completed=True, end_time=t0 + i * 100 + 600)

        sessions_all = self.memory.get_focus_sessions()
        self.assertEqual(len(sessions_all), 5)

        sessions_limited = self.memory.get_focus_sessions(limit=2)
        self.assertEqual(len(sessions_limited), 2)


if __name__ == "__main__":
    unittest.main()
