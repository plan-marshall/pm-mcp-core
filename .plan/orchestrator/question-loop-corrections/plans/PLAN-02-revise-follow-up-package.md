# PLAN-02: Revise the follow-up package and the dependencies of question-continuation

epic: question-loop-corrections
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-02-revise-follow-up-package.md` and is queued as one row file, `queue/PLAN-02.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.

## Objective

The package `question-loop-follow-up` (`plan-marshall-mcp` #108, `doc/plans/question-loop-follow-up.adoc`) holds the
leftovers of the question-loop smoke run, but cannot run as written: its task 12 needs the stage `resumability`, which
`question-continuation` in this repository builds, while `question-continuation` waits for the values the follow-up
settles; it keeps no evidence outside a worktree that cleanup removes; it states the scale per group instead of per
fixture and host; it replaces "criteria fixed before the first measured run" with an owner-may-change clause; and
several tasks cannot fail. Meanwhile `question-continuation` counts as ready, because its only dependency,
`question-loop-verification`, is `done`. This plan revises the package and the dependency rows so that the follow-up is
runnable and binding and `question-continuation` waits for settled values. Documents only: one pull request in
`plan-marshall-mcp` and one here.

## Deliverables

1. **Durable evidence.** The follow-up package names where the run evidence (`runs.jsonl`, `invocations.jsonl`, stub
   logs, per-invocation archives, the answer audit) is kept so that no cleanup removes it — outside any plan worktree
   and pushed, for example a branch of the private repository `cuioss/plan-marshall-telemetry` beside the scenarios
   already kept there (the operator chooses the store at refine) — and a Done-when item that checks every published
   figure has its source in that store. Task 7's reference to `answer-audit.json` states that the smoke run's copy is
   lost and the new runs write their own.
2. **Scale and criteria.** The scale reads as the original package's: 20 runs per fixture and host, warm and fresh, with
   the number of fixtures per item stated. The sentence "The criteria are fixed before the first measured run and not
   changed by it" is restored; a criterion change is decided by the operator before the first run of the follow-up, on
   reasons not drawn from the follow-up's own runs, and recorded in the package — task 2's two criteria questions (R3
   `CALL_CUT`, the Q4 reference form) are put to the operator at refine in that form.
3. **Tasks that can fail.** Every task has a criterion that can be missed: task 1 states the tolerance within which a
   gated run reproduces a smoke group; task 3 drops "or states the difference" as a pass; task 9 names the candidate
   bound values and the runs per variant and the rule that picks the value; tasks 11 and 12 have no exit without a
   recorded operator decision; task 4 states its scale; task 6 names a default budget the operator confirms. Codex
   (task 5) stays in scope only on the operator's word at refine (it is not installed on the measuring machine and was not
   in the original package's scope); otherwise it is removed and named as not measured.
4. **No dependency cycle.** The follow-up settles the `headless.resumability` values from its own driver runs (task 12
   no longer waits for the stage `resumability`); the stage, once `question-continuation` builds it, repeats the
   measurement. The engine's parts of the follow-up (the profile schema values, the shipped profiles) move into a
   package of this repository (its file and index row in `doc/plans/`, name chosen at outline) that depends on
   `plan-marshall-mcp` / `question-loop-follow-up`; the follow-up package keeps only `plan-marshall-mcp` work and names
   that package. In this repository's index, `question-continuation` depends on that package (or directly on
   `question-loop-follow-up`), and its bullet *Measured first* names the follow-up as the source of the values.
5. **The `done` of a measurement package.** `plan-marshall-mcp` `doc/plans/README.adoc` defines `done` by the build
   checks only, so `question-loop-verification` reads `done` with its Done When unmet. The operator decides at refine
   between (a) a rule that a package whose Done When names results is `done` only when they are met, with the row of
   `question-loop-verification` corrected accordingly, and (b) keeping the definition and stating in the row that the
   follow-up carries the unmet part. The chosen form is applied.
6. **The OpenCode 2.x worker as now specified.** Core #206 (`3ed18f7b`) and `plan-marshall-documentation` #125
   (`a4c9934`) specify it: the profile member `headless.inline_settings` (the server's tools as direct tools, compaction
   off, the tool-result limit, request timeouts below the silence limit; values provisional until verified), the 2.x
   launch, version probe and `events` mapping, `PM-CLIENT-2`, `PM-WATCH-CLIENT-17`, the two OpenCode items of Roadmap
   Milestone 14, and the open questions `opencode-worker-login` and `opencode-event-source`. The follow-up package
   follows them:
   - *Task 3 keeps only the question loop's findings.* Whether the profile or the version pattern changes for what the
     specification now states (the supported range, `run --standalone` with the project from `PWD`, the version probe,
     the tool names) is Milestone 14's item that verifies and ships the 2.x profile; the package names that item as the
     owner and keeps the session-id pointer and the tool-catalog refusal of R2 warm/300.
   - *The configuration of a measured OpenCode run is stated.* Every OpenCode run uses the profile's inline settings, and
     the unset defaults are the matched control wherever a result may depend on a setting (`PM-WATCH-CLIENT-17`); the
     effect of each setting is checked before the first measured run, never its acceptance. The operator confirms at
     refine whether the control is run and for which items (OpenCode runs are cheap, the wall time of R1 and R2 is the
     cost).
   - *The tool-catalog refusal is investigated with code mode as a candidate cause* (HYPOTHESIS: the harness offers the
     server's tools behind its code tool by default, not as direct tools): the investigation compares direct tools with
     code mode and records the result either way.
   - *The per-worker data home waits on a decision.* A 2.x login is not in `auth.json`, so the setup of task 3's fourth
     bullet depends on the open question `opencode-worker-login`; the operator decides before the first run which of its
     options the measurement uses, or that OpenCode runs serially on the operator's data home as the smoke run did, and
     the package records the decision and any departure from the specified setup.
   - *Task 12 takes `inline_settings` as the form of a schema addition* (a member in the `required` list of the profile
     schema, `null` where a harness has none). The settled values go into the profile that ships the 2.x range
     `>=2.1.0 <3.0.0`; where Milestone 14 has not shipped it when task 12 is reached, they go into the evaluation
     reference only and the profile keeps `false` and `null` (the operator confirms).

**Done when**: the follow-up package names its evidence store and checks it, states 20 runs per fixture and host,
carries the fixed-criteria sentence, and has no task without a missable criterion; no package in either index depends,
directly or through another package, on one that depends on it; `question-continuation` no longer reads as ready while
the follow-up is open; the engine's part of the follow-up is a package of this repository with its index row; the
`done` decision is applied; the package states the configuration of its OpenCode runs, names Milestone 14's item as the
owner of the 2.x profile, and records the decision on the data home; the documentation link check reports
`BROKEN LINKS 0` against both repositories' `main`.

## Standing Constraints

- Change no criterion of the original package by this plan: a criterion change is the operator's decision, recorded in
  the package before the first run of the follow-up.
- Work packages are hand-maintained files with an index row each (`doc/plans/README.adoc` of each repository); the index
  is the only place that states status and dependencies. Follow the form of the sibling packages.
- `plan-marshall-mcp`'s local main checkout was at `6d9638f` on 2026-10-27, behind #107 and #108: work from `origin/main`
  in a worktree, never in a checkout another session may use.
- Two pull requests (`pm-mcp-core` with label `skip-bot-review`, and `plan-marshall-mcp`), neither merged without the
  operator's word. Commits end with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; no tool footer in the body.

## Claim Labels

- OBSERVED: the cycle — `doc/plans/question-continuation.adoc` (this repository, `origin/main` 2026-10-27) bullet
  *Measured first* says the values are "those `question-loop-verification` settled", its index row depends only on
  `plan-marshall-mcp` / `question-loop-verification` (`doc/plans/README.adoc` line 39), and `question-loop-follow-up.adoc`
  task 12 (line 121) says "Run the stage `resumability` of `pm-operator profile verify` and settle the shipped values
  from it", the stage being a deliverable of `question-continuation` (README line 37).
- OBSERVED: the scale wording — `question-loop-follow-up.adoc` line 13 "20 runs per group"; the original package's
  Method says "20 runs per fixture and host, warm and fresh".
- OBSERVED: the criteria clause — `question-loop-follow-up.adoc` line 13 "A criterion is changed only by the package
  owner and only with evidence"; the original package says "The criteria are fixed before the first measured run and not
  changed by it".
- OBSERVED: `answer-audit.json` is cited at `question-loop-follow-up.adoc` line 83 and was written under
  `target/verification-results/question-loop/` of the deleted worktree.
- HYPOTHESIS: tasks 1, 3, 4, 6, 9, 11 and 12 carry the open exits named in Deliverable 3 — confirm at
  `question-loop-follow-up.adoc` lines 26, 52, 60, 76, 99, 113 and 121 (verify-at-outline).
- OBSERVED: `codex` is not on the PATH of the measuring machine (`command -v codex` empty, 2026-10-27).
- OBSERVED: `plan-marshall-mcp` `doc/plans/README.adoc` line 137 defines `done` by the build checks only.
- OBSERVED: PLAN-73 of `pm-mcp-core-m2-m4` was launched and shipped as #201 (`a7a355aa`, "partial: library core")
  before this plan: its package file `doc/plans/question-continuation.adoc` is still open in the index, and its first
  bullet of *What* still says "Nothing of it exists". The index has no row for the OpenCode 2.x items of Roadmap
  Milestone 14 (`doc/plans/` holds no package named for the inline settings or the 2.x profile), and `doc/plans/` was not
  touched by #204 or #206 (2026-10-27).
- OBSERVED: the follow-up package at `3b93248` names OpenCode 2.x in task 3 (lines 42–51: supported range, `argv`, tool
  naming, version pattern, the R2 warm/300 refusal, the data home per worker) and in task 12 (lines 121–123), and says
  nothing of inline settings, code mode or compaction.
- Verify-first clause: before scoping, re-read both indexes and the package files at `origin/main` (another plan may
  have changed them), and read what #201 landed of `question-continuation` and which values it used. The change to the
  package file is the dependency, the bullet *Measured first*, and the first bullet of *What* (corrected to what #201
  landed, read from the merged code, not from this spec); its deliverables stay. If the code of #201 fixes a smoke-scale
  candidate value, report it in the inbox message as a finding for the remaining deliverables, not as a change here.

## Expected Surface

- OBSERVED: `doc/plans/question-continuation.adoc` — the bullet *Measured first*
- OBSERVED: `doc/plans/README.adoc` — the row of `question-continuation` and the row of the new package
- HYPOTHESIS: `doc/plans/` — one new package file for the engine's part of the follow-up, name chosen at outline (verify-at-outline)
- OBSERVED: `../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc` — in the sibling repository (not resolvable from this checkout)
- OBSERVED: `../plan-marshall-mcp/doc/plans/README.adoc` — the `done` definition and the two question-loop rows, in the sibling repository

## Dependencies and Sequencing

- Depends on: none (PLAN-01 may run beside it; their files are disjoint).
- Overlaps with: none within this epic. `doc/plans/README.adoc` is shared, one row per plan, with every plan of this
  repository (PLAN-42 and PLAN-73 of `pm-mcp-core-m2-m4` have shipped; the plan works from `origin/main` after them).
- Adjacent to: PLAN-73 of `pm-mcp-core-m2-m4` implemented the library core of `question-continuation` (#201); this plan
  changes only that package's dependency, its bullet *Measured first* and the statement of what exists, never its
  deliverables. The OpenCode 2.x items of Milestone 14 have no package yet: this plan names them as owners and creates
  none.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/question-loop-corrections/plans/PLAN-02-revise-follow-up-package.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
