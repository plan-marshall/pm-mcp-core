envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-12T13:25:23Z

## What landed

plan-11-enrolment-store shipped as #30 (merged, 50109697827d8376d29389d3c2297e3ae0636386).

```landing-facts
schema=landing-facts/1
plan_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
pr=#30
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=11625971
total_wall_seconds=174186.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_commit_sha=50109697827d8376d29389d3c2297e3ae0636386
step.sonar-roundtrip.new_code_issue_count=1
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- The companion documentation pull request plan-marshall/plan-marshall-documentation#30 (branch `feature/enrolment-store`, deliverable 8) is still OPEN. It must merge after #30, which it now can; it awaits the operator's word. Its worktree `/Users/oliver/git/plan-marshall-documentation-enrolment-store` remains.
- The one remaining Sonar new-code issue (deleteAll loop, S9391) was accepted, not fixed.
- Seven follow-ups needing components outside this slice are listed in the PR #30 body and the developer documentation's Known Limits (caller authorization, live-work lock, snapshot window, recovery wiring at startup via ProjectRecovery.withEnrolment, execution-policy open points, state/ and enrolments/ directory ownership, audit-sink deduplication by transaction_id).
- The plan-marshall plugin cache advanced mid-run; both executors were regenerated (0.1.1905) before the merge dispatch.
