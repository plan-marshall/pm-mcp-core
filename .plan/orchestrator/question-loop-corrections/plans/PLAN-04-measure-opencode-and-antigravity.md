# PLAN-04: Measure the question loop on OpenCode and Antigravity

epic: question-loop-corrections
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-04-measure-opencode-and-antigravity.md` and is queued as one row file, `queue/PLAN-04.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> **Staged and deferred**: it is split from PLAN-03 on 2026-10-29 and is not emitted until the launch conditions below hold.

## Objective

Implement the OpenCode and Antigravity part of the work package `question-loop-follow-up` of `plan-marshall-mcp`
(`doc/plans/question-loop-follow-up.adoc`) on the maintained driver and the evidence store that PLAN-03 leaves: settle the
OpenCode and Antigravity findings and configuration paths (tasks 3 and 4 of the package), re-measure Q1 to Q7 and R1 to R4
on those two hosts at the revised scale, observe Q2 where a host cannot withhold its question tool, settle the
OpenCode and Antigravity values (`headless.resumability`, the `questions` values of the shipped roles, whether one
question bound spans the hosts) with their evidence, name them for `pm-mcp-core` / `client-profile-measured-values`,
and close the package. The plan runs in `plan-marshall-mcp`; its results land there and in
`plan-marshall-documentation`, and it changes no specification of this repository itself.

## Deliverables

The tasks of `question-loop-follow-up.adoc` as PLAN-03 leaves them, each naming its part; this spec does not restate
them, because the package file is their only source. In outline:

1. OpenCode: the findings and the measurement setup (task 3), and Antigravity: what is observable and its configuration
   path (task 4).
2. The run plan for the OpenCode and Antigravity groups (task 6 for those hosts, budget recomputed and confirmed by the
   operator, the Antigravity start time named), and the re-measurement of Q1 to Q7 and R1 to R4 on those hosts (tasks 8
   to 11 restricted to them), with the client version of every run and the evidence in the store.
3. The OpenCode and Antigravity values of task 12 and whether one question bound spans the hosts, settled and recorded
   with their evidence, named for `client-profile-measured-values`.
4. The close of the package (task 13): rows, limits, developer document, and the package's row leaving the index.

**Done when**: the package's own Done When is met item by item for OpenCode and Antigravity, read at the revised scale,
every published figure has its source in the evidence store, and the whole package (PLAN-03's part included) meets its
Done When.

## Standing Constraints

- **Launch conditions** — emitted only on the operator's word, and only when all of these hold; the orchestrator checks
  them at `next` and sequences the plan while one does not:
  1. PLAN-03 has landed (the maintained driver, the evidence store and the amended package exist).
  2. A work package exists, in `pm-mcp-core` or `pm-mcp-clients`, that carries the OpenCode 2.x profile (Roadmap
     Milestone 14) or the shipped profile documents of `pm-api`, so that the OpenCode values have a consumer; or the
     operator decides to measure OpenCode without one and records why.
  3. Antigravity is usable (operator, 2026-10-28 18:10 CET: "available in 2 d 13 h", about **2026-10-31 07:10 CET**, a
     time computed from that statement) — for the Antigravity groups only; the OpenCode groups do not wait for it.
  4. The operator confirmed the recomputed run budget for these groups and the data-home choice.
- Scale revision, criteria, Q5 sequential, R1 and R2 longest first, per-run client version and model, evidence, and the
  rule that every figure comes from the stub's event log, the relay's host-side log and the worker's output: as stated in
  PLAN-03's Standing Constraints "Scale revision" and recorded in the package by PLAN-03; this plan applies them and
  changes none of them. A verdict states its run count and the client version, never "passed at the specified scale".
- Q6's quorum (operator, 2026-10-30): the quorum of the two vote links moved here from PLAN-03, which runs only Q6's
  consultation part on Claude Code. It votes on Claude Code and OpenCode, so this plan's budget includes its Claude Code
  voter runs.
- Re-measure before relying on a PLAN-03 value of the same host: harness versions move nearly daily, so a launch weeks
  after PLAN-03 first checks the client versions in the PLAN-03 rows against the installed ones and says which of those
  rows it re-ran.
- OpenCode runs use the configuration the package names (the inline settings of the profile, with the unset defaults as
  control only where the package says so), and the effect of each setting is checked before the first measured run, never
  its acceptance (`PM-WATCH-CLIENT-17`).
- The per-worker data home of an OpenCode 2.x worker follows the operator's decision on the open question
  `opencode-worker-login`, recorded in the package; without one, OpenCode runs serially on the operator's data home and
  every result says so.
- The OpenCode 2.x items of Roadmap Milestone 14 (version range, launch, version probe, `events` mapping, tool names)
  stay that milestone's; this plan names it as owner of those findings and changes nothing of the profile for them.
- Evidence is never kept only in a worktree: before the plan's worktree is removed, the evidence store holds every run.
- Commits end with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; no tool footer; no pull request merged without
  the operator's word.

## Claim Labels

- OBSERVED: PLAN-03 landed 2026-11-05 (`plan-marshall-mcp` #114 `61daf53` and #118 `e22f6c1`,
  `plan-marshall-documentation` #144 `2071fbc`) and amended the package: `doc/plans/question-loop-follow-up.adoc` on
  `plan-marshall-mcp` `main` names this plan's part at :8 and :82 and lists what is handed to it at :145–158 (tasks 3
  and 4, the OpenCode and Antigravity groups of tasks 6 to 11, task 9's Q2 observation on Antigravity, Q6's vote quorum,
  task 12's OpenCode and Antigravity values). Q4's cross-harness continuation is named only in the Q4 row of
  `evaluation.adoc`, not in that list. Claude Code items PLAN-03 did not run: Q5 bound 5 and R4 large work (see
  `landings/PLAN-03.md`).
  - verdict: contradicted | checked_at: 3d01811a84ab4c33641ad91f29f3bf5c0ec40c52 | by: question-loop-corrections/analyze | rescoped: yes | evidence: PLAN-03 landed (mcp #114, #118; docs #144): the package file on plan-marshall-mcp main names this plan's part at :8, :82 and :145-158; the claim was rewritten to say so
- OBSERVED: the OpenCode 2.x profile and its inline settings are specified (core #206, `plan-marshall-documentation`
  #125) with values provisional until verified, and Roadmap Milestone 14 holds the items that verify and ship them; no
  package of `doc/plans/` carries those items (re-checked 2026-10-28 at the landing of PLAN-02).
  - verdict: corroborated | checked_at: db02e44332a1818ff246c824e441d0a01ef06679 | by: question-loop-corrections/analyze | rescoped: n/a | evidence: re-checked 2026-11-10: no package of doc/plans in pm-mcp-core main carries the OpenCode 2.x profile items of Milestone 14 (client-profile-measured-values, client-profile-rendering, profile-verification-stages read)
- OBSERVED: the shipped profile documents are `classpath:/profiles/<client_id>.json` resources of `pm-api` in
  `pm-mcp-clients`, which has no work package for the measured values (ledger Watches, 2026-10-28).
- HYPOTHESIS: Antigravity becomes usable at about 2026-10-31 07:10 CET — the operator's statement, accurate to its
  rounding; confirm with the operator at launch (verify-at-outline).
- Verify-first clause: before scoping, read the package file as PLAN-03 left it at `origin/main` of `plan-marshall-mcp`
  and the indexes of both repositories; if PLAN-03 has not landed, stop and report in the inbox message.

## Expected Surface

- HYPOTHESIS: `../plan-marshall-mcp/pm-e2e/src/test/java/de/planmarshall/e2e/` — the driver extended for the two hosts' findings, in the sibling repository (verify-at-outline)
- HYPOTHESIS: `../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc` — the package file, closed when done
- HYPOTHESIS: `../plan-marshall-documentation/doc/specification/evaluation.adoc` — the result rows
- OBSERVED: `../plan-marshall-documentation/doc/developer/question-loop-measurement.adoc` — the developer document of the maintained code (created by PLAN-03, documentation #144; the earlier `question-loop-experiment.adoc` was deleted by #145, 2026-11-02)

## Dependencies and Sequencing

- Depends on: PLAN-03 (the driver, the evidence store, the amended package); the launch conditions above.
- Overlaps with: PLAN-03 on the package file, `evaluation.adoc` and the driver — sequenced after it, never concurrent.
- Hands over to: `pm-mcp-core` / `client-profile-measured-values`, which carries the settled values into the
  specifications and the profile schema, and to `pm-mcp-clients` (`pm-api`) and Roadmap Milestone 14, which own the
  profile documents and the OpenCode 2.x profile and have no work package yet.
- Adjacent to: PLAN-73 of `pm-mcp-core-m2-m4` shipped the library core of `question-continuation` (#201); the rest of
  that package waits for the settled values.

## Hand-Off Command

Run in the `plan-marshall-mcp` checkout (the package is that repository's), on `main`, only once the launch conditions
hold. The epic lives in `pm-mcp-core`, so the pointer names the ledger copy of the spec from the sibling checkout:

```text
/plan-marshall task="implement ../pm-mcp-core/.plan/local/worktrees/_orchestrator/.plan/orchestrator/question-loop-corrections/plans/PLAN-04-measure-opencode-and-antigravity.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
This epic does not resolve in the store of `plan-marshall-mcp`, so this plan files no inbox message: it reports through
its PR and its final report, and the orchestrator analyzes the landing from those.
