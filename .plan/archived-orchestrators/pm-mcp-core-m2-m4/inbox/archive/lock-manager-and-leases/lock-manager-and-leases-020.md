envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-10T11:56:28Z

## What landed

lock-manager-and-leases shipped as #20 (merged, squash commit c2b608b via the merge queue).

```landing-facts
schema=landing-facts/1
plan_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
pr=#20
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=6498792
total_wall_seconds=56857
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- Documentation follow-up owed in plan-marshall-documentation, not yet started: `doc/LogMessages.adoc` entries INFO 050 STALE_LEASE_SWEEP_FINISHED, WARN 151 LEASE_ORPHANED (moved from 150, which #19 took), ERROR 250 LOCK_ORDER_VIOLATION; and an amendment of the Format & Atomic Write Discipline item for the parent-directory force after the atomic move (added beyond the specification by operator decision).
- The plan-marshall-mcp package that wires FileLockManager must create exactly one instance per runtime; a second instance on the same lock file can release the first holder's OS lock on POSIX (documented in the class Javadoc only).
- cuioss-review-bot was moved from required to optional for this plan only, because it does not re-review after fix commits.
- Operator decisions beyond the specification shipped: parent-directory force in AtomicStoreFile.write, owner-checked LeaseStore.release(key, expectedOwner), reference-counted eviction of FileLockManager entries.
- The import-layout and record-header recipes of pm-mcp-parent are not stable between pre-commit runs (parent POM follow-up).
