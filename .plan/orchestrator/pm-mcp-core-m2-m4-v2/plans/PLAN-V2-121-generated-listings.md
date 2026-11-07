# PLAN-V2-121: The log message table and the protocol lists taken from the code instead of compared with it by a test (BLOCKED)

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
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/generated-listings.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R12, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: decided with the form; ADR directory: decided with the form; layout: workstream WS-12.
- R12 — log message holder: the holder of the module that logs.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

PARKED: the form of this package waits for the decision of the open question `generated-listings`. The package takes the log message table (`doc/LogMessages.adoc`) and the protocol lists from the code instead of comparing them with it by a test. The Deliverables below are placeholders from the package file and are rewritten when the question is decided.

## Deliverables

1. **Rewritten after the decision:** the package file `doc/plans/generated-listings.adoc` states the tasks; this spec carries none until the question is decided. Do not outline it.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Not emittable:** the row is `parked`; the status changes only after the operator's decision.
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- HYPOTHESIS: the two tests that read a list out of a document are `RuntimeLogMessagesTest` (reads `../doc/LogMessages.adoc`) and `ProtocolVocabularyParityTest` (pm-workflow) — the package file names the rest (verify-at-outline after the decision).

## Expected Surface

- OBSERVED: `doc/LogMessages.adoc`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/ProtocolVocabularyParityTest.java`
- OBSERVED: `doc/plans/generated-listings.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`) **and the open question `generated-listings`** of the documentation repository (`doc/discussions/open-questions.adoc`, branch `docs/schema-files-open-questions`). **Do not emit this plan until the operator has decided the question and this spec is rewritten.**
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- Rewritten with the decision.

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
