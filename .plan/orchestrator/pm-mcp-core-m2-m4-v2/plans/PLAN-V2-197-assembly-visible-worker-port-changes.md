# PLAN-V2-197: The assembly-visible worker port changes: the replacement start after a rotation, the item worktree release, the closing reason of an item task, the task an answer names, and the minor points of the grading adapter

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) landing revision 25, section 9 (operator cut of 2026-11-25): items 48(b), 48(c) release, 48(d), 48(e) and the minor points, 55(b) task naming
lane: runbook
> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-197-assembly-visible-worker-port-changes.md`. SELF-SUFFICIENT together with the standing rules named below.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R10, R14, R16, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

PLAN-V2-162 was cut at its delivered boundary. What it left are changes to ports the assembly implements. The grading harness's replacement start after a rotation still sees the ended worker as non-terminal. `ItemWorktrees` has no release. An item task closed for a model reason is left ungraded where Model Work scores it 0. `WorkerCalls.answerDelivered` names no task. Several minor points of the adapter remain. This plan changes those ports and their tests. A companion pull request in plan-marshall-mcp follows if the assembly implements the ports by then.

## Deliverables

1. **48(b), test first, then the fix.** The harness takes the concrete `WorkerSupervisor`, and the start after `forceEnd` finds the ended generation non-terminal. `room()` and `WorkerSupervisor.admitted()` count it, and `started()` returns null. A contaminated item, or a later item with count 1 and differing worktrees, is then left ungraded.
   - Build the fixture first: a run that ends a worker and starts the next in one run. It needs a clock/pause seam that makes a generation terminal while the harness waits. Then bound the wait, or count only live generations.
   - Decide whether `contaminationBlocksTheSharedDirectory` still pins the right outcome.
   - Port signature changed: the constructor `WorkerGradingHarness(EffectiveRole, WorkerSupervisor, WorkerEnding, ItemTaskOffers, ItemWorktrees, Path)` and `QualificationService.harness(role, supervisor, ending, offers, worktrees, projectCheckout)` take a narrow generation-view and wait seam instead of the concrete supervisor (HYPOTHESIS, shape decided at outline).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **48(c) release.** `ItemWorktrees` is a `@FunctionalInterface` with `Optional<Path> checkout(URI, String)` and `static none()`. Add `void release(Path)` and call it when the item is done. `none()` and every lambda implementation change. Controls: a zero-tool role creates no worktree (already pinned at #567; keep it), a refused checkout releases nothing, and a throwing release does not lose the answer.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **48(d) the closing reason.** `ItemTaskOffers.offer(Task)` returns `Optional<Submitted>` and cannot say why a task closed. Model Work "An item without an answer" (`01-roles-and-configuration.adoc:357`) and PM-WATCH-DISP-77 say a model-reason close is scored 0 and counted as not agreeing, and an item that ends for a runtime reason (`role_unavailable`, `withdrawn`) or whose judge closed `lost` is ungraded. The port carries the closing reason. The engine side (`GradingHarness`, `RoleGrader`) scores 0 or leaves the item ungraded. Table test over the DISP-77 guard, with its matched control.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
4. **55(b) task naming.** `WorkerCalls.answerDelivered(String generationId, Instant at)` names no task. Add `String queueTaskId`, as `holdOpened(generationId, queueTaskId, at)` has. `SupervisorCalls.answerDelivered` checks the lifecycle and arms the acknowledgement deadline only for the task the hold keeps. Test: a stray, late or wrong-task answer arms nothing (reason asserted).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core
   - Persona: implementer
5. **48(e) minor points.**
   - `CORPUS_CONTEXT` maps to the project checkout.
   - `envelope()` ignores `input.choices` and `input.answer_schema`.
   - A warm run shares the checkout for `CURRENT` items.
   - `startFresh`, `startWarm`, `started` and `RoleGrader.judgeCheck` return `@Nullable`; convert to `Optional`.
   - `WarmServing` matches contamination by `String.contains` (a prefix id splits wrongly) and is needlessly public.
   - A judge item id near `ID_MAX_LENGTH` (256) throws `IllegalArgumentException` out of `WorkerJudgeScorer.score`.
   - Four new test classes use no generators.
   - The planted item's discrimination lives in the judge mock (`PlantedCnItemTest`).
   - The last warm worker is never ended: add a close hook on `GradingHarness` (pm-core, assembly-visible).
   - `GradingOutcome.JUDGE_UNCHECKED` has no reference left at main or at d5c8da5d, so it is retired.
   - The `QualificationSchemaParityTest` line was fixed by #492.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:java-lombok, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
6. **Documents and the companion.** **Read first (PLAN-V2-162 landing analysis, `landings/PLAN-V2-162.md`):** #567 already changed three assembly-visible signatures although the lane said it made none: `QualificationService.harness` takes a required `WorkerEnding` (48(a)), `SupervisorEnvironment` gained a required non-null `spacing` component, and `SupervisorCalls.detection`, `loss` and `forget` went from public to package-private. `git grep` over `plan-marshall-mcp` `origin/main` `fd7e252` (2026-11-25) finds no consumer of any of these classes, so no companion pull request is owed for them; re-grep at the outline and name the result in the landing message together with the companion this plan owes for its own port changes. Amend `runtime-worker.adoc` (ports section), ADR `core-role/007`, ADR `runtime-worker/012` and the Traceability lines. Add one ADR each in `pm-core/doc/adr/core-role/` and `pm-runtime/doc/adr/runtime-worker/`, numbered as the next free number of that directory at the head of main when the outline is written. If the assembly implements any of the four ports at the outline, open the companion pull request in plan-marshall-mcp (see Dependencies).
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:**
- Deliverables 1 to 6.
- Each new behaviour test fails at the head of main after #567 merged, with the sha recorded by the outline and a failing run id. At 3ba79c4f the harness has the pre-#567 shape, so no failing run there is claimed.
- Each outcome table (48(d), 55(b)) has its matched control.
- The existing suites stay green after the change, with the same test count per class.
- Build: `verify -Ppre-commit` (rewrites reviewed and committed), `verify` of `pm-runtime`, `pm-core`, `pm-workflow`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am` and `verify -Pintegration-tests -pl pm-core -am`. Where a run exceeds the host's bound, give the per-module runs with log ids.
- Sonar: the open-finding read at the head (the PR's open new-code issues plus security hotspots still to review, and the rules left open; `unknown` for a failed read, never `0`) is `0`, or each remaining key is selected for dismissal by the operator.

## Standing Constraints

- Base is main after PR #567 merged. Every claim that rests on branch `feature/v2-162-runtime-worker-and-job-residue` (d5c8da5d) is a HYPOTHESIS until re-read on main.
- Read every cited line at the head first and drop what a merge settled (name the commit).
- Work to the Done-when. Ask only the questions this spec marks ask-at-refine, once.
- Every carried item gets an outcome: adopted, split off to a NAMED plan, or dropped with its reason.
- The port changes are assembly-visible (R16 reading): state the changed signatures in the PR body.
- No behaviour change beyond the items. No model-facing content. Pre-1.0: no shim, and no old overload is kept.
- No dependency or plugin is added. No more than 100 files per PR.
- CLAUDE.md test rules apply: JUnit 5, no new hand-written fake, EasyMock doubles, generators from cui-test-generator, assertion messages of 20 to 60 characters, each new test fails on the code it guards.
- Sonar dismissal only on the operator's selection. The question tool names each finding key, the progress record writes `operator-selected: {issue-key}: {reason}`, and the key is transmitted through `sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. The lane never records a dismissal of its own. A dismissal it cannot transmit is a blocker.

## Claim Labels

- OBSERVED (main): `ItemWorktrees` is `@FunctionalInterface` with `checkout(URI, String)` and `static none()`. `ItemTaskOffers.offer(Task)` returns `Optional<Submitted>`. `WorkerCalls.answerDelivered(String generationId, Instant at)`. All read at 3ba79c4f.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ItemWorktrees.java: @FunctionalInterface, Optional<Path> checkout(URI, String), static none(); ItemTaskOffers.java:43 Optional<Submitted> offer(Task); WorkerCalls.java:97 void answerDelivered(String generationId, Instant at); holdOpened(generationId, queueTaskId, at) at :78. All hold at the head.
- OBSERVED: plan-marshall-mcp origin/main 02639c14 has no Java, doc or test reference to `ItemWorktrees`, `ItemTaskOffers`, `WorkerGradingHarness`, `QualificationService`, `WorkerCalls` or `WorkerSupervisor`. The coupling is latent today. ADR `core-role/007:58` says the assembly will supply them.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Needs plan-marshall-mcp origin/main 02639c14, a repository outside this checkout. (Only the ADR part is readable: pm-core/doc/adr/core-role/007-...adoc:58 "The assembly in plan-marshall-mcp supplies ItemTaskOffers, ItemWorktrees and the engine" - holds.)
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc:357` and PM-WATCH-DISP-77 (`doc/implementation-watch/model-work.adoc`) both require the closing reason.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: model-work/01-roles-and-configuration.adoc:357 (item-without-result) and implementation-watch/model-work.adoc:62-75 PM-WATCH-DISP-77 Guard "The grading run receives the closing reason of every item task, not only its answer".
- OBSERVED (drift): `JUDGE_UNCHECKED` is found nowhere at 3ba79c4f.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git grep JUDGE_UNCHECKED over the tracked tree outside .plan finds nothing (only lower-case judge_unchecked in spec/ADR/Javadoc text, not a GradingOutcome constant; GradingOutcome.Refused has four constants at :62-71).
- HYPOTHESIS: on the 162 branch `WorkerGradingHarness` takes `WorkerEnding` and `SupervisorCalls.answerDelivered` sits at `:239`. Confirm at `WorkerGradingHarness.java` and `SupervisorCalls.java` (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: WorkerGradingHarness.java:72 ctor takes (EffectiveRole, WorkerSupervisor, WorkerEnding, ItemTaskOffers, ItemWorktrees, Path); SupervisorCalls.java:239 public synchronized void answerDelivered(String generationId, Instant at). Both hold on main at the head.
- HYPOTHESIS: 48(b) timing. After `rotate()` the ended generation is non-terminal when `room()` runs. Confirm with the fixture of deliverable 1 and `SupervisionPass`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: By reading: WorkerEnding.forceEnd (:129-141) only cancels the job via environment.starter().cancel (:300-303); terminal state arrives later through SupervisionPass.recordEnd (:302-315). room() (WorkerGradingHarness:~190-197) and WorkerSupervisor.existing()/admitted() (:497-527) count non-terminal generations; started() returns null on empty outcomes. Not run.
- HYPOTHESIS: the engine contract of 48(d) needs a `GradingHarness` result type that carries "not agreeing". Confirm at `GradingHarness.java` and `RoleGrader.java`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: GradingHarness.java: Optional<GradedAnswer> answer(Offered) with GradedAnswer(answer,totalTokens,durationMs) carries no closing reason; RoleGrader.java:211-215 turns every empty answer into Grade.ungraded(), so "not agreeing" (score 0) is not expressible through the port today.
- Verify-first clause: re-run `git grep` over plan-marshall-mcp at the outline. A refutation (no consumer) makes the companion PR a documentation-only note.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Future-tense verify-first instruction about re-running git grep over plan-marshall-mcp, which is outside this checkout.

## Expected Surface

27 existing files plus about 3 new files (the new seam type and two ADRs).

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerGradingHarness.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ItemWorktrees.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ItemTaskOffers.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerCalls.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerJudgeScorer.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WarmServing.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/QualificationService.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerGradingHarnessTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerJudgeScorerTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WarmServingTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/QualificationServiceTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorCallsTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorScenarioTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/GradingHarness.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/model/RoleGrader.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/model/RoleGraderTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/model/GraderFixtures.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/model/PlantedCnItemTest.java`
- OBSERVED: `pm-runtime/doc/developer/runtime-worker.adoc`, `pm-core/doc/adr/core-role/007-the-grading-adapter-offers-through-ports.adoc`, `pm-runtime/doc/adr/runtime-worker/012-the-answers-acknowledgement-is-a-loss-deadline.adoc`, `doc/specification/model-work/01-roles-and-configuration.adoc`, `doc/implementation-watch/model-work.adoc`, `doc/plans/question-continuation.adoc`, `doc/Traceability.adoc`
- HYPOTHESIS: new ADR files in `pm-core/doc/adr/core-role/` and `pm-runtime/doc/adr/runtime-worker/`, and the new seam type under `pm-runtime/src/main/java/de/planmarshall/runtime/worker/`.

## Dependencies and Sequencing

- Depends on: PR #567 (PLAN-V2-162) merged to main. This plan starts after it.
- Companion: a pull request in plan-marshall-mcp, only if the assembly implements the ports by then (HYPOTHESIS, re-grep at the outline).
- Order of the two PRs: this repository's PR merges first. Its merge deploys the SNAPSHOT, which is the coupling. The assembly PR then moves to that SNAPSHOT and implements the new signatures. The assembly build is red against the new SNAPSHOT until its PR merges, so open the companion as a draft as soon as this PR is open.
- Overlaps with: PLAN-V2-150 (worker tests, `WorkerActivityPump`), PLAN-V2-141, and this series' S3 and S5 (`SupervisorTestFixture`, `SupervisorCalls`). The gate decides.
- Adjacent to: PLAN-V2-147 and 148 (the `egress` stage carrier, 49(d)) and PLAN-V2-165 (75(b)-(f)). Untouched.
- Size: about six deliverables. The rationale for one plan is that they share the ports and one companion PR. The outline may cut deliverable 5 into its own PR.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-197-assembly-visible-worker-port-changes.md
```

## Write-Boundary

The plan touches only its own repository source and tests, and its PR in plan-marshall-mcp. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
