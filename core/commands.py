from dataclasses import dataclass, field
from enum import Enum
import time

class CommandType(Enum):
    NOTIFY = "NOTIFY"
    FORCE_CLOSE = "FORCE_CLOSE"
    START_TIMER = "START_TIMER"
    CANCEL_TIMER = "CANCEL_TIMER"
    LOG_MESSAGE = "LOG_MESSAGE"

@dataclass(frozen=True)
class Command:
    command_id: str
    command_type: CommandType
    timestamp: float = field(default_factory=time.time)
    payload: dict = field(default_factory=dict)
