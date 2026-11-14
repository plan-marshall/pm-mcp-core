envelope_version=1
sender_type=plan
sender_id=plan-v2-138-secret-redactor-hardening
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-13T18:22:01Z

## What landed

plan-v2-138-secret-redactor-hardening shipped as #407 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-138-secret-redactor-hardening
epic=pm-mcp-core-m2-m4-v2
pr=#407
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=43955207
total_wall_seconds=73662
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2114
executor_version=0.1.2114
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=7
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=4
step.code-review.rules_cited=4
```

## Residue

- **Merge.** The PR was merged by the platform merge queue as `64cec092fea78b763815a6096112678cbcf77232` (the queue rebased onto main, which was 6 ledger and documentation commits ahead with no overlap). `final_merge_without_asking` is `false` in this plan's `branch-cleanup` step params, so the pre-merge confirmation would have prompted; the merge consent was recorded from the repository CLAUDE.md standing authorization (the plan lifecycle merges its own PR through the merge queue, the operator's decision to start the plan is the permission) plus the operator's instruction to continue unattended. No interactive answer was given. Retrospective lesson 002 asks which side is authoritative.
- **Review gates that this run does not have.** The three review keys are `unknown` because this manifest has no `review-gate` step (they come from the runbook lane). What the run did observe: every CodeRabbit inline thread was answered and resolved on the provider across the three unified-triage firings (the respond passes reported no untransmitted or deferred reply), the pre-merge `pre_merge_gate merge` dispatch reported `findings_gate: clean` and `participation_gate: complete`, and no pending finding remained.
- **Sonar.** No Sonar credentials are configured, so the `sonar-roundtrip` step recorded `work_performed=false` in every firing. `sonar_open_new_issues=0` is this run's own plain-HTTP read of the public SonarCloud API for pullRequest=407 at the merge head: 0 open issues and 0 hotspots to review, quality gate OK. It is not a fact the step recorded.
- **Scope deviation accepted.** The TAP plan line is compared with the top-level test points, not with `# tests` (ADR `core-build/009`, decision 3), because the real `node --test` fixture plans one top-level point per suite.
- **Loop-backs.** Four finalize loop-back rounds (unified triage twice, code review twice) found one sibling of the same overflow family each time (TASK-22 per-run TAP plan, TASK-23 plan-count overflow, TASK-24 summary counts with the whole family swept). TASK-24 closed the family: fixed in the TAP, Jest and ESLint parsers, exempt with a stated bound at Maven, LCOV, TestCounts and RewriteLogResult.
- **CI.** One `integration-tests` failure at the last head was infrastructure (Maven Central could not resolve `surefire-junit-platform:3.6.0`); the same workflow run was re-run once and passed.
- **Leftovers for a staged plan.** (1) ADR `core-build/009` "What stays readable" has no bullet for the npm-path head residue (a credentialed URL on a line over 8192 characters whose closing `@` lies beyond the cut leaves the prefix readable); the spec line-bound paragraph needs the matching clause. (2) Two advisory test-quality notes: the "parts wrap to the total" tests in `TapOutputParserTest` and `JestOutputParserTest` cannot fail for the defect they name; no cost test for the possessive quoted-flag loop in `SecretRedactorTest`. (3) The PEM block pattern in `SecretRedactor` is quadratic across lines on repeated BEGIN markers without an END (author's untimed estimate), unnamed in ADR `core-build/009` Decision 1. (4) ADR `core-build/009` line 149 merges the Maven and LCOV bounds into one figure that does not add up to 2^58; the LCOV `Walk` Javadoc calls the stream cap an int bound although it is a long limited by its configured value; the Maven `CountScan` Javadoc says the sums cannot wrap although an unrepresentable scan wraps unread values.
- **Lessons.** The retrospective wrote 5 candidate lessons to this epic's inbox (001, 002 for this epic; 003 to 005 concern `plan-marshall:*` components and were forwarded to the plan-marshall `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-031` to `-033`, uncommitted in that ledger worktree until its land).
- **This landing.** Written by `emit-landing`; `emit-landing` and `archive-plan` follow it in the composed order and are not in `steps` above.
