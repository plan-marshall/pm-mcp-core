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
