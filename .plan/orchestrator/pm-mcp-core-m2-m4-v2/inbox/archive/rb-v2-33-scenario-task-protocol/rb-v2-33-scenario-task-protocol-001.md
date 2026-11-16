envelope_version=1
sender_type=plan
sender_id=rb-v2-33-scenario-task-protocol
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-16T10:13:56Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-33-scenario-task-protocol
epic=pm-mcp-core-m2-m4-v2
pr=#457
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=6
review_body_findings_handled=6
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-33 — the scenario harness runs the task protocol

Standing rules: read at this run, revision **r7 (2026-11-10)** (the spec's First Instruction names r4; the file's current
revision binds). Rules applied: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18,
R19, R20 (R12 no message expected, R14 and R16 out of scope).

PR #457, merge commit `688c0a421a15240d56b5d295f14101deabcd7a09`. The spec was read from the ledger worktree
(`spec_source=ledger_worktree`, no `spec_differ` marker). The executor-skew verdict was **match** (executor 0.1.2135,
lane 0.1.2135).

## 1. Deliverables

Every clause of the spec's one Done-when paragraph, as written.

| Clause | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|
| A protocol-worker scenario (wait, offer, acknowledge, submit, closed outcome from the record) replays twice to byte-identical stores | met | `ScenarioReplayRunnerTest$ProtocolWorker.shouldRunTheProtocolWorker`, `ScenarioDeterminismTest.shouldReplayTheProtocolWorkerToSameBytes` | mutation-after | the committed fix mutated (the generation counter made static, so the second replay starts another generation): `ScenarioDeterminismTest.java:124`, the snapshot comparison, job-log `0ad1d010073a4ab1834d3ee8e4c97043.log` | `bc364164` |
| The existing scenarios replay unchanged | met | the whole pre-existing `ScenarioReplayRunnerTest` suite, green at `445be47a` (2530 tests in `pm-workflow`) | none | no golden-byte comparison exists; the unchanged tests and their assertions are the evidence | `bc364164` |
| `assert_worker_state` and `assert_refused` pass on a state the run produced and fail with their line on one it did not | met | `ScenarioReplayRunnerTest$ProtocolWorker.shouldAssertRefusalsAndStates`, `shouldFailAnAssertRefusedOnAnotherReason`, `shouldFailAnAssertWorkerStateOnAnotherState` | test-first | the reader refused `worker_wait` before the fix: `ScenarioScriptTest.shouldReadTheSupervisionAssertions`, "line 2: 'worker_wait' is not supported by this harness yet", job-log `afec9f1db9204734b271614320296da4.log` (Tests run: 60, Failures: 2) | `bc364164` |
| The consultation scenario passes with the reference refusal | met | `ScenarioReplayRunnerTest$Consultation.shouldRefuseTheSubmitWithoutTheReference`, `shouldAcceptTheSubmitThatNamesTheReference` | mutation-after | the committed fix mutated (`validateSubmit` check removed): the submit was accepted, job-log `170f1443da72437da659e700e937e651.log` | `bc364164` |
| The directive table and the tests that pin it name only unbuilt directives | met | `ScenarioScriptTest$Refused.shouldNamePackageOfRefusedDirectives`, `ScenarioScriptTest#refusedDirectives` | none | — | `bc364164` |
| The developer guide, `package-info.java`, one ADR and the specification sentence are written | met | `pm-workflow/doc/developer/workflow-scenario.adoc`, `package-info.java`, ADR 009, Workflow DSL 07 line 105 | none | — | `1a773dfd` (+ `48e1189f` for the review amendments) |
| The package file no longer lists the work this plan did | met | `doc/plans/supervision-scenarios.adoc` bullet 1 reduced to the worker faults and the crash, with the recycling/guard ownership stated | none | — | `1a773dfd` (+ `48e1189f`) |
| Sonar: no open issue other than one dismissed, gate green, before the PR is reported ready | met | `read_open_issues` at `445be47a`: `open_issue_count` 0, `count_status` confirmed; `gate-status`: `OK` on all five conditions | none | — | — |
| Build: `verify -Ppre-commit`, `verify` of `pm-workflow`, `verify -Pcoverage`, the integration tests of every changed module | met | gate `10681` tests (job-log `8a2499f47c6f4cb4aa62047764b71914.log`), `verify -pl pm-workflow` green at `1a773dfd` (job-log `79dfa726e5174316b9536cd3a6f96d0e.log`), `verify -Pcoverage -pl pm-workflow` green at `445be47a` (job-log `9ff0f5a3e62f48c1b1b08a3153414f32.log`) | none | integration tests: `pm-workflow` declares no `integration-tests` profile and no changed code is in `pm-core` or `pm-runtime` — none applied | — |
| Links: the links check (R17) against the sibling at its `origin/main` | met | `trace.py` after `fetch origin main`: `BROKEN LINKS 0` | none | — | — |

The protocol-worker determinism proof also carries a second, earlier evidence run: the two-replay comparison of status
**and** queue bytes is the assertion that failed under the mutation above and passes at `445be47a`
(`pm-workflow` test run at `1a773dfd`, job-log `74c8cfce158544a39744f53f5b660f45.log`).

Per-head test runs: `module-tests` (`test -pl pm-workflow`) at `1a773dfd` (2528 tests, job-log
`74c8cfce158544a39744f53f5b660f45.log`), at `34f354d1` (2528, job-log `abc7957a6f4b46b9b9577b70a8570e25.log`), at
`71cb8830` (2529, job-log `6ac07918beaf4f1798338a19168fee0e.log`), at `48e1189f` (2530, job-log
`527ae356233d4618a135efa1864dc1dc.log`) and at `445be47a` (2530, CI green; the local gate ran 10681 tests). `test-compile`
is part of every `test`/`verify` run.

## 2. Personas and skills

- Production-code work: none (the plan is test scope; "No production code" holds).
- Unit and module tests (D1-D4): `persona-module-tester` and the coding standards of R20 that were read:
  `java-core`, `junit-core`, `javadoc`, `java-lombok`, `java-null-safety`, `cui-testing` (SKILL.md and the core
  standards). `cui-http-testing` was not needed (no HTTP).
- Documentation (D5): `persona-documenter` standard; the guide and ADR follow the existing structure.
- Self-review and the review triage: `persona-code-reviewer` (the self-review round-1 sub-agent and the triage of the
  CodeRabbit/Sourcery findings).
- The spec declares `skills: none named` for every deliverable; the R20 skills above were added from the standing rules.
- Surface delta: one new class the spec's Expected Surface did not name, `scenario.ScriptedProtocolWorker` (R19 allows
  a new class in a shared package; it is listed in `doc/Traceability.adoc`). The rest of the footprint is the eight
  declared scenario files, `pm-workflow/src/test/resources/hand-written-doubles.txt` (the scan's baseline, with ADR 026
  amended), the developer guide, ADR 001/008/009, the specification sentence, `doc/Traceability.adoc`,
  `doc/adr/026` and the package file.

## 3. Hypotheses

- `QueueStore` and the lock manager run in the replay's temporary directory without git and without a real clock —
  **confirmed**: `pm-core/.../queue/QueueStore.java:68` (a directory, a queue `LockKey` and a `LockManager`; no git)
  and `pm-workflow/src/test/java/.../task/TestLocks.java:24` (no OS lock; the scenario package writes its own).
- The engine's `QueueTaskCreator` port can write the `QueueTaskRecord` shape `TaskFixtures.queued` builds from the
  `CreateRequest` — **confirmed**: `pm-core/.../spi/QueueTaskCreator.java:37` and
  `pm-core/.../queue/QueueTaskRecord.java:71` (builder); the entity key is the state's entity kind
  (`WorkflowEngine.java:914-926`).
- The closed set of refusal wire names for `assert_refused` equals the closed set of `TaskRefusal` — **confirmed**:
  `pm-workflow/.../task/TaskRefusal.java:17-32` (seven constants, derived at run time in `ScenarioDirective.REFUSALS`).
- Regression scenario 14 needs no more of `ConsultationService` than `open`, `answer` and `validateSubmit` —
  **confirmed**: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc:132` (item 14 names the
  consultation answered through the asker's next wait and the submit that omits the reference);
  `ConsultationService` offers exactly those three at lines 134, 205 and 376.

## 4. Findings

Self-review round 1 (sub-agent, `persona-code-reviewer`, on `34f354d1`): 26 findings, no blocker. Two were real
defects and were fixed: an accepted `worker_do ... infeasible` closed the queue task but never settled the engine
(now any accepted call that closes the task settles it; test `shouldCloseTheTaskInfeasible`), and the specification's
own `worker_do ack {}` example was refused because the implementation required a role (the role-less form is now
accepted and targets the worker of the most recent `worker_wait`). The other findings: documentation made false by the
change (the "not an assertion" sentence, the fence claim, the `queued`/`fenced` wording, the JSON member list, the ADR
id scope, the `describe(Answered)` prose), the consultation determinism exception stated precisely, the package file's
ownership of recycling and the start-failure guard, the `fenced` derivation now from the fence document, the
consultation test now asserting the asker's re-offered task, the queue-task class derived from the role, `WorkerDo`
reduced to two components per R20, and the read-time `worker_wait` ordering. One nit was declined as a preference (the
defensive null guard stays). The review record is in the progress file.

CodeRabbit at `71cb8830`: five inline findings and one nitpick, all fixed in `48e1189f` and each answered and resolved
on its thread:

| Finding | Disposition |
|---|---|
| inline-1 — `CONSULT_ROLES` copies the unit's `consult role` clauses | derived from `StateBehavior.ModelJob.consultRoles()` of the unit's states (`48e1189f`) |
| inline-2 — `worker_wait` before task creation in the example | the example waits after `complete_job` (`48e1189f`) |
| inline-3 — `assert_refused` not required to be adjacent | the reader now requires the directly preceding `worker_do` of the protocol; test added (`48e1189f`) |
| inline-4 — worker-action vocabulary duplicated | one `action(String)` parser, the form decided by arity (`48e1189f`) |
| inline-5 — new hand-written doubles without an ADR 026 amendment | ADR 026 amended with the two declarations and their S2/F3 reasons (`48e1189f`) |
| nitpick-1 — new assertions without messages | every new assertion carries a message (`48e1189f`) |

Sourcery at `71cb8830`: "Approved", no finding.

## 5. Reviewers

| Reviewer | Kind | Head reviewed | Outcome | History |
|---|---|---|---|---|
| coderabbit | required, `quota_limited`, `review_complete_after_handling` | `71cb8830` (marker `kind=reviewed`, `coveredCommitId=71cb8830`) | reviewed | triggered on `1a773dfd`, refused on quota at `08:25:25Z`; its included review arrived after the `34f354d1` push and reviewed `71cb8830`; no re-trigger after later heads (the review is the review at any head, findings handled) |
| sourcery | optional | `71cb8830` | approved | triggered on `1a773dfd`, refused on quota at `08:25:48Z`, reviewed `71cb8830` |

Standing review authorization was not used: the required reviewer holds a qualifying review and every finding is
handled, so `review-gate` reported `merge_allowed: true` with `required_reviewer` coderabbit satisfied.

## 6. Operator questions

None asked. No question was needed: the quota policy's steps 1-3 were never reached (the required reviewer reviewed
before the 90-minute wait elapsed), no rule had to bend, and no operator decision was requested. Free-text operator
messages: none.

## 7. Rule check

- Guard: **none on this harness (unenforced)** — `resolve` reported `guard: none`; no guard log exists.
- The run's own breaks, stated rather than hidden:
  - two shell `ls | head` calls and one shell `grep -c` and one shell `diff` for file inspection, where the rules say
    the file tools; and the one `git -C <sibling> fetch` plus the `trace.py` call of R17, which the spec's Done-when
    names outside the executor.
  - three edits were first issued against the main checkout by a path mistake: one in `ScenarioScriptTest.java`
    (immediately reverted before any other action, `git status --porcelain` empty), one in
    `doc/plans/supervision-scenarios.adoc` (the tool applied it to the worktree; the main checkout was verified
    untouched), and one `diff --stat` read. No commit carries a main-checkout edit.
  - every build was routed to the daemon (`daemon_longpoll`, 29 builds) and every script went through
    `.plan/execute-script.py`.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker.
- Executor skew: `match` (0.1.2135 / 0.1.2135).
- Contradiction of the repository `CLAUDE.md` with a standing rule: none found.

## 8. Build server

Setup preflight: `ready` (registered, version "1"). Builds by mechanism: `daemon_longpoll` 29, `in_process_fallback` 0.
No build fell back, was degraded, needed a launch-fault retry, or was killed. `runtime_stamp_reason`: none (the stamp's
`plugin_version` is `unknown` because the seam returned none; `harness`, `model`, `effort` and `executor_version` were
read).

## 9. Residue

- **Lead (no production code changed):** `ConsultationService.freeId()` draws the consultation's queue task id from
  `QueueTaskId.random()`, so a consultation scenario is not byte-identical across replays. The harness discloses the
  exception in ADR 009 and the guide, asserts behaviour only, and this landing reports it. A later change should give
  the service a counter or a seam.
- **Owed to a later plan:** the worker faults, `restart_runtime`, the fenced late call as a scripted step, and the
  fault matrix are PLAN-V2-49's; the recycling and start-failure scenarios are `pm-runtime`'s, as the package file now
  records.
- **Surface delta:** `scenario.ScriptedProtocolWorker` (new file), `hand-written-doubles.txt` baseline plus the ADR 026
  amendment, and the shared bookkeeping of R05/R08/R19.
