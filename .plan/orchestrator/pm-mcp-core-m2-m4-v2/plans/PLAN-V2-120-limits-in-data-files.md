# PLAN-V2-120: The stalled threshold and the unit frontmatter limits move into properties files the code reads and the specification includes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off (operator decision 2026-11-07: the next plans are emitted for the runbook lane again).
> Lives at `plans/PLAN-V2-120-limits-in-data-files.md` and is queued as one row file, `queue/PLAN-V2-120.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names and the package file
> `doc/plans/limits-in-data-files.adoc` (tasks with spec anchors only; the package file is deleted by this plan, R01).
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off contract.
>
> Origin: the schema transition, operator decision of 2026-11-06 (highest priority of the epic), filed in the epic inbox as `schema-transition-001.md` by the orchestrating session of the transition; the packages are in `doc/plans/` of `pm-mcp-core` (pull request #304, merged as `60c45a87`). Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time; that lets a specification-only pull request break `main` (ADR 009 and the include list of ADR 015).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`), and repeat the revision and the rule ids in your landing message. Then read the package file `doc/plans/limits-in-data-files.adoc` on `main` and work its tasks.

Rules that apply to this plan: R01, R02, R05, R06, R08, R10 (replaced), R14, R15, R16, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the guide of the package that holds each limit names the key; ADR directory: none; layout: workstream WS-12.
- R12 — log message holder: none unless a load fault is logged (then the holder of `pm-runtime` or `pm-workflow`, R12).

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`), and the project skill `traced-implementation` of `plan-marshall-documentation`. Say in the first progress note which you read.

## Objective

The value of each limit lies in one file of the module; the code reads it from there and the specification includes it. No second copy and no prose that a test has to parse. The stalled threshold of `pm-runtime` and the three unit frontmatter limits of `pm-workflow` move into properties resources with tagged lines; the two tests that match a number out of specification prose become load tests.

## Deliverables

1. **The stalled threshold:** the value of `StalledScopeDetector.THRESHOLD` (`timeouts/02-runtime-leases-operator-web.adoc`, anchor `stalled-threshold`) goes into a properties resource of `pm-runtime` between `# tag::stalled-threshold[]` and `# end::stalled-threshold[]`; the constant is read from it when the class loads and fails fast when the resource or the key is absent.
2. **The unit frontmatter limits:** `MAX_UNIT_FILE_BYTES`, `MAX_FRONTMATTER_LINES` and `MAX_ECHO_LENGTH` (`workflow-dsl/01-grammar.adoc`) likewise, in a properties resource of `pm-workflow`, one tag per limit.
3. **The specification includes the tagged lines** (`include::<path>[tag=…]`) where it states the value; the sentence around it names the key and no longer repeats the number. A limit that cannot be stated that way is named by its key and the sentence links the properties file.
4. **The tests:** the two tests that match a number in the prose are replaced by a test of the load (the present resource is read; the absent resource and a non-numeric value fail, never default) and of the behaviour at the limit, which `UnitFrontmatterReaderTest` and `StalledScopeDetectorTest` already hold.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **No default on a missing value:** a missing resource, key or non-numeric value fails fast; the behaviour of the limit does not change.
- **The other values of the `timeouts` registry** have no test that reads them and are left as they are.
- **A durable protocol is touched** (the stalled-scope detector): R14 and R16 apply.
- **Sonar:** where the lane's Sonar step cannot scan, read the pull request over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with a reason recorded in the progress record.
- **No dependency or plugin is added.**
- **The landing message:** the lane files it (`landing-check`); it carries the standing-rule revision and rule ids and the open items, and names `spec_source` and `spec_differ`.

## Claim Labels

- OBSERVED: `StalledScopeDetectorTest` and `UnitFrontmatterReaderTest` name `pm.specification.root` today (grep, `main` of 2026-11-06).
- HYPOTHESIS: `StalledScopeDetector.THRESHOLD` lies in `pm-runtime/src/main/java/de/planmarshall/runtime/start/StalledScopeDetector.java` and the three limits in `UnitFrontmatterReader` of `pm-workflow` (`workflow/dsl/`) — confirm at the outline (verify-at-outline).
- Verify-first clause: before editing, list the open pull requests for one that touches these classes; PLAN-V2-89 edits the tests of `runtime/start/` and PLAN-V2-98 the production code of `pm-workflow`.

## Expected Surface

- OBSERVED: `doc/specification/timeouts/02-runtime-leases-operator-web.adoc`
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/StalledScopeDetectorTest.java`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/UnitFrontmatterReaderTest.java`
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/StalledScopeDetector.java`
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java`
- HYPOTHESIS: `pm-runtime/src/main/resources/de/planmarshall/runtime/start/` (new directory: the properties resource)
- HYPOTHESIS: `pm-workflow/src/main/resources/de/planmarshall/workflow/dsl/` (new directory: the properties resource)
- OBSERVED: `doc/plans/limits-in-data-files.adoc` (the package file; deleted by the plan, R01)

Not surface (R19): `doc/plans/README.adoc` (the plan removes its own row, R08), `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-115 (`schema-files-foundation`, on `main`). Its surface lies in `StalledScopeDetectorTest` (PLAN-V2-89, landed) and `UnitFrontmatterReaderTest` (PLAN-V2-98, landed), so it can be emitted.
- The packages of the transition: PLAN-V2-115 `schema-files-foundation` (first); PLAN-V2-116 to 119 (the four move packages, disjoint by document, parallel once the foundation is on `main`); PLAN-V2-120 `limits-in-data-files`; PLAN-V2-121 `generated-listings` (parked); PLAN-V2-122 `adoc-reading-retired` (last).
- Overlaps: by file with every plan that edits a test or document named in the Expected Surface; the orchestrator pairs them by the cross-check before emitting.

## Done When

- The load tests with their negative controls; `StalledScopeDetectorTest` and `UnitFrontmatterReaderTest` do not name `pm.specification.root`; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green in `pm-runtime` and `pm-workflow`.
- A search of the test sources for `Pattern.compile` over a specification document finds none.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-120-limits-in-data-files.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
