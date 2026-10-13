envelope_version=1
sender_type=plan
sender_id=plan-21-entity-addressing
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T15:52:24Z

## What landed

plan-21-entity-addressing shipped as #54 (merged via the merge queue as b25d115).

```landing-facts
schema=landing-facts/1
plan_id=plan-21-entity-addressing
epic=pm-mcp-core-m2-m4
pr=#54
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=5837278
total_wall_seconds=27252.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- Documentation PR plan-marshall/plan-marshall-documentation#56 (deliverable 6: PM-ARCH-4 / PM-WF-1 trace lines, roadmap Milestone 3 item, new watch item PM-WATCH-TOOL-22 for an `entry_unreadable` error_code) is OPEN and not merged; merging it is the operator's. Deliverable 6's success criterion was an opened PR, so it is counted done here.
- Operator decision during finalize: after #51 (session-log-and-generations) landed core.store.EntryId and PendingEntryStore, PLAN-21 was reworked in-PR (fix commit 52bc5c9): the entry_id grammar now lives only in core.store.EntryId, PendingEntryReader.ReadRequest carries the typed EntryId, and its Javadoc states that no pm-runtime adapter over PendingEntryStore exists yet. ADRs workflow-engine 005/006/009 carry dated amendments.
- The default entity kind `review` was renamed `lessons-review` in EntityKindRegistry (operator decision at outline; workflow-verify ADR 012).
- The format-version advisory (pm-mcp-core-m2-m4-001) does not apply: this plan touches no format_version, codec or journal.
- Sourcery (optional reviewer) did not review the final heads: its 7-day review budget was exhausted; the operator chose to proceed without it.
