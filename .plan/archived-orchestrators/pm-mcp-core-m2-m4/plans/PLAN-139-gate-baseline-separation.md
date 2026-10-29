# PLAN-139: Baseline separation of a red gate and the settlement step that stamps the gate's ledger row

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-139-gate-baseline-separation.md` and is queued as one row file, `queue/PLAN-139.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 7 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. R14 and R16 apply: the plan changes `pm-runtime`. This is the last plan of the package: it removes the package's row from `doc/plans/README.adoc` and deletes the package file, and drops the package from every _Depends on_ cell that names it (none names it at `a8da4104`).

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-job.adoc` (extended); `pm-core/doc/developer/core-build.adoc` (extended); ADR directory: `pm-runtime/doc/adr/runtime-job/` and `pm-core/doc/adr/core-build/` (the next free numbers); layout: workstream WS-10.
- R12 — log message holder: `RuntimeLogMessages` (`PM_RUNTIME`) for `runtime.job`; `CoreLogMessages` (`PM_CORE`) for `core.build`; next free identifier of the level.

## Objective

Implement the fourth honesty check, baseline separation, and the settlement step that applies all five checks before a gate is marked `fresh` in `change-ledger.jsonl`, in the work package `build-parsers-and-gate-honesty`: a red gate is checked, by a server rule with no model judgment, for failures already present on the base; located diagnostics on both sides are `baseline`, a coverage threshold failure in a module the plan did not change is `baseline_debt`, an all-baseline red writes a `baseline_red` ledger row bound to the gate's `worktree_sha`, and a base result that cannot be obtained attributes every diagnostic to the plan. The provider's CI result on the base (the primary proof) needs the CI contract, which has no module: it is a declared interface here and its implementation is an open operator decision.

## Deliverables

1. **The verdict engine** (pure, `core.build`): the failure fingerprint (SHA-256 over class, kind, file and the normalised message, the same value as the failure envelope's `fingerprint`), the comparison of the plan's diagnostics with the base's by fingerprint into `baseline` and plan-caused, `baseline_debt` for a coverage failure in a module whose main sources the plan did not change (one non-blocking `info` finding per module, never fanned out), and the fail-closed rule: a base run or result that cannot be obtained or parsed attributes every diagnostic to the plan and says so in the gate's facts. Tests: all-baseline, mixed, none-baseline, unobtainable base, a fingerprint that differs by one normalised character.
2. **The proof sources.** `BaseResultSource`, an interface declared in `pm-core` with the provider's CI result of the same check on the merge base (or on the latest base-branch commit that contains it and has a result) as the primary proof; its implementation is the CI contract and is not built here. The fallback proof, a local build of the merge base in the plan's baseline scratch worktree (`.marshall/local/worktrees/baseline-<plan_id>-<sha12>`, detached, created with `git.worktree-add`, reused while the merge base is unchanged, replaced when it moves, removed when the plan leaves `finalize` or ends, clean before each reuse, a job that dirties it reported as dirtying the base and the worktree replaced): the lifecycle over `GitOperations` and a status check through a `git` job. Tests with a real temporary git repository for the lifecycle.
3. **The `baseline_red` row and the freshness rule.** The row (the kind `BASELINE_RED` and its `baseline` proof object exist in `ChangeLedgerRecord`) is written with the gate's `worktree_sha`, the merge base, the base commit, the proof source and the digest of the accepted diagnostics. The freshness gate does not exist in code yet (`ChangeLedger` only keeps the rows in order): build it as a pure rule over the ledger — a gate is `fresh` for a `gate_kind` when its latest row is a pass for the current `worktree_sha`, and a `baseline_red` row counts as a pass for that same `worktree_sha` only; the gate step completes `done` with `assurance_verdict: degraded`; both build jobs keep their own outcomes. Tests against the real `ChangeLedger`: a `baseline_red` row for another `worktree_sha` does not make a gate fresh.
4. **The settlement step `CHANGE_LEDGER_ROW`** (new class implementing `SettlementStep`, registered through the seam delivered by PLAN-138 (`failed-unparsed-and-parser-wiring`)): applies the five checks of PLAN-140 (`gate-honesty-facts`) and the verdict engine, persists `gate_facts` in the job record, and stamps the gate's `fresh` row or the `baseline_red` row. A red that is not provably baseline-only goes to the build or CI remediation policy (the routing is `remediation-units`; the step records the plan-caused remainder).
5. **Documentation and package close.** Extend the guides, the ADRs (the fail-closed attribution; the proof sources and the open CI contract), delete the implemented § Baseline Separation bullets and PM-WATCH-JOB-8, -17, -25 where covered, and close the package: remove the row of `build-parsers-and-gate-honesty` from `doc/plans/README.adoc`, delete its package file, and drop it from every _Depends on_ cell that names it.

**Done when:** A failure present on the base with the same fingerprint is `baseline` and, for coverage, one `baseline_debt` finding per module; the all-baseline red writes its `baseline_red` row for the same `worktree_sha` only and completes `done` with `assurance_verdict: degraded`; a red that is not provably baseline-only is plan-caused; a base run or result that cannot be obtained attributes every diagnostic to the plan and says so; the baseline worktree is reused while the merge base is unchanged, replaced when it moves and when it is dirty, and removed at the end of the plan; a formatter that rewrites committed files there is reported as dirtying the base; the package row is gone from the index and the package file is deleted. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`, `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-SVC-1, PM-EXEC-6
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Gate Honesty Verification (item 4); `doc/specification/store-schemas/04-epic-and-change-ledger.adoc` § Change Ledger Record; `doc/specification/filesystem-layout/01-repository-workspace.adoc` § Worktree Lifecycle
- Watch: `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-8, PM-WATCH-JOB-17, PM-WATCH-JOB-25

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Open operator decision, not a choice for the lane:** the primary proof needs the provider's CI result on the base, i.e. the CI contract (`ci.*`), which has no module and no package (the `pm-provider-ci` module was removed by #156). R07 applies: if the outline finds two readings that change the file set, ask at refine. Until the operator decides, only the fallback proof is implemented and `BaseResultSource` has no production implementation.
- Format versions stay 1 (R13); a change to the `baseline` object of the ledger record replaces version 1 in place.
- **Fixtures, not the sibling checkout.** Expected values are derived from the original and committed as plain files; the commit of the `plan-marshall` checkout they were derived from is recorded in the fixture directory (the checkout is the sibling `../plan-marshall`, never read by a test; no Python runs in the build).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerRecord.java` line 81 `BASELINE_RED("baseline_red")`, line 45 the `baseline` parameter, line 373 `'baseline' is set exactly for the kind 'baseline_red'`, line 325 the check that the baseline's `gate_kind` is a gate — the row type and its proof object exist; nothing writes one (`grep -rn 'BASELINE_RED' pm-runtime/src/main` finds none)
- OBSERVED: `SettlementStep.java` line 60 names `CHANGE_LEDGER_ROW`; `JobSettlement.java` lines 78-83 map it to `notImplemented`; `ChangeLedger.java` (Javadoc line 24) only preserves the order of the rows — `grep -rln 'freshness\|isFresh' pm-*/src/main` finds the name in `ChangeLedger`, `ChangeLedgerRecord` and `WorkflowStatusCodec` as text, no freshness rule exists
- OBSERVED: `pm-providers/pm-provider-git/.../GitOperations.java` offers `worktreeAdd` (line 53), `worktreeList` (61) and `worktreeRemove` (71) and no `status`, no ignored listing and no merge-base — the lifecycle uses the first three, the rest needs a `git` job or a new operation (a new operation is PLAN-101's file `GitOperations.java`, parked)
- OBSERVED: there is no `ci.*` implementation: `grep -rn 'ci.whoami\|ci.issue-view' pm-*/src/main` finds only a Javadoc mention in `GitLabClient`; the epic ledger records the gap (Open Defects, "The CI contract has no module")
- HYPOTHESIS: the merge base is obtainable through JGit in `pm-provider-git` or a `git merge-base` job; which one is for the outline (a new `GitOperations` method would add `GitOperations.java`, `JGitOperations.java` and `JGitOperationsTest.java` to the surface and overlap PLAN-101)
- Verify-first clause: before editing, confirm that PLAN-140 (`gate-honesty-facts`), PLAN-138 (`failed-unparsed-and-parser-wiring`) and the parsers are merged, list the open pull requests touching `ChangeLedger*`, `runtime/job/` or `GitOperations`, and ask whether the operator has decided the CI contract; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/build/` new classes: the fingerprint, the verdict engine, `BaseResultSource`, the baseline proof types (exact names at the outline) and their tests
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/job/` new classes: `BaselineWorktrees` (the lifecycle), `GateLedgerStep` implementing `SettlementStep`, and their tests under `pm-runtime/src/test/java/de/planmarshall/runtime/job/`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedger.java` and `ChangeLedgerRecord.java` — read; the freshness rule is a new class that reads them; edited only if an accessor is missing (to verify at the outline)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerTest.java` — read; extended only if `ChangeLedger` changes
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc` (deleted), `doc/plans/README.adoc` (row removed: bookkeeping), `doc/specification/job-runtime/03-parsers-and-gates.adoc` (§ Gate Honesty, item 4), `doc/implementation-watch/job-runtime.adoc` (PM-WATCH-JOB-8, -17, -25)

## Dependencies and Sequencing

- Depends on: PLAN-140 (`gate-honesty-facts`), PLAN-138 (`failed-unparsed-and-parser-wiring`) (the settlement seam) and transitively the parsers.
- Sequenced 2026-10-29 by the cross-check: shares `ProcessTreeTracker` and its tests with PLAN-117 (`process-tree-sweep-pid-reuse`); run after PLAN-117 has merged.
- Overlaps with: code-disjoint from PLAN-128 (`adaptive-timeout-learning`) (a different step class). `ChangeLedger.java`/`ChangeLedgerRecord.java` are named in the text of PLAN-42, 53, 58, 73, 92, 100, 104, 105 but in the declared surface of none of the staged specs read for this draft: no declared overlap with PLAN-46, 105, 108, 109. If a `GitOperations` method is added, 3 entries overlap the parked PLAN-101 (`change-map`).
- Adjacent to: the CI contract (no package; an operator decision); `remediation-units` (the routing of the plan-caused remainder); `finalize-pipeline-and-merge` (the merge path that reads the `baseline_red` row).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-139-gate-baseline-separation.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
