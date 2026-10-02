import unittest
import time
from core.state_machine import AgentState
from core.perception import PerceptionState
from core.commands import CommandType
from core.memory import MemoryStore
from core.rule_engine import (
    BaseRule,
    RuleEngine,
    BlacklistEnforcementRule,
    CumulativeDistractionQuotaRule,
    BreakOverstayRule,
    NightWindDownRule
)


class TestRuleEngine(unittest.TestCase):
    def setUp(self):
        self.config = {
            "app_blacklist": ["com.instagram.android", "reddit"],
            "max_daily_distraction_minutes": 30,
            "night_winddown_time": "22:30",
            "default_break_duration_minutes": 5
        }
        self.engine = RuleEngine(config=self.config)

    def test_base_rule_is_abstract(self):
        with self.assertRaises(TypeError):
            BaseRule("test", "test description")

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

        # Non-blacklisted app in DEEP_FOCUS should NOT trigger
        safe_perception = PerceptionState(current_app="com.android.calculator2")
        commands_safe = self.engine.evaluate_all(AgentState.DEEP_FOCUS, safe_perception)
        self.assertEqual(len(commands_safe), 0)

    def test_blacklist_records_violation_in_memory(self):
        memory = MemoryStore(":memory:")
        engine = RuleEngine(config=self.config, memory=memory)
        perception = PerceptionState(current_app="reddit")

        commands = engine.evaluate_all(AgentState.DEEP_FOCUS, perception, current_time=1000.0)
        self.assertEqual(len(commands), 2)

        violations = memory.get_recent_violations()
        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0]["rule_name"], "BlacklistEnforcementRule")
        self.assertEqual(violations[0]["app_name"], "reddit")
        memory.close()

    def test_cumulative_distraction_quota(self):
        # Under limit (30 mins = 1800s)
        perception_under = PerceptionState(daily_distraction_seconds=1700)
        commands_under = self.engine.evaluate_all(AgentState.IDLE, perception_under)
        self.assertEqual(len(commands_under), 0)

        # Exceed 30 mins (1800s)
        perception = PerceptionState(daily_distraction_seconds=1850)
        commands = self.engine.evaluate_all(AgentState.IDLE, perception)
        command_types = [c.command_type for c in commands]
        self.assertIn(CommandType.NOTIFY, command_types)
        notify_cmd = next(c for c in commands if c.command_type == CommandType.NOTIFY)
        self.assertEqual(notify_cmd.payload["title"], "Daily Distraction Quota Exceeded")

        # Second evaluation should not trigger again due to alerted_today flag
        commands_second = self.engine.evaluate_all(AgentState.IDLE, perception)
        notify_types_second = [c.command_type for c in commands_second if c.payload.get("title") == "Daily Distraction Quota Exceeded"]
        self.assertEqual(len(notify_types_second), 0)

    def test_break_overstay_rule(self):
        perception = PerceptionState()
        now = 1000.0
        # Start a 5 min break timer (ends at 1300)
        perception.set_timer("break-timer", duration_sec=300, start_time=now)

        # Before expiry in SHORT_BREAK -> no command
        cmds_before = self.engine.evaluate_all(AgentState.SHORT_BREAK, perception, current_time=now + 100)
        self.assertEqual(len(cmds_before), 0)

        # After expiry in SHORT_BREAK -> should notify Break Over
        cmds_after = self.engine.evaluate_all(AgentState.SHORT_BREAK, perception, current_time=now + 301)
        self.assertEqual(len(cmds_after), 1)
        self.assertEqual(cmds_after[0].command_type, CommandType.NOTIFY)
        self.assertEqual(cmds_after[0].payload["title"], "Break Over")

        # Evaluated again in SHORT_BREAK -> should not notify again (reminded is True)
        cmds_again = self.engine.evaluate_all(AgentState.SHORT_BREAK, perception, current_time=now + 310)
        self.assertEqual(len(cmds_again), 0)

        # Transition away resets reminded
        self.engine.evaluate_all(AgentState.DEEP_FOCUS, perception, current_time=now + 320)
        # If back in SHORT_BREAK and expired -> should notify again
        cmds_resumed = self.engine.evaluate_all(AgentState.SHORT_BREAK, perception, current_time=now + 330)
        self.assertEqual(len(cmds_resumed), 1)
        self.assertEqual(cmds_resumed[0].payload["title"], "Break Over")

    def test_night_wind_down_rule(self):
        # 22:30 is winddown time in config
        # Create a timestamp at 22:35 local time
        now_struct = time.localtime()
        # Create timestamp for today at 22:35:00
        winddown_target = time.mktime((
            now_struct.tm_year,
            now_struct.tm_mon,
            now_struct.tm_mday,
            22,
            35,
            0,
            now_struct.tm_wday,
            now_struct.tm_yday,
            now_struct.tm_isdst
        ))
        early_target = time.mktime((
            now_struct.tm_year,
            now_struct.tm_mon,
            now_struct.tm_mday,
            21,
            0,
            0,
            now_struct.tm_wday,
            now_struct.tm_yday,
            now_struct.tm_isdst
        ))

        perception = PerceptionState()
        # Early time: should not trigger
        cmds_early = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=early_target)
        self.assertEqual(len(cmds_early), 0)

        # In DEEP_FOCUS: should not trigger even if late
        cmds_focus = self.engine.evaluate_all(AgentState.DEEP_FOCUS, perception, current_time=winddown_target)
        self.assertEqual(len(cmds_focus), 0)

        # In IDLE at 22:35: should trigger
        cmds_late = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=winddown_target)
        self.assertEqual(len(cmds_late), 1)
        self.assertEqual(cmds_late[0].payload["title"], "Night Wind-Down")

        # Second evaluation should not repeat
        cmds_late_repeat = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=winddown_target + 60)
        self.assertEqual(len(cmds_late_repeat), 0)

    def test_cumulative_distraction_quota_midnight_reset(self):
        # Day 1
        t1 = 1700000000.0
        perception = PerceptionState(daily_distraction_seconds=1850)
        cmds_day1 = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=t1)
        self.assertEqual(len(cmds_day1), 1)
        self.assertEqual(cmds_day1[0].payload["title"], "Daily Distraction Quota Exceeded")

        # Later same day -> suppressed
        cmds_day1_later = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=t1 + 3600)
        self.assertEqual(len(cmds_day1_later), 0)

        # Day 2 (+24h) -> should alert again for the new day
        t2 = t1 + 86400.0
        cmds_day2 = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=t2)
        self.assertEqual(len(cmds_day2), 1)
        self.assertEqual(cmds_day2[0].payload["title"], "Daily Distraction Quota Exceeded")

    def test_night_wind_down_midnight_reset(self):
        now_struct = time.localtime()
        day1_winddown = time.mktime((
            now_struct.tm_year,
            now_struct.tm_mon,
            now_struct.tm_mday,
            22,
            35,
            0,
            now_struct.tm_wday,
            now_struct.tm_yday,
            now_struct.tm_isdst
        ))
        perception = PerceptionState()

        # Day 1 trigger
        cmds_day1 = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=day1_winddown)
        self.assertEqual(len(cmds_day1), 1)
        self.assertEqual(cmds_day1[0].payload["title"], "Night Wind-Down")

        # Day 1 later -> suppressed
        cmds_day1_later = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=day1_winddown + 600)
        self.assertEqual(len(cmds_day1_later), 0)

        # Day 2 (+24h) -> should trigger again
        day2_winddown = day1_winddown + 86400.0
        cmds_day2 = self.engine.evaluate_all(AgentState.IDLE, perception, current_time=day2_winddown)
        self.assertEqual(len(cmds_day2), 1)
        self.assertEqual(cmds_day2[0].payload["title"], "Night Wind-Down")

    def test_rule_engine_loads_from_config_file(self):
        import tempfile
        import json
        with tempfile.NamedTemporaryFile("w", delete=False, suffix=".json", encoding="utf-8") as f:
            json.dump({
                "app_blacklist": ["custom.app"],
                "max_daily_distraction_minutes": 10
            }, f)
            temp_path = f.name

        try:
            custom_engine = RuleEngine(config_path=temp_path)
            self.assertEqual(custom_engine.config["app_blacklist"], ["custom.app"])
            self.assertEqual(custom_engine.config["max_daily_distraction_minutes"], 10)
        finally:
            import os
            if os.path.exists(temp_path):
                os.remove(temp_path)


if __name__ == "__main__":
    unittest.main()

