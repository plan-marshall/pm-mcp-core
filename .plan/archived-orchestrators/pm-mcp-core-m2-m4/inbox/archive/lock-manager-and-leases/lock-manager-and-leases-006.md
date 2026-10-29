envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:52:07Z

# Candidate lesson: main moved twice during finalize; shared log-message block caused a content conflict

- Component: epic coordination (pm-mcp-core-m2-m4, sibling plans in WS-01); pm-core PmMcpLogMessages
- Signal: baseline drift ERROR during loop-back re-entry
- Evidence: work log ERROR a4546f (2026-10-10T08:15:51Z), WARNING f96356 (09:29:44Z); decision log 676954; epic mail read at 08:42:38Z (main moved / identifier clash)

## What happened

- First drift: upstream #18 and #19 landed while the plan was in finalize; one content conflict in
  `PmMcpLogMessages.java` (log-identifier block shared by WS-01 plans). The operator chose rebase + force-push
  over a 2-refine re-cycle; both sets of log messages were kept.
- Second drift: #21 (docs/process) landed, overlap on `doc/plans/README.adoc`, no conflict; another rebase.
- Each drift cost a loop-back re-dispatch and a full re-verify.

## Suggested direction

Sibling plans in one workstream that all append LogRecord constants to `PmMcpLogMessages` should reserve
identifier ranges per plan up front (in the plan specs), so concurrent plans do not clash on identifiers and the
rebase conflict is mechanical. The orchestrator can treat `PmMcpLogMessages.java` as a shared surface in the
disjointness check.
