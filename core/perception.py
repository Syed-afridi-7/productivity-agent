from dataclasses import dataclass, field
import time
from typing import Optional, List
from core.events import Event, EventType

@dataclass
class PerceptionState:
    current_app: str = "launcher"
    screen_on: bool = True
    last_screen_toggle_time: float = field(default_factory=time.time)
    daily_distraction_seconds: float = 0.0
    daily_focus_seconds: float = 0.0
    active_timer: Optional[dict] = None  # {"timer_id": str, "started_at": float, "duration_sec": int, "ends_at": float}
    last_tick_time: float = field(default_factory=time.time)

    def set_timer(self, timer_id: str, duration_sec: int, start_time: Optional[float] = None) -> None:
        t0 = start_time if start_time is not None else time.time()
        self.active_timer = {
            "timer_id": timer_id,
            "started_at": t0,
            "duration_sec": duration_sec,
            "ends_at": t0 + duration_sec,
        }

    def clear_timer(self) -> None:
        self.active_timer = None

    def is_timer_expired(self, current_time: Optional[float] = None) -> bool:
        if not self.active_timer:
            return False
        now = current_time if current_time is not None else time.time()
        return now >= self.active_timer["ends_at"]

    def update(self, event: Event, blacklist: Optional[List[str]] = None) -> None:
        blacklist = blacklist or []
        if event.event_type == EventType.APP_FOREGROUND:
            self.current_app = event.payload.get("app_name", self.current_app)
        elif event.event_type == EventType.SCREEN_STATE:
            self.screen_on = event.payload.get("is_on", True)
            self.last_screen_toggle_time = event.timestamp
        elif event.event_type == EventType.TICK:
            delta = event.payload.get("delta_seconds", 0)
            if delta <= 0 and self.last_tick_time:
                delta = max(0.0, event.timestamp - self.last_tick_time)
            self.last_tick_time = event.timestamp

            if self.screen_on and self.current_app in blacklist:
                self.daily_distraction_seconds += delta
