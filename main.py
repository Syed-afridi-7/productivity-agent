import os
import threading
import time

from core.events import Event, EventType
from core.memory import MemoryStore
from core.perception import PerceptionState
from core.state_machine import StateMachine
from core.rule_engine import RuleEngine
from core.pipeline import EventPipeline
from core.heartbeat import HeartbeatRunner
from actuators.console_actuator import ConsoleActuator
from cli import AgentCLI


def main() -> None:
    print("Initializing Productivity Agent Core (Phase 1)...")
    db_path = os.path.join("data", "agent.db")
    memory = MemoryStore(db_path=db_path)
    perception = PerceptionState()
    fsm = StateMachine(memory=memory)
    rule_engine = RuleEngine(config_path="config/rules.json", memory=memory)
    actuator = ConsoleActuator()

    pipeline = EventPipeline(
        memory=memory,
        perception=perception,
        fsm=fsm,
        rule_engine=rule_engine,
        actuators=[actuator]
    )

    # Start worker thread
    worker_thread = threading.Thread(target=pipeline.run_worker, daemon=True)
    worker_thread.start()

    # Start heartbeat ticker (every 5 seconds)
    heartbeat = HeartbeatRunner(pipeline=pipeline, interval_sec=5.0)
    heartbeat.start()

    # Launch CLI
    cli = AgentCLI(pipeline, fsm, perception, memory, rule_engine)
    try:
        cli.run_loop()
    finally:
        print("Stopping heartbeat and worker pipeline...")
        heartbeat.stop()
        pipeline.post_event(Event(event_id="shutdown", event_type=EventType.SHUTDOWN))
        worker_thread.join(timeout=1.0)
        memory.close()
        print("Agent cleanly stopped.")


if __name__ == "__main__":
    main()
