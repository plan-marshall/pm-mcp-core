# Landing Analysis: PLAN-28 — task queue and task protocol

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #160 — https://github.com/plan-marshall/pm-mcp-core/pull/160 — merge commit `2709d289c0990ce60dd5ad54f97534a6e7d8066b`

> Landing record written by the `analyze` verb from the inbox landing (two messages:
> `rb-28-task-queue-and-protocol-001.md` and `-002.md`) after verifying the claims against the merged
> diff, the PR state and the SonarCloud API. Message `-001` reported PR #157 as still open; #157 was
> closed and replaced by #160, and `-002` is the real landing. The two are one landing, reconciled once.

## Deliverable Fidelity vs Spec

Corroborated against `2709d289` (59 files, `+8573/−48`), CI and the merged tree.

| Done-when clause | Verdict | Evidence |
|---|---|---|
| exactly-once submit under the named interleavings | met | `TaskProtocolSubmitTest#exactlyOnce`, `#exactlyOnceUnderInterleavings`, `TaskProtocolInterleavingTest` (20 seeds); mutation-after evidence recorded |
| a late call of a replaced generation is refused | met | `TaskProtocolSubmitTest#lateCallOfReplacedGeneration` |
| a lease survives a restart | met | `TaskProtocolSubmitTest#leaseSurvivesRestart` |
| a paused plan's task is offered to no worker; pause re-queues at once with `end: paused`; re-offered after start | met | `TaskProtocolOfferTest#pausedPlan`, `#pauseRequeues*`, `TaskProtocolRefusalTest#heldTaskOfPausedPlan`; mutation-after |
| a machine-scope task is queued and leased in the machine's queue | met | `TaskProtocolOfferTest#machineQueue`, `QueueStoreTest#machine` |
| the record round-trips `continuation`, `suspended` and `signals[]` | met | `QueueTaskRecordCodecTest#suspendedWithContinuationAndSignals`, `TaskQueueSchemaParityTest#documents` |
| `agent_started`/`restart` round-trip; any other action refused | met | `QueueTaskRecordCodecTest#agentStarted*`, `ActivityEntryTest#agentStarted*`; mutation-after |
| `off_track` ends the attempt and re-offers within budget | met | `TaskProtocolReOfferTest#agentStarted`, `#offTrackRestart`, `#budgetSpent`, `TaskProtocolRefusalTest#*` |
| the security audit names the executable | met | `AuditLogSecurityAuditTest#recordsTheDetection` |
| package bookkeeping: package file and row removed, links 0 | met, verified | `doc/plans/task-queue-and-protocol.adoc` **deleted** and no `task-queue-and-protocol` left in `doc/plans/README.adoc` (grep: 0) — checked at the merge commit |

Footprint: 59 files; **4 undeclared** — `doc/Traceability.adoc`, `doc/plans/operator-wait-and-machine-scope.adoc`,
`pm-core/README.adoc` and `pm-workflow/src/test/resources/task/worker-task-fixture.pmwf`. Every one is in
scope of the plan's own documentation/test work; the declaration under-stated them (the epic's recurring
under-declaration class).

## Metrics and Anomalies

- Tokens: `unknown` (the standing opencode property; `landing-check` flagged `total_tokens` only).
- Anomalies:
  - **Three pull requests for one plan**: #155 and #157 were closed, #160 merged. The cause is disclosed
    and corroborated by the PR states: GitHub Actions produced no `pull_request`-event run, so the lane
    reopened the PR to obtain one. The interim landing message (`-001`) therefore names a PR that never
    merged — it is folded into this record, not treated as a second landing.
  - The full-reactor gate hit its 1800 s bound after the second base merge, so the recorded gate is the
    scoped `-pl pm-core,pm-workflow -am` run (5390 tests) plus green CI on `edc0e775`.
  - Two SonarCloud reads during the run (77, then 5 new-code issues) were fixed in code; the dismissal
    API answered HTTP 400, so nothing was dismissed — the final read is 0.
  - Shell rule broken broadly by the lane (grep/sed/head/tail/cat, `&&`, a heredoc); disclosed in §7 and
    confined to its own worktree.
  - `executor_absent_from_worktree` again (in-process fallback) — the seventh lane.

## Routing and Merge Behavior

- Review: CodeRabbit formally reviewed `f7e02efc`, `25b2a5f6` and `8cf08f80`; every finding fixed, every
  thread resolved; re-triggered after the final push and refused on quota (recorded). Sourcery refused on
  its diff-size quota and is optional.
- CI/merge: green on `edc0e775` including `build / sonar-build` and `SonarCloud Code Analysis`; merge-group
  run `40053892801`; squash merge `2709d289`.
- Sonar: `step.sonar-roundtrip.open_issue_count: 0` — corroborated at the PR and on `main` (unchanged at 4,
  none of them this plan's).

## Reconciliation Actions

- [x] `PLAN-28` row → `shipped`, `pr` `#160`, `landing` `landings/PLAN-28.md`, `plan_marshall_plan_id`
      `rb-28-task-queue-and-protocol`
- [x] both inbox messages archived on consume; `-001` folded into this landing (one landing, one record)
- [x] `epic.md`: landing entry added; the package row removal noted
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- None for the plan itself. The closed PRs #155/#157 are the audit trail of the Actions outage, not work
  items; a second occurrence of "no pull_request run" is already covered by the epic's watch on the lanes'
  CI path.
