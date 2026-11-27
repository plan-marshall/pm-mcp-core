envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-27T19:28:35Z

## What landed

plan-v2-210-package-texts-third-doc-review shipped as #636 (merged through the merge queue, merge commit 36633b9bf6c3bb0d9fe6255c2478d173ae22753a).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
pr=#636
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=29871160
total_wall_seconds=41435.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2184
executor_version=0.1.2184
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.count_status=undecidable
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=4
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
```

## Residue

- Runtime stamp: `model` is self-reported (`model_source: self_reported`); the landing-time refresh could not measure it (`refreshed: false`, `model_unknown`). No `effort` was stamped, so it is `unknown`.
- The lifecycle records no review-thread or review-body-finding counts, so those three keys are `unknown` (known producer gap).
- Sonar: PR #636 changes only documents and Sonar produced no analysis. The first sonar-roundtrip firing failed (`analysis revision unavailable`); the operator chose "record unknown and proceed". The count is unknown, not 0.
- Quality gate: the spec's Done When asked for `verify -Ppre-commit` green. The build decision for this footprint was not_necessary and the freshness gate was exempt, so the gate runs were an extra the operator chose. They ran three times on the final head family (ed4c9b68) and were green: whole-repository quality gate, test-compile, whole-tree module tests.
- Five owners the spec names as "owner open", none edited by this plan: the CI contract at the act (ci-contract-finalize-operations), finalize.land-message (finalize-pipeline-and-merge), surfacer and retrospective-aspect selection (phase-sub-workflows), refine evidence_class and its questions (initial-lane-router), FIX to fixed (phase-sub-workflows task 7).
- Reported, not owned here, not created (owner open; recommendations held in epic.md): the git.branch-sync-state states / sync_required / git.sync-required / the lease after an integrated foreign head (no package names them), and the findings write seam (claim_refuted job-id resolution, the duplicate test, FIX to fixed then transmit, evidence_ref).
- Specification drift to report, not edit: workflow-dsl/06-reusable-sub-workflows.adoc still says "six phases"; job-runtime/02 item 2 lets a write_paths entry at the project root stay writable for writer workers, which contradicts section 1 of the 2026-11-24 doc review.
- Operator decisions during the plan: Q1 drop commit-boundary tasks 2 and 3 (later partly reversed for the lift-removal task by the operator at finding f421e7, TASK-9); Q2 the consultations go under phase-sub-workflows "Left out" without a plan id (PLAN-V2-202 is the plan that removes the worker-declared consultation references); Q3 keep the README diagram-generator comment (PLAN-V2-159 / PLAN-V2-163 decision record); Q4 add the epic-auto dependency on ci-contract-finalize-operations.
- Correction plans whose ids were removed from the package texts: PLAN-V2-13, PLAN-V2-59, PLAN-V2-146, PLAN-V2-190, PLAN-V2-192, PLAN-V2-193.
- Cost: finalize used 78% of all tokens, because every review finding after a push re-ran the steps that depend on the head. 11h30m wall time, 29.9M tokens (a floor; includes cache reads). Recorded as lessons 011-013 in the epic inbox.
- Two deviations: (1) the first Sonar count and the CodeRabbit quota wait were resolved by operator choice (wait for the reset, then review-bot recovery turned on for this plan only via review_rate_window_await=true); (2) the squash-message step of branch-cleanup did not run (no ci repo command exists).
