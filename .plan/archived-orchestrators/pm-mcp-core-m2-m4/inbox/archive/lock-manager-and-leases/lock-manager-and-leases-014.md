envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:53:20Z

# Candidate lesson: a Javadoc-stated precondition was not enforced (LeaseStore.reviseAll key check)

- Component: pm-core store (LeaseStore.reviseAll)
- Signal: review-bot finding fixed in-run (pr-comment 90255f, sourcery, Medium)
- Evidence: TASK-017 "pm-core store - reviseAll key check (90255f)"; commit 50f9ce8

## What happened

The Javadoc of `reviseAll` already required that a revision keep the lease's key, but the code saved whatever
the callback returned. A callback returning a lease under another key released the original claim and created a
new one. Fix: reject a revision whose key differs (IllegalArgumentException, nothing written), with a test.

## Suggested direction

Every precondition stated in Javadoc of a store mutator should have a guard and a test that fails without it;
self-review can check documented preconditions against the code before the PR.
