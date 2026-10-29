envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:13:10Z

component=pm-runtime
category=bug
signal=orchestrator-observation (verified)
evidence=TASK-27 "Fix: StartupColdStartIT guard measures the steps, not the seeding backlog, and compares a noise-robust statistic" (commit 02f42a5), added via verification-feedback pr-state at 18:38:27Z after CI failure on PR #29

# Timed regions in ITs must not absorb the seeding's write backlog; compare low quantiles, not medians

## Observation

`StartupColdStartIT` compared wall-clock medians of startup steps 1 to 5. It passed locally and flaked on CI: the fixture seeding left a write backlog in the OS page cache, and an fsync inside the timed region flushed it, so the measurement charged the seeding's I/O to the code under test. The fix flushes seeded fixtures before the timed region and compares a noise-robust statistic (low quantile) via a new `ColdStartComparison` helper with its own unit test.

## Rule (pm-mcp-core)

In any timing IT: (1) force seeded fixtures to disk (fsync / close) before the clock starts, so no I/O debt from setup lands in the timed region; (2) compare a low quantile (for example the minimum or p10 of repeated runs) rather than the median or mean, because CI noise only ever adds time; (3) keep the comparison logic in a plain class with a unit test so the statistic can be checked without timing.
