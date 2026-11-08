# PLAN-V2-121: The log message tables are generated from the holders and the protocol lists are dropped for links to the Javadoc

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (operator decision 2026-11-05: plans are run with Claude Code through the plan lifecycle).
> Lives at `plans/PLAN-V2-121-generated-listings.md` and is queued as one row file, `queue/PLAN-V2-121.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/generated-listings.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015). **Decided 2026-11-08** (operator decision, relayed in the epic inbox as `schema-transition-002.md`; the package file was rewritten in pull request #326, merged as `9c867f05`): the log message tables are generated from the holders, the protocol lists are dropped, `ProtocolVocabularyParityTest` is deleted; this spec was adapted from the placeholder to the five tasks of the rewritten package.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/generated-listings.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R12, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the guides `pm-core/doc/developer/core-log.adoc`, `pm-runtime/doc/developer/runtime-log.adoc`, `pm-workflow/doc/developer/workflow-log.adoc` and `pm-workflow/doc/developer/workflow-task.adoc` state the new form; ADR directory: `doc/adr/` for the form of the generated files (one ADR; R11 for the number), layout: workstream WS-12.
- R12 — log message holder: the holder of the module that logs (`CoreLogMessages`, `RuntimeLogMessages`, `WorkflowLogMessages`); this plan adds no record.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

The code is the source of two lists that tests compare today with a hand-written document: the log message tables of `doc/LogMessages.adoc` (`RuntimeLogMessagesTest`) and the protocol lists of `pm-workflow/doc/developer/workflow-task.adoc` (`ProtocolVocabularyParityTest`). After this plan the direction turns: the identifier and template of every record are generated from the holder into a committed file below `doc/` that the document includes, one test per module compares it byte for byte with a fresh generation, and the protocol lists are replaced by links to the Javadoc. No test opens `doc/LogMessages.adoc` or `workflow-task.adoc` any more; the only files below a `doc/` tree a test still reads are the generated ones.

## Deliverables

1. **The log message tables are generated:** for every holder table of `doc/LogMessages.adoc` (pm-core, pm-runtime, pm-workflow) a committed file below `doc/` lists the identifier and the template of every record of the holder, generated from the holder; the document includes the file in place of the table rows. The Component and Description columns go: what a record is for stays the Javadoc of the record, which the document links per holder. Before a column goes, every sentence of a Description that the Javadoc of its record does not say (the reason a guard closes tasks with, the component that logs the record) moves into that Javadoc. The location and the name of the generated files are chosen at the outline and recorded in one ADR.
2. **The check of the generated files:** a test per module compares the committed file with a fresh generation byte for byte and parses nothing; its matched positive control is the current file, its negative control a file with one identifier or template changed.
3. **The protocol lists are dropped:** the enumerations of the `TaskRefusal` values, the `WaitAgain` reasons and the `End` reasons in `pm-workflow/doc/developer/workflow-task.adoc` are replaced by a link to the Javadoc of the enum or type that holds them; the prose around them stays. Before a list goes, the reasons of `WaitAnswer.WaitAgain` and `WaitAnswer.End`, which are string literals at their call sites today, get one definition in the code (named constants or an enum), and the Javadoc of that definition and of `TaskRefusal` names every value; a reason no definition holds cannot be used.
4. **Remove the checks that parse a document:** `everyRecordDocumented` and `undocumentedRecordsAreFound` of `RuntimeLogMessagesTest` are replaced by Deliverable 2, and `ProtocolVocabularyParityTest` is deleted.
5. **The other holders:** a holder of `doc/LogMessages.adoc` whose rows no test checks follows the same form (ADR 005, one log message holder per module).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Operator word before the emit:** the schema-transition orchestrating session asks that nothing of this plan is emitted without the operator's word (`schema-transition-002.md`, the standing instruction of 2026-11-07).
- **No stored or wire format changes:** the text of a log record (identifier and template) does not change; only its place in the document does.
- **The generated files are the only files below a `doc/` tree that a test reads;** `PLAN-V2-122` narrows the `**/doc/**` entry of the docs-only include list to them, so the files must be easy to name by one glob.
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- OBSERVED: `RuntimeLogMessagesTest` is the only test that reads `doc/LogMessages.adoc` (`git grep` of the test trees at `5f0e74be`; the other hit is `.github/project.yml`, the docs-only include list).
- OBSERVED: `ProtocolVocabularyParityTest` reads `pm-workflow/doc/developer/workflow-task.adoc` (`GUIDE`, `:41`) and matches Java source with a regular expression (`:94`).
- HYPOTHESIS: no test other than `RuntimeLogMessagesTest` checks the rows of `doc/LogMessages.adoc` for the `pm-core` and `pm-workflow` tables (`CoreLogMessagesTest` and `WorkflowLogMessagesTest` exist) — confirm at the outline.
- HYPOTHESIS: the `WaitAgain` and `End` reasons are string literals at nine call sites, all in `TaskProtocol.java` (`git grep` of `WaitAnswer.WaitAgain(` and `WaitAnswer.End(` in the main trees at `5f0e74be`); the package file also names call sites in `pm-runtime` — confirm at the outline.
- Verify-first clause: before editing, list the open pull requests for one that touches a holder, a holder test or `doc/LogMessages.adoc`, and re-run the two greps above at the head of `main`.

## Expected Surface

- OBSERVED: `doc/LogMessages.adoc`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/log/CoreLogMessages.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/log/RuntimeLogMessages.java`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/log/WorkflowLogMessages.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/log/CoreLogMessagesTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/log/RuntimeLogMessagesTest.java`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/log/WorkflowLogMessagesTest.java`
- OBSERVED: `pm-workflow/doc/developer/workflow-task.adoc`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/WaitAnswer.java`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskRefusal.java`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/ProtocolVocabularyParityTest.java` (deleted)
- OBSERVED: `doc/plans/generated-listings.adoc` (the package file; deleted by the plan, R01)
- HYPOTHESIS: the generated files, one per holder, in the documentation tree of the repository root (location and name decided at the outline, so no path is declared here) and the generator each module's byte-check test runs (new paths)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping (the log guides state the new form), and the new ADR of the form of the generated files (R11, next free number).

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`, on `main`). The open question `generated-listings` is decided (2026-11-08). For the log tables the holder surfaces of PLAN-V2-116 to 118 are to be checked for an overlap by the cross-check before the emit.
- PLAN-V2-122 (`adoc-reading-retired`) depends on this plan: it narrows `**/doc/**` to the generated files this plan creates.
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings`; PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test, a holder or a document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- `doc/LogMessages.adoc` states no hand-written identifier or template, and `workflow-task.adoc` states no list the code owns; no test opens either document. Tests read only the generated files below `doc/` and compare them byte for byte with a fresh generation.
- The byte check with its two controls; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in every module whose holder table changed and in `pm-workflow`.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-121-generated-listings.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
