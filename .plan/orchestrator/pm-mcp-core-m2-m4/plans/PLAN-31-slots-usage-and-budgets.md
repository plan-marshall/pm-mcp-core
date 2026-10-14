# PLAN-31: Model slots, usage rows and learned budgets

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-31-slots-usage-and-budgets.md` and is queued as one row file, `queue/PLAN-31.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `slots-usage-and-budgets` of `doc/plans/slots-usage-and-budgets.adoc` — model slots, usage rows and learned budgets — as one pull request in this repository with its documentation pull request in `plan-marshall-documentation`. It serves Roadmap Milestone 4 and lands in: pm-runtime; pm-core package `core.store`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/slots-usage-and-budgets.adoc` § What, in its words. A bullet that states what already exists or what follows in a later milestone is context, not a deliverable.

1. The machine model slots beside the build slot scheduler.
2. Usage rows per task and job, parsed from the harness output through the profile's pointers; the per-role learned worker budget.
3. The minimal writers and readers of this milestone: worker metrics, change ledger, learned budgets, qualification settings.

**Done when** (the package's own acceptance): Usage of a recorded harness output lands in the metrics row with each column; a missing figure is recorded unmeasured with its reason; slots never exceed their count under concurrent claims.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Requirements: PM-WORK-3, PM-EXEC-3
- Specification: Timeouts § Job Budgets; § Worker Budget; Store Schemas § Metrics & Quality Report
- Watch: Model Work; Store Schemas

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request sets the package's row to `done` within its commits. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents.** Javadoc, `package-info.java` and the work package file live here. Requirement and specification documents, the user documentation, the deletion of implemented specification sections and watch items and the roadmap update are made directly in `plan-marshall-documentation` by this plan, in a pull request there that need not name the code pull request; merging it is the operator's.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-runtime/doc/developer/runtime-launch.adoc`, `pm-core/doc/developer/core-store.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-runtime/doc/adr/runtime-launch/`, `pm-core/doc/adr/core-store/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of `plan-marshall-documentation`, under the holder's section, in the documentation pull request.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/slots-usage-and-budgets.adoc` § What, Trace, Done When (origin/main at 7d99648)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/slots-usage-and-budgets.adoc exists at fa58b0d (presence checked, content not re-read)
- OBSERVED: the index gives this package the status `open` and the dependencies `worker-launch`, `store-substrate` — read at `doc/plans/README.adoc` § Packages
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row of slots-usage-and-budgets still reads open as claimed at fa58b0d; dependencies cell: link:worker-launch.adoc[`worker-launch`]; link:store-substrate.adoc[`store-substrate`]
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing on 2026-10-09
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-runtime/src/main/java/de/planmarshall/runtime/launch/ still holds only package-info.java at fa58b0d
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` exists and holds `ConfinementOutcome.java`, `PathConfinement.java`, `WorkspaceRoots.java`, `package-info.java` today, so the package is an extension of those declarations — directory listing on 2026-10-09
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- HYPOTHESIS: part of this package lands in `pm-runtime` package `runtime.launch`; the package file does not name it, but the description of that package in the package table covers the subject — confirm/refute at `../plan-marshall-documentation/doc/specification/module-structure.adoc` § the package table of `pm-runtime` (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (3 records), `RuntimeLogMessages` (54), `WorkflowLogMessages` (3); `PmMcpLogMessages` is deleted — read at branch `refactor/log-messages-per-module` head a777427 (pm-mcp-core #64, open on 2026-10-14); re-read on main before scoping
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `slots-usage-and-budgets`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` — production code; package inferred, verify-at-outline
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/` — tests; new directory; package inferred, verify-at-outline
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/` — tests
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-launch.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-launch/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent

## Dependencies and Sequencing

- Depends on: PLAN-29 (`worker-launch`); PLAN-06 (`store-substrate`)
- Overlaps with: PLAN-01 (`lock-manager-and-leases`) in `core.store`; PLAN-03 (`workspace-confinement`) in `core.store`; PLAN-06 (`store-substrate`) in `core.store`; PLAN-09 (`audit-trail`) in `core.store`; PLAN-11 (`enrolment-store`) in `core.store`; PLAN-12 (`session-log-and-generations`) in `core.store`; PLAN-13 (`workflow-engine`) in `core.store`; PLAN-14 (`operator-and-question-stores`) in `core.store`; PLAN-15 (`project-machine-store`) in `core.store`; PLAN-20 (`job-runner`) in `core.store`; PLAN-23 (`stalled-scope-detection`) in `core.store`; PLAN-29 (`worker-launch`) in `runtime.launch`; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `doc/LogMessages.adoc` of `plan-marshall-documentation`, which goes into the documentation pull request

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-31-slots-usage-and-budgets.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
