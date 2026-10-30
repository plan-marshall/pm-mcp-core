# Standing Rules of the Epic pm-mcp-core-m2-m4-v2

Revision: **r4 (2026-10-30)** (R20 added: the coding skills of the marketplace; r3, 2026-10-29, R05 amended to condense and point; r2, 2026-10-27, added R19; r1, 2026-10-22, held R01 to R18). The single source of the rules that bind every plan of this epic. Before 2026-10-22 the same text was copied into the section *Standing Constraints* of each plan spec; it now lives here once, and each spec names the rules that apply to it in its *First Instruction*.

Plan ids with the prefix `PLAN-V2-` are plans of this epic; a bare `PLAN-NN` (without `V2`) in a rule or a spec names a plan of the closed predecessor epic `pm-mcp-core-m2-m4` (its landings and history), never a plan of this epic.

## How to read these rules

- The rules were set by the operator for every plan of this epic from 2026-10-09 on, beside the rules of the `CLAUDE.md` of the repository; later ones carry their date and the plan that found them. They bind a plan exactly as if they stood in its spec.
- A spec names the rules that apply to it (*Rules that apply to this plan*) and gives the values the parametrised rules ask for (*Plan parameters*). A rule with a scope line applies only where the scope fits; the spec's list already reflects it.
- Where a spec states something different for one rule, in its own *Standing Constraints*, the spec governs for that plan, and says which rule it replaces. Where a rule contradicts the `CLAUDE.md` of the repository, stop and report it through the inbox message; do not choose.
- Read the whole file before you start the outline. Say in your first progress note that you read it, with the revision above, and repeat the revision and the rule ids in your landing message (`Standing rules: r1, R01 … R18`), so the analysis can check it.
- A change of a rule is made here, with a new revision and a line in the log below; no spec is edited for it. A plan already running keeps the revision it read; the analysis names any rule that changed since.

## R01 — Work the package as the index says

*Scope: every plan that works a package of `doc/plans/`.*

**Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.

## R02 — Java 25 is the baseline

*Scope: every plan.*

**Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.

## R03 — HTTP calls go through `cui-http`

*Scope: every plan.*

**HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.

## R04 — HTTP tests use `cui-test-mockwebserver-junit5`

*Scope: every plan.*

**HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.

## R05 — Where the documents live

*Scope: every plan.*

**Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the reduction of the implemented specification sections and watch items (**condense and point**, operator decision 2026-10-29: the implemented prose of a section is replaced by a paragraph that opens "Implemented by `…`", links the developer guide and says "Remaining: …"; every section anchor stays, because other specification parts and watch items link to it; a watch item is deleted only when a named test covers its guard, and is otherwise kept with a reason; a section with nothing remaining and no inbound link is deleted), a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.

## R06 — Module documentation

*Scope: every plan; the three values are the plan parameters.*

*Parametrised: replace `{DEV}`, `{ADR}` and `{WS}` by the plan parameters of the spec.* **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in {DEV} (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in {ADR} as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream {WS}.

## R07 — Do not leave a design choice open that changes the file set

*Scope: every plan.*

**Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.

## R08 — Files shared by every plan

*Scope: every plan.*

**Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.

## R09 — Settled design: a refused project path is a value

*Scope: every plan.*

**Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.

## R10 — Specifications can be test input

*Scope: every plan.*

**Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.

## R11 — Renumber the ADRs against `origin/main`

*Scope: every plan that writes an ADR.*

**Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.

## R12 — Log messages: the holder of your module, next free identifier

*Scope: every plan that may add a log message; the holder is a plan parameter.*

*Parametrised: replace `{LOG}` by the plan parameter of the spec.* **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: {LOG}. A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

## R13 — Format versions stay 1

*Scope: every plan.*

**Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## R14 — Checklist for durable multi-step protocols in `pm-runtime` and `core.store`

*Scope: plans that change `pm-runtime` or `core.store`.*

**Checklist for durable multi-step protocols in `pm-runtime` and `core.store` (found by PLAN-74, 2026-10-18; four review rounds each found another unguarded transition of the credential-store move).** (1) A resume decides from the pair (record phase, state actually recorded), never from the phase alone, and a guard added to one phase branch gets its symmetric guard on every other branch; enumerate (phase x recorded state x writer) with a test per cell before the first review. (2) While a durable in-progress record exists, every other writer of the state it switches is refused under the same lock, and an authorization is verified against durable state, never asserted by the caller. (3) The record that drives recovery is deleted last, after every side effect (audit event, clean-up after a failure) is durable. (4) A decision with side effects (which store, which key) is decided once and persisted, never re-derived per call from a probe that can flap. (5) A copy-then-switch move has a test with a pre-populated target (an overlapping key and a target-only key) and a failure injected after the copy; the pre-existing-target hazard is checked for every kind of object the protocol copies (entries, integrity key, sidecars) in the same task. (6) A store's temp-file namespace is disjoint from its legal entry-name namespace. (7) A fix of a state-machine transition lists the specification section, the ADR, the Javadoc and the test that state it among its affected files and updates them in the same commit; ADR and test are checked against the specification text, not against the code they document. (8) The invariants of a multi-write protocol are stated per crash window, not per phase. (9) A test that asserts a file location derives the expected path from the production layout independently, never from the object under test. (10) A native binding is loaded once; only what can change at runtime (a lock state) is probed per operation.

## R15 — Checklist for `pm-workflow` code

*Scope: plans that change `pm-workflow`.*

**Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.

## R16 — Facts about `pm-runtime`

*Scope: plans that change `pm-runtime`.*

**Facts about `pm-runtime` (found by PLAN-20, 2026-10-18).** (1) FFM downcall descriptors (`MacSecurity`, `Posix`, the `kill(2)` descriptor of `KillBinding` and `ProcessGroupSignals`) are registered in `pm-runtime/src/main/resources/META-INF/native-image/de.planmarshall/pm-runtime-credentials/reachability-metadata.json`, not in the `plan-marshall-mcp` assembly; verify a location claim with a path query, not with a Javadoc sentence. (2) `ProjectRecovery` is public and wired by the `pm-mcp-server` assembly in `plan-marshall-mcp`: a change of its factory methods needs a source-compatible overload or a named follow-up in `plan-marshall-mcp` after the SNAPSHOT deploy; the outline says which. (3) Signal numbers differ between Linux and macOS: a table of signal names for a job killed by a signal is chosen per OS, never taken from the Linux table. (4) In `pm-runtime`, suppress a Sonar rule inline at the declaration with a stated reason beside it where the design requires the flagged shape (seen: S2142, S3077, S6539, S1075), rather than reshaping the design or excluding the rule for the file.

## R17 — Sibling checkouts from the plan worktree

*Scope: plans that run the links check.*

**Sibling checkouts from the plan worktree (found by PLAN-72, 2026-10-17).** The plan runs in `.plan/local/worktrees/{plan}/`, three directories deeper than the main checkout, so `../plan-marshall-documentation` (as `CLAUDE.md` and the links of the package file write it) does not resolve there. Run the links check with the absolute paths of the sibling checkout, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .`, and record the run so the functional review finds it.

## R18 — SonarCloud: clear the open issues of your pull request before you report it ready

*Scope: every plan that touches Java.*

**SonarCloud: clear the open issues of your pull request before you report it ready (operator decision 2026-10-22; found by PLAN-19, #137, merged with 176 open issues and a red gate).** Sonar analyses `main` and pull requests only: the organisation refuses every other branch ("Organization is not allowed to access data from non main branches", the answer of the API for `branch=<feature>` and the failure of `sonar-build` on a push to a branch that has no open pull request yet). Open the pull request first, wait for its run, and read the issues of the pull request, not of the branch: `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&pullRequest=<N>&resolved=false&ps=500` (the project is public: no token, no credentials, so a failing `workflow-integration-sonar` verb does not excuse the check). Fix every issue in the code, or dismiss it with a stated reason; the quality gate checks thresholds only, so a green gate with open issues is not clean. A red gate (`https://sonarcloud.io/api/qualitygates/project_status?projectKey=plan-marshall_pm-mcp-core&pullRequest=<N>`) blocks the merge request like a failing check: merging on it is the operator's decision, asked with the issue count and the failing conditions. State the count of open issues and the gate state in the landing (`sonar_open_issues`; `unknown` when the search could not be made, never `0`).

## R19 — The Expected Surface is declared per class; shared bookkeeping is not surface

*Scope: every plan; binds the orchestrator's pairing and every plan started from revision r2 (2026-10-27) on.*

**The Expected Surface is declared per class; shared bookkeeping is not surface (operator decision 2026-10-27, after the analysis of why the epic could not run more plans in parallel).** The orchestrator pairs plans by the disjointness of their declared `## Expected Surface`. Written at directory level (`pm-core/.../core/store/` was declared by 25 of 31 active specs, a package of 100 classes) the declarations made 81 % of all spec pairs "overlap" while the footprints of the landed plans shared 0 to 1 code files. Therefore:

- **A code entry names a file, not a directory,** where the package already exists: the existing classes and test classes the plan modifies or extends (`OBSERVED`, each existing at the head of `main`) and the new classes the spec's text implies (`HYPOTHESIS`). A new class that the text does not imply is not listed: a new file in a shared package does not collide with another plan. A directory entry stays only for a package that does not exist yet.
- **Specification parts and watch documents stay as specific files** (a real conflict point); the package file `doc/plans/<package>.adoc` stays.
- **Shared bookkeeping is not surface** (it is governed by R08, R11 and R12): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory of an existing package, and the developer guide of an existing package (additive sections). A guide or an ADR directory that does not exist yet is listed. A landing reports them as undeclared; the analysis treats that as expected, not as expansion.
- **At the outline the plan confirms or corrects its surface against its trace matrix** and says so in its first progress note; a class it must edit that its spec does not name, it records (and names in the landing). The landing's surface delta is the check: a plan that touches an existing file it did not declare is an observation for the analysis, not a failure.
- **Specs are refined by the orchestrator** (`cleanup` and every emit): the orchestrator re-derives the class-level surface from the spec text and the code, marks it `HYPOTHESIS` where it is a prediction, and never lists a file it cannot verify as existing under `OBSERVED`. Candidates of one emit are compared with each other and with every running plan, and the operator is told of the overlaps between the candidates before being asked.

## R20 — The coding skills of the marketplace apply

*Scope: every plan that touches Java, production or test.*

**Read and follow the coding skills of the plan-marshall marketplace** (operator decision 2026-10-30, after the conformance audit of the eight modules, `skills-audit-2026-10-30.md`; the plan family PLAN-V2-83 to PLAN-V2-106 brings the existing code to this state and runs first): `cui-logging`, `cui-testing` and `cui-http-testing` (bundle `pm-dev-java-cui`), `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core` and `junit-integration` (bundle `pm-dev-java`), found under `/home/oliver/git/plan-marshall/marketplace/bundles/`. Read `SKILL.md` and its standards before the outline and say in the first progress note which you read. Concretely:

- **Test data come from `cui-test-generator`** (`@EnableGeneratorController`, `Generators.*`, `@GeneratorsSource`). A literal stays only where it is the contract (a wire format, an error or reason code, a schema or specification string, a golden file, a fixture path), and the test says so in its display name or a one-line comment. No `Random`, no `UUID.randomUUID()` as test data.
- **A class with a hand-written `equals`/`hashCode`** (not a record or an enum) has a contract test through `ShouldHandleObjectContracts` (`cui-test-value-objects`).
- **One log message holder per module, never one for the whole repository** (operator, 2026-10-30; as R12 and CLAUDE.md say): `CoreLogMessages`, `WorkflowLogMessages`, `RuntimeLogMessages` and one `<X>LogMessages` per provider module that logs, each in the module's own `log` package with its own prefix and its own id ranges; a module logs through its own holder and that of `pm-core` only. No plan of the conformance family merges, renames or moves a holder.
- **Every `LogRecord` a class logs is asserted by its constant** with `LogAsserts` (`@EnableTestLogger`); no hand-made log handler; no AAA phase comments.
- **Every assertion carries a message** of 20 to 60 characters that says what is checked (`junit-core`).
- **Test doubles are EasyMock doubles** (the operator allowed the dependency); no Mockito, no PowerMock, no Hamcrest, no new hand-written fake.
- **HTTP tests** use `@EnableMockWebServer` with `@MockResponseConfig` or `@ModuleDispatcher` and an injected `URIBuilder`; no hand-written `Dispatcher` (this replaces ADR `doc/adr/002`, superseded by PLAN-V2-93).
- **Every production `package-info.java` carries `@NullMarked`.** Absence is returned as `Optional<T>`, never as `null` and never as a `@Nullable` return; `Optional` is **not** a parameter, a field or a record component: a nullable state of those is `@Nullable`.
- **A record of seven or more components carries `@Builder`;** a smaller one keeps its canonical constructor.
- **A class that holds only static methods is a Lombok `@UtilityClass`;** no catch or throw of `Exception`, `RuntimeException` or `Throwable` (a last-resort handler that the spec names carries a `@SuppressWarnings` with its reason).
- **The one accepted exception** (the ADR of PLAN-V2-83 is the record, with the operator's answers): tests follow behaviour, not one test class per production class, and the 400-line budget of `junit-core` is reported by the landing, not enforced. Until a plan of the family has landed, the existing code of its module is not a precedent for a new class.

## Revision log

- r1, 2026-10-22: extracted from the Standing Constraints of the 49 staged and parked plan specs (identical text in 48 of them; 13 module-documentation and 17 log-holder values became plan parameters). R18 (SonarCloud) was added the same day after PLAN-19 (#137) merged with 176 open issues and a red gate; the analysis of the Sonar access is in `epic.md` § Decisions (2026-10-22).
- r2, 2026-10-27: R19 added (operator: "yes, and persist the updated rules", answering the analysis of why so few plans run in parallel: the dependency graph is narrow, 25 of 31 active specs declared `core/store/` by directory, every plan edits the index, `Traceability` and shared specification parts). Plans already running (PLAN-42, PLAN-73) keep r1; the analysis names R19 for them as a rule that changed. The specs of all staged and parked plans were refined to class level the same day (see `epic.md` § Queue annotations, "Surface refinement 2026-10-27").
- r3, 2026-10-29: R05 amended (operator: "1", choosing to adopt the practice the lanes already follow: condense and point, anchors kept, a watch item deleted only when a named test covers its guard). Source: the landings of PLAN-25, 26 and 29 (#145, #146, #172) and PLAN-107 (#215), and the analysis of the open defect "R05 is practised as condense and point". Plans already running (PLAN-46, PLAN-105) keep r2; the analysis names R05 for them as a rule that changed. The staged specs still name r2 in their First Instruction and read the file, so they pick r3 up when they start.
- r4, 2026-10-30: R20 added (operator: allowed the dependencies `cui-test-generator`, `cui-test-value-objects`, JSpecify, Lombok in `pm-workflow` and `pm-provider-sonar`, and EasyMock; chose to migrate all tests to generated data, to convert the HTTP tests to the annotations, to mark every package `@NullMarked`, to convert every `Optional` parameter, field and record component, to give records of seven or more components a builder, to give every assertion a message and to replace the hand-written fakes with EasyMock; and: one log message holder per module, not one for the repository). Source: the conformance audit of 2026-10-30. Plans already running keep the revision they read; none is running at this date.
