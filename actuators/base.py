from abc import ABC, abstractmethod
from core.commands import Command


class BaseActuator(ABC):
    """Abstract base class for all agent actuators."""

    @abstractmethod
    def handle(self, command: Command) -> None:
        """Execute or dispatch an agent command."""
        pass
