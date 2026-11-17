envelope_version=1
sender_type=plan
sender_id=rb-v2-31-supervisor-calls-and-pass
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T09:21:40Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-31-supervisor-calls-and-pass
epic=pm-mcp-core-m2-m4-v2
pr=#472
merge_state=open
cleanup_owed=true
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:not-started,merge:blocked
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Landing — PLAN-V2-31: the supervisor's inbound calls and its supervision pass

Standing rules: read at revision **r7** (the spec's First Instruction names r4; r4→r7 amends R05 and R12, adds R20 and
R21). Rules this plan applied: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18,
R19, R20. R15 does not apply (no `pm-workflow` code). guard: none on this harness (unenforced).

**The pull request is open and deliberately NOT reported ready to merge.** The operator decided on 2026-11-17 to ship
Deliverable 2 exactly as specified and report the blocker below. `merge_state=open`, `cleanup_owed=true` (the branch and
the worktree stay, because nothing was merged).

## Deliverables

Deliverables 1–5 are met as written; the plan's *premise* about the end path is refuted (B1), which is a spec-level gap,
not a missed criterion.

**D1 — `SupervisorCalls` implements `WorkerCalls`** · met · order: `none` (the Done-when has no "fails at" clause).
All six methods are `@Override`-implemented in `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java`
and one test per call exists. The mapping is as specified; two documented refinements: a heartbeat beyond the first call
settles the acknowledgement through `LossDetection.acknowledged` (the deadline an offer arms would otherwise never be
cleared — the shipped `claude` value is 15 s), and `acknowledged` subsumes the `called` record it names, so
`LossDetection.called` now has no caller. Commit `285b591a`, fixes `8a18940f`, `ab1849ec`, `e3867aeb`, `a987dd83`.

**D2 — `SupervisionPass.run()` exists and is deterministic under the virtual clock** · met as specified · order: `none`.
`SupervisionPass.java` takes exactly one `clock().instant()` per pass, starts no thread and schedules nothing; the replay
test asserts the same entries twice. Commits `3c399198`, `118d788f`, `b99b45ce`.

**D3 — the five scenario test groups pass beside every existing `runtime.worker` test** · met · order: `none` (green run).
`SupervisorCallsTest` (18 tests) and `SupervisionPassTest` (8 tests) run inside the whole-module run on the final head
(`b99b45ce`: job `4871cc30…` pre-commit, 10 958 tests). The new classes were also shown to fail under a deliberate
mutation of the committed fix (pump call and guard clear removed → 3 failures, job `e6705d35ffaa453a96448679c3253a72`;
`waitClosed` removed → 1 failure, job `a4d0e0d219ef4610a5f3c64de9d442f0`), both mutations reverted. Commits `278d17cd`,
`673f2dae`, `089a1081`.

**D4 — the ADR, the guide and the Traceability lines** · met · order: `none`. ADR `runtime-worker/014` (next free number
at `origin/main`, re-checked after the merge), the two guide passages extended, and the `PM-WORK-3` / `PM-WORK-7` lines
updated. Commits `e3420dd4`, `45c80fcb`, `7b2be925`.

**D5 — the owed rest in the PR body and the guide** · met · order: `none`. The PR body carries it, and the guide's *Ports
and the Assembly Adapter* section states it; the work packages `worker-activity` and `question-continuation` stay open and
no row, file, specification section or watch item was removed. Commit `e3420dd4`.

Per-head test runs (module tests) at every pushed head: `d91d879007e74980b985312492640081` (e3420dd4), then the
`verify -Ppre-commit` runs on each later head (`dd5b7b67…`, `3eab14ed…`, `cbcd2fc7…`, `4871cc30…`, all green).

## ⛔ The blocker (B1) — recorded, not fixed

`SupervisionPass` completes an ended worker's end only once its job record is settled, and `WorkerEnding.exited` writes
the terminal worker state through `WorkerStateWriter.over` → `JobRecordStore.writeWorker`, which refuses a settled record
(`JobRecordStore.java:328-332`). The runner writes the fate at settlement (`SettlementStep.java:207`) and the settlement
carries whatever worker block the record held, so the store's contract requires the end path's write *before* the fate —
while the fate is what tells the pass the exit happened. Against the landed writer the first pass over an ended worker
throws, the generation never becomes terminal, later passes throw again, the generations after it go unobserved, and each
retry repeats the end path's pre-write side effects. The fixture's mock `WorkerStateWriter` hides it.

The trigger and the write order need a specification amendment (a second exit observable, or a store-compatible terminal
write). Owner: the assembly or a follow-up plan. Operator decision 2026-11-17: ship as specified, report it.

## Other open items (reported as open, not settled)

1. A `fresh` worker's normal end is recorded `LOST` (`EndCause.TASK_DONE` has no production caller); a worker that dies
   while it holds the lease records `exited_without_submit`, one whose task closed first records no attempt end.
2. `harnessEnrolled` clears only the roles the registry holds on the harness — narrower than Deliverable 1.
3. The start-failure count is owed to a follow-up: the pass is the only reader of the settled record.
4. Owed cleanups: `forget` on registry retirement for the pass's pump map and finished-attempt set and the calls' maps;
   `AgentStartedDetector`'s process trees; ADR `runtime-worker/012`'s `answerDelivered` entry.
5. The `waitOpened` finally's exceptional path is untested (`WorkerRecycling` is final, so no throwing double).

## Personas and skills

Per step: `persona-implementer` for the two production classes, `persona-module-tester` for the two test classes,
`persona-code-reviewer` for every self-review round. The spec declares no `Persona:` and no `Skills:` lines
(`skills: none named`); R20's coding skills were loaded as standards (cui-testing, java-core, java-lombok,
java-null-safety, javadoc, junit-core, plus the cui-logging and cui-http-testing pointers) and applied. No skill was added
for surface drift; the one undeclared class edit (`SupervisorEnvironment.StartGuard.firstCall`) is a seam addition, not a
module.

## Hypotheses

- `queueTaskId` is the candidate task the transport would offer, and no candidate means "no task queued" — **confirmed**
  (`02-task-protocol.adoc:32`, § `pm_wait`: the tool takes no argument and the server decides the offer;
  `WorkerCalls.java:39`; a real id is `qt-…`). Recorded in ADR 014.
- The supervisor learns of an exit from the job record alone — **confirmed** (`SettlementStep.java:207` writes the fate
  at settlement).
- The four new files are the whole code surface — **confirmed**, with the `StartGuard.firstCall` seam addition recorded
  as a surface delta (R19).

## Findings

Self-review ran **four** rounds (three permitted; the operator asked for a fourth). Rounds 1–4 raised 2 blockers, 4
majors and about 20 minors/nits; every code-level finding was fixed, every spec-level one is reported open above.

- Round 1: B1 (above); B2 the acknowledgement deadline was never cleared → fixed; M1 a closed task made the pass throw
  on every later pass → fixed; M2 terminal generations were still "known" → fixed; minors (weak replay anchor, Javadoc
  claims, validate-before-mutate) → fixed.
- Round 2: A a warm worker's first call armed an acknowledgement it does not owe → fixed; B `waitOpened` mutated before
  validating → fixed with top-level validation and a `finally`; D the attempt end after the terminal write → moved before;
  E `forget` left an armed detection → cancelled; F `Optional` parameters (R20) → `@Nullable`; C `harnessEnrolled`'s
  narrower role set → recorded.
- Round 3: C-1 a transport call could land after the terminal write → the completion now holds the calls monitor; C-3 a
  first call that is a wait did not record the first call → fixed; C-4 the acknowledgement was rebased instead of cleared
  → fixed; C-7 null validation → added; C-8 idle count dropped on the recycle branch → persisted; test gaps → filled.
- Round 4: N1 a retried completion re-pumped the log and appended a second attempt end → fixed (finished-attempt set);
  N2 the timer thread blocked by a log read → the tail pump moved outside the monitor; N3 the recorder's closed-task
  refusal escaped `run()` → caught narrowly; N4 the `@Nullable` return (R20) → `Optional`; N5 the attempt-end table was
  not tested cell by cell → a table test; N6 the idle count had no assertion → added; D1–D4 doc claims the fixes made
  false → corrected.
- After the first CI run: `main` advanced (PLAN-V2-153) and made the git-metadata enumeration a required member of a
  worker's job record → the fixture's record now carries `unenforced` (commit `089a1081`).
- After the second CI run: two SonarCloud findings on the new class (S135 continue count, S6213 restricted identifier
  `record`) → refactored into an `advance` step, `record` renamed (commit `b99b45ce`); the second CI run was also red on
  `sonar-build` for a Maven Central HTTP 429, cleared by a re-run.

## Reviewers

- `coderabbit` (required, quota-limited, `review_complete_after_handling`) — **not triggered**: the pull request is not
  reported ready (operator decision), so no review quota was spent.
- `sourcery` (optional) — not triggered; GitHub reports the Sourcery check as skipped.
- `review-gate` on the final head `b99b45ce`: `unresolved_review_threads=0`, `review_body_findings_total=0`,
  `review_body_findings_handled=0`, `merge_allowed: true` — the surfaces are clean because no reviewer ran, not because
  findings were handled.

## Operator questions

1. "The settled record blocks the end write" — options: ship as specified and report / add a second exit observable /
   relax the store's refusal / stop and return the plan. Selected: **ship as specified, report the blocker**. (Asked
   twice: the first call's answer was invalidated by an operator "re-ask", so it was re-asked and answered again.)
2. "Self-review round limit" — after three rounds with items open, options: continue with a fourth round / accept the
   open items and open the PR. Selected: **continue with a fourth round**.

Free-text operator messages: "re-ask" (after the first B1 question).

## Rule check

- No rule was knowingly broken. `guard: none on this harness (unenforced)` — nothing enforced a tool-call guard, so no
  guard-log line exists for this run and "all kept" is not claimed. The run's own actions were reviewed instead: every
  file read and search used the file tools; no shell file operation, no `cat`/`grep`/`find`/`sed`, no pipe or `&&`; no
  write outside the worktree except `.plan/temp/` and the one CI body slot; every script through the executor; every CI
  action through `ci`; every build through the resolved `build` map.
- Routed builds (all `daemon_longpoll`): test-compile ×2, targeted test runs ×8, `verify -Ppre-commit` ×5, `verify` ×2,
  `verify -Pcoverage` ×2, `verify -Pintegration-tests -pl pm-runtime -am` ×2. Two anomalies: one bound expiry
  (`timeout_used_seconds 461`, learned bound) on a job re-submitted while another held the directory, and queue waits of
  139 s/267 s/319 s behind a busy directory. No `degraded` fallback and no launch-fault retry; no `killed` job.
- Operator messages: the two questions above and one "re-ask". No model or effort switch during the run.
- Executor skew at start: `match` (executor 0.1.2135, lane text 0.1.2135). The spec copy read: `spec_source=ledger_worktree`,
  no `spec_differ` marker.
- The merge with `origin/main` (commit `3e5ebfbf`, for the git-metadata change) is the only base-branch merge; the
  build gate and CI were re-run on the merged head, and a fresh links check (`BROKEN LINKS 0`) was taken after it.
- The delivered mailbox reported `mailbox_state=no_mailbox` at all three reads (setup, before self-review, before the
  enqueue) — not an empty mailbox, and nothing was delivered. No inbox message was owed to it.

## Build server

Setup preflight: `ready` (registered). Every build routed (`mechanism=daemon_longpoll`); no in-process fallback. The two
anomalies above are the only deviations. No killed job.

## Residue

The blocker and the five open items above. The branch `feature/v2-31-supervisor-calls-and-pass` and the worktree remain
(`cleanup_owed=true`); the pull request is #472 and stays open, un-reviewed and un-merged.
