# PLAN-37: Ported-primitive tests

epic: pm-mcp-core-m2-m4
workstream: WS-06

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-37-differential-test-harness.md` and is queued as one row file, `queue/PLAN-37.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `differential-test-harness` of `doc/plans/differential-test-harness.adoc`: ported-primitive tests. Serves: Roadmap Milestone 4. Where: pm-mcp-core, test scope. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Renamed and re-scoped (pm-mcp-core #101, `98ce971`, and plan-marshall-documentation #94, 2026-10-18).** The operator's answer to the open question on the test strategy replaced the differential harness: there is no run of the Python original beside the Java primitive, and no comparison of the two results. The package keeps its file name and its slug `differential-test-harness`; its title, its bullets and its acceptance changed, and the index now gives it the status `open` with no dependency. The names `differential` in the package and file names below are the old name of the same package, not a design: a plan that creates a test package or a developer document names it after what it holds (the ported primitives and their test data), and says so in an ADR if it chooses a name for the test-scope utilities.

## Deliverables

The bullets of `doc/plans/differential-test-harness.adoc` § What, in its words (regenerated at 98ce971, after pm-mcp-core #101 rewrote the package as ported-primitive tests).

1. The convention for the tests of the `Ported` scripts: each script is adapted one at a time, with the original script and its pytest suite as the guide. The Java tests and their test data are written from it; the legacy mapping lives in test code and test data only.
2. The tests run in Java and Maven alone. The build, CI, and every workflow need no Python interpreter and no checkout of `plan-marshall`; no run compares the Java result with a Python result.
3. The original is `plan-marshall` at the commit the migration inventory pins. The pin is advanced deliberately, in one change that updates the inventory and verifies each legacy format again at the new commit.
4. First used by the `Ported` stores and primitives of this stage.

**Done when** (the package's own acceptance): One `Ported` primitive has Java tests and test data derived from its original script, and a planted difference in the primitive fails them; no production class reads a legacy format.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-TEST-2, PM-MIG-4
- Specification: Store Schemas § Ported-Primitive Tests (anchor `#ported-tests` of `store-schemas/13-leases-integrity-testing.adoc`, formerly `#differential-harness`); Migration inventory
- Watch: Store Schemas; Cross-Cutting

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-core/doc/developer/{package}.adoc` of the test package the plan names (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-core/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: none expected.** This plan is expected to add no INFO, WARN or ERROR message. If the trace matrix shows one, it goes into the holder of the module that logs it, at the next free identifier of its level (see ADR `doc/adr/005-one-log-message-holder-per-module.adoc`), and the plan says so in its inbox message.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/differential-test-harness.adoc` and `doc/plans/README.adoc` at 98ce971 (pm-mcp-core #101, 2026-10-18), which rewrote the package from a differential harness into ported-primitive tests
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show 98ce971 --stat: doc/plans/differential-test-harness.adoc and the index row rewritten by #101; the Objective, Deliverables, Done when and Trace above equal the package file read at origin/main 98ce971
- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/differential-test-harness.adoc` § What, Trace, Done When (origin/main at 98ce971)
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: doc/plans/differential-test-harness.adoc § What, Trace and Done When read at origin/main 98ce971 equal the Deliverables, trace set and acceptance above (title Ported-primitive tests, anchor #ported-tests)
- OBSERVED: the index gives this package the status `open` and no dependency (`nothing`), under the title "Tests of the ported primitives, derived from their original scripts"; before #101 it was `blocked` on a checkout of `plan-marshall` and a Python interpreter — read at `doc/plans/README.adoc` § Packages (origin/main at 98ce971)
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: doc/plans/README.adoc row differential-test-harness read at origin/main 98ce971: title Tests of the ported primitives, derived from their original scripts; status open; depends on nothing
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file (`pm-mcp-core, test scope`), not from a trace matrix; the directory names containing `differential` are the old package name and are replaced by names the plan chooses — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan: the Where line says only pm-mcp-core, test scope; the plan names the test package and its resources
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (60), `WorkflowLogMessages` (5); `PmMcpLogMessages` is deleted — read on main at 238c823 on 2026-10-16
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: CoreLogMessages has 5 records, RuntimeLogMessages 60, WorkflowLogMessages 5 at 238c823 (claim said 3, 54, 3); one holder with its own test per module and PmMcpLogMessages gone still hold; claim rewritten
- HYPOTHESIS: the package adds no INFO, WARN or ERROR message: it is a test-scope harness (its `Where` line says test scope and its Expected Surface names no `src/main` path), so it touches no log holder; should the trace matrix show production code that logs, the record goes into the holder of its module at the next free identifier as the operator decided on 2026-10-13 — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: the package is test scope only: its Where line says test scope and its Expected Surface names no src/main path, so it adds no log record; claim rewritten
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the specification sections of the trace starting set name no HTTP call of this package; pm-core and pm-workflow cannot depend on cui-http anyway
- Verify-first clause: before scoping, read the roadmap item this package serves (Milestone 4, "Establish the porting-test convention") for what the code already covers, list the open pull requests for one that names `differential-test-harness`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: row differential-test-harness is open in the index at 98ce971 with no dependency; the only open PR of the repository is #71 (a dependabot bump), none names the package

## Expected Surface

- HYPOTHESIS: the test tree of `pm-core` (`pm-core/src/test/java/de/planmarshall/core/`, and its test resources) — the package file says only: pm-mcp-core, test scope; the test package and the test resources of the ported primitives are named by the plan, not `differential`
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/` — the developer documentation of the test package the plan creates (`{package}.adoc`); created or extended
- HYPOTHESIS: `pm-core/doc/adr/` — ADRs of the package's design decisions, in the directory of the test package the plan names; new directory if absent
- OBSERVED: `doc/plans/differential-test-harness.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/store-schemas/13-leases-integrity-testing.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: nothing (the index gives the package the status `open` since pm-mcp-core #101; the earlier dependencies on a `plan-marshall` checkout and a Python interpreter fell away with the differential harness)
- Overlaps with: no other plan by package; every plan through `doc/plans/README.adoc`
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-37-differential-test-harness.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
