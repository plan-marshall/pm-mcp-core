# PLAN-V2-51: Feed the learned build budgets: which runs count, poisoned and cold runs, the timing observation step

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: PLAN-128 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-51-adaptive-timeout-learning.md` and is queued as one row file, `queue/PLAN-V2-51.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 5 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. R14 and R16 apply: the plan changes `pm-runtime`.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-job.adoc` (existing, extended); `pm-core/doc/developer/core-store.adoc` (existing, only if `core.store` changes); ADR directory: `pm-runtime/doc/adr/runtime-job/` (the next free number of the directory); layout: workstream WS-10.
- R12 — log message holder: `RuntimeLogMessages` (`PM_RUNTIME`) for a message of `runtime.job`; `CoreLogMessages` (`PM_CORE`) for `core.store`; next free identifier of the level.

## Objective

Close the loop of the adaptive timeout of the work package `build-parsers-and-gate-honesty`: the store of the learned budgets exists (`BuildTimingsStore`, with the clean, poisoned and cold baselines and the one-shot escalation) but nothing feeds it — the settlement step `TIMING_OBSERVATION` is a seam that does nothing. Decide which finished runs feed the learned baseline `L`, classify a run as `poisoned` or `cold`, implement the settlement step, and compute the seeded expected duration of a CI wait.

## Deliverables

1. **The feed rules** (new class in `runtime.job`, a pure decision): every finished run, whatever its exit code, feeds `L` with its duration; `killed` and `cancelled` runs never do; a `timed_out` run never feeds `L` and sets the one-shot escalation (`BuildTimingsStore.escalate`, the cap from Timeouts & Budgets) that the next finished run of the key clears; a worker (key `llm:<role>`) feeds `L` only with successful work — a fresh worker with its spawn-to-exit duration when it ended `succeeded`, a warm worker with the duration of each attempt that closed its task with a submission (attempt `end: closed`), from the task's offer to the attempt's end, never its idle time; an in-process operation feeds with the time it took minus the time deferred by a provider rate limit. One test per row (PM-WATCH-JOB-15, -16, -33).
2. **Residue classification.** A finished run whose worktree held git-ignored build output above a named engine constant at its start is `poisoned`; a run that starts after a dependency sync or a build-cache invalidation since the key's previous observation (a changed resolved-dependency set or a cleared cache) is `cold`. `pm-runtime` may not depend on `pm-provider-git` (the module rules), so both facts arrive through seams declared in `pm-core` or `runtime.job` and implemented by the assembly; the constant is recorded with the observation. Tests: a poisoned and a cold run are stored as such and not blended while clean observations exist; a poisoned run is never read as a regression.
3. **The `TIMING_OBSERVATION` settlement step** (new class implementing `SettlementStep`, registered through the settlement seam delivered by PLAN-V2-36 (`failed-unparsed-and-parser-wiring`)): calls `BuildTimingsStore.observe(key, duration, residue, observedAt)` or `escalate` per the feed rules; a store failure it can name is `Failed`. `budget_derivation` (`learned` with its observation count, `escalated`, `operator`, `floor_default`) stays in the job record and in no representation; a `timed_out` outcome is `no_verdict` whatever the derivation. Tests with a real `BuildTimingsStore` on a temporary directory.
4. **The seeded CI wait budget:** a pure function that returns the expected duration of a CI wait from the project's known-slow ceilings (the operator budgets of the enrolment) when no learned figure exists; it sets only the expected duration recorded with the wait and never a failure deadline. Where no composite-wait record exists at HEAD to carry it, the function and its test are the deliverable and the use is named for the package that builds the wait (Hypermedia Format § Composite Wait). Review-bot waits are never bound by a learned or seeded figure.
5. **Documentation:** extend `pm-runtime/doc/developer/runtime-job.adoc` with the feed table; the ADR for the seams of residue and cold detection; delete the implemented § Adaptive Timeout Learning bullets of `doc/specification/job-runtime/03-parsers-and-gates.adoc` and PM-WATCH-JOB-15, -16, -33 where covered. Name the assembly's registration of the step and the two seams as the follow-up owed to `plan-marshall-mcp` (R16(2)).

**Done when:** A table-driven test shows, per fate and per kind, whether a run feeds `L`, escalates or is ignored, and the worker rule for a fresh and a warm worker; a poisoned and a cold run are stored in their own baselines and not blended while clean observations exist; the step observes into a real store and a timed-out run leaves the learned value unchanged and sets the escalation, which the next finished run clears; the seeded expected duration is never a deadline; `budget_derivation` appears in the job record and in no representation; the guide and the ADR are written. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`, `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXEC-6, PM-EXEC-4
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Adaptive Timeout Learning; `doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc` § Build Timings Store; `doc/specification/timeouts/01-job-budgets-and-waits.adoc` § Job Budgets
- Watch: `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-15, PM-WATCH-JOB-16, PM-WATCH-JOB-33

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- Format versions stay 1 (R13): the build timings document is not versioned further here.
- No learned value of `plan-marshall` is read or imported (`PM-MIG-2`, Job Runtime § No Inherited Baselines).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/BuildTimingsStore.java` line 73 `observe(String key, Duration duration, Residue residue, Instant observedAt)` and line 101 `escalate(String key, long budgetSeconds, long capSeconds)`; `BuildTimings.java` line 127 `KeyTimings(clean, poisoned, cold, escalationSeconds, …)` and line 182 `enum Residue { CLEAN, POISONED, COLD }` — the store is complete
- OBSERVED: `grep -rn '\.observe(\|\.escalate(\|BuildTimings' pm-runtime/src/main pm-workflow/src/main` finds no caller of the store; `SettlementStep.java` line 62 names `TIMING_OBSERVATION` and `JobSettlement.java` lines 78-83 map it to `SettlementStep.notImplemented(name)` — nothing feeds the store
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobBudget.java` line 31 `record JobBudget(String key, long seconds, BudgetDerivation derivation)`; `JobRecord` and `JobRecordCodec` carry `budget_derivation` — the record side exists
- OBSERVED: `pm-runtime/pom.xml` declares no dependency on a provider module (`grep provider` finds none), so the ignored-file count of `WorktreeShaDigest` (`pm-providers/pm-provider-git`) is not reachable from `runtime.job` without a seam
- HYPOTHESIS: the cold-start fact (a changed resolved-dependency set or a cleared cache) has no producer in the repository yet; the seam may stay without an implementation in this repository and be owed to the assembly — confirm at the trace matrix (verify-at-outline)
- HYPOTHESIS: the worker attempt durations (offer to attempt end, `end: closed`) are readable from `QueueTaskRecord`/`AttemptEnd` of `core.queue`/`core.store` without a new member — confirm at `AttemptEnd.java` (verify-at-outline)
- Verify-first clause: before editing, confirm that PLAN-V2-36 (`failed-unparsed-and-parser-wiring`) is merged (the settlement seam), list the open pull requests touching `runtime/job/` or `BuildTimingsStore`, and re-read the claims above at the head; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/job/` new classes: the feed-rules decision, the residue/cold seams, `TimingObservationStep`, the seeded wait budget (exact names at the outline) and their tests under `pm-runtime/src/test/java/de/planmarshall/runtime/job/`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/BuildTimingsStore.java` and `BuildTimings.java` — read; edited only if a method the step needs is missing (expected: no)
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc`, `doc/specification/job-runtime/03-parsers-and-gates.adoc` (§ Adaptive Timeout Learning), `doc/implementation-watch/job-runtime.adoc` (PM-WATCH-JOB-15, -16, -33) — edited, section-disjoint with the sibling plans
- OBSERVED: `pm-runtime/doc/developer/runtime-job.adoc` — extended (additive)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-36 (`failed-unparsed-and-parser-wiring`) (the settlement seam it registers the step through).
- Sequenced 2026-10-29 by the cross-check: shares `ProcessTreeTracker` and its tests with PLAN-V2-66 (`process-tree-sweep-pid-reuse`); run after PLAN-V2-66 has merged.
- Overlaps with: code-disjoint from PLAN-V2-55 (`gate-baseline-separation`) (a different step class); no entry shared with PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43. `BuildTimingsStore.java`, `BuildTimings.java` are named by no live plan; PLAN-V2-42 (`runtime-metrics`) will implement the neighbouring `METRICS_ROW` step and may touch `SettlementStep.java`/`JobSettlement.java` (it does not declare them): the lane merges the base branch first.
- Adjacent to: `plan-marshall-mcp` `pm-mcp-server` (registers the step and the seams); Timeouts & Budgets, whose blend formula and floors are not touched here.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-51-adaptive-timeout-learning.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
