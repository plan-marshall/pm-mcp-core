# Landing Analysis: PLAN-06 — Versioned store substrate

epic: pm-mcp-core-m2-m4
workstream: WS-02
pr: #26 (https://github.com/plan-marshall/pm-mcp-core/pull/26), merge commit cbdd04e

> Landing record for one shipped plan. Lives at `landings/PLAN-06.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-06-store-substrate-015.md` (landing-facts complete, no missing key),
corroborated on 2026-10-11 against the PR state (MERGED 07:07Z), the file list of cbdd04e, the
index and `PmMcpLogMessages` on `origin/main`, the `/review` comment and bot run on #26, and the
plan's archived decision log.

## Deliverable Fidelity vs Spec

The plan counts 13 deliverables; verdicts rest on the file list, test content was not read.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Versioned store substrate: journal, digests, scope transactions, cross-scope reconciliation | shipped-as-specified | 44 paths in `core/store/` (main and test) |
| Index row | shipped-as-specified | `store-substrate` reads `done` on `origin/main` |
| Documentation | not done | The `plan-marshall-documentation` branch `feature/plan-06-store-substrate` (b4d886e, a3f97af, d74ce90, 7d0d1c0) is local and unpushed; it carries D1, D11–D13, the audit-delivery specification and PM_MCP-160 |

Surface: expansion. Outside the declared `core/store/`: `core/service/AuditEvent` and
`AuditSink` (new `transactionId` parameters, plus `AuditEventTest`), `runtime/lock/StaleLeaseSweep`
with its test, and test files in `core/spi`, `core/spi/validation`, `core/toon`, `core/findings`,
`core/log` and `BuildGuardsIT`. The `AuditEvent`/`StaleLeaseSweep` edits are API follow-through;
the edits to unrelated tests fit the non-idempotent pre-commit recipe (lesson 2026-10-10-14-001).
No running plan was hit: PLAN-05 and PLAN-07 work elsewhere.

Log identifiers: WARN 160 `AUDIT_RECORD_DEFERRED`, the first of the WS-02 block 160-169.

## Metrics and Anomalies

- Tokens: 5,617,933 recorded; wall time 84,818 s (about 23.6 hours).
- 57 review findings resolved; recurring Sonar rules reached the PR, one round-1 fix raised a
  round-2 issue (lesson 2026-10-11-07-010).
- Post-merge `verify -Pcoverage` and `-Pintegration-tests` green on `main` at cbdd04e (plan's claim).

## Routing and Merge Behavior

- Review: the manifest was composed with `re_review_on_loopback: false`; at 06:29Z the operator
  chose to set it to `true` plan-locally and plan-marshall posted `/review` (06:30:39Z). Run
  38118297503 pulled `ghcr.io/cuioss/pr-agent-mirror@sha256:b253845c…` and its "reviewer actually
  produced a review" step passed at head 64414e3. Three fix commits followed at 06:50Z; the
  operator ruled "at least one review is ok" and the plan merged without a second `/review`.
- CI/merge: merge queue, cbdd04e; Sonar 0 new-code issues.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#26`; `landing` `landings/PLAN-06.md`
- [x] 14 lessons dispositioned: 5 promoted here (2026-10-11-07-006 to -010), 9 routed to plan-marshall (`pm-mcp-core-m2-m4-048.md` to `-056.md`)
- [x] review-bot Watch retired (all three steps seen on #26); stale-snapshot defect opened
- [x] epic.md narrative reconciled; resume anchor; `queue-view.md` regenerated

## Follow-Ups

- Documentation branch to push and its pull request to open in `plan-marshall-documentation` (operator).
- Downstream API changes for `plan-marshall-mcp`: journal `format_version` 2 with `owed_events`;
  `CrossScopeReconciler.reconcile(LockManager, AuditSink)`; `AuditEvent.jsonlTailRepaired` /
  `crossScopeReconciled` take a `transactionId`; `ScopeTransaction.commit` refuses while an
  unrecovered journal exists.
- Known limitation for PLAN-09 `audit-trail` and the mailbox package: closing a channel on a POSIX
  file releases the process's OS locks on it, so `JsonlStoreFile` appends on file-locked stores drop
  the lock manager's OS lock; the fix belongs in the lock manager.
- Unblocked: PLAN-09, PLAN-11, PLAN-12, PLAN-14 (all `core.store`, mutually overlapping).
