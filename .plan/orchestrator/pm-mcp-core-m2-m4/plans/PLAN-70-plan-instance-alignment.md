# PLAN-70: Plan instance aligned with the specification: prune reasons, shipped rules, plan axes, header, and diff

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-70-plan-instance-alignment.md` and is queued as one row file, `queue/PLAN-70.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `plan-instance-alignment` of `doc/plans/plan-instance-alignment.adoc`: plan instance aligned with the specification: prune reasons, shipped rules, plan axes, header, and diff. Serves: Roadmap Milestone 3 (the plan instance: the prune reasons `plan_default` and `merge_by_operator` in place of the shipped rules on `merge.auto_merge` and `recipe_id`, the adjudication unit in the header, `diff_summary` of the composition log). Where: pm-workflow package `workflow.plan` (`PruneReason`, `CompositionCause`, `composition-rules.json`, `CompositionRules`, `PlanFacts`, `PlanInstanceComposer`, `HeaderSchemaCheck`, `CompositionRecord`) and package `workflow.verify` (the declaration schemas of the method and asking variants in `DeclarationBounds`). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/plan-instance-alignment.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

Context: The composer of `plan-instance` implements an earlier form of the header and of the shipped rule file. This package aligns the code with Store Schemas § Plan Instance, § Composition Rules, and Workflow DSL § Plan Behaviour Axes as far as the composition reads them, without a deprecation (pre-1.0). The engine's side of the versions is `plan-instance-versions`'s.

1. The closed reasons: `PruneReason` loses `recipe_override` and `auto_merge_off` and gains `plan_default` and `merge_by_operator`, so it equals the `prune_reason` enum of the header schema (`posture`, `provider_unconfigured`, `footprint_disjoint`, `change_type_exempt`, `local_only`, `land_lane`, `adopt`, `merge_by_operator`, `plan_default`, `returned`, `handed_back`, `empty_harvest`); `CompositionCause.FallbackCause` loses `question_expired` (no decision ends by time) and `auto_merge_off`, and gains `merge_by_operator` and `files_not_applicable`, so it equals `fallback_cause` of the composition record; `HeaderSchemaCheck` and the header's element `call` (a pruned call of a phase sub-workflow or an axis variant) follow.
2. The shipped rule file: the rules `auto-merge-off` (fact `merge.auto_merge`) and `simplify-recipe` (fact `recipe_id`, reason `recipe_override`) leave `composition-rules.json`, and with them the configuration fact `merge.auto_merge` of `CompositionRules` and `PlanFacts.RECIPE_ID_FACT`; the simplify recipe is its own workflow (`recipe-workflows`). A new rule prunes the merge execution (`finalize.merge-dispatch`, `finalize.merge-queue-wait`, `finalize.merge-queue-exit`) with the reason `merge_by_operator` where the bound asking variant declares `self_merge: false`, its source fact the bound variant; the file's `revision` is raised.
3. The plan axes in the composition: a rule's `when` may read the variant bound on a behaviour axis and its declarations; the declaration schemas of the method variants (`order`) and the asking variants (`wait_after`, `outline_review`, `self_merge`) join the compiled schemas of `DeclarationBounds`; the composer binds the variant of each axis from the plan's own value (`overrides.json`), else the project's, else the machine's, where a level sets it, and prunes the variants not chosen with the reason `plan_default` as pruned calls; `overrides.json` is read as a plan fact and counted in `plan_facts_digest`; a value of the plan set or changed later composes a version with the event `override`, refused with `position_not_contained` where it would not contain the plan's position. Writing the machine's and the project's values and the plan's overrides is `machine-onboarding-unit`'s and `plan-and-epic-control`'s; the landing variant composed into the file of `phase-6-finalize`, and one composed file per phase sub-workflow and bound variant, are `phase-sub-workflows`'s.
4. The adjudication unit in the header: `adjudicate.decisions`, which no call site calls and the engine runs for the decisions of the version, is listed in `inputs.sub_units` with its digest and its level, and its verified text is stored like every bound sub-unit.
5. `diff_summary`: each composition record of a composed version carries the pruned elements added and removed, the ceilings changed, and the gates waived or unwaived relative to the previous version, `null` for version 1; the specification names only the content, so the package states the members of the object in the schema of `plan-marshall-documentation` before it writes them.
6. The documents: the developer guide of `workflow.plan` and ADR `workflow-plan/008` without the shipped rules on `merge.auto_merge` and `recipe_id`; in `plan-marshall-documentation` the parts of `PM-IMPL-5` and of the roadmap item of the plan instance that this package implements.

**Done when** (the package's own acceptance): `PruneReason` and `FallbackCause` equal their enums of the specification, and a header or a composition record with `recipe_override`, `auto_merge_off`, or `question_expired` is refused; the shipped rule file has no rule on `merge.auto_merge` or `recipe_id`, and a fixture plan whose asking variant declares `self_merge: false` loses the merge execution with `merge_by_operator` while one with `self_merge: true` keeps it; a fixture with an axis set at plan, project, and machine level binds the plan's variant and prunes the others as calls with `plan_default`, and an override after the start composes an `override` version or is refused with `position_not_contained`; the header lists `adjudicate.decisions` with its digest; each composed version after the first records its `diff_summary`; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-5; PM-WF-25; PM-WF-1
- Specification: the sections the introduction names; Workflow DSL § Composition Rules; § Plan Overrides; Store Schemas § Plan Overrides; § Gap Record (the gap causes); Identifier Registry § Prune reason
- Watch: PM-WATCH-PHASE-89; Workflow DSL

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/plan-instance-alignment.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that names `plan-instance-alignment`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/plan-instance-alignment.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/identifiers/02-registry-stores-to-web.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-70-plan-instance-alignment.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
