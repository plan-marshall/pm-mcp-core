# Epic: pm-mcp-core work packages, roadmap Milestones 2 to 4

slug: pm-mcp-core-m2-m4

> Hand-written narrative for one epic under `.plan/orchestrator/pm-mcp-core-m2-m4/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug pm-mcp-core-m2-m4`
> on the merged tree, and `git add` the result.

## Vision

Implement every work package of `doc/plans/` in this repository — the 35 packages that serve
roadmap Milestones 2 to 4 — one plan per package, each landing as one pull request here with its
documentation pull request in `plan-marshall-documentation`. The work is too large for one plan
because the packages form a dependency graph about nine packages deep that spans `pm-core`,
`pm-workflow` and `pm-runtime`. The index `doc/plans/README.adoc` stays the only place that states
a package's status and dependencies; this epic sequences against it and never replaces it. The
epic is done when every package in that index is `done`, or is recorded here as blocked on a
decision or a dependency outside this repository.

## Queue annotations

- PLAN-05 — not a package of `doc/plans/`: staged from the operator's HTTP rules, because the
  existing provider tests use hand-made `FakeServer` classes and `CiHttpClient` imports
  `java.net.http`. It adds its own package file and index row.
- PLAN-19 — parked: waits on `pm-mcp-clients` / `profile-documents` (`open` there on 2026-10-09).
  It gates PLAN-25 `waits` and PLAN-26 `role-sets-and-model-caller`, and through them most of
  WS-04 and WS-05 — the first external dependency on the critical path.
- PLAN-27 — staged, but also waits on `pm-mcp-clients` / `exec-read-denied-paths`.
- PLAN-04, PLAN-06 — shipped 2026-10-11 as #25 and #26.
- PLAN-09, PLAN-11, PLAN-12, PLAN-14 — ready since PLAN-06 shipped, but all four declare
  `core.store/` and overlap each other: run one at a time unless the operator pairs them.
  PLAN-09 also declares `runtime.start/` and so overlaps PLAN-08. PLAN-09 shipped as #69 on
  2026-10-14 (3236ed5); PLAN-11 and PLAN-12 had shipped before it. Of the four, only PLAN-14 is left.
- PLAN-24 — shipped as #67 (2ebec2a, documentation #73 1eaa6c6). It also touched `core.store` (`ScopeTransaction`,
  ADR core-store/028), which PLAN-14 must take into account. TASK-15 was split into the new package
  `lane-route-pruning`, and the code lags documentation #76 (see the watch).
- Corpus reconciled by `cleanup` on 2026-10-15 at a8a4214, after #67, #68, #70, #72, #73 and #74:
  - PLAN-15, PLAN-23, PLAN-28 and PLAN-34 corrected to their package files. PLAN-23 and PLAN-28 no longer depend
    on PLAN-12, and PLAN-34 now also depends on `corpus-sources`.
  - PLAN-33 `session-as-worker` is `retired` (#72 deleted the package).
  - The 19 parked specs were regenerated from their package files, so their deliverables no longer carry the
    "specification first" task. Their Expected Surface is still the old `Where` inference: re-derive it before
    an emit.
  - New rows:
    - PLAN-64 `removed-concepts` (staged, ready). It removes PLAN-12's session log and session generations,
      `FencedSessionGenerationsSweep`, `JobRefusal.operatorCommand` and the scenario directive
      `expire_question`. It overlaps PLAN-14, 15, 20, 23 and 28 in `core.store`, `core.queue`, `runtime.start`
      and `runtime.job`.
    - PLAN-65 `lane-route-pruning` (staged, ready). Its § Discussion is decided with the operator before any
      code.
    - PLAN-66 `worker-activity` (staged).
    - PLAN-67 `process-and-overview` and PLAN-68 `chat-plan-revision` (both parked).
  - `corpus-sources` has no row: pm-mcp-core #74 (a8a4214) did it outside this queue.
- PLAN-19 — still parked after the cleanup: the index row still names `pm-mcp-clients` / `profile-documents`
  (`open` there on 2026-10-15).
- Ready by the index on 2026-10-15: PLAN-14, PLAN-15, PLAN-20, PLAN-23, PLAN-64 and PLAN-65 (plus PLAN-40, which
  has no package file). Of the code plans, only PLAN-65 is disjoint from the others; the rest share `core.store`.
  Recommended order: PLAN-64 first, because it deletes code that PLAN-23 and PLAN-28 would otherwise read, and
  shrinks `core.store` before PLAN-14 and PLAN-15 extend it.
- PLAN-07 — unblocked on 2026-10-10: its only dependency PLAN-02 shipped.
- PLAN-15 — PLAN-03 shipped; still waits on PLAN-06 and PLAN-11.
- PLAN-38 — shipped as #24 on 2026-10-10; WS-09 closes with it.
- PLAN-36 — serves Milestone 6; ready now and disjoint by package from every other plan, so it
  is a good filler for a free slot. It sits at the queue end because a row's order is fixed when
  it is staged; ask for it by id when a slot is free.
- PLAN-39, PLAN-40 — WS-10 module documentation backfill, staged 2026-10-11; both wait for PLAN-08,
  PLAN-10, PLAN-11, PLAN-36 and are disjoint from each other.
- PLAN-42, PLAN-43 — parked: added to the index by #35 and #36 (2026-10-12), `blocked` on use cases
  in `plan-marshall-documentation` (whose #38 and #39 merged the same day — re-check the index).
  They rework parts of PLAN-13, PLAN-14, PLAN-17, PLAN-24, PLAN-25 and PLAN-41's Rules 7 and 10.
- PLAN-45 — parked: added by #37 (2026-10-12), `blocked` on UC-PRJ-1 and PLAN-42.
- PLAN-44 — shipped as #38, gaps materially open; PLAN-54 completed the package (#48, minor gaps). PLAN-17 is unblocked; PLAN-56 closed the residuals (#66, 2026-10-14), except the Rule 3 seam waiting on documentation issue #71.
- PLAN-55 — parked: `chat`, added by #46 (2026-10-13), blocked; PLAN-51, PLAN-52 and PLAN-53 now also depend on it.
- PLAN-46 to PLAN-53 — parked: blocked packages added by #39 to #42 (2026-10-12), specs generated from their package files; re-ground before emitting.
- PLAN-37 — parked: `blocked` in the index on the operator's tool decision.
- Corpus reconciled by `cleanup` on 2026-10-16 at 238c823 (documentation 6881b7e), after documentation #81 (the specifications
  moved here) and pm-mcp-core #79 and #80:
  - The specifications themselves did not change after the work packages were aligned (#77, 13:20Z); documentation #81 is the
    counterpart of #77. What had drifted is the snapshot each spec carries of the repository, not the specification.
  - Re-grounding: 278 verdicts stamped at 238c823 for every claim of the 47 live specs (167 corroborated, 47 unverifiable,
    64 contradicted and re-scoped in place), 0 blocking, none stale for a live spec. The 95 stale rows left belong to shipped
    and retired specs, which are frozen. Corrections made: the log-holder counts (3, 54, 3 became 5, 60, 5; 16 specs), the
    index-row snapshots of dependencies that are done and dropped or were added since (28 specs), the `core.store`/`runtime.start`
    listings, `core.role` no longer greenfield (PLAN-26, PLAN-34: the `corpus` subpackage of #74 and #80), PLAN-35 and PLAN-37
    add no log record (test scope), PLAN-50 named `core/profile` for the recipe descriptor (now `core/recipe`), PLAN-40 counted
    ten developer files (now fifteen).
  - Expected Surface completed in 20 specs (`claimed_count` 942 to 976, verified by membership of the added paths in `claimed[]`).
    The new overlaps that matter for `next`: PLAN-69, 70, 71, 72, 65, 43 and 62 share `core.store`, `workflow.ast` and
    `workflow.plan` (up to 18 shared files per pair) and are sequenced, not paired.
  - PLAN-23 was the one live spec never regenerated after #77: its body still described the documentation pull request, the row
    set to `done`, `doc/LogMessages.adoc` in the documentation repository and the dependencies PLAN-06 and PLAN-13. Regenerated.
  - PLAN-40 — parked (was staged): it has no package file and no index row, and never had one, so the index, the only place that
    states a package's status, says nothing about it. The work is real (15 developer files exist, 25 packages with production code
    have none, none of the seven guides of the documentation repository has moved), so it is not retired. It returns to `staged`
    when a `docs(plans)` change adds `doc/plans/module-developer-docs.adoc` and its row.
  - Ready by the index: PLAN-14, 15, 20, 23, 64, 65, 69, 70, 72 and 74 (all `open`, depending on nothing). Recommended first:
    PLAN-64, as before.
- Distribution of the plans, 2026-10-17 (operator: 8 to 12 deliverables per plan are authorized; the distribution must let at
  least three plans run in parallel). Measured on 48 live plans / 318 deliverables: 8 plans have 3 or fewer deliverables, 24 have 4
  to 7, 13 are in the band, 2 are above it (PLAN-48: 21, PLAN-51: 14). The index is 15 waves deep and hub-and-spoke (PLAN-42 is
  waited on by 11 plans, PLAN-28 by 8, PLAN-26 by 7), and 28 of 47 specs declared `core/store/` as a directory, which made every
  plan one conflict component. Merging siblings that do not conflict, or merging along a dependency edge, does not help.
  - PLAN-75 `work-package-redistribution` (WS-10, staged, docs only): merges `project-machine-store` into
    `operator-and-question-stores`, `lane-route-pruning` into `plan-instance-alignment`, `stalled-scope-detection` into
    `removed-concepts`, `epic-findings-review` into `epic-decomposition`, and splits `start-from-clients` (21: 9 + 12) and
    `plan-and-epic-control` (14: 7 + 7). It runs first and alone among the plans of the ten changed packages; PLAN-14, 15, 23, 64,
    65, 70, 48, 51, 52 and 53 name it in their _Depends on_. Afterwards the orchestrator regenerates the merged specs (surface
    = union), stages the two new rows and supersedes the four absorbed ones. It shipped as #85 on 2026-10-17 (see "Landing of
    PLAN-75" below); the regeneration is under way, one merged package at a time.
  - Class-level Expected Surface for the ten ready plans (drafted by read-only leaves from the code at 238c823, new file names
    inferred, so every entry is a HYPOTHESIS unless the file exists and is certain). Result: 23 triples and 4 quadruples of the
    ready plans share no code file (before: one triple, 15/65/74). Real code collisions that remain: the plan-instance cluster
    (65/69/70 share `PlanInstanceComposer`, `InstancePruner` and fixtures; 65/72 and 69/72 share `InvariantId`, `HeaderSchemaCheckTest`,
    `WorkflowStatus`), PLAN-64 with 14/20/23, PLAN-20 with PLAN-74 (`RecoveryStep`, `StartupSequence`). PLAN-69 is a repo-wide
    refactor of `pm-workflow` (85 files, six fixture folders edited in full; its surface had omitted `plan`, `diagram`, `scenario`):
    run it alone and early, or last in its cluster.
  - A class-level surface cannot be drafted for the later plans before the earlier ones exist; the plans after wave 1 keep their
    directory-level surface until they are refined. Rule from now on: before a plan is emitted by `next`, its Expected Surface is
    sharpened to files from the code at that HEAD.
- Landing of PLAN-72, 2026-10-17 (`landings/PLAN-72.md`):
  - PLAN-72 `spend-removal` — shipped as pm-mcp-core #83 (acb5bba, merge queue). All six bullets delivered; the two items in
    `plan-marshall-documentation` (roadmap check-off, `token-usage` "remaining" statement) are owed to the milestone exit. It declared
    29 surface entries and touched 18 files; three were not declared (`CompositionRulesTest`, `PlanInstanceComposerTest`,
    `doc/plans/token-usage.adoc`). No collision with PLAN-74 or PLAN-75 (both rebased on acb5bba, 0 behind). PLAN-61's deliverable 9
    follows the reworded package file ("was removed by `spend-removal`").
  - After #83 only PLAN-20 and PLAN-69 have every dependency met; every other staged plan waits for PLAN-75, PLAN-19 or a staged plan.
    Emitted together on the operator's answers to two overlap prompts (PLAN-20 with PLAN-74: 3 declared files, 1 real; PLAN-20 with
    PLAN-69: the `core-store` ADR directory and guide). PLAN-69 starts now because the plan-instance cluster (65, 70, 71) cannot
    start before PLAN-75 lands anyway; while it runs, plans that edit `pm-workflow` wait.
  - PLAN-75's branch (11 commits, 20:03) names its two splits `start-source-intake` and `epic-resource-and-release`, not the
    `start-entry-and-screening` and `epic-actions` of the spec, and edits `CompositionEvent.java` (Javadoc) beside the package files.
    Reconcile both at its landing; the specs of the two new packages take the names the branch used.
- Landing of PLAN-69, 2026-10-18 (`landings/PLAN-69.md`):
  - PLAN-69 `unit-frontmatter-and-types` — shipped as pm-mcp-core #91 (`21872fb`, merge queue), 10 of 10 plan deliverables (7 met, 3 with
    residuals), 266 files, 10.5M tokens in 13 h 38 min (the largest run of the epic). It declared 95 surface entries; 13 touched files were
    not declared (`UnitGraph`, `AstVocabularyTest`, `PlanInstanceStoreTest`, `VerifyFixtures`, `pm-workflow/README.adoc`, two developer
    guides, three package files, three shared documents). Declared and untouched: `WorkflowLexer` and its test, `engine/package-info.java`,
    `specification/identifiers/02`.
  - Unblocked by #91: the plan-instance cluster (PLAN-70, now 11 deliverables; PLAN-71 also waits for `role-sets-and-model-caller`), and
    PLAN-43, 62 (both name `unit-frontmatter-and-types`).
  - The ledger land of 2026-10-17/18 (pm-mcp-core #89, `683707e`) went through the merge queue after the operator removed the
    documentation link check from this repository (#88); the check now runs in the documentation repository over both trees.
  - Lessons `plan-69-…-001` to `-007` (outline and review findings about `pm-workflow`) are folded as two Standing Constraints into the
    staged and parked specs (a checklist for `pm-workflow` code in 28 specs; "specifications can be test input" in 39); `-008` to `-018`
    concern `plan-marshall:*` components and went to plan-marshall's `lessons-routing` inbox (`pm-mcp-core-m2-m4-303` to `-313`).
- Landing of PLAN-74, 2026-10-18 (`landings/PLAN-74.md`):
  - PLAN-74 `credential-store-choice` — shipped as pm-mcp-core #90 (`44f904e`, merge queue), 10 of 10 plan deliverables (8 met, 2 with
    residuals), 67 files, 15.2M tokens in 19 h 47 min (the largest run of the epic). It declared 37 surface entries; 28 touched files were
    not declared; the declared `RecoveryStep`/`StartupSequence` overlap with PLAN-20 never materialised. Four code-review rounds each found
    another unguarded transition of the move state machine and an external bot found a lost-data defect they had passed.
  - **Breaks `plan-marshall-mcp` (verified):** `pm-mcp-server/.../SecretStoreProducer.java` on `origin/main` still imports the deleted
    `de.planmarshall.runtime.credentials.SecretStoreSelector` and calls `SecretStoreSelector.select()`; the credential-store aspect must
    call `CredentialStoreChoice.record(LockManager, user, store)`. This is deliverable 7 of the PLAN-74 spec ("follows in a pull request
    there") and nothing in `plan-marshall-mcp` plans it yet (see the Open Defect). The Keychain and Secret Service paths are unproven on
    the Linux host.
  - The four advisories the plan recorded and did not fix are staged as PLAN-82 `credential-store-move-advisories`.
  - Lessons `-001` to `-008` and `-010` to `-018` (pm-runtime) are folded as one checklist for durable multi-step protocols into the
    Standing Constraints of the 29 staged and parked specs that declare `pm-runtime` or `core.store` code; `-009` and `-019` to `-035`
    went to plan-marshall's `lessons-routing` inbox (`pm-mcp-core-m2-m4-314` to `-331`).
- Cleanup of 2026-10-18 (after PLAN-14, 20, 69, 74): 85 rows and 85 specs reconcile both ways; the verify-first clause of the 40 staged and
  parked specs that have an index row was re-stamped against `origin/main` (`3323ce7`) and the one open PR (#71, a dependency bump);
  PLAN-40 (no index row, known) and the five follow-up plans without a package (PLAN-78, 81, 82, 84, 85) keep their earlier stamps.
  `corpus verdicts`: 569 claims, 0 blocking, 404 stale (reported, never promoted). `corpus surfaces`: 85 declarative, 0 indeterminate.
  `corpus cross-check`: no live plan to compare against. `compact`: both invariants ok. Restart verdict `not_ready` on one signal only: the
  plugin registry pins 0.1.1932/0.1.1955 while the executor is 0.1.1953 (no plan is running any more). The claims about code were not re-read.
- Landing of PLAN-14, 2026-10-18 (`landings/PLAN-14.md`):
  - PLAN-14 `operator-and-question-stores` (with the former `project-machine-store`) — shipped as pm-mcp-core #93 (`3323ce7`, merge queue), 8
    of 8 plan deliverables (7 met, 1 with a non-consequential residual: the acceptance criterion `BROKEN LINKS 0` was superseded by the
    removal of the link check, #88), 62 files, 7.8M tokens in 17 h 36 min. First plan emitted from a spec the orchestrator regenerated after a
    merge of two packages, and the best surface calibration of the epic: 60 declared, 62 touched, 7 undeclared, 12 declared untouched. A
    file-level surface regenerated from the package file and the code is the form to keep.
  - ADR numbers: the plan renumbered its ADRs twice (final 032-039; `main` owns 029-031) because other plans took numbers in the same
    `core-store` directory; git reports no conflict for different slugs and the branch-cleanup gate scans only `doc/adr`. The rule is
    folded into the Standing Constraints of the 42 staged and parked specs that declare an ADR directory.
  - Unblocked by #93: PLAN-73 `question-continuation` (it names `operator-and-question-stores`); the former PLAN-15 stays `superseded`.
  - Advisories the plan recorded and did not fix are staged as PLAN-85 `store-records-advisories` (repeated waiver id, duplicate or
    double-pending `waiver_requests`, `QuestionLog.revise` accepting `decision` on a non-first line, a part asked into an existing set,
    `ProjectResolver` hiding a root conflict, untested guards, placeholder ADR sections, Traceability entries for PM-WF-12 and PM-SEC-3).
  - Lessons `-001` to `-014` concern `plan-marshall:*` components (two with a local half, handled) and went to plan-marshall's
    `lessons-routing` inbox (`pm-mcp-core-m2-m4-344` to `-357`).
  - Registered outside the orchestrator again: pm-mcp-core #98 (`3b53359`, mutation step, knowledge search, skill residency, scope-held Q-Gate,
    git identities; 9 specification parts, no package file amended) — added to the scope of PLAN-78 (still `staged`). Its § Git Identities
    now specifies `PM-WATCH-GEN-59`, so Deliverable 2 of PLAN-78 has a specification to point at.
- Landing of PLAN-20, 2026-10-18 (`landings/PLAN-20.md`):
  - PLAN-20 `job-runner` — shipped as pm-mcp-core #92 (`2ecea3f`, merge queue), 11 of 11 plan deliverables (7 met, 4 with residuals), 86
    files, 20.5M tokens in 21 h 16 min (the largest run of the epic; 65% in finalize, 37 finalize dispatches; the execute accumulator
    carries about 393k tokens of metric values recorded by mistake). The merge was on the operator's word with the review budgets spent and
    `automatic-review` closed as an operator override; the final head `cb39204` was not reviewed by the steps whose `done` stands at
    `b494a52`.
  - **Surface calibration, the worst of the epic:** 54 declared, 86 touched, 43 undeclared; the declared class names (`JobRuntime`,
    `ProcessTermination`, `JobRecovery`, `RecoveryStep`, `StartupSequence` and their tests) were guesses and the design took others
    (`SubprocessJobRunner`, `KillBinding`, `ProcessGroupSignals`, `TerminationProtocol`, `JobRecoveryStep`). A class-level surface drafted
    from names, not from the code, is wrong in both directions: weigh a `HYPOTHESIS` surface of that kind as directory-level at the next
    pairing decision.
  - **Not delivered from pm-mcp-core #84:** termination step 3a (descendants of a worker or probe that left the process group, launcher
    staying as the job's process) and the refusal reason `harness_update_unverified` (verified absent on `main`; `TerminationProtocol`
    handles descendants that remain in the group). Step 3a is staged as a deliverable of PLAN-84 `job-runner-residuals`; the refusal
    reason is Deliverable 4 of PLAN-78. Whether the heads-up reached the plan is unknown.
  - `ProjectRecovery` got an additive overload (`withEnrolment`), so the assembly in `plan-marshall-mcp` does not break from #92. The native
    build of `pm-mcpd` against the SNAPSHOT has not run; it is the only check of the `kill` downcall descriptor (Watch).
  - Staged from it: PLAN-84 `job-runner-residuals` (the open items of note 5 of the PR description, step 3a, the native build).
  - Lessons `-001` to `-012` went to plan-marshall's `lessons-routing` inbox (`pm-mcp-core-m2-m4-332` to `-343`); `-013` and `-014`
    (facts about `pm-runtime`, the Sonar suppression preference) are folded as one constraint into the 23 staged and parked specs that
    declare `pm-runtime` code.
- Registered outside the orchestrator, 2026-10-18: pm-mcp-core #97 (`5796aa9`, engine specifications: outline record, builder check, review
  results, escalation, capability specifications, measurement; 22 files) registered the package `service-unit-restore` (open, depends on
  nothing, Milestone 10); staged as PLAN-83. #97 also changed specification parts without amending a package file: added to the scope of
  PLAN-78 (Deliverable 11). PLAN-78 itself is still `staged` (the operator has not confirmed a start).
- Landing of PLAN-75, 2026-10-17 (`landings/PLAN-75.md`):
  - PLAN-75 `work-package-redistribution` — shipped as pm-mcp-core #85 (`c281f6c`). All nine deliverables corroborated from the merged
    tree: 9 + 12 + 7 + 7 + 6 + 11 + 8 + 12 = 72 bullets, the README holds no deleted name. The two new packages are named
    `start-source-intake` (12 bullets, from `start-from-clients`) and `epic-resource-and-release` (7, from `plan-and-epic-control`), the
    names the branch used (refine decision), not `start-entry-and-screening` and `epic-actions`. The plan's sibling PR
    plan-marshall-documentation #85 re-pointed `roadmap.adoc:233`; the owed corrections in `pm-mcp-clients` and `plan-marshall-mcp` (the
    packages they name were merged or renamed) are not made: they are listed in the PR body and are the operator's.
  - Index after #85: `operator-and-question-stores` (6 bullets), `removed-concepts` (8), `plan-instance-alignment` (11) and
    `unit-frontmatter-and-types` are `open` and depend on nothing; `epic-decomposition` (12) waits for `chat`, `operator-wait-and-machine-scope`
    and `epic-resource-and-release`; `start-from-clients` (9) waits for `start-source-intake` among others; `epic-resource-and-release` waits
    for `plan-and-epic-control`. The four absorbed rows are `superseded`: PLAN-15 by PLAN-14 (spec merged), PLAN-23 by PLAN-64, PLAN-65 by
    PLAN-70, PLAN-52 by PLAN-53. The specs were merged and split the same day and `cleanup` ran (see "Regeneration and cleanup after
    PLAN-75" below); the two new rows are PLAN-76 `start-source-intake` and PLAN-77 `epic-resource-and-release`.
  - Emit: PLAN-14 with PLAN-15 merged in (six deliverables, 60 declared surface entries, declarative). Its spec was regenerated from the package
    file at `c281f6c` and all ten claims re-grounded (`corpus verdicts`: 0 blocking). Operator answered the overlap prompt (5 files shared with
    PLAN-20 and 6 with PLAN-69, README row included; PLAN-74 the README row only): emit. PLAN-64 (16 files shared with PLAN-20, 6 with PLAN-74)
    was the other candidate; PLAN-70 shares 15 files with PLAN-69. Both wait.
  - Candidate lessons `work-package-redistribution-001` to `-008`: seven concern `plan-marshall:*` components and went to plan-marshall's
    `lessons-routing` inbox (`pm-mcp-core-m2-m4-296` to `-302`). `-006` (a plan that deletes a package file linked from the documentation
    repository fails the `Links` check until the sibling fix merges) is folded into PLAN-70, the one plan whose package file
    `roadmap.adoc:233` still links.
- Regeneration and cleanup after PLAN-75, 2026-10-17 (operator: do all splits and merges, then cleanup, then land):
  - Merged specs, regenerated from the package files at `c281f6c` with every claim re-grounded and stamped `pm-mcp-core-m2-m4/analyze`:
    PLAN-14 (+15: 6 deliverables), PLAN-64 (+23: 8), PLAN-70 (+65: 11; it still waits for PLAN-69, the plan-instance cluster), PLAN-53
    (+52: 12). Rows 15, 23, 52, 65 are `superseded`; their specs stay in `plans/`. Split: PLAN-48 `start-from-clients` (9) and the new
    PLAN-76 `start-source-intake` (12, `parked`); PLAN-51 `plan-and-epic-control` (7) and the new PLAN-77 `epic-resource-and-release` (7,
    `parked`). The surfaces of the splits are partitioned by the `Where` lines of the package files (HYPOTHESIS; the `pm-runtime`
    package for the endpoints is a guess: `web`). Dependents re-pointed to the new rows: PLAN-47, 55, 57, 58, 62, 63.
  - PLAN-78 `spec-obligation-owners` staged (the amendment of the package files for the obligations of pm-mcp-core #84; its Standing
    Constraints order the PR with the label `skip-bot-review`).
  - Re-grounding scope: the verify-first clause of every staged or parked spec was re-checked mechanically against the index and the three
    open PRs (32 stamped); the index claims of six dependents were rewritten from the rows. The claims about code were NOT re-read: PLAN-72,
    #84 and #85 changed no code the specs cite beyond `WorkflowStatus*` (named in PLAN-14) and the specification parts, and
    `corpus verdicts` reports staleness without promoting it (313 stale of 544, 0 blocking). The executing plan re-reads at its branch point.
  - `corpus surfaces`: 78 specs, 0 indeterminate. `corpus cross-check`: 78 comparable; the only source-origin matches are the four live
    plans. `compact`: both invariants ok; settled-narrative relocation deferred (it needs the operator's confirmation on a first run).
    Restart verdict `not_ready`: four plans running, and the registry pins 0.1.1932/0.1.1955 against executor 0.1.1953.
  - Known gaps at that time: `PLAN-40 module-developer-docs` is parked with no index row (the package row was left behind by its own
    plan, found by PLAN-75). `corpus-overlay-extensions` and `corpus-group-contamination` had no queue row: both were delivered outside the
    orchestrator (#80, #86) and are recorded as PLAN-79 and PLAN-80, `shipped`, with retroactive specs and landing notes (2026-10-18).

- Corpus reconciled by `cleanup` on 2026-10-21 at 99971f3, after #122, #125, #127 and #128 (documentation 963e879):
  - Unparked to `staged` (18, on the index: each row is `open` and every dependency is shipped or staged): PLAN-19 `profile-loader`,
    42, 43, 45, 46, 47, 48, 50, 51, 53, 55, 57, 58, 61, 62, 67, 76, 77. PLAN-19 was the root of the chain: it waited on
    `pm-mcp-clients` / `profile-documents`, which is `done` there (as are `exec-read-denied-paths` and `installation-layout`); the index
    here still names all three and links to files that moved to `doc/plans/implemented/`. Staged as **PLAN-93** `index-done-dependencies`
    (runbook lane, after PLAN-89's pull request #126, which edits the same file). A staged plan is not an emittable one: PLAN-19 gates
    PLAN-25 and PLAN-26 and through them most of WS-04 and WS-05, so nothing below it is ready until PLAN-19 has shipped.
  - Still parked (5, with the reason in each spec): PLAN-49 `forgejo-provider`, PLAN-59 `runtime-control`, PLAN-63 `cleanup`, PLAN-68
    `chat-plan-revision` (all `blocked` in the index, each on a dependency that has no work package or on a blocked package) and PLAN-60
    `runtime-metrics` (`blocked` only because its row still lists the metrics-library choice that #101 decided; PLAN-89's pull request #126
    removes it: unpark at the next cleanup after PLAN-89 has landed). PLAN-40 `module-developer-docs` stays parked until PLAN-89 adds its
    package file and row.
  - The 12 specs with a contradicted claim 0 (PLAN-19, 27, 28, 29, 30, 34, 35, 43, 53, 58, 66, 68) were regenerated from their package
    files, as were 15 more that had drifted (PLAN-31, 45, 46, 47, 49, 50, 51, 55, 57, 59, 60, 62, 63, 76, 77): 27 specs in all with PLAN-30, their
    Deliverables, Done when and Trace now equal the package files word for word (a regeneration diff reads `current` for all 37 live
    specs with a package file, the running plans excluded). PLAN-27's Expected Surface gained the git provider (deliverable 8 names the git contract) and
    a specification part.
  - **PLAN-53 split.** `epic-decomposition` lost the review of an epic's findings and watches to the new package `epic-review` (#111):
    PLAN-53 keeps 7 deliverables, and the review half is the new **PLAN-92** `epic-review` (5 deliverables, staged, WS-03, same dependencies).
    The two share four specification parts and are sequenced, not paired. The spec of PLAN-52 stays superseded.
  - Claims: 625 verdicts, 0 blocking (12 before). 258 verdicts were re-stamped at 99971f3 for the live specs; the stale rows that remain
    belong to shipped, superseded and retired specs (frozen) and to PLAN-84, 85 and 89, which are running and excluded. Corrections made in
    place: the log holders now count 5, 78 and 5 records; `core/queue/` no longer exists (PLAN-64), so PLAN-28 creates the package
    (contradicted, rescoped); PLAN-40's counts of developer files (21, and 16 packages without) and its taken-over guides.
  - Later the same day, at the operator's word: **PLAN-94** `sonar-main-backlog` staged (the 80 SonarCloud issues of `main` no plan owned), and seven closed entries of Open Defects and Watches relocated to `settled.md` (the specs behind #111, PR #95, the #125 rewrite, the PLAN-64 landing, the cross-repository blockers of PLAN-19 and PLAN-27, the differential harness, the analysis of documentation #82 to #84); the annotation zone was not touched.
  - Analyzed 2026-10-21 (landed #126 `c45a968` PLAN-89 and #131 `3d4baf7` PLAN-91, both reconciled in `landings/`): the row `runtime-metrics` is `open` and the row `module-developer-docs` exists,
    so PLAN-60 and PLAN-40 were unparked to `staged` and re-grounded (PLAN-46 was regenerated from `machine-onboarding-unit.adoc`, which #131 edited); PLAN-93 no longer waits for #126.
    Only PLAN-49, 59, 63 and 68 stay parked (blocked in the index). Twelve candidate lessons went to plan-marshall's `lessons-routing` inbox (`pm-mcp-core-m2-m4-391` to `-402`).
  - Collision recorded (Step 4 item 5): #126 touched `runtime-job.adoc`, ADR `runtime-job/001` and `ArgumentSchemaValidatorTest`, which PLAN-84 (running, a runbook plan the live-plan gate cannot see: it has no plan directory) declares. The declaration was right; PLAN-84 must re-read the three files after #126 (the 256 code-point ceiling) before it edits them. Emitted 2026-10-21 into the two free slots: PLAN-19 (runbook, the root of the chain) and PLAN-93 (runbook); held: PLAN-87 (multi-file overlap with PLAN-84 and PLAN-85: three test files and two core-store documents, an operator prompt), PLAN-94 (after PLAN-84), PLAN-40 (its developer-file directories overlap every running plan).
  - `corpus surfaces`: 92 specs, 0 indeterminate. `corpus cross-check`: the only source-origin matches are the running plans' own specs.
    `compact`: both invariants ok, settled-narrative relocation deferred again (the operator's confirmation is owed). Restart verdict
    `not_ready`: four plans running, and the registry pins 0.1.1977 against executor 0.1.1961.
  - Analyzed 2026-10-22 (landed #136 `7756dfd` PLAN-93, reconciled in `landings/PLAN-93.md`): three cells of `doc/plans/README.adoc` lost their done `pm-mcp-clients` dependencies; `profile-loader` reads `nothing`. D2 produced no diff (the one other cross-repository cell, `question-loop-verification`, is open in `plan-marshall-mcp`). The plan reported Done-when clause 3 (`trace.py`) as not met because it skipped the run; the orchestrator ran it: 0 broken links against documentation `origin/main` (`b8c61b3`), 1 against the stale local checkout (the `roadmap.adoc:242` link the documentation repository has since fixed). Three running plans now: PLAN-84 (#135 open), PLAN-85 (#129 open), PLAN-19.
  - Outside the ledger, 2026-10-21 18:22Z: the operator merged three specification-only pull requests, #132 `672d746`, #133 `ab1826f`, #134 `50322a7` (23 files: the judgment endpoint, judgment votes, `stop_reason`, `own_calls`, the change map). They are in the base of PLAN-19, 84 and 85. Twenty-nine specs name the changed files by path; no work package owns the new content (Open Defects).

## Decisions

- 2026-10-09 — One orchestrator epic for the whole repository, not one per milestone or per
  module. Alternatives: an epic per milestone; an epic per module. Rationale: the dependencies
  cross every such cut (Milestone 2's `stalled-scope-detection` needs Milestone 3's
  `store-substrate` and `workflow-engine`; `workflow-engine` in `pm-workflow` needs
  `store-substrate` in `pm-core`), and an epic orders only its own queue.
- 2026-10-09 — `parallelization_scope` set to 3 by the operator on first init (project default
  suggestion was 1).
- 2026-10-09 — The operator switched `orchestrator.use_worktree` on right after init. The ledger
  lives in the shared ledger worktree and reaches `main` only through `land`; the ignore rule
  that makes it trackable and the knob itself are PR #16.

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

- 2026-10-10 — Operator decision: `parallelization_scope` raised from 3 to 4, because PLAN-05
  `http-baseline` is disjoint from the running PLAN-04, PLAN-06 and PLAN-07 (only
  `doc/plans/README.adoc` shared). PLAN-36 is also disjoint and is the next filler.

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

- 2026-10-13 — PLAN-13 `workflow-engine` landed as #45 (5a5db68), Claude Code; landing record
  `landings/PLAN-13.md`. Clean: CI green, `/review` after every round with no override, pre-commit
  on the final head, documentation PR #49 merged, and the first plan to follow the WS-10 module
  documentation rule (developer guides plus 9 ADRs). Inbox drain of 19 messages: the landing
  reconciled; 1 lesson promoted (2026-10-13-06-001, specification drift during a run), 1 folded into
  2026-10-10-14-001, 16 routed as `pm-mcp-core-m2-m4-153.md` to `-168.md`. #46 added the `blocked`
  package `chat`, staged as parked PLAN-55.
- 2026-10-13 — Operator rule: the harness is Claude Code unless the operator states otherwise.

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

- 2026-10-13 — Operator rule: every landing analysis verifies that all PRs related to the plan,
  especially its `plan-marshall-documentation` PR, are **merged**, and that their content matches the
  merged code. Each landing record lists them by number with state and merge time. Applied at once to
  all shipped plans: the documentation PRs of PLAN-01, 02, 03, 04, 06, 07, 08, 10, 11, 13, 16, 18, 22
  and 36 (doc #19, 20, 24, 26, 27, 28, 30, 31, 33, 34, 46, 49, 52, 53) are all MERGED; no PR is open
  in `plan-marshall-documentation`. Gaps found: doc #46 (PLAN-18) merged with its single pre-review
  commit, so the mandatory `maxLength` of enrolled patterns is not in the specification; PLAN-41,
  PLAN-44 and PLAN-54 opened no documentation PR, so PM-WATCH-DSL-3/4/5/8/9/11/15/16/29/32 stay open on
  documentation main although the verifier implements them.

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

- 2026-10-13 — Operator decision: every versioned artifact stays at `format_version` 1 until the
  first release; a format change replaces version 1 in place. Existing bumps are an Open Defect to
  clean up at the end of this epic. Added as a Standing Constraint to every staged and parked spec;
  the running PLAN-17, PLAN-21 and PLAN-39 were told through their mailboxes.

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
- 2026-10-17 — Rule (operator): a lesson or finding about a component of another repository goes to that repository's
  `lessons-routing` inbox, and is then archived here. `manage-lessons add` refuses such a lesson in this store (`wrong_store`);
  the guard is never bypassed with `--allow-foreign-store`. For the `plan-marshall:*` components the inbox is
  `/home/oliver/git/plan-marshall/.plan/local/worktrees/_orchestrator/.plan/orchestrator/lessons-routing/inbox`; the message is
  written from that repository with `orchestrator inbox write --slug lessons-routing --sender-type orchestrator --sender-id
  pm-mcp-core-m2-m4 --kind candidate-lesson --payload-file {body}`, the body verbatim. First use: the nine candidate lessons of
  PLAN-72 (`spend-removal-001` to `-009`: provider URL error, finalize refire after a fix commit, outline criterion on the PR body,
  executor version skew, removal tests that pass on the old code, sweep criteria over resource trees, worktree-relative commands,
  task completed before its verification, persona skills against `CLAUDE.md`) are queued there as `pm-mcp-core-m2-m4-287` to
  `-295`, uncommitted in that ledger worktree until its `land`. Second use, 2026-10-17: seven of the eight candidate lessons of
  PLAN-75 (`pm-mcp-core-m2-m4-296` to `-302`).
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

## Open Defects

> ↪ Relocated to `settled.md` § "Defect: plan-marshall-mcp did not compile against the pm-runtime SNAPSHOT" — closed by plan-marshall-mcp #88 (8137c9e), verified 2026-10-19

> ↪ Relocated to `settled.md` § "Defect: staged specs behind doc/plans/" — reconciled by cleanup on 2026-10-15 at a8a4214

- **Format version bumped although nothing is released (cleanup at the end of the epic).**
  - `TransactionJournal.FORMAT_VERSION = 3` (`pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java`
    l.77, and its Javadoc l.48): raised 1→2 by PLAN-06 (#26, owed audit events) and 2→3 by PLAN-12 (#51,
    delete mode).
  - The bump is also recorded in `pm-core/doc/developer/core-store.adoc` (l.169, l.414) and ADR
    `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc`, plus any test fixture
    or test that pins 2 or 3.
  - Open `plan-marshall-documentation` #59 (PLAN-12) puts `"format_version": 3` into the store schema
    specification.

  **Staged on 2026-10-19 as PLAN-87 `journal-format-version-reset`.** The target: the journal at version 1 everywhere — code, tests, fixtures, guide, ADR (record the
  decision instead of the bump) and specification. Every other versioned artifact is at 1 (checked on
  2026-10-13 at bed24eb) — source: operator, 2026-10-13.

> ↪ Relocated to `settled.md` § "Defect: the enrolled-pattern length rule missing from the Job Runtime specification" — closed by #126 (c45a968); landings/PLAN-89.md

> ↪ Relocated to `settled.md` § "Defect: static verifier residuals after PLAN-54" — PLAN-56 shipped as #66 (7981d17)

- No consumer is wired to the confinement check of PLAN-03; `pm-provider-git` was put out of slice
  by operator decision and no staged plan owns the wiring. The specs not yet started now tell each
  package to adopt the check itself — source: landing PLAN-03.
> ↪ Relocated to `settled.md` § "Defect: shared log identifier blocks" — replaced by one holder per module (pm-mcp-core #64, 50827cf)

- The roadmap item behind PLAN-02 is only partly delivered and no staged plan owns the rest:
  validators for `request.md` and pull request bodies (PM-WF-6), the `OutlineValidator` rules that
  need the working tree, task derivation or build extensions, the registry checks that need the
  closed gate list or the keyword list, and the binding of free parameters — source: landing
  PLAN-02 (plan's own statement; not checked against the roadmap).
- `AsciiDocValidator` treats `!===` as the nested-table delimiter; CodeRabbit named `!==`. Neither
  was checked against the AsciiDoc documentation. If `!==` is valid, such a nested table is read
  as ordinary text — source: landing PLAN-02 and lesson 2026-10-10-07-010.
> ↪ Relocated to `settled.md` § "Defect: corpus-overlay-extensions outlived its implementation" — closed by pm-mcp-core #94 (ab308ad) on 2026-10-18; retroactive row PLAN-79
> ↪ Relocated to `settled.md` § "Defect: watch link of stalled-scope-detection.adoc" — the package file was merged into removed-concepts and deleted by PLAN-75 (#85)
> ↪ Relocated to `settled.md` § "Defect: no package owns the pm-runtime adapter for QuestionRecordReader" — closed by pm-mcp-core #116 (`c39ce3b`); retroactive row PLAN-88
> ↪ Relocated to `settled.md` § "Defect: Traceability cites two tests that do not prove the spend removal" — closed by #126 (c45a968); landings/PLAN-89.md
> ↪ Relocated to `settled.md` § "Defect: new specification obligations that no package file owns" — closed by pm-mcp-core #111 (72aec5c, PLAN-78), verified 2026-10-20
> ↪ Relocated to `settled.md` § "Defect: the specs of the plans of the packages amended by #111 were behind their package files" — regenerated by cleanup on 2026-10-21; PLAN-92 owns epic-review
- **The landing of PLAN-78 is incomplete: `total_tokens` is `unknown` (`inbox landing-check`, `rb-78-spec-obligation-owners-001.md`).** The opencode
  harness measured none and the runbook lane writes `unknown` rather than a guess. Every landing of the runbook lane on opencode will carry it
  (PLAN-81 and PLAN-88 next): fold the recurrence here and read tokens as unobserved. The same landing reports a build gate that was not run
  (see the watch on the executor) and a merge without a required review (see PLAN-78's landing report).
  **Recurred on 2026-10-20 (PLAN-88, `rb-88-question-record-reader-adapter-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same
  cause. Expect it from PLAN-81 too; treat it as a fixed property of the opencode runbook landing until the harness measures tokens.
  **Recurred on 2026-10-20 (PLAN-81, `rb-81-unit-frontmatter-hardening-001.md`):** `total_tokens=unknown`, same cause; all three opencode runbook
  landings carry it. Kept open as a standing property, not per landing.
  **Recurred on 2026-10-21 (PLAN-90, `rb-90-ported-tests-advisories-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same cause; the
  fourth opencode runbook landing.
  **Recurred on 2026-10-21 (PLAN-82, `rb-82-credential-store-move-advisories-001.md`):** `total_tokens=unknown`, same cause; the fifth.
  **Recurred on 2026-10-21 (PLAN-83, `rb-83-service-unit-restore-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same cause; the sixth.
  **Recurred on 2026-10-21 (PLAN-64, `rb-64-removed-concepts-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same cause; the seventh.
- **The port `QuestionRecordReader` cannot address a pending entry.** `ReadRequest(Optional<String> scopeId, String questionId)` names a plan, an
  epic or the workspace, nothing else, so a `question` state of a pending entry (an entity of the workspace scope) cannot be resolved through the
  adapter PLAN-88 shipped. Reported by the lane and corroborated against the record's two components (`pm-core` `core.spi`). No package owns it: a
  change of the port belongs to `pm-core`, its adapter to `pm-runtime`, the engine side to `question-continuation` (PLAN-73) — the operator
  decides the owner. Source: landing PLAN-88, corroborated 2026-10-20.
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


- **Nothing in production records `credentials.store`, so a fresh install refuses every credential (found by the follow-up of a separate session to the API change, plan-marshall-mcp #88 `8137c9e` and #90 `91b60d5`, merged 2026-10-18/19; corroborated on 2026-10-19 against the code and the merged PR).** The daemon serves only the store recorded in `credentials.store` of `machine-config.json`; before one is recorded every credential operation is refused and `CredentialsResource` answers `409 {"error":"onboarding_incomplete"}`. The only legitimate writer is the credential-store aspect of the machine onboarding (`store.choose` to `store.move`), through `CredentialStoreChoice.record(LockManager, user, Store)` of `pm-runtime`; no production class calls it (grep of `pm-mcp-server/src/main` at `91b60d5`). The aspect belongs to PLAN-46 `machine-onboarding-unit`, which is parked on PLAN-42 `operator-wait-and-machine-scope`, PLAN-61 `token-usage` and PLAN-70 `plan-instance-alignment` (running). The aspect is not separable from the machine scope, so PLAN-46 is on the critical path of every use of a credential; PLAN-74's deliverable 7 is the part already delivered (the producer reads the recorded choice). The operator verb is NOT missing and no plan was added for it (checked 2026-10-19 after pulling the clients): `pm-operator config credentials --store os|file` is implemented in `pm-mcp-clients` (`ConfigCommand.CredentialsCommand`), designed in its terminal-client design, and the specification names `pm-operator credentials migrate os|file` for the move. What is missing is the runtime side the verb talks to: it posts to `/api/v1/config/credentials`, which no repository serves and no specification defines (the credential-store aspect of PLAN-46 is that side). The clients' call path is theirs to check. PLAN-46's spec carries the finding; its dependency on `credential-store-choice` was dropped (shipped as #90).

- **`SchemaSubset.checkString` evaluates a pattern before it checks `maxLength` (CodeRabbit lead on #126, verified in the code at `3d4baf7`, 2026-10-21).**
  `pm-core/src/main/java/de/planmarshall/core/role/corpus/SchemaSubset.java` lines 139 to 153 compile and match the schema `pattern` against the whole
  value and test `maxLength` afterwards, so a long value reaches the backtracking engine first. It is the verification-corpus path (PM-WORK-6, `CorpusValidator`),
  not the enrolled-schema path PLAN-89 bounded; whether a corpus value or schema can be attacker-controlled was not assessed. The thread is unresolved on #126.
  A fix (check the length first) changes behaviour, so it is not a PLAN-94 item. No plan owns it: the operator decides — a one-deliverable plan, or a bullet of
  the plan that next edits `core.role.corpus` — source: landing PLAN-89, corroborated 2026-10-21.
- **Two landings of 2026-10-21 are incomplete (`inbox landing-check`).** PLAN-89 (`plans-index-and-traceability-corrections-006.md`): `missing_keys: [total_tokens]`
  and `harness`, `model`, `effort`, `plugin_version` `unknown`. PLAN-91 (`plan-91-plan-instance-advisories-008.md`): no `landing-facts` block at all, all nine required keys
  missing; the message is a narrative with a front-matter block. Both were reconciled from git, the pull requests and the code. The `/plan-marshall` lane on opencode
  wrote a free-form landing for PLAN-91; fold further occurrences here.

## Watches

- Owed to the milestone exit after PLAN-70 (#107, 2026-10-19) and PLAN-91 (#131, 2026-10-21: the concept and user documentation of the slice and the roadmap check-off), written in plan-marshall-documentation and not by any plan here: the roadmap check-off of the plan-instance item (`roadmap.adoc` line 233 still lists the prune reasons `plan_default` and `merge_by_operator`, the override event and `diff_summary` under "Remaining") and the documentation parts of PM-IMPL-5. The plan's statement that its change breaks `plan-marshall-mcp` was REFUTED on 2026-10-19 (`grep` finds no Java file there naming `CompositionRecord`, `CompiledInvariants`, `PlanFacts` or `RECIPE_ID_FACT` at `91b60d5`); it holds only for whoever first wires the composer into the assembly. Retire this watch when the roadmap line is updated.
> ↪ Relocated to `settled.md` § "Watch: documentation PR #95, the pin advance of the migration inventory" — retired 2026-10-21: merged as 963e879
- Filed 2026-10-21 in plan-marshall's `live-blockers` inbox (`pm-mcp-core-m2-m4-001` to `-004`, queued and uncommitted in that ledger worktree until its `land`): the executor-skew evidence of five runbook landings, the `total_tokens=unknown` recurrence (seven landings), the lane edit of the main checkout and the bot silence on a merge head (PLAN-81, the two unfiled lane-contract lessons), and the `ci` abstraction's lack of a repository selector.
- The Secret Service path (Linux) is unproven end to end (stated on plan-marshall-mcp #88 and #90; the Keychain path was measured on macOS). In this repository `SecretServiceIT` of `pm-runtime` is `@EnabledOnOs(LINUX)` and skips itself by `assumeTrue` when there is no session bus address or the default collection is locked, so a CI run proves nothing for it. Proving it takes a Linux desktop session with an unlocked default collection running `verify -Pintegration-tests -pl pm-runtime -am`; that is plan work or the operator's, not an orchestrator read. Retire this watch when a run on such a session shows the test executed and green.
- The native build of `pm-mcpd` in `plan-marshall-mcp` against the deployed SNAPSHOT has not run since #90 and #92. It is the only check of
  the `kill` downcall descriptor of PLAN-20 and of the Keychain and Secret Service paths of PLAN-74, and `SecretStoreProducer` there compiles
  again since plan-marshall-mcp #88 (8137c9e), but no native build has run. PLAN-84 asks to run it or to name the plan that does.

- Owed after PLAN-69 (#91, 2026-10-18), CORRECTED 2026-10-18 after the operator asked: (1) the landing message said the change is
  "breaking for the daemon assembly (`pm-mcp-server`)" and the first version of this entry repeated it as a fact. Checked against
  `plan-marshall-mcp` `origin/main`: it holds no `.pmwf` unit, no module depends on `pm-workflow` and no Java file imports
  `de.planmarshall.workflow`, so NOTHING breaks today. The statement holds only once the package `default-units-and-drift-tests`
  (open, not started; its package file in `plan-marshall-mcp` lists "the default workflow units as resources of the assembly") wires
  `pm-workflow` and adds the default units: those must then carry the frontmatter (`name`, `description`, `type`, entries, exits),
  `startState` is optional and `CompiledInvariants` takes a new required argument. The fix is one bullet in that package file (a
  `docs(plans)` change in `plan-marshall-mcp`); no issue was filed and none is needed before the package starts. (2) Two audit
  anti-pattern findings (info) stay `pending` because each needs a decision in the specification first (Unicode format characters in
  the frontmatter and a case-sensitive comparison of the reserved author; no size bound on the unit file, the frontmatter lines and
  the echoed text): staged as PLAN-81 `unit-frontmatter-hardening` — **both closed by pm-mcp-core #114 (`afc918b`, 2026-10-20)**: the frontmatter refuses
  Cf, Zl and Zp characters with code point and line, the reserved author is compared NFKC-normalised and case-folded, the unit file (1 MiB),
  the frontmatter (200 lines) and the echoed text (80 characters) are bounded; verified, `landings/PLAN-81.md`. (3) Deliverable 9 landed partly by design: the six
  specification files keep their anchors `#unit-frontmatter`, `#unit-types`, `#interface-rules` as short sections.
- The link check left this repository (#88): a plan that deletes a package file linked from `plan-marshall-documentation` (today only
  `roadmap.adoc:233` -> `plan-instance-alignment.adoc`, PLAN-70) now breaks the documentation repository's check after its merge, not its
  own pull request. The Standing Constraint of PLAN-70 still asks for the sibling fix during execute.

> ↪ Relocated to `settled.md` § "Watch: doc #56 of PLAN-21" — #56 merged 2026-10-14T06:51:30Z (42217c3); PLAN-21 complete
- A pm-runtime adapter for `core.spi.PendingEntryReader` over `PendingEntryStore` is owed (PLAN-21) — the daemon wiring.
> ↪ Relocated to `settled.md` § "Watch: the log-holder PRs" — #64 merged 06:08:33Z (50827cf, SNAPSHOT deployed), doc #68 merged 06:20:45Z (d9d3e83)
> ↪ Relocated to `settled.md` § "Watch: pm-mcp-server against the log-holder SNAPSHOT" — plan-marshall-mcp #76 merged 2026-10-14 (e34961b), assembly on `RuntimeLogMessages`
> ↪ Relocated to `settled.md` § "Watch: doc #57 of PLAN-17" — #57 merged 2026-10-13T20:44:58Z (fff6552); PLAN-17 complete
- plan-marshall-mcp still owes (after #76): from PLAN-17, `DefaultUnitInvariantTest` over the real default units and wiring the project-unit loader into plan start/resume (the `config_invalid` refusal); taking its 16 log records over from `RuntimeLogMessages` into a holder of its own; wiring a `PeriodicSweep` with `PendingEntryRecordsSweep` and `FencedSessionGenerationsSweep` once the daemon runs the periodic sweep (new behaviour — the assembly never used `SweepStep`). The sweep item is void: the package `removed-concepts` (PLAN-64) removes `FencedSessionGenerationsSweep`, and the age sweep of pending entries goes to `start-from-clients` (confirmed by cleanup against `doc/plans/removed-concepts.adoc` at a8a4214). Do not carry it to plan-marshall-mcp.

> ↪ Relocated to `settled.md` § "Watch: pm-mcp-server and SweepStep.notImplemented" — refuted by #76: no use site in the assembly
> ↪ Relocated to `settled.md` § "Watch: no production AuditSink or AuditChainVerification in pm-mcp-core" — PLAN-09 shipped `AuditLogSink` and `AuditVerification` (#69, 3236ed5)
- plan-marshall-mcp also owes (after PLAN-09, #69): wiring `AuditLogSink` (`new AuditHead(…)` → `new AuditLog(…)` → `new AuditLogSink(locks, log)`) and `AuditVerification` in place of `AuditChainVerification.NONE` and the refusing sink; creating `logs/` before the log is built; breaking the `FileLockManager` ↔ `AuditLogSink` constructor cycle; and the `pm-operator audit verify` command over `AuditVerification.full()` — source: #69 PR body and landing message, corroborated 2026-10-15.

> ↪ Relocated to `settled.md` § "Watch: PLAN-24 landing" — #67 (2ebec2a) and documentation #73 (1eaa6c6) merged; landing drained 2026-10-15
- **Plan instance code behind its specification (after PLAN-24).** The code still ships the prune reasons
  `recipe_override` and `auto_merge_off` (`PruneReason`, `CompositionCause`, `composition-rules.json`). Since
  documentation #76, the specification targets `plan_default` and `merge_by_operator`, with the plan axes, the
  plan's overrides and phase sub-workflows. `PM-IMPL-5` and the roadmap item (`[~]`) name the gap. By operator
  decision (2026-10-15) the alignment is follow-up work, now staged as PLAN-70 `plan-instance-alignment` (prune
  reasons, rules, axes, adjudication unit, `diff_summary`) and PLAN-71 `plan-instance-versions` (composition events,
  version switch, Rules 13 and 14, resources), beside PLAN-65 `lane-route-pruning`. The source is the PLAN-24 landing,
  checked against `origin/main` on 2026-10-15.
- Specification issue `plan-marshall-documentation` #71 ("Workflow DSL: define how a `depends_on_config` value activates a guard") is OPEN (filed by PLAN-56). Rule 3 configuration reachability stays a named seam (ADRs 004, 006) until it is answered; then stage a follow-up package that implements the specified activation.
- Verifier performance on the real merged plan unit (simple-cycle enumeration, 2^N configuration combinations) is untested; no default units live in this repository.

- No plan records its harness (high-priority finding `pm-mcp-core-m2-m4-132.md`). The operator's rule since 2026-10-13: assume Claude Code unless stated otherwise.
- **Non-Claude-Code runs (Antigravity, opencode) need the extended landing verification** (code-vs-package audit, build-log and
  gate check, review trail), because the plan's self-report was unreliable on its first run (seven
  high-priority findings at plan-marshall). Retire when an Antigravity landing passes it cleanly.
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
- **Eighteen SonarCloud issues on PR #122 are untriaged (observed through the SonarCloud API, 2026-10-21).** The gate is OK, the landing says "quality
  gate passed" and does not mention them. Twelve are in `StalledScopeDetectorTest` (S5778 x5, S7467 x3, S6213, S1612, S9358, S8786), two are CRITICAL
  `S1192` duplicate literals in `AuditEvent.java:305-306`, one is `S1172` (unused parameter `projectId`, `WrapperVerification.java:52`), one `S3400`
  (`StalledScopeDetector.java:146`), one each in `AuditCodecTest` (S6213) and `JobRefusalTest` (S1612). **Decision 2026-10-21 (operator): the fixes of
  all 29 open issues (#120: 1, #121: 10, #122: 18) are Deliverable 5 of PLAN-87**, not a plan of their own; the other watches on #120 and #121 below stay
  as the record of where they were found.
- **Owed outside PLAN-64 (plan-reported, corroborated by the Javadoc):** the `pm-mcp-server` assembly in `plan-marshall-mcp` registers `PendingEntryRecordsSweep`
  and wires `StalledScopeDetector` with an `OperatorPort`; `process-and-overview` serves the `needs_operator` summary; the removed public `JobRefusal` API
  is a change the clients' build meets. The roadmap `_Remaining_` update and the concept and user documentation belong to the milestone exit.
> ↪ Relocated to `settled.md` § "Watch: #125 reached main without a rewrite the pre-commit gate applies" — carried by #127 (c7780a0)
- **Ten SonarCloud issues on PR #121 are untriaged (unverified lead).** The PR's Sonar comment says quality gate passed and 10 new issues; the lane
  did not read them and the orchestrator did not open them. Check the PR's Sonar page before the next change to the credentials package.
> ↪ Relocated to `settled.md` § "Watch: PLAN-64 met #121 and #125 and landed" — landed 2026-10-21 as #122 (c6da0e2); landings/PLAN-64.md
- **Owed outside PLAN-83 (plan-reported, accepted by the operator):** `ServiceUnitInstaller.restore()` is implemented and tested, but no production
  start calls it; the `pm-mcp-server` assembly in `plan-marshall-mcp` must run it first (like `CredentialStoreChoice.record` below). Until then the
  macOS recovery after an interrupted reinstall does not exist end to end. The concept and user documentation and the roadmap check-off belong to the
  milestone exit; the rest of the upgrade rollback (staged installation directory, the switch) is not implemented, so the Upgrade section stays.
- **Install, restore and uninstall share the kept pair with no cross-process owner (plan-reported, corroborated in the code).** No production caller
  exists yet. CodeRabbit raised it; the lane answered on the PR. The serialization belongs to the change that wires `pm-operator runtime install|uninstall`
  and the start.
- **A failed drop after a successful load can restore an OLDER unit later (unverified lead from the bot, behaviour corroborated).** CodeRabbit's
  security-architecture note on #125: when the load succeeds and the marker cannot be deleted, `install` stays a success (WARN `PM_RUNTIME-136`,
  `dropAfterLoad`), and a later start or failed install restores the kept, older unit. The design documents it; the bot proposes separating the install's
  commit state from the cleanup of the pair. Not in the landing's Residue. Re-read when the start is wired.
- **Owed outside PLAN-82 (plan-reported):** the `pm-mcp-server` assembly in `plan-marshall-mcp` has to call `CredentialStoreChoice.record(LockManager, user, store)`;
  the concept and user documentation and the roadmap check-off belong to the milestone exit in `plan-marshall-documentation`.
- **One SonarCloud issue on PR #120 is untriaged (unverified lead).** The landing reports quality gate passed and 1 new issue it did not read; the
  orchestrator did not read it either. Check the PR's Sonar page before the next change to `pm-provider-git` tests.
- **The trust assumptions of the question reader before it is wired (unverified lead).** CodeRabbit's architecture review of #116 says the
  confinement keeps the check-time limitation of `WorkspaceConfinement` (the snapshot opens the path after the check) and that an out-of-band
  change of `questions.jsonl` that is still syntactically valid is not detected (no digest, no replay). It proposes fixing the ownership and
  recovery guarantees of the runtime store before the production wiring. Inferred by the bot, not verified here. Re-read when the CDI producer
  is planned in `plan-marshall-mcp`.
> ↪ Relocated to `settled.md` § "Watch: documentation #36 during PLAN-41" — PLAN-44 shipped as #38 against the current specification


- `FindingSchemaParityTest` of PLAN-36 and `WorkflowStatusSchemaParityTest` of PLAN-13 read the sibling checkout of `plan-marshall-documentation`
  (`pm.documentation.root`) and aborts by assumption when it is absent — in CI it most likely never
  runs. Check a CI log of `main`; if skipped, the parity guard exists only on developer machines.
> ↪ Relocated to `settled.md` § "Watch: breaking changes for plan-marshall-mcp" — settled by plan-marshall-mcp #76 (e34961b)

- Closing a channel on a POSIX file drops the process's OS locks on
  it, so `JsonlStoreFile` appends on file-locked stores lose the lock manager's OS lock. PLAN-09 (#69)
  accepted the loss for the audit log alone (ADR core-store/024: the per-key in-process lock and
  `RuntimeLock` serialize appends; a deviation from the specification's "append only under
  `FileChannel.lock()`", recorded in the ADR). Every other file-locked JSONL store is still affected and no
  staged plan owns the fix, which belongs in the lock manager.

- The `plan-marshall-mcp` package that wires `FileLockManager` must create one instance per runtime
  (a second one can release the first holder's OS lock on POSIX); stated only in the Javadoc.
  Outside this repository — carry to whoever stages that wiring.
- `verify -Ppre-commit` of `pm-mcp-parent` is not idempotent and rewrites files a plan never
  touched (27 `pm-runtime` files on PLAN-01; lesson 2026-10-10-14-001). Every running plan can
  pull unrelated diffs into its PR. A parent POM change, outside this repository.

> ↪ Relocated to `settled.md` § "Watch: doc #20 of PLAN-02" — #20 merged 2026-10-13T11:28:36Z (be27641)
- Token metrics of a plan can be overstated: PLAN-02 recorded 8,809,647 against its own sum of
  6,131,992 (lesson 2026-10-10-07-019). Read later landings' totals with that in mind.

> ↪ Relocated to `settled.md` § "Watch: PR #16 before the first land" — PR #16 merged (f049631) and the first land #17 carried the ledger
> ↪ Relocated to `settled.md` § "Watch: code-analysis-base at decompose" — it joined as PLAN-36 and shipped as #31
> ↪ Relocated to `settled.md` § "Watch: the two files nearly every package edits" — settled by the per-workstream identifier block decision of 2026-10-09
- About eleven packages land in `pm-core` package `core.store` — re-check at each pairing decision.
- Design choices that change the file set and are therefore for the operator at refine, found by the cleanup of 2026-10-16: PLAN-14
  (the home of the question store: `core.store`, `runtime` per the Javadoc of `QuestionRecordReader`, or `workflow.question` per the
  module structure) and PLAN-19 (where the HMAC sidecar is verified: the HMAC and key code exists only in `pm-runtime`, which
  `pm-core` cannot depend on, yet the surface lists only `core/profile`).
- The local checkout of `plan-marshall-mcp` is five commits behind `origin/main` (#78 to #83, among them the split of
  `question-corpus-public-datasets`); the links of `corpus-overlay-extensions` and `question-continuation` into its `doc/plans/`
  resolve on `origin/main` but not in the stale checkout.
> ↪ Relocated to `settled.md` § "Watch: cross-repository blockers of profile-loader and job-confinement-and-environment" — done in pm-mcp-clients; PLAN-19 unparked 2026-10-21, PLAN-93 owns the index
> ↪ Relocated to `settled.md` § "Watch: differential-test-harness blocked on an operator tool decision" — dropped by the decision of 2026-10-18; PLAN-37 shipped as #106
- Owed to the milestone exit by PLAN-72 (listed in the body of #83): in `plan-marshall-documentation`, the roadmap item of the
  spend removal checked off, and the removal no longer stated as remaining in the roadmap item of `token-usage`. Retire when
  the Milestone 3 exit batch is written.
- The executor of this checkout pins plugin 0.1.1916 while 0.1.1947 is installed and the registry pins 0.1.1932/0.1.1947. PLAN-72's
  run met `unknown_verb` for `git-workflow commit` and `manage-solution-outline deliverable-hashes` and committed with plain `git`
  (lesson forwarded to plan-marshall). The same skew keeps the restart verdict `indeterminate`. Regenerate the executor before the
  next plan starts; PLAN-20 and PLAN-69 inherit the pin otherwise.
  **Recurred on 2026-10-20 (PLAN-78, runbook lane on opencode):** the executor maps `plan-marshall:runbook-lane:runbook_lane` to `0.1.1961`
  (only `resolve`), while the lane skill the opencode run read is newer (`build-classify` exists from `0.1.1969`; `0.1.1971` is installed).
  The lane met `unknown_verb`, asked, and the operator chose "docs-only by inspection", so the plan's Maven gates never ran. PLAN-81 and
  PLAN-88 are running on the same executor and will meet it at their gate step. Regenerate the executor (operator) before they get there.
  **Recurred on 2026-10-20 (PLAN-88):** `build-classify` was `unknown_verb` again, and the build names the `resolve` verb supplied
  (`quality-gate`, `module-tests`, `verify`, `test-compile`) were rejected by the executor, so the operator chose the literal commands of
  `CLAUDE.md`; plan-reported, the `resolve` symptom not re-run here. PLAN-81 landed with no `unknown_verb` reported and its gates run. The executor
  of this checkout still maps `runbook_lane` to `0.1.1961` (0.1.1971 installed); regenerate it before the next runbook plan is emitted.
  **Recurred on 2026-10-21 (PLAN-90):** `build-classify` is `unknown_verb`, `resolve` omits `base_branch`, `build_timeout_seconds` and `guard_log`, and
  the canonical build names were rejected again (plan-reported); the operator chose the executor with real Maven arguments. PLAN-64 and PLAN-82
  were emitted on the same executor.
  **Recurred on 2026-10-21 (PLAN-82):** `maven run --command-args "module-tests"` fails (alias not expanded) and `--project-dir` does not move the
  build's working directory in the `in_process` fallback, so the lane passed `-f {worktree}/pom.xml` (plan-reported). `cleanup restart-check` now
  reads: the registry pins 0.1.1977, the executor is still 0.1.1961 (`registry_parity: indeterminate`). PLAN-64 (runbook, opencode) is still running on
  it; regenerate the executor (operator) before the next runbook plan is emitted.
  **Recurred on 2026-10-21 (PLAN-83):** the resolved build names (`quality-gate`, `module-tests`, `test-compile`) reached Maven and failed ("Unknown lifecycle phase"),
  and the lane read the installed skill documentation under `~/.config/opencode/skills/` to find the invocation surfaces (plan-reported, no guard on
  opencode). PLAN-83 was emitted on the same executor, as were PLAN-64's and PLAN-90's. Still pinned 0.1.1961 against registry 0.1.1977.
- The check command of `CLAUDE.md` (`../plan-marshall-documentation/…`) does not resolve inside a plan worktree, three
  directories deeper: PLAN-72's functional review graded the link criteria on indirect evidence until the command was run by hand.
  The Standing Constraints of PLAN-20 and PLAN-69 now carry the absolute-path command; add the same bullet to every other spec
  when it is emitted, or change `CLAUDE.md` (operator's call).
- The run of PLAN-72 cost about 3.9M tokens against a budget of 2.5M for its plan class; 41% fell in finalize, caused by one
  operator-requested fix commit that re-fired both reviews, the gates, CI, the bot and Sonar. Plan-reported, not re-measured. Fix
  advisory findings in the same commit as the reviewed change, or accept them for a follow-up, to avoid a second full finalize.
> ↪ Relocated to `settled.md` § "Watch: documentation #82, #83, #84 and pm-mcp-core #84" — reconciled; PLAN-20 shipped as #92, labels corrected 2026-10-21
- **SonarCloud lists 109 open issues on `main`; PLAN-87 owns 29 of them (found by `cleanup`, 2026-10-21).** The 29 of the pull requests #120, #121 and
  #122 are all open on `main` at the same file, rule and line (hypothesis of PLAN-87 corroborated). The other 80 lie in other files
  (the rules of all 109: `java:S4032` 22, `S7467` 19, `S5778` 15, `S6213` 10). **Staged on 2026-10-21 as PLAN-94 `sonar-main-backlog`** at the operator's word (runbook lane, WS-06, 8 deliverables: 58 issues in 31 files fixed in the code, the 22 `S4032` on package-only packages NOT removed because they are the reserved homes of staged plans, their settling is the operator's answer at refine; sequenced after PLAN-84) — source: `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false`.
- **Specification ahead of the work packages: #132 to #134 added content no package owns (found by the analysis of PLAN-93, 2026-10-22).**
  The operator's three specification-only merges (`672d746`, `ab1826f`, `50322a7`, 2026-10-21; articles `opus-5-5-prompting`, `first-level-thinker`, `barely-read-the-code`) added: the judgment endpoint (`client-profiles/04-judgment-endpoint.adoc`, protocol `system_one`, watch `PM-WATCH-CLIENT-14` and `-15`), judgment votes and the form of vote tasks (`model-work/10`, `11`, `store-schemas/16`, `18`), a turn's `stop_reason` and an attempt-end per metrics row (`client-profiles/01`, `store-schemas/05`, `model-work/09`), the `own_calls` of `submit:TASK-NNN` and decision point `execute.own-call`, the change map and the dependency delta, and the trade-offs nobody named (`phase-workflows/03`, `04`, `05`, `01`). `grep` of `doc/plans/`, of the 94 specs and of the index finds no package that builds the judgment endpoint, the votes, `own_calls` or the change map; `stop_reason` is part of the profile schema PLAN-19 loads (its branch contains #132). No package file changed, so the staged specs still match their package files, but 29 of them cite a specification part that grew (`landings/PLAN-93.md` lists them). Owed: the operator's decision whether these become packages (and which milestone), and a `cleanup` pass before the next emit to re-ground the specs that cite the changed parts.
> ↪ Relocated to `settled.md` § "Defect: the index names three done packages of pm-mcp-clients, and the dangling roadmap link" — closed by #136 (PLAN-93, 2026-10-22); the roadmap link is fixed in the documentation repository
- **The ninth landing without `total_tokens`: PLAN-93 (`/plan-marshall-runbook-lane` on opencode, 2026-10-22; the eighth was PLAN-89, 2026-10-21).** Same standing property as the opencode landings above; read tokens as unobserved.
