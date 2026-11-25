# PLAN-V2-191: The re-planning triggers, the review-cap gate and the domain-choice declaration the specification dropped are removed

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
origin: doc-review-2026-11-24-001.md section 1 ("Re-planning triggers", "Review caps") and doc-review-2026-11-25-001.md section 1 (V8); operator's cut, plan 4.
lane: runbook

> Staged plan spec — ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-191-declaration-bounds-and-review-cap-removals.md`; one queue row, lane `runbook`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (orchestrator confirms). Plan parameters: R06 developer documentation `pm-workflow/doc/developer/workflow-verify.adoc`; ADR directory `pm-workflow/doc/adr/workflow-verify/` (002 and 007 amended with a stamp); layout WS-02.

## Objective

Re-planning no longer triggers on a base advance or on a downstream limit and the pre-wait cap gate `finalize.review-cap-check` no longer exists; the review bot's own refusal is acted on instead. The declared threshold `base_overlap_min_files`, the declaration `kind_domain_choice` (`init.domain-choice` is gone) and the Rule 9 region entry `finalize.review-cap-check` are removed; Rule 9 names `finalize.concurrent-wait` instead.

## Deliverables

Three deliverables, about 14 files, no split needed. Schema file first.

1. **The re-planning record.** `replan-record.schema.json`: trigger kinds `base_advanced` and `downstream_limit` (:37), the checkpoint `finalize.review-cap-check` (:39 description). `status.schema.json:308` only references the record; reword only if it names a removed kind. `register-item.schema.json:30` `base_advanced` is the epic watch trigger and STAYS.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first a schema-level negative control (a trigger of kind `base_advanced` or `downstream_limit` fails the schema; `infeasible`, `merge_conflict`, `ceiling` pass), red at e451ed04; then the schema.
2. **The declaration bounds.** `DeclarationBounds.java:134` `base_overlap_min_files` and `:130` `kind_domain_choice` go; fixtures `unit-fixtures/defaults/replan.pmwf:18` and `adjudicate-decisions.pmwf:21`; `DeclarationBoundsTest` cases :590-593 (both `base_overlap_min_files` bounds) and :613-615 are deleted, :619-621 (a decision-kind declaration with an integer) moves to another decision kind (`kind_outline_review`).
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first two negative controls in the shape of the "an unknown declaration" case (:624-626): a unit declaring `base_overlap_min_files`, and one declaring `kind_domain_choice`, are refused as unknown, naming the declaration; then removal.
3. **Rule 9 region entries.** `WorkflowValidator.java:97-98` `REGION_ENTRY_STATES` names `finalize.concurrent-wait` and `finalize.merge-evidence-wait`; `WorkflowValidatorTest` (:576-595) and `verify/refused/step-completion-firing-per-step.pmwf` (:19,24) rename the opening; `workflow-verify.adoc:177`; ADRs workflow-verify/002 (:20) and 007 (:14) name the old state and are amended.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
   - Test order: first the case with `finalize.concurrent-wait` as the opening (a firing that passes another step's opening before completing its step is refused, matched with the fixture), and the control that a state named `finalize.review-cap-check` is no longer an opening; then the constant.

**Done when** (against `origin/main` e451ed04): a unit declaring `base_overlap_min_files` or `kind_domain_choice` is refused as unknown — fails at e451ed04; a state `finalize.concurrent-wait` opens a Rule 9 firing and `finalize.review-cap-check` does not — fails at e451ed04; a replan trigger of kind `base_advanced` or `downstream_limit` fails the schema — fails at e451ed04; the other declarations of `replan` and `adjudicate.decisions` (`replan_budget`, `conflict_min_files`, `prerequisite_wait_seconds`, `vote_wait_seconds`, `max_false_auto_percent`, `kind_recipe_candidate`, `kind_sibling_collision`, `kind_outline_review`) keep their bounds — holds at e451ed04. `verify -Ppre-commit`, `verify -Pcoverage`, `verify -pl pm-core,pm-workflow -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Sonar: 0 open new-code findings at the head or each key selected by the operator.

## Standing Constraints

- Every removal deletes its pinning tests and adds a negative control; no read shim; schema and parity test in one commit.
- Read cited lines at the head first. No dependency or plugin; at most 100 files per pull request; no model-facing content.
- Sonar: fix or dismiss only on the operator's selection; an undecidable read blocks.

## Claim Labels

- OBSERVED: `DeclarationBounds.java:130,134`; `DeclarationBoundsTest.java:590-593,613-615,619-621`; `replan.pmwf:18`; `adjudicate-decisions.pmwf:21`; no other file of `pm-workflow`, `pm-core` or `pm-runtime` names `conflict_min_files`, `kind_recipe_candidate` or `replan_budget` besides these four.
- OBSERVED: `replan-record.schema.json:37` enum `["infeasible","base_advanced","merge_conflict","downstream_limit","ceiling"]` and `:39` names `finalize.review-cap-check`; no Java names `downstream_limit` or `base_advanced` of the re-planning (the only `base_advanced` of the code is `register-item.schema.json:30`).
- OBSERVED: `WorkflowValidator.java:97-98`; `finalize.concurrent-wait` is named by no class of `main` yet (state catalogue only: `state-catalogue/04:184`, `workflow-dsl/04:69`).
- OBSERVED: `review-cap` occurs only in `replan-record.schema.json`, `WorkflowValidator.java`, `WorkflowValidatorTest.java`, `step-completion-firing-per-step.pmwf`, `workflow-verify.adoc` and ADRs workflow-verify/002, 007.
- HYPOTHESIS: `status.schema.json:308` needs no edit — confirm that its description names no removed kind (verify-at-outline).
- Verify-first clause: if `finalize.merge-evidence-wait` is also renamed by a merge since, the constant follows the state catalogue at the head.

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/replan-record.schema.json`
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/DeclarationBounds.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java`
- OBSERVED: `pm-workflow/src/test/resources/unit-fixtures/defaults/replan.pmwf`, `pm-workflow/src/test/resources/unit-fixtures/defaults/adjudicate-decisions.pmwf`, `pm-workflow/src/test/resources/verify/refused/step-completion-firing-per-step.pmwf`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/DeclarationBoundsTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/WorkflowValidatorTest.java`
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc`, `pm-workflow/doc/adr/workflow-verify/002-rule-9-firing-model.adoc`, `pm-workflow/doc/adr/workflow-verify/007-rule-9-derived-firing-openings.adoc`
- Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: the schema-and-store-removals plan (it names no file of this plan; start after it is on main so the specification renders consistently).
- Overlaps with: the lane-entries plan P3 on `workflow-verify.adoc` (P3 :306,371,416-417, this plan :177): run after P3 or rebase; `status.schema.json` (HYPOTHESIS entry above). PLAN-V2-126 (tests of runtime and providers) and PLAN-V2-162 touch none of these files.
- Adjacent to: `register-item.schema.json` (`base_advanced` stays), the package `re-planning` (its text edit is in the package-text plan).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-191-declaration-bounds-and-review-cap-removals.md
```

## Write-Boundary

The plan touches only its own repository source and tests; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).
