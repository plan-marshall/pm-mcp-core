# Landing Analysis: PLAN-V2-25 — command-record-and-dispatch

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #542 (squash merge `aaa7c002`, 46 files, +2307/-106)

> Landing record, plan-marshall form (`plan-v2-25-command-record-and-dispatch`, harness claude, model `claude-sonnet-5-5`, effort unknown, 4 deliverables: 2 shipped as specified, 2 shipped modified by recorded operator decisions). One landing message (`-004`, 2026-11-21T15:37:28Z, `merge_state=merged`) and three candidate-lesson messages (`-001` to `-003`).
> Landing facts: `unresolved_review_threads=unknown`, `review_body_findings_total=unknown`, `review_body_findings_handled=unknown`, `sonar_open_new_issues=0`, functional review 1 met and 3 met with residuals, 0 not met, code-review `blocking_count=0`, `advisory_count=5`, `total_tokens` 19 750 184, `total_wall_seconds` 19 214 (5 h 20 min), no blocker.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the value records | shipped-modified | `Command`, `Provenance` (with `Trigger`, nine pinned wire names) and `CommandSettlement` with their tests; `Optional<String>` became `@Nullable String` on `triggerRef`, `reason`, `spawnedJobId`, `raisedCondition` (CLAUDE.md allows `Optional` only as a return type; ADR core-spi/015); `CommandGenerator` added beyond the spec. |
| D2 the dispatch through the value | shipped-modified | `Provenance` replaces the spec's trigger, trigger reference and correlation id on `Run` (Decision 3); all 12 `new Run(` sites are mapped (10 with a provenance, `composeOperatorEvent` and `restingWait` with null by operator decision Q1 A); `CommandDispatchTest` covers the Done-when (three settlements in order, distinct ids, one correlation, `assertSame` input, every `Trigger`, a mapped reason, determinism under equal seeds). |
| D3 the settlements on the step | shipped-as-specified | `copyOf` is `List.copyOf(requireNonNull(...))` (`EngineStep.java:218`); the constructors' refusals unchanged; `ConditionRaised.of` and `raise` give an empty list. |
| D4 specification, guides, ADRs | shipped-modified | `[#command-and-settlement]` at `05-primitives.adoc:61-64` without the "Remaining: the persisted command log" line; the two guides; 10 ADRs (core-spi 014 to 018, workflow-engine 023 to 027) for the eight decisions plus two from carried item 73; Traceability lines for PM-IMPL-5 and PM-WF-16. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, three layers in one brief; no file written, no build run, HEAD not moved). Its counts: 18 existing test files touched (14 in pm-workflow, 1 in pm-core, 3 in pm-runtime), 4 forced arity edits, 10 ADRs. The 5 528, 8 133, 8 579 and 11 828 test counts of the PR body are unchecked.
- **Verdict:** no blocker. The engine's behaviour is unchanged by the code read; the residuals the landing names are real and minor.

### Layer 1 — Specified

- "Every existing test passes UNCHANGED": the 4 construction sites the spec names were edited with `List.of()` (`WorkflowEngineTest` at 736, 740, 743 and `EngineResumeTest:281`; the PR body says 737); the other 14 hunks are imports or whitespace; the PR body does not list the `WorkflowEngineTest.java:578` hunk and gives per-module `verify` counts instead of one command.
- The completeness test derives the permitted set from `EngineEvent.class.getPermittedSubclasses()`; the sample events and the four entry-point triggers are listed by hand. The clock is fixed (`CommandDispatchTest:80-83`), so `issuedAt` and `settledAt` are not told apart (minor).
- Carried item 73 (the stale statements after PLAN-V2-19's merge): honoured in the code (sealed `PrimitiveRun`, settlement on both exits, narrower reason grammar ADR 017, raw facts ADR 027); #542 touches no spec in the epic tree.

### Layer 2 — Implemented

- `runPrimitive` (`WorkflowEngine.java:1546-1583`) builds the `Command` around the binder's record, calls the handler with `command.input()`; `settle` runs on the raised exit before `Raised` (`:1571`) and on the routed exit after `transitionOf` (`:1581`); a fault leaves no settlement. Every step-returning site passes `settlements`.
- **L2-1 (minor):** `spawnedJobId` mapping is untested; the raised exit carries `result.spawnedJobId()` (`:1575`) although the engine binds no job. **L2-2 (minor):** no provenance test for the transactional overloads (`:612`, `:629`, `:647`, `:666`). **L2-3 (minor):** the null-provenance refusal (`:1548`) has no test. **L2-4 (minor):** the wire-name grammars are duplicated (`CommandSettlement.java:59`, `:61` copy `PrimitiveRegistry.java:57`, `:67`).
- No toString leak through the generated record `toString` of the `EngineStep` variants (the settlements print through safe `toString`s). Unchecked premise (L2-6, minor): the Lombok `@Builder` toString on `Command`, `CommandSettlement` and the step variants would print values if a builder were printed.
- Interaction with PLAN-V2-19: an unmappable `conditionKind` now throws `IllegalArgumentException` from the `CommandSettlement` constructor before `ConditionRaised.raise`; the site and message differ from what item 71(b) of PLAN-V2-161 describes. Seeded ids shift by one draw per primitive run (documented in Decision 2).
- No new hand-written fake; `CommandDispatchFixtures` uses an EasyMock binder and composes `EngineFixtures` and `ReasonFixtures`.

### Layer 3 — Correct

- No other module constructs `EngineStep` or its variants; `plan-marshall-mcp` (origin/main `02639c1`) has no reference to the engine; no record-pattern deconstruction exists. ADR numbers unique per directory; links resolve.
- Stale after the merge: `CommandId.java:21-25`, `core-id.adoc:40-41` and `identifiers/01:237-238` (one id per call frame; now also one per primitive run); the guide and Traceability claim "one correlation per advance, the one its pushed frames carry" cites a test that checks commands, not frames; "not persisted" and "a fault ends the advance before a settlement exists" cite no test.
- PR #545 (PLAN-V2-20): behind main by 3 commits, GitHub reports MERGEABLE and CLEAN; the shared files are `Traceability.adoc` and `05-primitives.adoc` with disjoint hunks; no compile coupling to `Command` or `EngineStep`; no rebase is forced. Its spec's example anchor is now `05-primitives.adoc:66-107`, not `:59-100`.

### Review State

CodeRabbit refused with a quota notice on #542; with `review_rate_window_await: false` the step looped back twice before the operator chose "Re-ask CodeRabbit"; iteration 3 triggered the stale-notice recovery and the review arrived. The thread and finding counts are `unknown` in the landing facts. Sonar 0 new issues. Operator choice at the overlap gate on `doc/Traceability.adoc`: `auto_rebase_threshold=auto_resolvable` for this plan only.

## Metrics and Anomalies

19.75M tokens, 5 h 20 min wall; phase 5 had five hand-backs because the host moved every build longer than 180 s into the background (5-execute idle 1 h 2 min of 2 h 10 min; maven 77.7% of script time); all 16 dispatch-boundary rows lack `--step-id`. The links check could not run from the worktree path; run from the main checkout it found no broken link introduced by the branch and 0 inline schema blocks (97 broken links from stale sibling checkouts).

## Routing and Merge Behavior

Merged through the merge queue (`aaa7c002`). `cleanup_owed=false`. Four candidate lessons forwarded to the plan-marshall lessons-routing inbox (`-053` to `-056`): the stale-notice recovery of automatic-review with the rate-window await off, the 180 s auto-background of phase-5 builds, the missing `--step-id` on `record-dispatch-boundary`, and the landing facts written `unknown` (by the orchestrator).

## Reconciliation Actions

- Row PLAN-V2-25 transitioned to `shipped`; `pr` `#542`, `landing`, `plan_marshall_plan_id` `plan-v2-25-command-record-and-dispatch`.
- Residue placed, none new staged: item 77 in PLAN-V2-164 (the untested `spawnedJobId` mapping, the transactional provenance, the null-provenance refusal, the builder toString premise, the constructor failure site; its ADR numbers and the engine order of item 74 corrected), item 78 in PLAN-V2-161 (the `CommandId` drift, the weak guide citations, the missing "Remaining" line, the third grammar copy, the ADR list of the guide, the moved lines of item 71). PLAN-V2-163 is running, so its stale cites are recorded here and not in its spec.

## Follow-Ups

- Stale in PLAN-V2-163 (running, spec not edited): item 70(d) `workflow-engine.adoc:847-851` is `:893-897` and the Decisions list now runs 001 to 018 and 023 to 027; item 70(g) has a third grammar copy (`CommandSettlement.java:59`, accepts `not-found` at `CommandSettlementTest:102`, `:112`); "engine rework (PLAN-V2-25 item 73)" is done; `core-spi.adoc:405` is about `:436` and `:91-95` about `:115-119`. Item 78(d) and (e) of PLAN-V2-161 carry them.
- Stale in PLAN-V2-125 (counts +1 for the "every datum" literal at `CommandDispatchFixtures:46`, about three more lower-cased `letterStrings` helper sites): read at the head before editing.
- The functional-review and measurement gaps (thread and finding counts `unknown`): lesson `-056`.
