# PLAN-V2-22: The entry as a directory and its consumption (the Milestone 3 part of start-source-intake)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: PLAN-76 of the epic pm-mcp-core-m2-m4; split 2026-11-10 (operator, Q11 of the issues pass): the intake of the sources is PLAN-V2-133

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-22-start-source-intake.md` and is queued as one row file, `queue/PLAN-V2-22.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the Milestone 3 part of the work package `start-source-intake` of `doc/plans/start-source-intake.adoc`: the entry as a directory and its consumption. Serves: roadmap Milestone 3 (the pending entry in its directory form, its opening and consumption); starting work in both clients of `pm-mcp-clients`. The rest of the package (Milestone 10: the screening of the sources, documents copied or moved, the entry routes and the listing of files; Milestone 12: an epic's sources in its ledger) was split off on 2026-11-10 (operator decision, Q11 of the issues pass) into PLAN-V2-133 (`start-source-intake-sources-and-screening`, parked), which depends on this plan. Where: pm-core (the pending entry's directory with its record, decision stores, and transcript, the source directory and its manifest, `origin`); pm-workflow (the move at creation in the start units); pm-runtime (the sweep of the entry directory, the reader of the entry directory). This package is the new half of `start-from-clients` that PLAN-75 (#85) split off on 2026-10-17: it takes 12 of its 21 bullets (the entry as a directory and the intake of the sources) and inherits its dependencies, and `start-from-clients` (PLAN-V2-24) now depends on it. The old spec of PLAN-V2-24 is the source of this one. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

The package file used to introduce itself (the design audit #286 removed that paragraph); the epic keeps the sentence as the list of sections the tasks implement: This plan implements Filesystem Layout § The Source Directory (the directory, its manifest and the move at creation) and Store Schemas § Pending Entry (`PM-WF-10`); State Catalogue § Documents as a Source, Model Work § What Is on This Machine Is Trusted and § Screened Once Stored, Store Schemas § A Claimed Lesson Ends with the Landing and the entry's address in Runtime Model § Events per Scope (`PM-SEC-1`) are PLAN-V2-133's. It builds on the setup gate, the source units, and the other sources of `start-from-clients`.

## Deliverables

The thirteen tasks of `doc/plans/start-source-intake.adoc` § Tasks (restructured by the design audit #286; the specification sections each task names are the single statement of what it builds) map onto the deliverables below: task 1 is deliverable 4, task 2 is 3, task 3 is 5, task 4 is 9, task 5 is 1, task 6 is 2, task 7 is 6, task 8 is 7, task 9 (a pull request that changes an instruction file, flagged at its screening) is the new deliverable 14, task 10 is 8, task 11 is 10, task 12 is 11, task 13 (schema parity) is 12. Deliverable 13 is not a task of the package file; it is the operator's fold-in of 2026-10-29 and stays. Split 2026-11-10: deliverables 1, 2, 6, 7, 8, 9, 11, 14, the route part of 10 and the epic-ledger part of 5 moved to PLAN-V2-133; a moved deliverable stays here as a one-line pointer, so that cross references, claim labels and the Grounding corrections section keep their numbers. The wording of the deliverables is the epic's elaboration (the package file no longer states it); where it differs from the Grounding corrections section, that section is the later reading.

1. (moved to PLAN-V2-133, deliverable 1) the source cause `rate_limited` of a forge fetch the forge refuses for its rate limit.
2. (moved to PLAN-V2-133, deliverable 2) documents copied or moved, the machine key `sources.documents_mode`.
3. An entry never expires: a pending entry has no time limit (Timeouts § Pending Entry); it stays among the project's open entries, offered to continue in both clients, until it is started or abandoned, or deleted with the release of its project. The pending entry record's maximum age in the code (24 hours, `PENDING_ENTRY_MAX_AGE` of `PeriodicSweep`) and the sweep step `PendingEntryRecordsSweep` that removes older records go; the sweep removes only the directory of an entry already started or abandoned whose removal a stop cut off, by that evidence (Runtime Lifecycle § Periodic Sweep).
4. The entry as a directory: `pending/<entry_id>/` with `entry.json` (the record of its schema), `sources/` with its manifest `sources.json`, `questions.jsonl` and `decision-log.jsonl` (the entry's decisions and their decision-log records, the two members Store Schemas § Pending Entry names), and `chat/` (the transcript), created in one workspace transaction when the entry is opened; it replaces the file form `pending/<entry_id>.json` of `PendingEntryStore`, `EntryId`, `PendingEntryReader`, and its codec. A digest mismatch of an entry pauses only that entry (`integrity_breach`, kind `store_tampered`, a hold of kind `integrity_hold`), ended by `accept-edit workspace pending/<entry_id>/entry.json` or by abandoning the entry, no other entry and no workspace workflow affected.
5. The source directory and the move at creation: `start.create-scope` moves the source directory into `plans/<plan_id>/sources/`, the transcript into `plans/<plan_id>/chat/` or `epics/<epic_id>/chat/`, never into a ledger, and the entry's decision stores into the new scope's question and decision stores, deleting the entry's directory last; the manifest; `origin` naming the sources; `request.md` composed from them. The epic-ledger part (the source directory moved into the epic's ledger, the epic header listing the sources, `epic.decompose` reading them) is deliverable 5 of PLAN-V2-133. It builds on the steps `start.open`, `start-epic.open`, and `start.create-scope` and on the source units of `start-from-clients` (`source-documents`, `source-issue`, `source-lesson.*`, `source-local-changes.*`).
6. (moved to PLAN-V2-133, deliverable 6) a source is screened once stored, by the screener over the stored copy.
7. (moved to PLAN-V2-133, deliverable 7) a failing screener fails closed to the wait `source-<kind>.unscreened`.
8. (moved to PLAN-V2-133, deliverable 8) a claimed lesson ends with the landing of the plan.
9. (moved to PLAN-V2-133, deliverable 9) an epic's sources in its ledger.
10. (moved to PLAN-V2-133, deliverable 10) the entry routes, addressed like plans and epics; they are `pm-mcp-server` classes and nothing of them is separable here (the entry address of the question port stays in deliverable 13).
11. (moved to PLAN-V2-133, deliverable 11) the listing of files, a move and documents outside the checkout, loopback only.
12. Schema parity: `PendingEntrySchemaParityTest` reads the schema of the entry's directory form (it takes `pending-entry.schema.json` from the class path through `SchemaResource`, so there is no heading anchor to repoint; the schema file becomes that of `entry.json`) and is re-enabled, and `AuditSchemaParityTest.enums` is already enabled and green with the scope `entry` of the audit record (the category `identity` and the entry row of the pending entry record are gone).
13. **Folded in 2026-10-29 (operator walk, decision 13 of 18; the orchestrator was above 95% sure): the reader of the entry in its directory form, and the question addressing of an entry.** an earlier plan (`pending-entry-reader-adapter`, never run) was staged to implement `PendingEntryReader` in `pm-runtime` over the file form `pending/<entry_id>.json` of `PendingEntryStore`; this plan replaces that port, that store, `EntryId` and the codec by the directory form (deliverable 4), so an adapter built before it would be deleted by it, and one built after it has no port to implement. The earlier draft (`pending-entry-reader-adapter`, never run) stays as the source of the design: its spec is `plans/PLAN-116-pending-entry-reader-adapter.md` in the closed ledger of the epic `pm-mcp-core-m2-m4`. This plan therefore (a) provides the pm-runtime reader of the directory form (the new package `de.planmarshall.runtime.entry`, confined like `QuestionLogRecordReader`, the three results of the draft's deliverable 1 and its tests); (b) closes the port gap of the Open Defect "The port `QuestionRecordReader` cannot address a pending entry": `ReadRequest(@Nullable String scopeId, String questionId)` (`QuestionRecordReader.java:48`; the `Optional` was converted by PLAN-V2-97 slice B, #347) names a plan, an epic or the workspace only, so a `question` state of a pending entry (a `questions.jsonl` of the entry directory, `QuestionLog.ofEntry`) cannot be resolved; the request gains the entry address and `QuestionLogRecordReader` resolves it; (c) records the trust assumptions of the reader (nothing mentions out-of-band change or check-time limits today; the draft's deliverable 3c). Verify-first: re-read the draft's spec and the two ports at the head before writing the class names; this item adds three Expected Surface entries below.

14. (moved to PLAN-V2-133, deliverable 14) a pull request that changes an instruction file is flagged at its screening.

**Done when** (the package file's Done When is a Watch bullet and a Tests bullet; the epic adds the Sonar clause): Watch: PM-WATCH-STORE-42 (a start entry leaves no residue) holds (PM-WATCH-STORE-58 and PM-WATCH-DISP-75 are PLAN-V2-133's). Tests: An entry is opened as its directory, and its creation moves the sources, the transcript, and the decision stores into the new scope and deletes the directory last; a tampered entry pauses only that entry until its edit is accepted or it is abandoned; each stored source has its manifest entry; a pending entry older than a day is still offered and no sweep removes it, while the directory of an entry already started or abandoned whose removal a stop cut off is removed by the sweep; `PendingEntrySchemaParityTest` and `AuditSchemaParityTest.enums` run without `@Disabled` and pass. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-10
- Specification: the sections the tasks name; Timeouts § Pending Entry; Runtime Lifecycle § Periodic Sweep
- Watch: Store Schemas (PM-WATCH-STORE-42)

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/start-source-intake.adoc` § What, Trace, Done When on `origin/main` at `4617865` (12 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: rescoped 2026-11-10: absorbed by the Grounding corrections section and the replaced deliverable, constraint and surface text of the spec. doc/plans/start-source-intake.adoc on origin/main has Tasks (13), no section What and not 12 bullets; new task 9 (an instruction-file change in a pull request flagged at screening) and task 13 (schema parity) are absent from the spec deliverables
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-V2-17 (`project-onboarding-unit`)) — read at `doc/plans/start-source-intake.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: the row is still open (README.adoc:114-118) but Depends on is now machine-onboarding-unit and project-onboarding-unit; operator-wait-and-machine-scope is gone because PLAN-42 shipped (#203); the spec lists PLAN-42 as met
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: all listed paths exist at origin/main; workflow/screening and workflow/primitive are package-info only; runtime/entry and workflow-screening.adoc are absent as hypothesised
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (81), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `4617865`
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: holder tests hold (Core 5, Workflow 5 unchanged) but Runtime has 96 LogRecord constants, not 81; the spec's Constraints use the next free identifier, not a count
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: the need for messages holds (D1 rate_limited, D4 integrity_pause, D3 retires PM_RUNTIME-23) but the holder is no longer outside the Expected Surface (the spec lists RuntimeLogMessages and its test) and ADR 019 adds a committed generated listing doc/log-messages/pm-runtime.adoc that a record change must regenerate
- HYPOTHESIS: whether the package makes an HTTP call is decided at the specification sections of the trace starting set; the two HTTP constraints bind only if the trace matrix shows one, and only in `pm-runtime` or a provider module — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: D1 forge fetch is the HTTP call: HttpClient.java:223-224 returns RATE_LIMITED with resetAt; the HTTP constraints bind only in pm-runtime or a provider module
- OBSERVED: the specification and watch parts of the Expected Surface changed since the claims were last grounded at `c281f6c` only as listed in the verdict — `git diff --numstat c281f6c..4617865`; the executing plan re-reads them at its branch point
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: bounded to the diff c281f6c7..4617865 over the Expected Surface parts; all eight parts changed again after 4617865, so the plan must re-read at its branch point as the claim says
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `start-source-intake`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: README.adoc:114-118 start-source-intake is open and depends on two open rows; the code still has pending/<id>.json and PeriodicSweep.PENDING_ENTRY_MAX_AGE (PeriodicSweep.java:53); open-pull-request part unverifiable

## Expected Surface

- OBSERVED: `doc/plans/start-source-intake.adoc` — the package file, deleted when the package is done (by the last of this plan and PLAN-V2-133, which still implements part of it)
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — PM-WATCH-STORE-42 (a start entry leaves no residue); PM-WATCH-STORE-58 (untrusted lessons) is PLAN-V2-133's
- OBSERVED: `doc/specification/filesystem-layout/03-project-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/01-format-and-session-log.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/timeouts/02-runtime-leases-operator-web.adoc` — specification part; the implemented sections deleted
- OBSERVED: `pm-core/doc/adr/core-store/036-entry-question-file-moved-by-the-store.adoc` — accepted; D4 needs it superseded
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java` — file form pending/<entry_id>.json becomes the directory form
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryCodec.java` — entry.json record of the directory form
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryRecord.java` — the entry record of the directory form
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/EntryId.java` — replaced with the file form
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QuestionLog.java` — entry questions and decisions moved into the new scope at creation (moveEntryQuestions precedent)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/PendingEntryReader.java` — replaced with the file form
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/QuestionRecordReader.java` — item 13: `ReadRequest` gains the entry address
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/question/QuestionLogRecordReader.java` — item 13: resolves the entry address
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/entry/` — item 13: new package, the reader of the entry directory (from the draft of the earlier reader-adapter plan; verify-at-outline the class names)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java` — PENDING_ENTRY_REMOVED
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/primitive/` — the move at creation; package-info only
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/PeriodicSweep.java` — PENDING_ENTRY_MAX_AGE goes
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java` — the age-based step D3 retires
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/SweepStep.java` — javadoc reference to the retired step
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/log/RuntimeLogMessages.java` — PM_RUNTIME-23 PENDING_ENTRY_RECORDS_SWEPT
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java` — the directory form
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryCodecTest.java` — entry.json codec
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/EntryQuestionMoveTest.java` — one workspace transaction, directory deleted last
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntrySchemaParityTest.java` — re-enabled; it reads `pending-entry.schema.json` through `SchemaResource` (no heading anchor any more), which becomes the schema of `entry.json`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/spi/EnginePortsTest.java` — covers the PendingEntryReader port
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/PeriodicSweepTest.java` — an old entry still offered, no age sweep
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java` — only a started or abandoned entry's cut-off directory is removed
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/PeriodicSweepKilledRuntimeIT.java` — the sweep after a stop
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/log/RuntimeLogMessagesTest.java` — holder test if PM_RUNTIME-23 goes
- OBSERVED: `doc/log-messages/pm-runtime.adoc` — the committed generated listing of `RuntimeLogMessages` (ADR `doc/adr/019`): a record added (deliverable 4, at the next free identifier; the record of deliverable 1 is PLAN-V2-133's) or retired (PM_RUNTIME-23 `PENDING_ENTRY_RECORDS_SWEPT`, `RuntimeLogMessages.java:322`, with deliverable 3) is regenerated in the same pull request, and the holder test compares the file byte for byte; `doc/log-messages/pm-core.adoc` and `pm-workflow.adoc` likewise if those holders change
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/pending-entry.schema.json` — the schema of the entry record; becomes the schema of `entry.json` (deliverables 4 and 12)
- OBSERVED: `pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-start.adoc` and `runtime-question.adoc` — extended (R06); the ADR directories `pm-core/doc/adr/core-store/` and `pm-runtime/doc/adr/runtime-start/` exist, a new ADR takes the next free number
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-entry.adoc` and `pm-runtime/doc/adr/runtime-entry/` — created with the new package of deliverable 13

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 8: 2 corroborated, 3 contradicted (re-scoped by this section), 3 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 2: `runtime/web/` is TLS state only (`Der`, `WebState`, `WebTls`, `WebListenerConflictException`); `workflow/unit/` is six loader files; `workflow/screening/` is package-info only; the start units and routes are not here.
- Claim 3: `RuntimeLogMessages` holds 89 records (not 81); Core 5 and Workflow 5; the three holder tests hold; `PM_RUNTIME-23` `PENDING_ENTRY_RECORDS_SWEPT` is still present.
- Claim 6: three declared specification parts changed since `88ec9c70`: `model-work/07` (#189), `state-catalogue/09` (#189), `store-schemas/10` (#188, #189); `filesystem-layout/03`, `runtime-lifecycle/01`, `store-schemas/01`, `timeouts/02` are unchanged.

### Surface

- Added to the Expected Surface above: ADR `core-store/036`, the two sweep classes, `workflow/primitive/`, the store-schemas watch, the `runtime-start` ADR directory, `doc/Traceability.adoc`.
- wrong: `pm-runtime/.../runtime/web/` is TLS state only (no entry endpoint or file listing can live there: `pm-mcp-server`). `pm-workflow/.../workflow/unit/` is loaders only; the start units are `plan-marshall-mcp` content.

### Specification changes carried (not yet in the Deliverables above)

- **#189 (`1c9f366d`) — Model Work 07 "Trusted" no longer includes a lesson derived from untrusted text (the new field `derived_from_untrusted`): a start screens it like an external source and it stays marked untrusted.** D6's "the operator's text and documents on the machine are never screened" stays true, but a lesson is no longer always trusted; D5, D7 and D8 `source-lesson.*` now include `source-lesson.screen`, `.flagged`, `.unscreened` (State Catalogue 09 renames the source-lesson outcome `stored_untrusted` to `source-lesson.screen`); **D7's "(source-issue.unscreened, source-pr.unscreened)" is incomplete**; the manifest trust rule in Filesystem Layout 03 (`trusted` for what is on this machine) was not updated. Store Schemas 10 adds the required lesson member `derived_from_untrusted` and `PM-WATCH-STORE-58`; the lesson-record code does not exist yet. Widen D5 and D7 to `source-lesson` screening or name the owner at the outline.
- #188 — Store Schemas 10 job-record `gate_facts` gains a required `side_state`: not this package's; `JobRecordSchemaParityTest` copies `gate_facts` verbatim and is unaffected. Credentials and Enrolment 03 § Closed List of Outbound Calls: the forge fetches of D1 are configured providers and are on the list; they must stay in the provider modules (`pm-provider-http` over `cui-http` `HttpHandler`, `Outcome.RATE_LIMITED` with `resetAt`), and the "no connection" integration test must use a local fake, not a forge.
- #187 — Job Runtime 02 § Worker Read Set: no effect (the screener is `zero_tool` and gets text in-band; `pending/<id>/sources/` lies under `<PM_MCP_BASE>`, outside every job's read set).

### Shipped or superseded since the last grounding

- #190: the supervisor start of a screener (`WorkerStartRequest.zeroToolPrompt`, `WorkdirKind.JOB_PRIVATE`) and `WorkerProtocol.closeUnavailable`; D7's fail-closed must map the model caller's `role_unqualified` and `role_undefined` (PLAN-26) **and** the start-failure guard's `role_unavailable` close onto `source-<kind>.unscreened`, never onto `suspicious`. Screening cannot run end to end in production until something drives the supervision loop (no owner). Still absent: the `entry.json` directory form, `decisions.jsonl`, `chat/`, `sources/`, the `rate_limited` source cause, `integrity_pause`, `documents_mode`. `PendingEntrySchemaParityTest` is still `@Disabled` and its anchor `=== Pending Entry Record` no longer exists (the heading is `== Pending Entry (pending/<entry_id>/)`).
- The spec's Blocks line omits `access-grants`, which the index lists as dependent on `start-source-intake`.

### Open questions for the outline

- Which package carries the lesson remedy into the intent (D8, #141 earlier); whether D5 and D7 widen to `source-lesson.*` and who owns it; superseding ADR 036; the anchor repoint for `PendingEntrySchemaParityTest`; where the entry service and routes live; whether the residue sweep keeps `PM_RUNTIME-23` and `pending_entry_removed`.

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 8: 4 corroborated, 2 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 2: 2 of the 6 hypothesis paths are wrong at HEAD: pm-runtime/.../runtime/web/ holds only Der; WebState; WebTls; WebListenerConflictException (TLS and web state; no /api/v1 route in any pm-* module; the listeners are pm-mcp-server per module-structure.adoc) and pm-workflow/.../workflow/unit/ holds only loaders (DefaultUnitLoader; ProjectUnitLoader; PlanUnitMerger; VerificationContext) while default units are classpath:/workflows/ of pm-mcp-server (plan-marshall-mcp doc/plans/default-units-and-drift-tests.adoc); core/store; core/spi; workflow/screening; runtime/start exist; the 9 OBSERVED paths all exist
- Claim 3: structure holds (CoreLogMessagesTest; RuntimeLogMessagesTest; WorkflowLogMessagesTest each refuse a duplicate id and an out-of-range id) but the count drifted: Core 5 and Workflow 5 hold; RuntimeLogMessages has 83 static final LogRecord at HEAD not 81 (HARNESS_SELF_UPDATE_ACCEPTED and WORKER_LAUNCH_REFUSED added by #172 4806a304)

### Surface

- Added to the Expected Surface above: `pm-runtime/src/main/java/de/planmarshall/runtime/log/RuntimeLogMessages.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/log/RuntimeLogMessagesTest.java`, `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java`, `doc/LogMessages.adoc`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntrySchemaParityTest.java`.
- understated: core/store: add PendingEntryStore; PendingEntryCodec; PendingEntryRecord; EntryId (file form pending/<entry_id>.json) and tests PendingEntryStoreTest; PendingEntryCodecTest; EntryQuestionMoveTest; QuestionLog already holds pending/<entry_id>/questions.jsonl and moveEntryQuestions (ADR pm-core/doc/adr/core-store/036 keeps the record flat and must be superseded)
- wrong: workflow/unit holds loaders only; the start units are plan-marshall-mcp classpath:/workflows/ (adjacent); the move at creation (start.create-scope) belongs in pm-core/.../core/store/ (QuestionLog.moveEntryQuestions precedent) and pm-workflow/.../workflow/primitive/ (package-info only)
- understated: runtime/start: add PeriodicSweep; PendingEntryRecordsSweep; SweepStep; package-info.java (line 90); tests PeriodicSweepTest; PendingEntryRecordsSweepTest; PeriodicSweepKilledRuntimeIT; plus pm-runtime/.../runtime/log/RuntimeLogMessages.java PM_RUNTIME-23; pm-runtime/src/test/.../log/RuntimeLogMessagesTest.java; doc/LogMessages.adoc row; pm-core/.../service/AuditEvent.java PENDING_ENTRY_REMOVED
- wrong: runtime/web holds TLS and web state only; no entry endpoint or file listing can live there; routes /api/v1 are pm-mcp-server (adjacent); name the service package or drop the path
- understated: add pm-core/src/test/.../store/PendingEntrySchemaParityTest.java (anchor line still startsWith the heading '=== Pending Entry Record' which no longer exists; heading is '== Pending Entry (`pending/<entry_id>/`)' in store-schemas/01); AuditSchemaParityTest needs no change
- understated: add pm-providers/pm-provider-http (HttpClient; Response resetAt) as the consumed carrier of Deliverable 1 and the issue/PR fetch of pm-provider-github and pm-provider-gitlab (read; not changed unless the outline says so)

### Specification changes carried (not yet in the Deliverables above)

- #141 — store-schemas/10 Lesson front matter and Remedy (remedy required; a plan started with the lesson carries the remedy in its intent): State in D8 which package owns carrying the lesson remedy into the intent (source-lesson.* of start-from-clients or this one); no new path since store-schemas/10 is already in the surface

### Shipped or superseded since the last grounding

- partly fixed: the question half of the entry's decision stores: pm-core QuestionLog.ofEntry gives pending/<entry_id>/questions.jsonl and moveEntryQuestions moves it into the new scope (#93 3323ce7b; EntryQuestionMoveTest); decisions.jsonl chat/ sources/ entry.json do not exist
- partly fixed: Deliverable 12 second half: AuditRecord.Category has transition; job; operator; security; credential; store (no identity) and Scope.ENTRY exists; AuditSchemaParityTest.enums has no @Disabled
- partly fixed: forge rate limit carrier: pm-provider-http HttpClient maps 429 and exhausted 403 to Outcome.RATE_LIMITED with resetAt (#156 3672829a); the source-level cause rate_limited and resets_at is not built
- partly fixed: integrity evidence: pm-core ScopeIntegrity yields the store_tampered evidence; the per-entry integrity_pause hold is absent

### Open questions for the outline

- ADR core-store/036 (accepted) keeps PendingEntryStore flat and moves the entry questions in two transactions while Deliverable 4 replaces the file form and wants one workspace transaction; the spec does not say the ADR is superseded
- PendingEntrySchemaParityTest anchors on the heading '=== Pending Entry Record' which store-schemas/01 no longer has; re-enabling needs the anchor repointed and the member list rechecked against the directory-form schema
- Deliverable 10 and 11 (entry endpoints; listing of files; loopback only) name pm-runtime but no route layer exists there; whether the service lives in pm-runtime and the route in pm-mcp-server is not stated
- audit id pending_entry_removed and log record PM_RUNTIME-23 exist in code and doc/LogMessages.adoc but appear nowhere in doc/specification; the spec does not say whether the residue sweep keeps them
- the Blocks line omits access-grants (index row added by #151 depends on start-source-intake)

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-46 landed (#233): the dependency is met for the stores; the plan axes are still owed by PLAN-V2-43.
- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-42 (`operator-wait-and-machine-scope`, shipped #199 and #203: met); PLAN-46 (`machine-onboarding-unit`, part 1 of 3, shipped #233: met for the stores); PLAN-V2-17 (`project-onboarding-unit`, staged); PLAN-V2-01 (`primitive-failure-reasons`) (the primitives of this plan follow the settled SPI)
- Blocks: PLAN-V2-23 (`decision-engine`); PLAN-V2-24 (`start-from-clients`); PLAN-V2-29 (`chat`); PLAN-V2-41 (`operator-reports`); PLAN-V2-69 (`process-designer`); the package `access-grants` (index row, `doc/plans/README.adoc:288-292`)
- Blocked deliverables move to PLAN-V2-133 (`start-source-intake-sources-and-screening`, parked: Milestones 10 and 12 are outside the staging window): deliverables 1, 2, 6, 7, 8, 9, 11, 14, the route part of 10 and the epic-ledger part of 5; with them go the dependencies only they need, PLAN-V2-43 (the key `sources.documents_mode` of deliverable 2) and PLAN-V2-16 (the lesson store of deliverable 8)
- PLAN-V2-133 depends on this plan (it builds on the directory form, the manifest and the move at creation of deliverables 3 to 5)
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-22-start-source-intake.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **The port `QuestionRecordReader` cannot address a pending entry** (Op18, decided 2026-10-30: this plan owns it) — `ReadRequest(@Nullable String scopeId, String questionId)` names a plan, an epic or the workspace, nothing else, so a `question` state of a pending entry (an entity of the workspace scope) cannot be resolved through the adapter PLAN-88 shipped; the port change belongs to `pm-core` `core.spi`, its adapter to `pm-runtime`; the `Optional` conversion (PLAN-V2-97 slice B, #347) is done: the scope parameter is `@Nullable` already

## Rescope and carries (2026-11-09)

Added by the orchestrator after the operator's walk-through of the open defects (decision log, 2026-11-09). Read at the outline, re-check each statement against `origin/main` (Claim Labels) and adopt it into a deliverable of the outline or say in the landing why not. It extends no Done-when by itself.

- **Rescope (audit, operator 2026-11-09):** only the Milestone 3 part, with the D13 names (decision log). It depends on PLAN-V2-17, which stays staged.
- **Carried:** the port `QuestionRecordReader` cannot address a pending entry (`ReadRequest(@Nullable String scopeId, String questionId)` at `pm-core/.../spi/QuestionRecordReader.java:48`, `@Nullable` since PLAN-V2-97 slice B #347; operator decision 2026-10-30: this plan owns the entry address); the `unscreened` clause of PLAN-97 residue (3) belongs to deliverable 7 and moved with it to PLAN-V2-133.

## Placement of endpoints and default units (operator decision 2026-11-09)

Companion package outside, logic stays here: the routes and endpoints named under `pm-runtime/.../web/` are not built here (`/api/v1` routes are `pm-mcp-server` classes of `plan-marshall-mcp`; `runtime/web` holds listener state and certificate only), and the `.pmwf` default units are owed to `plan-marshall-mcp` as for PLAN-V2-17. This plan keeps the framework-free logic as a `pm-runtime` package (the `QuestionRecordReader` addressing of item 13; the forge fetch and the intake moved to PLAN-V2-133). Its declared surface keeps the `web` path until the outline decides which files are real, so the gate still sees the overlap.

## Grounding corrections (cleanup 2026-11-10, main f9e7011f)

Claim 0: `doc/plans/start-source-intake.adoc` on origin/main (restructured by the design audit #286) has no section What and not 12 bullets. It has 13 numbered Tasks, a Trace, and a Done When of three watches (PM-WATCH-STORE-42, -58, PM-WATCH-DISP-75) plus one Tests bullet. The mapping to the deliverables:
- Task 1 is deliverable 4.
- Task 2 is deliverable 3.
- Task 3 is deliverable 5.
- Task 4 is deliverable 9.
- Task 5 is deliverable 1.
- Task 6 is deliverable 2.
- Task 7 is deliverable 6.
- Task 8 is deliverable 7.
- Task 9 is NEW: a pull request that changes an instruction file, flagged `suspicious` at its screening (Model Work § A Pull Request That Changes an Instruction File, `model-work/07-untrusted-text-screening.adoc:36`, PM-WATCH-DISP-75). It is added as deliverable 14 in one sentence.
- Task 10 is deliverable 8.
- Task 11 is deliverable 10.
- Task 12 is deliverable 11.
- Task 13 (schema parity) is deliverable 12. It is NOT absent: the package file names only `PendingEntrySchemaParityTest`, but its Done When still says `AuditSchemaParityTest.enums` without `@Disabled`, so deliverable 12 stays as written.
- Deliverable 13 is not a task of the package file; it is the operator's fold-in of 2026-10-29 and stays.

The Done-when gains the Watch bullet of the package file and the test for deliverable 14; the Trace gains the Watch line. The Deliverables introduction line, the line "The package file introduces itself" (that paragraph no longer exists in the file) and the Done-when opener are replaced.

Claim 1 stays absorbed, with one addition. The row is `open` and depends on `machine-onboarding-unit` and `project-onboarding-unit` (`README.adoc:114-118`); PLAN-42 (#199, #203) is met and PLAN-46 (#233) is met for the stores. PLAN-V2-43 `machine-settings-and-axes` (staged) is the rest of `machine-onboarding-unit` and writes the machine key `sources.documents_mode` through `settings.documents-mode`, which deliverable 2 reads. The queue spec of PLAN-V2-43 lists this plan under Blocks, and the Dependencies line now names it. The index also has `access-grants` depending on this package (`README.adoc:288-292`); the Blocks line now says so.

Claim 3: the holder tests hold. `CoreLogMessages` has 5 records and `WorkflowLogMessages` 5. `RuntimeLogMessages` has 96 `static final LogRecord`, not 81 (the 83 and 89 of the earlier records are also superseded). The spec cites no count outside the Claim Labels. The identifier rule stays "the next free identifier of the level".

Claim 4: the need for messages holds. Deliverable 1 (`rate_limited`) and deliverable 4 (the entry integrity pause) add records, and deliverable 3 retires PM_RUNTIME-23 `PENDING_ENTRY_RECORDS_SWEPT` (`RuntimeLogMessages.java:322`, identifier 23). But the holder is no longer outside the Expected Surface: the spec lists `RuntimeLogMessages` and its test. ADR `doc/adr/019` adds a committed generated listing, `doc/log-messages/pm-runtime.adoc`. A record change must regenerate it in the same pull request, and the holder test compares it byte for byte. The listing is added to the Expected Surface.

Further stale premises found while reading the code and specifications at this head (not in the contradiction file):
- Deliverable 4 names `decisions.jsonl` and an `integrity_pause` hold. Store Schemas § Pending Entry (`store-schemas/01-format-and-session-log.adoc:55-58, 42-43`) names `questions.jsonl` and `decision-log.jsonl`, and a hold of kind `integrity_hold`. `integrity_pause` appears in no specification. `filesystem-layout/03` lists the entry directory differently (`entry.json`, `sources/`, `chat/`, `grants.jsonl`); `grants.jsonl` belongs to the package `access-grants`, not built here, and the outline reconciles the two member lists on the Store Schemas side.
- Deliverable 13 and the Carried and Rescope sections cite `ReadRequest(Optional<String> scopeId, String questionId)`. PLAN-V2-97 slice B (#347) made it `ReadRequest(@Nullable String scopeId, String questionId)` (`pm-core/.../spi/QuestionRecordReader.java:48`), so the conversion is done. Adding the entry address remains this plan's job.
- Deliverable 12: `PendingEntrySchemaParityTest` is still `@Disabled`, but it no longer anchors on a heading. It reads `pending-entry.schema.json` from the class path through `SchemaResource`. The file becomes the schema of `entry.json`. The "anchor repointed" wording is obsolete.
- The lesson half of deliverables 6 to 8 has moved. `LessonStore` (PLAN-V2-16, shipped) has `claim` (:453), `release` (:488) and `endClaimApplied` (:526, retire with verdict `applied`, `by` the plan), and `LessonFrontMatter` has `derivedFromUntrusted`. State Catalogue 09 line 355 lists `source-<kind>.unscreened` for `source-issue`, `source-pr` and `source-lesson`. So deliverable 7's list is widened and deliverable 6's "never screened" gets the lesson exception. This resolves the open question of the 2026-10-26 record, and PM-WATCH-STORE-58 is in the package's Done When.
- Deliverable 1: the carrier exists. `pm-provider-http` `HttpClient` answers `Outcome.RATE_LIMITED` with `resetAt` (`HttpClient.java:224`); the source-level cause is what is built here.

Expected Surface entries replaced or added: the `PendingEntrySchemaParityTest` entry is re-worded; the generated listing, `LessonStore`, `HttpClient`, `pending-entry.schema.json`, the developer files and ADR directories (and the new `runtime-entry` guide for the new package of deliverable 13) are added.

Asks for the operator at refine: (1) scope and split: the section "Rescope and carries (2026-11-09)" says this plan is only the Milestone 3 part, with the D13 names, but the package file still has 13 tasks (index row: Milestones 3, 10, 12) and this spec has 14 deliverables, far beyond six; recommended: split, keeping here the Milestone 3 entry work (deliverables 3, 4, 5 without the epic-ledger part, 12 and 13) and moving deliverables 1, 2, 6, 7, 8, 9, 11 and the new 14 to a successor plan under the next free PLAN-V2 number with an edge to this plan; deliverable 10 (entry addressing) names routes that live in `pm-mcp-server`, so decide at the same time whether it stays only as the framework-free part; if the operator keeps one plan the outline must cut it; (2) the new deliverable 14 (the instruction-file flag) builds on the screener and on the `source-pr` unit of the parked `start-from-clients`; recommended: keep it next to deliverable 6 in whichever plan carries it; it is not Milestone 3 work.

**Settled asks (2026-11-10, orchestrator, confidence above 90% after reading the code and specifications at origin/main f9e7011f; the operator asked for every question above 90% to be settled):**
- Deliverable 14 (the instruction-file flag) stays next to deliverable 6 in whichever plan carries the screening: it builds on the `source-pr` unit and on deliverable 6, and the roadmap puts both in Milestone 10 (`roadmap.adoc:636`).
- Still with the operator: the split of this plan (14 deliverables).

Split 2026-11-10 (operator, Q11): deliverables 1, 2, 6, 7, 8, 9, 11, 14 and the route part of 10 moved to PLAN-V2-133. The epic-ledger part of deliverable 5 moved with them, and nothing of deliverable 10 is separable here.
