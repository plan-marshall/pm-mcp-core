envelope_version=1
sender_type=plan
sender_id=rb-28-task-queue-and-protocol
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-23T20:04:34Z

```landing-facts
schema=landing-facts/1
plan_id=rb-28-task-queue-and-protocol
epic=pm-mcp-core-m2-m4
pr=#157
merge_state=open
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1989
executor_version=0.1.1989
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:pending,merge:pending
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

Standing rules: r1, R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18.

Guard: none on this harness (unenforced).

Runtime stamp: taken from the setup-time `resolve`. The landing-time `resolve` failed twice with `resolve_bundles_root: could not locate a 'plan-marshall' bundle above …/plan-marshall-manage-architecture/scripts/_cmd_client_build.py`, an environment condition outside the worktree; no model or effort switch happened during the run, so the setup stamp stands (`runtime_stamp_reason: resolve_bundles_root`).

## Deliverables

| Deliverable | Done-when | Verdict | Test | order | Failing run / commit | Commit |
|---|---|---|---|---|---|---|
| 1 — queue store, leases per generation | a lease survives a restart | met | `TaskProtocolSubmitTest#leaseSurvivesRestart` | none | — | 5cf1885 |
| 1 | records carry the generation per attempt and lease | met | `TaskProtocolOfferTest#offersAndLeases`, `QueueTaskRecordCodecTest#queued` | none | — | 4acd226 |
| 2 — protocol | exactly-once submit under the interleavings the specification names | met | `TaskProtocolSubmitTest#exactlyOnce`, `#exactlyOnceUnderInterleavings`, `TaskProtocolInterleavingTest#aSubmitIsReachable`, `#atMostOneClose` | mutation-after | closed-task guard disabled at 78a9b86; `expected STALE_ENTITY but was JOB_SCOPE_MISMATCH`; log `…/pm-core,pm-workflow/maven-2026-10-23-124258.log` | 5cf1885, 20714350 |
| 2 | a late call of a replaced generation is refused | met | `TaskProtocolSubmitTest#lateCallOfReplacedGeneration` | none | — | 5cf1885 |
| 2 | skill delivery per generation | met | `SkillDeliveryTest`, `TaskProtocolRefusalTest#skillsNotLoaded`, `#submitAfterDelivery` | none | — | 1f8d03d |
| 3 — queue task record / machine queue | a machine-scope task is queued and leased in the machine's queue | met | `TaskProtocolOfferTest#machineQueue`, `QueueStoreTest#machine` | none | — | 4acd226, 5cf1885 |
| 3 | a record with a `continuation`, a `suspended` attempt end and `signals[]` round-trips | met | `QueueTaskRecordCodecTest#suspendedWithContinuationAndSignals`, `TaskQueueSchemaParityTest#documents` | none | — | 4acd226 |
| 4 — pause | a task of a paused plan is offered to no worker; a pause re-queues at once with `end: paused` without spending a re-offer; offered again once started | met | `TaskProtocolOfferTest#pausedPlan`, `TaskProtocolReOfferTest#pauseRequeues`, `#pauseRequeuesAtOnce`, `#pauseClearsContinuation`, `TaskProtocolRefusalTest#heldTaskOfPausedPlan` | mutation-after (end and budget) | pause end mutated to `off_track` at 78a9b86; `#pauseRequeues`/`#pauseRequeuesAtOnce` failed; log `…/pm-workflow/maven-2026-10-23-124258.log` | 5cf1885, 1f8d03d |
| 5 — off track | the attempt ends `off_track` and the task is re-offered within its budget; `signals[]` stored | met | `TaskProtocolReOfferTest#offTrackRestart`, `#offTrackShow`, `#signalOnce`, `#signalActionConflict`, `#budgetSpent` | none | — | 5cf1885, 1f8d03d |
| 6 — `worker-task` fixture unit | the protocol is proved against a fixture unit | met | `WorkerTaskUnitFixtureTest#fixtureCompiles`, `#protocolFollowsFixture` | none | — | 784eded |
| 7 — `agent_started` | record and activity entry with `agent_started`/`restart` round-trip with attempt, measurement and instant | met | `QueueTaskRecordCodecTest#agentStarted`, `ActivityEntryTest#agentStarted`, `TaskQueueSchemaParityTest#agentStarted`, `ActivityEntryValidationTest#everyKind` | none | — | 4acd226, 3b603b9 |
| 7 | any other action is refused | met | `QueueTaskRecordCodecTest#agentStartedWithAnotherAction`, `ActivityEntryTest#agentStartedWithAnotherAction` | mutation-after | refusal disabled at 78a9b86; “Expected IllegalArgumentException to be thrown, but nothing was thrown”; log `…/pm-workflow/maven-2026-10-23-124234.log` | 4acd226, 3b603b9 |
| 7 | attempt ends `off_track`, task re-offered within budget, security audit names the executable | met | `TaskProtocolReOfferTest#agentStarted`, `AuditLogSecurityAuditTest#recordsTheDetection` | none | — | 3b603b9 |
| Package bookkeeping | package file and index row removed; retained sections stated in the PR body; links `BROKEN LINKS 0` | met | links check at 20714350 (`BROKEN LINKS 0`); PR #157 body | none | — | 78a9b86 |
| Build | `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am` green | met (local) | 7215 tests, all coverage checks met; 10 integration tests | none | — | 20714350 |
| Sonar | no open PR issue and a green gate before ready | pending | the pull request has not been analysed at 20714350 (no pull_request-event run); the read at `pullRequest=155` is 77% new-code coverage at 7a00f806 | — | — | — |

Per-head test runs: every build above ran at 20714350 (`verify -Ppre-commit` 7215 tests, `verify -Pcoverage` 7215 tests with all checks met, `verify -Pintegration-tests -pl pm-core -am` 10 tests).

## Personas and skills

- Production code: `persona-implementer` (3.1).
- Unit and module tests: `persona-module-tester` (3.2, and the self-review test rounds).
- Multi-module path: `persona-integration-tester` (the integration-test build gate).
- Trust boundary and secrets: `persona-security-expert` (the `agent_started` security audit and the fence).
- Documentation: `persona-documenter` (developer guides, ADRs, traceability).
- Every self-review round and the PR comment triage: `persona-code-reviewer`.
- Domain skills: `skills: none named` in the spec; no surface drift, so no skill was added.

## Hypotheses

- Expected Surface inferred from the `Where` line: **confirmed** — the two production packages were written and the tests, developer docs and ADRs lie on the named surface.
- The package adds INFO/WARN/ERROR messages: **refuted** — it adds no log record, so no holder changed.
- The package makes no HTTP call: **confirmed** — no HTTP client, socket or `cui-http` use.

## Findings

- Self-review round 1: 24 findings (fence read outside the transaction, missing submit skill check, journal write defects, pause window, false docs among them), fixed in 6271b40.
- Self-review round 2: 17 findings (journal per scope, fence and release in one transaction, broken 77% coverage, missing regression tests among them), fixed in 1f8d03d.
- Self-review round 3: 12 findings (7 blocking: journal directory before the lock, the bounded rewrite under the file's own lock, three false statements in `workflow-task.adoc`, a contradictory ADR 002 sentence, the `Attempt.counts` diagnostic, the audit attempt, and the PM-TOOL-1 traceability claim), fixed in 7a00f80.
- CodeRabbit at head 7a00f806: two inline findings — the open-consultation answer check and the conflicting duplicate signal — both fixed in 20714350 and marked addressed; two nitpicks (usage round-trip test, the kind enum parity assertion) fixed in 20714350.
- No pre-existing test assertion was removed or weakened; the formatter rewrites (`assertTrue(x instanceof Y)` to `assertInstanceOf`) are semantics-preserving.

## Reviewers

| Reviewer | Required | Reviewed | Head | History |
|---|---|---|---|---|
| coderabbit | yes (quota-limited) | yes, at 7a00f806 (`COMMENTED`, 2 inline findings) | not the current head 20714350 | triggered at 17:58 on #155; reviewed in 11 minutes; re-triggered at 18:12 → “already reviewed the last commit”; on 20714350 the bot is rate-limited (next included review in ~36 minutes) and has not reviewed the current head |
| sourcery | no | refused | — | “your pull request is larger than the review limit of 150,000 diff characters” |

Standing review authorization does not apply: the current head has no review from the required reviewer, and none of the other conditions (all findings handled on the current head, a re-trigger after the last push recorded, every other gate green) is met.

## Operator questions

1. “The lane allows 3 self-review rounds … do you want a 4th review round before I open the PR?” — options {Proceed to the PR, Run a 4th self-review round} — selected **Proceed to the PR**.
2. “PR #155 is blocked by a CI infrastructure condition … How do you want to proceed?” — options {Close #155 and open a new PR, I will trigger the PR run, Merge with the push-run sonar failure} — selected **Close #155 and open a new PR**.
3. “Closing #155 and opening #157 did not produce a pull_request-event Maven run … What now?” — options {Stop and report blocked, Merge despite the red sonar check, Wait for GitHub Actions to recover} — selected **Wait for GitHub Actions to recover**.
Free-text operator messages: none.

## Rule check

- `guard: none on this harness (unenforced)`.
- Rule breaks: shell `grep` was used a few times to read minified GitHub logs and the minified JaCoCo XML instead of the file tools, and one `python3 - <<EOF` heredoc was issued once (a no-op). Both are against section 1's shell rules and are recorded here.
- No repository file was written in the main checkout after the first misplacement was moved into the worktree (`git status --porcelain` in the main checkout has been empty since).

## Build server

- Setup preflight: `ready`.
- Fallback: `executor_absent_from_worktree` — the worktree has no `.plan/execute-script.py`, so every build ran in-process with `--execution-mode in_process`.
- The first `verify -Pcoverage` hit the learned 860 s bound (`timeout_source: learned`) and was re-run with `--timeout 1800`, then succeeded in 615–853 s.
- No `killed` job.

## Residue

- GitHub Actions created no pull_request-event Maven run for head 20714350 (the last pull_request run anywhere is 17:59:43, another PR); the only suite for the head is the push run, whose `sonar-build` fails on the organisation's “not allowed to access data from non main branches” gate read. #157 stays open and red on that check.
- The PR-scoped Sonar quality gate reads the previous head 7a00f806 at 77.0% new-code coverage; the current head has not been analysed, so whether the added validation and refusal tests lift it above 80% is unconfirmed.
- CodeRabbit has not reviewed the current head (rate limit); no review of #157 exists yet.
- The branch is not merged and the worktree is not cleaned up; the main checkout has not moved and no merge was attempted.
- The specification block in `workflow-dsl/02-units-jobs-decisions.adoc` omits the `task.awaiting_answer` and `task.suspended` states the fixture adds; a documentation change is owed.
