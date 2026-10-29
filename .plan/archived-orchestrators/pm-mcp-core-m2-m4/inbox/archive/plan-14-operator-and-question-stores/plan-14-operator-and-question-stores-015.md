envelope_version=1
sender_type=plan
sender_id=plan-14-operator-and-question-stores
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-18T14:25:00Z

## What landed

plan-14-operator-and-question-stores shipped as #93 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-14-operator-and-question-stores
epic=pm-mcp-core-m2-m4
pr=#93
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=7822361
total_wall_seconds=63387.0
harness=claude
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=11
step.code-review.rules_cited=9
```

## Residue

- Deliverable 8's link criterion ("BROKEN LINKS 0") was superseded by the operator decision that the link check is gone (commit 065001e, #88); the functional review records it as not met with a non-consequential residual. `CLAUDE.md` of this repository may still name that check.
- The pull request went through three unified-triage rounds (SonarQube Cloud and CodeRabbit findings fixed by fix tasks 15-24), two rebases onto a moving `main`, and two renumberings of this plan's ADRs (final numbers 032-039, `main` owns 029-031). The final head was reviewed end to end; the third round accepted two Minor documentation remarks without a change.
- Open advisories recorded and deliberately not fixed: `ProjectResolver` hides an `enrolment_root_conflict` when the second enrolment file is unreadable (ADR 037 and the guide overclaim), duplicate or double-pending `waiver_requests`, a repeated waiver id in `WaiverLog`, `QuestionLog.revise` accepts a `decision` on a non-first revised line, a part asked into an existing question set makes the set unrevisable, untested guards of `QuestionRecord` and `QuestionLog`, the size of three test classes, placeholder Alternatives sections of ADRs 033 and 034, missing Traceability entries for PM-WF-12 and PM-SEC-3.
- The `pm-runtime` adapter for `QuestionRecordReader` is not part of this plan; the concept and user documentation and the roadmap check-off are owed to the milestone exit in `plan-marshall-documentation`.
- Seven plus seven `candidate-lesson` messages (001-014) are in this epic's inbox; most concern `plan-marshall:*` components and are to be forwarded to the plan-marshall `lessons-routing` inbox when drained.
