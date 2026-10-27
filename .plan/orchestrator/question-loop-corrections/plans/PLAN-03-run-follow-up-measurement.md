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
`verification/question-loop-experiment`, re-measure Q1 to Q7 and R1 to R4 at 20 runs per fixture and host with the
criteria of the original package unchanged, keep every run's evidence in the store the package names, and write the
settled values (`headless.resumability` per shipped profile, the `questions` values of the shipped roles, the question
bound) into their owning specifications, so that `question-continuation` implements measured values. The plan runs in
`plan-marshall-mcp`; its results land there, in `plan-marshall-documentation` and in this repository.

## Deliverables

The tasks of `question-loop-follow-up.adoc` as PLAN-02 leaves them; this spec does not restate them, because the package
file is their only source. In outline:

1. The experiment's durable parts as maintained code, from the branch `verification/question-loop-experiment`.
2. The operator decisions the package records before the first run (criteria questions, budget, Codex, R2 at 24 h).
3. The re-measurement of every item at the specified scale, with its evidence in the named store.
4. The results in the evaluation reference and the settled values in their owning specifications; the engine's part in
   the package of this repository PLAN-02 creates.

**Done when**: the package's own Done When is met item by item, and every published figure has its source in the
evidence store.

## Standing Constraints

- Launched only on the operator's word, after PLAN-02 and PLAN-01 have landed and the operator accepted the run budget
  the package states.
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
  `pm-e2e/src/test/java/de/planmarshall/e2e/experiment/question/`).
- HYPOTHESIS: the Expected Surface below is the package's `Where` as of `a3f3a28`; PLAN-02 may move the engine's part
  into a package here — confirm at the package file after PLAN-02 landed (verify-at-outline).
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
- HYPOTHESIS: `doc/specification/client-profiles/01-profile-schema.adoc` — the settled `headless.resumability` values (verify-at-outline)
- HYPOTHESIS: `doc/specification/model-work/01-roles-and-configuration.adoc` — the settled `questions` values (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-02 (the package it implements), PLAN-01 (the corrected evaluation rows it extends).
- Overlaps with: PLAN-01 on Client Profiles 01 and Model Work 01 — sequenced after it.
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
