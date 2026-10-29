# PLAN-91: Close the advisories PLAN-70 left on the plan instance alignment

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-91-plan-instance-alignment-advisories.md` and is queued as one row file, `queue/PLAN-91.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

PLAN-70 (#107, `21939cb`, 91 files) aligned the plan instance composer with the specification and left advisory notes unfixed. The items are the plan's own statements (landing message `plan-70-plan-instance-alignment-026`); the executing plan reads the code first and drops or re-scopes any item the code refutes. The orchestrator re-read two items at `21939cb`: the untested refused-parser arm and the duplicated lane entry id.

## Deliverables

1. **Untested arms of `PlanInstanceStore.previousHeader`** (`pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceStore.java`, line 217 at `21939cb`): its Javadoc promises `IllegalArgumentException` for a missing predecessor and `IllegalStateException` for a predecessor without a readable header or with a header that fails the schema. A test per arm asserts the documented type: a missing predecessor, a predecessor the parser refuses (the arm the last inline fix added), a predecessor that parsed but carries no instance header, and a predecessor whose header fails the schema. `grep previousHeader pm-workflow/src/test` finds no test today.
2. **Untested arms of `InstancePruner`** (the plan's statement, not re-read): the call selector and the land, adopt and unknown-lane arms; one test per arm, each asserting what is pruned and the prune reason.
3. **One definition of the lane entry ids.** `InstancePruner.LIGHT_ENTRY` and `InitInvariants.LIGHT_GATE` both hold `"init.light-gate"`, and `InstancePruner.DEEP_ENTRY` holds `"refine.baseline-reconcile"`. Define each id once, where the verifier and the pruner can both read it (or have one derive from the other), and add a test that fails when the shipped default unit names no state under either id.
4. **Per-lane rework in the fixtures.** The fixture units `plan.pmwf` and `execute.pmwf` under `pm-workflow/src/test/resources/unit-fixtures/defaults/` stay flat, so no fixture proves per-lane rework (the plan's statement; read the fixtures first). Add the fixture that proves it, or record in the pull request body why the existing ones do.
5. **Axes items 1 and 2 of Workflow DSL part 9 disagree when no level sets an axis** (the plan's statement; ADR `pm-workflow/doc/adr/workflow-plan/015`). Read both items and the ADR; state which wins and correct the other, or record in the pull request body that they agree. A change of a specification statement is a correction the plan may make in this repository (`doc/specification/workflow-dsl/09-plan-instance.adoc`).

**Done when:** each item is fixed with a test that fails without the change, or shown by the code to be no defect and recorded in the pull request body; `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green; ADRs, if any, are numbered against `origin/main` after the last rebase.

**Trace starting set:**

- Requirements: PM-IMPL-5, PM-WF-1
- Specification: Workflow DSL § Plan Instance; Store Schemas § Plan Instance, Waivers & Questions
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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: none expected: tests, one shared constant and documents. A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: PLAN-70 shipped as #107 (`21939cb`, 91 files) and recorded the advisories as not fixed — `landings/PLAN-70.md` and the landing message `plan-70-plan-instance-alignment-026`
  - verdict: corroborated | checked_at: 21939cb22cad1bbf772fc06f0f2fa2ce135ba55c | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git diff --stat 21939cb^ 21939cb: 91 files; ci pr queue-state --pr-number 107: merged, merge_commit_sha 21939cb2; the advisories are quoted from the landing message
- OBSERVED: `PlanInstanceStore.previousHeader` (line 217) has the arms missing predecessor, refused parse, no instance header and unreadable header, and no test in `pm-workflow/src/test` names it — `sed -n 200,232p` of the file and `grep -rn previousHeader pm-workflow/src/test` at 21939cb
  - verdict: corroborated | checked_at: 21939cb22cad1bbf772fc06f0f2fa2ce135ba55c | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the method body at 21939cb shows IllegalArgumentException for the missing predecessor and IllegalStateException wrapping IllegalArgumentException of HeaderJson.read; the grep over the test tree prints nothing
- OBSERVED: `InstancePruner.LIGHT_ENTRY` and `InitInvariants.LIGHT_GATE` both equal `init.light-gate`, and `DEEP_ENTRY` equals `refine.baseline-reconcile` — grep at 21939cb
  - verdict: corroborated | checked_at: 21939cb22cad1bbf772fc06f0f2fa2ce135ba55c | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: grep of InstancePruner.java lines 86 and 89 and InitInvariants.java line 50 at 21939cb
- HYPOTHESIS: the untested arms of `InstancePruner`, the flat fixture units and the disagreement of axes items 1 and 2 are as the plan says — confirm/refute at `InstancePruner.java` § its selector arms, `pm-workflow/src/test/resources/unit-fixtures/defaults/plan.pmwf` and `doc/specification/workflow-dsl/09-plan-instance.adoc` § plan behaviour axes (verify-at-outline)
  - verdict: unverifiable | checked_at: 21939cb22cad1bbf772fc06f0f2fa2ce135ba55c | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the plan's statements, not re-read by the orchestrator; verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-workflow` `workflow.plan` or `workflow.verify`, and re-read each item at `main`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 21939cb22cad1bbf772fc06f0f2fa2ce135ba55c | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceStore.java` — read; changed only if a test shows a defect
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceStoreTest.java` — the previousHeader cases
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/InstancePruner.java` — the shared entry ids
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InitInvariants.java` — the shared entry ids
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/InstancePrunerTest.java` — the selector arms
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/defaults/` — the per-lane rework fixture
- HYPOTHESIS: `doc/specification/workflow-dsl/09-plan-instance.adoc` — axes items 1 and 2, only if they disagree
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — the developer guide of the package, where the tests or the ids change what it says

## Dependencies and Sequencing

- Depends on: nothing — PLAN-70 shipped as #107
- Overlaps with: computed by `corpus cross-check` at emit time; PLAN-81 `unit-frontmatter-hardening` edits the unit loader and its guide, PLAN-87 and PLAN-85 edit `core-store` documents
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-91-plan-instance-alignment-advisories.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
