# PLAN-V2-119: Move the schema blocks of eight further specification documents to files

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off (operator decision 2026-11-07: the next plans are emitted for the runbook lane again).
> Lives at `plans/PLAN-V2-119-schema-files-other-specifications.md` and is queued as one row file, `queue/PLAN-V2-119.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/schema-files-other-specifications.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (the file is at `r7`; this spec was written at `r6`, and the plan reads the file, so `r7` binds: R05 and R12 were amended at r7, R19 and R20 are unchanged for this plan), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/schema-files-other-specifications.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R15, R16, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none beyond the foundation; ADR directory: none; layout: workstream WS-12.
- R12 — log message holder: none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

14 schema blocks in eight documents (domain extensions, harness profiles, model work) become files, as in `schema-files-tested-records`; the example blocks (`domain-extensions/06-configuration.adoc` and the second block of `harness-profiles/01-profile-schema.adoc`) stay inline. The corpus schemas that already lie in `core/role/corpus/` (`source-overlay`, `source-manifest`, `imported-item`) replace the matching blocks after the two statements are compared.

## Deliverables

1. **Move the 14 blocks:** `domain-extensions/01-four-axis-architecture.adoc` (1) and `05-recipes-and-extension-points.adoc` (4); `harness-profiles/01-profile-schema.adoc` (the schema block) and `03-endpoint-harness.adoc` (1); `model-work/01-roles-and-configuration.adoc` (2), `06-envelopes-questions-deadlines.adoc` (2), `07-untrusted-text-screening.adoc` (2) and `09-completion-usage-and-spend.adoc` (1).
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
2. **Where a file of the same record already exists,** the block is replaced by an include of that file after the two statements are compared; the deviating one is corrected with evidence, never worked around.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
3. **A parity test per schema whose codec exists,** as in `schema-files-remaining-records`; a block whose codec does not exist yet is moved and named in the package that builds the codec.

   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-core, pm-dev-java:java-null-safety
   - Persona: module-tester
## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **A move plus new tests,** as in the other move packages: byte for byte, a deviation is reported, a schema is not changed inside the move.
- **Example blocks stay inline** and are named as examples in the paragraph above them, so `trace.py` (foundation) does not report them.
- **Sonar:** where the lane's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** the lane files it (`landing-check`); it carries the standing-rule revision and rule ids and the open items, and names `spec_source` and `spec_differ`.

## Claim Labels

- OBSERVED: the three corpus schema files exist in `pm-core/src/main/resources/de/planmarshall/core/role/corpus/` (`source-overlay`, `source-manifest`, `imported-item`).
- HYPOTHESIS: the codecs of the other blocks lie in `pm-core` (`core/role/`, `core/profile/`), `pm-workflow` and `pm-runtime` (`endpoint/`) — confirm at the outline (verify-at-outline).
- Verify-first clause: count the blocks of the eight documents at the head of `main` (the package says 14).

## Expected Surface

- OBSERVED: `doc/specification/domain-extensions/01-four-axis-architecture.adoc`
- OBSERVED: `doc/specification/domain-extensions/05-recipes-and-extension-points.adoc`
- OBSERVED: `doc/specification/harness-profiles/01-profile-schema.adoc`
- OBSERVED: `doc/specification/harness-profiles/03-endpoint-harness.adoc`
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc`
- OBSERVED: `doc/specification/model-work/06-envelopes-questions-deadlines.adoc`
- OBSERVED: `doc/specification/model-work/07-untrusted-text-screening.adoc`
- OBSERVED: `doc/specification/model-work/09-completion-usage-and-spend.adoc`
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/source-overlay.schema.json`
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/source-manifest.schema.json`
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/imported-item.schema.json`
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/profile/` (new directory: the harness profile schema files)
- OBSERVED: `doc/implementation-watch/model-work.adoc`
- OBSERVED: `doc/plans/schema-files-other-specifications.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`, on `main`).
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- State at the emit (2026-11-12, `origin/main` `1283710f`+): PLAN-V2-115 is landed and PLAN-V2-116, 117, 118 and 120 are shipped; `trace.py` reports `INLINE SCHEMA BLOCKS PENDING 14` (the count of this spec holds; the two blocks of `model-work/06` are at `:267` and `:289`, in § The Decision Structure, far from the sections PLAN-V2-40 condenses, §§ Cost Sizing and Packing). **The three corpus schemas gained `maxLength` bounds in PLAN-V2-65 (#389, `a4c2fa86`):** a specification block of a corpus format that was written before it deviates from the file by exactly those bounds; the file is the authority, so the include replaces the block and the deviation is stated, not worked around (Deliverable 2). `pm-core/src/main/resources/de/planmarshall/core/profile/` does not exist yet (HYPOTHESIS, new). The gate of the emit reads one shared path with the running PLAN-V2-40 (`model-work/06`, different hunks) and none with PLAN-V2-138 and 140.
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- Every schema block of the eight documents is an include of a schema file; an example block is still inline and is not a schema.
- A parity test exists for each schema whose codec exists; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in every module touched. After this plan and the three move packages before it, `trace.py` reports no inline schema block below `doc/specification` of this repository.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-119-schema-files-other-specifications.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Rescope and carries (2026-11-09)

Added by the orchestrator after the operator's walk-through of the open defects (decision log, 2026-11-09). Read at the outline, re-check each statement against `origin/main` (Claim Labels) and adopt it into a deliverable of the outline or say in the landing why not. It extends no Done-when by itself.

- **Sequencing (operator 2026-11-09):** run AFTER PLAN-V2-129 (deferred-code removal), so that the schema block of `harness-profiles/03-endpoint-harness` is not moved for code that is deleted.

## Grounding note (cleanup 2026-11-10, main 0dff9ea7, after #354)

Deliverable 1 moves the schema block of `harness-profiles/01-profile-schema.adoc` byte for byte, and Deliverables 2 and 3 compare each block to its codec. The merged pull request #354 (`1b5dc230`) edited that block: `headless.usage` gained the required pointer `model` (`01-profile-schema.adoc`, lines 323 to 326, in the `required` list), while `ProfileValidator` closes `headless.usage` to the seven `ProfileStages.USAGE_FIGURES` (`ProfileValidator.java:946-947`) and no shipped profile carries `model`: the block and its codec now deviate. This plan reports the deviation (a schema is not changed inside the move) and names the plan that closes it: the new Milestone 4 item "the model that answered" (the finding `zed-acp-ingestion-001`), which adds the pointer to the validator and the shipped `claude` profile.
