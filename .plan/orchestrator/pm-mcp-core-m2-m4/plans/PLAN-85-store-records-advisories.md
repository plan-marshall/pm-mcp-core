# PLAN-85: Close the advisories PLAN-14 left on the waiver, question and project stores

epic: pm-mcp-core-m2-m4
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-85-store-records-advisories.md` and is queued as one row file, `queue/PLAN-85.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

PLAN-14 (#93, `3323ce7`) shipped the waiver, waiver-consumption and question stores, the per-project machine store and the project resolution, and recorded advisories it did not fix. The items are the plan's own statements (landing message `plan-14-operator-and-question-stores-015`); the executing plan reads the code first and drops or re-scopes any item the code refutes. The correctness items come first because a record store that accepts a malformed event stream is hard to repair once data exists.

## Deliverables

1. **A repeated waiver id in `WaiverLog`**, and **duplicate or double-pending `waiver_requests`** in the workflow status: decide per the Store Schemas specification what a second line with the same id and a second pending request for one subject mean (refused, or the last readable line wins) and enforce it with a test per case.
2. **`QuestionLog.revise` accepts a `decision` on a non-first revised line**, though the `decision` member belongs to the first question of the set; refuse it with the record's own refusal.
3. **A part asked into an existing question set makes the set unrevisable**; decide and implement what asking a part into an existing set means (refuse, or revise the set) per the specification.
4. **`ProjectResolver` hides an `enrolment_root_conflict`** when the second enrolment file is unreadable; surface the conflict, and correct ADR `core-store/037` and the developer guide, which overclaim.
5. **Untested guards** of `QuestionRecord` and `QuestionLog` get a test each that fails without the guard; the three oversized test classes are split along their nested groups.
6. **Documents**: the placeholder Alternatives sections of ADRs `core-store/033` and `034` are written, and `doc/Traceability.adoc` gets the missing entries for `PM-WF-12` and `PM-SEC-3` (verify first that this plan's code is what they trace to).

**Done when:** Each item is fixed with a test that fails without the change, or shown by the code to be no defect and recorded in the pull request body; the checklist for durable multi-step protocols of the Standing Constraints is applied to the question log; ADRs are numbered against `origin/main` after the last rebase.

**Trace starting set:**

- Requirements: PM-IMPL-2, PM-IMPL-7, PM-WF-12, PM-SEC-3 (as PLAN-14)
- Specification: Store Schemas § Plan Instance, Waivers & Questions; Filesystem Layout § Project Resolution
- Watch: none

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: PLAN-14 shipped as #93 (`3323ce7`, 62 files) and recorded the advisories as not fixed — `landings/PLAN-14.md` and the landing message
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: ci pr view 93 and git show --stat 3323ce7 on 2026-10-18; the advisories are quoted from the landing message
- OBSERVED: `WaiverLog`, `QuestionLog`, `QuestionRecord`, `ProjectResolver` exist in `pm-core` `core.store` at `3323ce7` — `git show --name-only 3323ce7`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show --name-only 3323ce7 lists WaiverLog, QuestionLog, QuestionRecord, ProjectResolver and their tests
- HYPOTHESIS: each advisory is a real defect of the merged code — the plan's statement; confirm/refute at the code (verify-at-outline). A first grep for the literal `enrolment_root_conflict` in `ProjectResolver.java` at `main` found nothing, so that item's name may differ
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the plan's statements, not re-read by the orchestrator; a grep for the literal code in ProjectResolver.java found no hit; verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that edits the stores of `core.store`, re-read each advisory at `main`, and check the Traceability entries named; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverLog.java` — repeated id
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRequest.java` — duplicate or double-pending requests
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java` — the pending-request rule
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionLog.java` — revise and ask
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecord.java` — guards
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectResolver.java` — the conflict
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WaiverLogTest.java` — cases
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/QuestionLogTest.java` — cases
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/QuestionRecordCodecTest.java` — cases
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectResolverTest.java` — the conflict case
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation; corrected
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs 033, 034, 037 corrected; new ADRs at the next free number
- OBSERVED: `doc/Traceability.adoc` — PM-WF-12 and PM-SEC-3 entries
- OBSERVED: `doc/plans/README.adoc` — no row of its own

## Dependencies and Sequencing

- Depends on: nothing — PLAN-14 shipped as #93
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-85-store-records-advisories.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
