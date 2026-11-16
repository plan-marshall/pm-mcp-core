# Landing Analysis: PLAN-V2-33 — The scenario harness runs the task protocol

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #457 (merge `688c0a42`, 19 files, `merged` through the merge queue)

> Landing record written by the `analyze` drain of 2026-11-16 from the message `rb-v2-33-scenario-task-protocol-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high). `landing-check` reports `complete: false`: `total_tokens` and `plugin_version` are `unknown`, the known defect E27 (recurrence, no new entry). Every point marked re-read was read again by the orchestrator at its `file:line` at `d1fa2ecb`, not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|---|---|---|
| D1: a real queue and the protocol under the replay, counters and the manual clock | shipped-as-specified | `ScenarioFixtures` (+285), `ScenarioReplayRunner` (+400), `ScriptedProtocolWorker` (new, 112 lines); the diff stat of `688c0a42` re-read; two-replay comparison of the status and queue bytes in `ScenarioDeterminismTest` (leaf read: `Snapshot` equality, the generation counter per fixtures) |
| D2: `worker_wait` and `worker_do ack | submit:<kind> | infeasible` beside the direct form | shipped-as-specified | `ScenarioScript`, `ScenarioDirective`, runner; the role-less `ack {}` targets the worker of the last `worker_wait` (leaf read) |
| D3: `assert_worker_state` and `assert_refused` | shipped-modified | `offered`, `acknowledged` and `closed` are producible; `queued` and `fenced` are not (no directive of this plan releases, fences or replaces a generation: `ScenarioReplayRunner.java:504-526` re-read); `assert_refused` is checked at read time (`ScenarioScript.java:172-188` re-read), `assert_worker_state` only at run time |
| D4: the consultation scenario (regression scenario 14) | shipped-modified | the refusal half is a script that fails; the accepted-submit half is a direct `validateSubmit` call in the Java test, not a replayed script (`ScenarioReplayRunnerTest.java:661-683` re-read), because the consultation id comes from `QueueTaskId.random()` (`ConsultationService.java:551-553` re-read) and a script cannot name it |
| D5: directive table, guide, `package-info.java`, ADR 009, specification sentence, package file | shipped-as-specified | ADR 009 (93 lines), ADR 001 and 008 edits, the guide (+241/-), `package-info.java`, Workflow DSL 07, `doc/plans/supervision-scenarios.adoc` in the stat |
| Surface | shipped-modified | one new class (`ScriptedProtocolWorker`, declared by the lane), `hand-written-doubles.txt` (+3 lines) with ADR 026 amended (+18), `doc/Traceability.adoc`, ADR 001 and 008; no `src/main` path in the diff (re-read: 0) |

## Three-Layer Analysis

Read on three layers by one read-only leaf (`execution-context-level-3` on the `orchestrator.analyze` surface), one landing per leaf; the orchestrator re-read the points marked below. **By reading, no build, no test run, no write.**

- **Dispatch:** one read-only leaf — reason: every full ship gets its own leaf.
- **Verdict:** layer 1 findings (1 minor); layer 2 findings (1 major, 4 minor); layer 3 findings (3 minor, one not shown by reading). No blocker, no state-machine defect found.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D1 says two replays of one script give byte-identical stores; D4 requires a scenario over the shipped `ConsultationService` with no production change; `freeId()` draws a machine-random id, so both cannot hold for a consultation scenario. The Done-when holds only because it asks byte identity of the protocol-worker scenario. The spec also lists states (`queued` after a release, `fenced`) and a "replaced generation" refusal that only PLAN-V2-49 can produce | checked, re-read for the `freeId` half | refutes | `ConsultationService.java:551-553` |

**Layer 1 verdict:** findings — 1 (minor). No corpus verdict persisted: the claims the spec carries (HYPOTHESIS labels) were settled at the emit, and the lane confirmed all four.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D4 | `ScenarioReplayRunner.java:396-410` (re-read) | the consultation tests | the accepted-submit path (`consultationIds()` parse, protocol submit, settle) is never run by a replayed script; no passing script holds scenario 14 (**major**) | checked (re-read) |
| D3 | `ScenarioReplayRunner.java:504-526` (re-read) | `offered`, `acknowledged`, `closed` | `queued` and `fenced` are dead surface with no test; ADR 009 says `assert_refused` "already accepts the fence's refusal", true of the wire name only, since `generation_fenced` is unreachable (minor) | checked (re-read) |
| D3 | `ScenarioScript.java:137-186` | — | `assert_worker_state` needs only an earlier `worker_wait` of the role, so `worker_wait r; assert_worker_state r closed` reads fine and fails at run time: the read-time guarantee of the spec is narrower than stated (minor) | checked |
| D4 | `ScenarioReplayRunner.java:396-410` (re-read) | — | the consultation-reference refusal is thrown as a `ScenarioFailure`, never a `CallResult.Refused`, so `assert_refused invalid_link_args` cannot express it; the same for every refusal raised before the protocol call (minor) | checked (re-read) |
| Lane claim (a): two-replay comparison covers status and queue bytes and fails under a static generation counter | — | `ScenarioDeterminismTest` | the comparison reads as claimed; the mutation run itself is not shown by reading | checked |
| Lane claim (b): consultation not byte-identical, disclosed | — | — | honest: PR, ADR 009 and the guide disclose it; neither says it also makes the accepted submit unscriptable | checked |
| Lane claims (c), (d), (e), (g) | role-less `ack {}`, settle on any closing accepted call, `assert_refused` adjacency, derived consult roles | — | all hold (leaf read); caveats: a consultation task closed by a plain submit throws loudly, `consultRoles` is the union over top-level states | checked |
| Lane claim (f): existing scenarios replay unchanged | — | — | ids unchanged, but tests were edited and `shouldFailSurplusOutcome` now needs a second sub-directory because the queue persists in the replay directory; a direct-worker task stays `queued` in the queue record while the engine is settled; no golden bytes exist, so byte equality with before is not shown | checked, partly |

**Layer 2 verdict:** findings — 5 (1 major, 4 minor).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `ScenarioScriptTest.java:385-388` (re-read) | four `assertFalse(…containsKey…)` without a message; one `assertDoesNotThrow` in a `forEach`; two dynamic messages over 60 characters | CLAUDE.md "every assertion carries a message of 20 to 60 characters" is violated by new code; `pm-workflow` has no scan guard yet, so nothing machine-checks it | minor |
| L3-2 | `ScenarioFixtures.java:672-735`, ADR 026 | `QueueTasks` replaces an EasyMock `QueueTaskCreator`; `Locks` duplicates the package-private `workflow.task.TestLocks` | the amendment's reason for `QueueTasks` ("a mock would restate the counter") describes what the old mock already did through its `Capture`; weak, not wrong | minor |
| L3-3 | `ScriptedProtocolWorker.queueTaskId`; runner fields `currentWorker`, `lastWorkerCall`, `scope`, `store`, `status`, `ended` | null-initialised fields without `@Nullable` | CLAUDE.md "a field that may be absent is `@Nullable`" (test scope, so `@NullMarked` is not required) | minor |

**Layer 3 not shown by reading:** the validate-then-submit order is the harness's own choice, and whether the shipped server composes the two in that order and with which wire name cannot be read here; `pm-core` record bytes; a `QueueStore` collision on a reused directory; the mutation and the green-build runs.

**Layer 3 verdict:** findings — 3 (minor); one not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md | JUnit 5 only, `@DisplayName`, no AAA comments, no Mockito or Hamcrest | conforms | diffs read by the leaf |
| CLAUDE.md | Every assertion message 20 to 60 characters | deviates | L3-1 |
| CLAUDE.md | Hand-written doubles only per ADR 026 | conforms | the amendment and two baseline lines are present and consistent; the justification is weak (L3-2) |
| CLAUDE.md | Absence: a field that may be absent is `@Nullable` | deviates | L3-3 |
| CLAUDE.md | No new dependency, no production code | conforms | 0 `src/main` paths in `git show --stat 688c0a42` (re-read) |
| ADR 002 / spec D1 | Byte-identical stores from counters and the manual clock | conforms | the only random source is `freeId()`, disclosed |
| CLAUDE.md | Test data from generators | not shown by reading | script literals are contract text; not audited literal by literal |

## Metrics and Anomalies

- Tokens: `unknown` (the runbook lane measures none; an unobserved fact, not a zero).
- Duration: not stated by the lane. 29 builds, all `daemon_longpoll`, none fell back or was killed.
- Anomalies: the lane's three edits first issued against the main checkout by a path mistake (reverted or verified untouched by the lane; no commit carries one); the lane ran no guard (`guard: none`, OpenCode). The final merge diff carries no main-checkout path outside the 19 files.

## Routing and Merge Behavior

- Review: CodeRabbit required (quota-limited, reviewed `71cb8830`, 5 inline findings and 1 nitpick, all fixed in `48e1189f` and answered); Sourcery optional, approved with no finding. Self-review round 1: 26 findings, no blocker, two real defects fixed.
- Review numbers re-read: `unresolved_review_threads` 0, equal to the landing's 0. `review_body_findings_total`: `review-gate` on #457 reads 6, equal to the landing's 6. `review_body_findings_handled`: the progress file `.plan/temp/rb-v2-33-scenario-task-protocol/progress.md` carries 6 `finding:` lines (five inline and one nitpick, each naming `48e1189f`), so the landing's 6 handled is corroborated. No false claim.
- CI/merge: merged through the merge queue as `688c0a42`; `cleanup_owed=false`; no `v2-33-…` worktree remains under `.plan/local/worktrees/` (re-read at the start of this analysis).
- Sonar: the landing's `sonar_open_new_issues=0` and gate `OK` were not re-measured by this analysis; the lane's claim, not re-verified.
- Surface collisions: none observed in this landing; PLAN-V2-49 shares the scenario package with it by design and its own spec says "strictly after the merge".

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-33 --status shipped`
- [x] row `pr` stamped — `#457`
- [x] row `landing` stamped — `landings/PLAN-V2-33.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-v2-33-scenario-task-protocol` (from the sender)
- [x] epic.md narrative reconciled (queue annotation; E27 recurrence, no new line)
- [x] residue routed: the harness gaps (D3 dead states, untyped refusals, read-time check, the accepted-submit scenario) into PLAN-V2-49 as a carried section; the rest into PLAN-V2-114 item 33 with its Expected Surface widened in the same edit
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- PLAN-V2-49 (staged, "strictly after its merge", now unblocked): the carried section names the five harness gaps; its Expected Surface already covers the whole scenario package, so no surface edit.
- PLAN-V2-114 item 33: the machine-random consultation id as a production seam (the one change that makes scenario 14's accepted submit scriptable; production code, so not in 49), the weak ADR 026 justification, the `@Nullable` fields and the missing assertion messages (the messages overlap PLAN-V2-152 for the rest of `pm-workflow`).
