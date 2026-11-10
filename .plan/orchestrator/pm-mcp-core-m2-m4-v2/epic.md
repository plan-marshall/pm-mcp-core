# pm-mcp-core M2-M4, restructured

epic: pm-mcp-core-m2-m4-v2

> Hand-written narrative only. START HERE and the Ordered Queue are generated into `queue-view.md`; nothing generated is pasted here.

## Vision

Implement the work packages of `doc/plans/` of the staging window (Milestones 2 to 4 plus the groundwork and the conformance family named below; the operator's decision of 2026-11-09 applies the design audit's window rule: plans outside it are parked, not built) in this repository, one plan per shippable unit, each landing as one pull request here with its documentation pull request in `plan-marshall-documentation`. This epic succeeds `pm-mcp-core-m2-m4` (closed after its two running plans, PLAN-46 and PLAN-105, landed): it holds only the plans that were still unfinished (staged or parked) on 2026-10-29, re-cut from the ground up into twelve workstreams and ordered for four concurrent plans (`parallelization_scope` 4), with ids `PLAN-V2-NN` that run in the emit order of the first schedule. The epic is done when every package in the index `doc/plans/README.adoc` that it serves is `done`, or is recorded here as blocked on a decision or a dependency outside this repository.

The first thing it does, by the operator's decision of 2026-10-30, is the skills conformance family (PLAN-V2-83 to PLAN-V2-108, workstream WS-11): the code is brought to the coding skills of the marketplace before any new feature plan. The groundwork it adds next: the command model of the workflow engine (PLAN-V2-01, PLAN-V2-19, PLAN-V2-25 and the other plans of workstream WS-01), so that every plan that adds a primitive follows one SPI.

## Queue annotations

**Structure.** Eleven workstreams, each a theme and a surface; a plan belongs to exactly one. The n=4 schedule below is a list schedule (critical path first, command-model groundwork first, a pair never runs together when it shares two or more code entries), with the two running plans of the predecessor (PLAN-46, PLAN-105) holding two slots until they land. The unit of the time column is one deliverable; it orders the emit, it does not predict days.

| Workstream | Slug | Plans | Plan numbers (V2) |
|---|---|---|---|
| WS-01 | command-model-and-unit-contract | 7 | 01, 04, 10, 15, 19, 20, 25 |
| WS-02 | plan-instance-and-process-views | 9 | 13, 26, 33, 44, 49, 54, 57, 58, 69 |
| WS-03 | epic-and-plan-control | 11 | 21, 22, 23, 27, 32, 38, 41, 64, 74, 76, 77 |
| WS-04 | clients-onboarding-and-profiles | 12 | 17, 24, 29, 39, 40, 43, 52, 63, 68, 70, 71, 75 |
| WS-05 | stores-roles-and-facts | 12 | 02, 03, 05, 06, 07, 11, 16, 56, 60, 65, 95, 96 |
| WS-06 | runtime-worker-and-endpoint | 9 | 31, 42, 46, 47, 48, 53, 59, 66, 73 |
| WS-07 | build-chain | 7 | 08, 18, 28, 36, 37, 51, 55 |
| WS-08 | architecture-and-domains | 8 | 09, 14, 30, 35, 61, 78, 79, 80 |
| WS-09 | ci-and-providers | 3 | 12, 62, 72 |
| WS-10 | spec-bookkeeping-and-hygiene | 4 | 34, 45, 50, 67 |
| WS-11 | skills-conformance | 24 | 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108 |

**Schedule (first wave = t 0).** Plans in one row may run together; the disjointness gate still decides each emit.

| t | Plans (workstream, deliverables) |
|---|---|
| 0 | PLAN-V2-01 (WS-01, 5), PLAN-V2-02 (WS-05, 6) |
| 5 | PLAN-V2-03 (WS-05, 6) |
| 6 | PLAN-V2-04 (WS-01, 5), PLAN-V2-05 (WS-05, 7), PLAN-V2-06 (WS-05, 6) |
| 11 | PLAN-V2-07 (WS-05, 6), PLAN-V2-08 (WS-07, 5) |
| 12 | PLAN-V2-09 (WS-08, 5) |
| 13 | PLAN-V2-10 (WS-01, 5) |
| 16 | PLAN-V2-11 (WS-05, 5) |
| 17 | PLAN-V2-12 (WS-09, 6), PLAN-V2-13 (WS-02, 3) |
| 18 | PLAN-V2-14 (WS-08, 6) |
| 20 | PLAN-V2-15 (WS-01, 6) |
| 21 | PLAN-V2-16 (WS-05, 6) |
| 23 | PLAN-V2-17 (WS-04, 3) |
| 24 | PLAN-V2-18 (WS-07, 5) |
| 26 | PLAN-V2-19 (WS-01, 5), PLAN-V2-20 (WS-01, 5) |
| 27 | PLAN-V2-21 (WS-03, 3) |
| 29 | PLAN-V2-22 (WS-03, 1) |
| 30 | PLAN-V2-23 (WS-03, 3), PLAN-V2-24 (WS-04, 3) |
| 31 | PLAN-V2-25 (WS-01, 4), PLAN-V2-26 (WS-02, 3) |
| 33 | PLAN-V2-27 (WS-03, 3), PLAN-V2-28 (WS-07, 5) |
| 34 | PLAN-V2-29 (WS-04, 3) |
| 35 | PLAN-V2-30 (WS-08, 4) |
| 36 | PLAN-V2-31 (WS-06, 5) |
| 37 | PLAN-V2-32 (WS-03, 3) |
| 38 | PLAN-V2-33 (WS-02, 5) |
| 39 | PLAN-V2-34 (WS-10, 5) |
| 40 | PLAN-V2-35 (WS-08, 5) |
| 41 | PLAN-V2-36 (WS-07, 5) |
| 43 | PLAN-V2-37 (WS-07, 5) |
| 44 | PLAN-V2-38 (WS-03, 3) |
| 45 | PLAN-V2-39 (WS-04, 3) |
| 46 | PLAN-V2-40 (WS-04, 5) |
| 47 | PLAN-V2-41 (WS-03, 3) |
| 48 | PLAN-V2-42 (WS-06, 3), PLAN-V2-43 (WS-04, 3) |
| 50 | PLAN-V2-44 (WS-02, 6) |
| 51 | PLAN-V2-45 (WS-10, 6), PLAN-V2-46 (WS-06, 5), PLAN-V2-47 (WS-06, 5) |
| 56 | PLAN-V2-48 (WS-06, 5), PLAN-V2-49 (WS-02, 5), PLAN-V2-50 (WS-10, 5) |
| 57 | PLAN-V2-51 (WS-07, 5) |
| 61 | PLAN-V2-52 (WS-04, 5), PLAN-V2-53 (WS-06, 5), PLAN-V2-54 (WS-02, 3) |
| 62 | PLAN-V2-55 (WS-07, 5) |
| 64 | PLAN-V2-56 (WS-05, 4) |
| 66 | PLAN-V2-57 (WS-02, 4), PLAN-V2-58 (WS-02, 4) |
| 67 | PLAN-V2-59 (WS-06, 5) |
| 68 | PLAN-V2-60 (WS-05, 4) |
| 70 | PLAN-V2-61 (WS-08, 4), PLAN-V2-62 (WS-09, 4) |
| 72 | PLAN-V2-63 (WS-04, 4), PLAN-V2-64 (WS-03, 3) |
| 74 | PLAN-V2-65 (WS-05, 3), PLAN-V2-66 (WS-06, 3) |
| 75 | PLAN-V2-67 (WS-10, 2) |
| 76 | PLAN-V2-68 (WS-04, 3) |
| 77 | PLAN-V2-69 (WS-02, 1) |
| 79 | PLAN-V2-70 (WS-04, 3) |
| 82 | PLAN-V2-71 (WS-04, 2) |

**Wave 0 — the skills conformance family (operator decisions 2026-10-30; first priority, WS-11 and two plans of WS-05).** The audit of the eight modules (`skills-audit-2026-10-30.md`) found logging and Javadoc conforming and generated test data, contract tests, JSpecify, the HTTP test style, `Optional` parameters, record builders, assertion messages and hand-written fakes not. Twenty-five plans close it before any feature plan starts; they carry negative `seq` values so that `next` fills the slots with them first. Order at n=4: PLAN-V2-83 alone (dependencies, CLAUDE.md, ADR); PLAN-V2-84 alone (`@NullMarked`, `Optional` returns); PLAN-V2-107 (generator artifacts: the shared domain generators, before the test migration uses them); PLAN-V2-85, 86, 87, 88 (core store halves, core rest, workflow tests); PLAN-V2-89, 90, 91 (runtime tests) with PLAN-V2-94 (`@UtilityClass`, generic catches); PLAN-V2-92 (provider tests) and PLAN-V2-93 (HTTP tests); PLAN-V2-95 (lock manager channel close, before PLAN-V2-07) with PLAN-V2-97, 98, 99 (`Optional` to `@Nullable`, by module); PLAN-V2-100 (builders, `pm-core`) and PLAN-V2-108 (builders, the other modules) with PLAN-V2-106 (EasyMock for the fakes); last PLAN-V2-101 to 105 (a message on every assertion, by package group). PLAN-V2-96 (`role-qualification-adapter`) is a feature plan: it follows PLAN-V2-31. Every other plan depends on PLAN-V2-83 (dependencies and R20); the others start as the disjointness gate allows, after the wave has passed the packages they edit, because the wave plans declare their packages by directory (R19 is replaced for them). The first slots of the epic after the wave are PLAN-V2-01 to 04 as before.

**Added after the landings of PLAN-46 and PLAN-105 (ids 81 and 82 sit at the end of the id order, but the emit order is not the id order for them):** PLAN-V2-82 (`instance-composition-residue`, WS-02) has no dependency and runs first among the plans that edit the composer: before PLAN-V2-13, 21, 54 and 69; PLAN-V2-81 (`machine-stores-residue`, WS-04) has no dependency and runs before PLAN-V2-43 and PLAN-V2-50. Both are free to start in the first wave if the gate allows.

**Parked** (until the operator's decision): PLAN-V2-72, PLAN-V2-73, PLAN-V2-74, PLAN-V2-75, PLAN-V2-76, PLAN-V2-77, PLAN-V2-78, PLAN-V2-79. **After a parked plan:** PLAN-V2-80.

**Start condition.** Met in part: the predecessor's two running plans landed on 2026-10-29 (PLAN-105 as #230, PLAN-46 as #233, part 1 of 3 of `machine-onboarding-unit`) and their landing notes are folded into the specs below; this epic starts when the predecessor ledger is closed and archived and the shared ledger worktree is landed (operator decision of 2026-10-29). Mentions of PLAN-46 or PLAN-105 as running in the carried sections below are stale; a lane re-grounds a spec at refine (R07).

**Follow-up plans staged 2026-11-09 (PLAN-V2-123 to 131, all runbook lane).** 123 `schema-parity-drift-and-readers` (WS-12; Wa80, 81, 85; after 129), 124 `lock-manager-spec-and-os-lock-tests` (WS-05; Wa84; after 99, before 07 and 03), 125 `test-conformance-core-and-workflow` and 126 `test-conformance-runtime-and-providers` (WS-11; Wa66 to Wa83 residues; 126 after 125), 127 `npm-parser-success-and-redaction` (WS-07; after 18), 128 `ci-contract-pull-request-numbers` (WS-09), 129 `deferred-code-removal` (WS-10; after 99, before 119 and 123), 130 `json-schema-validator-3-upgrade` (WS-10; the pull request carries the label `skip-bot-review`; after 121, 119, 122, 123; replaces Dependabot #71). **Staged 2026-11-10:** 132 `coverage-parser-hardening` (WS-07, runbook lane; the layer-1 and layer-3 findings of the PLAN-V2-18 analysis; after 127, which shares `03-parsers-and-gates.adoc` and the `core-build/` ADR numbers). Each spec lists the ask-at-refine items it leaves to the operator and, under Carried Open Items, the residues it absorbed or that stay owned elsewhere. Parked on 2026-11-09 (dependency oracle and window rule): 52, 70, 23, 60, 42, 96 and the 23 plans outside the window; retired: 45, 76. 131 `build-log-bounds` (WS-07; size cap, line-bounded regexes, tail excerpt; after 127).

## Decisions

- 2026-10-29 — Operator: **restructure from the ground up** (this epic). All unfinished plans of `pm-mcp-core-m2-m4` (69 staged, 8 parked) moved here and renumbered `PLAN-V2-01` to `PLAN-V2-80` in schedule order (PLAN-V2-81 and 82, the residues of the last two landings, were added at the end); the predecessor rows carry the status `transferred`, the superseded and retired plans stay there as history and appear nowhere here. Parallelism 4. Each spec keeps a line `origin:` naming its predecessor id.
- 2026-10-29 — Operator: **drain, close and archive the predecessor before this epic starts.** No plan of this epic is emitted while PLAN-46 or PLAN-105 runs; the ledger of `pm-mcp-core-m2-m4` is closed and archived first.
- 2026-10-29 — Operator: **the command model is groundwork of this epic.** Three namespaces stay apart: configuration (effective keys `project.…`, `workflow.…`, `local-config.…`), facts (read-only, never overridable by a caller) and a per-call `input.<name>` (unit-local, declared in the frontmatter of the unit; qualified form `<unit id>.input.<name>` only in provenance and logs). A name is an outcome only where the graph continues differently; every other failure is a reason carried as data (typed, closed, data only: no routing on reasons). The call frame is a command and carries `command_id` and `correlation_id`. Later, not staged: `command-log-store` and `engine-writes-the-command-log` (three linked records tied by `command_id`; redacted, bounded input with a digest; replay as audit read or result replay, never re-execution; write-ahead for git and CI primitives).
- 2026-10-29 — Operator: this repository owns the production registries (configuration keys, fact kinds) and the git commit primitives with the entity kind `commit-message`; the daemon wiring stays in `plan-marshall-mcp`. The remainder of the commit unit with an owner but no plan is recorded under Open Defects.
- 2026-10-30 — Operator: **the coding skills of the marketplace are binding (R20), and the code is brought to them first.** Basis: the conformance audit (`skills-audit-2026-10-30.md`). Answers, one by one: the dependencies `cui-test-generator`, `cui-test-value-objects`, JSpecify (direct), Lombok in `pm-workflow` and `pm-provider-sonar` and EasyMock are allowed; all tests are migrated to generated data (exemption: a literal that is the contract); the twelve HTTP test classes go to `@MockResponseConfig` / `@ModuleDispatcher` and ADR 002 is superseded; every production package is `@NullMarked`, absence is `Optional`, and every existing `Optional` parameter, field and record component is converted to `@Nullable`; records of four or more components get `@Builder` (the documented rule); every assertion gets a message; the hand-written fakes become EasyMock doubles; the one exception: tests follow behaviour and the 400-line budget is only reported. Logging stays as it is: one message holder per module, never one for the whole repository (R12, R20). Each module with reusable test-data generators publishes a generator artifact (a test JAR deployed with the `SNAPSHOT`) that the modules allowed to depend on it use (PLAN-V2-107). Plans: PLAN-V2-83 to PLAN-V2-108; all other specs name R20, revision r4, and depend on PLAN-V2-83.
- 2026-10-30 — Operator: **six owed decisions answered.** (1) `judgment_task_completed` carries the machine-level `os_principal` (carried by PLAN-V2-81). (2) The pending-entry port of `QuestionRecordReader` belongs to PLAN-V2-22. (3) The lock manager loses no OS lock on a JSONL append: PLAN-V2-95, before PLAN-V2-07. (4) The grading adapter of role qualification: PLAN-V2-96, after PLAN-V2-31. (5) The remainder of the PLAN-02 roadmap item is checked against the roadmap first, then staged or retired. (6) The six `java:S1135` issues are the generated TODO comments above the six `catch (RuntimeException _)` clauses that PLAN-V2-46 replaces: no parent-POM change, no credential.
- 2026-10-30 — Orchestrator, on the operator's request for a lane plan to fix the red `main`: **PLAN-V2-109 (`store-digest-main-green`, WS-10) is staged, row seq 97, with no dependency on PLAN-V2-83.** Finding: the `Maven Build` of `main` has failed on every push since at least 2026-10-28 (30 consecutive runs); in the newest (`132a7a0c`) only `sonar-build` fails, on the SonarCloud gate of `branch=main`, whose one failing condition is `new_reliability_rating` (4 against 1) because of the single `BUG` `java:S9428` at `StoreDigest.java:97` (created 2026-10-11, inside the new-code period); the 12 other open issues are code smells and fail no condition. Why not PLAN-V2-67: its verdict bullets are all `verdict_unparsed` (admits false), its count of 9 open issues is stale (13), and it carries a second deliverable (S112) and two ADRs that do not decide the gate. PLAN-V2-109 takes D1 of PLAN-V2-67 (the inline suppression with the reason beside it, plus its ADR); PLAN-V2-67 drops D1 when 109 lands. The departure from wave 0 (PLAN-V2-83 alone) is that 109 runs beside 83: they share no file. Launch is the operator's to confirm.

- 2026-10-31 — Operator, on the red `main` after PLAN-V2-84: **fix the dead null checks and null returns at the sites, do not ignore the rules** (offered: ignore S2583, S2589 and S2637 as ADR 011 ignores S4449; wait for PLAN-V2-97 to 99; fix now). Staged as PLAN-V2-110. **Sequencing decision of the orchestrator that follows from it:** PLAN-V2-110 edits the production classes and tests of 99 files and changes signatures (`Optional` returns), and PLAN-V2-85 to 93, 94, 97 to 99, 100 and 108 edit the same classes (the gate reads 96 shared files with 94, 75 with 97 and 100); **none of them is emitted before PLAN-V2-110 has landed.** Their specs are not edited for it; the disjointness gate sequences them while 110 runs, and this line covers the window in which 110 is staged and not yet launched. PLAN-V2-107 (generator artifacts) and the feature plans that share no file with 110 (PLAN-V2-01, 02, 03 and, by one file, 04) are free to run beside it.
- 2026-11-06 — Operator (relayed by the schema-transition orchestrating session in `schema-transition-001.md`, and by the operator's word that the pull request #304 was merged): **the schema transition is the highest priority of the epic; `schema-files-foundation` is the next plan to emit, ahead of the PLAN-V2-84 family wherever the surfaces allow.** Eight plans staged in the new workstream WS-12 (`workstreams/WS-12-schema-files-transition.md`): PLAN-V2-115 `schema-files-foundation`, 116 `schema-files-tested-records`, 117 `schema-files-machine-stores`, 118 `schema-files-remaining-records`, 119 `schema-files-other-specifications`, 120 `limits-in-data-files`, 121 `generated-listings` (**parked**: the open question `generated-listings` of the documentation repository; never emitted before the operator decides), 122 `adoc-reading-retired` (last; depends on 121 too). Runtime schema validation (`runtime-schema-validation`) is not planned. R10 ("specifications can be test input") is replaced for the family: the blocks a parity test reads are moved into files and the tests read the files (stated in each spec). 120 waits for PLAN-V2-89 and 98 (it edits `StalledScopeDetectorTest` and `UnitFrontmatterReaderTest`); PLAN-V2-91 (`credentials/CredentialStoreMoveRecordParityTest`) is held behind 117. The queue `seq` of the new rows is the staging order (the row verb allocates the next number), the emit order is decided at each `next`.
- 2026-11-08 — Operator (relayed by the schema-transition orchestrating session in `schema-transition-002.md`; the package files were rewritten in pull request #326, merged as `9c867f05`): **the two open questions of the schema transition are decided.** (1) `generated-listings`: the log message tables of `doc/LogMessages.adoc` are generated from the holders (identifier and template, a committed file below `doc/`, included by the document, one byte-for-byte test per module; the Component and Description columns go); the three protocol lists of `workflow-task.adoc` are dropped for links to the Javadoc and `ProtocolVocabularyParityTest` is deleted. (2) `runtime-schema-validation`: the schema files serve the tests; no run-time validation of a stored record; `networknt` stays test-scope. PLAN-V2-121 is unparked (`staged`, spec rewritten to the five tasks, lane `plan-marshall`) and PLAN-V2-122's narrowing of `**/doc/**` is unconditional. **Nothing of 121 is emitted without the operator's word.**
- Decisions taken in the predecessor that still bind (the full text is in its `history.md`): R05 reads "condense and point"; the CI contract is the module `pm-provider-ci`; the epic ledger records and the lesson store are packages before their consumers; `core.id` and `workflow.primitive` stay, the envelope package is cut; outbound calls are guarded by an ArchUnit rule in `pm-runtime`; `build_systems` is an array on the module record; a strict TOML subset reader lives in `pm-core`; the role-name pattern has one definition (run early); the PLAN-35 package stays open for the conformance harness D6 and D7.
- 2026-11-09 — Operator: **the staging window is applied and the residues get follow-up plans.** Hold or build is decided by dependency (a plan is parked only when no in-window plan depends on it); PLAN-V2-45 and 76 are retired; PLAN-V2-129 deletes the deferred code (the one approved code package); the registry repin is ignored; PR #71 becomes PLAN-V2-130 with the label `skip-bot-review`; fix plans 123 to 128 take the residues of Wa65 to Wa85 (one plan per module family for the test residues); 12 old open defects were settled by the orchestrator at over 90% confidence (`settled.md`); the lesson about the runtime-info seam went to plan-marshall's `lessons-routing` inbox.

## Open Defects

> ↪ Relocated to `settled.md` § "The schema files of the tested records are ahead of their codecs by two enumeration values, and 16 of the 27 files have no reader (settled 2026-11-09, assigned to PLAN-V2-123)" — assigned to PLAN-V2-123 (staged 2026-11-09)
> ↪ Relocated to `settled.md` § "The lock-manager fix of 2026-11-09 leaves the specification false and its unit tests unable to show the OS lock (settled 2026-11-09, assigned to PLAN-V2-124)" — assigned to PLAN-V2-124 (staged 2026-11-09)
> ↪ Relocated to `settled.md` § "The generated-data migration of the pm-runtime worker, endpoint, credentials, enrolment and question tests left small defects no plan owns (settled 2026-11-09, assigned to PLAN-V2-125 and PLAN-V2-126)" — assigned to PLAN-V2-125 and PLAN-V2-126 (staged 2026-11-09)
> ↪ Relocated to `settled.md` § "The limits move of 2026-11-08 left a test-strength gap and small defects no plan owns (settled 2026-11-09, assigned to PLAN-V2-125 and PLAN-V2-126)" — assigned to PLAN-V2-125 and PLAN-V2-126 (staged 2026-11-09)
> ↪ Relocated to `settled.md` § "The two schema-file moves of 2026-11-08 left test-strength and drift findings no plan owns (settled 2026-11-09, assigned to PLAN-V2-123 and PLAN-V2-125)" — assigned to PLAN-V2-123 and PLAN-V2-125 (staged 2026-11-09)
> ↪ Relocated to `settled.md` § "`main` is red again after PLAN-V2-84 (resolved 2026-11-05)" — resolved by PLAN-V2-110 and PLAN-V2-112 (#293); the entry itself says it is retired
- **Runbook-lane landings carry `total_tokens` and `plugin_version` as `unknown`, so `landing-check` reports them incomplete (first found at PLAN-V2-83, 2026-10-30; 20 landings to PLAN-V2-128, 97 and 99, 2026-11-10; the PLAN-V2-18 block also left out `unresolved_review_threads` and `review_body_findings_total`, and the PLAN-V2-99 block left out the four review and Sonar numbers; the orchestrator re-read them from the provider).** The runtime-info seam reports neither value for the runbook lane; `unknown` is the honest value and every landing was reconciled in full. Routed 2026-11-09 as a candidate lesson to plan-marshall's `lessons-routing` inbox (message `pm-mcp-core-m2-m4-v2-007.md`, written in that repository's ledger worktree, uncommitted until its `land`). Stays open as the record until the seam reports both; later landings with the same two keys need no new line here.
> ↪ Relocated to `settled.md` § "PLAN-V2-97 landed with a false review-and-Sonar claim, and a merge-gate gap (settled 2026-11-10, owned)" — owned by PLAN-V2-125 (S5778), 114 and 126 (the two threads) (2026-11-10)
> ↪ Relocated to `settled.md` § "The Optional-to-@Nullable conversion left code and decision residues no plan owns (settled 2026-11-10, assigned to PLAN-V2-114)" — assigned to PLAN-V2-114 (Carried 2026-11-10, operator decision 1.iii)
> ↪ Relocated to `settled.md` § "PLAN-V2-18 (`f97bc7f2`): the XML hardening of the coverage parsers rests on a text prefilter and an unmeasured premise (settled 2026-11-10, assigned to PLAN-V2-132)" — assigned to PLAN-V2-132 (staged 2026-11-10)
> ↪ Relocated to `settled.md` § "A plan started with the ledger-worktree path of its spec files no landing message of its own (settled 2026-11-09, resolved)" — resolved 2026-11-09: epic-relative emit path in every hand-off command
> ↪ Relocated to `settled.md` § "The design audit of 2026-11-02 asks for plan actions this epic has not taken (settled 2026-11-09, decided by the operator)" — decided 2026-11-09 (window applied; deletion staged as PLAN-V2-129)
Owners of every item below and of the items dropped from this section: `owner-register.md` (assigned to a plan, owed outside, awaiting a decision, dropped as history).

> ↪ Relocated to `settled.md` § "The epic ledger's surrounding pieces have no package (settled 2026-11-09, deferred (operator: agreed))" — deferred, agreed (2026-11-09)

> ↪ Relocated to `settled.md` § "PLAN-98 residues (settled 2026-11-09, owned and partly moot)" — owned by PLAN-V2-53 and 67 (Op1); judgment parts moot after PLAN-V2-129 (2026-11-09)
> ↪ Relocated to `settled.md` § "PLAN-106 shipped the library half; the assembly halves of D1 to D4 have no staged owner (settled 2026-11-09, owed outside)" — owed outside (E1) (2026-11-09)
> ↪ Relocated to `settled.md` § "PLAN-97 shipped 9 of 10; three residues (settled 2026-11-09, owned and owed outside)" — owned by PLAN-V2-46 (S3776), the rest owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "PLAN-43 open items the lane recorded, not fixed (settled 2026-11-09, decided by the operator)" — decided 2026-11-09: phase `completed` for all delivery ends (PLAN-V2-57)
> ↪ Relocated to `settled.md` § "`client-profile-measured-values` is a new open package with no row and no spec in this ledger, and it blocks the rest of `question-continuation` (settled 2026-11-09, deferred behind a measurement)" — deferred (E2) (2026-11-09)
> ↪ Relocated to `settled.md` § "PLAN-35 shipped 3 of 7 deliverables; D1 (settled 2026-11-09, owned)" — owned by PLAN-V2-33 and 49 (2026-11-09)
> ↪ Relocated to `settled.md` § "`token-usage` is partial and its remainder has no owner (closed 2026-10-28)" — closed by PLAN-107 (#215)
> ↪ Relocated to `settled.md` § "Two code residues of the token-usage closure (settled 2026-11-09, owned)" — owned by PLAN-V2-56 (Op12) (2026-11-09)

- The roadmap item behind PLAN-02 (roadmap line 230) was checked on 2026-10-30 (`plan02-roadmap-check-2026-10-30.md`): the runtime binding of free parameters is open and carried by PLAN-V2-15; the OutlineValidator rules that need the working tree and the task derivation are Milestone 8 work for a package not yet cut (after PLAN-V2-35); the closed gate list belongs to the open packages `remediation-units` and `finalize-pipeline-and-merge`; the validators for `request.md` and PR bodies and the keyword-list check are not required by any specification. The overlap between roadmap line 230 and the Milestone 7/8 items is a correction for the documentation repository.
> ↪ Relocated to `settled.md` § "The port `QuestionRecordReader` cannot address a pending entry (settled 2026-11-09, owned by PLAN-V2-22)" — owned by PLAN-V2-22 item 13 (2026-11-09)
> ↪ Relocated to `settled.md` § "Owed outside this repository, from that analysis (settled 2026-11-09, owed outside)" — owed outside (E3) (2026-11-09)


> ↪ Relocated to `settled.md` § "Nothing in production records `credentials.store`, so a fresh install refuses every credential (settled 2026-11-09, owed outside)" — owed outside (E4) (2026-11-09)

> ↪ Relocated to `settled.md` § "`SchemaSubset.checkString` evaluates a pattern before it checks `maxLength` (closed 2026-10-22)" — closed by #144 (PLAN-95)
> ↪ Relocated to `settled.md` § "PLAN-25's package bookkeeping is partial (settled 2026-11-09, owned)" — owned by PLAN-V2-34 (Op23) (2026-11-09)
- **Lead confirmed and owned (2026-11-09): `ProfileValidator` misses the 30-second margin of a bounded wait.** Fixed by staged PLAN-V2-68. ↪ Full text: `settled.md` § "`ProfileValidator` accepts a bounded wait the specification forbids (settled 2026-11-09, owned by a staged plan)".
> ↪ Relocated to `settled.md` § "Unverified lead (settled 2026-11-09, owned)" — owned by PLAN-V2-65 (Op25) (2026-11-09)
> ↪ Relocated to `settled.md` § "Two SonarCloud items of PLAN-94 are unsettled, and 2 further issues of `main` are owned by no plan (settled 2026-11-09, owned)" — owned by PLAN-V2-67 (Op27) (2026-11-09)
> ↪ Relocated to `settled.md` § "The 8 work packages #151 cut have no orchestrator row and no staged spec (settled 2026-11-09, deferred by the window rule)" — deferred (window rule) (2026-11-09)
> ↪ Relocated to `settled.md` § "`job-confinement-and-environment` (settled 2026-11-09, owned)" — owned by PLAN-V2-59 (Op29) (2026-11-09)
> ↪ Relocated to `settled.md` § "The `module-structure.adoc` correction for the HTTP provider move (settled 2026-11-09, moot)" — moot (2026-11-09)
> ↪ Relocated to `settled.md` § "R05 is practised as 'condense and point', not 'delete' (settled 2026-11-09, resolved)" — resolved (2026-11-09)
- **Owned (2026-11-09): verify-not-resolve validation of a composed worker vector** — staged PLAN-V2-48; its text quotes a "Remaining:" sentence that no longer exists on main, refresh at the outline. ↪ `settled.md` § "The verify-not-resolve validation of a composed worker vector has no owner (settled 2026-11-09, owned by a staged plan)".
> ↪ Relocated to `settled.md` § "The post-merge findings of PLAN-31 are owed (closed 2026-10-26)" — closed by #185 (PLAN-104 D3 to D6)
> ↪ Relocated to `settled.md` § "Ten staged specs place HTTP endpoints in `pm-runtime`, whose `web` package holds only the listener state and certificate (settled 2026-11-09, decided by the operator)" — decided 2026-11-09: companion package outside, logic stays here
> ↪ Relocated to `settled.md` § "Eleven staged specs ship default units, roles or fixtures whose home is `plan-marshall-mcp` (settled 2026-11-09, decided by the operator)" — decided 2026-11-09: companion package outside, logic stays here
> ↪ Relocated to `settled.md` § "The CI contract has no module and no work package (settled 2026-11-09, resolved)" — resolved (2026-11-09)
- **Deferred (2026-11-09): the epic records (`epic.json`, queue row, inbox, registers) have no class.** All consumers (03, 07, 21, 27, 32, 38, 64) are parked; the first one unparked creates them. The lesson store is resolved (PLAN-V2-11, 16). ↪ `settled.md` § "No package builds the epic ledger records, and no package builds the lesson store (settled 2026-11-09, split (resolved + deferred))".
> ↪ Relocated to `settled.md` § "Specification content of #141, #165, #171, #173 and #174 has no staged owner, and the packages that would own it are not staged (settled 2026-11-09, deferred with its packages)" — deferred with the unstaged packages (2026-11-09)
- **Owned (2026-11-09): `HarnessEnrolmentStore.decode` accepts a missing `harnesses` member** — staged PLAN-V2-46 D5. ↪ `settled.md` § "`HarnessEnrolmentStore.decode` reads a missing `harnesses` member as an empty map (settled 2026-11-09, owned by a staged plan)".
> ↪ Relocated to `settled.md` § "UPDATE 2026-10-27 (settled 2026-11-09, owned and owed outside)" — owned by PLAN-V2-31, loop owed outside (2026-11-09)
- **Owed outside (2026-11-09): the supervision loop** (scheduler and transport) belongs to `pm-mcp-server` in `plan-marshall-mcp`; the library share is PLAN-V2-31. ↪ `settled.md` § "No plan owns the supervision loop that drives the worker supervisor (settled 2026-11-09, owed outside this repository)".
> ↪ Relocated to `settled.md` § "#190 deleted the specification sentence that PLAN-73's Deliverable 7 depends on (settled 2026-11-09, resolved)" — resolved (2026-11-09)
- **Deferred (2026-11-09): the grading adapter of `role-qualification`** — owner PLAN-V2-96, parked. ↪ `settled.md` § "The grading adapter of `role-qualification` has no owner (settled 2026-11-09, deferred to a parked plan)".
> ↪ Relocated to `settled.md` § "The question-loop verification of `plan-marshall-mcp` is `done` (settled 2026-11-09, superseded and deferred)" — superseded; deferred behind the measurement (2026-11-09)
> ↪ Relocated to `settled.md` § "The specification of #181, #186 to #189, #191, #192 added requirements no staged package carries (settled 2026-11-09, deferred with its packages)" — deferred with owners; (3) V2-53, (7) V2-46 and 59 (2026-11-09)
> ↪ Relocated to `settled.md` § "Two staged specs call `runtime.worker` greenfield and it is not (settled 2026-11-09, moot)" — moot (2026-11-09)

- 2026-10-29 — Remainder of the commit unit with an owner but no plan (found drafting PLAN-V2-10 and PLAN-V2-20; owner: this repository; to be staged when its first consumer is emitted, after PLAN-V2-20): the table of COMPILED DEFAULT values (PLAN-V2-10 pins only whether a default exists), the commit attempt counters (cap 3 and the change-set cap), the change-ledger row and the `freshness_reconcile` stamp, the mapping of `footprint_violation` to a condition, footprint checks (2) pinned build files changed and (3) the main-checkout baseline, and Rule 3 on `depends_on_config` (still a seam per ADR `workflow-verify/004` and `006`). Verify against HEAD before specifying.

- **Owned (2026-11-09): landing residues of PLAN-46 and PLAN-105** — staged PLAN-V2-81, 82, 13, 57 and 43. ↪ `settled.md` § "Landing residues of PLAN-46 and PLAN-105 (settled 2026-11-09, owned by a staged plan)".

## Watches

> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-12 landed (settled 2026-11-09, owned)" — owned (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-11 landed (settled 2026-11-09, owned)" — owned (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-08 landed (settled 2026-11-09, decided by the operator)" — decided 2026-11-09: PLAN-V2-131 (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-110 landed completely (settled 2026-11-09, owned)" — owned (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-16 landed (settled 2026-11-09, owned)" — owned (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Operator decisions of 2026-11-04 on the analysis findings: three fix plans staged (settled 2026-11-09, resolved)" — resolved (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-111 landed (settled 2026-11-09, owned)" — owned (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: PM-WATCH-STORE-43 was deleted by PLAN-V2-05 on tests that do not exercise its guard (settled 2026-11-09, resolved)" — resolved (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: PLAN-V2-01 landed (settled 2026-11-09, owned)" — owned by PLAN-V2-67 D4 (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: A ledger-only pull request runs the full Maven build (settled 2026-11-09, moot)" — moot (2026-11-09)
- **ADR 011 ignores `java:S4449` for every Java source (PLAN-V2-84, operator answer of 2026-10-31).** One property in the root `pom.xml` (`sonar.issue.ignore.multicriteria.e2`), the second ignore after `java:S4032` (ADR 008). Retire when Sonar's rule recognises `org.jspecify.annotations.Nullable`, or when the operator narrows or removes it; any later request to ignore a further rule (see the Open Defect on the red `main`) is the same kind of decision and is the operator's.
- **CodeRabbit refuses a pull request of more than 100 files ("Too many files"); PLAN-V2-84 had to be cut into five pull requests (285 files).** Plans whose Expected Surface is directory-wide (PLAN-V2-85 to 93, 97 to 99, 100, 101 to 105, 108) will meet it; the lane splits by module or directory before it opens the first pull request. Retire when the limit is raised or a plan of that size has landed in one pull request. **Recurred 2026-11-09:** PLAN-V2-87 (#290, 117 files, split into #294 and #295) and PLAN-V2-98 (#309 and #310); still affected: PLAN-V2-97 and 99 (running), 100 to 105 and 108 (staged).
> ↪ Relocated to `settled.md` § "Watch: ADR `doc/adr/002` still reads 'Accepted' while R20 replaces its dispatcher rule (settled 2026-11-09, resolved)" — resolved (2026-11-09)
- **Cross-repo links baseline.** The eight broken links of 2026-10-30 are obsolete (the PM-WATCH-STORE-43 anchor is restored, the documentation head moved); a plan's R17 run against the freshest documentation `origin/main` (fetch first, the sibling checkout lags) is the only baseline. Not re-measured 2026-11-09.
- **A runbook lane that resolves `sonar: not_configured` cannot transmit a Sonar dismissal (PLAN-V2-83 landing, section Residue).** Still true as a fact about the lane (the project config carries no Sonar project key), but no plan needs the dismissal today: PLAN-V2-109 confirmed that the analysis honours an inline `@SuppressWarnings("java:S9428")`, so its fallback went unused. Retire when the project config carries the Sonar project key, or when a plan first needs a dismissal.
- **Acceptance of unenforced egress has no specification (operator decision 27, 2026-10-29: recorded, not specified).** PLAN-V2-59 writes `egress: unenforced` on every `llm` job and adds the `egress` activity kind; nothing writes `enforced` until `pm-exec` (pm-mcp-clients) reports it. Not specified anywhere: how the operator accepts unenforced egress, the audit record that writes and where it is stored, the admission gate, and the runtime-status line (`runtime-lifecycle/01:67` has none; `AuditEvent` has `egress_refused` only). The design belongs with requirement PM-SEC-5 (documentation repository) and the onboarding consent store (PLAN-46). Revisit when `pm-exec` ships its enforcement or PLAN-46 has landed.

> ↪ Relocated to `settled.md` § "Watch: Audit identity of `judgment_task_completed` (settled 2026-11-09, owned)" — owned (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: Owed to the milestone exit after PLAN-70 (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Filed 2026-10-21 in plan-marshall's `live-blockers` inbox (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
- **`/tmp` inode exhaustion on the operator's machine (PLAN-96 landing, 2026-10-22).** The local gate failed with 3 of 1,048,576 inodes free (OpenRewrite and JUnit temp directories); the lane cleaned stale `build-map-seed-tree-*`, `junit-*`, `java-template*`, `base-probe-*`, `executor-mappings-*`, `guard-node-*` and `cuioss-review-bot-artifact-guard-*` directories on the operator's selected option, freeing about 508k inodes. `/tmp` is still at 52% inode use and the machine-wide cause was not identified. Expect the next full `verify` run in a lane to hit it again. **Hit again 2026-10-22 (PLAN-95): `/tmp` inode table 100 % full, the operator chose to clean build scratch.** Retire this watch when a lane reports `/tmp` inode use back at the usual level. **Recurred 2026-11-09:** `/tmp` read 99% of its inodes (about 20k free) and filled the scratch volume twice during the resume; it was cleared by the operator (cause not identified; `df -i /tmp` read 12% afterwards). Keep as a standing watch (Wa6).
> ↪ Relocated to `settled.md` § "Watch: #150 (settled 2026-11-09, resolved)" — resolved (2026-11-09)
- The Secret Service path (Linux) is unproven end to end (stated on plan-marshall-mcp #88 and #90; the Keychain path was measured on macOS). In this repository `SecretServiceIT` of `pm-runtime` is `@EnabledOnOs(LINUX)` and skips itself by `assumeTrue` when there is no session bus address or the default collection is locked, so a CI run proves nothing for it. Proving it takes a Linux desktop session with an unlocked default collection running `verify -Pintegration-tests -pl pm-runtime -am`; that is plan work or the operator's, not an orchestrator read. Retire this watch when a run on such a session shows the test executed and green.
> ↪ Relocated to `settled.md` § "Watch: The native build of `pm-mcpd` in `plan-marshall-mcp` against the deployed SNAPSHOT has not run since #90 and # (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Unverified leads from the review bot on #135 (settled 2026-11-09, owned)" — owned (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: Owed after PLAN-69 (settled 2026-11-09, resolved and owed)" — resolved and owed (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: A pm-runtime adapter for `core.spi.PendingEntryReader` over `PendingEntryStore` is owed (settled 2026-11-09, owned and owed)" — owned and owed (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: plan-marshall-mcp still owes (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: plan-marshall-mcp also owes (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: Plan instance code behind its specification (settled 2026-11-09, resolved and owned)" — resolved and owned (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Specification issue `plan-marshall-documentation` #71 (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Verifier performance on the real merged plan unit (settled 2026-11-09, owned)" — owned (2026-11-09)

- No plan records its harness (high-priority finding `pm-mcp-core-m2-m4-132.md`). The operator's rule since 2026-10-13: assume Claude Code unless stated otherwise.
- **Non-Claude-Code runs (Antigravity, opencode) need the extended landing verification** (code-vs-package audit, build-log and
  gate check, review trail), because the plan's self-report was unreliable on its first run (seven
  high-priority findings at plan-marshall). Retire when an Antigravity landing passes it cleanly.
> ↪ Relocated to `settled.md` § "Watch: Owed outside PLAN-64 (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Owed outside PLAN-83 (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Install, restore and uninstall share the kept pair with no cross-process owner (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
- **A failed drop after a successful load can restore an OLDER unit later (unverified lead from the bot, behaviour corroborated).** CodeRabbit's
  security-architecture note on #125: when the load succeeds and the marker cannot be deleted, `install` stays a success (WARN `PM_RUNTIME-136`,
  `dropAfterLoad`), and a later start or failed install restores the kept, older unit. The design documents it; the bot proposes separating the install's
  commit state from the cleanup of the pair. Not in the landing's Residue. Re-read when the start is wired.
> ↪ Relocated to `settled.md` § "Watch: Owed outside PLAN-82 (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
- **The trust assumptions of the question reader before it is wired (unverified lead).** CodeRabbit's architecture review of #116 says the
  confinement keeps the check-time limitation of `WorkspaceConfinement` (the snapshot opens the path after the check) and that an out-of-band
  change of `questions.jsonl` that is still syntactically valid is not detected (no digest, no replay). It proposes fixing the ownership and
  recovery guarantees of the runtime store before the production wiring. Inferred by the bot, not verified here. Re-read when the CDI producer
  is planned in `plan-marshall-mcp`.


> ↪ Relocated to `settled.md` § "Watch: Closing a channel on a POSIX file drops the process's OS locks on (settled 2026-11-09, resolved)" — resolved (2026-11-09)

> ↪ Relocated to `settled.md` § "Watch: The `plan-marshall-mcp` package that wires `FileLockManager` must create one instance per runtime (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
- `verify -Ppre-commit` of `pm-mcp-parent` is not idempotent and rewrites files a plan never
  touched (27 `pm-runtime` files on PLAN-01; lesson 2026-10-10-14-001). Every running plan can
  pull unrelated diffs into its PR. A parent POM change, outside this repository.
  **Observed again 2026-10-23 (PLAN-87, #148):** after merging `origin/main` the gate rewrote 10 files the plan does not touch; the operator chose to commit them (`fa09d68`), so they ride inside the 23-file squash. Unlike #147 they are not separable from the plan's own work in the merged diff — the footprint stays accounted for (19 declared files realised, 4 `S1172`-ripple files), but the rewrite still inflates a PR's declared surface. Third instance in three landings.
  **Observed again 2026-10-23 (PLAN-94, #147):** 10 files of already-merged `#146`/`#150` code (`workflow.model` ×7, `core.role` tests ×3, +23/−16) rode into the PR and into its 75-file footprint that declared none of them — the largest instance so far, and the reason the landing's `surface_delta` reads `expansion_detected`.

> ↪ Relocated to `settled.md` § "Watch: Owed to the milestone exit by PLAN-72 (settled 2026-11-09, owed outside)" — owed outside (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Specification parts that no work package or staged plan names (settled 2026-11-09, resolved)" — resolved (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: Step 3a of the job runtime specification is delivered for a probe only, and the owners of the rest are not cor (settled 2026-11-09, owned and owed)" — owned and owed (2026-11-09)
> ↪ Relocated to `settled.md` § "The record's stage and platform gate had no owner (closed 2026-10-26)" — closed by #190 (PLAN-30 D10 and D11)
- **`StalledHold.kind()` became `StalledHold.KIND` (PLAN-87, #148; outside-repository check owed).** S3400's prescribed fix replaced the accessor with a `public static final String KIND = "stalled"` constant. No consumer remains in this repository, and a grep of the `plan-marshall-mcp` checkout finds no `StalledHold` reference at all, so nothing breaks today; re-check at the next SNAPSHOT deployment, because that checkout is behind and the assembly could still name the old accessor. Retire when a SNAPSHOT-based build of `pm-mcp-server` compiles.
> ↪ Relocated to `settled.md` § "Watch: `pm-provider-ci` still appears where the HTTP move (settled 2026-11-09, moot)" — moot (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: #172 changed `pm-core` and the shared `runtime.job` seams outside PLAN-29's declaration (settled 2026-11-09, moot)" — moot (2026-11-09)
> ↪ Relocated to `settled.md` § "Watch: #169 changed `TaskProtocol` outside PLAN-32's declaration, and a staged spec declares it (settled 2026-11-09, owned)" — owned (2026-11-09)