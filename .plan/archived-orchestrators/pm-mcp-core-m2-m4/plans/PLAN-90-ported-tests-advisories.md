# PLAN-90: Close the advisories PLAN-37 left on the ported-primitive tests of the git contract

epic: pm-mcp-core-m2-m4
workstream: WS-06

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-90-ported-tests-advisories.md` and is queued as one row file, `queue/PLAN-90.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

PLAN-37 (#106, `ebcf128`, 35 files) shipped the convention for the ported-primitive tests and its first use, the tests of the working-tree digest `worktree_sha` in `pm-provider-git` (`de.planmarshall.provider.git.ported`, 17 fixtures under `src/test/resources/ported/worktree-sha/`), and left five advisory notes unfixed. The items are the plan's own statements (landing message `plan-37-ported-primitive-tests-012`); the executing plan reads the files first and drops or re-scopes any item the files refute. The orchestrator re-read two of them at `ebcf128`: the three fixture comments and the missing `@throws`.

## Deliverables

1. **Stale "Rule 6" comments** on line 2 of `deletion.txt`, `head-change.txt` and `non-ascii-path.txt` (`# Rule 6: ...`; checked): the rule numbering belongs to the list of the former differential harness; make each comment name the behaviour it pins and, where it cites the specification, the section `Store Schemas § Ported-Primitive Tests` or `Job Runtime § ...` by its heading, not by an ordinal. Check the other fixtures for the same ordinal.
2. **The nested-class sentence** at `pm-providers/pm-provider-git/doc/developer/provider-git-ported.adoc` line 218 (the entry for `WorktreeShaPortedTest`'s `equalityBehaviour` and `posixEqualityBehaviour`): the plan calls its wording about the nested classes inexact; re-read it against `WorktreeShaPortedTest` and correct it.
3. **Missing `@throws`** on `WorktreeShaScenario.run` (declares `IOException` and `GitAPIException`) and `unescape`, and on `PortedFixtures.roster`, `load` and `read` (checked: the three `@throws` tags of the `ported` package do not cover them); document each, in the form of the neighbouring Javadoc.
4. **Wording** at `pm-providers/pm-provider-git/README.adoc` line 13 (the entry for the package `de.planmarshall.provider.git.ported`): the plan calls it inexact; re-read it against the developer guide and correct it.

**Done when:** each item is fixed or shown by the files to be no defect and recorded in the pull request body; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` are green; no test changes its assertions (the planted-difference trials keep failing and the ported suite stays green); ADRs, if any, are numbered against `origin/main` after the last rebase.

**Trace starting set:**

- Requirements: PM-TEST-2
- Specification: Store Schemas § Ported-Primitive Tests; Job Runtime § Parsers & Gates
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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: none expected: comments, Javadoc and documents only. A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: PLAN-37 shipped as #106 (`ebcf128`, 35 files) and recorded the five advisories as not fixed — `landings/PLAN-37.md` and the landing message `plan-37-ported-primitive-tests-012`
  - verdict: corroborated | checked_at: ebcf128e5fcdbe39a7f3427c5f5cf28242a068f1 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show --stat ebcf128 lists 35 files under pm-provider-git; ci pr queue-state --pr-number 106: merged; the advisories are quoted from the landing message
- OBSERVED: line 2 of `deletion.txt`, `head-change.txt` and `non-ascii-path.txt` under `pm-providers/pm-provider-git/src/test/resources/ported/worktree-sha/` starts with `# Rule 6:` — `sed -n 2p` at ebcf128
  - verdict: corroborated | checked_at: ebcf128e5fcdbe39a7f3427c5f5cf28242a068f1 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: sed -n 2p of the three files at main ebcf128 prints: # Rule 6: deleting a tracked file ..., # Rule 6: a new HEAD ..., # Rule 6: non-ASCII paths count ...
- OBSERVED: `WorktreeShaScenario.run` (line 129) and `unescape` (line 224) and `PortedFixtures.load` (117), `read` (141), `roster` (255) are package-private static methods of the `ported` test package, and the package carries three `@throws` tags in all — grep at ebcf128
  - verdict: corroborated | checked_at: ebcf128e5fcdbe39a7f3427c5f5cf28242a068f1 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: grep of static run( unescape( roster( load( read( in the ported package at ebcf128 finds those five declarations; grep -c @throws over the package files totals 3
- HYPOTHESIS: the sentence at `provider-git-ported.adoc:218` and the wording at `pm-provider-git/README.adoc:13` are inexact as the plan says — confirm/refute at `pm-providers/pm-provider-git/doc/developer/provider-git-ported.adoc` § the entry for `WorktreeShaPortedTest` and `pm-providers/pm-provider-git/README.adoc` § Packages (verify-at-outline)
  - verdict: unverifiable | checked_at: ebcf128e5fcdbe39a7f3427c5f5cf28242a068f1 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the plan's statements, not re-read by the orchestrator; verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-providers/pm-provider-git`, and re-read each item at `main`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: ebcf128e5fcdbe39a7f3427c5f5cf28242a068f1 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `pm-providers/pm-provider-git/src/test/resources/ported/worktree-sha/deletion.txt` — line 2
- OBSERVED: `pm-providers/pm-provider-git/src/test/resources/ported/worktree-sha/head-change.txt` — line 2
- OBSERVED: `pm-providers/pm-provider-git/src/test/resources/ported/worktree-sha/non-ascii-path.txt` — line 2
- HYPOTHESIS: `pm-providers/pm-provider-git/doc/developer/provider-git-ported.adoc` — line 218
- HYPOTHESIS: `pm-providers/pm-provider-git/README.adoc` — line 13
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/ported/WorktreeShaScenario.java` — Javadoc of `run` and `unescape`
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/ported/PortedFixtures.java` — Javadoc of `roster`, `load` and `read`

## Dependencies and Sequencing

- Depends on: nothing — PLAN-37 shipped as #106
- Overlaps with: computed by `corpus cross-check` at emit time; PLAN-86 `provider-http-module` edits other files of `pm-providers/`
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-90-ported-tests-advisories.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
