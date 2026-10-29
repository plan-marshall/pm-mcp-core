# PLAN-03: Run the question-loop re-measurement at the specified scale

epic: question-loop-corrections
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-03-run-follow-up-measurement.md` and is queued as one row file, `queue/PLAN-03.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.

## Objective

Implement the work package `question-loop-follow-up` of `plan-marshall-mcp` (`doc/plans/question-loop-follow-up.adoc`)
in the form PLAN-02 leaves it: extract the durable parts of the experiment from the branch
`verification/question-loop-experiment`, re-measure Q1 to Q7 and R1 to R4 at the revised scale of the Standing Constraint
"Scale revision" (not the 20 runs per fixture and host the package states) with the criteria of the original package
unchanged, keep every run's evidence in the store the package names, and record the
settled values (`headless.resumability` per shipped profile, the `questions` values of the shipped roles, the question
bound) with their evidence and name them for `pm-mcp-core` / `client-profile-measured-values`, the package of this
repository that carries them into the specifications and the profiles, so that `question-continuation` implements
measured values. The plan runs in `plan-marshall-mcp`; its results land there and in `plan-marshall-documentation`, and
it changes no specification of this repository itself.

## Deliverables

The tasks of `question-loop-follow-up.adoc` as PLAN-02 leaves them; this spec does not restate them, because the package
file is their only source. In outline:

1. The experiment's durable parts as maintained code, from the branch `verification/question-loop-experiment`.
2. The operator decisions the package records before the first run (criteria questions, budget, Codex, R2 at 24 h).
3. The re-measurement of every item at the revised scale, with its evidence in the named store and the client version
   of every run.
4. The results in the evaluation reference and the settled values recorded with their evidence in the package file and
   named for `client-profile-measured-values`, the package of this repository PLAN-02 created; that package, not this
   plan, writes them into `doc/specification/` and the profiles.

**Done when**: the package's own Done When is met item by item, read at the revised scale (its "specified scale" and
"20 runs per fixture and host" read as the Standing Constraint "Scale revision" states, with the package amended to say
so), and every published figure has its source in the evidence store.

## Standing Constraints

- Launched only on the operator's word, after PLAN-02 and PLAN-01 have landed and the operator accepted the run budget
  (task 6 of the package names about 55 USD and about 240 h as the default, an estimate from the smoke figures; the
  operator has asked for the smaller scale below, and task 6 recomputes the budget for it, which the operator then
  confirms or replaces).
- Antigravity availability (operator, 2026-10-28 18:10 CET: "available in 2 d 13 h"): Gemini / Antigravity is not usable
  before about **2026-10-31 07:10 CET**, a time computed from that statement, so accurate to the operator's rounding, not
  to the minute. No Antigravity run (task 4, and the Antigravity groups of tasks 8 to 10) starts before then; the plan
  runs the Claude Code and OpenCode groups first (the cheap hosts first order of task 6 already puts them first) and
  starts the Antigravity groups at or after that time. The plan is not closed while an Antigravity group is neither run
  nor carries a recorded reason, so it may finish its other work and wait. The run plan of task 6 names the start time
  of the Antigravity groups, and records the time the operator gives if the date moves.
- Scale revision (operator, 2026-10-28; reason: the harnesses change nearly daily, so a value measured over days is
  stale on arrival; the reason is not drawn from any run of the follow-up, so it satisfies the package's rule on changes
  before the first run). As its first act the plan amends the package (tasks 4, 6, 8, 9, 10, 11 and Done When) and the
  Evaluation Reference (its definition of the "specified scale" and the rows that say "not at the specified scale") to
  this scale, and records the decision in the package; no run starts before that record exists.
  - The measurement is decision-driven, not scale-driven: it runs only as much as the values it settles need
    (`headless.resumability`, the `questions` values of the shipped roles, the question bound). The base is 3 runs per
    fixture, host and form, the scale of the smoke run, with the driver defects fixed, so that the mechanisms are
    confirmed and every figure has durable evidence.
  - *Criteria.* The rate thresholds of the original package (at least 95 % for Q1, Q2's asks, Q4, R1 and R2; at least 90 %
    for the second ask of Q5) are testable only at about 20 runs. They are replaced, before the first run, by "every run
    of the group passes". A group whose runs disagree (some pass, some fail) is extended to 10 runs and reported with its
    rate; a group whose runs all agree is not extended. A value is shipped only for a duration, bound or rule at which
    every run passed. The clauses that were already "every run" or "no run" (Q2's no-host-tool and no-guess clauses, Q3,
    Q5's bound clause, Q6, Q7, R3) and R4's missing criterion are unchanged.
  - *Q5, the question bound.* Sequential: run the shipped `3` first; if no run reached the bound, `3` stays and the
    candidates `4` and `5` are not run; only where a run reached it, run `4`, then `5`, by the package's rule.
  - *R1 and R2, hold and resume.* Longest first: run the cap (3,600 s hold; a resume after 1 hour) per host; where every
    run passes, that duration is the host's value and the shorter durations are not run; where one fails, descend through
    30, 15 and 5 minutes until one passes. R2 stays at 1 hour at most (not 24 h).
  - The Sonnet repeats of task 11 stay by the operator's recorded decision per group.
  - A verdict is stated with the number of runs behind it and the client version, never as "passed at the specified
    scale"; a rule of the form "no run reached the bound" is stated with the pooled count it rests on.
  - The client version (the version each run's output names, or the version probe where it names none) and the model are
    recorded for every run and published with every value, so that a settled value carries the version range it was
    measured on. The driver and its gate are kept so that the measurement can be repeated per version; the plan does not
    ship a scheduled repeat.
- The evidence store is the branch `question-loop-follow-up` of `cuioss/plan-marshall-telemetry`; it does not exist yet,
  and this plan creates it and pushes to it before the first measured run.
- The criteria are fixed before the first measured run and not changed by it.
- OpenCode runs use the configuration the package names (the inline settings of the profile, with the unset defaults as
  control only where the package says so), and the effect of each setting is checked before the first measured run, never
  its acceptance (`PM-WATCH-CLIENT-17`).
- The per-worker data home of an OpenCode 2.x worker follows the operator's decision on the open question
  `opencode-worker-login`, recorded in the package; without one, OpenCode runs serially on the operator's data home and
  every result says so.
- Evidence is never kept only in a worktree: before the plan's worktree is removed, the evidence store holds every run.
- Every figure comes from the stub's event log, the relay's host-side log and the worker's output, never from what a
  model reports about itself.
- Commits end with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; no tool footer; no pull request merged without
  the operator's word.

## Claim Labels

- OBSERVED: the package file exists at `plan-marshall-mcp` `origin/main` (`a3f3a28`, #108) as
  `doc/plans/question-loop-follow-up.adoc` with 12 tasks, and the experiment code is on the remote branch
  `verification/question-loop-experiment` at `488ce47` (14 files under
  `pm-e2e/src/test/java/de/planmarshall/e2e/experiment/question/`). After PLAN-02 (`plan-marshall-mcp` #110, `da9d6d8`)
  the package file has 13 tasks, task 13 recording the results and closing.
  - verdict: contradicted | checked_at: 6010dbc74bada62d7b1f5a046e6b60a325a8a4e6 | by: question-loop-corrections/analyze | rescoped: yes | evidence: plan-marshall-mcp origin/main da9d6d8 (#110) holds the package file with 13 tasks, not 12; the claim above was rewritten to say so
- HYPOTHESIS: the Expected Surface below is the package's `Where` as of `a3f3a28`; PLAN-02 may move the engine's part
  into a package here — confirm at the package file after PLAN-02 landed (verify-at-outline). Corroborated and
  rescoped 2026-10-28: PLAN-02 landed (`pm-mcp-core` #212, `plan-marshall-mcp` #110) and moved the engine's part into
  `client-profile-measured-values`; the two specification paths this spec declared were removed.
  - verdict: corroborated | checked_at: 6010dbc74bada62d7b1f5a046e6b60a325a8a4e6 | by: question-loop-corrections/analyze | rescoped: n/a | evidence: PLAN-02 landed (core #212, mcp #110) and moved the engine's part into doc/plans/client-profile-measured-values.adoc; the two specification paths were removed from Expected Surface
- OBSERVED: the OpenCode 2.x profile and its inline settings are specified (core #206, `plan-marshall-documentation`
  #125) with values provisional until verified, and Roadmap Milestone 14 holds the items that verify and ship them; no
  package of `doc/plans/` carries those items (2026-10-27). The settled `headless.resumability` of OpenCode therefore
  lands in the evaluation reference and, once the profile that ships `>=2.1.0 <3.0.0` is on `main`, in that profile.
- Verify-first clause: before scoping, read the package file and both indexes at `origin/main` of `plan-marshall-mcp`
  and this repository; if PLAN-02 has not landed, stop and report in the inbox message.

## Expected Surface

- HYPOTHESIS: `../plan-marshall-mcp/pm-e2e/src/test/java/de/planmarshall/e2e/` — the extracted experiment code, in the sibling repository (verify-at-outline)
- HYPOTHESIS: `../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc` — the package file, closed when done
- HYPOTHESIS: `../plan-marshall-documentation/doc/specification/evaluation.adoc` — the result rows

## Dependencies and Sequencing

- Depends on: PLAN-02 (landed 2026-10-28; the package it implements), PLAN-01 (landed 2026-10-28: `pm-mcp-core` #213,
  `plan-marshall-documentation` #127; the corrected evaluation rows it extends). The Limits of `evaluation.adoc` still lack
  the statement that the OpenCode runs had no inline settings; this plan states it with the figures it publishes if no
  earlier pull request has.
- Overlaps with: PLAN-01 on `evaluation.adoc` — now sequenced, PLAN-01 is on `main`. The shared Client Profiles 01 and Model Work 01 paths
  left this spec with the rescope above.
- Hands over to: `pm-mcp-core` / `client-profile-measured-values`, which carries the settled values into the
  specifications and the profile schema, and to `pm-mcp-clients` (`pm-api`), where the shipped profile documents live and
  which has no work package for the owed change.
- Also waits for: the operator's decision on `opencode-worker-login`, which PLAN-02 records in the package, or the
  recorded choice to run OpenCode serially on the operator's data home.
- Adjacent to: PLAN-73 of `pm-mcp-core-m2-m4` shipped the library core of `question-continuation` (#201); the rest of
  that package waits for this plan's values.

## Hand-Off Command

Run in the `plan-marshall-mcp` checkout (the package is that repository's):

```text
/plan-marshall task="implement .plan/orchestrator/question-loop-corrections/plans/PLAN-03-run-follow-up-measurement.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
