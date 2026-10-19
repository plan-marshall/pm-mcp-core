# PLAN-43: Phases as sub-workflows, and the plan's history

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-43-phase-sub-workflows.md` and is queued as one row file, `queue/PLAN-43.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `phase-sub-workflows` of `doc/plans/phase-sub-workflows.adoc`: phases as sub-workflows, and the plan's history. Serves: roadmap Milestone 3 (the remainder of the loader of project-owned units: the plan unit composed of its six phase sub-workflows); Use Cases, UC-PLN-1; the drill-down of a plan in both clients of `pm-mcp-clients`. Where: pm-workflow (units, composition, engine, static verifier, diagram); pm-core (the plan's history). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** waits on packages that are not done: `operator-wait-and-machine-scope`, `unit-frontmatter-and-types`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/phase-sub-workflows.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Store Schemas § Phases as Sub-Workflows and the Phase of a Record, State Catalogue § Plan Unit `phases`, and Store Schemas § The Plan's History, with § The Start of the Current Phase (`PM-WF-16`, `PM-IMPL-5`).
2. Phases as sub-workflows: the plan unit (`plan.pmwf`, unit id `plan`, its call states `phases.*`) becomes a workflow of the six phases `init`, `refine`, `outline`, `plan`, `execute`, and `finalize`, each the sub-workflow `phase-1-init.pmwf` … `phase-6-finalize.pmwf` the Phase Workflows specify, with the entries and exits of its frontmatter (`start` for every phase, `rework` of `outline`, `land` and `reentry` of `execute`, `adopt`, `hand_back`, and `post_run` of `finalize`), called by the plan unit at the entry its call state names; the frontmatter, `entry <name> -> <state>`, `call <unit> entry <name>`, and the interface rules are `unit-frontmatter-and-types`'s; a loop-back a call of a phase again, a re-entry from finalize into execute an outcome of finalize. The `phase` clause of a state (`StateNode.phase`, `phase inherit`) and every mapping of a state to its phase go, and with the merge of the phase files `PlanUnitMerger` and `partVersions`; the call stack holds at most five frames, the phase level adding one frame to every call path: the code's limit of four goes up to five in every place that states it, the status store (`WorkflowStatus.MAX_CALL_DEPTH`), the engine's `CallStack.MAX_DEPTH` and its push, and the static verifier's depth rule (`WorkflowValidator`, `VerificationRule`), so a fifth frame is admitted and a sixth refused, statically and at run time alike; the plan instance composes the phases, one instance file per composed unit, and prunes a phase as a call, with its reason.
3. Lanes as a choice of phases: a lane is never a phase. The land lane calls no refine, outline, or plan phase, and its `land.*` states (today `land.pmwf`, run in phase `execute`) become a sub-workflow called within the phase `execute`; the adopt lane calls `init` and then `finalize`.
4. The phase of a sub-workflow: a plan's phase is the phase sub-workflow on its call stack, and every sub-workflow called within a phase (a remediation unit, `land.*`, a chat) has that phase; no unit and no state declares a phase of its own, the entity units included.
5. The plan's history: an append-only history per plan of states entered and left, sub-workflows called and returned, with their outcomes, the instance version in force, and the time; archived with the plan; the runtime derives each node's state from it: done, running, planned, waiting for the operator, failed (with its outcome), not taken, and, for a pruned phase, not in this plan with its reason.
6. Where `workflow-engine`, `plan-instance`, or `project-units-and-invariants` have already implemented the flat plan unit, this package restructures them.

**Done when** (the package's own acceptance): Every phase is a sub-workflow the plan unit calls, and the static verifier and the fuzzer pass over the default units; the plan unit enters each phase at the entry its call state names; a test per lane shows the phases it calls and prunes, the land lane its `land.*` sub-workflow within `execute`; a sub-workflow called within a phase reports that phase; a call path of five frames is accepted by the static verifier and pushed by the engine, and one of six refused by both; a loop-back calls a phase again and the history records both runs; a plan's history reproduces the node states of its process, also failed and not taken, after a runtime restart.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-PLN-1
- Requirements: PM-WF-16; PM-IMPL-5
- Specification: the sections the first bullet of _What_ names; Workflow DSL § Workflow Definition Units; § Interface Rules; § Reusable Sub-Workflows; § Plan Instance; Phase Workflows; § Land Lane

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
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.
- **Checklist for durable multi-step protocols in `pm-runtime` and `core.store` (found by PLAN-74, 2026-10-18; four review rounds each found another unguarded transition of the credential-store move).** (1) A resume decides from the pair (record phase, state actually recorded), never from the phase alone, and a guard added to one phase branch gets its symmetric guard on every other branch; enumerate (phase x recorded state x writer) with a test per cell before the first review. (2) While a durable in-progress record exists, every other writer of the state it switches is refused under the same lock, and an authorization is verified against durable state, never asserted by the caller. (3) The record that drives recovery is deleted last, after every side effect (audit event, clean-up after a failure) is durable. (4) A decision with side effects (which store, which key) is decided once and persisted, never re-derived per call from a probe that can flap. (5) A copy-then-switch move has a test with a pre-populated target (an overlapping key and a target-only key) and a failure injected after the copy; the pre-existing-target hazard is checked for every kind of object the protocol copies (entries, integrity key, sidecars) in the same task. (6) A store's temp-file namespace is disjoint from its legal entry-name namespace. (7) A fix of a state-machine transition lists the specification section, the ADR, the Javadoc and the test that state it among its affected files and updates them in the same commit; ADR and test are checked against the specification text, not against the code they document. (8) The invariants of a multi-write protocol are stated per crash window, not per phase. (9) A test that asserts a file location derives the expected path from the production layout independently, never from the object under test. (10) A native binding is loaded once; only what can change at runtime (a lock state) is probed per operation.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/phase-sub-workflows.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/phase-sub-workflows.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-69 (`unit-frontmatter-and-types`); `workflow-engine` and `plan-instance` are done and left the index) — read at `doc/plans/phase-sub-workflows.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: README.adoc row phase-sub-workflows re-read at 238c823: open; depends on operator-wait-and-machine-scope, unit-frontmatter-and-types; the claim named other dependencies (done and dropped, or added since); rewritten from the row
- OBSERVED: the call-graph depth limit of 4 (Rule 7, `CALL_GRAPH_DAG`) shipped with PLAN-41 (#34); this package grows the call depth by one — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: VerificationRule.CALL_GRAPH_DAG(7) states a maximum depth of 4; WorkflowValidator.checkDagDfs and WorkflowStatus.MAX_CALL_DEPTH=4 follow it; shipped with #34
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the plan's history lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/phase-sub-workflows.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row phase-sub-workflows is open in the index at 98ce971; open PRs (ci pr list): #71 (a dependabot bump) only, none names the package; doc/plans/phase-sub-workflows.adoc is unchanged since 3323ce7; code claims of the spec were not re-read

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — the plan unit as a workflow of phases
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — the call stack and the history
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — call depth and phase rules
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — composition and pruning
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/` — phase drill-down
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — the plan's history
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/` — Deliverable 2 removes `StateNode.phase`, the phase clause, phase inherit and `partVersions` of `WorkflowDefinition`; added by cleanup on 2026-10-16
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/` — the parser side of the same removal; added by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-diagram.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-diagram/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/phase-sub-workflows.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/phase-workflows/01-planning-phases-1-2.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/02-planning-lanes-phases-3-4.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/18-plan-unit-axes-short-landings.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/03-findings-and-review-records.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-69 (`unit-frontmatter-and-types`)
- Restructures: the flat plan unit of PLAN-13, PLAN-17 and PLAN-24; Rule 7's depth limit of PLAN-41

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-43-phase-sub-workflows.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
