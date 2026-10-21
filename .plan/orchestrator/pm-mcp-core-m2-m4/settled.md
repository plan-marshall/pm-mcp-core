# Settled narrative: pm-mcp-core-m2-m4

Sections of `epic.md` whose subject is closed, moved here verbatim by `cleanup` on 2026-10-12 with the operator's confirmation. Each origin keeps a pointer. A second pass on 2026-10-21 (the operator's word) added the sections from "Defect: the specs of the plans of the packages amended by #111 were behind their package files" on.

## Watch: PR #16 before the first land

- PR #16 must be on `main`, and the ledger worktree on that base, before the first `land`:
  until then the worktree's `.gitignore` still ignores the ledger.

## Watch: code-analysis-base at decompose

- `code-analysis-base` (added by #15, serves Milestone 6, depends on nothing, `open`) is in the
  index but outside the Milestone 2 to 4 scope this epic was framed for — decide at `decompose`
  whether it joins the queue.

## Watch: the two files nearly every package edits

- Nearly every package is expected to edit both `doc/plans/README.adoc` (its status row) and
  `PmMcpLogMessages` in `pm-core`; two shared files is the threshold at which the disjointness
  gate asks the operator — decide at `decompose` how the specs declare these two files.

<!-- moved by cleanup on 2026-10-13 with the operator's standing confirmation -->

## Watch: doc #57 of PLAN-17

- Documentation PR `plan-marshall-documentation` #57 (PLAN-17) is OPEN — PLAN-17's landing completes when it is merged. Merge it before the log-holder plan's documentation PR (both edit `doc/LogMessages.adoc`; the split renumbers rows 70/170/270).

## Watch: doc #20 of PLAN-02

- `plan-marshall-documentation` #20 (documentation of PLAN-02) was still open on 2026-10-10 at the
  PLAN-03 drain. The index already says `done`. Retire when #20 is merged.

## Watch: documentation #36 during PLAN-41

- `plan-marshall-documentation` #36 changed `workflow-dsl/02-units-jobs-decisions.adoc` during
  PLAN-41's execute (07:25Z); PLAN-44 re-verifies against the current specification.

## Defect: shared log identifier blocks

- Two plans of one workstream can take the same log identifier; it surfaced as a merge conflict
  between #19 and #20. The shared block is per workstream, not per plan — source: landing PLAN-03;
  confirmed by PLAN-01 (inbox 006), which moved to WARN 151. PLAN-04, PLAN-08 and PLAN-09 share the
  same WS-01 block.

## Watch: the log-holder PRs

- pm-mcp-core #64 (log holders per module) and plan-marshall-documentation #68 are OPEN. Merge #64 first, then #68. No plan is emitted before #64 is on main, because every open spec's log rule names the new holders. Once #64 has merged, retire the Open Defect on shared log identifiers.

## Watch: doc #56 of PLAN-21

- Documentation PR `plan-marshall-documentation` #56 (PLAN-21) is OPEN — PLAN-21's landing completes when it is merged.

<!-- moved by analyze on 2026-10-14: plan-marshall-mcp #76 merged (e34961b) -->

## Watch: pm-mcp-server against the log-holder SNAPSHOT

- plan-marshall-mcp `pm-mcp-server` imports `PmMcpLogMessages` and breaks against the SNAPSHOT of #64 until it switches to `RuntimeLogMessages` (all of its 16 records) or takes over its own records.

## Watch: pm-mcp-server and SweepStep.notImplemented

- plan-marshall-mcp `pm-mcp-server` still calls `SweepStep.notImplemented`, which PLAN-12 removed; the assembly breaks against the new pm-core/pm-runtime SNAPSHOT until it registers `PendingEntryRecordsSweep` and `FencedSessionGenerationsSweep`. This adds to the earlier breaking changes for plan-marshall-mcp (PLAN-06, 08, 11, 36).
- Refuted by #76: the assembly never used `SweepStep` or `PeriodicSweep` (no use site at e34961b^1). The sweep wiring is owed as new behaviour, not as a follow-up — see the plan-marshall-mcp watch in `epic.md`.

## Watch: breaking changes for plan-marshall-mcp

- Breaking changes for plan-marshall-mcp against the deployed SNAPSHOT: `FindingSource` /
  `FindingResponder` type parameters (PLAN-36); `StartupSequence`, `ProjectRecovery` (PLAN-08);
  `AuditEvent`, `CrossScopeReconciler`, journal format (PLAN-06).
- Settled by #76: `StartupSequence` followed (`AuditChainVerification.NONE` plus a refusing sink in `PmMcpd`); `FindingSource`, `FindingResponder`, `ProjectRecovery`, `AuditEvent`, `CrossScopeReconciler`, the journal format and the enrolment store types have no use site in the assembly. All gates and CI green on 28bd5fe.

<!-- moved by analyze on 2026-10-14: PLAN-56 shipped as #66 (7981d17) -->

## Defect: static verifier residuals after PLAN-54

- Static verifier residuals after PLAN-54: Rule 9 firing per step, result guards on non-call transitions, the remediation budget re-check, `lost` in plan-only sub-workflows, the Rule 3 activation, and documentation over-claims. Owned by PLAN-56 — source: landing PLAN-54.
- Settled by PLAN-56 (#66, 7981d17; documentation #72): every item was implemented or named as a seam. The Rule 3 activation remains a seam until documentation issue #71 is answered (see the watch in `epic.md`); `landings/PLAN-56.md`.

<!-- moved by analyze on 2026-10-15: PLAN-09 shipped as #69 (3236ed5) -->

## Watch: no production AuditSink or AuditChainVerification in pm-mcp-core

- **No production `AuditSink` or `AuditChainVerification` in pm-mcp-core** (only test sinks; `AuditChainVerification.NONE`). Since #76 the daemon starts with `NONE` and `NO_AUDIT_STORE`, a sink that refuses every record (`StartupSequence` then logs `AUDIT_CHAIN_BROKEN_NOT_RECORDED`). PLAN-09 `audit-trail` is the natural owner — source: plan-marshall-mcp #76 report, corroborated 2026-10-14.
- Settled by PLAN-09 (#69, 3236ed5): `core.store.AuditLogSink` is the production sink and `runtime.audit.AuditVerification` implements `AuditChainVerification`. The wiring in plan-marshall-mcp is owed (see the plan-marshall-mcp watch in `epic.md`); `landings/PLAN-09.md`.

<!-- moved by analyze on 2026-10-15: PLAN-24 landing drained -->

## Watch: PLAN-24 landing

- PLAN-24 `plan-instance`: code PR #67 merged (2ebec2a, 2026-10-14); documentation PR #73 open; no landing message yet — checked 2026-10-15.
- Settled: documentation #73 merged (1eaa6c6) and the landing message `plan-24-plan-instance-001.md` was drained on 2026-10-15; `landings/PLAN-24.md`. The code/specification gap it left is a new watch in `epic.md`.

<!-- moved by cleanup on 2026-10-15: corpus reconciled at a8a4214 -->

## Defect: staged specs behind doc/plans/

- **Staged specs behind `doc/plans/` (corpus reconciliation owed; run `cleanup`).** Between 7981d17 and
  2ebec2a three pull requests outside this epic's plans changed the package corpus:
  - #70 (017ed53) — the packages now implement the specification agreed in documentation #76: 19 packages
    lost their "specification first" task, contradictions were fixed, and three packages are new
    (`removed-concepts`, `worker-activity`, `process-and-overview`). `session-as-worker` is proposed for removal
    and `blocked` on the operator's decision.
  - #68 (4ab1f56) — the new package `corpus-sources`; `role-qualification` now depends on it.
  - #67 (2ebec2a, PLAN-24) — the new package `lane-route-pruning`, added by operator exception.

  Affected rows of this queue:
  - staged: PLAN-15 `project-machine-store`, PLAN-28 `task-queue-and-protocol`, PLAN-33 `session-as-worker`
    and PLAN-34 `role-qualification`;
  - shipped: PLAN-12 `session-log-and-generations`, whose code `removed-concepts` now removes;
  - parked: PLAN-42, 43, 45 to 53, 55 and 57 to 63.

  The five new packages have no row. The staged specs were generated before these changes, so their
  deliverables, dependencies and surfaces may be stale, and an emit from them could start a plan against
  the old package text. Hold these rows until `cleanup` (Phase A, corpus reconciliation) re-grounds them —
  source: `git diff 7981d17 origin/main -- doc/plans/` and the bodies of #67, #68 and #70, checked
  2026-10-15.
- Settled by cleanup (2026-10-15, at a8a4214, which also covers #72, #73 and #74):
  - PLAN-15, 23, 28 and 34 were corrected, PLAN-33 was retired, and the 19 parked specs were regenerated.
  - PLAN-64 to PLAN-68 were staged for the five new packages.
  - `corpus-sources` was done by #74 outside the queue.
  - See the queue annotation in `epic.md` and the cleanup decision in the decision log.

## Defect: corpus-overlay-extensions outlived its implementation

- **`corpus-overlay-extensions` outlived its implementation.** pm-mcp-core #80 (238c823) implemented the package (HTTPS fetch for
  `huggingface`, several items per row, `answer_schema`; `HuggingFaceSourceRepository`, ADR `core-role-corpus/006`), but
  `doc/plans/corpus-overlay-extensions.adoc` and its row in `doc/plans/README.adoc` are still there, although a plan's pull
  request removes both. It has no queue row. Its Done When asks for one import of the three sources against `huggingface.co`,
  run by hand and recorded in the pull request; whether #80 recorded it was not verified. A small `docs(plans)` change in
  pm-mcp-core removes file and row once that is confirmed — source: cleanup of 2026-10-16.

## Defect: watch link of stalled-scope-detection.adoc

- **Watch link of `doc/plans/stalled-scope-detection.adoc`.** The package file links the watch `Runtime Model` of the documentation
  repository, which holds no stalled item; the items PM-WATCH-RUN-2 and PM-WATCH-RUN-8 are in
  `doc/implementation-watch/runtime-lifecycle.adoc` here. PLAN-23 names the right file; the package file is owed a one-line fix
  — source: cleanup of 2026-10-16.

## Defect: plan-marshall-mcp did not compile against the pm-runtime SNAPSHOT

- **`plan-marshall-mcp` does not compile against the pm-runtime SNAPSHOT since #90 (owed by PLAN-74 deliverable 7).** `SecretStoreProducer`
  in `pm-mcp-server` uses the deleted `SecretStoreSelector`; the producer of the active store must read the recorded choice and the
  credential-store aspect of the onboarding must call `CredentialStoreChoice.record`. The checkout of `plan-marshall-mcp` is 11 commits
  behind `origin/main`, whose newest are docs(plans). No package of `plan-marshall-mcp` owns it and no issue exists (verified by
  listing `doc/plans` there on 2026-10-18). Fix: a package file (or a bullet of the package that wires the credentials) and then a
  plan in that repository; the repository is outside this epic's ledger, so the operator decides whether to file an issue or open the
  `docs(plans)` change — source: landing PLAN-74.

Closed by `plan-marshall-mcp` #88 (`8137c9e`, 2026-10-18, `fix(credentials): serve the recorded credential store, not a selection`): `SecretStoreProducer` produces the runtime's `CredentialStoreChoice` and a `SecretStore` that resolves `choice.activeStore()` at every call; no reference to `SecretStoreSelector` remains in the checkout at `9bbe852` (verified 2026-10-19 by grep). The follow-up `plan-marshall-mcp` #90 (`91b60d5`, 2026-10-19) maps the store's refusal to `409 onboarding_incomplete` in `CredentialsResource` (verified in the merged diff and by `ci pr queue-state`: merged); what remains open is the writer of the choice, an Open Defect of `epic.md`.

## Defect: new specification obligations that no package file owns

- **New specification obligations that no package file owned (pm-mcp-core #84, merged `5197490`; documentation #84, merged).** Operator said yes on
  2026-10-17: staged as PLAN-78 `spec-obligation-owners`, docs only. The change added to the engine specifications: the worker egress proxy and macOS
  (Seatbelt) confinement (`job-runtime/02`); the launcher staying as the job's process (step 3a) and the refusal reason `harness_update_unverified`
  (`job-runtime/01`, `store-schemas/10`); update evidence, `endpoints`, `extension_isolation` and the `egress` and `isolation` profile stages
  (`client-profiles/01`); the writing skill, the byte-identical shared launch prefix and harness-extension isolation (`model-work/04`); the
  `agent_started` signal (`model-work/03`, `store-schemas/16`, `timeouts/01`); navigation figures (`model-work/09`); the comparison footprint
  (`workflow-dsl/07`); the upgrade checked against the build attestation (`runtime-lifecycle/01`). Only `runtime-control.adoc` was amended by #84
  — source: analysis of 2026-10-17.

Closed by pm-mcp-core #111 (`72aec5c`, 2026-10-20, `docs(plans): give the new specification obligations an owner (PLAN-78)`), run in the runbook lane
on opencode: the obligations of #84, #95 to #98, #101, #109 and documentation #94 are bullets of eleven package files or are listed in its body
(No owner found, Owed to a staged plan, Owed outside); `epic-decomposition` was split into `epic-decomposition` and `epic-review` by the
operator's decision. Verified 2026-10-20 against `main` at `72aec5c`: the terms and anchors named by the landing are in `doc/plans/` and were in none
of it at `993a7e7`; `trace.py` reports `BROKEN LINKS 0`. What stays open: the specs of the plans of the amended packages (an Open Defect of
`epic.md`), and the remainders the landing lists (`landings/PLAN-78.md`).

## Defect: no package owns the pm-runtime adapter for QuestionRecordReader

- **No package owns the `pm-runtime` adapter for `QuestionRecordReader`** (found by the landing of PLAN-14, 2026-10-18; verified: no file of
  `doc/plans/` names `QuestionRecordReader`). PLAN-14 delivered the store in `pm-core` and states that the adapter is not part of it; the
  module structure places the question records in `pm-workflow` `workflow.question` and the port in `pm-core`. Candidates to take it:
  `question-continuation` (PLAN-73), `task-queue-and-protocol` (PLAN-28) or a bullet of `runtime-control`. **Staged on 2026-10-19 as PLAN-88 `question-record-reader-adapter`** (its own plan, sequenced against PLAN-73 by the disjointness check).

Closed by pm-mcp-core #116 (`c39ce3b`, 2026-10-20, `feat(runtime): read question records through a runtime adapter`), run in the runbook lane on opencode:
`QuestionLogRecordReader` in the new package `de.planmarshall.runtime.question` implements the port over `QuestionLog`; it is the only product
implementation (verified 2026-10-20 at `c39ce3b`; the two others are `pm-workflow` test fixtures). It needed no package file: the plan carried no row
in `doc/plans/` and none was added. What stays open: the CDI producer and the end-to-end `question` state path (`plan-marshall-mcp`), the
pending-entry addressing the port lacks (an Open Defect of `epic.md`), and the concept and user documentation at the milestone exit.

## Defect: the specs of the plans of the packages amended by #111 were behind their package files

- **The specs of the plans of the packages amended by #111 are behind their package files (found by the landing of PLAN-78, 2026-10-20).**
  Eleven package files gained bullets, `epic-decomposition` was split and `epic-review.adoc` is new, so the specs written from the old
  files omit the new scope. Staged: PLAN-27 (`job-confinement-and-environment`), 28 (`task-queue-and-protocol`), 29 (`worker-launch`),
  30 (`supervisor`), 34 (`role-qualification`), 35 (`supervision-scenarios`), 66 (`worker-activity`). Parked: PLAN-19 (`profile-loader`),
  43 (`phase-sub-workflows`), 53 (`epic-decomposition`), 58 (`operator-reports`), 68 (`chat-plan-revision`). PLAN-53's scope now covers only
  half of what its package file said: the review of an epic's findings and watches is the package `epic-review`, which no row owns. Run
  `cleanup` (it flags every spec whose declared surface moved), then regenerate the specs of those plans and add the row for `epic-review`
  before any of them is emitted. **Resolved by `cleanup` on 2026-10-21:** all twelve specs were regenerated from their package files, the row for `epic-review` is PLAN-92, and the operator asked for the cleanup. Not done by the landing; the operator's word is owed. Remainders the landing lists for the operator:
  two figures of `model-work/09` § Navigation Figures that no package names (`Most expensive turns`, `Largest tool results`; `worker-activity`
  has room, 9 bullets), the truncation rule and the two `DRAFT` use cases (no owner, operator decision), and the items owed to
  `plan-marshall-mcp` and `pm-mcp-clients` (`landings/PLAN-78.md`) — source: landing PLAN-78, corroborated 2026-10-20.

Closed by `cleanup` on 2026-10-21 at 99971f3 (the operator asked for it): the twelve specs named here were regenerated from their package files, 15 more that had drifted with them (27 in all), the row for `epic-review` is PLAN-92 (cut from PLAN-53), and a regeneration diff reads `current` for every live spec with a package file. Verified by `corpus verdicts`: 0 blocking claims (12 before).

## Watch: documentation PR #95, the pin advance of the migration inventory

- RETIRED 2026-10-21: plan-marshall-documentation PR #95 (the pin advance of the migration inventory to `297ae30`, opened by PLAN-37) was merged on the operator's word as `963e879`; the roadmap on `origin/main` names `297ae30661ab…`. The sibling repository has no CI configuration for the `ci` abstraction (`CI provider not configured`), so the pull request was read and merged with `gh` in that checkout (no checks and no rulesets exist there); the gap is filed as a finding in the `live-blockers` inbox.

Closed: merged as `963e879` on the operator's word on 2026-10-21; nothing further is owed here.

## Watch: #125 reached main without a rewrite the pre-commit gate applies

- **#125 reached `main` without a rewrite the pre-commit gate applies (observed).** #127 carries it (declaration-only). PLAN-83's lane ran on the stale
  executor that rejected the canonical build names; whether `verify -Ppre-commit` ran there is not in its landing. Regenerating the executor is the
  remedy already owed.

Closed: #127 (`c7780a0`) merged the rewrite. What stays open is the stale executor, tracked in the watch on the executor and in the findings filed in plan-marshall's `live-blockers` inbox.

## Watch: PLAN-64 met #121 and #125 and landed

- **PLAN-64 met #121 and #125 and landed (closed 2026-10-21).** `corpus declaration-currency` predicted the overlap; the branch merged both squashes
  (`da892e9`) and renumbered its ADR to `runtime-start/015`, and #122 merged as `c6da0e2`. The uncommitted `ServiceUnitInstallerTest.java` edit seen in
  its worktree was a build rewrite of #125's file, landed apart as #127 (`c7780a0`), not part of #122 (`landings/PLAN-64.md`). PLAN-84 (`doc/LogMessages.adoc`,
  the directory `runtime/start/` of its test surface), PLAN-85 and PLAN-89 (`doc/Traceability.adoc`, `README.adoc`) overlap the landed footprints too. PLAN-87's
  hypothesis `ScopeSessionLogTest.java` no longer exists (the spec is corrected); PLAN-28 and PLAN-73 declare `core-queue.adoc` and `core/queue/`, which
  PLAN-28 recreates.

Closed: the landing is recorded in `landings/PLAN-64.md`; the open remainders are the Sonar issues (PLAN-87 Deliverable 5) and the items owed to `plan-marshall-mcp`, each a watch of its own.

## Watch: cross-repository blockers of profile-loader and job-confinement-and-environment

- Cross-repository blockers: `profile-loader` waits on `pm-mcp-clients` / `profile-documents`;
  `job-confinement-and-environment` waits on `pm-mcp-clients` / `exec-read-denied-paths` —
  re-check when those merge and their `SNAPSHOT` is deployed.

Closed on 2026-10-21: `profile-documents`, `exec-read-denied-paths` and `installation-layout` are `done` in the index of `pm-mcp-clients` (`origin/main`). PLAN-19 was unparked by `cleanup`; the index of this repository still names them, which PLAN-93 corrects.

## Watch: differential-test-harness blocked on an operator tool decision

- `differential-test-harness` is `blocked` on an operator tool decision (a Python interpreter in
  the test environment and a `plan-marshall` checkout at the pinned inventory commit).

Closed: the decision of 2026-10-18 (pm-mcp-core #101, documentation #94) dropped the differential harness; PLAN-37 was re-written as the tests of the ported scripts and shipped as #106.

## Watch: documentation #82, #83, #84 and pm-mcp-core #84

- Documentation #82, #83, #84 and pm-mcp-core #84 (analysed 2026-10-17; the local documentation checkout was pulled to 595a525):
  - #82 (use cases) and #83 (requirements and specifications aligned): no plan scope changes. The companion engine change is
    pm-mcp-core #82 (28d04ef), already on `main`, where the plan files differ only in link labels ("CLI, Coordination & Security" is
    now "Credentials & Enrolment", "Runtime Model" is "Runtime Lifecycle"); the specs of PLAN-45, 46, 48, 49, 51, 60 and 64 carry the old
    labels, which regeneration corrects. Small semantic edits: PM-ARCH-1 (`pm-operator` reads `language.interface` from
    `machine-config.json` while no runtime answers; PLAN-26, 63, 74 mention that file), PM-WF-19 (the decision store of a
    pending entry before its scope exists, moving into the scope at creation; PLAN-47 and PLAN-48), PM-WF-1 (the workspace scope
    rests in `project.completed`, `setup.completed` is the sub-workflow's terminal; PLAN-45). The roadmap changed labels only.
  - #84 (articles, non-functional findings) changes requirements PM-SKILL-1/2/4, PM-SEC-5, PM-CLIENT-2, PM-EXEC-1/5, PM-DIST-1, PM-SVC-2,
    PM-WORK-6/7, PM-WF-14, PM-TEST-6. Its engine counterpart, pm-mcp-core #84, MERGED on 2026-10-17 (`5197490`), so `corpus verdicts` now
    reports 379 stale claims (every spec declares the README row, which every merge touches; staleness is reported, never promoted). It touches 16 files, among them parts
    declared by PLAN-19, 20, 26, 27, 28, 29, 30, 34, 35, 45, 46, 48, 49, 51, 58, 59, 60, 61, 63, 64, 66, 73 and 74. A trial merge
    (`git merge-tree`) with the branches of PLAN-74 and PLAN-75 shows no conflict.
  - Running plans: PLAN-20 (job runner, plan `plan-20-job-runner`) is the one concerned. Its package covers termination and the
    outcome table; #84 adds termination step 3a (launcher stays as job process, subreaper), the refusal reason
    `harness_update_unverified` and the `agent_started` signal's process-tree basis. The launcher itself is `pm-exec` in
    `pm-mcp-clients`. PLAN-20 works from a worktree cut before #84; at the landing of PLAN-75 it was already in `5-execute`
    (3 commits, 2 behind `main`), so #84 merged AFTER its outline: the plan has not read the new termination step 3a, the refusal reason
    or the signal unless it is told. Not sent: the operator's word is owed (heads-up to `plan-20-job-runner`'s mailbox).
    PLAN-74 shares two lines of `store-schemas/11` and `/12` with #84 (a digest description and the self-update paragraph); no
    conflict. PLAN-69 and PLAN-75 (PR #85) are not concerned.
  - After pm-mcp-core #84 merges: run `cleanup` (it flags every spec whose declared surface moved), then hand-check the owners
    named in the Open Defect "New specification obligations that no package file owns".

Closed: the engine counterpart #84 was merged on 2026-10-17 (`5197490`) and its obligations were given owners by #111 (PLAN-78); PLAN-20, the running plan concerned, shipped as #92; the old link labels of the specs were corrected by the regeneration of `cleanup` on 2026-10-21.
