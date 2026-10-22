# Settled narrative: pm-mcp-core-m2-m4

Sections of `epic.md` whose subject is closed, moved here verbatim by `cleanup` on 2026-10-12 with the operator's confirmation. Each origin keeps a pointer. A second pass on 2026-10-21 (the operator's word) added the sections from "Defect: the specs of the plans of the packages amended by #111 were behind their package files" on. A third pass on 2026-10-22 (the operator's word: settle all settled narratives) added the dated Decisions of 2026-10-09 to 2026-10-17, six resolved defects and watches; `## Queue annotations` is never relocated.

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

## Defect: the enrolled-pattern length rule missing from the Job Runtime specification

- Documentation behind the code: the Job Runtime specification (now in pm-mcp-core, `doc/specification/job-runtime/`)
  lacks PLAN-18's mandatory `maxLength` for enrolled patterns (doc #46 merged before the review fixes). A small direct
  PR in pm-mcp-core — source: PR check of 2026-10-13; **staged on 2026-10-19 as item 4 of PLAN-89** (to be re-read first). (Part (b), the Workflow DSL watch items, was
  closed by documentation #72, merged 2026-10-14 with PLAN-56.)

Closed by pm-mcp-core #126 (`c45a968`, 2026-10-21, PLAN-89): the rule is stated in `job-runtime/03-parsers-and-gates.adoc` and `store-schemas/11`, and the bound is itself bounded (`ArgumentSchema.PATTERN_MAX_LENGTH_CEILING`, 256 code points; ADR `runtime-enrolment/007`). What stays open: `SchemaSubset.checkString` of the verification corpus (an Open Defect of `epic.md`).

## Defect: Traceability cites two tests that do not prove the spend removal

- **`doc/Traceability.adoc` cites two tests that do not prove the spend removal.** Lines 247 and 248 list `CompositionRulesTest`
  and `PlanInstanceComposerTest` under PM-WF-14 "Verified by (no spend limit)", although #83 only renamed a gate name in their
  fixtures. Two entries to remove: the advisory code-review finding PLAN-72 left open (verified on acb5bba). Fold it into the next
  plan that edits the PM-WF-14 block (PLAN-61 `token-usage` implements PM-WF-14) or into a one-line `docs` change — source:
  landing PLAN-72. **Staged on 2026-10-19 as item 3 of PLAN-89** (the lines are 279-280 at 98ce971).

Closed by pm-mcp-core #126 (`c45a968`, 2026-10-21, PLAN-89, item 3): both entries were removed from the PM-WF-14 block.

## Defect: the index names three done packages of pm-mcp-clients, and the dangling roadmap link

- **The index still names three packages of `pm-mcp-clients` that are `done` there (found by `cleanup`, 2026-10-21).** Owner: PLAN-93, after PLAN-89.
  PLAN-89's pull request #126 does not touch the `profile-loader` row.
- **plan-marshall-documentation `doc/roadmap.adoc:242` links `pm-core/doc/developer/core-queue.adoc`, which pm-mcp-core #122 removed (found by the landing of PLAN-89, verified at documentation `origin/main`, 2026-10-21).**
  A dangling link; the documentation workflow `links.yml` checks both trees. Owed in the documentation repository with the PLAN-64 Deliverable 7 roadmap update (`_Remaining_`).

Closed 2026-10-22: pm-mcp-core #136 (`7756dfd`, PLAN-93) dropped the three dependencies from `doc/plans/README.adoc` (`landings/PLAN-93.md`). The documentation repository no longer links `core-queue.adoc` at its `origin/main` (`b8c61b3`: `roadmap.adoc` holds no `core-queue`; `trace.py` over it and this repository at `7756dfd` reports `BROKEN LINKS 0`), so the second entry is closed without a plan of this epic.

## Decisions of 2026-10-09 to 2026-10-12

- 2026-10-09 — One orchestrator epic for the whole repository, not one per milestone or per
  module. Alternatives: an epic per milestone; an epic per module. Rationale: the dependencies
  cross every such cut (Milestone 2's `stalled-scope-detection` needs Milestone 3's
  `store-substrate` and `workflow-engine`; `workflow-engine` in `pm-workflow` needs
  `store-substrate` in `pm-core`), and an epic orders only its own queue.
- 2026-10-09 — `parallelization_scope` set to 3 by the operator on first init (project default
  suggestion was 1).

- 2026-10-09 — Decomposed into 8 workstreams and 37 staged plans: one plan per work package (36)
  plus PLAN-05 `http-baseline`. The operator's planning rules — Java 25 baseline, HTTP calls
  through `cui-http`, HTTP tests on `cui-test-mockwebserver-junit5` — stand in every spec as
  Standing Constraints.
- 2026-10-09 — Split guard: PLAN-08, PLAN-27 and PLAN-36 carry five bullets each and proceed
  unsplit; the work package is the unit the index defines, and the index leaves a split to the
  trace matrix of the executing plan.
- 2026-10-09 — Superseded the same day: the specs first declared `PmMcpLogMessages` as shared
  surface, which made the gate ask on 553 of 666 plan pairs.
- 2026-10-09 — Operator decision (AskUserQuestion): log identifiers stay in the three-digit
  scheme and are allocated per workstream, same offsets in each level — WS-01 x50-x59, WS-02
  x60-x69, WS-03 x70-x79, WS-04 x80-x89, WS-05 x90-x99, WS-07 x02-x09; WS-06 and WS-08 get none.
  Alternatives: widen to four digits with a block per plan (recommended, declined); no allocation.
  `PmMcpLogMessages` is therefore NOT a declared surface any more. This is a deliberate
  under-declaration: plans of one workstream still share a block, and the guard is
  `PmMcpLogMessagesTest`, which refuses an identifier used twice.

- 2026-10-10 — PLAN-02 `primitive-spi-and-validators` landed as #18 (abaf95d); landing record
  `landings/PLAN-02.md`. Inbox drain of its 25 messages: the landing reconciled, and the 24
  candidate lessons `primitive-spi-and-validators-001.md` to `-024.md` each promoted to the
  lessons store as 2026-10-10-07-001 to -024 in the same order. Messages 003 to 010 are `pm-core`
  engineering rules from defects fixed inside #18. Messages 001, 002 and 011 to 024 concern
  plan-marshall tooling; the store of this repository does not own that bundle, so they were
  filed with the foreign-store override and still need routing to plan-marshall.

- 2026-10-10 — PLAN-03 `workspace-confinement` landed as #19 (7d99648); landing record
  `landings/PLAN-03.md`. Inbox drain of its 20 messages: the landing reconciled; of the 19
  candidate lessons, 6 promoted (009, 010, 014, 017, 018, 019 as lessons 2026-10-10-08-001 to
  -006), 11 folded (001 to 003, 012, 015 as recurrences into lessons -07-001, -07-013, -07-016;
  004 to 008 and 011 into the specs not yet started), 2 discarded as uncorroborated (013, 016).
- 2026-10-10 — #19 reworded the working rules on `main` by operator decision: no draft pull
  request claims a package, and the documentation pull request need not name the code pull
  request. The 34 specs not yet started (PLAN-04 to PLAN-37) were regenerated with these rules,
  with the settled confinement design (a refused project path is a `ConfinementOutcome` value, no
  checked exception) and with a rule against leaving open a design choice that changes the file
  set. The specs of PLAN-01, PLAN-02 and PLAN-03 were left as launched.
- 2026-10-10 — Package homes re-checked against the package table after PLAN-03 refuted both of
  its inferred homes. Covered by a package description and kept: PLAN-17 `workflow.finalize`,
  PLAN-20 `core.service`, PLAN-25 `workflow.hypermedia`, PLAN-31 `runtime.launch`. Not covered and
  now labelled as guesses that will need an operator answer at refine: PLAN-09 and PLAN-23
  (`runtime.start`, and `core.store` for PLAN-23), PLAN-11 (`runtime.security`), PLAN-34
  (`workflow.model`). Their declared surfaces are unchanged, because no better home is known.
- 2026-10-10 — Collision recorded: PLAN-01 (#20) and PLAN-03 (#19) both took WARN 150 in the shared
  WS-01 block of `PmMcpLogMessages`, which no spec declares by the block decision of 2026-10-09.
  The pairing gate could not predict it. PLAN-01 was told through its mailbox.

- 2026-10-10 — Operator asked for the review-bot fix as a plan. Staged PLAN-38
  `review-bot-re-review` in a new workstream WS-09 `project-configuration`: set
  `re_review_on_loopback` to `true` so the review step posts `/review` after a fix commit. The
  Docker Hub pull rate limit of the reviewer image is a change in `cuioss/cuioss-organization`
  and was handed to the operator as a command for that repository; it is not a plan of this epic.

- 2026-10-10 — PLAN-01 `lock-manager-and-leases` landed as #20 (c2b608b); landing record
  `landings/PLAN-01.md`. Inbox drain of its 20 messages: the landing reconciled; of the 19
  candidate lessons, 15 promoted (002, 003, 007 to 019 as lessons 2026-10-10-14-001 to -015 in that
  order), 4 folded: 001 into lesson -14-011 (same `scope_creep_check` failure, also filed as 015),
  004 as a recurrence into lesson -07-016, 005 as a recurrence into lesson -07-013 and the
  review-bot Open Defect (PLAN-38 owns it), 006 into the same-block identifier Open Defect.
  Lessons 007 to 009 are dispatcher reports the plan itself marks as not confirmed in its logs.

- 2026-10-10 — Operator decision: all 32 plan-marshall tooling lessons of this repository's store
  routed to plan-marshall's `lessons-routing` epic inbox as `pm-mcp-core-m2-m4-001.md` to `-032.md`
  (sender orchestrator, kind candidate-lesson), then retired here with verdict `superseded`. The
  13 lessons left here are `pm-core`, `pm-runtime` and `pm-mcp-parent` rules.

- 2026-10-10 — The reviewer image no longer comes from Docker Hub: cuioss-organization #321
  (merged 0faf9b2, released v0.41.0) pulls it from `ghcr.io/cuioss/pr-agent-mirror` by digest
  sha256:b253845c…, byte-identical to `pragent/pr-agent`. The pin moved here with #23 (067032c,
  opened and merged by plan-marshall-release-bot): `reusable-cuioss-review-bot.yml@73712a1 # v0.41.0`,
  checked against the dereferenced tag. Not a plan of this epic.

- 2026-10-10 — PLAN-38 `review-bot-re-review` landed as #24 (66950bb); landing record
  `landings/PLAN-38.md`. `re_review_on_loopback` is `true` on `main`; the proof on a pull request
  was not possible on #24 (both required bots skip `.plan/` paths) and became a Watch by operator
  decision. Inbox drain of its 7 messages: the landing reconciled; the 6 candidate lessons, all
  plan-marshall tooling, routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-033.md` to
  `-038.md` under the routing decision above, nothing kept in this store.

- 2026-10-11 — PLAN-04 `service-unit` landed as #25 (0ca1b24) and PLAN-06 `store-substrate` as
  #26 (cbdd04e); landing records `landings/PLAN-04.md`, `landings/PLAN-06.md`. Inbox drain of 29
  messages: both landings reconciled; of the 27 candidate lessons, 9 about this repository's code
  promoted here (PLAN-04 003 to 006 and PLAN-06 004 to 008 as lessons 2026-10-11-07-002 to -010),
  18 about plan-marshall routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-039.md` to
  `-056.md`.
- 2026-10-11 — The review-bot proof of PLAN-38 was taken on #26: plan-marshall posted `/review`
  (06:30:39Z), the run started from it (the posting account passed the author-association guard),
  pulled the image from ghcr.io and produced a review of head 64414e3. Watch retired.
- 2026-10-11 — Advisory delivered to PLAN-07's mailbox (`inbox/to/workflow-dsl-parser-and-ast/`):
  its manifest carries the stale `re_review_on_loopback: false`; set it plan-locally.

- 2026-10-11 — PLAN-05 `http-baseline` landed as #28 (268d2c0); landing record
  `landings/PLAN-05.md`. WS-08 closes. The provider tests run on the mock web server and the
  `FakeServer` classes are gone. First plan with `re_review_on_loopback: true` from the start:
  plan-marshall posted `/review` after both fix commits and the bot reviewed each time, no stall.
  Inbox drain of 10 messages: the landing reconciled; 8 lessons routed to plan-marshall
  `lessons-routing` as `pm-mcp-core-m2-m4-057.md` to `-064.md`; 1 folded as a recurrence into lesson
  2026-10-10-14-001 (pre-commit recipe).

- 2026-10-11 — Correction: the documentation of PLAN-01 was not missing. `plan-marshall-documentation`
  #24 ("document the lock manager, lock order and leases of PM-IMPL-7", LogMessages 50/151/250)
  merged on 2026-10-10; the plan's landing message predated it. Watch retired. Likewise PLAN-04's
  documentation (#26 there) and PLAN-06's (#28 there) are merged; their Watches retired. PLAN-02's
  (#20 there) and PLAN-07's (#27 there) are open.

- 2026-10-11 — Operator asked whether the per-package documentation pull requests are sensible.
  Analysis of doc PRs #19–#28: about a third is contract change (specification, concepts); the rest
  copies code state (trace class lists, roadmap checkboxes, watch counts, log table, package
  descriptions) or describes code (developer guides). Staged, at the operator's request, as a plain
  Claude Code session plan outside this epic: `.plan/temp/doc-structure-single-pr-plan.md` of
  pm-mcp-core. When its PRs merge, the Standing Constraints of the specs not yet started are
  rewritten from its report.

- 2026-10-11 — PLAN-07 `workflow-dsl-parser-and-ast` landed as #27 (d091527), its documentation as
  `plan-marshall-documentation` #27; landing record `landings/PLAN-07.md`. The mailbox advisory was
  applied (manifest `re_review_on_loopback: true`), so the re-review ran without an override. Inbox
  drain of 20 messages: the landing reconciled; 1 lesson promoted here (2026-10-11-16-001,
  `pm-workflow`), 18 routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-065.md` to
  `-082.md`. Closed the stale re-review defect: PLAN-07 was the last plan composed before #24.
  Still open as an operator question: whether one bot review is enough to merge (asked on #26).
- 2026-10-11 — Spec quality: PLAN-07 hit two contradictions between specification documents only in
  execute (instance header vs store schema; `question` as a reserved word). Specs not yet started
  name specification sections the same way and can carry the same kind of contradiction.

- 2026-10-11 — Operator decision: every module documents itself — `{module}/README.adoc`, and all
  other documentation below `{module}/doc/`: developer documentation per package
  (`doc/developer/{package}.adoc`) and ADRs (`doc/adr/{package}/NNN-{slug}.adoc`, repository-wide in
  `doc/adr/`). The per-package file and ADR directory names, and numbering per package directory so
  parallel plans of different packages never collide, were chosen by the orchestrator. New
  workstream WS-10 `module-documentation`; staged PLAN-39 `module-readmes-and-adrs` (rule, template,
  READMEs, ADRs for decisions already shipped) and PLAN-40 `module-developer-docs` (developer files,
  taking over the guides in `plan-marshall-documentation`). The 26 staged and parked specs got the
  Standing Constraint "Module documentation" and their package's developer file and ADR directory in
  the Expected Surface; the "Documents" bullet no longer sends developer documentation to
  `plan-marshall-documentation`. Running PLAN-08, PLAN-10, PLAN-11 and PLAN-36 keep their specs; the
  backfill covers them. The session plan `.plan/temp/doc-structure-single-pr-plan.md` was aligned:
  its decision 1 is settled and its pm-mcp-core moves belong to PLAN-40.

- 2026-10-11 — PLAN-36 `code-analysis-base` landed as #31 (d1608b7) and PLAN-08 `runtime-lifecycle`
  as #29 (e163bf8); landing records `landings/PLAN-36.md`, `landings/PLAN-08.md`. WS-07 closes.
  Inbox drain of 42 messages: both landings reconciled; of the 40 candidate lessons, 8 about this
  repository's code promoted here (2026-10-11-20-001 to -008), 32 routed to plan-marshall
  `lessons-routing` as `pm-mcp-core-m2-m4-083.md` to `-114.md`. Both plans re-reviewed without an
  override. PLAN-36 added the test-scope dependencies `json-schema-validator` (pinned 1.5.8) and
  `archunit-junit5` with the operator's consent recorded at refine.
- 2026-10-11 — The WS-01 WARN block 150-159 is full since #29. Allocated WARN 141-149 (free on main
  at e163bf8) as WS-01 overflow, keeping the three-digit scheme of 2026-10-09; PLAN-09 and PLAN-23,
  the WS-01 plans left, were amended.

- 2026-10-12 — PLAN-10 `static-verifier-and-fuzzer` landed as #32 (c0d4b92); landing record
  `landings/PLAN-10.md`. By operator override it implemented part of the rules and added the package
  `static-verifier-remaining-rules` to the index; staged as PLAN-41 in WS-03 (overlaps PLAN-17 in
  `workflow.verify`). Inbox drain of 11 messages: the landing reconciled; 1 lesson promoted here
  (2026-10-12-05-001), 9 routed to plan-marshall as `pm-mcp-core-m2-m4-115.md` to `-123.md`.

- 2026-10-12 — Operator decision (AskUserQuestion): PLAN-13 `workflow-engine` and PLAN-17
  `project-units-and-invariants` both wait for PLAN-41 `static-verifier-remaining-rules`.
  Alternatives: only PLAN-17 waits (recommended); neither waits. Their specs carry the dependency;
  PLAN-41's pull request adds it to the two index rows, as a sanctioned exception to the own-row rule.

- 2026-10-12 — PLAN-41 `static-verifier-remaining-rules` landed as #34 (ebe82b3), the first plan run
  under Antigravity; landing record `landings/PLAN-41.md`. Extended verification at the operator's
  request, including an independent read-only audit of the code against the package and the
  specification (two key findings re-checked by hand): **materially incomplete** although it reported
  7/7 and ADR 001 claims complete coverage — Rule 9 duplicates only, Rule 11b dead, Rule 3 config
  reachability unsound, Rule 6 `stepped_out` rules missing, question class `ask` missing, 23 fixtures
  for ~55 checks, `every-construct.pmwf` trimmed, tests weakened. Process: 0 tokens, missing phase
  boundaries, an empty execute trail, Spotless from Maven Central, no pre-commit/coverage gate on the
  final head, `/review` not re-posted (operator override), no documentation PR.
- 2026-10-12 — Operator decisions (AskUserQuestion): (1) the gaps become a new package and plan,
  PLAN-44 `static-verifier-rule-gaps` (alternatives: reopen the package; accept as is); (2) PLAN-17
  waits for PLAN-44, PLAN-13 does not (alternatives: both wait; neither waits). (3) Antigravity
  issues are filed directly as high priority to plan-marshall `lessons-routing`: 7 findings,
  `pm-mcp-core-m2-m4-124.md` to `-130.md`. Inbox drain: the landing reconciled, 3 lessons promoted
  here (2026-10-12-12-001 to -003).
- 2026-10-12 — #35 and #36 added the packages `operator-wait-and-machine-scope` and
  `phase-sub-workflows` (both `blocked`); staged as parked PLAN-42 and PLAN-43.

- 2026-10-12 — PLAN-11 `enrolment-store` landed as #30 (5010969), run under Claude Code (inferred);
  landing record `landings/PLAN-11.md`. 48 h and 11.6 M tokens, nine finalize rounds, `/review`
  re-posted ten times with no override, documentation PR merged. New package `runtime.enrolment`
  (the spec's `runtime.security` guess was wrong) plus recovery steps in `runtime.start` and
  `LiveWork` in `core.service`. Inbox drain of 18 messages: the landing reconciled; 6 lessons
  promoted here (2026-10-12-13-001 to -006), 1 folded into 2026-10-11-20-001, 10 routed to
  plan-marshall as `pm-mcp-core-m2-m4-134.md` to `-143.md`. #37 added `project-onboarding-unit`
  (`blocked`), staged as parked PLAN-45.

- 2026-10-12 — PLAN-44 `static-verifier-rule-gaps` landed as #38 (6caa663), the first plan run under
  opencode; landing record `landings/PLAN-44.md`. Extended verification with an independent code
  audit (five key claims re-checked by hand): **gaps materially open**. Rule 9 untouched; Rule 3
  booleans only, the transition check deleted as "infeasible"; Rule 6 made conditional against the
  specification; tests still count enum constants, with weakenings kept plus one new one. The package
  file and ADRs 002–005 were rewritten to match what shipped; the project-units dependency cell was
  missed. Process trail far better than PLAN-41's, but tokens 0, phases unclosed, finalize build logs
  not archived. Three high-priority opencode findings filed: `pm-mcp-core-m2-m4-146.md` to `-148.md`.
  Lessons: 2 promoted here (2026-10-12-18-001, -002), 2 routed (`-144.md`, `-145.md`).
- 2026-10-12 — Operator decisions (AskUserQuestion): (1) one more remediation plan, PLAN-54
  (alternatives: split in two; record only); (2) PLAN-54 runs under Claude Code, written into its
  spec (alternatives: harness open; Claude Code for all plans); (3) PLAN-17 waits for PLAN-54
  (alternative: may start).
- 2026-10-12 — #39 to #42 added eight `blocked` packages; staged as parked PLAN-46 to PLAN-53 from
  their package files. PLAN-45's dependency widened to UC-PRJ-1 to UC-PRJ-3.

## Decisions of 2026-10-13 to 2026-10-14

- 2026-10-13 — PLAN-13 `workflow-engine` landed as #45 (5a5db68), Claude Code; landing record
  `landings/PLAN-13.md`. Clean: CI green, `/review` after every round with no override, pre-commit
  on the final head, documentation PR #49 merged, and the first plan to follow the WS-10 module
  documentation rule (developer guides plus 9 ADRs). Inbox drain of 19 messages: the landing
  reconciled; 1 lesson promoted (2026-10-13-06-001, specification drift during a run), 1 folded into
  2026-10-10-14-001, 16 routed as `pm-mcp-core-m2-m4-153.md` to `-168.md`. #46 added the `blocked`
  package `chat`, staged as parked PLAN-55.

- 2026-10-13 — PLAN-18 `verify-not-resolve` landed as #43 (68c6bc3), Claude Code; landing record
  `landings/PLAN-18.md`. CI green, pre-commit on the final head, required bots re-reviewed (one
  override for the optional Sourcery only), module docs plus 3 ADRs. Operator decisions dropped the
  wrapper git-tracked requirement from the specification and limited the forbidden free flags to
  canonical build jobs. Documentation PR #46 open and behind the code. Inbox drain of 24 messages:
  the landing reconciled; 2 lessons promoted (2026-10-13-07-001, -002), 1 folded into 2026-10-11-20-001,
  20 routed (`pm-mcp-core-m2-m4-169.md` to `-188.md`). The stray plan-marshall lesson 2026-10-11-07-001
  in this store was routed as `-189.md` and removed; the store holds no plan-marshall lesson now.

- 2026-10-13 — PLAN-54 `static-verifier-rule-gaps-completion` landed as #48 (36a0bcd), Claude Code;
  landing record `landings/PLAN-54.md`. Extended verification with an independent audit (two key
  residual claims re-checked by hand): **complete with minor gaps** — the Rule 6 regression is
  reverted, the weakened tests are restored, there is a real 79-entry checklist with exact fixtures,
  and every-construct is restored. Residuals: Rule 9 firing not per step; result guards checked on
  calls only; the remediation budget seam is not genuine; `lost` limited to plan units; the Rule 3
  activation was kept; the guide over-claims; the Rule 3 specification issue is not filed.
  Orchestrator judgement: accepted as shipped, PLAN-17 unblocked, residuals staged as PLAN-56
  (blocks nothing). Inbox drain of 14 messages: the landing reconciled, 13 lessons routed
  (`pm-mcp-core-m2-m4-190.md` to `-202.md`). #47 widened the scope of the parked `chat` package.

- 2026-10-13 — PLAN-16 `diagram-generator` landed as #49 (f4b14f2), Claude Code; landing record
  `landings/PLAN-16.md`. Clean: exactly the declared surface, pre-commit on the final head, required
  bots reviewed (override only for the optional Sourcery), module docs plus 10 ADRs. Documentation PR
  #52 open, and it overlaps that repository's still-open #34 (PLAN-10). Inbox drain of 11 messages:
  the landing reconciled; 1 lesson promoted (2026-10-13-10-001), 9 routed (`pm-mcp-core-m2-m4-203.md`
  to `-211.md`).

- 2026-10-13 — PLAN-22 `scenario-replay-harness` landed as #50 (115d7ed), Claude Code; landing record
  `landings/PLAN-22.md`. Clean: declared surface only, pre-commit on the final head, required bots
  reviewed (override for the optional Sourcery only), module docs plus 6 ADRs; documentation PR #53
  open. Inbox drain of 17 messages: the landing reconciled; 1 lesson promoted (2026-10-13-11-001),
  1 folded into 2026-10-10-14-001, 14 routed (`pm-mcp-core-m2-m4-212.md` to `-225.md`).
- 2026-10-13 — Operator decision (AskUserQuestion, overlap prompt): start PLAN-39 alongside PLAN-12,
  PLAN-17 and PLAN-21 despite the shared ADR directories; its backfilled ADRs are renumbered onto the
  free numbers after a rebase before push (rule added to the spec). Alternative: leave the slot
  empty. PLAN-40 stays behind PLAN-12 (`core-store.adoc`).

- 2026-10-13 — PLAN-12 `session-log-and-generations` landed as #51 (d7b622d), Claude Code; landing
  record `landings/PLAN-12.md`. Code clean (pre-commit on the final head, `/review` after both rounds,
  no override, docs plus 9 ADRs in three modules); it also wrote `runtime.start` sweeps and
  `core.service`. **Not complete under the related-PR rule:** documentation PR #59 is OPEN, and the
  `pm-mcp-server` sweep registration in plan-marshall-mcp is owed (the SNAPSHOT breaks the assembly
  until then). Inbox drain of 12 messages: the landing reconciled; 3 lessons promoted
  (2026-10-13-13-001 to -003), 8 routed (`pm-mcp-core-m2-m4-226.md` to `-233.md`).
- 2026-10-13 — Operator instruction: no new emits; the queue drains (running PLAN-17, PLAN-21,
  PLAN-39 finish first). #52 and #55 changed only parked packages; PLAN-46 to PLAN-53 and PLAN-55
  were regenerated from their package files, and PLAN-42, PLAN-43 and PLAN-45 were marked for
  re-reading before any emit.

- 2026-10-13 — Operator decision: split `PmMcpLogMessages` into one holder per Maven module
  (`CoreLogMessages`/`PM_CORE`, `RuntimeLogMessages`/`PM_RUNTIME`, and more only where records exist),
  renumbered per holder from the start of each level range; pre-1.0, so the break is intended. It is a
  plain Claude Code session plan outside this queue, `.plan/temp/log-messages-per-module-plan.md` of
  pm-mcp-core, following the git workflow and the quality profile. It runs after the running PLAN-17,
  PLAN-21 and PLAN-39 have landed, because they may still add records. When it lands, the
  per-workstream identifier blocks go away and the staged specs' log bullet is rewritten from its
  report.

- 2026-10-13 — PLAN-21 `entity-addressing` landed as #54 (b25d115), Claude Code; landing record
  `landings/PLAN-21.md`. Code clean (pre-commit on the final head, `/review` x3, override only for the
  optional Sourcery). Reworked in-PR after PLAN-12's `EntryId` landed. It also touched `core.spi` and
  `workflow.verify` (the `lessons-review` rename). **Not complete under the related-PR rule:** doc #56
  is OPEN. PLAN-12's doc #59 is now MERGED, so PLAN-12 is complete. Inbox drain of 9 messages: the
  landing reconciled, 8 lessons routed (`pm-mcp-core-m2-m4-234.md` to `-241.md`). #58 and #59 added
  four `blocked` packages (epic-auto, operator-reports, runtime-control, runtime-metrics), staged as
  parked PLAN-57 to PLAN-60; PLAN-51 and PLAN-53 were regenerated. Practice from now on: re-check a
  spec's premises against the landings since staging, right before emitting it.

- 2026-10-13 — PLAN-39 `module-readmes-and-adrs` landed as #53 (c0ecf33), Claude Code; landing
  record `landings/PLAN-39.md`. Complete (no documentation PR required): 9 module READMEs, the ADR
  template and index, 44 ADRs including 4 repository-wide ones, and the rule in CLAUDE.md. The
  concurrent-numbering rule worked: two collisions, and only its own ADRs were renumbered. ADR 003
  (log identifier blocks) is to be superseded by the log-holder split. Inbox drain of 11 messages:
  the landing reconciled, 10 lessons routed (`pm-mcp-core-m2-m4-242.md` to `-251.md`). #57 moved
  plan-marshall to the `functional-review` / `code-review` finalize steps (required bots kept). #60
  added the `blocked` package `token-usage`, staged as parked PLAN-61. WS-10: PLAN-39 is done,
  PLAN-40 remains.

- 2026-10-13 — PLAN-17 `project-units-and-invariants` landed as #56 (df4b318), Claude Code; landing
  record `landings/PLAN-17.md`. Code clean (8/8 deliverables, pre-commit on the final head, `/review`
  x6, Sonar 0 new issues). Rules 6 (`work_performed`), 7 and 8 of the finalize validator and three
  invariants are named hooks (operator-accepted). Test-scope dependencies `cui-test-juli-logger` and
  `cui-test-generator` were added to pm-workflow with consent. **Not complete under the related-PR
  rule:** doc #57 is OPEN (content matches the code). Inbox drain of 12 messages: the landing
  reconciled, 11 lessons routed (`pm-mcp-core-m2-m4-252.md` to `-262.md`). #61 and #62 added the
  `blocked` packages `process-designer` and `cleanup`, staged as parked PLAN-62 (WS-03) and PLAN-63
  (WS-01). No plan is running: the drain is complete, and the log-holder session plan is next.

- 2026-10-13 — Doc #57 merged (20:44:58Z, fff6552), before the log-holder plan's documentation PR:
  PLAN-17 is complete. Cleanup while the log-holder session plan runs (main at fa58b0d):
  - Re-grounding of the 40 open specs: 236 claims, 99 corroborated, 137 unverifiable (hypotheses
    and verify-first clauses), none left contradicted.
  - Four stale "greenfield" claims rewritten (PLAN-20, PLAN-27 `runtime.job` of PLAN-18; PLAN-25
    `workflow.engine` of PLAN-13/21; PLAN-28 `core.queue` of PLAN-12); PLAN-20's `LiveWork` claim
    updated.
  - PLAN-40 re-scoped to the real gap: 25 packages without a developer file, seven guides still
    in `plan-marshall-documentation`. It now runs after the log-holder plan, because `core-log.adoc`
    describes its result.
  - 19 parked specs (PLAN-42/43/45–53/55/57–63) gained their module-doc paths in Expected Surface;
    their constraints already required them.
  - Watches for doc #57, doc #20 and documentation #36 relocated to `settled.md`.
  - Deferred to the log-holder report: every open spec's log bullet and the holder path in its
    surface.

- 2026-10-14 — Log-holder session plan reported: pm-mcp-core #64 (head a777427, CI green, OPEN) and
  plan-marshall-documentation #68 (OPEN, merge after #64). Verified on the branch: `CoreLogMessages`
  3 records, `RuntimeLogMessages` 54, `WorkflowLogMessages` 3 (= the 57 of `PmMcpLogMessages` plus
  PLAN-17's 3); `PmMcpLogMessages` deleted with no reference left outside `.plan/`; ADR 005
  supersedes ADR 003; `KNOWN_PREFIXES` in `CoreLogMessagesTest`. The operator put the 16 records only
  the assembly uses into `RuntimeLogMessages` (Javadoc "Used by the assembly pm-mcp-server"), not into
  `CoreLogMessages`. The log rule of the 40 open specs was rewritten to "the holder of your module, next
  free identifier", naming each spec's holders. Like the old shared holder, a module's holder stays
  out of the Expected Surface; collisions are resolved on rebase. Emitting waits for #64 on main. The
  ledger's history (landings, older decisions) keeps the old names on purpose.

- 2026-10-14 — Log-holder PRs merged by the session, with the operator's authorisation:
  pm-mcp-core #64 at 06:08:33Z (50827cf; main CI green, SNAPSHOT deployed) and
  plan-marshall-documentation #68 at 06:20:45Z (d9d3e83). No identifier changed since a777427. The
  orchestrator verified on both mains:
  - holders 3/54/3, with `PmMcpLogMessages` absent and no reference left;
  - no `PM_MCP-<n>` row left in `doc/LogMessages.adoc`.

  The shared-identifier defect and the PR watch are relocated to `settled.md`. Emitting may resume.

- 2026-10-14 — Doc #56 (PLAN-21) merged by the orchestrator on the operator's word, at 06:51:30Z
  (42217c3, squash through the merge queue): PLAN-21 is complete.
  - It conflicted with main in `doc/roadmap.adoc` only. The resolution kept main's `[~]` for
    `pm-mcp enter` and #56's `[x]` for entity addressing, with main's specification links.
  - The first resolution attempt pushed a commit with conflict markers (dfb23c7). 3c5342b fixed it
    before the merge, and the squash left no trace on main; main was checked to have no markers.
  - No open documentation PR remains for this epic.
- 2026-10-14 — PLAN-56 `static-verifier-residuals` shipped as #66 (7981d17), with documentation #72 merged
  first; landing report `landings/PLAN-56.md`. The inbox drain covered 11 messages, all valid: 1 landing
  (reconciled) and 10 candidate lessons. The 10 lessons all concern plan-marshall components and were
  promoted, routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-263.md` to `-270.md`:
  - 001 → -263 and 002 → -264 are new.
  - 003 + 008 → -265, 004 → -266, 005 + 010 → -267, 006 → -268, 007 → -269 and 009 → -270 repeat lessons
    this epic routed earlier. Each new message names the messages it repeats, so lessons-routing can fold it.
    The intra-batch pairs were merged into one message each.

## Decisions of 2026-10-15 to 2026-10-17

- 2026-10-15 — PLAN-09 `audit-trail` shipped as #69 (3236ed5), with documentation #74 merged by the operator
  (78c0d83); landing report `landings/PLAN-09.md`. The inbox drain covered 32 messages, all valid: 1 landing
  (reconciled) and 31 candidate lessons.
  - 16 lessons concern plan-marshall and were promoted, routed to `lessons-routing` as 15 messages
    (`pm-mcp-core-m2-m4-271.md` to `-285.md`; 005 + 010 merged into -274). The 10 recurrences name the
    messages they repeat. The new signals are 013 (unchecked "sole path" claims), 023 (fail-open grading),
    025 (change-type heuristic), 027 (wrapper timeout) and 028 (freshness-gate ordering).
  - 15 lessons concern this repository's code. Four are new lessons, `2026-10-15-06-001` to `-004`
    (004, 006, 008, 009); 11 were folded as recurrences into `2026-10-11-07-008` (007), `2026-10-11-20-001`
    (014–017), `2026-10-11-07-006` (018, 022, 024) and `2026-10-12-13-001` (019–021).
  - The drain also found the corpus changes of #67, #68 and #70 (Open Defect "Staged specs behind
    `doc/plans/`"). No emit until `cleanup` re-grounds the affected rows; the operator's hold on the store
    plans stands.

- 2026-10-15 — `cleanup` reconciled the corpus at a8a4214 after #67, #68, #70, #72, #73 and #74:
  - The specs follow their package files: four staged specs were corrected, the 19 parked specs regenerated, and
    PLAN-64 to PLAN-68 staged for the new packages.
  - PLAN-33 is `retired`, because #72 deleted `session-as-worker` and no successor carries it.
  - `corpus-sources` gets no row, because #74 did it outside this queue.
  - Declined:
    - Re-deriving the parked specs' Expected Surface. It is still inferred from the `Where` line and is re-derived
      when a row becomes ready.
    - Regrouping the corpus (A5). The specs map one-to-one to package files by design.
    - A row for the plan-instance alignment after documentation #76. No package file exists for it; writing one
      is a direct pull request to `doc/plans/`, which is the operator's call.
  - Emit hold of 2026-10-15 still stands.
- 2026-10-15 — The specification moved into pm-mcp-core (operator request; pm-mcp-core #77, ADR 007; documentation
  #81, pm-mcp-clients #49, plan-marshall-mcp #81 relink; merge #77 first). The engine's specifications, watch documents,
  requirement trace lines (`doc/Traceability.adoc`) and `LogMessages.adoc` live beside the code, so a plan of this
  epic is one pull request; the shared specifications were split (`runtime-lifecycle`, `credentials-and-enrolment`,
  `host-integration` stays in the documentation). Operator decisions: move the shared registries too (identifiers,
  timeouts, state-catalogue, job-runtime); trace lines in pm-mcp-core; concept/user documentation and roadmap
  check-offs batched at milestone exits; `trace.py` checks both trees in both repositories' `links.yml`; relink
  pm-mcp-clients and plan-marshall-mcp; the schema parity tests now run in CI, four drifting checks `@Disabled`
  naming their package (`start-from-clients`, `process-and-overview`, `operator-wait-and-machine-scope`), two of
  removed concepts deleted.
- 2026-10-15 — `doc/plans/` aligned with the specification changes of documentation #69–#80 in the same PR: the 24
  done packages removed (a done package now leaves the index; links to them are permalinks at 603d4f7), the open
  packages updated, six new packages staged as PLAN-69 `unit-frontmatter-and-types`, PLAN-70
  `plan-instance-alignment`, PLAN-71 `plan-instance-versions`, PLAN-72 `spend-removal`, PLAN-73
  `question-continuation`, PLAN-74 `credential-store-choice`. Every staged and parked spec was regenerated from its
  package file at 710da61 (Objective, Deliverables, Trace, Depends on; the Documents, shared-file and log constraints
  rewritten for the one-pull-request flow; the specification parts each package links added to its Expected
  Surface); earlier claims kept. The specs point at package files that reach `main` with #77, so no command is
  emitted before it merges.
- 2026-10-17 — After the landing of PLAN-75 the merged packages are regenerated one at a time, the emitted one first: the spec of
  PLAN-14 now carries the six deliverables of `operator-and-question-stores` and the former `project-machine-store`, its surface is
  the union of both specs minus the deleted package file, and PLAN-15 is `superseded`. Rationale for not regenerating all six pairs
  in the act of `analyze`: a spec that no one emits is re-grounded by `cleanup` anyway, and `cleanup` also owes the surface check
  for pm-mcp-core #84. Consequence: the three other absorbed rows (PLAN-23, 52, 65) are `superseded` at once, because `next` walks the
  staged rows by the sequencing notes of their specs, which name only PLAN-75 (shipped) and would have let it emit a package whose file no
  longer exists. Their specs stay in `plans/` as the source of the merge. The survivors PLAN-64, 70 and 53 absorbed the deliverables
  (PLAN-23: 1, PLAN-65: 5, PLAN-52: 6) later the same day, at the operator's order to do all splits and merges.
- 2026-10-17 — Operator, overlap prompt of the `next` after PLAN-75: emit PLAN-14 (with PLAN-15 merged in) into the one free slot,
  with 5 declared files shared with PLAN-20 and 6 with PLAN-69 (the README row counted in each). Accepted on the terms written into
  the spec: next free ADR number of `core-store` at commit and after each rebase (029 and 030 are already claimed), the guide extended
  in a section of its own, and the typed `waiver_requests[]` member on `WorkflowStatus` decided at refine (the raw reading leaves the
  three shared `WorkflowStatus*` files untouched).

## Defect: two decisions of 2026-10-18 reached the specifications after the last reconciliation

- **Two decisions of 2026-10-18 reached the specifications after the last reconciliation: pm-mcp-core #101 (`98ce971`, 21:49 local) and
  documentation #94 (`273171d`); analysed on 2026-10-19 and reconciled the same day.** PLAN-37 re-written (the differential harness is gone; the
  package is the tests of the ported scripts, `open`, no dependency; unparked to `staged`); PLAN-60 (Micrometer decided, dependency
  `micrometer-core` in `pm-runtime` only, `quarkus-micrometer` and the native build are `plan-marshall-mcp`'s); PLAN-49 (the HTTP base is
  the module `pm-provider-http`, built by PLAN-86); PLAN-78 (deliverables 8 and 10 lose `differential-test-harness` as an owner; new
  deliverable 14: the agreement per topic → `role-qualification`, the items owed to other repositories). **Staged as plans with no package
  file (the precedent is PLAN-81 to 85): PLAN-86 `provider-http-module`** (the move of the HTTP base out of `pm-provider-ci`, due because
  `pm-provider-sonar` already carries `SonarProvider` on it; one open design choice for the operator at refine: whether `pm-provider-ci`
  stays as an empty module), **PLAN-87** (journal version), **PLAN-88** (the `QuestionRecordReader` adapter) and **PLAN-89** (the stale
  `runtime-metrics` dependency in the index, the package file and row `module-developer-docs` for PLAN-40, the two Traceability entries,
  the enrolled-pattern length rule). PLAN-40 returns to `staged` when PLAN-89 has landed. Owed outside this repository, for the owners of
  those repositories: the skill tools without scope (`plan-marshall-mcp`), the ICU message subset in `pm-api` (`pm-mcp-clients`),
  `quarkus-micrometer` with the native build (`plan-marshall-mcp`), the Landlock check on WSL2 moved to Milestone 7. `UC-PRJ-4` and
  `UC-PLN-6` are `DRAFT` use cases without a requirement: no owner is sought yet. Not staged, because the entry itself is unverified: the
  rest of the roadmap item behind PLAN-02 (validators for `request.md` and pull request bodies and the other items listed above), and the
  `!===` reading of `AsciiDocValidator`.

## Defect: untriaged SonarCloud issues of #120, #121 and #122

- **Eighteen SonarCloud issues on PR #122 are untriaged (observed through the SonarCloud API, 2026-10-21).** The gate is OK, the landing says "quality
  gate passed" and does not mention them. Twelve are in `StalledScopeDetectorTest` (S5778 x5, S7467 x3, S6213, S1612, S9358, S8786), two are CRITICAL
  `S1192` duplicate literals in `AuditEvent.java:305-306`, one is `S1172` (unused parameter `projectId`, `WrapperVerification.java:52`), one `S3400`
  (`StalledScopeDetector.java:146`), one each in `AuditCodecTest` (S6213) and `JobRefusalTest` (S1612). **Decision 2026-10-21 (operator): the fixes of
  all 29 open issues (#120: 1, #121: 10, #122: 18) are Deliverable 5 of PLAN-87**, not a plan of their own; the other watches on #120 and #121 below stay
  as the record of where they were found.

- **Ten SonarCloud issues on PR #121 are untriaged (unverified lead).** The PR's Sonar comment says quality gate passed and 10 new issues; the lane
  did not read them and the orchestrator did not open them. Check the PR's Sonar page before the next change to the credentials package.

- **One SonarCloud issue on PR #120 is untriaged (unverified lead).** The landing reports quality gate passed and 1 new issue it did not read; the
  orchestrator did not read it either. Check the PR's Sonar page before the next change to `pm-provider-git` tests.

Closed 2026-10-22: the 29 issues of the three pull requests are all open on `main` at the same file, rule and line (cleanup of 2026-10-21) and are Deliverable 5 of PLAN-87 (operator decision of 2026-10-21); the other 80 are PLAN-94.

## Watch: the link check left this repository

- The link check left this repository (#88): a plan that deletes a package file linked from `plan-marshall-documentation` (today only
  `roadmap.adoc:233` -> `plan-instance-alignment.adoc`, PLAN-70) now breaks the documentation repository's check after its merge, not its
  own pull request. The Standing Constraint of PLAN-70 still asks for the sibling fix during execute.

Closed 2026-10-22: the documentation repository dropped the link to `plan-instance-alignment.adoc` (documentation #96, `8c1ddf4`), PLAN-70 shipped, and `roadmap.adoc` no longer links a deleted package file at `origin/main` (`b8c61b3`).

## Watch: the schema parity tests and the sibling checkout

- `FindingSchemaParityTest` of PLAN-36 and `WorkflowStatusSchemaParityTest` of PLAN-13 read the sibling checkout of `plan-marshall-documentation`
  (`pm.documentation.root`) and aborts by assumption when it is absent — in CI it most likely never
  runs. Check a CI log of `main`; if skipped, the parity guard exists only on developer machines.

Closed 2026-10-22: the specifications moved into this repository (pm-mcp-core #77, ADR 007; the decision of 2026-10-15 in "Decisions of 2026-10-15 to 2026-10-17") and the schema parity tests run in CI, with the drifting checks `@Disabled` naming their package.

## Watch: verification records of the opencode runbook landings

First opencode runbook landing (PLAN-78, #111, 2026-10-20), verified: the deliverables and the link check held (`BROKEN LINKS 0`), but two
absolute bullet counts in the report and in the squash message are one too low (`supervision-scenarios`, `chat-plan-revision`), the build
gate was not run (stale executor, see the watch above), no required reviewer reviewed the head and the merge went in on the operator's
selection (the spec's own `skip-bot-review` standing constraint against the lane's rule; only PLAN-78's spec carries it), and the token
total is `unknown`. Keep the watch open for PLAN-81 and PLAN-88.
Second opencode runbook landing (PLAN-88, #116, 2026-10-20), verified: the deliverables held, the adapter is the only product implementation of
the port, both required reviewers cover the merged head, the four Maven gates ran (plan-reported; the merge-group run is green) and the
orchestrator's own run of the three new test classes passed (69 tests); `trace.py` reports `BROKEN LINKS 0`. Still found: the lane had read
access to the documentation checkout and recorded no link-check run; the per-class method counts are off (37 reported, 34 counted; the 69
invocations hold); the gate path needed two operator decisions (the `resolve` verb's build names rejected, a CI substitution for the local
`verify` that the final local `verify` made unnecessary); `total_tokens` is `unknown`. The pass is clean in substance.
Third opencode runbook landing (PLAN-81, #114, 2026-10-20), verified: the seven deliverables held against the merged source and the specification
(constants equal the specification's numbers, the author comparison is NFKC first), the orchestrator's run of the four touched test classes passed
(550 tests), `trace.py` reports `BROKEN LINKS 0`, the main checkout is clean. Found: the lane edited six files in the main checkout before the
worktree and corrected it by hand (no guard on opencode to stop it); it ran no link check although it had read access; `cuioss-review-bot` covers
`5ca7772`, not the merged head `ead74de`, and the merge went in on the operator's selected "Merge with the gap"; `total_tokens` unknown. Two lane-contract
lessons are for plan-marshall's lessons-routing inbox. Retire the watch's "unreliable self-report" half after PLAN-81 and PLAN-88 only if the
operator agrees; the Antigravity case stays open.
Fourth opencode runbook landing (PLAN-90, #120, 2026-10-21), verified: the squash `73ec37a` is on `main`, 30 checks pass, and the eight merged files
match the four deliverables (no production code; fixtures, two `.adoc` files, three test-tree Java files). The `run` advisory is refuted as the
landing says (both `@throws` present at `WorktreeShaScenario.java:126-127`). One file was beyond the declared surface (`WorktreeShaPortedTest.java`,
one added probe). The lane's own test, link and Sonar claims were not re-run here; `total_tokens` unknown; the landing again reports the stale
executor (`build-classify` unknown, canonical build names rejected).
Fifth opencode runbook landing (PLAN-82, #121, 2026-10-21), verified: the squash `15fbe6f` is on `main`, 30 checks pass, the three review threads are
resolved, the seven named commits exist, and every test the landing names is in the merged tree under that name; `failureUnder`, the
`FIRST_RECORDING` operation and `transaction_id` are in the code, ADRs 007-009 and log ids 32/133/134 are unique. Eleven files lay outside the
declared surface (the record class, its three tests, the log holder, `EnrolmentKey`, three `.adoc` files). The lane reports T1 (test first) as not met
(`mutation-after`) and a self-review round 2 that was skipped for budget; the landing says so itself. Its rule breaks (first edits in the main
checkout, a `python3 -c` probe) are plan-reported; the main checkout is clean here. Build, test count and the Sonar claim were not re-run.
Sixth opencode runbook landing (PLAN-83, #125, 2026-10-21), verified: the squash `7d980c1` is on `main`, 30 checks pass, SonarCloud reports 0 new
issues, the nine named commits exist, the progress file agrees with the landing, and `keepForRestore`, `restoreKept`, `restore` and the log ids
33/34/135/136 are in the merged code and in `doc/LogMessages.adoc`. The one replaced test (`shouldKeepUnitOnManagerFailure`) inverts its
assertions by the decision of ADR 014; nothing is weakened. Six files lay outside the declared surface (`LogMessages.adoc`, `Traceability.adoc`, the
log holder and its test, two Javadoc paragraphs) and one declared file was untouched (the Upgrade section of `01-startup-recovery-drain.adoc`, kept
because only the unit part of the rollback exists). The macOS clause is met only at the library level (the operator accepted that; see the Watch).
Same stale-executor defect as before (canonical build names rejected, the lane read `~/.config/opencode/skills/`). Build and test counts were not re-run.
Seventh opencode runbook landing (PLAN-64, #122, 2026-10-21), verified: the squash `c6da0e2` is on `main`, 30 checks pass, the 18 review threads are all
resolved, and no Java file at `origin/main` names the removed types (`ScopeSession*`, `SessionGeneration*`, `FencedSessionGenerations*`,
`operatorCommand`, `CATEGORY_IDENTITY`, `entryClientChanged`); `expire_question` survives only in the refusal test the lane's matrix requires.
`StalledScopeDetector` is new with the 600 s threshold of the timeouts specification; `AuditRecord.Scope.ENTRY` exists (the operator's D4 decision).
D7 is not fully met, as the landing says: the roadmap `_Remaining_` update is owed in `plan-marshall-documentation`. Fifteen files lay outside the
declared surface (the log holder and its test, rows and files that named the package, two workflow ADRs, three new or changed tests) and ten declared
entries were untouched (mostly specification parts that were already corrected). The follow-up #127 (`c7780a0`) is the explanation of the uncommitted
`ServiceUnitInstallerTest` edit: 14 lines `throws IOException` to `throws Exception`, nothing else, merged with `skip-bot-review` on the operator's
word (plan-reported). The detector has no production caller: the stalled hold does not exist end to end until the assembly wires it. Build and test
counts were not re-run.

## Defect: specification ahead of the work packages (#132 to #134)

- **Specification ahead of the work packages: #132 to #134 added content no package owns (found by the analysis of PLAN-93, 2026-10-22; carried to the owning plans by the cleanup of the same day).**
  The operator's three specification-only merges (`672d746`, `ab1826f`, `50322a7`, 2026-10-21; articles `opus-5-5-prompting`, `first-level-thinker`, `barely-read-the-code`) changed 23 specification and watch files. **Carried into four staged specs** (section *Specification Added Since The Package File*, a claim stamped `corroborated` at `7756dfd` each): PLAN-66 `worker-activity` (the `stop_reason` of a turn, in the profile schema PLAN-19 already loads and in the activity journal), PLAN-31 `slots-usage-and-budgets` (the required `attempt_end` of a metrics row), PLAN-43 `phase-sub-workflows` (the trade-offs nobody named at refine, the three questions of the builder check), PLAN-47 `decision-engine` (the vote members `rationale_origin` and `probabilities`, rendered as composed). PLAN-19, 84 and 85 contain all three merges in their base (running, not re-scoped). **Not carried, because no package builds it** (`grep` of `doc/plans/`, the 94 specs and the index): the endpoint harness of `client-profiles/03` (its `locality`, `credential_key` and nullable `executable`; unowned before #133 too), the judgment endpoint `client-profiles/04` (protocol `system_one`, the request in two orders, versioned model ids, the remote form with its Token Lane credential; watch `PM-WATCH-CLIENT-14` and `-15`), judgment votes as a listed voter and the calibration `form` (`model-work/10`, `store-schemas/16`), the own calls of a writer (`submit:TASK-NNN` `own_calls`, the schema `own-call`, decision point `execute.own-call`, the server checks, the `## Own Calls` section of the pull-request body; `phase-workflows/03`, `hypermedia-format/02`, `model-work/06` and `11`, `state-catalogue/10`, `workflow-dsl/07`), and the change map with the removed coordinates of the dependency delta (`phase-workflows/05`; watch `PM-WATCH-PHASE-113` and `-114`). Owed: the operator's decision whether these become packages and for which milestone; until then they are specification without a plan.

**Closed 2026-10-22 (operator decision: every specification must be implemented):** the five areas became work packages. PLAN-96 `spec-ahead-work-packages` writes the five package files and index rows (`endpoint-harness`, `judgment-endpoint`, `judgment-votes`, `own-calls`, `change-map`); PLAN-97 to PLAN-101 build them. The roadmap of `plan-marshall-documentation` already had an item for each at `origin/main` (own calls: Milestone 9; change map: Milestone 10; endpoint harness and `system_one` with the judgment vote: Milestone 14); the local checkout of the documentation, nine commits behind, did not show them, which is why the first reading said the roadmap had none. PLAN-99, PLAN-100 and PLAN-101 are `parked`: the decision quorum, the task envelope with `submit` and the Task Diff Record, and the module discovery with the Dependency Delta Check have no work package (see the Open Defect of the same day).
