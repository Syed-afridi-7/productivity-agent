# Productivity Agent

A pure classical + custom neural network personal productivity agent built entirely from scratch with **zero third-party dependencies**, **zero cloud LLMs**, and **zero external APIs**.

---

## Core Philosophy

Modern AI productivity tools often rely on heavy cloud LLMs, recurring token costs, and opaque black-box models. 

This project takes the classical AI & Cybernetics approach:
- **100% Python Standard Library**: Runs anywhere instantly with pure `sqlite3`, `dataclasses`, `enum`, `threading`, `queue`, `json`, and `unittest`.
- **Deterministic Expert System**: Transparent, verifiable rule engine with customizable JSON configuration.
- **Finite State Machine (FSM)**: Strict macro-state management (Idle, Deep Focus, Breaks, Distraction Cooldowns, Reflection, Sleep).
- **Persistent Memory**: Full local SQLite event audit trail, session tracking, and productivity scorecards.
- **Zero Privacy Leakage**: All logs, timers, and rules stay 100% on your local machine.

---

## Architecture (Phase 1)

```
┌─────────────────────────────────────────────────────────────┐
│                    Perception Layer                         │
│  (CLI Simulator in Phase 1 / Android Bridge in Phase 2)     │
└──────────────────────────┬──────────────────────────────────┘
                           │ Event(TICK, APP_SWITCH, COMMAND)
┌──────────────────────────▼──────────────────────────────────┐
│                   Thread-Safe Event Queue                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│             EventPipeline (5-Stage Sequential)              │
│                                                             │
│  Stage 1: Persist          → SQLite events table            │
│  Stage 2: Perception State → Current app, screen, timers    │
│  Stage 3: State Machine    → FSM transitions & validation   │
│  Stage 4: Rule Engine      → Hard rules & quota checks      │
│  Stage 5: Actuators        → Terminal alerts, sound chimes  │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### 1. Requirements
- Python 3.10+
- Zero `pip` packages required.

### 2. Run the Interactive Simulator
Launch the agent with:
```bash
python main.py
```

### 3. Available CLI Commands
| Command | Description | Example |
| :--- | :--- | :--- |
| `status` | View current state, active app, timers, and daily metrics | `status` |
| `focus [min]` | Start a Deep Focus session (enforces blocklists) | `focus 25` |
| `break [min]` | Start a short break session | `break 5` |
| `stop` | Stop or cancel active focus session | `stop` |
| `app <name>` | Simulate opening an application | `app com.instagram.android` |
| `screen <on\|off>` | Simulate phone screen toggles | `screen off` |
| `tick [sec]` | Fast-forward simulated time | `tick 300` |
| `violations` | View recorded infractions today | `violations` |
| `summary [date]`| Generate daily focus & distraction report | `summary` |
| `rules` | Inspect active rules and app blocklists | `rules` |
| `quit` | Gracefully terminate all threads and exit | `quit` |

---

## Running the Automated Test Suite

Run all 61 tests with Python's standard library test runner:
```bash
python -m unittest discover -s tests -p "test_*.py" -v
```

---

## Roadmap

- [x] **Phase 1: Core Engine Skeleton**
  - Event contracts, Perception tracker, SQLite memory, FSM, hybrid rule engine, console actuator, heartbeat daemon, and CLI simulator.
- [ ] **Phase 2: Android Phone Bridge**
  - Companion service (AccessibilityService + UsageStatsManager) streaming live phone events via local socket/HTTP or ADB.
- [ ] **Phase 3: Classical Planner**
  - Constraint satisfaction and priority queue daily schedule optimizer.
- [ ] **Phase 4: From-Scratch Neural Networks**
  - Pure Python / NumPy matrix library, forward/backward pass, and custom lightweight distraction classifiers.
- [ ] **Phase 5: Polish & Autonomy**
  - Evening reflection scoring, adaptive thresholds, and battery-efficient scheduling.
