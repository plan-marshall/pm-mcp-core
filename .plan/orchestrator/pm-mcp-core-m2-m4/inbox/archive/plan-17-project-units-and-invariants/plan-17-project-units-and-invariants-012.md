envelope_version=1
sender_type=plan
sender_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T20:11:16Z

## What landed

plan-17-project-units-and-invariants shipped as #56 (merged via the merge queue, df4b318).

```landing-facts
schema=landing-facts/1
plan_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
pr=#56
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=7802605
total_wall_seconds=60624.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- Documentation PR plan-marshall/plan-marshall-documentation#57 (requirements, specification sections, AST node model, LogMessages 070/170/270, roadmap) is open and unmerged; merging it is the operator's. Per the landing rule, check it is MERGED and matches the merged code.
- Format versions (mailbox advisory pm-mcp-core-m2-m4-001): this plan raises no format_version; the DSL COMPILED_FORMAT_VERSION stays 1, and TransactionJournal is not touched.
- Module ADR numbering: this package's workflow-verify ADRs landed as 017-020 after #54 and #53 took 012-016; workflow-unit 001-004 and workflow-finalize 001-003 unchanged.
- WS-03 log identifiers used: INFO 070 PROJECT_UNITS_VERIFIED, WARN 170 PROJECT_UNIT_REFUSED, ERROR 270 DEFAULT_UNIT_REFUSED.
- Operator-accepted deviations: FinalizePipelineValidator rules 6 (work_performed), 7 and 8 are named hooks; INV-GEN-2, INV-GEN-4 (runtime part) and INV-REM-6 are tree-checked with named hooks; DefaultUnitInvariantTest is owed by plan-marshall-mcp; the loader is not wired into plan start/resume here (the assembly pm-mcp-server applies the refusal); the specification invariant tables were kept with an implemented-status note for the later plan-instance package.
- cuioss-review-bot declined the first re-review (head 0f23147) and the operator proceeded; later heads were reviewed by both required bots. Sourcery (optional) was out of review budget for the whole run.
- Candidate lessons for this epic: inbox messages plan-17-project-units-and-invariants-001 to -011.
