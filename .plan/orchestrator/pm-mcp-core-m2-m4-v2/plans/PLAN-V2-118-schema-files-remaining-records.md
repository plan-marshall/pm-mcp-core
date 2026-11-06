# PLAN-V2-118: Move the schema blocks of seven record documents to files and add the parity tests they never had

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (operator decision 2026-11-05: plans are run with Claude Code through the plan lifecycle).
> Lives at `plans/PLAN-V2-118-schema-files-remaining-records.md` and is queued as one row file, `queue/PLAN-V2-118.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/schema-files-remaining-records.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/schema-files-remaining-records.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R15, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none beyond the foundation; ADR directory: none; layout: workstream WS-12.
- R12 — log message holder: none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

25 blocks in seven documents of `store-schemas/` become files, plus the check the other store records already have: a parity test per schema whose codec exists (members, required members, enumerations and limits of the record against the file, in the form of `FindingSchemaParityTest`). These are the records for which the specification and the code could drift without any test noticing; the test is new, not a replacement.

## Deliverables

1. **Move the 25 blocks** of `store-schemas/04-epic-and-change-ledger.adoc` (9), `05-metrics-and-quality-report.adoc` (2), `06-task-and-references.adoc` (6), `07-project-configuration.adoc` (2), `08-project-configuration-local.adoc` (3), `17-re-planning.adoc` (2) and `18-decision-records.adoc` (1).
2. **A parity test per schema whose codec exists,** with a matched positive control (the unchanged file) and a negative control (a member present in the file and absent from the record, and the reverse). A schema whose codec does not exist yet is moved and named in the task of the package that builds the codec.
3. **Where a schema file of the same record already lies in a module,** the block is replaced by an include of that file after the two statements are compared; the deviating one is corrected with evidence (the correction rule of the project skill `traced-implementation`), never worked around.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **A move plus new tests:** no schema text changes except a correction with evidence; a drift the new parity tests reveal is reported in the landing message and fixed only where the specification is the side that is wrong.
- **`pm-workflow` rule:** R15 applies where a schema's codec lies in `pm-workflow`.
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- HYPOTHESIS: the codecs of these records lie in `pm-core` (`core/store/`, `core/config/`, `core/queue/`) and `pm-workflow` (re-planning, decision records) — the trace matrix names them; confirm at the outline and list the real files in the first progress note (verify-at-outline).
- Verify-first clause: count the blocks of the seven documents at the head of `main` (the package says 25).

## Expected Surface

- OBSERVED: `doc/specification/store-schemas/04-epic-and-change-ledger.adoc`
- OBSERVED: `doc/specification/store-schemas/05-metrics-and-quality-report.adoc`
- OBSERVED: `doc/specification/store-schemas/06-task-and-references.adoc`
- OBSERVED: `doc/specification/store-schemas/07-project-configuration.adoc`
- OBSERVED: `doc/specification/store-schemas/08-project-configuration-local.adoc`
- OBSERVED: `doc/specification/store-schemas/17-re-planning.adoc`
- OBSERVED: `doc/specification/store-schemas/18-decision-records.adoc`
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/config/` (new directory: the schema files of the project configuration)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/config/ProjectConfigurationSchemaParityTest.java` (new)
- OBSERVED: `doc/plans/schema-files-remaining-records.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`, on `main`).
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- Every `[source,json]` block of the seven documents is an include of a schema file.
- A parity test exists for each schema whose codec exists, with a matched positive control and a negative control in both directions; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in every module touched.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/worktrees/_orchestrator/.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-118-schema-files-remaining-records.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
