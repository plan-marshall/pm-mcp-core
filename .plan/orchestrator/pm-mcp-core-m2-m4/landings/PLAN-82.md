# Landing Analysis: PLAN-82 — Close the advisories PLAN-74 left on the credential-store choice and move

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #121 (https://github.com/plan-marshall/pm-mcp-core/pull/121), merged as squash `15fbe6f` through the merge queue on 2026-10-21

> Landing record for one shipped plan. Lives at `landings/PLAN-82.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `rb-82-credential-store-move-advisories-001.md` (`kind: landing`, sender `rb-82-credential-store-move-advisories`, runbook lane on opencode, model `deepseek-v4.1-flash`, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`.

## Deliverable Fidelity vs Spec

The landing counts six deliverables (`deliverables_total=6`, `deliverables_done=5`): the five numbered ones and the orchestrator-added lane block T. The five are met; T is met in part (T1 and the local half of T4 are reported as not met as written). The orchestrator re-read the merged tree at `15fbe6f`; it did not run a build (prime directive).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 First-recording key carry reconciles both stores | shipped-as-specified (decision: reconcile) | `CredentialStoreChoice.java` +519/-… and `IntegrityKeyCarry.java` +31; `IntegrityKeyCarry.failureUnder(SecretKey)` exists (line 170) and is called at `CredentialStoreChoice.java:588-589, 745`; tests `reconcilesTheOtherStore` (5 placements), `refusesWhenTheSidecarsDoNotDecide`, `targetOnlyKey`, `failureAfterTheCopyLeavesNothingToResume`, `noKeyYet` found by name in `CredentialStoreChoiceTest` (lines 907-1110); the operator chose "Reconcile" at refine (plan-reported) |
| D2 A stop between the switch and the key delete is resumed | shipped-as-specified (decision: reuse the move record) | `CredentialStoreMoveRecord.Operation.FIRST_RECORDING` (line 120) and `transaction_id` (line 84); `CredentialStoreMove.java:275` and `start/CredentialStoreMoveResume.java:78` dispatch on the operation; tests `cutBeforeTheSwitch`, `cutBetweenTheSwitchAndTheRecord`, `cutAfterTheSwitch`, `idempotent`, `switchedWithoutTheRecordedStore`, `anotherStoreRecorded`, `sourceWithoutTheKeptKey`, `anotherKeyBesideOneTheSidecarsDoNotVerifyUnder`, `aspectRefusedUntilTheResume` found in `CredentialStoreMoveResumeTest` (lines 208-506); `deletionAfterTheSwitchFails` in `CredentialStoreChoiceTest:998` |
| D3 `credential_store_moved` carries a transaction id | shipped-as-specified | schema block gains `operation` and `transaction_id` (`12-machine-stores.adoc`, required list and properties); `CredentialStoreMoveRecordParityTest` (+168) is new; `reDeliveredKeepsItsId` (`CredentialStoreMoveTest:933`) and `anotherMoveIsANewEvent` (`:961`) exist; ADR `009-the-transaction-id-of-the-operation.adoc` |
| D4 Two spec texts corrected | shipped-as-specified | `12-machine-stores.adoc`: `target_held_key` no longer says it is "the key of `from`" for every case; `02-machine-runtime.adoc` comment now lists `operation`, `target_held_key`, transaction id and names the first recording; `01-provider-credentials.adoc` "The first recording" bullet rewritten. The before/after quotes live in the lane's progress file, which is not in the repository; not re-read |
| D5 Tests, developer documentation, one ADR per decision | shipped-as-specified | ADRs 007, 008, 009 are new and numbered 007-009 in a directory that ended at 006 (no duplicate number on `15fbe6f`); ADRs 004 and 006 amended; `runtime-credentials.adoc` +207/-…, `runtime-enrolment.adoc`, `runtime-start.adoc` changed; `doc/LogMessages.adoc` +3 for `PM_RUNTIME-32/133/134`; no duplicate `identifier(` in `RuntimeLogMessages.java` |
| T Lane test proof (orchestrator-added) | shipped-modified | T1 (test first, seen failing) is **not met as written**: the fix was written first and the failing run was produced by disabling it (`mutation-after`), the landing says so itself; T4 local `verify`, `-Pcoverage` and `-Pintegration-tests` did not run to green (inotify limit, fails the same on the base `918ef86`, plan-reported) and CI proved them on the final head by the operator's choice; T2, T3, T5, T6 met as reported |

Hypotheses of the spec, as the landing settles them: all four confirmed at `918ef86` (plan-reported with file:line); the diff of `CredentialStoreChoice.java`, `CredentialStoreMove.java` and the two spec files is consistent with each. The old line numbers were not re-read.

Surface delta (`inbox landing-check`): `unmeasured` (neither side supplied). Orchestrator count against the spec's `## Expected Surface` (12 entries): 25 files merged. Declared and touched: the four main classes, `CredentialStoreChoiceTest`, `CredentialStoreMoveResumeTest`, `runtime-credentials.adoc`, the ADR directory, `12-machine-stores.adoc`, `02-machine-runtime.adoc`. Declared and untouched (`missing`): `CredentialStoreChoiceFixture.java`, and `doc/plans/README.adoc` (a "no row of its own" entry). Undeclared (`added`): `CredentialStoreMoveRecord.java`, `EnrolmentKey.java`, `RuntimeLogMessages.java`, `CredentialStoreMoveRecordParityTest.java`, `CredentialStoreMoveRecordTest.java`, `CredentialStoreMoveTest.java`, `runtime-enrolment.adoc`, `runtime-start.adoc`, `01-provider-credentials.adoc`, `doc/LogMessages.adoc`, `doc/Traceability.adoc`. The record class and its tests are the natural carriers of D2 and D3, so the spec under-declared its own surface by about eleven files.

Removed assertions: the diff removes 12 lines containing an assertion in the test tree. The landing names two replaced assertions of `deletionAfterTheSwitchFails`; the diff shows three removed there (`"nothing was recorded"` negation, `assertEquals(recordedDocument, readMachineConfig())`, `assertTrue(verified(key(choice).readOrCreate()).isEmpty())`), all three replaced by assertions of the new behaviour (record fields, `ASPECT_OWNED` refusal, resume, second resume empty). The other nine are re-formatted or carried: the five-argument record constructor in two tests (now seven, with `Operation.MOVE` and the transaction id), two message texts ("only the move records" → "only that operation records"), the audit-event equality (split into a count, the details without the id, and a check that the id carries the clock's instant), `assertMoved` (split into `assertMovedState` plus a distinct-event count), and one `assertFalse(Files.exists(...))` and one `assertThrows` that kept their assertion and only changed their line break. Read in the diff hunks: every removal has a replacement of the same subject that is equal or stronger, so T3 holds, with the count corrected (three, not two, in `deletionAfterTheSwitchFails`).

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none). The fifth runbook landing on opencode carrying it. It is the one missing key of `landing-check`.
- Tests (plan-reported): `pm-runtime` 1522 → 1559, `pm-core` unchanged at 3105; 6817 tests in the full local gate. Orchestrator count in the diff: 27 added `@Test`/`@ParameterizedTest` annotations, none removed. Not re-run.
- Coverage: SonarCloud 90.7% on new code (plan-reported, from the PR's Sonar comment); the local `-Pcoverage` was environment-blocked.
- Deliverable order (plan-reported): `mutation-after` for every behaviour; T1 not met as written.
- Self-review: round 1 produced 15 findings (two blocking in the resume and the failure clean-up, one for `doc/Traceability.adoc`), all closed; round 2 was **not run** (`self-review:budget-exhausted` in the facts), no finding open.
- Executor: the lane met the stale installed executor again (`runbook_lane` pinned at 0.1.1961): `maven run --command-args "module-tests"` fails because the alias is not expanded, and `--project-dir` does not move the build's working directory in the `in_process` fallback, so every build passed `-f {worktree}/pom.xml` (plan-reported). Registry now pins 0.1.1977.
- Rule breaks (plan-reported): (1) the first edits went into the **main checkout** instead of the worktree; staged under `.plan/temp/rb82-edits`, the main checkout restored and the files copied across. Verified here: the main checkout is at `15fbe6f` on `main` with a clean status and no stash; no branch of the plan lives in it. (2) one `python3 -c "print(1)"` probe, a forbidden form. Guard: none on this harness (unenforced).

## Routing and Merge Behavior

- Review: `coderabbit` (required) reviewed; three inline threads on the PR, all `resolved: true` (read through `ci pr comments`), each answered by the plan; the findings were the "no key, no record" mechanism (`488eced`), `deleteKeyOfSource` skipping its checks when `target_held_key` is true (`488eced`) and a self-contradicting guide sentence (`7a23e29`, `23fda0e`). The last CodeRabbit run on the PR says "No actionable comments were generated in the recent review". `sourcery` (optional) refused on its review budget twice. Review-body "reliability" remark on an unreadable store is documented as a residual in ADR 007.
- CI/merge (read from the PR through `ci`): `state: merged`, merge commit `15fbe6f` on `main`; `checks status`: `overall_status: success`, 30 checks, including `build / build (25)`, `build / build (26)`, `build / sonar-build`, `integration-tests / test`, `quarkus-alignment` and `SonarCloud Code Analysis` (pass). All seven commits the landing names exist in the object store; `e2f2e5f` is a merge of `origin/main` into the branch, as the lane rule says (no rebase). The remote branch `feature/82-credential-store-move-advisories` is gone and no local branch or worktree remains.
- SonarCloud reports 10 new issues with the quality gate passed (read from the PR's Sonar summary comment, third-party text, a lead); the lane did not triage them and the orchestrator did not open them.
- Collision with the plans that ran beside it: PLAN-64 is the observed one. `corpus declaration-currency` against the merged footprint finds two files PLAN-64 declares: `doc/specification/store-schemas/12-machine-stores.adoc` and `pm-runtime/doc/developer/runtime-start.adoc`. The branch `feature/64-removed-concepts` (head `a562098`) does **not** contain `15fbe6f`, and its diff against the merge base touches `runtime-start.adoc`; the plan has to merge `origin/main` and may meet a textual conflict there. Waiting specs that now overlap with the landed footprint: PLAN-83 (`runtime-start.adoc`), PLAN-84 (`doc/LogMessages.adoc`, `CredentialStoreMoveResumeTest.java`), PLAN-85 and PLAN-89 (`doc/Traceability.adoc`). PLAN-87 and PLAN-91 share none. PLAN-89 and PLAN-91 are in flight and unaffected by the file overlap for 91; PLAN-89's `Traceability.adoc` overlap with the landed edit is on lines it must read fresh.
- Index: the squash did not touch `doc/plans/`; no row or package file for PLAN-74's advisories existed (PLAN-82 was staged from the PLAN-74 landing), so nothing is owed.
- Cleanup: `cleanup_owed=false`; confirmed above.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-82 --status shipped`
- [x] row `pr` stamped — `#121`
- [x] row `landing` stamped — `landings/PLAN-82.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-82-credential-store-move-advisories` (the sender, per the runbook rule)
- [x] epic.md: the token defect and the opencode watch get their fifth landing; the executor watch updated (registry now 0.1.1977); the 10 Sonar issues recorded as an unverified lead; the PLAN-64 file overlap recorded as a watch
- [x] the landing carries no candidate lesson for this store; the lane-contract lessons (executor aliases, `--project-dir` in the fallback) go to plan-marshall's lessons-routing inbox and are still unfiled
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- The assembly (`pm-mcp-server` in `plan-marshall-mcp`) still has to call `CredentialStoreChoice.record(LockManager, user, store)`; the concept and user documentation and the roadmap check-off belong to the milestone exit in `plan-marshall-documentation` (plan-reported "Owed to the milestone exit", in the PR body). A watch in `epic.md`.
- PLAN-64 will meet `15fbe6f` in `runtime-start.adoc` and `12-machine-stores.adoc` when it merges `origin/main`: recorded as a Watch in `epic.md`; the operator should expect a conflict resolution there.
- 10 SonarCloud issues on #121 are untriaged: a Watch in `epic.md`.
- A shipped plan's spec is not corrected after the fact. Recorded so the pattern stays visible: a plan that changes a record's shape touches the record class, its tests and the log holder, none of which the spec declared.
