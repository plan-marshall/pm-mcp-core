envelope_version=1
sender_type=plan
sender_id=verify-not-resolve
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T07:29:33Z

## What landed

verify-not-resolve shipped as #43 (merged, squash via merge queue as 68c6bc3).

```landing-facts
schema=landing-facts/1
plan_id=verify-not-resolve
epic=pm-mcp-core-m2-m4
pr=#43
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=5181280
total_wall_seconds=109245.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- The documentation PR plan-marshall/plan-marshall-documentation#46 is open and awaits the operator's merge. It was written before the review loop-backs, so it does not yet cover:
  - the interpreter host-tool refusal (TASK-18);
  - the mandatory maxLength for enrolled patterns (TASK-19);
  - the VerifiedJob.projectScriptArguments carrier (TASK-17).

  Requirement trace links and the refusal-code list in the Job Runtime specification may need a follow-up commit there.
- Operator decision: the wrapper git-tracked/committed requirement was dropped from PM-EXEC-1, PM-SEC-5 and the Job Runtime specification. Wrappers are verified by location, enrolled name and digest only (pm-runtime ADR runtime-job/003).
- Operator decision: the forbidden free flags apply to canonical build jobs only. Shell metacharacters are refused in every job kind.
- Residual review concern, not fixed and recorded in ADR runtime-job/001 as rejected alternatives. CodeRabbit's summary rates it Medium and inferred: maxLength bounds the input length but not the regex matching time under the caller's enrolment lock. A non-backtracking engine (new dependency) or a per-match time budget would close it. A candidate for job-runner or job-confinement-and-environment.
- Overlap for PLAN-20 (job-runner): the caller must close its LockTransaction as soon as VerifyNotResolvePipeline.verify returns, never while the job launches or runs (documented in pm-runtime/doc/developer/runtime-job.adoc).
- WS-04 log identifier used: WARN PM_MCP-180 (JOB_REFUSED).
- 23 candidate lessons were sent to this inbox as verify-not-resolve-001 to -023.
