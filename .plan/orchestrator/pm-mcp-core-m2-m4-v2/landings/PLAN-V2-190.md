# Landing Analysis: PLAN-V2-190 — the lane entries follow the new specification, the composition reads the realized change, a declared path lifts no protection

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #645 (https://github.com/plan-marshall/pm-mcp-core/pull/645), merge `cf7b34b6` (squash through the merge queue, parent `18dc06fb`; the first enqueue was ejected by a race in `StoreSnapshot.readIfPresent` that this plan did not touch, fixed separately in #650), analysed on the forty-fifth run, 2026-11-28

> Landing record. Source: the plan's inbox message `plan-v2-190-lane-entries-realized-composition-025` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics) with 24 candidate lessons (001 to 024), read against git (`git diff cf7b34b6^ cf7b34b6`: 70 files, +1435/-628, pm-core and pm-workflow) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read L3-4 (`CompositionRule.java:137`, `PlanFacts.java:209-211,252`), the revision text (`workflow-plan.adoc:371,504`), the engine's event list (`workflow-engine.adoc:134`) and the absence of any composer of `realized` in `src/main` (`git grep` of `REALIZED` and `"realized"` at `cf7b34b6`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 lane entries | shipped-as-specified | `LaneEntryIds.java:29,32,35` hold `refine.analyze`, `outline.light`, `outline.light-gate` as the only literals in `src/main`; `InitInvariants`, `RefineInvariants`, `InstancePruner` read them; `InvariantId` INV_INIT_2 lists the `outline` unit (`workflow-dsl/04:188-190`); no live use of `refine.baseline-reconcile`, `init.light-gate`, `outline.light-regate` outside negative controls and ADR history |
| D1 `escalation_trigger` | shipped; the negative control is structural | `status.schema.json:350` has `budget_cap, budget_band, neighbours_indeterminate, premise, cross_module_ripple, public_symbol_deletion, null` (`phase-workflows/02:39,40,52`); `RemovedSchemaMembersTest:105-120` shows the enum lacks the values, it validates no stored status document |
| D2 event `realized` | **partly** | the enum (`CompositionEvent.java:47`), the header schema enum (`plan-instance-header.schema.json:89`) and the composer branch (`PlanInstanceComposer.java:433`) exist; NO production code in `pm-workflow/src/main` or `pm-core/src/main` composes the event: `WorkflowEngine:1396` asks the port `InstanceComposition.serverEventAt`, and the mapping of `execute.verify-sweep` to `realized` is the assembly's (ADR workflow-plan/029 "Consequences"); the Done-when "a `realized` version prunes by the realized footprint" holds at composer level only |
| D2 rule `no-code-delta` and the removals | shipped | `composition-rules.json:96-111` reads `realized_footprint` with `no_code_delta`; `report-change-type`, `FOOTPRINT_DISJOINT`, `CHANGE_TYPE_EXEMPT` gone; revision `"3"`; header schema: `realized` added, `:23` "realized footprint", "else the projection" dropped, `inputs.change_type` removed |
| D2 revision documented | partly | `workflow-plan.adoc:371` says revision `3`, `:504` still says "revision `2`" (L3-9) |
| D3 `ProtectedPaths` | shipped | `of(List)` and `isProtected(String)` carry no `declared_files` lift, the family is unchanged; `references.schema.json:29` and `core-store.adoc:2910-2936` updated |
| ADRs | shipped, two statuses too strong | new core-store/091, workflow-verify/044, workflow-plan/029; superseded core-store/071 and workflow-plan/008; amended workflow-plan/011, 012, 013, 016, 017 and workflow-verify/029, 030 (L3-10) |
| Done-when | met except the two rows above | "unknown state" shown by `CompiledInvariantsTest:347-362` (`INV-GEN-1`); "protected path reported" by `ProtectedPathsTest:37-60`; the realized cases of the spec's test order by `PlanInstanceComposerTest:557-725`; "composes as before" shown only by unchanged tests |

## Three-Layer Analysis

### Layer 1 — Specified

All three deliverables shipped; one row (the event composed) holds only at composer level, one (the revision text) is stale in one line. The rest of the sweep is forced by deleting a record component.

### Layer 2 — Implemented

70 files, +1435/-628. Files outside the Expected Surface: the main code of the removed `changeType` and of the digest move (`InstanceInputs`, `InstanceHeaderReader`, `CompositionInputs`, `CompositionRecord`, `CompositionRequest`, `CompositionRules`, `HeaderJson`, `HeaderSchemaCheck`), 16 tests and 2 fixtures that call the changed records positionally (the six consumer-sweep misses the lessons name). Behaviour beyond the spec, recorded in ADR workflow-plan/029: `CompositionRequest.CARRYING_EVENTS` with `checkRealizedFootprint`, and `realizedFootprintDigest` removed from the public record. Citations sampled: 15, 11 exact, 3 imprecise, 1 wrong (`workflow-plan.adoc:504`). Every removal has its negative control (`CompositionRulesTest:416-420`, `HeaderSchemaCheckTest:482-484,258`, `PlanInstanceHeaderTest:681,699`, `LaneEntryIdsTest:69`, `ProtectedPathsTest:37-75` with a signature check).

### Layer 3 — Correct

No major defect. The checks that hold: a fact absent before `realized` is indeterminate for `disjoint` (`CompositionRule.Disjoint.holds` `:165-171`); `PlanInstanceComposer.factsRead` (`:810-817`) masks the fact at start, outline, plan and replan; an old header naming a removed reason or `inputs.change_type` is refused (`HeaderJson:131` runs the schema check before `PruneReason::fromWireName`); the holder is single-sourced; the INV-INIT-2 ends set (`InitInvariants:917`) exempts escalation and fallbacks.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-4 | `CompositionRule.java:137`, `PlanFacts.java:209-211,252` | **re-read:** `Unset.holds` is `facts.text(fact).isEmpty() && facts.list(fact).isEmpty()`; `list("realized_footprint")` returns `Optional.ofNullable(realizedFootprint)` and `withoutRealizedFootprint()` nulls it, so a rule `{fact: realized_footprint, unset: true}` holds at start, outline, plan, replan (prunes before realization) and for an unreadable footprint at `realized`; the fact is registered for every operator (`CompositionRules:80,280,369`); the only shipped `unset` rule is `integrations.sonar`; this is the CodeRabbit comment f01e00 | a future rule prunes before realization; the Javadoc and `workflow-plan.adoc:396` overclaim | minor (latent) |
| L3-5 | `PlanFacts.java:263`, `CompositionRequest.checkRealizedFootprint` | the digest is taken in the order supplied | the same paths in another order are refused as another digest | minor |
| L3-6 | `CompositionRequest.java:149-154` | the carrying-event refusal throws `IllegalArgumentException` from the constructor; `workflow-plan.adoc` (Purpose) says a refused version is returned as a value; the spec states no such refusal, ADR 029 records the addition | an assembly that forgets the footprint at a waiver after `realized` fails hard | minor |
| L3-7 | `PlanInstanceComposerTest:708` | a `replan` after `realized` restores the pruned steps and relies on the next `realized`, which nothing here composes | follows the spec; depends on L3-4's first row | info |
| L3-8 | `workflow-engine.adoc:134` | **re-read:** the server events list omits `realized` | stale statement | minor |
| L3-9 | `workflow-plan.adoc:504` | **re-read:** "revision `2`" against `:371` "revision `3`" | stale statement | minor |
| L3-10 | ADR core-store/071, workflow-plan/008 | plain "Superseded by" although ADR 091 keeps 071's placement and matching rules and `workflow-plan.adoc:301` still cites 008 as live | the status is stronger than the facts | minor |
| L3-11 | `doc/implementation-watch/store-schemas.adoc:528-530` | the retrospective's advisory is FALSE: the hit is a plan-marshall component name in a lesson fixture, not a state | none | clean |
| L3-12 | `doc/Traceability.adoc` | not updated: `:525` cites only ADR 071; the plan-instance Documentation lines cite no workflow-plan/029 or workflow-verify/044; `:3022-3023` stop before 029, 044, 091; the Implementation blocks `:294-316` and `:2900-2931` do not name `realized`; `doc/plans/commit-boundary.adoc:16` still describes the lift as present | the shipped gap the retrospective reports | minor |
| imprecise | `PlanFacts` Javadoc, `workflow-plan.adoc:396`, ADR 029 | "never holds there" and "an absent list is indeterminate" are true for `disjoint`, `equals`, `in`, `empty` and false for `unset` | folded into L3-4 | info |

**Layer 3 verdict:** findings — 9 points (six minor, three info) and one false advisory; not shown by reading — compilation, the tests, `verify -Ppre-commit`, coverage, integration tests, Sonar, whether the merge-queue ejection is independent of the diff, the assembly side (`InstanceComposition` mapping `execute.verify-sweep` to `realized`), and the "composes as before" row beyond unchanged tests. No major finding, nothing reported at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| Standing Constraints (this plan) | every removal deletes its pinning tests and adds a negative control | conforms | the list in Layer 2 |
| Standing Constraints (this plan) | a superseded ADR gets its status line in the same pull request | conforms | with the strength question L3-10 |
| CLAUDE.md Module Documentation | developer documentation and one ADR per decision | partly | `workflow-engine.adoc` and `workflow-plan.adoc:504` stale |
| Project Setup | the plan changes `doc/Traceability.adoc` in its own pull request | not met | L3-12 |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `cf7b34b6` after one ejection and a re-enqueue |

## Metrics and Anomalies

- Harness claude, model `claude-sonnet-5-5`, effort medium, plugin `0.1.2184`, executor `0.1.2184`; `total_tokens=21986793`, `total_wall_seconds=66504` (18 h 28 min); `complete: false` for three keys, all unobserved facts recorded `unknown`, never 0: `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`. `sonar_open_new_issues=0` is read from the quality-gate comment on the merged head and from `sonar-roundtrip`, not from a dedicated merge-gate read.
- code review: 0 blocking, 8 advisories, 8 rules cited. CodeRabbit (required) refused once on quota and was re-triggered by the operator's choice; sourcery refused on size (150000 diff characters).
- Merge-queue ejection: `DerivedFactStoreConcurrencyTest.twoRefreshersOfOneKey` (JDK 27 build), a race in `StoreSnapshot.readIfPresent`; fixed by #650 (`18dc06fb`), then #645 was re-enqueued.
- Reported by the lane's own finalize: 2482 work-log entries; four script-failure clusters (`manage-findings`, `manage-solution-outline`, `manage-status`, `phase_handshake`) whose classification the lessons-capture step deferred (the retrospective's lesson 024 says it classified them deterministically from `script-execution.log`).

## Routing and Merge Behavior

- Merge: through the merge queue as `cf7b34b6`.
- Candidate lessons: 001 to 013 and 016 to 024 (the `weakened_test` false positives of the functional review on removed behaviour and a rename, the outline's consumer-sweep misses of positional record constructors, the scope validator on `doc/Traceability.adoc`, the four deferred script-failure clusters, `--step-id` on the dispatch-boundary rows, a missing record-step row for the orchestrator-tier quality gate, `architecture search --content` not reaching `*.pmwf` and `.json`, zero usage on resumed-session boundary rows, and the classification of the four clusters) forwarded verbatim to the plan-marshall `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-081` to `-102` (uncommitted in that ledger until its land). 014 and 015 are the two CodeRabbit comments about THIS repository (b4c4e8, f01e00) and are handled below, not forwarded.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#645`; `landing` `landings/PLAN-V2-190.md`; `plan_marshall_plan_id` `plan-v2-190-lane-entries-realized-composition`
- [x] the 25 messages archived (the lessons forwarded first, 014 and 015 read into this record)
- [x] every minor and info point staged as carried item 84 of the staged PLAN-V2-161 (the workflow engine and DSL residue): the revision line, the engine event list, the two ADR statuses, the `PlanFacts` Javadoc, the Traceability lines, `commit-boundary.adoc:16`, the latent `Unset` prune (test first, R07 owner question at the refine), the digest order, the constructor refusal
- [x] the Decisions entry and the Open Defects written; resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** PLAN-V2-161 carried item 84.
- **Owed outside this repository:** the assembly's `InstanceComposition` mapping of `execute.verify-sweep` to `realized` (`plan-marshall-mcp`): until it exists no `realized` version is composed and the rule `no-code-delta` never fires in a running plan; the live behaviour of the lane entries in the assembly.
- **Owner question (recorded, asked one at a time):** L8, the `unset` operator on `realized_footprint` (refuse at parse time, 70%).
- **b4c4e8:** the sentence in `references.schema.json:29` is backed by `store-schemas/13:56`, nothing here enforces it and `ProtectedPaths` has no production caller; accepting it unchanged is consistent. Whether the writer of `declared_files` outside this repository enforces it is owed to the assembly.
