import unittest
import time
import dataclasses
from core.events import Event, EventType
from core.commands import Command, CommandType

class TestEventsAndCommands(unittest.TestCase):
    def test_event_instantiation_and_defaults(self):
        t0 = time.time()
        evt = Event(event_id="evt-1", event_type=EventType.APP_FOREGROUND, payload={"app_name": "instagram"})
        self.assertEqual(evt.event_id, "evt-1")
        self.assertEqual(evt.event_type, EventType.APP_FOREGROUND)
        self.assertEqual(evt.payload["app_name"], "instagram")
        self.assertGreaterEqual(evt.timestamp, t0)

        # test default factory creates isolated dict
        evt2 = Event(event_id="evt-2", event_type=EventType.TICK)
        self.assertEqual(evt2.payload, {})
        self.assertIsNot(evt.payload, evt2.payload)

    def test_command_instantiation(self):
        cmd = Command(command_id="cmd-1", command_type=CommandType.FORCE_CLOSE, payload={"app_name": "instagram"})
        self.assertEqual(cmd.command_id, "cmd-1")
        self.assertEqual(cmd.command_type, CommandType.FORCE_CLOSE)
        self.assertEqual(cmd.payload["app_name"], "instagram")

        cmd2 = Command(command_id="cmd-2", command_type=CommandType.START_TIMER)
        self.assertEqual(cmd2.payload, {})
        self.assertIsNot(cmd.payload, cmd2.payload)

    def test_frozen_immutability(self):
        evt = Event(event_id="evt-2", event_type=EventType.TICK)
        with self.assertRaises(dataclasses.FrozenInstanceError):
            evt.event_id = "modified"

        cmd = Command(command_id="cmd-2", command_type=CommandType.NOTIFY)
        with self.assertRaises(dataclasses.FrozenInstanceError):
            cmd.command_id = "modified"

    def test_enum_members(self):
        expected_events = {"TICK", "APP_FOREGROUND", "SCREEN_STATE", "USER_COMMAND", "TIMER_EXPIRED", "SHUTDOWN"}
        self.assertEqual({e.value for e in EventType}, expected_events)

        expected_commands = {"NOTIFY", "FORCE_CLOSE", "START_TIMER", "CANCEL_TIMER", "LOG_MESSAGE"}
        self.assertEqual({c.value for c in CommandType}, expected_commands)

if __name__ == "__main__":
    unittest.main()
