# Landing Analysis: PLAN-64 — Remove the concepts the specification dropped and detect stalled scopes

epic: pm-mcp-core-m2-m4
workstream: WS-02
pr: #122 (https://github.com/plan-marshall/pm-mcp-core/pull/122), merged as squash `c6da0e2` through the merge queue on 2026-10-21; follow-up #127 merged as `c7780a0`

> Landing record for one shipped plan. Lives at `landings/PLAN-64.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `rb-64-removed-concepts-001.md` (`kind: landing`, sender `rb-64-removed-concepts`, runbook lane on opencode, model `deepseek-v4.1-flash`, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`; `surface_delta` unmeasured (neither side supplied).

## Deliverable Fidelity vs Spec

The landing counts eight deliverables (`deliverables_total=8`, `deliverables_done=7`): seven met, D7 reported as not fully met.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Scope session log removed | shipped-as-specified | `ScopeSessionLog`, `ScopeSessionRow`, `ScopeSessionRowCodec` and both tests are deleted in the squash; `git grep` over `*.java` at `origin/main` finds no `ScopeSession*`. The `session` member leaves `PendingEntryRecord`/`PendingEntryCodec`; `PendingEntryCodecTest` carries `membersWithoutSession`, `withoutSession`, `sessionMemberIsRefused` |
| D2 Session generation record removed | shipped-as-specified | the seven files of `core/queue/` and the two tests are deleted (`git show --name-status c6da0e2`: 18 `D`); no `SessionGeneration`, `GenerationCheck` or `FencedSessionGenerations` left in any Java file at `origin/main`. The "fails on the wrong choice" matrix case exists only as the uncommitted branch-point guard run (the Done-when forbids a committed test that names the concept) — plan-reported, not observable now |
| D3 Fenced-generation sweep step removed | shipped-as-specified | `FencedSessionGenerationsSweep` and its test deleted; `PeriodicSweepTest.shouldRegisterOnlyResidueSteps` exists; `PM_RUNTIME-24` is gone from the holder and `doc/LogMessages.adoc` |
| D4 Audit category `identity` removed | shipped-as-specified, with an addition | no `CATEGORY_IDENTITY`, `entryClientChanged`, `Category.IDENTITY` left; `AuditRecord.Scope.ENTRY` exists (`AuditRecord.java:153`). The addition is the operator's decision on the contradiction between the package bullet and the parity test (plan-reported: "Re-enable the parity test"), outside the spec's wording; `AuditSchemaParityTest` and `AuditCodecTest` are in the squash |
| D5 Operator command of a refusal removed | shipped-as-specified | no `operatorCommand` left in any Java file; `JobRefusalTest` is new; the enrolment refusal detail is built from the project and outcome code, as the PR body says |
| D6 Directive `expire_question` removed | shipped-as-specified | the directive, runner branch and `ExpireQuestion` are gone; `ScenarioScriptTest.shouldRefuseUnknownDirective` takes four names including `expire_question`. The only `expire_question` left in Java is that refusal test |
| D7 Documents | not fully met (agrees with the landing) | the `core.queue` guide and `doc/plans/removed-concepts.adoc` are deleted, its row left `doc/plans/README.adoc` (+3/-9), ADRs core-queue 001/003 and core-store 008/009 carry the superseding, 002 stays Accepted. The roadmap `_Remaining_` update in `plan-marshall-documentation` is owed (not in this repository). The Done-when clause "no document names the removed concepts" is not met as written and the landing says so: six `.adoc` files (the four superseded ADRs, `workflow-scenario/004`, `client-profiles/01-profile-schema.adoc`) still contain the words — the last one is an unrelated "session log in a harness data home" |
| D8 Stalled-scope detection | shipped-as-specified at the seam | `StalledScopeDetector` is new (`runtime.start`); `THRESHOLD = Duration.ofSeconds(600)` matches `timeouts/02-runtime-leases-operator-web.adoc` ("`600` since the last transition or task progress"); `AuditEvent.planStalled` and `PLAN_STALLED` exist; ADR `runtime-start/015` is in the squash. No production caller exists, as the landing says; `pm-mcp-server` is untouched |

Hypotheses of the spec, as the landing settles them: the Expected Surface was inferred from the package's `Where` line; the lane confirms it against the code, and the delta below agrees in the large (67 of the 82 merged files are covered by a declared entry). "The package adds no log record" holds in the sense that matters: one record is removed (`PM_RUNTIME-24`), none added — `RuntimeLogMessages.java` and its test are in the squash for the removal. "No HTTP call" holds (no HTTP type in the 82 files).

Surface delta (`corpus declaration-currency`, footprint base `c7780a0`, spec excluded): 82 files merged. Against the spec's `## Expected Surface` (64 entries, directories counted): 67 merged files are covered by a declared entry, 15 are undeclared, 10 declared entries are untouched.
- Undeclared: `doc/LogMessages.adoc` and `RuntimeLogMessages.java` + `RuntimeLogMessagesTest.java` (the removed record — the spec says the holder is deliberately not declared), `doc/Traceability.adoc`, `doc/adr/README.adoc`, `doc/plans/cleanup.adoc`, `doc/plans/start-source-intake.adoc`, `doc/plans/task-queue-and-protocol.adoc` (the rows and files that named the package), `pm-core/README.adoc`, `AuditCodecTest.java` and `AuditSchemaParityTest.java` (D4), `JobRefusalTest.java` (new, D5), `ScenarioFixtures.java` (D6), and two workflow ADRs (`workflow-engine/005`, `006`, documentation edits; their content was not read here).
- Declared and untouched: `PendingEntryRecordsSweepTest.java` (a hypothesis: the comment edit found by text match was not needed), `WorkflowStatus.java` (read only), and eight specification/watch parts (`hypermedia-format/01`, `model-work/02`, `runtime-lifecycle/01`, `store-schemas/01`, `store-schemas/12`, `timeouts/02`, `runtime-lifecycle/02`, `implementation-watch/runtime-lifecycle.adoc`). They are the spec's references to the already-corrected specification, not edit targets; the squash changes only `workflow-dsl/07`. Corroborated by the squash's file list.

## Removed-Assertion Analysis

The squash deletes about 4,500 lines and adds 1,600. Deleted tests: `SessionGenerationCodecTest`, `SessionGenerationStoreTest`, `ScopeSessionLogTest`, `ScopeSessionRowCodecTest`, `FencedSessionGenerationsSweepTest` — every one tests a class the same squash deletes. Modified tests that lose assertions: `ScenarioScriptTest` loses `new ExpireQuestion(1, null)` (the record is gone) and gains the four-name refusal case; `PeriodicSweepTest` changes to the single-step expectation (the failing line quoted by the landing, `was [PENDING_ENTRY_RECORDS, FENCED_SESSION_GENERATIONS]`, is the old expectation); `PendingEntryCodecTest` gains the three cases named above, and `PendingEntryStoreTest` is in the squash (its diff was not read). The plan-reported count "6758 → 6711 tests" agrees with deletions outweighing additions. No assertion was weakened to pass: each removed assertion belongs to a removed concept. The follow-up #127 changes 14 lines of `ServiceUnitInstallerTest` and nothing else (see below).

## Metrics and Anomalies

- Tokens: `unknown` (opencode measured none). The seventh runbook landing on opencode carrying it (after PLAN-78, 88, 81, 90, 82, 83); the one missing key of `landing-check`.
- Tests (plan-reported, not re-run here): 6738 in each of `verify -Ppre-commit`, `verify`, `verify -Pcoverage` at `da892e9`; integration 10 (`pm-core`) and 27 (`pm-runtime`); coverage pm-core 96/92, pm-runtime 89/86, pm-workflow 94/87. CI at the merged head is green (below). The lane's one excluded test, `SubprocessJobRunnerTest.escalated` (inotify limit), is the operator's machine, the same as in PLAN-83's landing; not tracked.
- Deliverable order (plan-reported): test-first for D1–D6 and D8 with failing run ids, none for D7. The progress file `.plan/temp/rb-64-removed-concepts/progress.md` agrees with the landing on gates, merge of `73ec37a`, the PR creation at `b91ffed` and the review triggers; the commit shas the landing names are on the PR branch (the squash hides them on `main`).
- Rule breaks (plan-reported, not observable on an unguarded harness): `sed -i` used twice for repetitive edits and one chained-command exploration call (shell rules, section 1); `trace.py` link check not run because the operator's permission to read the sibling checkout was read-only. The landing does not report the stale-executor build-name defect that PLAN-90, 82 and 83 hit, although PLAN-64 was emitted on the same executor (pinned 0.1.1961); its rule check says builds and scripts ran through the executor only.
- The lane asked the operator two questions (read access to `../plan-marshall-documentation`; the D4 contradiction); both answers are in the landing and the spec's operator-decision channel.
- The PR body and the squash message still say ADR `runtime-start/014` for the stall design; the merged tree has it at `015-stall-derived-on-read-one-record-per-episode.adoc` and every in-tree link says 015 (`runtime-start.adoc:128`). 014 is PLAN-83's ADR. A stale sentence in a squash message, not in the tree.
- Self-review hit the three-round limit (`self-review: budget-exhausted`); the landing says every round-3 item was fixed afterwards and the later CodeRabbit reviews were clean.

## Routing and Merge Behavior

- Review: `coderabbit` (required) reviewed eight heads and ended at `da892e9` with no actionable comment; `ci pr comments` shows 18 inline threads, all resolved. `sourcery` (optional) refused on its review budget. The lane triggered the bot after each push.
- CI/merge (read from the PR through `ci`): `state: merged`, merge commit `c6da0e2` on `origin/main`; `checks status`: `overall_status: success`, 30 checks including both `build` jobs, `integration-tests`, `quarkus-alignment`, `SonarCloud Code Analysis`.
- SonarCloud: the quality gate for #122 is `OK` (new coverage 98.7%). The API still lists 18 open issues on the pull request, which the landing does not mention ("quality gate passed" only): `java:S5778` x5 and `S7467` x3, `S6213` x2, `S1612` x2, `S1192` x2 (CRITICAL, `AuditEvent.java:305-306`, duplicated `DETAIL_STATE`/`DETAIL_CAUSE` literals), `S1172` (unused parameter `projectId`, `WrapperVerification.java:52`), `S3400` (`StalledScopeDetector.java:146`), `S9358`, `S8786` (a super-linear regex, `StalledScopeDetectorTest.java:558`) — most in the new `StalledScopeDetectorTest`. Untriaged; a Watch.
- Follow-up #127 (`c7780a0`): the landing explains the uncommitted `ServiceUnitInstallerTest.java` edit seen in PLAN-64's worktree (the prior watch): the build's rewrite appeared after #122 was enqueued; the operator wrote "Land it with a small merge using skip-bot-review as pr label and continue" (plan-reported). The PR was verified here: merged, 31 checks success, and `git show c7780a0` is exactly 14 lines `throws IOException` → `throws Exception` in one file, no other change. The file is PLAN-83's (#125), so the finding is that #125 reached `main` without a rewrite the build then applied; the file is outside PLAN-64's declared surface, which is why the lane kept it out of #122. The lane did not treat the operator's sentence as permission to skip a reviewer on #122 itself.
- Collision with the plans that ran beside it: PLAN-83 (#125, 5 files) was predicted and merged into the branch (`da892e9` resolves conflicts in `Traceability.adoc` and `runtime-start.adoc`, the ADR renumbered 014 → 015); PLAN-82 (#121) likewise. PLAN-89 and PLAN-85 share `Traceability.adoc` and `README.adoc` with this footprint. PLAN-23 (`stalled-scope-detection`) was superseded by PLAN-64 before the landing and D8 is the work it described.
- Index: the squash removed the `removed-concepts` row from `doc/plans/README.adoc` and deleted `doc/plans/removed-concepts.adoc` — the standing constraint was exercised.
- Cleanup: `cleanup_owed=false`. The remote branch `feature/64-removed-concepts` is the head of a merged PR.

## Staged specs the landing touches

`corpus declaration-currency` over the 90 other specs (footprint base `c7780a0`) shows three that declare files this squash deleted:
- PLAN-87 `journal-format-version-reset`: `ScopeSessionLogTest.java`, declared as a hypothesis ("journal fixtures"), no longer exists. The spec is corrected in this reconciliation (the entry removed with the reason).
- PLAN-28 `task-queue-and-protocol` and PLAN-73 `question-continuation`: declare `core-queue.adoc` and the `core/queue/` directories. PLAN-64's spec says `task-queue-and-protocol` recreates the package afterwards, so the entries are creations, not edits; both stay blocked (PLAN-28 by claim 0, PLAN-73 not ready) and are re-read when they are re-grounded.
PLAN-84's declared directory `runtime/start/` and `runtime/job/` test surface still resolves (the files it names exist; `FencedSessionGenerationsSweepTest` is a deleted file inside a directory entry, not a named claim). PLAN-85 shares only documents (`core-store.adoc`, ADRs 008/009/025/036) and none of its claims names a removed type.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-64 --status shipped`
- [x] row `pr` stamped — `#122`
- [x] row `landing` stamped — `landings/PLAN-64.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-64-removed-concepts` (the sender, per the runbook rule)
- [x] epic.md: the token defect gets its seventh landing; the PLAN-64 watch is closed (the worktree edit was #127); the 18 Sonar issues, the unapplied rewrite in #125, and the owed wiring are recorded as Watches
- [x] PLAN-87 spec: the deleted `ScopeSessionLogTest.java` entry removed
- [x] the landing carries no candidate lesson for this store
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Owed outside this repository** (plan-reported, corroborated by the Javadoc of the new types): `pm-mcp-server` in `plan-marshall-mcp` registers `PendingEntryRecordsSweep` and wires `StalledScopeDetector` with an `OperatorPort`; `process-and-overview` serves the `needs_operator` summary; the removed public `JobRefusal` API is an interface change to the clients' build. A Watch. The detector has no production caller, so the stalled hold does not exist end to end.
- **Owed to the milestone exit** (plan-reported, D7): the roadmap `_Remaining_` update in `plan-marshall-documentation` and the concept and user documentation of the slice. PM-WATCH-RUN-2 and PM-WATCH-RUN-8 stay gaps (clients, `pm-operator status`, epic view, metrics).
- **Untriaged Sonar issues on #122 (18)**: a Watch; the CRITICAL `S1192` in `AuditEvent.java` and the unused parameter in `WrapperVerification` are production code. The landing's own self-review list names an unused `projectId` as fixed, so the one Sonar still reports (`WrapperVerification.java:52`) is a lead for the next change to that file, not proof of a miss.
- **#125 merged without a rewrite the pre-commit gate applies**: #127 applied it (14 lines). A rule-break lead for PLAN-83's lane (`verify -Ppre-commit` not run, or run on an executor that rejected the canonical name); no further action beyond the executor regeneration already owed.
- The squash message names ADR 014 for the stall design; the tree has 015. Not fixable on `main`; recorded here only.
