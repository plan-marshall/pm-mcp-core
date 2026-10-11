# Landing Analysis: PLAN-04 — Per-user service unit

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #25 (https://github.com/plan-marshall/pm-mcp-core/pull/25), merge commit 0ca1b24

> Landing record for one shipped plan. Lives at `landings/PLAN-04.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-04-service-unit-014.md` (landing-facts complete, no missing key),
corroborated on 2026-10-11 against the PR state (MERGED 06:35Z), the file list of 0ca1b24, the
index and `PmMcpLogMessages` on `origin/main`, and the plan's archived `execution.toon`.

## Deliverable Fidelity vs Spec

Verdicts rest on the file list of the merge commit; test content was not read.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| The per-user service unit for launchd and systemd (install, uninstall, rendering) | shipped-modified | Code and tests in `runtime/start/`, fixtures `src/test/resources/service-unit/` (`de.planmarshall.mcpd.plist`, `pm-mcpd.service`). Operator-accepted deviation: systemd uninstall order disable → delete unit file → unload; PM-WATCH-RUN-15 stays open |
| Index row | shipped-as-specified | `service-unit` reads `done` on `origin/main` |
| Documentation | in flight | `plan-marshall-documentation` #26 open (Start Modes, LogMessages PM_MCP-51/52/251, roadmap, PM-WATCH-RUN-18); awaits the operator's merge |

Surface: `landing-check` `unmeasured`; read from 0ca1b24: every path inside the declared
`runtime/start/` and `src/test/resources/`, plus the index row and `PmMcpLogMessages`
(undeclared by the block decision). No expansion.

Log identifiers: INFO 051, 052 and ERROR 251, under their own WS-01 block comment.

## Metrics and Anomalies

- Tokens: 3,824,435 recorded; wall time 52,023 s (about 14.5 hours).
- Three review/Sonar loop-backs: round 1 hardening (explicit rw-r--r-- modes) raised Sonar
  java:S2612 and a red quality gate; round 2 moved to owner-only 0600/0700.

## Routing and Merge Behavior

- Review: `cuioss-review-bot` was `participated_stale` after the two fix commits. The plan's
  manifest carried `re_review_on_loopback: false` — composed before #24 merged — so no `/review`
  was posted; merged under an operator `barrier-ask-override` at 2297ecc.
- CI/merge: squash via the merge queue, 0ca1b24; Sonar 0 new-code issues.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#25`; `landing` `landings/PLAN-04.md`
- [x] 13 lessons dispositioned: 4 promoted here (2026-10-11-07-002 to -005), 9 routed to plan-marshall (`pm-mcp-core-m2-m4-039.md` to `-047.md`)
- [x] epic.md narrative reconciled; resume anchor; `queue-view.md` regenerated

## Follow-Ups

- PLAN-08 `runtime-lifecycle` (also `runtime.start`) is unblocked by this landing.
- Documentation #26 in `plan-marshall-documentation` to merge (operator).
