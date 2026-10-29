# Landing Analysis: PLAN-85 — Close the advisories PLAN-14 recorded on the waiver, question and project stores

epic: pm-mcp-core-m2-m4
workstream: WS-02
pr: #129 (https://github.com/plan-marshall/pm-mcp-core/pull/129), merged as squash `9732401` through the merge queue on 2026-10-22 09:40Z

> Landing record for one shipped plan. Lives at `landings/PLAN-85.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> reconciliation contract.

Source of the landing: inbox message `rb-85-store-records-advisories-001.md` (`kind: landing`, sender `rb-85-store-records-advisories`, `/plan-marshall-runbook-lane` on opencode, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`; `landing-facts` block present (`deliverables_total=6`, `deliverables_done=6`, `pr=#129`, `merge_state=merged`, `cleanup_owed=false`, `harness=opencode`, `model=deepseek-v4.1-flash`, `steps=…,self-review:clear,…`). No candidate-lesson messages came with it.

## Deliverable Fidelity vs Spec

Read at `9732401`. The orchestrator ran the 14 touched or split test classes of `pm-core` (`ProjectResolver*Test`, `QuestionLog*Test`, `QuestionRecord*Test`, `WaiverLogTest`, `WorkflowStatusCodecTest`) on `main` at `9732401`: 287 tests, green. All 15 test names the landing cites exist in the merged tree. The three oversized classes were split without losing a test: comparing the test method names of `ProjectResolverTest`, `QuestionLogTest` and `QuestionRecordCodecTest` at `0bf64f7` with those of every `ProjectResolver*`, `QuestionLog*` and `QuestionRecord*` test class at `9732401` finds none lost (71 methods before, 81 after; ten new: `decisionAfterTheFirstQuestionClosed`, `decisionOnlyOnTheFirstQuestion`, `evidenceCount`, `partAfterFirstQuestionClosedInOneAppend`, `partAfterRevisionInOneAppend`, `partIntoExistingSet`, `partNamesASetTheLogDoesNotHold`, `partNamesNoFirstQuestion`, `rootConflictBesideRefusedFile`, `unreadableFileBesideMatch`).

| # | Deliverable | Verdict | Evidence |
|---|-------------|---------|----------|
| 1 | Repeated waiver id; duplicate or double-pending `waiver_requests` | met; the first half is shown to be no defect | the repeated waiver id is the specified rule, "read as the last readable line of a waiver" (`doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` § Waiver Record, line 273; `PM-WATCH-STORE-45`), and `WaiverLog.current` returns the last readable line; `WaiverLogTest.repeatedIdTakenWhole` pins it. The duplicate request id and the second pending request are refused (`WorkflowStatusCodecTest.requestIdTwice`, `onePendingAtATime`; `WorkflowStatus` +30 lines); ADR core-store/041 |
| 2 | `decision` on a non-first revised line | met | `QuestionLogRevisionTest.decisionOnlyOnTheFirstQuestion`, `decisionAfterTheFirstQuestionClosed`; `QuestionLog` +69 lines |
| 3 | A part asked into an existing set | met | decided as "a question set is asked whole" (ADR core-store/040): `QuestionLogMalformedTest.partIntoExistingSet`, `partAfterRevisionInOneAppend`, `partAfterFirstQuestionClosedInOneAppend`, `partNamesNoFirstQuestion` |
| 4 | `ProjectResolver` hiding an enrolment conflict | met, with a sharpened reading | an unreadable enrolment file now surfaces `EnrolmentRefused` instead of a match; `RootConflict` stays for two readable matches (`ProjectResolverEnrolmentTest`, four methods); ADR core-store/037 and the developer guide `core-store.adoc` corrected (both in the diff) |
| 5 | Untested guards; the three oversized test classes split | met | `QuestionRecordCodecTest.evidenceCount`, `QuestionLogMalformedTest.partNamesASetTheLogDoesNotHold` exist; `ProjectResolverTest` (569 lines) was replaced by eight classes and a fixture, `QuestionLogTest` by six classes and a fixture beside what remains of it, `QuestionRecordCodecTest` by three classes |
| 6 | The Alternatives of ADRs 033 and 034; the Traceability of `PM-WF-12` and `PM-SEC-3` | met | both ADRs carry a written `Alternatives Considered`; `doc/Traceability.adoc` has `PM-WF-12` (line 324) and `PM-SEC-3` (line 743) with Implementation lines naming `QuestionRecord`, `QuestionLog`, `WaiverRecord`, `WaiverLog` and the consumption types |

The Done-when's "ADRs numbered against `origin/main` after the last rebase" holds: `core-store` now runs 030 to 041 without a gap or duplicate (033, 034, 037 edited; 040 and 041 new).

## Surface delta (measured by the orchestrator)

Declared 14, realized 38, added 25, declared and untouched 5, `state: expansion_detected`. Added: the new test classes and fixtures of the split, `ProjectResolution`, `QuestionRecordCodec`, `WorkflowStatus`, and two watch files. Declared and untouched: `QuestionRecord.java`, `WaiverLog.java`, `WaiverRequest.java`, `WorkflowStatusCodec.java` (the hypothesis that each advisory needed a change in its type was refuted for the waiver log, and the guards landed elsewhere) and `doc/plans/README.adoc`. The declared class list was a guess, as for PLAN-20 and PLAN-84.

## Metrics and Anomalies

- `total_tokens`: not in the message (opencode; the eleventh landing with it).
- CI on the final head (30 checks, `ci checks status`): green; SonarCloud gate OK. **Seven issues were open on the pull request at the merge** (the landing reports "7 new issues, informational, not triaged into this plan") and are on `main` now (118 open, 111 before): `java:S3776` and `S135` `ProjectResolver.java` 146 and 149; `S1854` and `S1481` `QuestionLogMalformedTest.java` 104; `S5778` x3 `WorkflowStatusCodecTest.java` 495, 503, 511. None is in a file PLAN-94 or PLAN-87 named; they are added to PLAN-94 (Deliverables 2, 3, 5 and 6) by this analysis.
- Reviews: all 21 review-thread comments are resolved. CodeRabbit last reviewed `02e0ed9`; the merged head `395a4f8` was not reviewed (quota), and the operator selected "merge now and keep the substitution for this run" (plan-reported; five operator questions, recorded in the message). Sourcery refused on quota (optional).
- Plan-reported and not re-run here: one superseded `sonar-build` failure at `5cb3fd7` (analyzer error, a newer run green), one local `LspClientTest.stubborn` 2 s timeout in an untouched module, the executor skew (`build-classify` absent, the canonical `quality-gate` name not a Maven goal; the lane ran `verify -Ppre-commit` scoped).
- Rule breaks (plan-reported): one `sed` to view a range; the current-head review bent as above. **The lane restored five `pm-runtime` files that `verify -Ppre-commit` rewrote from #135** (catch-block reformatting, `cui-rewrite:disable` markers): `main` therefore carries #135 files the quality gate would still rewrite, because #135 did not run the gate (its landing says so). PLAN-94's Done-when (`verify -Ppre-commit` run, rewrites reviewed and committed) covers it.

## Routing and Merge Behavior

Merged through the merge queue as a squash; no ejection. It merged after #135 (`0bf64f7`) and rebased over it (the lane's base merge moved the head once, `3c70205`). No file collision with #135 beyond the shared documents. `corpus declaration-currency` over the 38 footprint paths: the declared surface of 29 staged and parked specs contains footprint paths, most through the directory `pm-core/.../core/store/` (11 paths each for PLAN-42, 43, 45, 46, 47, 48, 51, 53, 55, 57, 58, 59, 62, 76, 77, 92; 12 for PLAN-61 and 63; 35 and 36 for PLAN-71 and PLAN-31; 5 for PLAN-66, 67, 68, 99, 100). Together with #135, **31 staged and parked specs hold stale claims** (0 blocking): PLAN-27, 28, 31, 34, 40, 42, 43, 45, 46, 47, 48, 51, 53, 55, 57, 58, 59, 60, 61, 62, 63, 66, 67, 68, 71, 76, 77, 87, 92, 99, 100. Their code claims about `core.store` (the question log, the waiver log, the status document, the project resolver) were true before #129; a `cleanup` re-reads them.

## Reconciliation Actions

- Row `PLAN-85`: `shipped`, `pr=#129`, `landing=landings/PLAN-85.md`, `plan_marshall_plan_id=rb-85-store-records-advisories`; message `-001` archived.
- PLAN-94 extended by the seven new Sonar issues (Deliverables 2, 3, 5, 6; 118 issues on `main`, 89 its own).
- Open Defect folded: the landing carried no `total_tokens` (eleventh).
- The operator-overlap prompt PLAN-87 waited on (PLAN-84 and PLAN-85 running) is moot: both have landed.

## Follow-Ups

- Run `cleanup` before the next emit: 31 specs hold stale claims.
- Owed to the milestone exit, in `plan-marshall-documentation`: the concept and user documentation of the stores and the roadmap check-off of `operator-and-question-stores`.
