# PLAN-V2-122: Delete SpecificationDocument and pm.specification.root and shrink the docs-only include list

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (operator decision 2026-11-05: plans are run with Claude Code through the plan lifecycle).
> Lives at `plans/PLAN-V2-122-adoc-reading-retired.md` and is queued as one row file, `queue/PLAN-V2-122.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/adoc-reading-retired.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/adoc-reading-retired.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R11, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the build document of Foundation is checked; ADR directory: `doc/adr/` (the ADR of the foundation is amended: ADR 009 and the `**/doc/**` entry of ADR 015 are superseded; R11 for any new number).
- R12 — log message holder: none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

The closing package of the schema transition. When every package before it is done, no test reads a specification: this plan deletes `SpecificationDocument` and the property `pm.specification.root`, narrows the `**/doc/**` entry of the include list of the docs-only skip (ADR 015) to the generated log message files of `generated-listings` (decided 2026-11-08: those are the only files below a `doc/` tree a test still reads), and marks ADR 009 and the entry superseded.

## Deliverables

1. **Delete** `SpecificationDocument` and the property `pm.specification.root`: its declaration and its pass-through to the tests in `pm-core/pom.xml`, and the `..` fallback of every test that read the property.
2. **Shrink the include list** of `maven-build.paths-code-extra` in `.github/project.yml`: `**/doc/**` narrows to the generated log message files of `generated-listings`, the only files below a `doc/` tree a test still reads (unconditional since the decision of 2026-11-08). `**/src/**` stays: a fixture below a source tree is build input.
3. **The ADR:** the ADR of `schema-files-foundation` states that the premise of ADR 009 and the `**/doc/**` entry of ADR 015 no longer hold; the ADR index marks them superseded.
4. **Verify by search:** no test source opens a path below `doc/` (a search for `"doc/`, `Path.of("..`, `Path.of("doc` and `getProperty("pm.` in the test trees), apart from the byte check of the generated log message files of `generated-listings`.
5. **Documents here:** the two sentences of project setup about `skip-on-docs-only` and the docs-only pull request are checked; the pull request beside this one is only needed where one names them.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Start only when every package of the transition is done:** the search of Deliverable 4 is the proof; a reader that remains is a finding, not a deletion.
- **Observation:** the first pull request after this plan that changes only a file below `doc/specification/` skips the build and is reported correctly, because no test reads it (as ADR 015 notes for its own change).
- **Sonar:** where the plan lifecycle's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** this plan is started with a spec path, so no `emit-landing` runs; write the landing message to the epic inbox by hand as the last step (`inbox write --kind landing`), with the `landing-facts` block, the standing-rule revision and rule ids, and the open items.

## Claim Labels

- OBSERVED: ADR 015 (`doc/adr/015-the-docs-only-build-skip-with-a-code-include-list.adoc`) is on `main` with the include list `**/doc/**` and `**/src/**`; `.github/project.yml` carries `paths-code-extra`.
- Verify-first clause: the search of Deliverable 4 at the head of `main` before editing.

## Expected Surface

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/SpecificationDocument.java` (deleted)
- OBSERVED: `pm-core/pom.xml`
- OBSERVED: `.github/project.yml`
- OBSERVED: `doc/adr/009-no-build-skip-for-a-specification-only-pull-request.adoc`
- OBSERVED: `doc/adr/015-the-docs-only-build-skip-with-a-code-include-list.adoc`
- OBSERVED: `doc/adr/README.adoc` (index rows)
- OBSERVED: `doc/plans/adoc-reading-retired.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-116, 117, 118, 119 and 120 (117, 118 and 120 are shipped) and PLAN-V2-121 (`generated-listings`, decided 2026-11-08: it keeps a byte check of the generated log message files below `doc/`, whose names this plan reads). Emit last.
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (decided, staged); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- The search of Deliverable 4 finds no reader of a specification; `verify` green in every module.
- A pull request that changes only a file below `doc/specification/` skips the build and is still reported correctly; the first such pull request after this plan is the observation.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-122-adoc-reading-retired.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
