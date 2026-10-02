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
    """Interactive command-line simulator shell for the productivity agent core."""

    def __init__(
        self,
        pipeline: EventPipeline,
        fsm: StateMachine,
        perception: PerceptionState,
        memory: MemoryStore,
        rule_engine: RuleEngine
    ) -> None:
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

        elif cmd == "summary":
            target_date = args[0] if args else None
            summary = self.memory.get_daily_summary(target_date)
            focus_mins = summary["total_focus_seconds"] / 60.0
            distraction_mins = summary["total_distraction_seconds"] / 60.0
            return (
                f"\n=== DAILY SUMMARY ({summary['date']}) ===\n"
                f"Total Focus Time:       {focus_mins:.1f} mins ({summary['total_focus_seconds']:.0f}s)\n"
                f"Total Distraction Time: {distraction_mins:.1f} mins ({summary['total_distraction_seconds']:.0f}s)\n"
                f"Sessions Completed:     {summary['sessions_completed']} / {summary['total_sessions']}\n"
                f"Total Violations:       {summary['total_violations']}\n"
                f"Productivity Score:     {summary['productivity_score']:.1f}%\n"
                f"=========================================="
            )

        elif cmd == "rules":
            lines = ["\nActive Rules:"]
            for r in self.rule_engine.rules:
                lines.append(f" - {r.name}: {r.description}")
            lines.append(f"\nApp Blacklist: {self.rule_engine.config.get('app_blacklist', [])}")
            return "\n".join(lines)

        elif cmd in ("quit", "exit"):
            return "Exiting simulator..."

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
                "  summary [date]      - Show daily productivity summary (default: today)\n"
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
