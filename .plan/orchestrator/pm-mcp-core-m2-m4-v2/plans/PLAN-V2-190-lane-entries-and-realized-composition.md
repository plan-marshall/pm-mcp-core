# PLAN-V2-190: The lane entries follow the new specification, the composition reads the realized change, and a declared path lifts no protection

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
origin: doc-review-2026-11-24-001.md section 1 ("Lane entries", "Composition on the realized change", "Protected paths"); operator's cut, plan 3.
lane: runbook

> Staged plan spec — ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-190-lane-entries-and-realized-composition.md`; one queue row, lane `runbook`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (orchestrator confirms). Plan parameters: R06 developer documentation `pm-workflow/doc/developer/workflow-verify.adoc`, `workflow-plan.adoc`, `workflow-unit.adoc` and `pm-core/doc/developer/core-store.adoc`; ADR directories `pm-workflow/doc/adr/workflow-verify/`, `pm-workflow/doc/adr/workflow-plan/`, `pm-core/doc/adr/core-store/` (new ADRs: lane entries, composition on the realized change, protected paths; superseded ADRs get their status line); layout WS-02.

## Objective

Three mechanisms read what a plan declared or estimated where the specification now reads what happened. The deep lane enters at `refine.analyze`, the light lane at `outline.light` then `outline.light-gate`; the old entries `refine.baseline-reconcile`, `init.light-gate`, `outline.light-regate` are gone. The prunes that read the change run at the new composition event `realized`, against the realized footprint, and never read the declared footprint or `change_type`. The protected-path matcher no longer lifts a protection for a path the plan declared.

## Deliverables

Three deliverables, an estimated 50 files; below the 100-file cap, so no split is required. If the outline counts above 100, split along the deliverables: (1) lane entries, (2) composition plus protected paths.

1. **Lane entries.** `LaneEntryIds` (:26 `DEEP_ENTRY`, :29 `LIGHT_ENTRY`), `InstancePruner` (the split at the two exits of `init.ready`), `RefineInvariants` (:31,63), `InitInvariants` (:32-37,51-55,85-110, `LIGHT_REGATE`), `InvariantId` (:68-73); fixtures `unit-fixtures/defaults/{init,refine,outline}.pmwf` and `plan/per-lane-rework.pmwf`; tests `PlanInstanceComposerTest`, `CompiledInvariantsTest`, `ProjectUnitLoaderTest:353`, `InvariantFixtures:144-193`, `LaneEntryIdsTest`, `InstancePrunerTest`; docs `workflow-verify.adoc` (:306, :371, :416-417), `workflow-plan.adoc` (:157,179,485), `workflow-unit.adoc:119`; ADRs workflow-verify/029, 030 and workflow-plan/011, 012, 013, 016, 017 (superseded or amended by new ADRs). `status.schema.json:351` `escalation_trigger` drops `baseline_conflict` and `scope_lane_mismatch` (the new values: phase-workflows/02:39-52).
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
   - Test order: first the refusing cases of `InvariantFixtures`/`CompiledInvariantsTest` for the new entries (a unit whose light path skips `outline.light-gate`, one that still names `init.light-gate` as the entry); negative control: a stored status with `escalation_trigger: baseline_conflict` fails the schema (`RemovedSchemaMembersTest` style, see PLAN P1); then code, fixtures, docs.
2. **Composition on the realized change.** `composition-rules.json:95-122`: delete rule `report-change-type` and the reason `change_type_exempt`; the rule `no-code-delta` reads the realized footprint (reason `no_code_delta`, not `footprint_disjoint`); `PruneReason:27-30` drops `FOOTPRINT_DISJOINT` and `CHANGE_TYPE_EXEMPT`; `plan-instance-header.schema.json:90-91` gains event `realized` and loses the two reasons, `:23` says "realized footprint"; `CompositionEvent` gains `REALIZED` (composed in the transaction that enters `execute.verify-sweep`, again when a loop-back added tasks; Javadoc :38); `PlanFacts` (fact `footprint` = declared, :93-94,199,212,275) becomes a realized-footprint fact absent before `realized`. Design point (settle at outline): the rules have no event filter today, so the new rule must run only at `realized` — recommended: a fact that is absent before the event (`CompositionRule.Disjoint` already treats an absent footprint as indeterminate, never disjoint, `CompositionRule.java:143`), with no change of the rule grammar; alternative: an event filter on the rule. Bump `composition_rules.revision`. Tests: `CompositionRuleEvaluatorTest` (:228-269, :331, :360-373), `CompositionRulesTest` (:50, :202-209), `PlanInstanceHeaderTest`; `workflow-plan.adoc:384-389`; ADR workflow-plan/008 is superseded. Boundary: the rule `no_production_delta` and `default:land-files` stay with the package `finalize-prune-and-land-files`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first a `realized` version with a footprint of only `.adoc` files prunes `default:finalize-step-simplify` and `default:adr-propose` with `no_code_delta`, a code file keeps them, an unreadable footprint keeps them (indeterminate), `change_type: analysis` prunes nothing, a declared `footprint` in the facts changes nothing, the `start`, `outline` and `plan` versions prune nothing by code delta; negative control: a header naming `footprint_disjoint` or `change_type_exempt` is refused by the schema; then the code.
3. **Protected paths.** `ProtectedPaths` (:22-33, :53-79, :98-100) drops the `declared_files` lift (`of(paths)`, `isProtected(path)`); the family is unchanged; `ProtectedPathsTest` (:26,76,89,97-112); `core-store.adoc:2906-2914`; `references.schema.json:29` rewords the `unplanned_changes` breach sentence; ADR core-store/071 is superseded. No production caller exists (`git grep` finds tests only).
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the controls (a declared protected path is still protected; a different-case declaration lifts nothing), then the signature change.

**Done when** (against `origin/main` e451ed04): the default plan unit enters the deep lane at `refine.analyze` and the light lane through `outline.light-gate`, and a unit that routes through `init.light-gate` is refused as an unknown state — fails at e451ed04; a `realized` version prunes by the realized footprint only — fails at e451ed04 (no such event); a protected path named in the plan's declaration is reported, because the `declared_files` lift is gone — fails at e451ed04; the posture, provider, lane, `merge_by_operator` and post-run rules compose as before and the family of protected paths is unchanged — holds at e451ed04. `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, `verify -pl pm-core,pm-workflow -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Sonar: 0 open new-code findings at the head or each key selected by the operator.

## Standing Constraints

- Every removal deletes its pinning tests and adds a negative control; no read shim; schema and parity test in one commit; schema files first within a deliverable.
- A superseded ADR gets its status "Superseded by ADR NNN" in the same pull request; the new ADR numbers are the next free of each directory.
- Read cited lines at the head first; drop what a merge settled. No dependency or plugin; at most 100 files per pull request; no model-facing content.
- Sonar: fix or dismiss only on the operator's selection; an undecidable read blocks.

## Claim Labels

- OBSERVED: `LaneEntryIds.java:26,29`; `InitInvariants.java:33-35,55`; `InvariantId.java:68-73`; `RefineInvariants.java:31`; fixtures name the entries (`init.pmwf:44-48`, `refine.pmwf:19-21`, `outline.pmwf:47-49`, `per-lane-rework.pmwf:29-52`) — read there.
- OBSERVED: `status.schema.json:351` lists `baseline_conflict`, `scope_lane_mismatch`; `PruneReason.java:27-30`; `composition-rules.json:95-122` (rule `no-code-delta` reads fact `footprint`, rule `report-change-type` reads `change_type`); `composition-rules.json` revision is "2".
- OBSERVED: `CompositionEvent` has no `realized`; `plan-instance-header.schema.json:90` and `:91` carry the enums; `no_code_delta` and `no_production_delta` already exist in `PruneReason:53-55` and the header enum.
- OBSERVED: `ProtectedPaths.of(paths, declaredFiles)` and `isProtected(path, declaredFiles)` have no caller in `main` — read at `ProtectedPaths.java:56,76`; `Traceability.adoc:510` ("nothing supplies a changed protected path yet").
- OBSERVED: the specification states the target: `identifiers/02:61,68`, `phase-workflows/02:221`, `phase-workflows/03:243`, `store-schemas/14:33`, `workflow-dsl/09:33,87`, `phase-workflows/03:153` ("a path the outline declares lifts no protection").
- HYPOTHESIS: the other classes that handle composition events (`PlanInstanceComposer`, `CompositionRuleEvaluator`, `CompositionRecord`, `HeaderSchemaCheck`, `WorkflowEngine`) need an edit for `REALIZED` — confirm at `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java` (:466-492) and `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java` (verify-at-outline).
- HYPOTHESIS: the realized-footprint fact name is `realized_footprint` — confirm against `workflow-dsl/09` and the header's `inputs.realized_footprint_digest` (verify-at-outline).
- Verify-first clause: the design point of deliverable 2 is settled by the outline and recorded as an ADR before code.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/LaneEntryIds.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/RefineInvariants.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InitInvariants.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InvariantId.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/plan/InstancePruner.java`
- OBSERVED: `pm-workflow/src/test/resources/unit-fixtures/defaults/init.pmwf`, `pm-workflow/src/test/resources/unit-fixtures/defaults/refine.pmwf`, `pm-workflow/src/test/resources/unit-fixtures/defaults/outline.pmwf`, `pm-workflow/src/test/resources/plan/per-lane-rework.pmwf`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceComposerTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/plan/InstancePrunerTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/LaneEntryIdsTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/unit/ProjectUnitLoaderTest.java`
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/references.schema.json`, `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json`, `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/plan-instance-header.schema.json`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PruneReason.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionEvent.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanFacts.java`
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionRuleEvaluator.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/CompositionRuleEvaluatorTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/plan/CompositionRulesTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceHeaderTest.java`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ProtectedPaths.java`, `pm-core/src/test/java/de/planmarshall/core/store/ProtectedPathsTest.java`
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc`, `pm-workflow/doc/developer/workflow-plan.adoc`, `pm-workflow/doc/developer/workflow-unit.adoc`, `pm-core/doc/developer/core-store.adoc`
- OBSERVED (superseded or amended): `pm-workflow/doc/adr/workflow-verify/029-inv-init-2-per-lane-with-a-lane-input.adoc`, `pm-workflow/doc/adr/workflow-verify/030-lane-entry-ids-in-one-holder.adoc`, `pm-workflow/doc/adr/workflow-plan/008-rule-family-targets-derived.adoc`, `pm-workflow/doc/adr/workflow-plan/011-lane-route-pruning-deferred.adoc`, `pm-workflow/doc/adr/workflow-plan/012-per-lane-routes-and-other-lane-pruning.adoc`, `pm-workflow/doc/adr/workflow-plan/013-default-plan-unit-in-the-specification-and-test-fixtures.adoc`, `pm-workflow/doc/adr/workflow-plan/016-pruned-merge-gate-settles-in-the-engine.adoc`, `pm-workflow/doc/adr/workflow-plan/017-per-lane-rework-fixture.adoc`, `pm-core/doc/adr/core-store/071-the-protected-path-matcher-is-a-pure-core-class.adoc`
- Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: the schema-and-store-removals plan and the outline plan (all three edit `status.schema.json`); starts after both merged.
- Overlaps with: the declaration-bounds plan P4 (`workflow-verify.adoc`: P3 :306,371,416-417, P4 :177; run P4 after P3 or rebase); the finalize-prune-and-land-files package (boundary above); PLAN-V2-185 on `core-store.adoc` (185 first or rebase; sections disjoint); PLAN-V2-162 touches runtime job and worker only, none of these files.
- Adjacent to: the phase `sanity-check` feature of `phase-sub-workflows` (the lane-entry names appear in its text, edited by the package-text plan).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-190-lane-entries-and-realized-composition.md
```

## Write-Boundary

The plan touches only its own repository source and tests; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).
