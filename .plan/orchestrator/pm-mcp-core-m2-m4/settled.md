# Settled narrative: pm-mcp-core-m2-m4

Sections of `epic.md` whose subject is closed, moved here verbatim by `cleanup` on 2026-10-12 with the operator's confirmation. Each origin keeps a pointer.

## Watch: PR #16 before the first land

- PR #16 must be on `main`, and the ledger worktree on that base, before the first `land`:
  until then the worktree's `.gitignore` still ignores the ledger.

## Watch: code-analysis-base at decompose

- `code-analysis-base` (added by #15, serves Milestone 6, depends on nothing, `open`) is in the
  index but outside the Milestone 2 to 4 scope this epic was framed for — decide at `decompose`
  whether it joins the queue.

## Watch: the two files nearly every package edits

- Nearly every package is expected to edit both `doc/plans/README.adoc` (its status row) and
  `PmMcpLogMessages` in `pm-core`; two shared files is the threshold at which the disjointness
  gate asks the operator — decide at `decompose` how the specs declare these two files.

<!-- moved by cleanup on 2026-10-13 with the operator's standing confirmation -->

## Watch: doc #57 of PLAN-17

- Documentation PR `plan-marshall-documentation` #57 (PLAN-17) is OPEN — PLAN-17's landing completes when it is merged. Merge it before the log-holder plan's documentation PR (both edit `doc/LogMessages.adoc`; the split renumbers rows 70/170/270).

## Watch: doc #20 of PLAN-02

- `plan-marshall-documentation` #20 (documentation of PLAN-02) was still open on 2026-10-10 at the
  PLAN-03 drain. The index already says `done`. Retire when #20 is merged.

## Watch: documentation #36 during PLAN-41

- `plan-marshall-documentation` #36 changed `workflow-dsl/02-units-jobs-decisions.adoc` during
  PLAN-41's execute (07:25Z); PLAN-44 re-verifies against the current specification.

## Defect: shared log identifier blocks

- Two plans of one workstream can take the same log identifier; it surfaced as a merge conflict
  between #19 and #20. The shared block is per workstream, not per plan — source: landing PLAN-03;
  confirmed by PLAN-01 (inbox 006), which moved to WARN 151. PLAN-04, PLAN-08 and PLAN-09 share the
  same WS-01 block.

## Watch: the log-holder PRs

- pm-mcp-core #64 (log holders per module) and plan-marshall-documentation #68 are OPEN. Merge #64 first, then #68. No plan is emitted before #64 is on main, because every open spec's log rule names the new holders. Once #64 has merged, retire the Open Defect on shared log identifiers.

## Watch: doc #56 of PLAN-21

- Documentation PR `plan-marshall-documentation` #56 (PLAN-21) is OPEN — PLAN-21's landing completes when it is merged.

<!-- moved by analyze on 2026-10-14: plan-marshall-mcp #76 merged (e34961b) -->

## Watch: pm-mcp-server against the log-holder SNAPSHOT

- plan-marshall-mcp `pm-mcp-server` imports `PmMcpLogMessages` and breaks against the SNAPSHOT of #64 until it switches to `RuntimeLogMessages` (all of its 16 records) or takes over its own records.

## Watch: pm-mcp-server and SweepStep.notImplemented

- plan-marshall-mcp `pm-mcp-server` still calls `SweepStep.notImplemented`, which PLAN-12 removed; the assembly breaks against the new pm-core/pm-runtime SNAPSHOT until it registers `PendingEntryRecordsSweep` and `FencedSessionGenerationsSweep`. This adds to the earlier breaking changes for plan-marshall-mcp (PLAN-06, 08, 11, 36).
- Refuted by #76: the assembly never used `SweepStep` or `PeriodicSweep` (no use site at e34961b^1). The sweep wiring is owed as new behaviour, not as a follow-up — see the plan-marshall-mcp watch in `epic.md`.

## Watch: breaking changes for plan-marshall-mcp

- Breaking changes for plan-marshall-mcp against the deployed SNAPSHOT: `FindingSource` /
  `FindingResponder` type parameters (PLAN-36); `StartupSequence`, `ProjectRecovery` (PLAN-08);
  `AuditEvent`, `CrossScopeReconciler`, journal format (PLAN-06).
- Settled by #76: `StartupSequence` followed (`AuditChainVerification.NONE` plus a refusing sink in `PmMcpd`); `FindingSource`, `FindingResponder`, `ProjectRecovery`, `AuditEvent`, `CrossScopeReconciler`, the journal format and the enrolment store types have no use site in the assembly. All gates and CI green on 28bd5fe.

<!-- moved by analyze on 2026-10-14: PLAN-56 shipped as #66 (7981d17) -->

## Defect: static verifier residuals after PLAN-54

- Static verifier residuals after PLAN-54: Rule 9 firing per step, result guards on non-call transitions, the remediation budget re-check, `lost` in plan-only sub-workflows, the Rule 3 activation, and documentation over-claims. Owned by PLAN-56 — source: landing PLAN-54.
- Settled by PLAN-56 (#66, 7981d17; documentation #72): every item was implemented or named as a seam. The Rule 3 activation remains a seam until documentation issue #71 is answered (see the watch in `epic.md`); `landings/PLAN-56.md`.

<!-- moved by analyze on 2026-10-15: PLAN-09 shipped as #69 (3236ed5) -->

## Watch: no production AuditSink or AuditChainVerification in pm-mcp-core

- **No production `AuditSink` or `AuditChainVerification` in pm-mcp-core** (only test sinks; `AuditChainVerification.NONE`). Since #76 the daemon starts with `NONE` and `NO_AUDIT_STORE`, a sink that refuses every record (`StartupSequence` then logs `AUDIT_CHAIN_BROKEN_NOT_RECORDED`). PLAN-09 `audit-trail` is the natural owner — source: plan-marshall-mcp #76 report, corroborated 2026-10-14.
- Settled by PLAN-09 (#69, 3236ed5): `core.store.AuditLogSink` is the production sink and `runtime.audit.AuditVerification` implements `AuditChainVerification`. The wiring in plan-marshall-mcp is owed (see the plan-marshall-mcp watch in `epic.md`); `landings/PLAN-09.md`.

<!-- moved by analyze on 2026-10-15: PLAN-24 landing drained -->

## Watch: PLAN-24 landing

- PLAN-24 `plan-instance`: code PR #67 merged (2ebec2a, 2026-10-14); documentation PR #73 open; no landing message yet — checked 2026-10-15.
- Settled: documentation #73 merged (1eaa6c6) and the landing message `plan-24-plan-instance-001.md` was drained on 2026-10-15; `landings/PLAN-24.md`. The code/specification gap it left is a new watch in `epic.md`.

<!-- moved by cleanup on 2026-10-15: corpus reconciled at a8a4214 -->

## Defect: staged specs behind doc/plans/

- **Staged specs behind `doc/plans/` (corpus reconciliation owed; run `cleanup`).** Between 7981d17 and
  2ebec2a three pull requests outside this epic's plans changed the package corpus:
  - #70 (017ed53) — the packages now implement the specification agreed in documentation #76: 19 packages
    lost their "specification first" task, contradictions were fixed, and three packages are new
    (`removed-concepts`, `worker-activity`, `process-and-overview`). `session-as-worker` is proposed for removal
    and `blocked` on the operator's decision.
  - #68 (4ab1f56) — the new package `corpus-sources`; `role-qualification` now depends on it.
  - #67 (2ebec2a, PLAN-24) — the new package `lane-route-pruning`, added by operator exception.

  Affected rows of this queue:
  - staged: PLAN-15 `project-machine-store`, PLAN-28 `task-queue-and-protocol`, PLAN-33 `session-as-worker`
    and PLAN-34 `role-qualification`;
  - shipped: PLAN-12 `session-log-and-generations`, whose code `removed-concepts` now removes;
  - parked: PLAN-42, 43, 45 to 53, 55 and 57 to 63.

  The five new packages have no row. The staged specs were generated before these changes, so their
  deliverables, dependencies and surfaces may be stale, and an emit from them could start a plan against
  the old package text. Hold these rows until `cleanup` (Phase A, corpus reconciliation) re-grounds them —
  source: `git diff 7981d17 origin/main -- doc/plans/` and the bodies of #67, #68 and #70, checked
  2026-10-15.
- Settled by cleanup (2026-10-15, at a8a4214, which also covers #72, #73 and #74):
  - PLAN-15, 23, 28 and 34 were corrected, PLAN-33 was retired, and the 19 parked specs were regenerated.
  - PLAN-64 to PLAN-68 were staged for the five new packages.
  - `corpus-sources` was done by #74 outside the queue.
  - See the queue annotation in `epic.md` and the cleanup decision in the decision log.

## Defect: corpus-overlay-extensions outlived its implementation

- **`corpus-overlay-extensions` outlived its implementation.** pm-mcp-core #80 (238c823) implemented the package (HTTPS fetch for
  `huggingface`, several items per row, `answer_schema`; `HuggingFaceSourceRepository`, ADR `core-role-corpus/006`), but
  `doc/plans/corpus-overlay-extensions.adoc` and its row in `doc/plans/README.adoc` are still there, although a plan's pull
  request removes both. It has no queue row. Its Done When asks for one import of the three sources against `huggingface.co`,
  run by hand and recorded in the pull request; whether #80 recorded it was not verified. A small `docs(plans)` change in
  pm-mcp-core removes file and row once that is confirmed — source: cleanup of 2026-10-16.

## Defect: watch link of stalled-scope-detection.adoc

- **Watch link of `doc/plans/stalled-scope-detection.adoc`.** The package file links the watch `Runtime Model` of the documentation
  repository, which holds no stalled item; the items PM-WATCH-RUN-2 and PM-WATCH-RUN-8 are in
  `doc/implementation-watch/runtime-lifecycle.adoc` here. PLAN-23 names the right file; the package file is owed a one-line fix
  — source: cleanup of 2026-10-16.
