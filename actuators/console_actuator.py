import sys
from core.commands import Command, CommandType
from actuators.base import BaseActuator


class ConsoleActuator(BaseActuator):
    """Actuator that prints commands, notifications, and alerts directly to stdout."""

    def handle(self, command: Command) -> None:
        if command.command_type == CommandType.NOTIFY:
            title = command.payload.get("title", "ALERT")
            body = command.payload.get("body", "")
            priority = command.payload.get("priority", "normal")
            color = "\033[93m" if priority == "warning" else ("\033[91m" if priority == "high" else "\033[96m")
            reset = "\033[0m"
            print(f"\n{color}[AGENT NOTIFICATION: {title}] {body}{reset}")
            # Terminal beep on high priority if supported
            if priority == "high" and sys.platform == "win32":
                try:
                    import winsound
                    winsound.MessageBeep(winsound.MB_ICONEXCLAMATION)
                except Exception:
                    pass

        elif command.command_type == CommandType.FORCE_CLOSE:
            app_name = command.payload.get("app_name", "")
            print(f"\n\033[91m[ACTUATOR ACTION] 🛑 FORCE-CLOSING APP: {app_name}\033[0m")

        elif command.command_type == CommandType.LOG_MESSAGE:
            msg = command.payload.get("message", "")
            print(f"[LOG] {msg}")
