# PLAN-83: Restoring the service unit when a new one does not load

epic: pm-mcp-core-m2-m4
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-83-service-unit-restore.md` and is queued as one row file, `queue/PLAN-83.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `service-unit-restore` of `doc/plans/service-unit-restore.adoc`: restoring the service unit when a new one does not load. Serves: the upgrade of `runtime-control` (a failure before the switch restores the previous service unit); roadmap Milestone 10. Where: pm-runtime (`runtime.start`: `ServiceUnitInstaller`, `ServiceUnitFile`). The package was registered by the operator's pull request #97 (`5796aa9`) outside the orchestrator and recorded here on 2026-10-18; the package file is the brief. `ServiceUnitInstaller.install` writes the new unit over the existing one and then loads it; when loading fails, the new unit stays written and the failure is reported (its Javadoc: "install can be run again"), as ADR 003 decided. The upgrade needs the opposite: Runtime Lifecycle § Upgrade states that a failure before the switch, the service unit included, is undone before it is reported, "the previous service unit restored and reloaded, so the installation in force is unchanged". Found by the review of `pm-mcp-core` #84.   is installed).   (or, when there was none, removes the new file and unloads nothing), and then reports the load failure with the   restore's own result; a restore that fails too is reported with both failures, the first one thrown and the   second suppressed, as `uninstall` already does.   ADR 003); on macOS, where a reinstall from a runtime started by the unit ends that runtime between unload and   load, the restore is the next start's first step: the kept bytes are written beside the unit before the load, and   a start that finds them with a unit that did not load restores them.   documented "run it again" behaviour, and record the decision in ADR 003's successor; the upgrade path restores in   every case.

## Deliverables

1. Keep the previous unit: before writing, the installer keeps the bytes of the unit in force (none when no unit
2. Restore on a failed load: when loading the new unit fails, the installer writes the kept bytes back and loads them
3. Order against self-termination: the file work stays before every command that can end the caller (the reason of
4. Plain install: decide in the plan whether `pm-operator runtime install` restores as well or keeps its

**Done when:** With the recording fake of the service manager: a failing load after replacing a unit leaves the previous unit's
bytes in place and loaded, a failing load on a machine without a unit leaves no unit file, and a failing restore is
reported with both failures; on macOS the start after an interrupted reinstall restores the kept unit; the decision for
plain install is recorded in an ADR; `verify` and `verify -Pcoverage` green.

**Trace starting set:**

- Requirements: PM-DIST-1 (upgrade); PM-ARCH-7 (the runtime's lifecycle)
- Specification: Runtime Lifecycle § Upgrade; CLI & Installer § Installation Layout
- Code: `pm-runtime/src/main/java/de/planmarshall/runtime/start/ServiceUnitInstaller.java` and its test

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the package file `doc/plans/service-unit-restore.adoc` and its index row (`open`, depends on nothing, Milestone 10) came with pm-mcp-core #97 (`5796aa9`) — read at `origin/main`
  - verdict: corroborated | checked_at: 5796aa9704052fcd3a8ea89fe27f802ce8648e31 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: package file and README row read at 5796aa9: 4 bullets, open, depends on nothing
- HYPOTHESIS: the Expected Surface is inferred from the `Where` line (`runtime.start`: `ServiceUnitInstaller`, `ServiceUnitFile`) and ADR `runtime-start/003`, not from a trace matrix — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 5796aa9704052fcd3a8ea89fe27f802ce8648e31 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, list the open pull requests for one that names `service-unit-restore` or edits `ServiceUnitInstaller`, and check that the row is still open and depends on nothing; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 3323ce7bfb2875b94b95dbeba5063171901f9c69 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row service-unit-restore is open in the index at 3323ce7; open PRs #71 (ci pr view): none names the package; the dependencies not done are listed in the spec's Depends on line

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/ServiceUnitInstaller.java` — keep the previous unit and restore on a failed load
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/ServiceUnitFile.java` — the kept bytes beside the unit
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/start/ServiceUnitInstallerTest.java` — restore cases, including a failing restore
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-start.adoc` — developer documentation; extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-start/` — ADR superseding the install-and-report rule of ADR 003; next free number
- OBSERVED: `doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc` — Upgrade: the rollback statement the package implements
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/service-unit-restore.adoc` — the package file, deleted when the package is done

## Dependencies and Sequencing

- Depends on: nothing — the index row depends on nothing
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-83-service-unit-restore.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
