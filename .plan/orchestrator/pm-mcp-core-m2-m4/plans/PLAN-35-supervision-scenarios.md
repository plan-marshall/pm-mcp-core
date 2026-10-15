# PLAN-35: Deterministic supervision scenarios

epic: pm-mcp-core-m2-m4
workstream: WS-06

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-35-supervision-scenarios.md` and is queued as one row file, `queue/PLAN-35.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `supervision-scenarios` of `doc/plans/supervision-scenarios.adoc`: deterministic supervision scenarios. Serves: Roadmap Milestone 4. Where: pm-workflow, test scope, package `workflow.scenario`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/supervision-scenarios.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. `pm-scenario` scenarios of the worker faults, recycling, the start-failure guard, consultation, fencing, and a runtime crash with a leased task.
2. A worker off track, from a scripted output stream of the fixture harness: one scenario per signal (`repeating`, `no_progress`, `over_size`, `outside_footprint`), raised at its threshold and never before, and per action (`show`, `nudge`, `yield`, `restart`), taken and recorded with its signal without ending the plan or waiting for the operator (`worker-activity`).
3. A pause at once of a plan with a leased task, and the missing answer acknowledgement of a continuing worker (`question-continuation`).
4. Operator acts at operator waits are given by the scenario script; where `scenario-replay-harness` has no directive for one, the trace matrix names it and this package adds it.
5. The same faults against real processes are `plan-marshall-mcp` / `worker-integration-tests`.

**Done when** (the package's own acceptance): One scenario per fault of the fault matrix and per off-track signal and action, each replayed twice to the same stores; no scenario relies on a session or a session generation.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-TEST-4
- Specification: Workflow DSL § Deterministic Scenario Replay Harness; Model Work § Supervision & Recycling; § A Worker Off Track
- Watch: Model Work; Workflow DSL

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-workflow/doc/developer/workflow-scenario.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-workflow/doc/adr/workflow-scenario/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: none expected.** This plan is expected to add no INFO, WARN or ERROR message. If the trace matrix shows one, it goes into the holder of the module that logs it, at the next free identifier of its level (see ADR `doc/adr/005-one-log-message-holder-per-module.adoc`), and the plan says so in its inbox message.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/supervision-scenarios.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/supervision-scenarios.adoc` § What, Trace, Done When (origin/main at 7d99648)
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans package file re-read at a8a421408bfa31a4a64630c5139fd9e50bfac6f3: § What, Trace and Done When match the Deliverables above (corrected by cleanup where #70/#68/#72 changed them)
- OBSERVED: the index gives this package the status `open` and the dependencies `scenario-replay-harness`, `supervisor`, `consultation` — read at `doc/plans/README.adoc` § Packages
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row re-read at a8a421408bfa31a4a64630c5139fd9e50bfac6f3: status and dependencies match the claim
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (3 records), `RuntimeLogMessages` (54), `WorkflowLogMessages` (3); `PmMcpLogMessages` is deleted — read at branch `refactor/log-messages-per-module` head a777427 (pm-mcp-core #64, open on 2026-10-14); re-read on main before scoping
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `supervision-scenarios`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/` — test scope only
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-scenario.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-scenario/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/supervision-scenarios.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/model-work/03-supervision-and-recycling.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-30 (`supervisor`); PLAN-32 (`consultation`); PLAN-66 (`worker-activity`); PLAN-73 (`question-continuation`)
- Overlaps with: no other plan by package; every plan through `doc/plans/README.adoc`
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-35-supervision-scenarios.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
