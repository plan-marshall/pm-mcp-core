envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-11T07:19:52Z

## What landed

plan-06-store-substrate (store-substrate, PR #26) merged into main as cbdd04e via the merge queue.

```landing-facts
schema=landing-facts/1
plan_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
pr=#26
merge_state=merged
cleanup_owed=false
deliverables_total=13
deliverables_done=13
total_tokens=5617933
total_wall_seconds=84818.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- Documentation PR NOT yet opened: branch feature/plan-06-store-substrate of plan-marshall-documentation (commits b4d886e, a3f97af, d74ce90, 7d0d1c0; worktree /Users/oliver/git/pm-mcp-core/.plan/temp/plan-06-plan-marshall-documentation) is local and unpushed, awaiting the operator. It covers D1, D11-D13 and the audit-delivery spec (TASK-31), and adds PM_MCP-160 to doc/LogMessages.adoc.
- Merged under an operator ruling: required bot cuioss-review-bot's review predates the fix commits (participated_stale); coderabbit and sourcery reviewed; all 57 findings resolved.
- WS-02 log identifier block: this plan used WARN 160 (AUDIT_RECORD_DEFERRED). Next free WARN in the block is 161.
- API/behaviour changes downstream consumers (plan-marshall-mcp pm-mcp-server) must follow: transaction journal format_version 2 with owed_events; CrossScopeReconciler.reconcile(LockManager, AuditSink); AuditEvent.jsonlTailRepaired / crossScopeReconciled gained a transactionId parameter; ScopeTransaction.commit refuses while an unrecovered journal exists.
- Known limitation for audit-trail / plan-mailbox packages: closing a channel on a POSIX file releases the process's OS locks on it, so JsonlStoreFile appends on file-locked stores drop the lock manager's OS lock; fix belongs in the lock manager (documented in the JsonlStoreFile Javadoc and the developer doc).
- Post-merge verify -Pcoverage and -Pintegration-tests (pm-core, pm-runtime) green on main cbdd04e.
