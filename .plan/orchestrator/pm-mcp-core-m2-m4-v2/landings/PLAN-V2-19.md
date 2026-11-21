# Landing Analysis: PLAN-V2-19 — engine-carries-reasons

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #531 (squash merge `1ada535b`, 46 files; the plan's own #530 was closed and #531 opened on the same branch to re-ask CodeRabbit)

> Landing record, plan-marshall form (`plan-v2-19-engine-carries-reasons`, harness claude, model `claude-sonnet-5-5`, effort medium, 5 deliverables: functional review 2 met and 3 met with residuals). One landing message (`-005`, 2026-11-21T09:44Z, `merge_state=merged`) and four candidate-lesson messages (`-001` to `-004`). The message names #530 in its `pr` key and #531 in its residue.
> Landing facts: `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` and `sonar_open_new_issues` are `unknown` (the lifecycle records none; `sonar-roundtrip` recorded `new_code_issue_count=0`, confirmed); `step.code-review.advisory_count=17`, `blocking_count=0`; functional review 21 advisory, 11 unrequested changes, 5 false self-reports; `total_tokens` 49 540 719, `total_wall_seconds` 142 845 (about 39 h 40 min), no blocker.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the engine checks the reason and raises the condition | shipped-modified, code as specified | `WorkflowEngine.runPrimitive` (`:1496-1520`) returns a private `PrimitiveRun`; `requireDeclaredReason` (`:1530`) raises `UNDECLARED_OUTCOME`; a mapped reason raises before the facts merge (`:1510-1516`); three callers (chain AutoStep `:1221-1228`, `chainOrRaise` `:526-532` for a resumed composite job and an operator link). The "raw type" foreign reason became a static-switch enum `SwitchOutcome` (`ReasonFixtures.java:239,502-525`); "the facts are not merged" has no test (the guide says so, `workflow-engine.adoc:218-224`). |
| D2 the reason on the history line | shipped-modified | optional `reason` on `PlanHistoryLine`, written only when present by `PlanHistoryCodec`; `history-line.schema.json` gets the property and two if/then clauses; format version stays 1; extra: a reason wire-name grammar in `PrimitiveRegistry.java:67,277-279` (ADR core-spi/013) the spec did not request. |
| D3 the verifier names a reason mistaken for an outcome | shipped-as-specified, fixtures moved | `coverOutcomes` and `reasonOutcomesOf` in `WorkflowValidator`; message verbatim; the fixtures sit in `verify/reasons/{refused,passing}/`, not `verify/{refused,passing}/`. |
| D4 specification and watch | shipped-modified | 04-ast, state-catalogue/01, 05-primitives, store-schemas/02, `PM-WATCH-DSL-44`; residuals: `Specification.adoc:32` says 38, DSL-44 in the wrong watch section, the 05-primitives paragraph not in the spec's "Implemented by / Remaining" form. |
| D5 guides and ADRs | shipped-modified | workflow-engine 019 to 022, workflow-verify 040, core-store 080 and an unplanned core-spi 013; neither guide's Decisions list is updated (`workflow-engine.adoc:818-851` ends at ADR 018, `workflow-verify.adoc:480` says "001 to 039"). |

Which three deliverables the review graded "met with residuals" is not in the landing; the leaf inferred D1, D4 and D5 or D2.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, three layers in one brief; it wrote no file, ran no build; it wrote and deleted one scratch file `.plan/temp/WE.java`, `git status` clean afterwards). Its measurements are the leaf's; the orchestrator re-read none of them.
- **Verdict:** no blocker. The merged code does what the spec says: the engine checks a reason, raises its mapped condition instead of the transition, never routes on it, and the verifier names a reason routed as an outcome. The defects are documentation and index drift, one spec statement about clearing that the engine does not honour for event-driven states, and untested clear-and-rerun effects. All are latent: no production primitive declares an `OutcomeReason` yet.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | D1 Done-when: a foreign reason through a raw type; the facts are not merged | checked | refutes (partly) | replaced by `SwitchOutcome` (`ReasonConditionTest:456-519`, three permutations); the facts assertion is untested. |
| L1-2 | surface | checked | info | 13 undeclared files by the leaf's count (the plan says 11): `OutcomeReason`, `PrimitiveRegistrationException`, `PrimitiveRegistry` and its test, `ReasonPrimitives`, `ReasonVerifyPrimitives`, `WorkflowEngineRecordsTest`, the two `hand-written-doubles.txt`, `ImplementationWatch.adoc`, ADR 026, `core-spi.adoc`, ADR core-spi/013; declared and untouched: `PlanHistory.java`, `EngineStep.java`, `VerificationRule.java`. |
| L1-3 | the watch count 38 against 39 | checked | confirms | the watch file has 39 items; `Specification.adoc:32` still says 38. |
| L1-4 | the missing PM-IMPL-5 backlink | checked | confirms | `trace.py`: `NO BACKLINK PM-WATCH-DSL-44 -> Requirements.adoc#PM-IMPL-5`; a documentation-repository defect, also absent at its `origin/main`. |
| L1-5 | Decisions 1 to 6 hold in the code | checked | confirms, one exception | Decision 1 holds for auto states; for composite-job and operator-link states "the state is run again after the clear" does not (L3-1). |
| L1-6 | the links check | checked | info | `BROKEN LINKS 50`, `INLINE SCHEMA BLOCKS 0`, none in a file of this PR; the documentation checkout is 6 commits behind origin. |
| L1-7 | no existing test changed its expectation | checked | refutes, narrowly | three tests changed signature only (`WorkflowEngineRecordsTest`, `PlanHistoryLineBuilderContractTest`, `PlanHistoryTest`). |
| L1-8 | the reason grammar `[a-z][a-z0-9_]{0,31}` | checked | info | it narrows PLAN-V2-01's registry rule, with ADR core-spi/013 and a test; neither requested nor owned by the spec. |

**Layer 1 verdict:** findings — 4 minor, rest info.

### Layer 2 — Implemented

- No `default ->` arm was added (`ReasonFixtures:405-438` exhaustive); `LessonStore.java:815` is untouched (PLAN-V2-164 D4). No AAA comment, no sleep, no `Random`; EasyMock only; wire names the only literals; no assertion message outside 20-60 characters by the leaf's rough scan. Null rules hold: `reasonValue()` returns `Optional`, `reason` is `@Nullable` like `outcome`. The three-permutation `UNDECLARED_OUTCOME` fault is tested.
- **L2-2 (minor):** `PrimitiveRun` (`WorkflowEngine.java:1552`) is a record of three `@Nullable` components, so "exactly one of target or condition" is not enforced by type; `requireNonNull(facts)` repeats at `:529` and `:1225`; a sealed Routed and Raised pair is safer (PLAN-V2-25 item 73).
- **L2-3 (minor):** untested: a reason whose `conditionKind()` is invalid at run time (`ConditionRaised.raise` throws `IllegalArgumentException`, not an internal fault, `:1510-1513`), the `precondition_unmet` mapping (all raise tests use `footprint_violation`), the 32-character boundary of the grammar.
- **L2-4 (minor):** after a raise and a clear, `chain` goes through `enter()` (`:1327-1339`), which writes a second `entered` line before one `left`; `clearedConditionRunsAgain` (`:255-267`) does not look at the history; the primitive executes twice (an idempotency hazard for effectful primitives); the guide says only the `entered` line is written (`workflow-engine.adoc:212-214`).
- **L2-5 (minor):** `chainOrRaise` raises over the original status: the operator's `fills` and the act parameters are dropped (documented, tested); after the clear a composite job parks (`:462-463`) and a link state rests (`:464`) and neither re-runs: untested.
- **L2-6 (minor):** the ADR 026 amendment is justified (the outcome and reason types must be enums, so EasyMock cannot create them; `EngineFixtures.StepOutcome` is the precedent) and the baseline lines match the ADR list; the ADR says an enum "keeps no state" but `SwitchOutcome#reasonType` reads a `static volatile` switch (`ReasonFixtures.java:239,266-278,525`), safe only while tests are not parallel; the baseline grows where PLAN-V2-125 and 126 shrink it.
- **L2-7 (minor):** `ReasonConditionTest` (521 lines) and `ReasonFixtures` (608 lines) are over the 400-line budget the PR body names.
- **L2-9 (unchecked):** the two flaky timing tests under daemon load are named nowhere; the diff has no sleep, await or timeout and nothing under `pm-runtime`, so they are almost certainly not new.

### Layer 3 — Correct

- **L3-1 (major, latent):** `state-catalogue/01` `:234-243` (Server `retry`: "the interrupted check runs again", "Clearing continues the interrupted step") against the new second origin: for a reason-raised `footprint_violation` or `precondition_unmet` the engine re-executes the whole primitive after the clear (auto state) or merely rests (composite-job and link states); neither is "continues the interrupted step", and no remedy or clear mechanism is specified; the re-execution is recorded only in ADR 019 and the guide.
- **L3-2 (minor):** ADR workflow-engine/019 Consequences ("re-runs the primitive once the condition clears") is false for event-driven states.
- **L3-3 (minor):** ADR core-spi/011 (`:21`, `:33`) still says no verifier reads a reason; ADR 040 "refines" it but 011 is not pointed.
- **L3-4 (minor):** ADR core-spi/013 says `PlanHistoryLine` and the schema are unchanged although the PR changed both; its known open point is real: the registry admits a hyphen in an outcome wire name (`PrimitiveRegistry.java:57-59`) while `PlanHistoryLine.OUTCOME_PATTERN` (`:56`) refuses it, so a hyphenated outcome throws when its `left` line is built.
- **L3-5 (info):** wire and schema parity holds: schema, codec, record, ADR 080, `store-schemas/02:40` and `PlanHistorySchemaParityTest` changed together; a line without a reason is byte-identical; an older binary refuses a line with `reason` (accepted pre-1.0).
- **L3-6 (minor):** `PM-WATCH-DSL-44` (`workflow-dsl.adoc:609`) sits in the sub-workflow section although its anchors are 04-ast and 05-primitives.
- **L3-7 (info):** the PR body's statements (facts bound, no `left` line, the registry change, DSL-44, the advisories, 38 against 39, the missing backlink) match the code.
- **Effect on staged plans:** PLAN-V2-164 (19 leaves its overlap and sequencing; the `precondition_unmet` row now carries a "Second origin" sentence; next ADR numbers) and PLAN-V2-25 (`PrimitiveRun` exists; two exits; six `ConditionRaised.raise` sites; Decision 5 grammar contradicted; `CommandId` and `CorrelationId` already exist from #522) carry the corrections as items 74 and 73.

### Review State

Review gate: pre-merge consent and a head-bound `barrier-ask-override` (gap class `review-barrier-gap`) at HEAD `9daae326` over the optional reviewer Sourcery, which refused on a hard weekly quota; the required reviewer CodeRabbit participated at that head; `automatic-review` closed `done` with "1 comment(s) found, 1 reviewed, 1 refused (unified triage pending)". CI and SonarCloud green. The four count keys of the landing are `unknown`.

## Metrics and Anomalies

49.5 M tokens over 39 h 40 min wall time (6-finalize holds 68 % of 10.2 M tokens per the retrospective). `automatic-review` spent 4 rounds (3 plus one operator-granted) after CodeRabbit's free-tier and rate-limit refusals and halted once on `rate_window_await`; `functional-review` spent 3 rounds, `branch-cleanup` 2, `code-review` 1, `pre-push-quality-gate` failed once for pm-core and passed on re-run. An operator-authorized rebase onto main with two content conflicts is reported by the dispatcher and not recorded in the step records. The recreation of #530 as #531 was `automatic-review`'s loop-back to re-ask CodeRabbit (the answer to the open question about 19's recreated pull request).

## Routing and Merge Behavior

Merged through the merge queue (`merge_mechanism=merge_queue`); `cleanup_owed=false`. The four candidate lessons (`plan-marshall:automatic-review`, `pm-mcp-core:doc-review` (the `trace.py` check from a plan worktree, the same defect as the lesson of PLAN-V2-155), `plan-marshall:phase-3-outline` (declare the ADR 026 baseline files when a spec adds enum fixtures), `plan-marshall:phase-6-finalize`) were forwarded to the `lessons-routing` inbox of the plan-marshall repository and archived here.

## Reconciliation Actions

- Row PLAN-V2-19 transitioned to `shipped`; `pr` `#531` (the plan's #530 was closed), `landing`, `plan_marshall_plan_id` `plan-v2-19-engine-carries-reasons`.
- Residue placed, none new staged: the index, backlink, ADR, guide, watch-placement, clearing-rule and grammar statements into PLAN-V2-163 as item 70; the history and failure-path tests into PLAN-V2-161 as item 71; the stateful enum fixture and the two oversized test files into PLAN-V2-125 as item 72; the stale statements of PLAN-V2-25 and PLAN-V2-164 as items 73 and 74 of those specs.

## Follow-Ups

- Operator: the identity of the two flaky timing tests the dispatcher reported is unknown (the landing does not name them); a documentation-repository pull request is needed for the missing `PM-IMPL-5` backlink of DSL-44 (and of DSL-41).
- Lesson of this landing for the epic's own staging: a staged spec that adds enum test fixtures must list `doc/adr/026-…adoc` and the module's `hand-written-doubles.txt` in its affected files (lesson `-003`).
