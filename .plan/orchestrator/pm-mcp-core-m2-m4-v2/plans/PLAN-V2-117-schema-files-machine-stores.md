# PLAN-V2-117: Move the schema blocks of four machine-store documents to files; the audit and credential parity tests read the file

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (operator decision 2026-11-05: plans are run with Claude Code through the plan lifecycle).
> Lives at `plans/PLAN-V2-117-schema-files-machine-stores.md` and is queued as one row file, `queue/PLAN-V2-117.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/schema-files-machine-stores.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/schema-files-machine-stores.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R14, R16, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none beyond the foundation; ADR directory: none; layout: workstream WS-12.
- R12 — log message holder: none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

A move only, as in `schema-files-tested-records`: 22 blocks in four documents become files, each in the module that owns its codec. `AuditSchemaParityTest` (pm-core) and `CredentialStoreMoveRecordParityTest` (pm-runtime) read the file; the credential test stops extracting members from the schema text with regular expressions and parses the file the way the tests of pm-core do through `SchemaResource`, reachable from pm-runtime through `pm-core:tests` (ADR 012). Every other schema of the four documents whose codec exists gets a parity test over its file.

## Deliverables

1. **Move the 22 blocks** of `store-schemas/11-enrolment-store.adoc` (1), `12-machine-stores.adoc` (18), `13-leases-integrity-testing.adoc` (1) and `15-screening-and-grants.adoc` (2).
2. **The two tests read the file:** `AuditSchemaParityTest` and `CredentialStoreMoveRecordParityTest` replace the block by the file; the latter drops the `PROPERTY` and `REQUIRED` regular expressions.
3. **The records without a test:** for every other schema of the four documents whose codec exists, a parity test over the file the way `AuditSchemaParityTest` has it. A schema whose codec does not exist yet is moved and named in the task of the package that builds the codec.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **A move only:** each file is the former block's text byte for byte; no member added or removed.
- **`pm-runtime` rules apply:** R14 and R16 for any durable protocol or fact the new tests touch; the plan changes test sources and resources only in `pm-runtime`.
- **Overlap with the family:** `CredentialStoreMoveRecordParityTest` lies in `pm-runtime/.../credentials/`, which PLAN-V2-91 (`runtime-tests-workers-and-endpoints`) converts; this plan runs before PLAN-V2-91 (operator decision 2026-11-06: the transition goes ahead of the family wherever the surfaces allow).
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- OBSERVED: `AuditSchemaParityTest` is `pm-core/src/test/java/de/planmarshall/core/store/AuditSchemaParityTest.java`; `CredentialStoreMoveRecordParityTest` is `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreMoveRecordParityTest.java` (find, `main` of 2026-11-06).
- HYPOTHESIS: the schema files lie in `core/store/` of `pm-core` and in a `credentials` resource package of `pm-runtime` — confirm at the outline against the owning codecs (verify-at-outline).
- Verify-first clause: count the blocks of the four documents at the head of `main` (the package says 22).

## Expected Surface

- OBSERVED: `doc/specification/store-schemas/11-enrolment-store.adoc`
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc`
- OBSERVED: `doc/specification/store-schemas/13-leases-integrity-testing.adoc`
- OBSERVED: `doc/specification/store-schemas/15-screening-and-grants.adoc`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/AuditSchemaParityTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreMoveRecordParityTest.java`
- HYPOTHESIS: `pm-runtime/src/main/resources/de/planmarshall/runtime/credentials/` (new directory: the schema files)
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/store/AuditRecord.schema.json` (new; the first of the machine-store files in the store resource package)
- OBSERVED: `doc/plans/schema-files-machine-stores.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`, on `main`).
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- Every `[source,json]` block of the four documents is an include of a schema file, each the former block's text.
- The two parity tests pass against the files and name neither `SpecificationDocument` nor `pm.specification.root`; a test exists for each schema whose codec exists; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in every module touched.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-117-schema-files-machine-stores.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
