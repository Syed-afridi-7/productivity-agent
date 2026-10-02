import json
import os
import sqlite3
import time
from typing import Any, Dict, List, Optional
from core.events import Event


class MemoryStore:
    def __init__(self, db_path: str = "data/agent.db") -> None:
        self.db_path = db_path
        if db_path != ":memory:":
            os.makedirs(os.path.dirname(os.path.abspath(db_path)), exist_ok=True)
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self._init_schema()

    def _init_schema(self) -> None:
        with self.conn:
            self.conn.executescript("""
            CREATE TABLE IF NOT EXISTS events (
                event_id TEXT PRIMARY KEY,
                timestamp REAL NOT NULL,
                event_type TEXT NOT NULL,
                payload_json TEXT
            );

            CREATE TABLE IF NOT EXISTS state_transitions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp REAL NOT NULL,
                from_state TEXT NOT NULL,
                to_state TEXT NOT NULL,
                reason TEXT
            );

            CREATE TABLE IF NOT EXISTS violations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp REAL NOT NULL,
                rule_name TEXT NOT NULL,
                app_name TEXT,
                details_json TEXT
            );

            CREATE TABLE IF NOT EXISTS focus_sessions (
                session_id TEXT PRIMARY KEY,
                start_time REAL NOT NULL,
                end_time REAL,
                target_duration_min INTEGER NOT NULL,
                actual_duration_sec REAL DEFAULT 0,
                completed INTEGER DEFAULT 0,
                violations_count INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS daily_metrics (
                date TEXT PRIMARY KEY,
                total_focus_seconds REAL DEFAULT 0,
                total_distraction_seconds REAL DEFAULT 0,
                total_violations INTEGER DEFAULT 0,
                sessions_completed INTEGER DEFAULT 0,
                productivity_score REAL DEFAULT 0.0
            );
            """)

    def log_event(self, event: Event) -> None:
        event_type_str = event.event_type.value if hasattr(event.event_type, "value") else str(event.event_type)
        with self.conn:
            self.conn.execute(
                "INSERT OR REPLACE INTO events (event_id, timestamp, event_type, payload_json) VALUES (?, ?, ?, ?)",
                (event.event_id, event.timestamp, event_type_str, json.dumps(event.payload))
            )

    def log_transition(self, from_state: Any, to_state: Any, reason: str, timestamp: Optional[float] = None) -> None:
        t = timestamp if timestamp is not None else time.time()
        from_str = from_state.value if hasattr(from_state, "value") else str(from_state)
        to_str = to_state.value if hasattr(to_state, "value") else str(to_state)
        with self.conn:
            self.conn.execute(
                "INSERT INTO state_transitions (timestamp, from_state, to_state, reason) VALUES (?, ?, ?, ?)",
                (t, from_str, to_str, reason)
            )

    def record_violation(
        self,
        rule_name: str,
        app_name: str,
        details: Optional[Dict[str, Any]] = None,
        timestamp: Optional[float] = None
    ) -> None:
        t = timestamp if timestamp is not None else time.time()
        with self.conn:
            self.conn.execute(
                "INSERT INTO violations (timestamp, rule_name, app_name, details_json) VALUES (?, ?, ?, ?)",
                (t, rule_name, app_name, json.dumps(details or {}))
            )
            # Increment violation count for any active focus session
            self.conn.execute(
                "UPDATE focus_sessions SET violations_count = violations_count + 1 WHERE end_time IS NULL"
            )

    def start_focus_session(self, session_id: str, target_min: int, start_time: Optional[float] = None) -> None:
        t0 = start_time if start_time is not None else time.time()
        with self.conn:
            self.conn.execute(
                """
                INSERT OR REPLACE INTO focus_sessions 
                (session_id, start_time, target_duration_min, actual_duration_sec, completed, violations_count) 
                VALUES (?, ?, ?, 0.0, 0, 0)
                """,
                (session_id, t0, target_min)
            )

    def end_focus_session(self, session_id: str, completed: bool, end_time: Optional[float] = None) -> None:
        t1 = end_time if end_time is not None else time.time()
        with self.conn:
            cur = self.conn.execute("SELECT start_time FROM focus_sessions WHERE session_id = ?", (session_id,))
            row = cur.fetchone()
            duration = max(0.0, t1 - row["start_time"]) if row else 0.0
            self.conn.execute(
                "UPDATE focus_sessions SET end_time = ?, actual_duration_sec = ?, completed = ? WHERE session_id = ?",
                (t1, duration, 1 if completed else 0, session_id)
            )

    def get_recent_events(self, limit: int = 20) -> List[Dict[str, Any]]:
        cur = self.conn.execute(
            "SELECT event_id, timestamp, event_type, payload_json FROM events ORDER BY timestamp DESC LIMIT ?",
            (limit,)
        )
        return [
            {
                "event_id": row["event_id"],
                "timestamp": row["timestamp"],
                "event_type": row["event_type"],
                "payload": json.loads(row["payload_json"] or "{}")
            }
            for row in cur.fetchall()
        ]

    def get_recent_transitions(self, limit: int = 20) -> List[Dict[str, Any]]:
        cur = self.conn.execute(
            "SELECT timestamp, from_state, to_state, reason FROM state_transitions ORDER BY timestamp DESC LIMIT ?",
            (limit,)
        )
        return [dict(row) for row in cur.fetchall()]

    def get_recent_violations(self, limit: int = 20) -> List[Dict[str, Any]]:
        cur = self.conn.execute(
            "SELECT id, timestamp, rule_name, app_name, details_json FROM violations ORDER BY timestamp DESC LIMIT ?",
            (limit,)
        )
        return [
            {
                "id": row["id"],
                "timestamp": row["timestamp"],
                "rule_name": row["rule_name"],
                "app_name": row["app_name"],
                "details": json.loads(row["details_json"] or "{}")
            }
            for row in cur.fetchall()
        ]

    def get_focus_sessions(self, limit: Optional[int] = None) -> List[Dict[str, Any]]:
        query = "SELECT * FROM focus_sessions ORDER BY start_time DESC"
        if limit is not None:
            query += f" LIMIT {int(limit)}"
        cur = self.conn.execute(query)
        return [dict(row) for row in cur.fetchall()]

    def record_daily_metrics(
        self,
        date_str: str,
        total_focus_seconds: float = 0.0,
        total_distraction_seconds: float = 0.0,
        total_violations: int = 0,
        sessions_completed: int = 0,
        productivity_score: float = 0.0
    ) -> None:
        with self.conn:
            self.conn.execute(
                """
                INSERT INTO daily_metrics 
                (date, total_focus_seconds, total_distraction_seconds, total_violations, sessions_completed, productivity_score)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(date) DO UPDATE SET
                    total_focus_seconds = excluded.total_focus_seconds,
                    total_distraction_seconds = excluded.total_distraction_seconds,
                    total_violations = excluded.total_violations,
                    sessions_completed = excluded.sessions_completed,
                    productivity_score = excluded.productivity_score
                """,
                (date_str, total_focus_seconds, total_distraction_seconds, total_violations, sessions_completed, productivity_score)
            )

    def get_daily_metrics(self, date_str: str) -> Optional[Dict[str, Any]]:
        cur = self.conn.execute("SELECT * FROM daily_metrics WHERE date = ?", (date_str,))
        row = cur.fetchone()
        return dict(row) if row else None

    def get_daily_summary(self, date_str: Optional[str] = None) -> Dict[str, Any]:
        if not date_str or date_str.lower() == "today":
            date_str = time.strftime("%Y-%m-%d", time.localtime())
        else:
            date_str = str(date_str)

        # 1. Query focus sessions for this date
        cur_sessions = self.conn.execute(
            """
            SELECT 
                COALESCE(SUM(actual_duration_sec), 0.0) AS total_focus,
                COUNT(CASE WHEN completed = 1 THEN 1 END) AS completed_sessions,
                COUNT(*) AS total_sessions
            FROM focus_sessions
            WHERE date(start_time, 'unixepoch', 'localtime') = ? OR date(start_time) = ?
            """,
            (date_str, date_str)
        )
        s_row = cur_sessions.fetchone()
        total_focus = float(s_row["total_focus"]) if s_row else 0.0
        completed_sessions = int(s_row["completed_sessions"]) if s_row else 0
        total_sessions = int(s_row["total_sessions"]) if s_row else 0

        # Fallback to UTC if no sessions matched localtime
        if total_sessions == 0:
            cur_fallback = self.conn.execute(
                """
                SELECT 
                    COALESCE(SUM(actual_duration_sec), 0.0) AS total_focus,
                    COUNT(CASE WHEN completed = 1 THEN 1 END) AS completed_sessions,
                    COUNT(*) AS total_sessions
                FROM focus_sessions
                WHERE date(start_time, 'unixepoch') = ?
                """,
                (date_str,)
            )
            fb_row = cur_fallback.fetchone()
            if fb_row and fb_row["total_sessions"] > 0:
                total_focus = float(fb_row["total_focus"])
                completed_sessions = int(fb_row["completed_sessions"])
                total_sessions = int(fb_row["total_sessions"])

        # 2. Query violations for this date
        cur_violations = self.conn.execute(
            """
            SELECT COUNT(*) AS total_violations
            FROM violations
            WHERE date(timestamp, 'unixepoch', 'localtime') = ? OR date(timestamp) = ?
            """,
            (date_str, date_str)
        )
        v_row = cur_violations.fetchone()
        total_violations = int(v_row["total_violations"]) if v_row else 0

        # Fallback to UTC for violations if none matched localtime
        if total_violations == 0:
            cur_v_fallback = self.conn.execute(
                "SELECT COUNT(*) AS total_violations FROM violations WHERE date(timestamp, 'unixepoch') = ?",
                (date_str,)
            )
            fb_v_row = cur_v_fallback.fetchone()
            if fb_v_row and fb_v_row["total_violations"] > 0:
                total_violations = int(fb_v_row["total_violations"])

        # 3. Check recorded daily_metrics
        m_row = self.get_daily_metrics(date_str)
        total_distraction_seconds = float(m_row["total_distraction_seconds"]) if m_row else 0.0
        if total_focus == 0.0 and m_row and float(m_row["total_focus_seconds"]) > 0:
            total_focus = float(m_row["total_focus_seconds"])
        if total_violations == 0 and m_row and int(m_row["total_violations"]) > 0:
            total_violations = int(m_row["total_violations"])
        if completed_sessions == 0 and m_row and int(m_row["sessions_completed"]) > 0:
            completed_sessions = int(m_row["sessions_completed"])

        # 4. Calculate productivity score
        total_tracked_time = total_focus + total_distraction_seconds
        if total_tracked_time > 0:
            productivity_score = round((total_focus / total_tracked_time) * 100, 2)
        elif m_row and float(m_row["productivity_score"]) > 0:
            productivity_score = float(m_row["productivity_score"])
        else:
            productivity_score = 0.0

        # 5. Persist updated summary to daily_metrics
        with self.conn:
            self.conn.execute(
                """
                INSERT INTO daily_metrics 
                (date, total_focus_seconds, total_distraction_seconds, total_violations, sessions_completed, productivity_score)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(date) DO UPDATE SET
                    total_focus_seconds = excluded.total_focus_seconds,
                    total_distraction_seconds = excluded.total_distraction_seconds,
                    total_violations = excluded.total_violations,
                    sessions_completed = excluded.sessions_completed,
                    productivity_score = excluded.productivity_score
                """,
                (date_str, total_focus, total_distraction_seconds, total_violations, completed_sessions, productivity_score)
            )

        return {
            "date": date_str,
            "total_focus_seconds": total_focus,
            "focus_time": total_focus,
            "total_distraction_seconds": total_distraction_seconds,
            "distraction_time": total_distraction_seconds,
            "total_violations": total_violations,
            "violations_count": total_violations,
            "sessions_completed": completed_sessions,
            "completed_sessions": completed_sessions,
            "total_sessions": total_sessions,
            "productivity_score": productivity_score,
        }

    def close(self) -> None:
        self.conn.close()
