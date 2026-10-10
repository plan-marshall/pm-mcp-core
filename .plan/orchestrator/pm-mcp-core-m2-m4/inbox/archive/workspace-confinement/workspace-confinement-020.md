envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-10T07:29:06Z

## What landed

workspace-confinement shipped as #19 (merged).

```landing-facts
schema=landing-facts/1
plan_id=workspace-confinement
epic=pm-mcp-core-m2-m4
pr=#19
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=5218327
total_wall_seconds=64403.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:unknown
step.finalize-step-sync-baseline.action=noop
step.finalize-step-sync-baseline.upstream_commit_count=0
step.create-pr.pr_number=19
step.sonar-roundtrip.count_status=confirmed
step.sonar-roundtrip.new_code_issue_count=0
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.branch-cleanup.cleanup_owed=false
step.record-metrics.total_tokens=5218327
step.record-metrics.total_wall_seconds=64403.0
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- Merge commit on main: 7d996488932afd835f9dbde32fad8201aca97364 (squash, via the platform merge queue; main had advanced by PR #18 before the enqueue).
- The companion documentation pull request plan-marshall/plan-marshall-documentation#19 (branch `workspace-confinement`, commits 7a03494 and 787a8cf) is OPEN and not merged; its merge is the operator's.
- Two fix rounds ran after review: commit 252b4b1 (Sonar java:S7467) and commit 470df0e (linked-worktree registration and gitdir back-pointer check, operator-decided fix of a CodeRabbit Major finding that supersedes a round-1 decline of the same point from Sourcery).
- The operator moved cuioss-review-bot from required to optional for this plan only; it never reviewed the two fix commits (its review is stale at 470df0e).
- Scope changed during planning by operator decision: a fourth deliverable reworded CLAUDE.md and the section "How to Work a Package" of doc/plans/README.adoc; the draft-pull-request claim and the rule that the documentation pull request names the code pull request were dropped.
- The new log message is WARN PROJECT_PATH_REFUSED, identifier 150 (first use of the WS-01 block 150-159).
- pm-provider-git is NOT wired to the confinement check (out of slice by operator decision); consumers adopt it in their own packages.
- Not verified by this run: PM-WATCH-PHASE-42 was never read; CLI and Security specification parts 01-03 were not read in full; the documentation repository was not searched for other restatements of the linked-worktree recognition rule; `.claude/skills/traced-implementation/SKILL.md` and the introduction of doc/plans/README.adoc still mention the paired-pull-request rule.
- The pre-push gate recorded its module-tests arm as DEGRADED (no module-tests canonical resolves at default scope in this Maven project; the whole-tree `verify -Ppre-commit` ran the tests).
- `steps` lists `archive-plan:unknown` because that step runs after this emission.
