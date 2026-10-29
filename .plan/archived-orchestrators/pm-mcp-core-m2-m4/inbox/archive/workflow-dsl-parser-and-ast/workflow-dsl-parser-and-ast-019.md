envelope_version=1
sender_type=plan
sender_id=workflow-dsl-parser-and-ast
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-11T16:09:33Z

## What landed

workflow-dsl-parser-and-ast shipped as #27 (merged).

```landing-facts
schema=landing-facts/1
plan_id=workflow-dsl-parser-and-ast
epic=pm-mcp-core-m2-m4
pr=#27
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=6614736
total_wall_seconds=81596.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.create-pr.pr_number=27
```

## Residue

- Code PR plan-marshall/pm-mcp-core#27 merged by the merge queue as d091527b3a36bb0a5391c039bebc5f7abca44249.
- Companion documentation PR plan-marshall/plan-marshall-documentation#27 merged as 9f220c06b89453fb0d0d5baea8bafdeb3c2491e2 after a rebase that kept both new DeveloperGuide.adoc rows (#28 store substrate, this plan's workflow DSL); trace.py BROKEN LINKS 0.
- Operator decisions taken mid-run that changed the specification: the instance header follows the PlanInstanceHeader store schema (nested inputs, no input_digests); the unit question.adjudicate is renamed adjudicate.decisions and its primitives adjudicate.check / adjudicate.vote-roles / adjudicate.decide-agreement (question is a reserved word); the scope clause is mandatory in the EBNF; UnitOrigin is declared.
- Two finalize fix rounds (6 fix tasks) followed the unified review/Sonar triage; final round clean (0 new Sonar issues, required-bot participation complete).
- pre-push-quality-gate ran degraded in this Maven project: derive_gate_bundles resolves no bundle and no module-tests canonical resolves at default scope (whole-tree verify -Ppre-commit and test-compile ran green instead).
- 18 candidate-lesson messages (workflow-dsl-parser-and-ast-001..018) were written to this epic's inbox by lessons-capture and the retrospective.
