# PLAN-81: Harden the unit frontmatter reader and the project unit loader (display spoofing, unbounded input)

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-81-unit-frontmatter-hardening.md` and is queued as one row file, `queue/PLAN-81.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Close the two audit findings that the security audit of PLAN-69 (#91) left `pending` because each needs a decision in the specification first. Both are severity `info`, neither is exploitable on its own: (1) `UnitFrontmatterReader` rejects only ISO control characters, so bidi overrides (U+202E), zero-width and other Cf characters and U+2028/2029 pass in `name`, `description` and `author`, which are shown to the operator, and `ProjectUnitLoader.checkAuthor` compares the reserved author `plan-marshall` by exact equals, so a quoted author that differs by case or a zero-width character displays like the delivered author without being refused; (2) `ProjectUnitLoader.read` reads a unit file with `Files.readString` without a size limit, `UnitFrontmatterReader.read` splits and lexes the whole source although it reads only the frontmatter, and the number of entry/exit lines and the length of an unknown key or version echoed into a refusal message are unbounded, so a repository cloned from an untrusted source can place a very large `.marshall/config` file. The audit findings are the plan's own statements (archived findings `37bbb0` and `b6bdcf`); the executing plan re-verifies them at the code.

## Deliverables

1. **Decide in the specification** (ask the operator at refine; do not choose silently): whether the frontmatter refuses Unicode format, line- and paragraph-separator characters (categories Cf, Zl, Zp) in `name`, `description`, `author` and the entry and exit descriptions, in addition to control characters, and what the closed format says for the bound of the unit file, the number of frontmatter lines and the length of text echoed into a refusal. The specification part is `doc/specification/workflow-dsl/01-grammar.adoc` § Unit Frontmatter.
2. **The reader** refuses the characters the decision names, with a refusal that names the code point and the line; `UnitFrontmatterReader.isPlain` stays the single definition of the plain-scalar rule that `WorkflowPrinter` calls.
3. **The reserved author** is compared on its stripped, NFKC-normalised, case-folded form in `ProjectUnitLoader.checkAuthor`, so `Plan-Marshall` and `plan-marshall` plus a zero-width character are refused like `plan-marshall`.
4. **The bounds**: a cap on the size of a unit file at read time in `ProjectUnitLoader.read`, a cap on the frontmatter's line count, and a cap on the length of the text echoed into a refusal message, each a named constant with the specification's value; the frontmatter is read from the head of the source without lexing the whole of it.
5. **Tests**: a refusing fixture per refused character class and per bound, a CRLF twin for each new line-oriented fixture, and the author comparison cases (case, NFKC, zero-width); the completeness test over the refusal enum derives its set from the enum.
6. **Documents**: the developer documentation of `workflow.dsl` and `workflow.unit`, an ADR per decision taken (`pm-workflow/doc/adr/workflow-dsl/` and `workflow-unit/`, next free numbers), and the specification part changed in the same pull request.

**Done when:** A frontmatter with a Cf, Zl or Zp character (as decided) is refused with its code point; an author that equals the reserved one after normalisation and case folding is refused as the reserved author; a unit file above the cap, a frontmatter above the line cap and a refusal message above the length cap are bounded as the specification states; the mutation of the specification and the code are in one pull request.

**Trace starting set:**

- Requirements: none of its own; the unit frontmatter of `PM-IMPL-5` and the reserved author of ADR `workflow-unit/005`
- Specification: Workflow DSL § Unit Frontmatter (`workflow-dsl/01-grammar.adoc`)
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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the audit of PLAN-69 recorded the two findings as `pending`, severity `info`, with `resolution_detail` empty — read in `.plan/local/archived-plans/2026-10-18-plan-69-unit-frontmatter-and-types/artifacts/findings/anti-pattern.jsonl` (hash ids `37bbb0` and `b6bdcf`)
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: read the archived findings file on 2026-10-18: both records, resolution pending, severity info, files UnitFrontmatterReader.java:397 and ProjectUnitLoader.java:219
- OBSERVED: `ProjectUnitLoader.read` calls `Files.readString` on the confined path without a size limit — read in `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProjectUnitLoader.java` at `44f904e`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show origin/main:ProjectUnitLoader.java lines 205-225 at 44f904e: Files.readString(confinedPath) with no size check
- HYPOTHESIS: `UnitFrontmatterReader` rejects only `Character.isISOControl` characters, bidi and Cf characters pass in `name`, `description` and `author`, and the reserved author is compared by exact equals — the audit's statement; confirm/refute at the code (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the audit's statement, not re-read by the orchestrator; verify-at-outline by the executing plan
- HYPOTHESIS: neither finding is exploitable on its own: the signature is carried, not verified, and the author is no authorization decision — the audit's grading; confirm/refute at the use of the author field (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the audit's grading; verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that edits `UnitFrontmatterReader`, `ProjectUnitLoader` or the Unit Frontmatter specification part, and re-read both findings at the code at `main`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java` — characters and bounds
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProjectUnitLoader.java` — author comparison and the read bound
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowPrinter.java` — only if the plain-scalar rule changes
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/UnitFrontmatterReaderTest.java` — refusing fixtures
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/ProjectUnitLoaderTest.java` — author and size cases
- HYPOTHESIS: `pm-workflow/src/test/resources/dsl/malformed/` — fixtures for the refused characters and bounds; new files
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-dsl.adoc` — developer documentation; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-dsl/` — ADRs; next free numbers
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs; next free numbers
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the unit frontmatter section is changed
- OBSERVED: `doc/plans/README.adoc` — no row of its own; this plan is not a package of the index

## Dependencies and Sequencing

- Depends on: nothing — PLAN-69 shipped as #91
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-81-unit-frontmatter-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
