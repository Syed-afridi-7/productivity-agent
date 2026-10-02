from dataclasses import dataclass, field
from enum import Enum
import time

class EventType(Enum):
    TICK = "TICK"
    APP_FOREGROUND = "APP_FOREGROUND"
    SCREEN_STATE = "SCREEN_STATE"
    USER_COMMAND = "USER_COMMAND"
    TIMER_EXPIRED = "TIMER_EXPIRED"
    SHUTDOWN = "SHUTDOWN"

@dataclass(frozen=True)
class Event:
    event_id: str
    event_type: EventType
    timestamp: float = field(default_factory=time.time)
    payload: dict = field(default_factory=dict)
