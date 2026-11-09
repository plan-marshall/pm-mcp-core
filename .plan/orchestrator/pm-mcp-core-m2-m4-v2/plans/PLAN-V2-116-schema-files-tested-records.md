# PLAN-V2-116: Move the schema blocks of seven store-schema documents to files; eight parity tests read the file

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (operator decision 2026-11-05: plans are run with Claude Code through the plan lifecycle).
> Lives at `plans/PLAN-V2-116-schema-files-tested-records.md` and is queued as one row file, `queue/PLAN-V2-116.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/schema-files-tested-records.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/schema-files-tested-records.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none beyond the foundation (a one-line pointer where a guide names a schema block); ADR directory: none; layout: workstream WS-12.
- R12 — log message holder: none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

A move only, no schema change and no behaviour change: 27 blocks in seven documents of `store-schemas/` become `<record>.schema.json` files in the modules that own their codecs, each block in the document is replaced by the include, and eight parity tests read the file through `SchemaResource` instead of extracting the block from the `.adoc`. One more parity test is added for the plan history, whose round-trip test cannot catch a codec change made together with its own reader.

## Deliverables

1. **Move the 27 blocks** of `store-schemas/01-format-and-session-log.adoc` (1), `02-workflow-state-store.adoc` (2), `03-findings-and-review-records.adoc` (6), `09-derived-facts-and-build-timings.adoc` (4), `10-job-and-lesson-records.adoc` (2), `14-plan-instance-waivers-questions.adoc` (7) and `16-task-queue.adoc` (5): each block to its file, each block in the document replaced by the include (layout and convention: the ADR of `schema-files-foundation`).
2. **The tests read the file:** `PendingEntrySchemaParityTest` (stays disabled with its reason until `start-source-intake`), `WorkflowStatusSchemaParityTest`, `FindingSchemaParityTest`, `AnalysisResultSchemaTest`, `DerivedFactsSchemaParityTest`, `JobRecordSchemaParityTest`, `LessonFrontMatterSchemaParityTest` and `TaskQueueSchemaParityTest` replace `SpecificationDocument.jsonBlockAfter(document, heading)` by `SchemaResource`; their assertions stay as they are.
3. **A parity test of the plan history line** (`02-workflow-state-store.adoc`) against its schema file: `PlanHistoryTest` is a round trip only.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **A move only:** no schema text changes, no member added or removed, no behaviour change; each file is the former block's text byte for byte (`$id`, `$defs`, member order). A deviation the move reveals between a block and its codec is recorded and reported, never fixed inside the move.
- **Order of the move:** before a block leaves its document, the parity test that reads it is switched in the same commit, so no commit leaves a test reading a deleted block.
- **Open the first migrated document on GitHub** and write into the pull request that the include renders as a link to the file (the paragraph above it names the file).
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- OBSERVED: the eight tests of Deliverable 2 name `SpecificationDocument` today (grep, `main` of 2026-11-06); `PendingEntrySchemaParityTest` is `@Disabled` with a reason.
  - verdict: corroborated | checked_at: 81b023dd | by: pm-mcp-core-m2-m4-v2/analyze | rescoped: n/a | evidence: the eight tests named SpecificationDocument at 81b023dd^1 (10 jsonBlockAfter calls); PendingEntrySchemaParityTest was Disabled with a reason
- HYPOTHESIS: the resource package of each file is the package of its codec (`core/store/`, `core/findings/`, `core/analysis/`, `core/queue/` below `pm-core/src/main/resources/de/planmarshall/core/`) — confirm at the outline against the trace matrix (verify-at-outline).
  - verdict: contradicted | checked_at: 81b023dd | by: pm-mcp-core-m2-m4-v2/analyze | rescoped: no | evidence: 5 of 27 files lie elsewhere: 3 in pm-core core/role/qualification and 2 in pm-workflow workflow/plan (ADR 018, operator decision 2026-11-07); the other 22 sit in the codec package
- Verify-first clause: count the `[source,json]` blocks of the seven documents at the head of `main` (the package says 27); a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 81b023dd | by: pm-mcp-core-m2-m4-v2/analyze | rescoped: n/a | evidence: 27 blocks counted at 81b023dd^1: 01:1, 02:2, 03:6, 09:4, 10:2, 14:7, 16:5

## Expected Surface

- OBSERVED: `doc/specification/store-schemas/01-format-and-session-log.adoc`
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc`
- OBSERVED: `doc/specification/store-schemas/03-findings-and-review-records.adoc`
- OBSERVED: `doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc`
- OBSERVED: `doc/specification/store-schemas/10-job-and-lesson-records.adoc`
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc`
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntrySchemaParityTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/DerivedFactsSchemaParityTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/JobRecordSchemaParityTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/LessonFrontMatterSchemaParityTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/findings/FindingSchemaParityTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/analysis/AnalysisResultSchemaTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/queue/TaskQueueSchemaParityTest.java`
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/store/` (new directory: the schema files)
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/findings/` (new directory)
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/analysis/` (new directory)
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/queue/` (new directory)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/PlanHistorySchemaParityTest.java` (new)
- OBSERVED: `doc/plans/schema-files-tested-records.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`, on `main`).
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- Every `[source,json]` block of the seven documents is an include of a schema file, each file the former block's text; the parity tests that passed against the blocks before the move pass against the files after it, unchanged.
- The eight tests pass against the files (`PendingEntrySchemaParityTest` stays disabled with its reason and is not part of this criterion); none of them names `SpecificationDocument` or `pm.specification.root`; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in every module touched.
- The first migrated document is opened on GitHub and the include is seen to render as a link to the file; the result is written into the pull request.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-116-schema-files-tested-records.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
