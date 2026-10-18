# Landing Analysis: PLAN-69 — Unit frontmatter, unit types and unit entries

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #91 (https://github.com/plan-marshall/pm-mcp-core/pull/91), merged through the merge queue as squash `21872fb` on 2026-10-17/18

> Landing record for one shipped plan. Lives at `landings/PLAN-69.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `plan-69-unit-frontmatter-and-types-019.md` (`kind: landing`), corroborated by the orchestrator against `ci pr view 91`, `git show --stat 21872fb` and a comparison of the merged file list with the spec's Expected Surface. The plan id is `plan-69-unit-frontmatter-and-types`.

## Deliverable Fidelity vs Spec

The spec carries eight bullets of `doc/plans/unit-frontmatter-and-types.adoc` § What; the plan counts ten (`deliverables_total=10`, 10 done). `functional-review`: 7 met, 3 met with residuals, 0 not met. The per-deliverable verdicts of the plan are its own; the orchestrator checked what the merged tree can show.

| Spec bullet | Verdict | Evidence |
|-------------|---------|----------|
| 1-3. Frontmatter reader, closed YAML subset, fields | shipped-as-specified | `UnitFrontmatterReader` in `workflow.dsl` (CRLF handled, `isPlain` the single definition of the plain-scalar rule, per the lessons); 234 of the 266 files are under `pm-workflow/src` |
| 4. The six unit types against scope, entries, exits | shipped-as-specified | `UnitType` with `wireName()`; the type controls of `WorkflowValidatorTest` derive from `UnitType.values()` |
| 5-6. Entries, exits, stop points | shipped-as-specified | parser refuses `start` beside entries and a repeated binding; the verifier owns the rule-1 comparisons; the plan reports 22 refused verifier fixtures (15 are named `interface-*` on `main`) |
| 7. `INV-GEN-1` compares type, entries, exits, bindings | shipped-as-specified | `GeneralInvariants`; the instance-type exception holds only for the composed plan instance |
| 8. Fixtures and existing units | shipped-modified | every test unit of `pm-workflow` carries its frontmatter (the plan reports 124 migrated and 46 added; `main` holds 170 `.pmwf` test resources); the units shipped by `plan-marshall-mcp` are NOT done here, by design (owed, below) |
| Specification parts (deliverable 9 of the plan) | shipped-modified | the six specification files were left unchanged: the anchors `#unit-frontmatter`, `#unit-types` and `#interface-rules` stay as short sections; the plan marked the six write-replace steps done although nothing changed (candidate lesson 018) |

Done-when was not re-run by the orchestrator; the merge queue admitted the PR, which needs the required checks green.

## Metrics and Anomalies

- Tokens: 10,475,752 total (plan-reported, a floor for the init phase); wall clock 49,101 s (about 13 h 38 min). The largest plan of the epic so far; PLAN-72 and PLAN-75 each used about 3.9M. Not re-measurable by the orchestrator.
- Anomalies, all plan-reported: a concurrent OpenCode session regenerated the shared executor mid-plan (`manage-config domain-narrow` crashed from the OpenCode skill tree; lessons 011 and 013); five `qgate add --type warning` calls failed so five drift findings were never recorded (lesson 010); two commits were made without the change-ledger row (lesson 012); the Sourcery budget notice was filed as a review comment twice (lesson 014).

## Routing and Merge Behavior

- Review: functional review as above; code review `blocking_count=0`, `advisory_count=13`; 7 bot findings fixed before the merge (commits `0f229d4`, `116238e`). Sourcery refused for budget twice.
- CI/merge: merge queue, `cleanup_owed=false`. No rebase conflict reported.
- Surface calibration (declared against realised): the spec declared 95 entries; the merged diff touched 266 files, 13 of them not declared: `UnitGraph.java`, `AstVocabularyTest`, `PlanInstanceStoreTest`, `VerifyFixtures`, `pm-workflow/README.adoc`, the developer guides `workflow-diagram` and `workflow-plan`, the package files `phase-sub-workflows`, `process-designer` and `recipe-workflows`, and the three shared documents `Specification.adoc`, `ImplementationWatch.adoc` and `Traceability.adoc`. Four declared entries were not touched: `WorkflowLexer` and its test, `engine/package-info.java` and `specification/identifiers/02`. The declaration was a class-level HYPOTHESIS list, so this is calibration only; the spec is shipped.
- No collision with PLAN-14, 20 or 74 was reported. PLAN-14 and PLAN-69 shared `WorkflowStatus*` and the `core-store` ADR directory by declaration; PLAN-69 touched four `pm-core/src` files and two `pm-core/doc` files; whether PLAN-14 meets them is for its own rebase.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-69 --status shipped`
- [x] row `pr`, `landing`, `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-69`
- [x] epic.md narrative reconciled (queue annotation; Watches for the owed default-unit frontmatter in `plan-marshall-mcp`, the two audit findings pending a specification decision, and the breaking change for the daemon assembly)
- [x] landing message `plan-69-unit-frontmatter-and-types-019.md` archived after the reconciliation was persisted
- [x] candidate lessons `-001` to `-018`: dispositioned in the decision log (`-001` to `-007` folded as checklists into the staged specs, `-008` to `-018` forwarded to plan-marshall's `lessons-routing` inbox)
- [ ] owed outside this verb: the frontmatter of the default units in `plan-marshall-mcp` (`default-units-and-drift-tests`), without which the deployed SNAPSHOT refuses every default unit
- [x] resume anchor updated in `resume_anchor.md`
