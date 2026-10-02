import threading
import time
import uuid
from typing import Any
from core.events import Event, EventType


class HeartbeatRunner:
    """Background daemon thread emitting periodic TICK events into the pipeline."""

    def __init__(self, pipeline: Any, interval_sec: float = 5.0):
        self.pipeline = pipeline
        self.interval_sec = interval_sec
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> None:
        """Start the background heartbeat thread."""
        self.thread.start()

    def stop(self) -> None:
        """Signal the heartbeat thread to stop and wait for it to join."""
        self.stop_event.set()
        self.thread.join(timeout=1.0)

    def _run(self) -> None:
        """Internal run loop for the ticker daemon."""
        last_tick = time.time()
        while not self.stop_event.is_set():
            if self.stop_event.wait(self.interval_sec):
                break
            now = time.time()
            delta = now - last_tick
            last_tick = now
            tick_event = Event(
                event_id=f"tick-{uuid.uuid4().hex[:8]}",
                event_type=EventType.TICK,
                timestamp=now,
                payload={"delta_seconds": delta}
            )
            self.pipeline.post_event(tick_event)
