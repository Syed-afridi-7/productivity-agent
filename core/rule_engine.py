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
        self.config = config if config is not None else self._load_config(config_path)
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
        t = current_time if current_time is not None else time.time()
        all_commands: List[Command] = []
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
