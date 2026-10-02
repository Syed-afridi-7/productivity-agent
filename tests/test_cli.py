import io
import sys
import unittest
from unittest.mock import patch, MagicMock

from core.events import Event, EventType
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

    def test_cli_command_dispatch_status_and_focus(self):
        output = self.cli.execute_command("status")
        self.assertIn("=== AGENT STATUS ===", output)
        self.assertIn("State:", output)

        res = self.cli.execute_command("focus 30")
        self.assertIn("Initiated 30m Deep Focus session.", res)
        # Drain queue
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

    def test_cli_focus_default_and_stop(self):
        res = self.cli.execute_command("focus")
        self.assertIn("Initiated 25m Deep Focus session.", res)
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertEqual(self.fsm.current_state, AgentState.DEEP_FOCUS)

        res_stop = self.cli.execute_command("stop")
        self.assertIn("Stopped active session.", res_stop)
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertEqual(self.fsm.current_state, AgentState.IDLE)

    def test_cli_break_default_and_custom(self):
        self.fsm.current_state = AgentState.DEEP_FOCUS
        res = self.cli.execute_command("break")
        self.assertIn("Taking a 5m break.", res)
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertEqual(self.fsm.current_state, AgentState.SHORT_BREAK)

        res_custom = self.cli.execute_command("break 15")
        self.assertIn("Taking a 15m break.", res_custom)

    def test_cli_app_command(self):
        usage = self.cli.execute_command("app")
        self.assertEqual(usage, "Usage: app <app_name>")

        res = self.cli.execute_command("app chrome")
        self.assertIn("Simulated switch to app: chrome", res)
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertEqual(self.perception.current_app, "chrome")

    def test_cli_screen_command(self):
        usage = self.cli.execute_command("screen")
        self.assertEqual(usage, "Usage: screen <on|off>")

        res_off = self.cli.execute_command("screen off")
        self.assertIn("Simulated screen state: OFF", res_off)
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertFalse(self.perception.screen_on)

        res_on = self.cli.execute_command("screen on")
        self.assertIn("Simulated screen state: ON", res_on)
        self.pipeline.process_event(self.pipeline.queue.get())
        self.assertTrue(self.perception.screen_on)

    def test_cli_tick_command(self):
        res = self.cli.execute_command("tick")
        self.assertIn("Simulated time jump of 5.0s.", res)
        event = self.pipeline.queue.get()
        self.assertEqual(event.event_type, EventType.TICK)
        self.assertEqual(event.payload.get("delta_seconds"), 5.0)

        res_custom = self.cli.execute_command("tick 10.5")
        self.assertIn("Simulated time jump of 10.5s.", res_custom)
        event2 = self.pipeline.queue.get()
        self.assertEqual(event2.payload.get("delta_seconds"), 10.5)

    def test_cli_violations_command(self):
        empty_res = self.cli.execute_command("violations")
        self.assertIn("No violations recorded today.", empty_res)

        self.memory.record_violation("BlacklistRule", "instagram", {"reason": "test"})
        res = self.cli.execute_command("violations")
        self.assertIn("Recent Violations:", res)
        self.assertIn("BlacklistRule", res)
        self.assertIn("instagram", res)

    def test_cli_summary_command(self):
        # Initial empty summary
        res = self.cli.execute_command("summary")
        self.assertIn("=== DAILY SUMMARY", res)
        self.assertIn("Total Focus Time:", res)
        self.assertIn("Total Distraction Time:", res)
        self.assertIn("Sessions Completed:", res)
        self.assertIn("Total Violations:", res)
        self.assertIn("Productivity Score:", res)

        # Seed metrics into memory and test summary with date argument
        self.memory.record_daily_metrics(
            date_str="2026-10-02",
            total_focus_seconds=1500.0,
            total_distraction_seconds=300.0,
            total_violations=2,
            sessions_completed=1,
            productivity_score=83.33
        )
        res_date = self.cli.execute_command("summary 2026-10-02")
        self.assertIn("=== DAILY SUMMARY (2026-10-02) ===", res_date)
        self.assertIn("25.0 mins (1500s)", res_date)
        self.assertIn("5.0 mins (300s)", res_date)
        self.assertIn("Total Violations:       2", res_date)
        self.assertIn("Productivity Score:     83.3%", res_date)

    def test_cli_rules_and_help(self):
        rules_res = self.cli.execute_command("rules")
        self.assertIn("Active Rules:", rules_res)
        self.assertIn("App Blacklist: ['instagram']", rules_res)

        help_res = self.cli.execute_command("help")
        self.assertIn("Available commands:", help_res)
        self.assertIn("status", help_res)
        self.assertIn("focus", help_res)
        self.assertIn("break", help_res)
        self.assertIn("stop", help_res)
        self.assertIn("app", help_res)
        self.assertIn("screen", help_res)
        self.assertIn("tick", help_res)
        self.assertIn("violations", help_res)
        self.assertIn("summary", help_res)
        self.assertIn("rules", help_res)
        self.assertIn("quit", help_res)

    def test_cli_empty_unknown_and_quit_commands(self):
        empty_res = self.cli.execute_command("")
        self.assertEqual(empty_res, "")

        unknown_res = self.cli.execute_command("unknown_xyz")
        self.assertIn("Unknown command: 'unknown_xyz'", unknown_res)

        quit_res = self.cli.execute_command("quit")
        self.assertIn("Exiting", quit_res)

        exit_res = self.cli.execute_command("exit")
        self.assertIn("Exiting", exit_res)

    def test_cli_status_with_active_timer(self):
        self.perception.set_timer("focus_timer", duration_sec=120)
        status_res = self.cli.execute_command("status")
        self.assertIn("Active Timer: focus_timer", status_res)

    def test_cli_run_loop(self):
        inputs = ["status", "help", "quit"]
        with patch("builtins.input", side_effect=inputs):
            with patch("sys.stdout", new=io.StringIO()) as fake_out:
                self.cli.run_loop()
                output = fake_out.getvalue()
                self.assertIn("Productivity Agent Core Engine CLI", output)
                self.assertIn("Shutting down CLI...", output)

    def test_main_bootstrap(self):
        import main
        with patch("cli.AgentCLI.run_loop", return_value=None):
            with patch("sys.stdout", new=io.StringIO()) as fake_out:
                main.main()
                output = fake_out.getvalue()
                self.assertIn("Initializing Productivity Agent Core", output)
                self.assertIn("Agent cleanly stopped.", output)


if __name__ == "__main__":
    unittest.main()
