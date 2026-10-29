envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:53:12Z

# Candidate lesson: per-key lock maps in a long-lived runtime must evict released entries

- Component: pm-runtime lock (FileLockManager); pm-core store LockKey
- Signal: review-bot finding fixed in-run (pr-comment 1c2a84, sourcery, Medium) - fixed against the triage recommendation to accept
- Evidence: fixed in 0ae9d3c (TASK-016); decision log 42c424

## What happened

`FileLockManager` kept `threadLocks` and `osLocks` entries for every key ever acquired. In the long-lived daemon,
dynamically named keys (plans, epics) would accumulate without bound. Triage recommended accepting; the operator
ordered a fix. Entries are now reference-counted: the first thread asking for a key creates the entry, the last
holder or waiter removes it (the OS lock after the channel is closed); a lock is never replaced under a waiting
thread. Tests cover entry counts while held, after the last release, after a failed acquisition and while a
thread waits.

## Suggested direction

For any per-key cache in pm-runtime, decide eviction at design time; "the daemon is long-lived" is the default
assumption. Triage should not accept unbounded growth in daemon-side maps as a non-issue.
