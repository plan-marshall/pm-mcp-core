envelope_version=1
sender_type=plan
sender_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-11T06:47:18Z

## What landed

plan-04-service-unit shipped as #25 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
pr=#25
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=3824435
total_wall_seconds=52023.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.create-pr.pr_number=25
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- Merged under an operator `barrier-ask-override` at 2297ecc: the required review bot `cuioss-review-bot` was participated_stale (its review predates the two fix commits; pushes do not re-trigger it, and with use_merge_queue=true the branch-cleanup re-review trigger never fires). `automatic-review` was closed as an operator override (`may_close=operator_override`).
- Merge commit on main: 0ca1b247406c4beb7817dcf6e99d8d185e1810bc (squash via the platform merge queue).
- Documentation pull request https://github.com/plan-marshall/plan-marshall-documentation/pull/26 (Start Modes, LogMessages.adoc PM_MCP-51/52/251, roadmap, new watch item PM-WATCH-RUN-18) is open and awaits the operator's merge; its body names the code branch, not PR #25.
- Operator-accepted scope deviation: systemd uninstall order is disable (unit file present) -> delete unit file -> unload; launchd unloads after the file is removed. PM-WATCH-RUN-15 stays open.
- Three review/Sonar loop-backs: round 1 hardening (explicit rw-r--r-- modes) triggered Sonar java:S2612 and a red quality gate; round 2 switched to owner-only 0600/0700 via PosixModes.
