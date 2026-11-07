# PLAN-V2-115: Schemas as files: the ADR, the layout, the include convention, the test helper and the checks

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (operator decision 2026-11-05: plans are run with Claude Code through the plan lifecycle).
> Lives at `plans/PLAN-V2-115-schema-files-foundation.md` and is queued as one row file, `queue/PLAN-V2-115.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/schema-files-foundation.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/schema-files-foundation.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R11, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer document of the build (the document that states how the repository is built and tested; the outline names it) gains where a schema file lies and how a test loads it; ADR directory: `doc/adr/` (repository-wide ADR; the next free number against `origin/main`, R11); layout: workstream WS-12.
- R12 — log message holder: none (no message is added).

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

The JSON schemas of the specification become files of the module that implements them, and the specification includes the file instead of holding the block. Today ten parity tests and the test helper `SpecificationDocument` read `.adoc` files at run time, which lets a specification-only pull request break `main` (ADR 009). This first package of the schema transition (operator decision 2026-11-06: the highest priority of the epic) states the rule in an ADR, fixes the layout and the include convention, adds the test helper `SchemaResource`, and replaces the documents here in `plan-marshall-documentation` that state the old rule. Every other package of the transition depends on it.

## Deliverables

1. **The ADR, the layout and the include convention.** The next free ADR number at the start of the plan (the last on `main` is 015): the JSON schemas of the specification are files of the module that implements them, the specification includes the file, and a test reads inputs of its own repository only; it supersedes the sentence of ADR 007 that has the parity tests read the specification (`pm.specification.root`). A schema file lies in `src/main/resources` of the module that owns its codec, in the codec's resource package, named `<record>.schema.json` (precedent: `core/role/corpus/`), one file per block, the text of the block byte for byte. The block in the specification becomes `[source,json]` over `----` with one line `include::<relative path>[]`; the heading and the anchor stay; an example block stays inline; the paragraph above the include names the file and its path (GitHub shows a link to the file, not the content). The premise of ADR 009 and the `**/doc/**` entry of ADR 015 are retired by `adoc-reading-retired`, not here.
2. **The test helper `SchemaResource`** beside `SpecificationDocument` in the test sources of `pm-core` (published through `pm-core:tests`, ADR 012): loads a schema from the class path by resource path and parses it with the parser `SpecificationDocument` has; an absent resource or invalid JSON fails the test, never skips it. `SpecificationDocument` stays until `adoc-reading-retired`.
3. **The documents in `plan-marshall-documentation`**, in the pull request beside this one (its CLAUDE.md, _A change that spans code and documents_): the sentence of Project Setup § Planning the Work that keeps the schema blocks "that a test reads as its contract" and the sentence of § Repositories and Checkouts that has a test read a document from the sibling checkout are replaced by the rule of the ADR; the project skill `traced-implementation` says the same; `trace.py` checks that every `include::` target exists (also an include into a sibling checkout) and reports an inline schema block of a specification (a block with `$schema`, or `properties` under `"type": "object"`, or `items` under `"type": "array"`; the test of the check holds an inline example of each shape and one permitted example that is not a schema), failing for the specifications below `pm-mcp-core/doc/specification` and only reporting those of specifications nothing implements yet (`harness-integration/02-bundle-manifest.adoc`).
4. **The developer documentation of the build** names where a schema file lies and how a test loads it.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Not part of the transition:** validating a stored record against its schema file at run time. `networknt` is a test-scope dependency of `pm-core`; a compile-scope validator is a new dependency and waits for the operator (open question `runtime-schema-validation` in the documentation repository). Do not plan it.
- **R10 is replaced for the whole transition family:** R10 tells a plan to keep the blocks a parity test reads byte-stable; the family moves those blocks into files and the tests read the files. A block a test reads is not deleted, it is moved.
- **Two repositories, two pull requests** (the CLAUDE.md of the documentation repository): the documents of Deliverable 3 are a pull request there, opened beside this one; the plan names both in its landing message. Merge only on the operator's word.
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- OBSERVED: ten parity tests and `SpecificationDocument` read `.adoc` files — `pm-core/src/test/java/de/planmarshall/core/SpecificationDocument.java` and the 13 test classes that name `SpecificationDocument` or `pm.specification.root` (grep at the head of `main`, 2026-11-06); `pm-core/pom.xml:22-23,112` declares and passes `pm.specification.root`.
- OBSERVED: the only schema files in a module today are the three corpus schemas `source-overlay`, `source-manifest` and `imported-item` in `pm-core/src/main/resources/de/planmarshall/core/role/corpus/`.
- HYPOTHESIS: the developer document of the build is a section of `CLAUDE.md` § Build or of the Project Setup of the documentation repository — confirm at the outline and name it (verify-at-outline).
- Verify-first clause: before editing, list the open pull requests for one that touches `doc/adr/`, `SpecificationDocument` or `doc/specification/` (the other packages of the transition are staged behind this one, a plan of another epic may not be) and re-count the readers of `SpecificationDocument` at the head of `main`.

## Expected Surface

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/SpecificationDocument.java`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/SchemaResource.java` (new)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/SchemaResourceTest.java` (new)
- OBSERVED: `doc/adr/007-specifications-of-the-engine-in-this-repository.adoc` (the superseded sentence)
- OBSERVED: `doc/adr/README.adoc` (index row)
- HYPOTHESIS: `doc/adr/NNN-schemas-are-files-of-the-implementing-module.adoc` (new; NNN is the next free number against the head of main, R11)
- OBSERVED: `../plan-marshall-documentation/doc/developer/project-setup.adoc` (the other repository)
- OBSERVED: `../plan-marshall-documentation/.claude/skills/traced-implementation/SKILL.md` (the other repository)
- OBSERVED: `../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py` (the other repository; its test is a new file there)
- OBSERVED: `doc/plans/schema-files-foundation.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (shipped: the dependencies and R20) and PLAN-V2-107 (shipped: the generator artifacts, ADR 012, whose `pm-core:tests` publishes the helper). Nothing in the transition runs before this plan is on `main`.
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- `SchemaResource` loads a present schema (matched positive control), fails on an absent resource and on invalid JSON (negative controls); `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in `pm-core`.
- `trace.py` reports `BROKEN LINKS 0`; run on a specification with an inline schema block it reports the block (a negative control per shape), and on a specification whose block is an include it reports none.
- The ADR, the layout, the include convention and the developer documentation of the build are in the pull request; the pull request in the documentation repository is open and named in the landing message.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-115-schema-files-foundation.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
