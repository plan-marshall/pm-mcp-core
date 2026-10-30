# Owner register of pm-mcp-core-m2-m4-v2

Written 2026-10-30 when the open defects and watches of the predecessor epic were assigned. Every open item of the previous epic is in exactly one of five places: it names a plan of this epic in its own text (`epic.md`), it is assigned below to an existing plan (A), it is owed to another repository or to the operator (E), it awaits a decision (D), or it is history and was dropped with its reason (H). The full text of every item stays in `epic.md` (kept items) and in the archived ledger `archived-orchestrators/pm-mcp-core-m2-m4/epic.md`.

## A. Assigned to a plan of this epic

The owning plan carries the item in its section `Carried Open Items`; it is not a deliverable until the plan's outline adopts it.

| Item | Plan(s) | Note |
|------|---------|------|
| Op1: PLAN-98 residues (analysis of #222, 2026-10-29). | PLAN-V2-53, PLAN-V2-67 | the repository-wide architecture rule and the recording-test-network test of the Closed List of Outbound Calls belong to V2-53; the small residues (the two limits bisections as one helper, a WARN for a pre-send refusal, a negative |
| Op3: PLAN-97 shipped 9 of 10; three residues (analysis of #214, 2026-10-28) | PLAN-V2-46 | Sonar: java:S3776 in AnswerSchema.check (complexity 25 against 15) is a refactor of this plan's surface; the six java:S1135 issues are the generated TODO comments above the six catch (RuntimeException _) clauses that Deliverable 3 replaces (decision 6, closed); the unwired seams |
| Op6: PLAN-43 open items the lane recorded, not fixed (message `-004`, 2026- | PLAN-V2-57, PLAN-V2-82 | handed_back/returned end with phase aborted (WorkflowEngine.ended) is V2-57; the cross-unit duplicate state ids in CallGraphExpansion are V2-82; the end-to-end default-plan test and the unmodelled adopt/land lanes are to be named  |
| Op12: Two code residues of the token-usage closure (analysis of #215, 2026-1 | PLAN-V2-56 | store-schemas/16 no longer lists tokens_in/tokens_out on the qualification and judge-check records while QualificationRecordCodec and JudgeCheckCodec still write and require them: change the code to match; and the source comments  |
| Op21: `SchemaSubset.checkString` evaluates a pattern before it checks `maxLe | PLAN-V2-65 | SchemaSubset.checkString compiles and matches the pattern before it tests maxLength (lines 139 to 153): test maxLength first; whether a corpus value can be attacker-controlled was not assessed |
| Op23: PLAN-25's package bookkeeping is partial (landing, checked). | PLAN-V2-34 | PLAN-25 kept the implemented rows of Timeouts § Waits and PM-WATCH-JOB-5 where R05 says delete; this reduction owns them (PM-WATCH-JOB-30 stays open) |
| Op24: Unverified lead (lane report, PLAN-25): | PLAN-V2-68 | ProfileValidator rejects bounded >= idle_timeout_seconds only, the specification requires the 30-second margin (idle - 30); ConnectionSettings enforces it, so a profile in the narrow band loads and is refused later; not yet checke |
| Op25: Unverified lead (lane report, PLAN-95): | PLAN-V2-65 | imported-item.schema.json has nine pattern members and a maxLength only on title; the corpus path has no PATTERN_MAX_LENGTH_CEILING; the lane refuted attacker-controlled schemas, values come from project text |
| Op27: Two SonarCloud items of PLAN-94 are unsettled, and 2 further issues of | PLAN-V2-67 | main: java:S112 at MacSecurity (the downcall must declare throws Throwable: dismissal is the only settlement), java:S5778 at ProfileValidatorTest, java:S9428 at StoreDigest; re-query before editing |
| Op28: The 8 work packages #151 cut have no orchestrator row and no staged sp | PLAN-V2-45 | the eight packages cut by #151 (access-grants, build-parsers-and-gate-honesty, client-profile-rendering, decision-quorum-and-calibration, domain-extensions-and-architecture, finalize-pipeline-and-merge, re-planning, remediation-un |
| Op29: `job-confinement-and-environment` (PLAN-27) | PLAN-V2-59 | in-repo share of PLAN-27 is delivered; missing here: the egress field of the job record and a production caller of WorkerEgressProxy; the Landlock/Seatbelt launcher, the worker network namespace, the egress tunnel and the CLI git  |
| Op32: R05 is practised as "condense and point", not "delete", and the lane t | PLAN-V2-34 | R05 was practised as condense-and-point, not delete (#172): this reduction applies the same rule to the launch protocol part and removes no watch item without a reason |
| Op33: The verify-not-resolve validation of a composed worker vector has no o | PLAN-V2-48 | the verify-not-resolve argument-schema validation of a composed worker vector is not run by the worker start path; Model Work § Launch Contract says "Remaining: ..." |
| Op43: `HarnessEnrolmentStore.decode` reads a missing, `null` or non-object t | PLAN-V2-46 | HarnessEnrolmentStore.decode reads a missing, null or non-object top-level harnesses member as an empty map although store-schemas/11 requires it; refuse with harness_store_unreadable |
| Op44: UPDATE 2026-10-27 (analysis of PLAN-66, `landings/PLAN-66.md`): | PLAN-V2-31 | PLAN-66 shipped the ingress (WorkerActivityPump, ActivityRecorder, OffTrackDetector) but no loop; nothing in src/main calls them; the loop itself is the assembly (external E3), this plan owns the library calls and the pass |
| Op45: No plan owns the supervision loop that drives the worker supervisor (f | PLAN-V2-31 | WorkerSupervisor, LossDetection, WorkerEnding, WorkerRecycling, StartFailureGuard and AgentStartedDetector are called from nowhere in src/main; "the pass is the supervision loop's call" (runtime-worker.adoc:268); the loop is the a |
| Op47: #190 deleted the specification sentence that PLAN-73's Deliverable 7 d | PLAN-V2-47 | #190 deleted Model Work 03 § Recycling Warm Workers incl. "a worker holding its task for an answer that reaches the budget ends its hold"; D7 depends on that sentence; WorkerRecycling recycles a HOLDING generation at the budget wi |
| Op49: The question-loop verification of `plan-marshall-mcp` is `done` (`ques | PLAN-V2-47 | the question-loop verification of plan-marshall-mcp is done: this plan has no open cross-repository dependency left; #197 wrote the resumability candidates and max_hold_seconds / max_age_seconds 3600 into Client Profiles 01/02 and |
| Wa10: Unverified leads from the review bot on #135 (CodeRabbit, 2026-10-22;  | PLAN-V2-66 | ProcessTreeTracker.sweep signals by numeric PID after a liveness check (reuse window); where the platform reports no start instant SubprocessJobRunner treats a descendant as matching: CodeRabbit leads on #135, exploitability unpro |
| Wa17: Verifier performance on the real merged plan unit (simple-cycle enumer | PLAN-V2-58 | verifier performance on the real merged plan unit (simple-cycle enumeration, 2^N configuration combinations) is untested |
| Wa26: Closing a channel on a POSIX file drops the process's OS locks on it,  | PLAN-V2-07 | closing a channel on a POSIX file drops the process's OS locks on it, so JsonlStoreFile appends on file-locked stores lose the lock manager's OS lock (ADR core-store/024 accepted it for the audit log alone); V2-07 adds two more JS |
| Wa40: Step 3a of the job runtime specification is delivered for a probe only | PLAN-V2-66 | step 3a of the job runtime specification is delivered for a probe only (ProcessTreeTracker); not built: the worker (llm) half, the launcher subreaper (external E1) and "the runtime status shows it" |
| Wa41: The record's stage and platform gate had no owner (found by the analys | PLAN-V2-39 | PM-WATCH-CLIENT-13: the load-time gate (refuse worker.eligible: true and a non-null zero_tool when the record's stages did not pass on the runtime's platform) was carried into PLAN-30 and is open; V2-43 and V2-50 name it as well |
| Wa52: #169 changed `TaskProtocol` outside PLAN-32's declaration, and a stage | PLAN-V2-33, PLAN-V2-77 | #169 made workflow.task.TaskProtocol refuse a consultation task's generic submit and infeasible with link_not_available (only submit:answer); read the changed protocol and ADR workflow-consult/004 before outlining |

## E. Owed outside this repository or to the operator

| Id | Item | Owner | Where it is recorded |
|----|------|-------|----------------------|
| E1 Op2 | PLAN-106 shipped the library half; the assembly halves of D1 | plan-marshall-mcp | entry points submit:refine, light submit:outline, re-planning and the phase-owned parts of PLAN-106 D1 to D4 (library half is on main with no production caller) |
| E2 Op7 | `client-profile-measured-values` is a new open package with  | plan-marshall-mcp (question-loop-follow-up) and the sibling epic question-loop-corrections | package client-profile-measured-values has no row here; blocked on a measurement; stage when unblocked |
| E3 Op19 | Owed outside this repository, from that analysis (still owed | plan-marshall-mcp, pm-mcp-clients, roadmap | skill tools without scope, the ICU message subset in pm-api, quarkus-micrometer with the native build, Landlock on WSL2 moved to Milestone 7; UC-PRJ-4 and UC-PLN-6 are DRAFT use cases |
| E4 Op20 | Nothing in production records `credentials.store`, so a fres | plan-marshall-mcp (machine onboarding unit) | a fresh install refuses every credential until the onboarding unit records credentials.store; the engine side is PR #233 |
| E5 Op30 | The `module-structure.adoc` correction for the HTTP provider | plan-marshall-documentation | module-structure.adoc still describes pm-provider-ci as a module; the correction is in the body of pm-mcp-core #156; retire when merged |
| E6 Wa2 | Owed to the milestone exit after PLAN-70 (#107, 2026-10-19) | plan-marshall-documentation | roadmap check-offs and the documentation parts of PM-IMPL-5 for the plan-instance slice (milestone exit) |
| E7 Wa3 | Filed 2026-10-21 in plan-marshall's `live-blockers` inbox (` | plan-marshall (live-blockers inbox) | executor skew, the total_tokens=unknown recurrence, the lane edit of the main checkout, the ci repository selector, Sonar issues left open at merge: filed as pm-mcp-core-m2-m4-001..005 |
| E8 Wa8 | The Secret Service path (Linux) | operator (Linux desktop session) | SecretServiceIT skips itself without a session bus and an unlocked default collection; run verify -Pintegration-tests -pl pm-runtime -am on such a session |
| E9 Wa9 | The native build of `pm-mcpd` in `plan-marshall-mcp` against | plan-marshall-mcp | the native build of pm-mcpd against the SNAPSHOT has not run since #90/#92; it is the only check of the kill downcall and of the Keychain and Secret Service paths |
| E10 Wa11 | Owed after PLAN-69 (#91, 2026-10-18), CORRECTED 2026-10-18 a | plan-marshall-mcp (default-units-and-drift-tests) | nothing breaks today (no .pmwf unit, no import of de.planmarshall.workflow); it matters once the default units become resources there |
| E11 Wa12 | A pm-runtime adapter for `core.spi.PendingEntryReader` over  | plan-marshall-mcp | daemon wiring of the pm-runtime adapter for core.spi.PendingEntryReader over PendingEntryStore |
| E12 Wa13 | plan-marshall-mcp still owes (after #76): | plan-marshall-mcp | DefaultUnitInvariantTest over the real default units, wiring the project-unit loader (config_invalid), taking the 16 log records of PLAN-17 into a holder of its own; the sweep item is void |
| E13 Wa14 | plan-marshall-mcp also owes (after PLAN-09, #69): | plan-marshall-mcp | wire AuditLogSink and AuditVerification in place of AuditChainVerification.NONE, create logs/ before the log is built, break the FileLockManager/AuditLogSink constructor cycle, pm-operator audit verify |
| E14 Wa16 | Specification issue `plan-marshall-documentation` #71 ("Work | plan-marshall-documentation | issue #71 (how a depends_on_config value activates a guard) is open; rule 3 stays a named seam until it is answered; then stage the implementation |
| E15 Wa20 | Owed outside PLAN-64 (plan-reported, corroborated by the Jav | plan-marshall-mcp, plan-marshall-documentation | register PendingEntryRecordsSweep, wire StalledScopeDetector with an OperatorPort, serve the needs_operator summary; roadmap and concept documentation |
| E16 Wa21 | Owed outside PLAN-83 (plan-reported, accepted by the operato | plan-marshall-mcp | ServiceUnitInstaller.restore() has no production caller: the assembly must run it first |
| E17 Wa22 | Install, restore and uninstall share the kept pair with no c | plan-marshall-mcp | install, restore and uninstall share the kept pair with no cross-process owner; the change that wires pm-operator runtime install/uninstall serializes them |
| E18 Wa23 | A failed drop after a successful load can restore an OLDER u | plan-marshall-mcp | a failed drop after a successful load can restore an OLDER unit later (WARN PM_RUNTIME-136, dropAfterLoad); re-read when the start is wired |
| E19 Wa24 | Owed outside PLAN-82 (plan-reported): | plan-marshall-mcp | the assembly must call CredentialStoreChoice.record(LockManager, user, store) |
| E20 Wa25 | The trust assumptions of the question reader before it is wi | plan-marshall-mcp | trust assumptions of the question reader before the CDI producer: check-time limitation of WorkspaceConfinement, an out-of-band change of questions.jsonl is not detected |
| E21 Wa27 | The `plan-marshall-mcp` package that wires `FileLockManager` | plan-marshall-mcp | the package that wires FileLockManager must create one instance per runtime (a second one can release the first holder's OS lock on POSIX) |
| E22 Wa28 | `verify -Ppre-commit` of `pm-mcp-parent` is not idempotent a | parent POM (pm-mcp-parent) | verify -Ppre-commit is not idempotent and rewrites files a plan never touched; every plan can pull unrelated diffs into its PR |
| E23 Wa33 | Owed to the milestone exit by PLAN-72 (listed in the body of | plan-marshall-documentation | roadmap item of the spend removal checked off at the Milestone 3 exit batch |
| E24 Wa43 | `StalledHold.kind()` became `StalledHold.KIND` (PLAN-87, #14 | plan-marshall-mcp (SNAPSHOT build) | StalledHold.kind() became StalledHold.KIND; retire when a SNAPSHOT-based build of pm-mcp-server compiles |

## D. Decisions of the operator (all answered on 2026-10-30)

| Id | Question | Decision | Carried by |
|----|----------|----------|------------|
| D1 Wa1 | Audit identity of `judgment_task_completed` | Machine-level `os_principal` | PLAN-V2-81 (Carried Open Items) |
| D2 Op18 | Owner of the pending-entry port of `QuestionRecordReader` | PLAN-V2-22 | PLAN-V2-22 (Carried Open Items) |
| D3 Wa26 | The OS lock lost on a JSONL append | Fix in the lock manager before PLAN-V2-07 | PLAN-V2-95 (new); PLAN-V2-07 depends on it |
| D4 Op48 | Owner of the grading adapter of role qualification | A plan after PLAN-V2-31 | PLAN-V2-96 (new) |
| D5 Op15 | The remainder of the PLAN-02 roadmap item | Checked 2026-10-30 (`plan02-roadmap-check-2026-10-30.md`): lead 5, the runtime binding of free parameters, is a real gap and is carried by PLAN-V2-15; leads 1 (request.md and PR body validators) and 4b (keyword list) are stale and retired; leads 2 and 3 (OutlineValidator rules that need the working tree, task derivation) are Milestone 8 work for a later outline-validation package after PLAN-V2-35, not staged now; lead 4a (closed gate list) belongs inside the open packages `remediation-units` and `finalize-pipeline-and-merge` | PLAN-V2-15 (Carried Open Items) |
| D6 Op3 | Six `java:S1135` TODO issues | Closed: they are the TODO comments above the six generic catches that PLAN-V2-46 replaces | PLAN-V2-46 Deliverable 3 |
| D7 | `Optional` as parameter, field, record component | Convert all (reverses the earlier choice) | PLAN-V2-97, 98, 99 (new) |
| D8 | `@Builder` on records | From seven components | PLAN-V2-100 (new) |
| D9 | Test class layout and the 400-line budget | Behaviour tests stay; 400 lines reported only | the one exception, ADR of PLAN-V2-83 |
| D10 | Assertion messages | Every assertion everywhere | PLAN-V2-101 to 105 (new) |
| D11 | Hand-written fakes | Replace with EasyMock | PLAN-V2-106 (new); the dependency in PLAN-V2-83 |

## H. Dropped as history (the record stays in the archived ledger)

| Item | Reason |
|------|--------|
| Op4: Incomplete landings `rb-106-...-001` and `rb-97-...-001` (2026-10-28): | incomplete landing record, folded into the recurring runbook-lane gap (Wa3) |
| Op8: Incomplete landing `rb-43-phase-sub-workflows-003.md` (2026-10-28): | same |
| Op10: Incomplete landing `rb-35-supervision-scenarios-001.md` (2026-10-29): | same |
| Op17: The landing of PLAN-78 is incomplete: | same |
| Op26: Two landings of 2026-10-21 are incomplete (`inbox landing-check`). | same |
| Wa39: The ninth landing without `total_tokens`: | same |
| Op5: PLAN-43 cannot merge with the composer tests red, and PLAN-105 waits f | decided 2026-10-28; PLAN-43 and PLAN-105 have landed |
| Op13: Format version bumped although nothing is released (cleanup at the end | closed by #148 (FORMAT_VERSION is 1) |
| Op14: No consumer is wired to the confinement check of PLAN-03; `pm-provider | decided: the specs tell each package to adopt the confinement check itself |
| Op16: `AsciiDocValidator` treats `!===` as the nested-table delimiter; CodeR | AsciiDocValidator is not in this repository (it appears only in the archived ledger); the lead belongs to the plan-marshall marketplace |
| Op22: A spec edited in the ledger worktree is not what a lane reads (found 2 | resolved by the land-before-emit rule (confirmed at PLAN-87) |
| Op36: UPDATE 2026-10-27: a twelfth, PLAN-43 `phase-sub-workflows`, which nei | PLAN-43 was re-scoped and has landed |
| Op42: `FindingSchemaParityTest` is red on `main` again after a specification | fixed: FindingSourceKind has CAPABILITY_TESTS on main |
| Wa4: The runbook lane's build routing fails in a fresh lane worktree (PLAN- | executor skew of 0.1.19xx, stale; filed in the live-blockers inbox (Wa3) |
| Wa34: The executor of this checkout pins plugin 0.1.1916 while 0.1.1947 is i | same |
| Wa35: The check command of `CLAUDE.md` (`../plan-marshall-documentation/…`) | the standing constraints of the specs carry the absolute-path command |
| Wa5: The merge queue dequeued #145 on a Maven Central 403 (PLAN-25 landing, | one-off Maven Central 403 in a merge group |
| Wa29: Token metrics of a plan can be overstated: | metrics caveat of one plan |
| Wa36: The run of PLAN-72 cost about 3.9M tokens against a budget of 2.5M for | one-off cost note of PLAN-72 |
| Wa30: About eleven packages land in `pm-core` package `core.store` — re-chec | moot: the schedule pairs plans by declared surface |
| Wa31: Design choices that change the file set and are therefore for the oper | PLAN-14 and PLAN-19 shipped |
| Wa32: The local checkout of `plan-marshall-mcp` is five commits behind `orig | checkout lag, no longer the case |
| Wa37: SonarCloud lists 111 open issues on `main` (109 on 2026-10-21, 111 aft | closed by PLAN-94; main reads 13 issues, listed where owned (V2-46, V2-67, V2-81) |
| Wa42: PLAN-19 merged on a red SonarCloud gate, 176 issues open (found by the | owned by PLAN-94, shipped |
| Wa50: `verify -Pcoverage` is reported red in `pm-provider-git` on `main` (br | closed 2026-10-26 |
| Wa53: PLAN-42 and PLAN-V2-13 overlap each other on 13 declared entries and t | PLAN-42 shipped; the schedule pairs plans by declared surface |

Also dropped: the nine entries of the predecessor epic whose heading began CLOSED or RETIRED.

## Kept as standing watches (no owner needed)

- Wa6 `/tmp` inode exhaustion on the operator's machine; Wa18 assume Claude Code unless a plan states otherwise; Wa19 non-Claude-Code runs need the extended landing verification.
