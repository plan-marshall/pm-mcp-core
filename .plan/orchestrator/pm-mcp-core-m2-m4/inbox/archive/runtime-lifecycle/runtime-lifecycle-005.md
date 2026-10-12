envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:36Z

component=plan-marshall:phase-5-execute
category=bug
signal=script-failure-cluster
evidence=work.log [ERROR] script_failure notation=plan-marshall:phase-5-execute:scope_creep_check exit_code=1 failure_kind=script_internal_failure (6 occurrences, every execute envelope incl. loop-backs); decision.log entries 594484, c6f5a0, d8932b

# scope_creep_check fails with finding_persist_failed and flags upstream merges as scope creep

## Observation

Two defects in `plan-marshall:phase-5-execute:scope_creep_check`, both hit on every phase-5 envelope of plan runtime-lifecycle:

1. **Invalid finding type.** The script persists its warning as finding type `scope_creep_warning`, which manage-findings rejects: "Invalid finding type: scope_creep_warning. Must be one of ('bug', 'improvement', 'anti-pattern', 'triage', 'tip', 'insight', 'best-practice', 'build-error', 'test-failure', 'lint-issue', 'sonar-issue', 'arch-constraint', 'pr-comment', 'pr-comment-overflow')". The script exits 1 (script_internal_failure), so the guard's own result is lost and the leaf has to log and continue by hand.
2. **False positives from upstream merges.** The residual set is computed against `plan_creation_sha` (cbdd04e). Files of upstream PRs merged to main after the plan base and absorbed into the worktree (PR #28 268d2c0, later PR #27 after the rebase) were reported as scope creep: 14 files first, 76 files after the finalize rebase. Test fixtures added inside a deliverable (ProviderLeaseStores, RecoveryStepFixtures) were also flagged.

## Suggested direction

- Use a finding type the store accepts (or register `scope_creep_warning` in the manage-findings type enum) so the guard can persist its result and exit 0.
- Compute the residual against the current merge base / the absorbed upstream SHA (the self-absorb and sync-baseline steps already know it), not against `plan_creation_sha`, so absorbed upstream commits are not attributed to the plan.
