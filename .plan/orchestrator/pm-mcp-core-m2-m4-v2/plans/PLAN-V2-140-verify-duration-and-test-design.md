# PLAN-V2-140: The full pre-commit verify takes 22 minutes: measure where the time goes, cut it in every module, and keep the test quality while removing duplicated tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: the operator's instruction of 2026-11-12 ("a full pre-commit verify takes locally around 22 minutes, that is way too long: confirm the times, spec a plan to analyze the current durations, analyze and fix them over all modules; the test quality must not deviate but improve; there should be no test duplication; eventually we must rework the test design; this is the next plan to be run"), and the durations the landings of this epic already record
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off (lane `plan-marshall` under the operator's standing emit format of 2026-11-05: a normal plan, several deliverables over every module).
> Lives at `plans/PLAN-V2-140-verify-duration-and-test-design.md` and is queued as one row file, `queue/PLAN-V2-140.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

**Queue position (operator, 2026-11-12): this is the next plan to be emitted** when a slot is free, ahead of every other staged plan. Four plans were running when it was staged (PLAN-V2-129, 132, 66, 65), so it waits for the first slot; the gate at that emit names its overlaps with the plans then running (the test classes it merges or removes).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: the plan adds no HTTP call and no HTTP test; R09, R13, R14, R15, R16: no stored format, protocol or class of `pm-workflow`/`pm-runtime` changes its behaviour; R12: no message is added. R07 binds the choice of the target time and of the parallel-execution design: both are named below as ask-at-refine, with a recommendation, because they change the file set.)

Plan parameters (the values the rules ask for):

- R06 — documentation: this is a repository-wide decision, so its ADR is `doc/adr/NNN-…adoc` (the next free number at `origin/main` is taken when the ADR is written, R11; `023` exists at the emit) and the build and test commands of each module README that the plan changes (`{module}/README.adoc`, additive); no package developer guide changes unless a test base class of a package is moved.
- R12 — none (no message).

**Read the skills first (R20).** `junit-core`, `cui-testing`, `java-core`, `java-maven` (`manage-maven-profiles`) and `java-null-safety` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

A full `verify -Ppre-commit` of this repository takes about 22 minutes on the operator's host, so it cannot be run whole inside the build daemon's own bound (1120 s) and every plan splits it into per-module runs. This plan confirms the figure by measuring it phase by phase and module by module, finds what the time is spent on, and removes the causes in every module — without lowering the quality of the tests: coverage per module does not fall, no test that is removed is without a retained test that asserts the same behaviour, and the tests that duplicate each other are merged. What the analysis shows about the structure of the test suite and what a reworked test design has to be is recorded, so the rework the operator expects ("eventually") starts from facts.

## Deliverables

Five deliverables. The order is binding: 1 and 2 decide what 3 and 4 may touch.

1. **The measured baseline: confirm or refute the 22 minutes.** Measure `verify -Ppre-commit`, plain `verify` and the coverage lane per module and per phase, with the same host conditions stated for every run, and record the result.
   - The measurement uses what the build already offers (separate invocations per phase: `compile`, `test-compile`, `test`, the quality-gate rewrites with tests skipped, packaging; the surefire and failsafe XML reports; the reactor summary) — no plugin, extension or dependency is added to read time.
   - The record states, per module (`pm-core`, `pm-workflow`, `pm-runtime`, and each module below `pm-providers/`): wall time per phase, the number of test classes and tests run, the sum of the test times from the reports, and the time of the slowest 15 classes. At least three runs; the number of other builds running on the host at the time (the live plans of the epic share it) is recorded for each run, and the run on an otherwise idle host is the reference.
   - The record also holds the quality baseline the later deliverables are measured against: `verify -Pcoverage` instruction and branch coverage per module, and the test and assertion count per module.
   - It states in one sentence whether the reported 22 minutes holds and which share of it is test execution.
   - Skills: pm-dev-java:junit-core, pm-dev-java:manage-maven-profiles, plan-marshall:build-maven (`.mvn/maven.config` and `.mvn/jvm.config` are unmapped by `spec-skills`; named here)
   - Persona: module-tester
2. **The analysis: every cost has a cause and a decision.** Rank what the baseline found by cost and name the cause of each item above the cut-off, so that no fix is chosen before its cause is known.
   - The causes the plan examines at least: a wait that is real time (a sleep, a poll loop, a timeout the test waits out), a process or a nested Maven build a test starts, a fixture built again for every test, the work the quality gate repeats on every module (compilation with annotation processing, the rewrite recipes, the licence and import checks, the coverage agent), upstream modules rebuilt by `-am`, the reactor's thread count (`.mvn/maven.config` carries `-T1`; the commit that set it is `0e0d128a`, which gives no reason) and the test engine's serial execution (no `junit-platform.properties` exists in the repository), and the host's contention with the plans that run beside.
   - The result is a ranked table: item, module, seconds, cause, decision (`fix in 3`, `fix in 4`, `keep, because …`, `parent POM or CI workflow: lead for the operator`). An item above the cut-off that is kept carries its reason.
   - The cut-off and the target time are ask-at-refine (see Carried Open Items).
   - Skills: pm-dev-java:junit-core, pm-dev-java:manage-maven-profiles, plan-marshall:ref-code-quality
   - Persona: module-tester
3. **The build-level fixes, in the repository's own configuration.** Apply the decisions of the table that are configuration: forking and parallelism of the test engine, the reactor's thread count, what a module's phases repeat, which tests run in which lane.
   - Every change is measured before it is kept, with the figure in the ADR; a change that does not shorten the reference run is reverted and recorded as tried.
   - A parallel setting is kept only after an audit of the shared state the tests have (static fields, the process-global working directory and system properties, temporary base directories, ports, the lock and process tests of `pm-runtime`) and after three consecutive green runs of the full module on the changed configuration; a test that cannot run in parallel is marked, not left to flake.
   - The parent POM (another repository) and the CI workflows (`cuioss/cuioss-organization`, `.github/project.yml`) are not changed by this plan: a finding there is a lead in the ADR and in the landing.
   - The decision is recorded as the repository ADR (context, the measured table, decision, alternatives tried, consequences).
   - Skills: pm-dev-java:manage-maven-profiles, pm-dev-java:junit-core, plan-marshall:build-maven, pm-dev-java:java-core
   - Persona: implementer
4. **The test-level fixes in every module: the slow classes, and the duplicated tests.** For every class the table sends here: shorten the test without weakening it, or merge it into the test that already asserts the same behaviour.
   - A real wait becomes a controllable condition (a latch, an event the test waits for, a clock the code under test already takes); a class that starts a process or a nested Maven build shares what it can share without sharing state between tests; a fixture built per test is built once where the test does not change it. No sleep is added; no hand-written fake is added (EasyMock is the double, `CLAUDE.md`).
   - Duplication is found by behaviour, not by name: two tests (or two classes) that assert the same observable with the same input classes. A parity test, a differential test against a ported copy, a contract test and a test that a specification or `doc/Traceability.adoc` names as the verification of a requirement are NOT duplicates of the test they resemble unless the reading shows the same observable, the same inputs and the same assertion.
   - Every removed or merged test has a row in the **retention table** (in the ADR or a table beside it): the removed test, the retained test, the assertions that carry the behaviour, and the evidence that the retained test would fail if the behaviour broke (the one-line break of the code under test, shown red in the worktree and not committed, recorded in the progress note). `doc/Traceability.adoc` and every `link:` to a removed test class stay valid (the link check of `CLAUDE.md`).
   - The quality does not fall and improves where the plan touches a test: coverage (instruction and branch) per module is not lower than the baseline of Deliverable 1; the classes the plan touches follow R20 (assertion messages of 20 to 60 characters, generators for test data, `@ParameterizedTest` for three or more variants, no AAA comments). A class the plan does not touch is not rewritten for conformance (that is PLAN-V2-125 and 126).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: module-tester
5. **The result and the design findings.** Re-measure on the final tree exactly as Deliverable 1 did, state the before and after table per module, and write down what the analysis found about the test design.
   - The findings name, with the evidence of the retention table and of the ranking: the layers the suite mixes (unit, component with real files and processes, nested-build), the fixtures and base classes that cost time or hide duplication, the kinds of tests that duplicate each other by construction, and the structure a reworked test design needs. The findings are the input of the rework; the rework itself is not part of this plan (see Carried Open Items for the one decision on it).
   - Where the module READMEs state a build or test command whose duration or lane changed, they are updated.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:junit-core
   - Persona: documenter

**Done-when (the plan is done when all hold at the head of its branch, each stated with its figure):**

- The record of Deliverable 1 exists with the three runs per module and the host conditions, and says whether the 22 minutes holds.
- The ranked table of Deliverable 2 exists, and every item above the cut-off has a decision.
- A full `verify -Ppre-commit` on an idle host takes no longer than the target time that the operator set at refine (the recommendation is 600 s), and finishes inside the build daemon's 1120 s bound without a split; the figure is the median of three runs.
- Coverage (instruction and branch) of each module of the coverage lane is at least its baseline; the number of tests may fall, the retention table accounts for every test that was removed, and no retained test has fewer assertions on the behaviour than the pair it replaced.
- Three consecutive green runs of `verify -Ppre-commit` per changed module on the final configuration show no failure that the plan's parallel or shared settings introduced.
- The open-finding read at the head of the pull request: the pull request's open new-code findings (issues plus the security hotspots still to review) are `0`, or every remaining finding's key is selected by the operator (see Standing Constraints); the rules left open are named, and a failed read is `unknown`, never `0`.

## Standing Constraints

- **Measure before fixing, and fix only what the table says.** A change whose gain is not shown by the reference run is reverted.
- **The test quality is a floor, not a trade.** No coverage loss per module, no weaker assertion, no removed test without a retention row, no test turned off, disabled or moved to a lane that does not run in the quality gate. Tests that the standing rules or the specification require as verification stay.
- **No duplication, and none created.** A merge keeps the stronger of two tests; where the two assert different inputs, the retained test takes both input sets (parameterised).
- **No dependency, plugin, extension, parent-POM change, CI-workflow change or deployment target is added** (`CLAUDE.md`: ask first). A measure that needs one is a lead for the operator.
- **A routed build that returns `indeterminate` or `queued` because the shared daemon is stale is run with `--execution-mode in_process`**, and the shared daemon is not restarted unasked. A full-reactor run that exceeds the daemon's bound is split per module and says so in the record.
- **The host is shared with the plans that run beside.** Every measured figure carries the host load; a figure taken under load is marked as such and is not the reference.
- **Gate rewrites in their own commit.** A file the pre-commit gate rewrites outside the plan's scope goes in its own commit; the pre-commit gate runs on every head the plan pushes.
- Where the project analyses its pull requests with Sonar (`resolve` reports `sonar: configured`): an
  open Sonar finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION — the
  question tool names each finding key, the progress record records the selection as
  `operator-selected: {issue-key}: {reason}`, and the chosen key is transmitted through the plan-less
  transition verb, `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key}
  --transition {falsepositive|wontfix}`. The lane never records a dismissal of its own, and
  `sonar-gate --dismissed-keys` is refused. A dismissal the run cannot transmit is a blocker, never a
  silent pass. The merge gate reads the open-finding count at the head — `0` confirmed, or every
  remaining finding's key selected by the operator — and an `undecidable` read blocks.

## Claim Labels

- OBSERVED: earlier landings of this epic record the length of the gate on this host: a quality gate of 1135 s, 1275 s and 673 s including the whole suite (PLAN-V2-131), 1045 s and a verify of 887 s (PLAN-V2-124), a whole-tree `verify -Pcoverage` that hit the daemon's 1120 s bound with 4725 tests passed and was split, `pm-core` coverage 663 s and `pm-runtime` 376 s (PLAN-V2-131), `verify -Ppre-commit` at 1120 s and a root-reactor `test` at 1120 s after a 461 s queue wait (PLAN-V2-58), a whole-tree verify of 1049 s twice (PLAN-V2-93, a plan-efficiency item) — read in `inbox/archive/rb-v2-131-build-log-bounds/…-001.md`, `inbox/archive/rb-v2-124-lock-manager-spec-and-os-lock-tests/…-001.md`, `inbox/archive/rb-v2-58-verifier-cycle-scale/…-001.md`, `inbox/archive/plan-v2-93-http-tests-annotations/…-002.md`. They corroborate the operator's 22 minutes; they do not decompose it.
- OBSERVED: the surefire reports in the primary checkout (written 2026-11-11 12:02, 715 of the 721 `*Test.java` classes) add up to about 200 s of test execution: `pm-core` 58 s (347 classes), `pm-workflow` 30 s (133), `pm-runtime` 97 s (203), the six provider modules 14 s together; the slowest classes are `ProbeJobTest` 17 s, `LspClientTest` 14 s, `SubprocessJobRunnerTest` 5 s. If these reports come from a full run, test execution is roughly a sixth of the 22 minutes and the rest is not test time.
- HYPOTHESIS: the larger share of the 22 minutes is the quality gate's work and the reactor: annotation-processing compilation under `failOnWarning`, the rewrite and format recipes, the coverage agent, packaging of the test JARs, and the `-T1` serial reactor with `-am` rebuilding — confirm or refute with the phase measurement of Deliverable 1 (the figures of the reports above exclude the fork start and the phases around the tests).
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/BuildGuardsIT.java` takes 180 s in the failsafe report and starts a nested `mvnw` per fixture project below `src/guard-controls` (read at `BuildGuardsIT.java:79-92`); the integration tests are skipped by default (`skipITs` is `true` in `pm-core/pom.xml:18` and `pm-runtime/pom.xml:21`) and run in the profile `integration-tests`, so this cost belongs to the integration lane, not to `verify -Ppre-commit`; the lane's own length is a part of the baseline.
- OBSERVED: no `junit-platform.properties` exists in the repository (`find` over the tree), `.mvn/maven.config` carries `-T1`, and the host has 16 CPUs and 30 GB of memory; 17 test classes use waits (`Thread.sleep`, `await`) of which `Thread.sleep` itself is used in none (grep of `src/test`) — the real-time waits are in the awaiting helpers and the process and LSP tests and are measured, not guessed.
- OBSERVED: `doc/Traceability.adoc` names test classes as the verification of requirements (for example `ProbeJobTest` at `:858` and `:967`), so a removal or merge must keep the traceability links valid.
- HYPOTHESIS: some tests duplicate each other (the same observable and inputs asserted in two classes: the ported tests beside the generated ones, the lifecycle tests repeated per store) — confirm or refute by reading, class by class, in Deliverable 4; a resemblance is not a duplicate (see Deliverable 4).
- HYPOTHESIS: the set of classes Deliverable 4 touches beyond the five named in the Expected Surface is decided by the ranking of Deliverable 2 and is not knowable from the spec — verify-at-outline against the baseline (the outline re-declares the surface in the first progress note and the landing reports the delta).
- Verify-first clause: before any configuration change, read why `-T1` was set (`git log` of `.mvn/maven.config`, the project-setup document, the memory-bound and the registry-access notes) and whether the build daemon, the lanes and the CI use the same file; a refutation of "the serial reactor is a default nobody chose" loops back to the scope of Deliverable 3.

## Expected Surface

The surface named here is what the spec can verify at HEAD; Deliverable 4's classes are chosen by the measurement and are re-declared at the outline (Claim Labels).

- OBSERVED: `pom.xml`, `pm-core/pom.xml`, `pm-workflow/pom.xml`, `pm-runtime/pom.xml` — the surefire, failsafe, coverage and profile configuration (`pm-core/pom.xml:109-125`, `pm-runtime/pom.xml:125-145`)
- OBSERVED: `pm-providers/pm-provider-ci/pom.xml`, `pm-providers/pm-provider-git/pom.xml`, `pm-providers/pm-provider-github/pom.xml`, `pm-providers/pm-provider-gitlab/pom.xml`, `pm-providers/pm-provider-http/pom.xml`, `pm-providers/pm-provider-sonar/pom.xml` — per-module test configuration, only if the decision table sends a change here
- OBSERVED: `.mvn/maven.config`, `.mvn/jvm.config` — the reactor and JVM settings (`-T1`)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/BuildGuardsIT.java`, `pm-core/src/test/java/de/planmarshall/core/service/JobRunnerTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProbeJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/lsp/LspClientTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/SubprocessJobRunnerTest.java`
- HYPOTHESIS: `pm-core/src/test/resources/junit-platform.properties`, `pm-runtime/src/test/resources/junit-platform.properties`, `pm-workflow/src/test/resources/junit-platform.properties` — the test engine's parallel and lifecycle settings, if Deliverable 3 chooses them (new files; verify-at-outline whether one root file or one per module is the design, R07)

## Dependencies and Sequencing

- Depends on: none. Position: **next to be emitted** (operator, 2026-11-12).
- Overlaps with: the test classes of the plans that run when it is emitted (PLAN-V2-129, 132, 66, 65 at the staging) only where Deliverable 4 merges or removes a class they edit — the gate of the emit names the files; PLAN-V2-125 and 126 (test conformance of `pm-core`, `pm-workflow`, `pm-runtime` and the providers) rewrite test classes that this plan may merge or delete: they are better emitted after this plan, which the orchestrator will say at their emit; PLAN-V2-114, 134, 136, 137 and 138 add tests to the same modules (`SubprocessJobRunnerTest` and `ProbeJobTest` among them).
- Adjacent to: the coding-skill conformance family (PLAN-V2-83 to 108): this plan does not rewrite a class for conformance, it only keeps R20 for the lines it touches; `doc/Traceability.adoc` (shared bookkeeping, R08/R19).

## Landing

This plan runs under the plan lifecycle (`/plan-marshall`). Its `request.md` `source_id` points at this spec, so `orchestrator inbox detect` classifies it as orchestrated and its phase 6 files one `landing` message in the epic inbox with the `landing-facts` block of `standards/landing-payload-spec.md`; the orchestrator reconciles the row from it and from the pull request. The plan writes nothing else under `.plan/orchestrator/`. The landing carries the before and after table per module, the retention table and the number of tests removed, merged and kept.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-140-verify-duration-and-test-design.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Ask-at-refine, one at a time, each with the orchestrator's recommendation (R07):

1. **The target time and the cut-off.** The operator named no figure ("way too long"). Recommendation: a full `verify -Ppre-commit` of at most 600 s on an idle host (median of three runs), inside the daemon's 1120 s bound with a margin of more than 40 %, and a cut-off of every class or phase above 5 s in the ranking (confidence about 60 %: the share of the 22 minutes that is test execution is unknown until Deliverable 1 reports it, and the figure may have to move with it).
2. **The parallel-execution design.** Whether the engine's parallel mode (one `junit-platform.properties` per module or one root setting) is admitted at all, or only per-class forking: it changes the file set (R07) and the risk (the lock and process tests). Recommendation: decide after Deliverable 2 from the table, never before; admit it only for modules whose audit is clean (confidence about 65%).
3. **The test-design rework.** The operator expects it ("eventually"). Recommendation: this plan delivers the findings (Deliverable 5) and the merges that duplication forces; the rework of the suite's structure is one further plan staged from those findings, not a part of this one (confidence about 70%: the rework changes the layering and the base classes of every module and does not fit the quality floor of a speed plan). The orchestrator stages that plan when the findings exist, or earlier if the operator asks.
