envelope_version=1
sender_type=plan
sender_id=plan-v2-158-null-contracts-and-optional-conversion
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-20T16:49:11Z

## What landed

plan-v2-158-null-contracts-and-optional-conversion shipped as two pull requests, #525 (deliverable 1, merged as c9d9c5a4) and #529 (deliverables 2 to 6, merged as 4917a034), both through the merge queue.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-158-null-contracts-and-optional-conversion
epic=pm-mcp-core-m2-m4-v2
pr=#529
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=27250816
total_wall_seconds=44577.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=8
step.code-review.rules_cited=5
```

## Residue

- **Two pull requests.** The plan was cut into PR A (deliverable 1, `pm-core`, #525) and PR B (deliverables 2 to 6, #529) by operator decision at the outline gate, because of the 100-file review limit. `pr=` names PR B; PR A is named above. The file-based checks of the retrospective read only PR B's files, so its file-coverage figure is low (see candidate-lesson -002).
- **Criterion D4.C4 ("BROKEN LINKS 0") is not met literally.** The links check, run from a sibling checkout where the documentation resolves, reports BROKEN LINKS 3, identical on `main` and in no file either pull request touches: `roadmap.adoc:250` (documentation repository, a missing `doc/plans/plan-instance-versions.adoc`), `pm-core/doc/adr/core-config/010-one-rule-decides-every-project-value-over-the-machine-s.adoc:62` (links to a missing `cli-and-installer.adoc`) and `doc/specification/harness-profiles.adoc:13` (links to a missing `harness-integration.adoc`). They are handed to the operator, not fixed by this plan. Run from the nested plan worktree the same check reports 3090 (the sibling links do not resolve there). The operator closed the functional review on this criterion by an in-session decision (operator override at 749b0051).
- **Review re-run waived.** After the last review round a documentation-and-Javadoc-only commit (d5f1d6ac, three files) was pushed; the operator waived the re-run of the functional review and the code review for it, recorded as `head_binding_waived=code-review` on branch-cleanup and as an operator close of the functional review.
- **Assembly read.** The assembly `plan-marshall-mcp` was read for dereferences of the 17 accessors that became nullable: no hit, so no follow-up after the SNAPSHOT deploys (read against its `origin/main` bde0939f; recorded in decision-log entries 319b95, 373fe9 and 7be36f).
- **Carried items.** The per-item outcomes of the spec's carried items (among them the Sonar S2637 suppression kept, the RoleAssignmentValue refusal kept, the ForgeUrl outcome and the MachineConfigurationCodec guard outside the declared surface) are in the plan's decision log (entries 8fa5fd, 6d2e07, 7254e8, 1b4170 and neighbours) and in ADRs 029 to 033, provider-ci/011, runtime-credentials/014 and runtime-config/007 of the repository.
- **Producer gaps.** `effort` is `unknown`: no effort stamp exists on this plan. The token total is a floor for one reason: the first partial runs of resumed agents (envelope 4's rate-limited attempt) were not measured. One placeholder usage entry made during finalize was reversed in the accumulator before the metrics were generated.
- **archive-plan** runs after this message; it is not in `steps=` because it had not run when the landing was written.
