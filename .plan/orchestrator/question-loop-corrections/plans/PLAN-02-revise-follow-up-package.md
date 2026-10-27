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

**Done when**: the follow-up package names its evidence store and checks it, states 20 runs per fixture and host,
carries the fixed-criteria sentence, and has no task without a missable criterion; no package in either index depends,
directly or through another package, on one that depends on it; `question-continuation` no longer reads as ready while
the follow-up is open; the engine's part of the follow-up is a package of this repository with its index row; the
`done` decision is applied; the documentation link check reports `BROKEN LINKS 0` against both repositories' `main`.

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
- Verify-first clause: before scoping, re-read both indexes and the package files at `origin/main` (another plan may
  have changed them), and check PLAN-73 of `pm-mcp-core-m2-m4` has not been launched; if it has, report it in the inbox
  message instead of changing its package file under it.

## Expected Surface

- OBSERVED: `doc/plans/question-continuation.adoc` — the bullet *Measured first*
- OBSERVED: `doc/plans/README.adoc` — the row of `question-continuation` and the row of the new package
- HYPOTHESIS: `doc/plans/` — one new package file for the engine's part of the follow-up, name chosen at outline (verify-at-outline)
- OBSERVED: `../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc` — in the sibling repository (not resolvable from this checkout)
- OBSERVED: `../plan-marshall-mcp/doc/plans/README.adoc` — the `done` definition and the two question-loop rows, in the sibling repository

## Dependencies and Sequencing

- Depends on: none (PLAN-01 may run beside it; their files are disjoint).
- Overlaps with: none within this epic. `doc/plans/README.adoc` is shared, one row per plan, with every plan of this
  repository (PLAN-42 of `pm-mcp-core-m2-m4` is running and resolves its conflicts there itself).
- Adjacent to: PLAN-73 of `pm-mcp-core-m2-m4` implements `question-continuation`; this plan changes only that package's
  dependency and its bullet *Measured first*, never its deliverables.

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
