# Landing Analysis: PLAN-35 — Supervision scenarios

epic: pm-mcp-core-m2-m4
workstream: WS-06
pr: #229

> Landing record for PLAN-35 (runbook lane `rb-35-supervision-scenarios`, harness opencode). Source: inbox message
> `rb-35-supervision-scenarios-001.md`, corroborated against the merge commit and the PR read through `ci`. The
> package is only PARTLY delivered: D2, D3 and D4 shipped; D1 is partial and D5, D6, D7 are not delivered here.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 fault matrix scenarios | dropped (partial) | No `pm-scenario` fault directive added: `kill_worker`, `stall_worker`, `end_turn`, `kill_relay`, `restart_runtime`, `assert_worker_state`, `assert_refused` still refused with `supervision-scenarios`. The shipped `SupervisorScenarioTest` (#190) and `TaskProtocolSubmitTest` cover part of the matrix. |
| D2 off-track scenarios | shipped-as-specified | `OffTrackScenarioTest` (new, 349 lines, 7 tests) in the merge diff of `a8da4104`. |
| D3 supervisor scenarios | shipped-as-specified | `SupervisorScenarioTest` +70 lines (2 new tests) in the merge diff. |
| D4 operator act | shipped-modified | `operator_do` directive in `ScenarioDirective`, `ScenarioScript`, `ScenarioReplayRunner`, `ScenarioFixtures`; ADR 008 records it. |
| D5 | dropped | Not delivered here. |
| D6 conformance harness | dropped (moved) | ADR 007 `007-conformance-harness-in-pm-conformance.adoc` (in the merge diff) moves the home out of this repository. |
| D7 conformance harness | dropped (moved) | Same ADR. |

The `deliverables_done=3` fact of the message equals D2, D3 and D4. The package's Done-when is NOT met as a whole.

Undeclared surface: `ScenarioFixtures.java` (existing, not named in the spec) and the new `OffTrackScenarioTest.java`.
Declared but untouched: `ScriptedFixtureWorker.java`, `ScenarioDeterminismTest.java`, four specification/watch files.

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none); `plugin_version` also `unknown`. The landing check reports
  `complete: false` for these two keys only.
- Duration: not supplied.
- Anomalies: executor skew at start (0.1.2028 vs 0.1.2034), regenerated on the operator's word; first routed build
  failed with an empty log and every later build ran `--execution-mode in_process`; one verify cut by the client's
  300 s default bound; second self-review dispatch not made (reported deviation); no failing run captured for the
  test-first evidence order of D2, D3, D4 (reported as not met as written).
- Spec read: `spec_source: ledger_worktree`, `spec_differ: true` — the lane read the adapted spec from the ledger
  worktree although it was started before the land, so it ran on r2.

## Routing and Merge Behavior

- Review: coderabbit reviewed `1af422a6` (2 minor, 3 nitpick, all fixed in `2169551e`), then refused the re-trigger on
  quota; sourcery refused on quota. Standing review authorization applied.
- CI/merge: squash-merged through the merge queue, merge commit `a8da4104e3efad83b2ca9c1b28ec724ad402fd8a`, confirmed
  by `ci pr view` (`state: merged`, same sha, base `main`). One red `build / sonar-build` on the push-event run, green
  on rerun (R18).
- Sonar: lane reports quality gate passed, 0 new issues (`open_issue_count=n/a` in the facts; the provider answered
  `unconfigured`). Not independently measured here — recorded as the lane's claim.
- Collisions: none reported with PLAN-46 or PLAN-105.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-35 --status shipped`
- [x] row `pr` stamped — `#229`
- [x] row `landing` stamped — `landings/PLAN-35.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-35-supervision-scenarios`
- [x] epic.md: Open Defect for the incomplete landing facts, Watch for the undelivered residue
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed

## Follow-Ups

- D1 (fault directives and a runtime-crash-with-a-leased-task scenario), D5, and D6/D7 (conformance harness, home
  decided by ADR 007) remain undelivered. The package file and its index row stay open in the product repository. Not
  staged as a new plan yet: it needs the operator's decision (see the Watch in epic.md).
- `REFUSED` still maps `worker_wait`/`worker_do` to the shipped `task-queue-and-protocol` (ADR 008 names it).
