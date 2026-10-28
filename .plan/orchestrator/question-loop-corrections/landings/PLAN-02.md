# Landing Analysis: PLAN-02 — Revise the follow-up package and the dependencies of question-continuation

epic: question-loop-corrections
workstream: WS-02
pr: #212 (`pm-mcp-core`), #110 (`plan-marshall-mcp`)

> Landing record for one shipped plan. Written by the `analyze` verb from the inbox message
> `rb-02-revise-follow-up-package-001.md`, after checking its claims against git and the CI abstraction.
> Claims the checks could not settle are marked as unverified leads.

## Deliverable Fidelity vs Spec

Checked against `origin/main` of both repositories on 2026-10-28 (`pm-mcp-core` `6010dbc7`, `plan-marshall-mcp`
`da9d6d8`). The documents-only claim and the Done-when clauses are the landing's own statements; the rows below say
what this analysis read itself.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Durable evidence | shipped-as-specified | `question-loop-follow-up.adoc` names the branch `question-loop-follow-up` of `cuioss/plan-marshall-telemetry` as the evidence store (line 15) and its Done When checks it; the branch itself does not exist yet (PLAN-03 creates it) |
| D2 Scale and criteria | shipped-as-specified | The package states 20 runs per fixture and host for each form (task 8, lines 92–97), carries the fixed-criteria sentence, and records the operator's two criteria decisions in task 2 |
| D3 Tasks that can fail | shipped-modified | Codex is removed and named as not measured (task 5, lines 71–76) as the spec allows; the package now has 13 tasks (task 13 closes and records), where the spec counted 12 |
| D4 No dependency cycle | shipped-as-specified | `doc/plans/client-profile-measured-values.adoc` exists on `pm-mcp-core` `main`, depends on the follow-up in the index; `question-continuation` depends on it and its bullet *Measured first* names the follow-up (line 22) |
| D5 The `done` of a measurement package | shipped-as-specified | Option (b): the `question-loop-verification` row of `plan-marshall-mcp` `doc/plans/README.adoc` (line 103) keeps `done` and states that the follow-up carries the unmet part |
| D6 The OpenCode 2.x worker as now specified | shipped-as-specified | Task 3 keeps the question loop's findings, states the inline-settings configuration (line 53) and records the serial data-home decision (line 54); task 12 takes `inline_settings` as the shape of a schema addition |

Not corroborated by this analysis: the Done-when clause `BROKEN LINKS 0` against both repositories' `main`. The landing
reports 0 for `pm-mcp-core` and 2843 for `plan-marshall-mcp`. The local `plan-marshall-mcp` checkout is behind `origin/main`
(#110), so a run here would measure a stale tree; the figures stay unverified leads (see Follow-Ups).

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none). The landing is therefore incomplete (`total_tokens`).
- Duration: not reported in the facts block.
- Lane: runbook lane on OpenCode, `deepseek-v4.1-flash/high`.
- Anomalies: the self-review reached its round limit with findings addressed but unconfirmed (`self-review:budget-exhausted`);
  the operator chose to open the pull requests. A deviation list in the message records a second repository (authorized
  by the operator), a read outside the root, and one `git commit --amend` of a local unpushed commit.
- Gate: `verify -Ppre-commit` on the merged tree: 7922 passed, 2 failed (`SubprocessJobRunnerTest.pidReuse`, `AgentStartedDetectorTest.theOperatorBinaryMatches`), both host-dependent, in `pm-runtime`, a module the change does not touch. Not re-run by this analysis.

## Routing and Merge Behavior

- Review: CodeRabbit raised 2 findings on #212 and 5 on #110, all fixed or answered and every thread resolved; CodeRabbit was quota-limited on the final heads, so the last pushes were not re-reviewed by it. Sourcery approved an earlier head of #212.
- CI/merge: `pm-mcp-core` #212 reads `merged` with `merge_commit_sha` `6010dbc7…` (read by `ci pr queue-state`); `plan-marshall-mcp` #110 is on its `origin/main` as `da9d6d8`. A duplicate Maven run failed its `sonar-build` on an analyzer error; the canonical run passed.
- Sonar: `step.sonar-roundtrip.open_issue_count` is `n/a` (project not configured); nothing to compare.
- Collisions: none observed. The plan edited `doc/plans/README.adoc` in a repository where sibling plans also edit it, with no conflict reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-02 --status shipped`
- [x] row `pr` stamped — `#212`
- [x] row `landing` stamped — `landings/PLAN-02.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-02-revise-follow-up-package`
- [x] epic.md narrative reconciled (defects, watches, decisions, queue annotations)
- [x] defect opened: the landing is incomplete (`total_tokens`)
- [x] PLAN-03 rescoped to the landed package; two verdicts persisted
- [x] finding sent to the inbox of `pm-mcp-core-m2-m4` for the two statements outside this epic
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- PLAN-03's Expected Surface and Deliverable 4 followed the move of the engine's part into `client-profile-measured-values`: PLAN-03 changes no specification of this repository any more. Done in the same act as this landing.
- `client-profile-measured-values` is a package of `doc/plans/` with no plan in this epic. It is `open` in the index and depends on the follow-up, so it is ready only after PLAN-03; this epic does not stage it.
- No work package exists for the owed change to the shipped profile documents in `pm-api` of `pm-mcp-clients`, nor for the OpenCode 2.x items of Roadmap Milestone 14. Recorded as Watches.
- `worker-integration-tests.adoc:14` still names `question-loop-verification` as the measurer of the real harnesses; outside this plan's declared surface. Recorded as a Watch.
- The landing says the main-checkout copy of the PLAN-02 spec is the older five-deliverable revision. The ledger copy is the one this analysis read and the one the land carries.
