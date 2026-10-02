import unittest
import time
import queue
from core.events import Event, EventType
from core.heartbeat import HeartbeatRunner


class MockPipeline:
    def __init__(self):
        self.received = []

    def post_event(self, event):
        self.received.append(event)


class TestHeartbeat(unittest.TestCase):
    def test_heartbeat_ticks_and_stops(self):
        mock_p = MockPipeline()
        runner = HeartbeatRunner(pipeline=mock_p, interval_sec=0.1)
        runner.start()
        time.sleep(0.35)
        runner.stop()
        self.assertGreaterEqual(len(mock_p.received), 2)
        self.assertEqual(mock_p.received[0].event_type, EventType.TICK)

    def test_heartbeat_payload_and_delta(self):
        mock_p = MockPipeline()
        runner = HeartbeatRunner(pipeline=mock_p, interval_sec=0.05)
        runner.start()
        time.sleep(0.15)
        runner.stop()
        self.assertGreaterEqual(len(mock_p.received), 1)
        first = mock_p.received[0]
        self.assertTrue(first.event_id.startswith("tick-"))
        self.assertIn("delta_seconds", first.payload)
        self.assertGreater(first.payload["delta_seconds"], 0)

    def test_heartbeat_immediate_stop(self):
        mock_p = MockPipeline()
        runner = HeartbeatRunner(pipeline=mock_p, interval_sec=1.0)
        runner.start()
        runner.stop()
        self.assertFalse(runner.thread.is_alive())
        self.assertEqual(len(mock_p.received), 0)

    def test_default_interval(self):
        mock_p = MockPipeline()
        runner = HeartbeatRunner(pipeline=mock_p)
        self.assertEqual(runner.interval_sec, 5.0)


if __name__ == "__main__":
    unittest.main()
