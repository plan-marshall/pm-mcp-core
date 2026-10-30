# PLAN-V2-37: Gate honesty facts: examined population, gate coverage, skipped tests and untracked side state

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: PLAN-140 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-37-gate-honesty-facts.md` and is queued as one row file, `queue/PLAN-V2-37.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 6 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. R10 binds in particular: `JobRecordSchemaParityTest` reads the JSON schema of the job record in `store-schemas/10-job-and-lesson-records.adoc`; that block stays byte-stable.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` (extended); ADR directory: `pm-core/doc/adr/core-build/` (the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`).

## Objective

Implement four of the five honesty checks of a gate in the work package `build-parsers-and-gate-honesty`, as pure functions of package `core.build` that the settlement of a build job applies before a gate may be marked `fresh`: (1) the examined population, (2) the gate coverage facts persisted as `gate_facts`, (3) the test-scope divergence with the `skipped_tests` record, and (5) the untracked side state. Baseline separation (check 4) is the plan PLAN-V2-55 (`gate-baseline-separation`).

## Deliverables

1. **Examined population (check 1).** From a parsed build, the set of modules and source files actually compiled and tested must be non-empty and cover every module mapped from the plan's realized file footprint. Tests: an empty population, a footprint module missing from the log, a complete one.
2. **Gate coverage (check 2).** The gate scope is computed from the `module` argument, never the verb: absent means the whole tree, present means that module only; a module-scoped run of any verb never satisfies a whole-tree gate, and a whole-tree gate (`quality-gate`, `verify`, `coverage`, `arch-gate`) is satisfied only by a whole-tree run of that verb. The server-derived facts, ported from `script-shared/scripts/build/_gate_coverage.py` (verified at plan-marshall commit `88fcfc9ef`) and its suites `test_gate_coverage.py` and `test_gate_coverage_parity_substrate.py`: `gate_coverage` (`COMPLETE`, `PARTIAL` with the degraded dimensions and reasons, `UNKNOWN`), `empty_scope` (neither a pass nor a gap), `structural_limits` per checked analysis kind (an unregistered kind is `UNKNOWN`, never omitted), `uncovered` split into `not_at_this_scope` and `not_by_this_gate` (`undeclared` without a declared scope, never an empty list), and a non-empty `parity_population`. A `GateFacts` type serialised to the `gate_facts` object of the job-record schema (the codec keeps writing it as raw text, `JobRecord.gateFacts`); a parity test asserts the written object validates against the schema block of `store-schemas/10`. The rendered subset is only `gate_coverage`, the degraded dimensions and `uncovered`.
3. **Test-scope divergence (check 3).** Port of `_test_scope_divergence.py`: detect and reject runs where test filter flags or skipped-test properties excluded tests of modified modules. A test that skips itself at run time (an assumption, a skip marker, a missing fixture) is not a divergence: it is recorded in `skipped_tests` per module of the realized footprint with the reason the parser reads (`null` where the log gives none), blocks nothing, changes no `gate_coverage` and is never rendered. Suite: `test_test_scope_divergence.py`.
4. **Untracked side state (check 5).** Compose the argument vector of `git status --porcelain --ignored` (ignored directories collapsed) for a `git` job in the plan's worktree, parse its output, drop every path that matches the compiled artifact pattern table (build output, dependency and tool caches — the table is new, in `core.build`) or lies under `.marshall/**`, and record the rest in `side_state` (at most 100 paths with the total count); it blocks nothing, changes no `gate_coverage`, is not rendered, and the plan's completion report lists it for a plan that lands without CI (PM-WATCH-JOB-39). Tests: an ignored fixture file is listed while `target/` and `node_modules/` are not; 150 ignored paths give 100 and a total of 150.
5. **Documentation.** Extend `core-build.adoc`; ADRs for the shape of `GateFacts` and the artifact pattern table; delete the implemented bullets of § Gate Honesty Verification (items 1, 2, 3 and 5) and PM-WATCH-JOB-39 where covered. The settlement step that applies the checks and stamps the ledger row, and the baseline check, are PLAN-V2-55 (`gate-baseline-separation`).

**Done when:** A gate result carries `gate_coverage`, `structural_limits`, `uncovered` and `parity_population` in a `gate_facts` object that validates against the job-record schema, with the split of `uncovered`; a module-scoped run never satisfies a whole-tree gate; a run that filtered out tests of a modified module is detected; a skipped test appears in `skipped_tests` with its reason (`null` where none) and blocks nothing; an ignored fixture a worker created is in `side_state` while `target/` and `node_modules/` are not, capped at 100 with the total; the ported suites' expected values are the fixtures' (commit recorded); no test runs Python. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-SVC-1, PM-EXEC-6, PM-TEST-2
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Gate Honesty Verification (items 1, 2, 3, 5); `doc/specification/store-schemas/10-job-and-lesson-records.adoc` § Job Record (`gate_facts`); `doc/specification/store-schemas/04-epic-and-change-ledger.adoc` § Change Ledger Record
- Watch: `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-39; `../plan-marshall-documentation/doc/implementation-watch/cross-cutting.adoc` PM-WATCH-GEN-2

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Fixtures, not the sibling checkout.** Expected values are derived from the original and committed as plain files; the commit of the `plan-marshall` checkout they were derived from is recorded in the fixture directory (the checkout is the sibling `../plan-marshall`, never read by a test; no Python runs in the build).
- **Log messages: none expected.**
- The `gate_facts` object is never rendered beyond `gate_coverage`, the degraded dimensions and `uncovered` (Hypermedia Format § Model-Facing Minimality): this plan only produces the facts.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: `JobRecord.java` line 95 carries `String gateFacts` as raw text (`requireRawObject(gateFacts, "gate_facts")` line 502, only for kind `build` line 580) and `JobRecordCodec.java` line 94 `GATE_FACTS = "gate_facts"` writes it unchanged; no `GateFacts` type, no `skipped_tests`, no `side_state` producer exists (`grep` over `pm-*/src/main` finds the names only in the record and its codec)
- OBSERVED: the schema of `gate_facts` is in `doc/specification/store-schemas/10-job-and-lesson-records.adoc` from line 145 (members `gate_coverage`, `degraded`, `empty_scope`, `structural_limits`, `uncovered`, `parity_population`, `skipped_tests`, `side_state`) and is read by `pm-core/src/test/java/de/planmarshall/core/store/JobRecordSchemaParityTest.java`
- OBSERVED: the originals exist in the sibling checkout: `script-shared/scripts/build/_gate_coverage.py`, `_test_scope_divergence.py`, `_test_scope_targets.py`, `_build_examined.py`, and the suites `test/plan-marshall/build-pyproject/test_gate_coverage.py`, `test_gate_coverage_parity_substrate.py`, `test_test_scope_divergence.py`
- OBSERVED: `pm-providers/pm-provider-git/.../GitOperations.java` has no `status` operation and no `ignored` listing (`grep` finds neither), which is why check 5 is an argument vector for a `git` job and a parser, not a JGit call
- HYPOTHESIS: the examined population and the skipped tests are readable from the parsed build of PLAN-V2-08 (`native-build-parsers-jvm`) and PLAN-V2-28 (`native-build-parsers-npm-and-python`) (module list, test names with skip reasons); if the model lacks them, the plan extends it and says so in the ADR (verify-at-outline)
- Verify-first clause: before editing, confirm that PLAN-V2-08 (`native-build-parsers-jvm`) and PLAN-V2-28 (`native-build-parsers-npm-and-python`) are merged, re-read `_gate_coverage.py` at the head of the sibling checkout (the spec pins `88fcfc9ef`: re-verify), and list the open pull requests touching `core/build/` or `JobRecord`; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/build/` new classes: the examined-population check, `GateCoverage` and `GateFacts` with their JSON writer, the divergence detector, the side-state argument vector, parser and artifact pattern table (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/build/` new test classes (including a `GateFactsSchemaParityTest` reading `store-schemas/10`) and `pm-core/src/test/resources/ported/gate/` fixtures (new)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JobRecord.java` — read-only unless the outline shows `gate_facts` needs a typed accessor (expected: untouched)
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc`, `doc/specification/job-runtime/03-parsers-and-gates.adoc` (§ Gate Honesty Verification), `doc/implementation-watch/job-runtime.adoc` (PM-WATCH-JOB-39) — edited, section-disjoint
- HYPOTHESIS: `pm-core/doc/developer/core-build.adoc`, `pm-core/doc/adr/core-build/` — extended additively once they exist (R19)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-08 (`native-build-parsers-jvm`) and PLAN-V2-28 (`native-build-parsers-npm-and-python`) (the parsed-build model, the skipped tests); PLAN-V2-18 (`coverage-report-parsers`) is read for the coverage dimension of `gate_coverage` if merged, otherwise the dimension is `UNKNOWN`.
- Sequenced 2026-10-29 (decision 14 of 18): PLAN-V2-59 (`egress-record`) is the only plan of this group that edits the pinned job-record block of `store-schemas/10` (and `JobRecord`, `JobRecordCodec`); run it first and merge the base before your gate.
- Overlaps with: code-disjoint from PLAN-V2-55 (`gate-baseline-separation`) (that plan reads `GateFacts` and adds the verdicts) and from every live plan: PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43 do not name `core/build/` or `JobRecord`. `JobRecord.java` is named by PLAN-V2-24, PLAN-V2-41, PLAN-V2-74, PLAN-66, PLAN-V2-26, PLAN-V2-22 as text but declared in the surface of none of the staged specs read here (R19 class lists name `QueueTaskRecord`, `WorkflowStatus`…) — if the outline must edit it, that is an undeclared overlap to report.
- Adjacent to: PLAN-V2-55 (`gate-baseline-separation`) (the settlement step and the baseline verdicts); `remediation-units` (its gather states read the gate facts).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-37-gate-honesty-facts.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
