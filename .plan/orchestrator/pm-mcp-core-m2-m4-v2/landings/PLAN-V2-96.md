# Landing Analysis: PLAN-V2-96 — The grading adapter that runs role qualification

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #486 (`70c21e5a`, merged through the merge queue 2026-11-18, runbook lane, opencode, `rb-v2-96-role-qualification-adapter`)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message `rb-v2-96-role-qualification-adapter-002.md`, which supersedes `-001`, archived on 2026-11-17 as a Watch) after verifying claims against git, the PR and one read-only three-layer leaf. A pasted claim is a lead, never a fact.

## Deliverable Fidelity vs Spec

`deliverables_done=4` of 5. The adapter is **dead code in this repository until the assembly wires it** (`QualificationService` takes an assembly-supplied `GradingEngine`; nothing here constructs the adapter), so every layer-3 defect below is latent.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 verify-first and outline | shipped-as-specified | the PR body lists what existed and what was missing; one hypothesis refuted (the corpus resources hold schema files only) |
| D2 the adapter | shipped-modified | `WorkerGradingHarness`, `WorkerJudgeScorer`, `QualificationService`, ports `ItemTaskOffers`, `ItemWorktrees` in `de.planmarshall.runtime.worker`; the Done-when "`git grep RoleGrader` finds a production caller" is **not met and cannot be met here**: `pm-runtime` may depend on `pm-core` and `pm-api` alone (CLAUDE.md, Dependency Rules; ADR `core-role/007`), so the caller is the assembly; **the Done-when is the orchestrator's authoring defect** (recorded in the Watch of 2026-11-17 before the merge) |
| D3 warm worker and contamination | shipped-modified | one warm worker per directory and contamination group (`WarmServing`); the spec's "no item output reaches the next item" conflicts with the warm form of Model Work Grading (layer 1) |
| D4 `roles qualify` runtime side and the planted `CN-<n>` item | shipped-modified | a pass-through service; the planted item exists as test code only (the repository ships no corpus tree), its discrimination lives in the judge mock (`PlantedCnItemTest` ~`:114`) |
| D5 the package `qualification-evidence` | shipped-as-specified | the codec writes and reads `thin` and `judge_check`; `QualificationSchemaParityTest` validates the written documents; the role grading records a failed check instead of refusing `judge_unchecked`; ADR `core-role/007`; the package file is deleted and its row closed |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`), a leaf of its own for this landing; the orchestrator re-read the points marked "re-read" at `70c21e5a`.
- **Verdict:** layer 1 findings (2); layer 2 findings; **layer 3 findings: five major (all latent, the adapter is unwired)**.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | Done-when `git grep RoleGrader` finds a production caller | checked | refutes | unmeetable here (above); orchestrator-confirmed before the merge |
| L1-2 | D3 "no item output reaches the next item" is consistent with Model Work Grading | checked | refutes | the warm form keeps the worker across items; the spec is also silent on a contaminated item whose worker cannot start |
| L1-3 | the planted `CN-<n>` item has a place in `core.role.corpus` | checked | refutes | that package holds three schema files only; the item can exist as test code only (the lane refuted the hypothesis) |

**Layer 1 verdict:** findings — 3.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D2, D3 | the adapter classes | `WorkerGradingHarnessTest` (14), `WorkerJudgeScorerTest` (5) | the tests mock the task protocol at the port, pin the "ungraded" outcomes as intended (`contaminationBlocksTheSharedDirectory`, `WorkerGradingHarnessTest` ~`:155`) and bypass the supervisor's own end path (L2-1) | checked |
| D4 | `QualificationService`, planted item | `PlantedCnItemTest` (2) | discrimination lives in the judge mock | checked |
| D5 | codec, parity tests, ADR | `QualificationRecordCodecTest.thinMark`, `failedJudgeCheck`, `judgeCheckAbsent`, `unreadable`; `RoleGraderTest.judgeCheckAbsent`, `judgeUnchecked` | none | checked |

The lane's "unexplained" `pm-workflow/doc/developer/workflow-model.adoc` is **refuted as unexplained**: D5 explains it. The only files no deliverable names are the `@Builder(toBuilder = true)` widening on `EffectiveRole` and `RoleArtifact` (serves `roleIn`) and the constructor ripple in test fixtures.

**Layer 2 verdict:** findings — 1 (L2-1: the tests cannot see L3-1).

### Layer 3 — Correct

Re-read by the orchestrator at `70c21e5a`: L3-1 (`QualificationService.java:66-67` builds a second `WorkerEnding`), L3-3 (`ItemWorktrees` has `checkout(...)` and no release), L3-4 (`ItemTaskOffers.offer` returns empty "when the task closed without one, which leaves the item ungraded"). L3-2 is **not shown by reading** (timing).

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `QualificationService.java:66` (re-read) | any warm rotation | the rotated worker leaves through a `WorkerEnding` that is not the supervisor's own end path (PM-WORK-3 "one end path"): the pass sees an unrequested exit and ends the generation LOST instead of RECYCLE, and the harness-local request map is never emptied (one entry per rotation) | major |
| L3-2 | `WorkerGradingHarness.java:128`, `:152-159`, `:116`, `WorkerJudgeScorer.java:80` | a contaminated item in the project checkout, or a later item with count 1 and differing worktrees | the replacement start is attempted right after `forceEnd` while the ended worker is still non-terminal in the supervisor: `room()` and `WorkerSupervisor.admitted()` count non-terminal generations, so `started()` returns null and the item is ungraded; a record with ungraded > 0 qualifies no form, so a corpus with a contamination note makes the role unqualifiable | major, **not shown by reading** (timing) |
| L3-3 | `ItemWorktrees.java:35` (re-read), harness `:109-113`, `RoleGrader.java:359-366` | every origin-commit item and every zero-tool role | no release exists and nothing removes a worktree: one per item × form × run; created also for zero-tool roles (the engine grants `ITEM_COMMIT` to any project-repository item) whose worker runs in an empty job-private directory: wasted worktrees, a zero-tool item ungraded when creation fails, and a record claiming `item_commit` for a run in an empty directory | major |
| L3-4 | `ItemTaskOffers.java:43` (re-read), harness `:101` | a model-reason end (refusal, an answer that does not validate, `infeasible`, `asked`, `input_too_large`) | the port answers empty for every close without a submit and the harness maps it to an ungraded item; Model Work "An item without an answer" says a model-reason end scores 0 and is never left out; the `GradingOutcome` port Javadoc inherits the contract | major |
| L3-5 | `WorkerGradingHarnessTest.java:302` | — | the tests build their own `WorkerEnding` and assert only that `starter.cancel` was reached, so L3-1 is invisible to them | major (a test that cannot fail on the defect) |
| L3-6..14 | harness `:109` (`CORPUS_CONTEXT` maps to the project checkout, latent), `:236` (`envelope()` offers one text `answer` submit and ignores `input.choices` and `input.answer_schema`), `:121` (warm reuse needs one path per commit), `startFresh`/`startWarm`/`started` and `RoleGrader.judgeCheck` return `@Nullable` (the Optional rule), `WarmServing` matches by `String.contains` and is needlessly public, `WorkerJudgeScorer.java:121` throws `IllegalArgumentException` out of `score()` for an item id near 256 characters, no generators in four new test classes, `GradingOutcome.JUDGE_UNCHECKED` dead, `QualificationSchemaParityTest.java:104` merged-hunk formatting | | | minor |

The last warm worker of a run is never ended by the harness (`GradingHarness` has no close hook); only idle recycling releases it (info).

**Layer 3 not shown by reading:** L3-2; assertion-message lengths (the per-module scanner decides); Sonar, the build and the pull-request body.

**Layer 3 verdict:** findings — 5 major, 9 minor. **Reported to the operator in the same turn; none is fixed inline.** All are latent: nothing in this repository constructs the adapter.

### Conformance to the Project's Own Rules

Thirteen rows: 7 conform, 5 deviate (the `@Nullable` returns against the Optional rule, `public WarmServing`, missing generators in four test classes, the second `WorkerEnding`, the model-reason score), 1 not shown by reading.

## Metrics and Anomalies

- Tokens: `unknown` (harness opencode); `plugin_version` `unknown`.
- Anomalies: the **first merge resolution was wrong** (it restored HEAD's whole `doc/Traceability.adoc`, dropping main's cleanly merged blocks; the second merge surfaced them; the file was rebuilt from `origin/main` with this plan's rows reapplied); the PR was `mergeable: conflicting` for hours because the lane's `verify` runs were local only (no `pull_request` workflow starts for a conflicting PR: the Sonar "undecidable" of `-001`, diagnosed by the orchestrator's Watch of 2026-11-17 and fixed by the merge of `main`); the lane named its own deviations: the first D5 edits written into the main checkout and moved, one `commit -am`. Three `verify -Ppre-commit` bound expiries (continued in the split form), one `LspClientTest` environment flake.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `370b5558` (5 findings) and `c9833175` (5 inline and 1 additional); Sourcery refused (hard quota, 5 days 15 hours). `review-gate --pr-number 486`: unresolved threads 0 (matches), body findings total 11 (matches the landing); `review_body_findings_handled` 11 **not re-verified** (the lane's progress file holds 5 `finding:` lines, which fits the first review only; not evidence of a false claim).
- CI/merge: through the merge queue as `70c21e5a`; main fast-forwarded; branch and worktree cleaned.
- Sonar: `sonar_open_new_issues=0`, gate ok, 92.0 % new-code coverage on the lane's read at the merged head; first analysis 5 issues, then 1, then 0 (not re-read here).
- Parallelization: ran beside PLAN-V2-59, 82 and 145; the 5-file overlap with PLAN-V2-145 the operator accepted produced no conflict (145 merged `origin/main` after #486 and met the `QualificationSchemaParityTest.java:104` rewrite, reverted in its worktree).

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#486`
- [x] row `landing` stamped `landings/PLAN-V2-96.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-96-role-qualification-adapter`
- [x] Watch of 2026-11-17 (PR #486 at the merge gate) retired; new Watch for the open residue; PLAN-V2-114 item 48
- [x] resume anchor, view regenerated

## Follow-Ups

- L3-1 to L3-14 and L2-1: PLAN-V2-114 item 48 (the residue carrier); the operator may prefer a plan of its own before the assembly wires the adapter, because L3-1 to L3-4 are defects of the first production use.
- The production caller of `RoleGrader` (D2's Done-when): owed to `plan-marshall-mcp` (the assembly).
- Open self-review items 1, 6, 8, 9, 11, 13, 14, 15 of the lane: folded into item 48 where they match the findings above.
