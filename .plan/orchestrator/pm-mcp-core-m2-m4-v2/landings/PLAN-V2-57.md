# Landing Analysis: PLAN-V2-57 — the fixture default plan models the land and adopt lanes, and the engine runs it end to end

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #378 (merge `6000f951ef22e5b81f543a68ce0a6e8ee8289ca6`, state `merged` per the CI abstraction, squash through the merge queue, merge-group run 43113640628)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `plan-v2-57-default-plan-lanes-and-run-007.md` of the plan lifecycle (lane `plan-marshall`, harness claude, model
> claude-sonnet-5-5, effort medium, plugin 0.1.2106). The message is a lead. The three layers were read by one read-only
> leaf from git at `6000f951` (no build, no test run); the orchestrator re-read the points marked "re-read"; the provider
> figures were re-read from GitHub (`ci pr view`, `runbook_lane review-gate`) and the SonarCloud public API.
> The six `candidate-lesson` messages of the same sender concern `plan-marshall:*` components and were forwarded to the
> `lessons-routing` inbox of the plan-marshall checkout (see Routing).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 land and adopt routes in the fixture units | shipped-as-specified | `init.pmwf` exits `land`/`adopt` (lines 7-8) with terminals reached by `init.ready`; `plan.pmwf:27` routes `land -> phases.execute.land`, `:96-102` the new call state `phases.finalize.adopt`; `phase-4-plan.pmwf` exit `land`, `plan.land-exit` and `RouteOutcome.LAND` are gone; `LaneOutcome` gains `LAND`/`ADOPT` (`InvariantFixtures.java:542`). Every changed fixture is explained by a deliverable except the one below. The "invariants pass unchanged in meaning" clause is **not** borne out for INV-PLAN-1 (L3-1) |
| D2 proof of each lane's path | shipped-as-specified | `DefaultPlanUnitTest` `laneEntersItsPhases` (parameterised, `:151-158`) with the phase lists per lane, `landLaneSkipsThePlanningPhases` (`:171-180`), the three old assertions changed to the new shape; the matched control (`:183-194`) replaces `land -> phases.execute.land` (occurs once in `plan.pmwf`) with `land -> phases.plan` and asserts the walk then contains `phase-4-plan` while the passing walk does not: it can fail. It asserts the walk the land test rests on, not the failure of the land test itself, which is equivalent |
| D3 end-to-end run | shipped, **one clause of the Done-when not met, by spec fault** | `DefaultPlanRunTest` runs the four lanes and the three loop-backs; every loop-back asserts a value that can differ (rework `:129-134`, finalize re-entry `:145-150`, hand-back `:162-178`). The call stack reaches **three** frames, not five (`DefaultPlanRunTest.java:80-81`, re-read): see L1-1. The `returned` terminal is never run end to end (only a synthetic engine test) |
| D4 documents | shipped, with misstatements | `workflow-unit.adoc` section and ADR `workflow-unit/008`; ADR numbering is clean (`workflow-unit` 001-008, `workflow-engine` 001-015). `workflow-unit.adoc:271` says "the plan unit and the six phase sub-workflows are twelve units" (that is seven; predates the commit); `workflow-engine.adoc:257` counts depth five as "the plan unit, a phase, and three nested calls" against line 40's `MAX_DEPTH (4)` (see L1-2) |
| Production fix (operator decision 2026-11-09) | shipped | `WorkflowEngine.java:551-556` (re-read): `ended` keys the end phase on the outcome id (`ABORTED_OUTCOME` keeps `aborted`, everything else `completed`); one test per delivery terminal in `WorkflowEngineTest` (parameterised, `:181-209`); ADR `workflow-engine/015`. Two gaps in L3-2 |
| Not in the declared surface | explained | `pm-workflow/src/test/resources/plan/per-lane-rework.pmwf` (needed: it consumes the shared `test.lane` registry that gained two outcomes; its header at `:10-11` is stale), `WorkflowEngineTest`, `WorkflowEngine.java`, `workflow-engine.adoc`, ADR `workflow-engine/015` (all explained by the operator decision of 2026-11-09 recorded in the spec) |

Facts block: `deliverables_total=5`, `deliverables_done=5`, `functional-review` 5 met, `code-review` 0 blocking / 5 advisory, `sonar_open_new_issues=0` (see below).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: a production change in the engine whose consumers had to be traced (every reader of the ended phase), a graph invariant whose behaviour over a changed corpus depends on the dominator code, and a deliverable trace across fixtures, tests and two guides; read against the merge diff `54b81c98..6000f951`, no build.
- **Verdict:** layer 1 findings (one major, three info/minor), layer 2 findings (minor and info), layer 3 findings (one **major**, two minor). The layer-3 defect is named in the operator report of this turn.
- **Re-read by the orchestrator at the merge commit:** `DefaultPlanRunTest.java:76-84` (corpus depth 3), `execute.pmwf:61` and `remediation-qgate-findings.pmwf:64` (the deepest path), `WorkflowEngine.java:545-560`, `DefaultPlanPrimitives.java:93` and `:118`, `DefaultPlanRunTest.java:269`, `CompiledInvariants.java:915-955` and `PlanInvariants.java:42-48`, `plan.pmwf:20-30`, `workflow-engine.adoc:40,255-258`, `workflow-unit.adoc:271`. Not re-read: the matched-control walk (`DefaultPlanUnitTest:183-194`), the consumers of the ended phase (`StalledScopeDetector`, `PlanProcessStates`), the `PlanInstanceComposerTest` lists, the Traceability rows: recorded as the leaf's reading.

### Layer 1 — Specified

- **L1-1 (major, refutes the spec).** The Done-when says a call stack of depth five is reached and popped. The deepest path of the corpus is `execute` -> `remediation.qgate-findings` -> `git.commit_and_push` = three frames (`execute.pmwf:61`, `remediation-qgate-findings.pmwf:64`, re-read); D1 adds no nesting; `MAX_CALL_DEPTH` is 5. The spec contradicts its own corpus. The lane asserted three, the operator chose that at the outline question, and the deviation is in ADR 008 and the class Javadoc, but the Done-when text in the repository was never amended. Corpus claim: none (spec 57 is shipped, so no staged spec carries the claim; no verdict persisted).
- **L1-2 (minor).** Depth counting is inconsistent between guide and code: `workflow-engine.adoc:257` ("five frames = the plan unit, a phase, and three nested calls") and line 40 (`MAX_DEPTH (4)`) against `WorkflowStatus.MAX_CALL_DEPTH` 5; the engine counts the top-level unit as no frame, so `call_stack` holds a phase plus up to four nested calls. Predates the commit, beside the new paragraph, and feeds L1-1.
- **L1-3 (info).** The spec's D3 says the test "asserts nothing about the phase value of a status ending `handed_back` or `returned`"; the operator decision of 2026-11-09, added to the same spec, makes it `completed`, and `assertDelivered` asserts it. Code follows the decision; the spec text is internally contradictory.
- **Confirmed (info):** the claim labels at the parent (the land route passed the plan phase, `init.pmwf` had two exits, no state called `finalize` at `adopt`); the hypothesis that a test pins the removed `land` exit is refuted (only unit-id lists in `DefaultUnitLoaderTest:101` and `ProjectUnitLoaderTest:126`).

### Layer 2 — Implemented

- **L2-1 (minor).** D1's "compiled invariants pass over the changed corpus unchanged in meaning" does not hold for INV-PLAN-1 (L3-1): `CompiledInvariantsTest` is unchanged by the diff and no matched control was added for the clause that went inert.
- **L2-2 (minor).** `DefaultPlanPrimitives.Scripted` (`:118`) is a new hand-written `Primitive` that near-duplicates `EngineFixtures.Scripted` (`EngineFixtures.java:661`), against "no new hand-written fake" (re-read).
- **L2-3 (minor).** `DefaultPlanPrimitives.evaluate(String, Optional<String>)` (`:93`) takes an `Optional` parameter (re-read); `DefaultPlanRunTest.Run` (`:269`) has four components and no `@Builder` (re-read).
- **L2-4 (minor).** `doc/Traceability.adoc` (the PM-WF-16 rows) was not updated for the new tests or the engine change; the engine guide's Decisions list ends at ADR 014 while ADR 015 is referenced only in the body.
- **L2-5 (info).** The `returned` terminal is never run end to end, and the recorder observes only primitive runs and rests, so a phase entered and left with neither would go unrecorded.
- **Not shown by reading:** whether the other users of `test.lane` (`PlanInstanceComposerTest.java:801-804` `other_lane` lists) stay valid; that the changed tests pass.

### Layer 3 — Correct

- **L3-1 (major, by reading the graph code; no run).** INV-PLAN-1's dominator clause (`CompiledInvariants.requireDominatorsOf`, `:924-951`, called with the plan phase as candidates and the execute phase as targets at `PlanInvariants.java:42-48`, re-read) goes **inert** on the corpus. The reference is the default unit of the same id (`CompiledInvariants.java:208`), here the corpus plan unit; its land lane now reaches `execute.land-entry` straight from `phases.init` (`plan.pmwf:27`) with no plan-phase state, so no plan state dominates every path into execute and the clause requires nothing. A deep-lane route around `plan.derive`, `plan.qgate` and `plan.compose` into execute is therefore no longer refused by it. The only INV-PLAN-1 refusing case (`CompiledInvariantsTest.java:721`) is the remediation one and does not exercise the dominator clause, so no test fails. ADR 008 "Consequences" (line 51) says only that it "no longer covers the land entry", which understates it. The cause is the specification's own land lane ("calls no refine, outline or plan phase"): the same holds for the product's default units in `plan-marshall-mcp`. The remedy is a decision about the invariant (dominate per lane, or per entry), not a patch of the fixture.
- **L3-2 (minor, two gaps of the engine change).** (a) Nothing pins the abort terminals' declared outcome id to `aborted` (`WorkflowValidator.ABORT_TERMINALS`, `:86`, keys on state ids and is never compared with the terminal outcome id): `state plan.aborted terminal cancelled failure` would now end with phase `completed` where it ended `aborted` before. (b) `ended` is scope-agnostic: root terminals of class failure whose id is not `aborted` (`start.abandoned`, `start.refused`, project-onboarding `abandoned`; `state-catalogue/09:119`, `/15:121`) changed from `aborted` to `completed`; those units live in `plan-marshall-mcp`, and whether any persists the phase is not shown by reading (ADR 015, lines 39-41, names the assembly as unchecked). The readers of the ended phase that the leaf read (`StalledScopeDetector.java:63,310`, `PlanProcessStates.java:150`, `WorkflowEngine.java:795`) treat `completed` and `aborted` alike or classify by outcome class.

### Conformance to the Project's Own Rules

| Rule (`CLAUDE.md`) | Verdict | Evidence |
|--------------------|---------|----------|
| Absence is `Optional`, never `null` | conforms | no new production method returns `null`; whether `PrimitiveResult`'s reason is `@Nullable` for the test helper's `null` is not shown by reading |
| `Optional` is a return type only | **deviates** | `DefaultPlanPrimitives.java:93` |
| Four or more components carry `@Builder` | **deviates** | `DefaultPlanRunTest.Run`, `:269` |
| No new hand-written fake | **deviates** | `DefaultPlanPrimitives.Scripted`, `:118` |
| JUnit 5 only, `@DisplayName`, `@ParameterizedTest` for 3+ variants, no AAA comments | conforms | four lanes and three terminals are parameterised; the three loop-backs are plain `@Test` |
| Test data from `cui-test-generator`, a literal only as contract | conforms | class-level comments state that each datum is a contract; the scope id comes from `Generators.letterStrings` |
| Assertion messages of 20 to 60 characters | conforms | every added or changed assertion has 25 to 51; untouched assertions in `landLaneCallsLandWithinExecute` and `lanesAndPhaseIdentities` still have none (predate the commit) |
| No generic `Exception` in production code | conforms | the production diff adds no catch or throw |
| `package-info` with `@NullMarked` | not shown by reading | no new package |
| Every public type documented | conforms | the one visibility change keeps its Javadoc |
| Logging, build, dependency rules | conforms | no logger, dependency or POM change |

## Unverified leads

- The operator's choice of three frames lives in the run's outline question, outside the diff: the repository records only the deviation, not the choice.
- Whether any unit in `plan-marshall-mcp` persists the phase of a non-`aborted` failure terminal (L3-2 b).
- Whether the composer tests' `other_lane` lists stay unchanged (`PlanInstanceComposerTest.java:801-804`).
- That the changed tests pass: the lane's gates are its own run (`verify -Ppre-commit`, `verify`, `verify -Pcoverage`); not re-run.

## Metrics and Anomalies

- 18.47M tokens, 36144 s wall (about ten hours), harness claude, model `claude-sonnet-5-5`, effort medium; five deliverables, six tasks in one execute envelope.
- **Finalize carried 58 % of the dispatched tokens** (2.57M of 4.39M): one comment-only loop-back (a header comment reworded in `per-lane-rework.pmwf`, a lambda replaced by `Objects::nonNull` in `DefaultPlanRunTest`) re-fired functional review, code review, a whole-tree pre-push gate (about 57 minutes, whole-tree because the spec `.adoc` path resolved to no registered module) and `ci-verify` (about 29 minutes): about two hours of re-verification. Candidate lesson 006.
- Per-task artifact lists are cumulative (TASK-006 lists 15 files) because the task start SHA is the envelope's. Candidate lesson 005.
- `emit-landing` and `archive-plan` are absent from `steps` by construction (this message is emitted by the first and precedes the second).

## Routing and Merge Behavior

- Merged through the merge queue (squash) at the operator's word at the pre-merge gate (consent recorded against head `614e8e51`).
- Provider figures (re-read on the merged head): `ci pr view` shows merged; `runbook_lane review-gate --pr-number 378` gives `unresolved_review_threads=0`, `review_body_findings_total=4` and `review_body_findings_handled=0`, with `merge_allowed: false`, `reason: body_findings_undisposed` (coderabbit `additional-1`, `inline-1` to `inline-3`). The landing states `unknown` for all three, so there is no difference to record as a false claim; the lane says the unified triage resolved all eight pr-comment findings of the run (five accepted, three taken into account) and posted replies, and a plan-lifecycle run has no progress file for `review-gate` to read the dispositions from, so `handled=0` is **not re-verified**, not a difference.
- Sonar: `pullRequest=378&resolved=false` lists 0 (re-read); the landing's `sonar_open_new_issues=0` agrees, with its own caveat that the plan's `sonar-roundtrip` step recorded "not configured" on both firings and the figure is a direct public-API read (an earlier head had one `java:S1612` in `DefaultPlanRunTest`, fixed in `614e8e51`). The `branch=main` analysis in SonarCloud still shows revision `5e4d3513` (85 open), so the post-merge figure for `6000f951` is not yet readable.
- `inbox landing-check` returned `complete: false` (`missing_keys`: `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`); `surface_delta` is `unmeasured` (`declared_not_supplied_and_realized_not_supplied`). Recorded as an Open Defect.
- Six `candidate-lesson` messages (`-001` to `-006`) concerned `plan-marshall:phase-5-execute`, `phase-6-finalize`, `manage-architecture` and `automatic-review`: forwarded to the plan-marshall `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-009` to `-014` and archived here (standing rule of 2026-10-17).
- PR #374 (`docs: repoint the judgment-endpoint links`) is open; the links it will repair are pre-existing broken links of untouched files, as the runbook plans also saw.

## Reconciliation Actions

- Row PLAN-V2-57: `shipped`, `pr` `#378`, `landing` `landings/PLAN-V2-57.md`, `plan_marshall_plan_id` `plan-v2-57-default-plan-lanes-and-run`.
- `epic.md`: Open Defects for L3-1 (the inert dominator clause), L1-1 with L2-3 (the depth-five Done-when and the conformance deviations) and the landing completeness gap; the inbox message was archived.
- The `resume_anchor` and `queue-view.md` were regenerated after the stamps.

## Follow-Ups

- **Operator decision (L3-1):** what INV-PLAN-1's dominator clause should mean once the land lane skips the plan phase. Not fixed inline; a staged spec only on the operator's word.
- **Owners for the minor findings (L2-2, L2-3, L2-4, L1-2, the stale header of `per-lane-rework.pmwf`, L3-2 a):** PLAN-V2-125 (`test-conformance-core-and-workflow`) for the three test-class deviations; PLAN-V2-136 (declares `workflow-engine.adoc`) for the guide's depth and Decisions list; the abort-terminal pin and the scope question of L3-2 are the engine's owner's. Asked of the operator one at a time; not folded yet.
- PLAN-V2-57 frees one slot. The `rb-v2-58`, `rb-v2-131` and `rb-v2-124` landings are still due.
