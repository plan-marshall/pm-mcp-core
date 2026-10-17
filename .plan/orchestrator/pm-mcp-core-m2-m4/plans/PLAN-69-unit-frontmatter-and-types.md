# PLAN-69: Unit frontmatter, unit types, and the interface of a unit

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-69-unit-frontmatter-and-types.md` and is queued as one row file, `queue/PLAN-69.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `unit-frontmatter-and-types` of `doc/plans/unit-frontmatter-and-types.adoc`: unit frontmatter, unit types, and the interface of a unit. Serves: Roadmap Milestone 3 (the Workflow DSL parser, immutable AST, and static verifier); the units, the building-block catalogue, and the process views of the clients of `pm-mcp-clients`, which show a unit's name, description, type, and interface. Where: pm-workflow packages `workflow.dsl` (the frontmatter parser, `entry <name> -> <state>`, `call <unit> entry <name>`, the printer), `workflow.ast` (the frontmatter, the entry bindings, the entry of a call and of a call frame), `workflow.verify` (the interface rules within Rules 3 and 6, the type check, `INV-GEN-1`), `workflow.unit` (the author check per level), and `workflow.engine` (entering a callee at its entry); pm-core package `core.store` (the member `entry` of a call frame). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/unit-frontmatter-and-types.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Workflow DSL § Unit Frontmatter, § Unit Types, and § Interface Rules, with the exit contract of § Result Guards and the Sub-Workflow Exit Contract (`PM-IMPL-5`, its statement on the frontmatter). None of it exists: no frontmatter is read, a unit has its `start` and no entries, a `call` names no entry, a call frame records none (`WorkflowStatus.CallFrame`, `ast.CallFrame`; the schema's `entry` is marked not implemented), and the exits of a sub-workflow are derived from its terminals.
2. Its own parser (PM-WATCH-DSL-36): the frontmatter is the closed, line-oriented subset of YAML between the two `---` lines that open every `.pmwf` file, read by the parser of `workflow.dsl` and never by a general YAML loader: one `key: value` per line at column 1, the closed keys only (`name`, `description`, `type`, `modification_date`, `version`, `author` exactly once, `signature` at most once), plain or double-quoted values with the escapes `\"` and `\\` only; a tag, an anchor, an alias, a merge key, a flow collection, a block scalar, a comment, a blank or indented line, a control character, and an unknown key are refused with file, line, and column; `entry` and `exit` are repeated per name and kept in order, never collapsed; an `entry_description` stands directly below its `entry` line and an `exit_description` below its `exit` line, at most one each, and a description anywhere else is refused.
3. The fields: `name` 3 to 100 characters, `description` 1 to 1000, each entry and exit description 1 to 500; `type` from the closed set; an entry an identifier and an exit an outcome id, each unique, an exit never `stepped_out`; `modification_date` a valid calendar date not after the day of validation; `version` in the release version grammar; `author: plan-marshall` refused for a unit outside the binary (a unit of the machine or project level); `signature` carried and not verified. The frontmatter is readable without the body, for the lists that show a unit, and the printer writes it in the order of the field table, so the round trip of a unit holds with its frontmatter.
4. The six types (`workflow`, `recipe`, `subworkflow`, `system`, `task`, `instance`) checked against the unit's `scope` and the presence of its entries and exits as their table states (interface rule 6); a type whose scope the AST does not have yet (`system` with `scope machine`) is checked once `operator-wait-and-machine-scope` adds the scope.
5. Entries and the call through an entry: the header line `entry <name> -> <state_id>` binds each declared entry exactly once to a state of the unit, and a unit with entries declares no `start` (rule 1); `call <unit> entry <name> (…)` enters the callee through an entry it declares, the entry omitted only for a callee with exactly one (rule 5); the engine pushes a frame that records the entry and enters the bound state, and the status store writes and reads the frame's `entry`.
6. Exits and stop points: a unit without exits declares at least one terminal, the resident workspace and machine units excepted; a unit with exits declares every terminal's outcome as an exit (or `stepped_out` on a terminal of that class) and every exit is reached; a landing variant declares one entry per step it holds, no exit, and no terminal; a called unit declares entries and exits, and only a unit with entries is the target of a `call` (rules 2 and 3); reachability from the starting point or from every entry, and every exit reached from an entry (rule 4, with Rule 3); the call routes every declared exit and `stepped_out` (Rule 6). Each violation is a value naming the rule, the file, the line, and the column.
7. The interface a project-owned unit promises: `INV-GEN-1` compares the type, the entries, the exits, and the entry bindings of a machine's or a project's unit with those of the default unit it replaces, beside its scope, start state, and terminals.
8. Fixtures and existing units: every test unit of `pm-workflow` gains its frontmatter; the units shipped by `plan-marshall-mcp` get theirs where they are written (`plan-marshall-mcp` / `default-units-and-drift-tests`). The phase clause of a state and the phases as sub-workflows are `phase-sub-workflows`'s, the units of type `recipe` `recipe-workflows`'s, the designer's validation of a placed unit `process-designer`'s; each builds on this package.

**Done when** (the package's own acceptance): A unit with a valid frontmatter parses, prints, and parses again to the same tree; each refused form of the frontmatter (a tag, an anchor, an alias, a merge key, a flow collection, an indented or blank line, an unknown or repeated single key, a misplaced description) has its refusing fixture with file, line, and column, and a unit with three entries keeps three in order; each interface rule and each type row has a refusing fixture and a passing control; a call through a named entry pushes a frame with that entry, which the status store round-trips, and the engine enters the bound state; a project's unit whose interface or type differs from the default unit's fails `INV-GEN-1`, and one outside the binary with the author `plan-marshall` is refused; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-5; PM-WF-16
- Specification: the sections the first bullet of _What_ names; Workflow DSL § Static AST Verification Rules (Rules 3 and 6); § Designable Workflow Units, Levels & Compiled Invariants (`INV-GEN-1`); Store Schemas § Workflow State Store (`call_stack`, `entry`); Identifier Registry § Release version
- Watch: PM-WATCH-DSL-36

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Sibling checkouts from the plan worktree (found by PLAN-72, 2026-10-17).** The plan runs in `.plan/local/worktrees/{plan}/`, three directories deeper than the main checkout, so `../plan-marshall-documentation` (as `CLAUDE.md` and the links of the package file write it) does not resolve there. Run the links check with the absolute paths of the sibling checkout, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .`, and record the run so the functional review finds it.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/unit-frontmatter-and-types.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/unit-frontmatter-and-types.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/unit-frontmatter-and-types.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- Verify-first clause: before scoping, list the open pull requests for one that names `unit-frontmatter-and-types`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java` — Deliverable 5 (shared hot file); class-level surface drafted by cleanup on 2026-10-17, replaces the directory entries
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java` — Deliverable 5 (shared hot file)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java` — Deliverable 5
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryCodecTest.java` — Deliverable 5
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/WorkflowDefinition.java` — Deliverables 1, 3, 5: a record, so a new component breaks every constructor and every three-component record pattern (shared hot file)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/CallFrame.java` — Deliverable 5 (shared hot file)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/StateBehavior.java` — Deliverable 5 (shared hot file)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/package-info.java` — Deliverable 1
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/UnitFrontmatter.java` — Deliverables 1 and 3, new
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/UnitType.java` — Deliverable 4, the six unit types, new
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/ast/AstNodeModelTest.java` — Deliverables 1 and 5
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-ast.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-ast/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowParser.java` — Deliverables 2, 3, 5 (shared hot file)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowPrinter.java` — Deliverables 3 and 5 (shared hot file)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowLexer.java` — Deliverable 2
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/RefusalRule.java` — Deliverable 2
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/package-info.java` — Deliverable 2
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java` — Deliverables 2 and 3, new
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowParserTest.java` — Deliverables 2, 3, 5
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowParserRefusalTest.java` — Deliverable 2
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowPrinterTest.java` — Deliverable 3
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/AstWithoutLocations.java` — Deliverables 1 and 5
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowLexerTest.java` — Deliverable 2
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/UnitFrontmatterReaderTest.java` — Deliverables 2 and 3, new
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-dsl.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-dsl/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java` — Deliverable 5 (shared hot file)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/CallStack.java` — Deliverable 5 (shared hot file)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/package-info.java` — Deliverable 5
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/EngineFixtures.java` — Deliverables 5 and 8 (shared hot file)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/SubWorkflowExecutionTest.java` — Deliverables 5 and 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/WorkflowEngineTest.java` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/EngineResumeTest.java` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/EntityAddressingTest.java` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/GuardEvaluatorTest.java` — Deliverable 8
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProjectUnitLoader.java` — Deliverable 3 (shared hot file; also called by `CompiledInvariants.check`)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/UnitFinding.java` — Deliverable 3
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/PlanUnitMerger.java` — Deliverable 8 (shared hot file)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/UnitFixtures.java` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/ProjectUnitLoaderTest.java` — Deliverables 3 and 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/PlanUnitMergerTest.java` — Deliverable 8
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` — Deliverables 4, 5, 6 (shared hot file)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java` — Deliverables 4 and 6 (shared hot file)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/GeneralInvariants.java` — Deliverable 7, INV-GEN-1 (shared hot file)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/SubWorkflowInvariants.java` — Deliverable 5
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/CompiledInvariants.java` — Deliverable 6, only if `startState` becomes optional (a unit with entries declares no `start`)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/ExecuteInvariants.java` — Deliverable 6, same condition
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/RefineInvariants.java` — Deliverable 6, same condition
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/TransitionFuzzer.java` — Deliverable 6, same condition
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/package-info.java` — Deliverables 4 and 6
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/WorkflowValidatorTest.java` — Deliverables 4, 5, 6
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/AdditionalRulesTest.java` — Deliverables 5, 6, 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java` — Deliverables 7 and 8 (shared hot file)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java` — Deliverables 7 and 8 (shared hot file)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/TestPrimitives.java` — Deliverable 8 (shared hot file)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/VerificationResultTest.java` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/RegistriesAndRecordsTest.java` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/DeclarationBoundsTest.java` — Deliverable 8 (shared hot file)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/TransitionFuzzerTest.java` — Deliverable 6, same condition
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java` — Deliverables 3 and 8: not declared before, the plan must change it (REAL collision with PLAN-70); added by cleanup on 2026-10-17
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/InstancePruner.java` — Deliverable 5 (REAL collision with PLAN-70)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceComposerTest.java` — Deliverables 3 and 8 (REAL collision with PLAN-70)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/StateTransitions.java` — Deliverable 5: not declared before; added by cleanup on 2026-10-17
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/MermaidRenderer.java` — Deliverable 6, only if `startState` becomes optional
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/AsciiDocTableRenderer.java` — Deliverable 6, same condition
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/InstanceMermaidRenderer.java` — Deliverable 6, same condition
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/DiagramFixtures.java` — Deliverable 8 (REAL collision with PLAN-70)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/WorkflowDiagramGeneratorTest.java` — Deliverable 8 (REAL collision with PLAN-70)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/StateKindTest.java` — Deliverable 5
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioFixtures.java` — Deliverable 8: not declared before
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/test/resources/dsl/valid/` — Deliverable 8, edit-all: 5 of 5 `.pmwf` gain their frontmatter (the 124 fixtures: 5 + 14 + 11 + 88 + 2 + 1 + 2 + 1); added by cleanup on 2026-10-16, made file-exact on 2026-10-17
- OBSERVED: `pm-workflow/src/test/resources/dsl/malformed/` — Deliverable 8, edit-all: 14 of 14; the new refusing frontmatter fixtures (Deliverable 2) are created here too
- OBSERVED: `pm-workflow/src/test/resources/unit-fixtures/defaults/` — Deliverable 8, edit-all: 11 of 11; the callee units also move from `start` to entry bindings (Deliverable 5)
- OBSERVED: `pm-workflow/src/test/resources/verify/refused/` — Deliverable 8, edit-all: 88 of 88; the new refusing fixtures of the interface rules and type rows (Deliverables 4 and 6) are created here too
- OBSERVED: `pm-workflow/src/test/resources/verify/fuzz/` — Deliverable 8, edit-all: 2 of 2
- OBSERVED: `pm-workflow/src/test/resources/verify/passing/` — Deliverable 8, edit-all: 1 of 1
- OBSERVED: `pm-workflow/src/test/resources/diagram/diagram-fixture.pmwf` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/resources/diagram/instance-fixture.pmwf` — Deliverable 8
- OBSERVED: `pm-workflow/src/test/resources/plan/plan-template-fixture.pmwf` — Deliverable 8 (REAL collision with PLAN-70, which rewrites its merge comment and probably the axis variants)
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/unit-frontmatter-and-types.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/identifiers/02-registry-stores-to-web.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Run alone within `pm-workflow` (operator-approved emit of 2026-10-17, beside PLAN-74 in `pm-runtime`/`pm-core` and PLAN-20 in `pm-runtime`): the plans of the plan-instance cluster (PLAN-65, 70, 71) and every other plan that edits `pm-workflow` wait until this one lands. Overlap accepted with PLAN-20 `job-runner`: `pm-core/doc/adr/core-store/` and `pm-core/doc/developer/core-store.adoc`; take the next free ADR number of the directory after a rebase, and extend the guide in a section of its own. #83 (spend-removal) is already on `main`: it changed `HeaderSchemaCheck`, `InvariantId`, `RemediationInvariants`, `FinalizeInvariants` and `WorkflowStatus`, so read them at the branch point, not from this spec.
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-69-unit-frontmatter-and-types.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
