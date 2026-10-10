envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:53:03Z

# Candidate lesson: lease release by key alone lets a delayed release delete a new holder's lease

- Component: pm-core store (LeaseStore.release)
- Signal: review-bot finding fixed in-run (pr-comment 44f486, sourcery, High)
- Evidence: fixed in 50f9ce8 (TASK-017); decision log 42c424

## What happened

`LeaseStore.release(key)` removed whatever record was stored under the key. A delayed or retried release from a
previous holder, arriving after another scope had claimed the same key, deleted the new holder's lease. Fix: an
owner-checked `release(key, expectedOwner)` that removes the lease only when the stored owner equals the
expected owner and otherwise writes nothing and returns the lease found; the unconditional `release(key)`
remains for callers that release by key alone.

## Suggested direction

Lease/lock release operations should be conditional on the caller's identity (owner or fencing token) by
default; a key-only release should be the explicit exception. Worth stating in the specification for later
lease consumers.
