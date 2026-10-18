# PLAN-61: Tokens, not money: usage recorded, reported, and not limited

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-61-token-usage.md` and is queued as one row file, `queue/PLAN-61.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `token-usage` of `doc/plans/token-usage.adoc`: tokens, not money: usage recorded, reported, and not limited. Serves: roadmap Milestone 4 (the usage rows parsed from harness output); Client Profiles § Tokens, Not Money; the token figures of a plan, an epic, the system view, and the harness list in both clients of `pm-mcp-clients`. Where: pm-core (the usage block of the profile schema and its loader, the usage columns of the metrics rows, the unified figures and their coverage as one derivation every view uses); pm-runtime (the token facts of a running plan and of an epic, the parsing of the harness output against recorded outputs per harness and version). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** waits on packages that are not done: `profile-loader`, `slots-usage-and-budgets`, `role-qualification`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/token-usage.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Client Profiles § Tokens, Not Money, the usage pointers and the stage `usage` of § Profile Verification, Model Work § Worker Result & Usage Parsing, § Usage Reporting & Spend Classes, and the usage columns and aggregates of Store Schemas § Metrics Store (`PM-WF-14`, `PM-WORK-3`, `PM-CLIENT-5`).
2. Record everything a harness reports, report usage only: every figure the harness reports is recorded raw, so reports and limits can be added later on the same rows: input, output, cache read, cache creation, reasoning, and the harness-reported cost, with the harness, its version (the new row field `harness_version`), the model, the effort, the role, and the phase. For now the product only reports, and only tokens.
3. No money shown: no price table of the product. The harness-reported cost keeps its pointer `cost_usd`, its capability `cost_field`, its column `cost_usd` in the metrics rows, and its field in the qualification and decision calibration records: it is stored as reported, or `unmeasured`, and never shown, reported, or used in a decision; the qualification and calibration records gain tokens with the split beside it, and those are what is shown. `priced_units` stays the internal figure of the migration trigger (`PM-MIG-5`) as the specification has it and is never a figure shown to the operator: `print-phase-breakdown` and every other operator view render tokens, never `priced_units`.
4. The usage pointers: `headless.usage` gains the pointer `reasoning`, the harness's reasoning or thinking figure, filling the new column `reasoning_tokens`. A profile maps it only where the harness reports reasoning apart from its output figure; where the output figure already contains it, `reasoning` is declared not applicable, so nothing is counted twice.
5. Not reported or not applicable: each figure of `headless.usage` is a JSON Pointer, `null` (the harness does not report it: the column is `unmeasured` with the reason `not_reported_by_harness`, as today), or `not_applicable` (the harness has no such figure by design, the cache creation of Antigravity for one: the column is recorded `not_applicable` and counts as `0`). A harness that lacks a figure by design is therefore still measured; a `not_applicable` column is never written as a measured `0`.
6. The unified figures: one figure for every harness, unweighted. input is input, cache read, and cache creation; output is output and reasoning; `tokens` is input plus output. A row's figure is measured when each of its five components is measured or `not_applicable`, otherwise `unmeasured`; a partial sum is never recorded. `input_tokens` and `output_tokens` can never be `not_applicable`: profile validation refuses a profile that declares either of them so, so a measured row always rests on a reported input and output. The rows keep their raw components (Raw Components Only); the persisted `total_tokens` becomes this sum, and input and output are derived in views by one derivation of pm-core that every view calls.
7. Coverage: every aggregate (per plan, phase, role, model and harness, cause class, spend class, epic) carries the total with its split and its coverage: the count of tasks measured and unmeasured with the reasons. A total over a population with unmeasured rows is a floor (`total_is_floor: true`), shown as _at least_, never as exact.
8. A running plan and an epic: the representation of a running plan carries its tokens so far with the split and the coverage; an epic's carries the sum of its plans and of its own tasks (decompose, landing, review), with the split. Neither carries a limit, a budget line, a projection, or a comparison with one: every figure the operator sees is usage only.
9. No limits: nothing compares a plan's tokens with a line; the specification has no anchor table with fallback ceilings, no planning-time projection or `split_advice`, no re-planning trigger `spend_exceeded`, no pre-merge spend check, no remediation unit `remediation.spend` and no spend waiver, and no post-run comparison with an anchor. The code built for them (the policy class `spend`, `RemediationInvariants.SPEND_UNIT`, the spend limits of the package `de.planmarshall.workflow.spend`) was removed by `spend-removal`, for roadmap Milestone 3; this package adds no limit back. The spend classes stay (they report waste), and so do the wall-clock job budgets and the token budget by which the supervisor recycles a warm worker: they bound a job or a context, not a plan's use. An operator's own limit per plan or epic is a later change on the recorded rows and not part of this package.
10. The verification records what a harness reports: a stage `usage` of the profile verification, after `faults`, checks that each pointer resolves on the real output of the `launch` stage's worker and records, per figure, `reported`, `not_applicable`, or `missing` (the cost among them), a declared pointer that does not resolve failing the stage; the record names the harness and its version. From it the harness's usage coverage follows: `complete`, `partial` with the figures missing, or `not_reported`; `machine-onboarding-unit` runs the stage and shows the coverage in the harness list.
11. Parsing fixtures: a recorded output of each shipped harness and version (one fresh run, and the turns of a warm worker) is a test fixture with its expected row: the usage is parsed through the profile into each column, the unified figures and the coverage are derived from it; a changed output format of a harness fails its fixture. The fixtures are written for the test from recorded runs, never copied from the content of the product.
12. The evaluation: the usage measurement of the Evaluation Reference (V7, Gate 13) is repeated for every new harness and harness version before its figures are trusted; until then its profile's usage stays provisional.

**Done when** (the package's own acceptance): Every figure a harness reports lands raw in its row, the cost included, with harness, harness version, model, effort, role, and phase, and no representation or view shows a cost or a price; a fixture profile with a `not_applicable` figure yields measured rows and one with a `null` figure unmeasured rows with their reason; a harness that reports reasoning apart and one that includes it in its output give the same unified figure for the same usage; the unified figures and the coverage of every aggregate come from the one derivation, a total over unmeasured rows marked a floor; each shipped harness and version has its recorded output parsed into its expected row; the verification records per figure `reported`, `not_applicable`, or `missing`, and fails for a pointer that does not resolve; the representations of a running plan and of an epic carry their tokens with split and coverage and no limit, budget line, projection, or comparison with one.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PLN-6; UC-SYS-2; UC-ONB-4
- Requirements: PM-WF-14; PM-WORK-3; PM-CLIENT-5; PM-MIG (`priced_units`, internal)
- Specification: the sections the first bullet of _What_ names; Client Profiles § Usage Pointers; § Profile Verification; Model Work § Worker Result & Usage Parsing; § Post-Completion Integrity Invariants; Phase Workflows § Re-Planning Triggers (no spend trigger); State Catalogue § Remediation Units: Merge Conflict & Q-Gate Findings (no spend unit); Module Structure Specification (`workflow.spend`); Store Schemas § Metrics Store; § Role Qualification Record; § Decision Calibration Record; Phase Workflows § Metric Aggregates; Evaluation Reference § Results
- Watch: PM-WATCH-CLIENT-13; Model Work; Store Schemas

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/token-usage.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/token-usage.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the package file and its index row (`open`; depends on PLAN-19 (`profile-loader`); PLAN-31 (`slots-usage-and-budgets`); PLAN-34 (`role-qualification`); `entity-addressing` is done and left the index) — read at `doc/plans/token-usage.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: README.adoc row token-usage re-read at 238c823: open; depends on profile-loader, slots-usage-and-budgets, role-qualification; the claim named other dependencies (done and dropped, or added since); rewritten from the row
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/token-usage.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: row token-usage is open in the index at c281f6c; open PRs 71, 86 and 87 (ci pr view): none names the package; the dependencies not done are listed in the spec's Depends on line

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/` — Deliverable 3 adds tokens to the qualification and calibration records that `core.role` holds; added by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-profile.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-launch.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-launch/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/token-usage.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/09-completion-usage-and-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/08-dynamic-re-planning.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/11-remediation-units-merge-qgate-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/05-metrics-and-quality-report.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-19 (`profile-loader`); PLAN-31 (`slots-usage-and-budgets`); PLAN-34 (`role-qualification`)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-61-token-usage.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
