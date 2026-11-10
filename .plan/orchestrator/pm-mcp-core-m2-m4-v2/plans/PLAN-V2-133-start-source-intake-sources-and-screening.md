# PLAN-V2-133: The intake of the sources: rate limits, documents, screening, the claimed lesson, the entry routes and an epic's sources

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the split of PLAN-V2-22 `start-source-intake` (operator decision of 2026-11-10, Q11 of the issues pass); PLAN-76 of the epic pm-mcp-core-m2-m4
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-133-start-source-intake-sources-and-screening.md` and is queued as one row file, `queue/PLAN-V2-133.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> **STAGED AND PARKED:** the roadmap Milestones 10 and 12, which this plan serves, are outside the staging window; the plan is not emitted until the operator lifts the park.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the part of the work package `start-source-intake` of `doc/plans/start-source-intake.adoc` that PLAN-V2-22 does not take: the intake of the sources. Serves: roadmap Milestone 10 (the source directory, the screening of the sources, documents copied or moved), Milestone 12 (an epic's sources in its ledger); starting work in both clients of `pm-mcp-clients`. Where: pm-core (the epic's sources in its ledger and the epic header's sources); pm-workflow (the screening, the claim of a lesson and its end); pm-runtime (the listing of files, loopback only); the provider modules are read (the forge fetch), not changed; the entry routes are `pm-mcp-server` classes of `plan-marshall-mcp` and are not built here (see Carried Open Items). This plan was split off PLAN-V2-22 on 2026-11-10 (operator decision, Q11 of the issues pass), because the package file covers Milestones 3, 10 and 12 and Milestones 10 and 12 are outside the staging window; PLAN-V2-22 keeps the Milestone 3 part (the entry as a directory, its opening and consumption, the schema parity and the entry address of the question port), and this plan builds on its directory form, its manifest and its move at creation, so it depends on it. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

The package file used to introduce itself (the design audit #286 removed that paragraph); the epic keeps the sentence as the list of sections the tasks implement. This plan implements State Catalogue § Documents as a Source; Model Work § What Is on This Machine Is Trusted and § Screened Once Stored; Store Schemas § A Claimed Lesson Ends with the Landing; the epic-ledger part of Filesystem Layout § The Source Directory; the entry's address in Runtime Model § Events per Scope (`PM-WF-10`, `PM-SEC-1`). PLAN-V2-22 implements Filesystem Layout § The Source Directory (the directory, its manifest and the move at creation) and Store Schemas § Pending Entry. It builds on the setup gate, the source units, and the other sources of `start-from-clients`.

## Deliverables

The tasks of `doc/plans/start-source-intake.adoc` § Tasks (restructured by the design audit #286; the specification sections each task names are the single statement of what it builds) that map onto the deliverables below: task 3 is deliverable 5 (its epic-ledger part), task 4 is 9, task 5 is 1, task 6 is 2, task 7 is 6, task 8 is 7, task 9 (a pull request that changes an instruction file, flagged at its screening) is the new deliverable 14, task 10 is 8, task 11 is 10, task 12 is 11. The deliverables keep their numbers from PLAN-V2-22, which keeps deliverables 3, 4, 5 (without the epic-ledger part), 12 and 13, so the numbers below are not contiguous. The wording of the deliverables is the epic's elaboration (the package file no longer states it); where it differs from the Grounding corrections section, that section is the later reading.

1. Rate limits: a fetch of an issue or a pull request that the forge refuses for its rate limit, with or without a credential, fails the source with the cause `rate_limited` and `resets_at` from the forge's answer (the carrier exists: `pm-provider-http` `HttpClient` answers `Outcome.RATE_LIMITED` with `resetAt`, `HttpClient.java:224`; the source-level cause is what is built here, and the fetch stays in the provider modules); it is not retried by itself, and the operator tries again or adds a credential for the forge. It builds on the source units `source-issue` and of the pull request of `start-from-clients`.
2. Documents, copy or move: the machine key `sources.documents_mode` (`copy`, `move`, or `ask`, the default); with `ask` the operator wait `source-documents.mode` with _copy_ preselected, where _always_ stores `copy` or `move` as the machine's value; otherwise straight to `source-documents.store`. A move removes a file git tracks from git through the short landing `git.land-removal`, landed by the project's landing (`landing.pull-request` or `landing.main`); with `landing.files` the removal stays uncommitted in the working tree. It builds on the source unit `source-documents` of `start-from-clients`.
5. The epic-ledger part of the source directory and the move at creation (the rest of deliverable 5 of PLAN-V2-22 stays there: the move into `plans/<plan_id>/sources/`, the transcript, the entry's decision stores, the manifest, `origin` and `request.md`): for an epic, `start.create-scope` moves the source directory into the epic's ledger (deliverable 9), the epic header lists the sources, and `epic.decompose` reads them. It builds on the steps `start-epic.open` and `start.create-scope`, on deliverable 5 of PLAN-V2-22 and on the source units of `start-from-clients` (`source-documents`, `source-issue`, `source-lesson.*`, `source-local-changes.*`).
6. Screened once stored: fetch, level-1 validator, store, screener over the stored copy, the verdict in the manifest; nothing but the screener reads an external source before a clean verdict or the operator's acceptance; the operator's text and the documents on the machine are never screened, while a lesson is screened only when it is `derived_from_untrusted` (Model Work § What Is on This Machine Is Trusted, PM-WATCH-STORE-58; the member is in `LessonFrontMatter`). It builds on the source units of `start-from-clients`.
7. Fail closed: a screener that fails, or a role `screener` with no qualified configuration, leaves the source unscreened with the cause in the operator wait `source-<kind>.unscreened` (`source-issue.unscreened`, `source-pr.unscreened`, `source-lesson.unscreened`: State Catalogue 09 lists the three, and a lesson reaches the screener only when it is `derived_from_untrusted`): try again, remove it, or accept it explicitly as untrusted, recorded with its reason like an accepted flagged source; a failure is never shown as `suspicious`. It builds on the source units of `start-from-clients`.
8. A claimed lesson ends with the landing: the lesson of a source is claimed at the plan's creation and retired when the plan lands its changes (the merge of `landing.pull-request`, the push of `landing.main`; verdict `applied`, `by` the plan), released when it ends without landing them (handed back with `landing.files`, cancelled, abandoned, released, aborted, deleted), the lesson staying `active`. The lesson store exists (PLAN-V2-16): `LessonStore.claim` (:453), `release` (:488) and `endClaimApplied` (:526, the retirement with the verdict `applied`, `by` the plan); this deliverable wires the ends of a plan to them. It builds on the lesson source `source-lesson.*` of `start-from-clients`.
9. An epic's sources in its ledger (`.marshall/epics/<epic_id>/sources/`, committed and landed), so that adoption and every decomposition read them.
10. Entries addressed like plans and epics: created under their project (`POST /api/v1/projects/{project_id}/entries`, the kind `plan` or `epic`), addressed at the top level by id (`/api/v1/entries/{entry_id}`, its events at `/api/v1/entries/{entry_id}/events`); `POST /api/v1/entries` goes.
11. Loopback only: the listing of files, a move, and documents outside the checkout only from the terminal client and the loopback listener.
14. A pull request that changes an instruction file, flagged at its screening (task 9 of the package file, new; Model Work § A Pull Request That Changes an Instruction File, PM-WATCH-DISP-75): a pull request the operator did not author whose changed files include an instruction file of any harness, at any depth (the instruction files of the Harness Integration Table in `plan-marshall-documentation`, `doc/specification/harness-integration/01-harness-detection.adoc#instruction-files`, matched ignoring case and after NFC normalisation), is flagged `suspicious` at its screening with those files as its reasons, whatever the screener's verdict on its text, so that no worker runs on its head before the operator accepted it with a reason or removed it. It builds on the source unit `source-pr` of `start-from-clients` and on deliverable 6.

**Done when** (the package file's Done When is a Watch bullet and a Tests bullet; the epic adds the Sonar clause; the Watch PM-WATCH-STORE-42 and the tests of the entry as a directory, the sweep and the schema parity are PLAN-V2-22's): Watch: PM-WATCH-STORE-58 (a lesson written from untrusted text stays marked, and a start screens it) and PM-WATCH-DISP-75 (a pull request that changes an instruction file waits for the operator before a worker runs on it) hold. Tests: no model but the screener reads an external source before its verdict, shown by a test that fails the screening and one whose screener fails or has no qualified configuration, leaving the source unscreened until tried again, removed, or accepted as untrusted; a rate-limited fetch of an issue and of a pull request fails with `rate_limited` and `resets_at` and is not retried; a pull request of another author that changes an instruction file of any harness is flagged `suspicious` at its screening with those files as its reasons, whatever the screener's verdict, and a pull request without such a file is not (matched control); documents are asked copy or move only with `ask`; a claimed lesson is retired on a merge and on a push to the base branch and released on every other end. The Done-when of the package names no test for the epic-ledger part of deliverable 5 and for deliverables 9, 10 and 11; the outline names one each. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-10; PM-SEC-1
- Specification: the sections the tasks name; Model Work § Two-Level Guard and § A Pull Request That Changes an Instruction File
- Watch: Store Schemas (PM-WATCH-STORE-58); Model Work (PM-WATCH-DISP-75)

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/start-source-intake.adoc` § What, Trace, Done When on `origin/main` at `4617865` (12 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-V2-17 (`project-onboarding-unit`)) — read at `doc/plans/start-source-intake.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (81), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `4617865`
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
- HYPOTHESIS: whether the package makes an HTTP call is decided at the specification sections of the trace starting set; the two HTTP constraints bind only if the trace matrix shows one, and only in `pm-runtime` or a provider module — confirm/refute at the trace matrix (verify-at-outline)
- OBSERVED: the specification and watch parts of the Expected Surface changed since the claims were last grounded at `c281f6c` only as listed in the verdict — `git diff --numstat c281f6c..4617865`; the executing plan re-reads them at its branch point
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `start-source-intake`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `doc/plans/start-source-intake.adoc` — the package file, deleted when the package is done (by the last of PLAN-V2-22 and this plan)
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — PM-WATCH-STORE-58 (untrusted lessons)
- OBSERVED: `doc/specification/filesystem-layout/03-project-store.adoc` — specification part; the implemented sections deleted (the epic's sources in its ledger; the entry directory is PLAN-V2-22's)
- OBSERVED: `doc/specification/model-work/07-untrusted-text-screening.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/09-bootstrap-and-lessons.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/10-job-and-lesson-records.adoc` — specification part; the implemented sections deleted
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/screening/` — the untrusted-text screening; package-info only
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/primitive/` — the move at creation, here its epic-ledger part (deliverable 5); package-info only
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/log/RuntimeLogMessages.java` — the record of deliverable 1 (`rate_limited`), appended at the next free identifier
- OBSERVED: `doc/log-messages/pm-runtime.adoc` — the committed generated listing of `RuntimeLogMessages` (ADR `doc/adr/019`): a record added (deliverable 1, at the next free identifier) is regenerated in the same pull request, and the holder test compares the file byte for byte; `doc/log-messages/pm-core.adoc` and `pm-workflow.adoc` likewise if those holders change
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-screening.adoc` — developer documentation of the package; created
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-screening/` — ADRs of the package's design decisions; new directory
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/LessonStore.java` — `claim`, `release`, `endClaimApplied` (PLAN-V2-16), consumed by deliverable 8; read
- OBSERVED: `pm-providers/pm-provider-http/src/main/java/de/planmarshall/provider/http/HttpClient.java` — `Outcome.RATE_LIMITED` with `resetAt`, the carrier of deliverable 1; read, not changed

## Dependencies and Sequencing

**PARKED:** this plan is staged and not emitted, because the roadmap Milestones 10 and 12 it serves are outside the staging window (operator decision of 2026-11-10, Q11 of the issues pass).

- Depends on: PLAN-V2-22 (`start-source-intake`: the entry as a directory, its manifest and the move at creation, deliverables 3, 4 and 5, which these deliverables build on); PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-42 (`operator-wait-and-machine-scope`, shipped #199 and #203: met); PLAN-46 (`machine-onboarding-unit`, part 1 of 3, shipped #233: met for the stores); PLAN-V2-43 (`machine-settings-and-axes`, staged: the rest of `machine-onboarding-unit`, writer of the machine key `sources.documents_mode` that deliverable 2 reads; it listed PLAN-V2-22 in its Blocks, and the edge now concerns this plan); PLAN-V2-17 (`project-onboarding-unit`, staged); PLAN-V2-16 (shipped) (`lesson-store-operations`) (the lesson store: `LessonStore`, the resolver, claim, release and retire; operator decision 3 of 18); PLAN-V2-01 (`primitive-failure-reasons`) (the primitives of this plan follow the settled SPI)
- Blocks: PLAN-V2-23 (`decision-engine`); PLAN-V2-24 (`start-from-clients`); PLAN-V2-29 (`chat`); PLAN-V2-41 (`operator-reports`); PLAN-V2-69 (`process-designer`); the package `access-grants` (index row, `doc/plans/README.adoc:288-292`) — the list of PLAN-V2-22 before the split; the outline of each says whether it needs the entry directory only (PLAN-V2-22) or also the intake of the sources (this plan)
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-133-start-source-intake-sources-and-screening.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items that this plan owns, taken from the Carried sections of PLAN-V2-22 and its Re-grounding Records at the split. They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **The `unscreened` clause of PLAN-97 residue (3)** (deliverable 7; carried by PLAN-V2-22 in its section "Rescope and carries (2026-11-09)", operator walk-through of the open defects, decision log 2026-11-09).
- **Placement of endpoints and default units (operator decision 2026-11-09):** companion package outside, logic stays here: the routes and endpoints of deliverables 10 and 11 named under `pm-runtime/.../web/` are not built here (`/api/v1` routes are `pm-mcp-server` classes of `plan-marshall-mcp`; `runtime/web` holds listener state and certificate only), and the `.pmwf` default units are owed to `plan-marshall-mcp` as for PLAN-V2-17. This plan keeps the framework-free logic as a `pm-runtime` package (forge fetch, intake). The outline decides which files of deliverables 10 and 11 are real; no framework-free part of deliverable 10 is separable at the split, because the entry address of the question port is deliverable 13 of PLAN-V2-22.
- **Fail closed maps three closes onto one outcome** (Re-grounding Record 2026-10-26, #190): deliverable 7 must map the model caller's `role_unqualified` and `role_undefined` (PLAN-26) and the start-failure guard's `role_unavailable` close onto `source-<kind>.unscreened`, never onto `suspicious`. Screening cannot run end to end in production until something drives the supervision loop (no owner).
- **Which package carries the lesson remedy into the intent** (deliverable 8; Re-grounding Records 2026-10-25 and 2026-10-26, #141): a plan started with a lesson carries the lesson's remedy in its intent; state in the outline which package owns it (`source-lesson.*` of `start-from-clients` or this one).
- **The forge fetches of deliverable 1 are on the closed list of outbound calls** (Credentials and Enrolment 03 § Closed List of Outbound Calls; Re-grounding Record 2026-10-26): they stay in the provider modules (`pm-provider-http` over `cui-http` `HttpHandler`, `Outcome.RATE_LIMITED` with `resetAt`), and the "no connection" integration test uses a local fake, not a forge.

## Grounding corrections (cleanup 2026-11-10, main f9e7011f)

Copied at the split from the section of the same name of PLAN-V2-22, for the claims that concern the deliverables of this plan. The deliverable numbers are the original ones.

Claim 0: `doc/plans/start-source-intake.adoc` on origin/main (restructured by the design audit #286) has no section What and not 12 bullets. It has 13 numbered Tasks, a Trace, and a Done When of three watches (PM-WATCH-STORE-42, -58, PM-WATCH-DISP-75) plus one Tests bullet. The mapping to the deliverables of this plan:
- Task 3 is deliverable 5 (the epic-ledger part is here; the rest of it is deliverable 5 of PLAN-V2-22).
- Task 4 is deliverable 9.
- Task 5 is deliverable 1.
- Task 6 is deliverable 2.
- Task 7 is deliverable 6.
- Task 8 is deliverable 7.
- Task 9 is NEW: a pull request that changes an instruction file, flagged `suspicious` at its screening (Model Work § A Pull Request That Changes an Instruction File, `model-work/07-untrusted-text-screening.adoc:36`, PM-WATCH-DISP-75). It is added as deliverable 14 in one sentence.
- Task 10 is deliverable 8.
- Task 11 is deliverable 10.
- Task 12 is deliverable 11.

The Done-when gains the Watch bullet of the package file and the test for deliverable 14; the Trace gains the Watch line.

Claim 1 stays absorbed, with one addition. The row is `open` and depends on `machine-onboarding-unit` and `project-onboarding-unit` (`README.adoc:114-118`); PLAN-42 (#199, #203) is met and PLAN-46 (#233) is met for the stores. PLAN-V2-43 `machine-settings-and-axes` (staged) is the rest of `machine-onboarding-unit` and writes the machine key `sources.documents_mode` through `settings.documents-mode`, which deliverable 2 reads. The queue spec of PLAN-V2-43 lists PLAN-V2-22 under Blocks, and the Dependencies line now names it. The index also has `access-grants` depending on this package (`README.adoc:288-292`); the Blocks line now says so.

Claim 3: the holder tests hold. `CoreLogMessages` has 5 records and `WorkflowLogMessages` 5. `RuntimeLogMessages` has 96 `static final LogRecord`, not 81 (the 83 and 89 of the earlier records are also superseded). The spec cites no count outside the Claim Labels. The identifier rule stays "the next free identifier of the level".

Claim 4: the need for messages holds. Deliverable 1 (`rate_limited`) and deliverable 4 (the entry integrity pause) add records, and deliverable 3 retires PM_RUNTIME-23 `PENDING_ENTRY_RECORDS_SWEPT` (`RuntimeLogMessages.java:322`, identifier 23). But the holder is no longer outside the Expected Surface: the spec lists `RuntimeLogMessages` and its test. ADR `doc/adr/019` adds a committed generated listing, `doc/log-messages/pm-runtime.adoc`. A record change must regenerate it in the same pull request, and the holder test compares it byte for byte. The listing is added to the Expected Surface.

Further stale premises found while reading the code and specifications at this head (not in the contradiction file):
- Deliverable 4 names `decisions.jsonl` and an `integrity_pause` hold. Store Schemas § Pending Entry (`store-schemas/01-format-and-session-log.adoc:55-58, 42-43`) names `questions.jsonl` and `decision-log.jsonl`, and a hold of kind `integrity_hold`. `integrity_pause` appears in no specification. `filesystem-layout/03` lists the entry directory differently (`entry.json`, `sources/`, `chat/`, `grants.jsonl`); `grants.jsonl` belongs to the package `access-grants`, not built here, and the outline reconciles the two member lists on the Store Schemas side.
- The lesson half of deliverables 6 to 8 has moved. `LessonStore` (PLAN-V2-16, shipped) has `claim` (:453), `release` (:488) and `endClaimApplied` (:526, retire with verdict `applied`, `by` the plan), and `LessonFrontMatter` has `derivedFromUntrusted`. State Catalogue 09 line 355 lists `source-<kind>.unscreened` for `source-issue`, `source-pr` and `source-lesson`. So deliverable 7's list is widened and deliverable 6's "never screened" gets the lesson exception. This resolves the open question of the 2026-10-26 record, and PM-WATCH-STORE-58 is in the package's Done When.
- Deliverable 1: the carrier exists. `pm-provider-http` `HttpClient` answers `Outcome.RATE_LIMITED` with `resetAt` (`HttpClient.java:224`); the source-level cause is what is built here.

Expected Surface entries replaced or added: the generated listing, `LessonStore`, `HttpClient` and the developer file and ADR directory of the screening package are added.

**Settled (2026-11-10, orchestrator, confidence above 90% after reading the code and specifications at origin/main f9e7011f; the operator asked for every question above 90% to be settled):** deliverable 14 (the instruction-file flag) stays next to deliverable 6 in whichever plan carries the screening: it builds on the `source-pr` unit and on deliverable 6, and the roadmap puts both in Milestone 10 (`roadmap.adoc:636`). The split itself is the operator's decision of 2026-11-10 (Q11 of the issues pass).

Created 2026-11-10 by the split of PLAN-V2-22 (operator decision, Q11 of the issues pass).
