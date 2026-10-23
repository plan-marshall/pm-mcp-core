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
  - Cleanup 2026-10-22 (restart verdict below): the corpus was re-grounded at `7756dfd` over the 40 staged and parked specs (245 claims re-read and stamped: 167 `corroborated`, 78 `unverifiable` hypotheses; 0 `contradicted`; 0 blocking; every staged and parked claim now `stale: false`). Ten specs (PLAN-25 to 32, 34, 35) carried a claim bullet twice, written by the regeneration of 2026-10-21; the duplicate was dropped. `Blocks` of PLAN-46 (it blocked nothing in the text, seven staged plans depend on it) and PLAN-60 were corrected. The specification additions of #132 to #134 were carried into PLAN-31, 43, 47 and 66 (Open Defects). `corpus surfaces`: 94 declarative, 0 indeterminate, and the two added surface paths are rows of `claimed[]`; `corpus cross-check`: 0 source-origin matches. The settled-narrative relocation was not applied (the operator's confirmation is owed).
  - Staged 2026-10-22 at the operator's word: **PLAN-95** `schema-subset-length-first` (WS-05, runbook, ready), **PLAN-96** `spec-ahead-work-packages` (WS-10, runbook, ready; writes the five package files and rows, and blocks PLAN-97 to PLAN-101), **PLAN-97** `endpoint-harness` and **PLAN-98** `judgment-endpoint` (WS-05, staged, after PLAN-96 and PLAN-19), and, `parked` on dependencies that have no work package, **PLAN-99** `judgment-votes`, **PLAN-100** `own-calls` and **PLAN-101** `change-map` (WS-03). PLAN-94 was re-stamped at `0bf64f7` (111 issues after #135; Deliverable 7 rewritten: the 22 packages stay, the rule is ignored by an analysis property). #135 (PLAN-84) made the claims of 11 staged and parked specs stale (PLAN-27, 40, 46, 47, 57, 58, 59, 60, 62, 63, 76); it was analysed the same day (next entry).
  - Analyzed 2026-10-22 (landed #135 `0bf64f7` PLAN-84, reconciled in `landings/PLAN-84.md`; the drain, no emit at the operator's word): D1 to D5 and D7 corroborated against the merged tree (24 named tests present, 185 tests of the six touched classes green on `main`, all 6 review threads resolved, the PR head tree equals the squash); D6 delivered for a probe only, the owner of the worker half, the launcher subreaper and the runtime-status sentence is not corroborated (Open Defects); two Sonar issues were open at the merge and are PLAN-94's; the footprint (23 files, 6 undeclared) touches the declared surface of PLAN-27 (15 paths) and, through the shared documents, of ten more staged or parked specs. Free slots: PLAN-19 (#137) and PLAN-85 (#129) run; nothing is emitted. `cleanup` owes the re-grounding of the 11 stale specs, PLAN-27 first.
  - Analyzed 2026-10-22 (landed #129 `9732401` PLAN-85, reconciled in `landings/PLAN-85.md`): all six deliverables corroborated (15 named tests present, 287 tests of the 14 touched or split classes green on `main`, no test method lost in the split of three oversized classes, ADRs core-store 030 to 041 without gap, the repeated waiver id shown to be the specified last-readable-line rule); seven Sonar issues were open at the merge (reported by the landing, not triaged) and are added to PLAN-94 (now 118 on `main`, 89 its own); the footprint (38 files) touches the declared surface of 29 staged and parked specs, mostly through the directory `core/store/`, and with #135 **31 staged and parked specs hold stale claims** (0 blocking): `cleanup` before the next emit. The operator-overlap prompt PLAN-87 waited on is moot (PLAN-84 and PLAN-85 landed); PLAN-19 (#137) is the only plan running. Operator decisions of the same turn: the worker half of step 3a goes to PLAN-29 (carried), a plan in `plan-marshall-mcp` owns the native build (PLAN-102), a coverage pass is staged (PLAN-103). Nothing is emitted (the operator drains first).
  - Analyzed 2026-10-22 (landed #137 `9c02edc` PLAN-19, reconciled in `landings/PLAN-19.md`; the drain, no emit): D1 to D3 corroborated against the merged tree (90 tests of the profile package green on `main`, the cross-repository prerequisite pm-mcp-clients#161 is `725f62a` on its `origin/main`); D4 met except one clause, the Claims gate (`PM-WATCH-CLIENT-13`), which had no owner and is carried into PLAN-30 (the operator's call). **The pull request merged on a red SonarCloud gate** (reliability rating 3 against 1 from two `S6218` bug-type issues, new-code coverage 74.9 % against 80; `build / conclusion` and `sonar-build` failed on the final head), accepted by the operator in the lane's questions; its 176 open issues are all on `main` now (293 in all) and are added to PLAN-94 as Deliverable 9. The footprint (38 files, 3 outside the declared surface) touches the declared surface of 15 staged and parked specs beyond the shared index; with the earlier 31, **39 staged and parked specs hold stale claims** (0 blocking), the code-bearing ones PLAN-61, 66, 73, 97, 98: `cleanup` before any emit. PLAN-19 shipped, so PLAN-25, 26, 27, 61, 66 and 73 have lost their last blocker (none runs; nothing running now). Nothing is emitted.
  - Cleanup 2026-10-22 at `4617865` (after #137 and the ledger land #139; the operator: "cleanup and land"): **all 310 claims of the 49 staged and parked specs are re-grounded and stamped at HEAD (214 corroborated, 95 unverifiable hypotheses, 0 contradicted); 0 live specs stale, 0 blocking.** The checks were run, not carried over: the package file claims against the index and the package files (only `question-continuation.adoc` changed since the last cleanup: one phrase, `profile-loader`'s to `core.profile`'s, applied to PLAN-73), the log holders (Core 5, Runtime 81, Workflow 5), the developer guides (22, `core-profile` added; the 16 packages without one unchanged), the format-version constants (all 1 but the journal's 3), the Sonar sets (293 on `main`, 21 `S4032`), the 7 package-only packages, the roadmap items and the module structure at `origin/main` of the documentation (`b8c61b3`, unchanged), the absence claims over `doc/plans/` (two cross-references in `process-designer` and `decision-engine` read and ruled out as not building the quorum) and the 22 unnamed specification parts (22, as before). Dependencies are regenerated from the index: PLAN-19 left every `Depends on` (PLAN-97 edited by hand; it has no index row yet). The declared-surface half: 1574 claimed paths, one indeterminate spec (PLAN-102, deliberately derived). Duplication: no source-origin match among 103. Restart verdict `indeterminate`: five signals ready, `registry_parity` indeterminate (registry pins plan-marshall 0.1.1983, the executor states 0.1.1961: regenerating the executor is a `marshall-steward` act not done here). PLAN-25, 26, 27, 61, 66 and 73 are now emittable by the gate once the operator says so.
  - Analyzed 2026-10-22 (landed #143 `9c9d99c` PLAN-96, reconciled in `landings/PLAN-96.md`; the drain): D1 to D4 corroborated against git (the diff of #143 is exactly the five package files and `doc/plans/README.adoc`, 193 insertions; the five index rows carry the statuses `open`, `open`, `blocked`, `blocked`, `blocked`; `profile-loader` dropped from the `endpoint-harness` row because #137 removed it); the links-check run is in the PR body and was not re-run here (`BROKEN LINKS 1` on the stale sibling checkout, 0 after the lane fast-forwarded it). One change beyond the deliverables, in the named file: the index introduction (`README.adoc:13`) corrected after a CodeRabbit finding. **PLAN-103 has lost its only blocker.** PLAN-97 still waits on PLAN-26, 29 and 34 (the `endpoint-harness` row of the index names `role-sets-and-model-caller`, `worker-launch` and `role-qualification`) and PLAN-98 on PLAN-97, so for those two only the verify-first clause "PLAN-96 has landed" is satisfied, not the dependency. The claims "PLAN-96 is staged and writes the package files" in PLAN-97 to PLAN-101 are now stale by construction (reported, not blocking; `cleanup` re-grounds them). No collision with PLAN-94 or PLAN-95. Nothing is emitted by this analysis.
  - Analyzed 2026-10-22 (landed #144 `3cc1585` PLAN-95, reconciled in `landings/PLAN-95.md`; the drain): D1 to D4 corroborated (the change is one method plus 79 test lines, ADR `core-role-corpus/008` and the developer guide; SonarCloud for #144: 0 issues, gate OK); the builds and test counts are the lane's report, not re-run. **The merge carries five `pm-runtime` files outside the declared surface** (`SubprocessJobRunner.java` with five `cui-rewrite:disable` markers, and four test files: the output of `verify -Ppre-commit`, committed as produced at the operator's choice in the lane): `surface_delta` `expansion_detected`, 4 declared, 9 realized. **Collision recorded (Step 4 item 5):** **PLAN-94** (launched) declares `SubprocessJobRunner.java`, `SubprocessJobRunnerTest.java` and `JobRecoveryStepTest.java`, which #144 changed: its lane must merge the base and re-read them before it edits them; **PLAN-26** (running) shares `SchemaSubset.java` and its test, the overlap the operator accepted, and must merge the base before its gate; PLAN-27 (staged) declares four of the five `pm-runtime` files. The declaration of PLAN-95 was wrong only by those five files, which the pre-commit rewrite caused, not the plan; it is shipped, so no spec is corrected. The claims of 23 specs now name changed files (reported, not blocking; `cleanup` re-grounds).
  - Analyzed 2026-10-23 (landed #145 `a53d312` PLAN-25, reconciled in `landings/PLAN-25.md`; the drain): the one deliverable and the three Done-when clauses the lane read corroborated (the selection code read against the four profile and token combinations; `ModelWaitTest` +220 lines; 5 new classes in `workflow.engine`; ADR `workflow-engine/012`; Traceability lines; CI `overall_status: success`; SonarCloud for #145: 0 issues, gate OK, new coverage 94.1); test and build counts are the lane's report, not re-run. **The adapted spec never reached the lane** (see Open Defects): the clauses added on 2026-10-22 (links check, local coverage and integration runs, deletion of covered specification and watch items) were not read and are not evidenced; the specification rows and the watch items were kept, not deleted, and `PM-WATCH-JOB-5` is not reported on. Footprint 13 files against 14 declared: `doc/Traceability.adoc` added, seven `workflow.hypermedia` and hypermedia-document paths missing (the lane refuted the hypothesis that the package reaches them). `waits` is done, so the `Depends on` of PLAN-28 and PLAN-42 lost it (the lane edited the index); **PLAN-26 (running) shares the index with it**. Self-review ended `budget-exhausted` (three rounds, the operator proceeded).

## Decisions

> ↪ Relocated to `settled.md` § "Decisions of 2026-10-09 to 2026-10-12" — the decomposition, the first twelve landings and the operator decisions of those days; every plan named is shipped and reconciled in `landings/`

- 2026-10-09 — The operator switched `orchestrator.use_worktree` on right after init. The ledger
  lives in the shared ledger worktree and reaches `main` only through `land`; the ignore rule
  that makes it trackable and the knob itself are PR #16.

- 2026-10-10 — Operator decision: `parallelization_scope` raised from 3 to 4, because PLAN-05
  `http-baseline` is disjoint from the running PLAN-04, PLAN-06 and PLAN-07 (only
  `doc/plans/README.adoc` shared). PLAN-36 is also disjoint and is the next filler.

> ↪ Relocated to `settled.md` § "Decisions of 2026-10-13 to 2026-10-14" — the landings of PLAN-13, 16, 17, 18, 21, 22, 39, 54, 56 and 12, the log-holder split (ADR 005, #64) and the module-documentation rule; all merged

- 2026-10-13 — Operator rule: the harness is Claude Code unless the operator states otherwise.

- 2026-10-13 — Operator rule: every landing analysis verifies that all PRs related to the plan,
  especially its `plan-marshall-documentation` PR, are **merged**, and that their content matches the
  merged code. Each landing record lists them by number with state and merge time. Applied at once to
  all shipped plans: the documentation PRs of PLAN-01, 02, 03, 04, 06, 07, 08, 10, 11, 13, 16, 18, 22
  and 36 (doc #19, 20, 24, 26, 27, 28, 30, 31, 33, 34, 46, 49, 52, 53) are all MERGED; no PR is open
  in `plan-marshall-documentation`. Gaps found: doc #46 (PLAN-18) merged with its single pre-review
  commit, so the mandatory `maxLength` of enrolled patterns is not in the specification; PLAN-41,
  PLAN-44 and PLAN-54 opened no documentation PR, so PM-WATCH-DSL-3/4/5/8/9/11/15/16/29/32 stay open on
  documentation main although the verifier implements them.

- 2026-10-13 — Operator decision: every versioned artifact stays at `format_version` 1 until the
  first release; a format change replaces version 1 in place. Existing bumps are an Open Defect to
  clean up at the end of this epic. Added as a Standing Constraint to every staged and parked spec;
  the running PLAN-17, PLAN-21 and PLAN-39 were told through their mailboxes.

- 2026-10-13 — Practice (from the landing of PLAN-21, kept in view): re-check a spec's premises against the landings since staging, right before emitting it. Full entry: `settled.md` § "Decisions of 2026-10-13 to 2026-10-14".

> ↪ Relocated to `settled.md` § "Decisions of 2026-10-15 to 2026-10-17" — the moves of the specifications into this repository (ADR 007), the cleanups and regenerations after PLAN-75, the overlap prompts and the landing of PLAN-69; all done

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

- 2026-10-22 — Operator decisions, in the order of the request: (1) the `SchemaSubset.checkString` fix (pattern evaluated before `maxLength`) is a small plan of its own: staged as PLAN-95 `schema-subset-length-first` (runbook lane, no dependency); it is not part of PLAN-94. (2) The 22 `java:S4032` on package-only packages: the operator asked whether `@SuppressWarnings` is right and to remove the unclaimed packages if empty and unreferenced. Verified: the annotation is legal on a package in JDK 25 but SonarJava never reads it (the rule reports on the file with no line; the filter visits classes, methods and variables and matches a line), and none of the 22 packages is unreferenced (the Module Structure Specification lists all 22 and states that every package exists with a `package-info.java` also where no type has arrived yet). So none is removed; PLAN-94 Deliverable 7 settles the rule with the analysis property `sonar.issue.ignore.multicriteria` for `**/package-info.java` in the root `pom.xml`, falling back to a `wontfix` dismissal in SonarCloud. (3) Every specification must be implemented: the five areas of #132 to #134 become work packages, PLAN-96 to PLAN-101 (Open Defects, "Specification ahead of the work packages", relocated). (4) All settled narrative is relocated (this pass): the dated Decisions of 2026-10-09 to 2026-10-17 with the six rules still in force kept here, and six resolved defects and watches; `## Queue annotations` is never relocated (the standard).
- 2026-10-22 — Operator decisions after the analysis of PLAN-84 and PLAN-85: (1) the worker half of termination step 3a is `worker-launch` (PLAN-29), "as proposed" (counter-evidence recorded in the Open Defect: ADR runtime-job/013 names the supervisor); (2) a plan in `plan-marshall-mcp` owns the native build: PLAN-102; (3) a coverage pass for the specification parts no package names: PLAN-103. Drain first, no emit.
- 2026-10-22 — Operator decisions after the analysis of PLAN-19: (1) **a plan may hold up to 12 deliverables** (the scope guard of about 6 is raised for this epic): PLAN-94 stays one plan with nine, Deliverable 9 (`core.profile`, 176 issues) is part of it; (2) the Claims gate (`PM-WATCH-CLIENT-13`) goes to **PLAN-30** `supervisor`, not PLAN-29 (the orchestrator had carried it into `worker-launch` first); (3) a Sonar Standing Constraint and Done-when clause go into every staged and parked spec that touches Java (44 specs, PLAN-94 included), after the analysis below; (4) **`gh` usages of the lanes are fine, now and later, and `plan-marshall` is deprecated: the focus is on what works**: no finding goes to plan-marshall's inbox for the lane's `gh` calls, and the lane rule breaks are no longer a Watch.
  **Sonar access, analysed 2026-10-22 (the operator: "there is only sonar for the main branch; some plans complained they cannot access").** Read from the API, the CI logs and the reusable workflow `cuioss/cuioss-organization` `reusable-maven-build.yml`: SonarCloud holds `main` and pull requests (65 pull-request analyses listed, the newest `137`), and no feature branch; the organisation refuses every other branch: the API answers `branch=<feature>` with "Organization is not allowed to access data from non main branches", and `sonar-build` of a **push to a branch that has no open pull request yet** fails with "Failed to get Quality Gate status - Organization is not allowed to access data from non main branches" (run 39634924569, PLAN-91; two of four failed push runs of PLAN-19; the workflow skips the push build once a same-repo pull request is open, so the pull-request run carries the Sonar analysis). So a lane that asks for its branch gets no data, and a push before the pull request fails the check; the pull-request query works without a token, because the project is public (`pullRequest=<N>`; the issues of #137 were read that way). A further, separate failure is the `workflow-integration-sonar` verb itself ("HTTPS required when authentication is configured", `spend-removal-001`; the verbs were not found by the lane of PLAN-83). The constraint therefore says: open the pull request first, query `pullRequest=<N>` by plain HTTP, never `branch=`, and a failing verb does not excuse the check. A red gate on the pull request is the operator's to accept. Whether the "organization restriction" the operator fixed during PLAN-19 was Sonar's or GitHub's merge restriction is not in the record.
- 2026-10-22 — Operator decision: **the standing rules are one file, read first.** The Standing Constraints that were copied into every spec (about 10 KB each, 48 of 49 live specs identical) are extracted into `standing-rules.md` beside the plans (rules R01 to R18, revision r1; the operator asked whether it is sensible: yes, one source instead of edits across 49 specs, as the Sonar fold of the same day needed). Each staged and parked spec now opens, before its Objective, with a *First Instruction* that names the file by repository-root path, by `../standing-rules.md` and by absolute path (for a plan launched from another repository, PLAN-102), lists the rules that apply to it and gives the plan parameters of R06 (developer documentation, ADR directory, workstream) and R12 (log holder); its *Standing Constraints* hold only what is plan-specific (PLAN-35, 40, 60). The conversion was lossless by construction: the 49 old bullet sets are rebuilt from the new specs and the rules file and compared (0 differences, 683 identical, 48 + 47 parametrised, 8 plan-specific kept). Shipped, superseded and retired specs are history and were not touched. **Consequences:** (1) a lane must read the file, so it must be on `main` before an emit: the ledger is landed with this change; (2) a rule change is made in the file with a new revision, not in the specs, and the analysis names the rules that changed since a running plan read them; (3) the analysis checks the landing for `Standing rules: r1` and the rule ids; (4) every spec staged from now on gets the First Instruction block, never a copy of the rules; (5) the `Sonar:` clause stays in each spec's Done-when, as acceptance.

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
  **Recurred on 2026-10-22 (PLAN-96, `rb-96-spec-ahead-work-packages-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same cause (opencode, `deepseek-v4.1-flash/high`); every other required key supplied. The running count of the opencode runbook landings is kept in the Watches line of the resume anchor.
  **Recurred on 2026-10-22 (PLAN-95, `rb-95-schema-subset-length-first-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same cause; the first landing to carry the optional `sonar_open_issues` fact (`0`).
  **Recurred on 2026-10-23 (PLAN-25, `rb-25-waits-001.md`):** `total_tokens=unknown`, `missing_keys: [total_tokens]`, same cause; with `self-review:budget-exhausted` in `steps`, which is a true statement and not a defect of the landing.
- **The port `QuestionRecordReader` cannot address a pending entry.** `ReadRequest(Optional<String> scopeId, String questionId)` names a plan, an
  epic or the workspace, nothing else, so a `question` state of a pending entry (an entity of the workspace scope) cannot be resolved through the
  adapter PLAN-88 shipped. Reported by the lane and corroborated against the record's two components (`pm-core` `core.spi`). No package owns it: a
  change of the port belongs to `pm-core`, its adapter to `pm-runtime`, the engine side to `question-continuation` (PLAN-73) — the operator
  decides the owner. Source: landing PLAN-88, corroborated 2026-10-20.
> ↪ Relocated to `settled.md` § "Defect: two decisions of 2026-10-18 reached the specifications after the last reconciliation" — analysed and reconciled on 2026-10-19; PLAN-37 shipped as #106, PLAN-86 to PLAN-89 staged and shipped or kept as rows
- **Owed outside this repository, from that analysis (still owed):** the skill tools without scope (`plan-marshall-mcp`), the ICU message subset in `pm-api` (`pm-mcp-clients`), `quarkus-micrometer` with the native build (`plan-marshall-mcp`), the Landlock check on WSL2 moved to Milestone 7; `UC-PRJ-4` and `UC-PLN-6` are `DRAFT` use cases without a requirement. Not staged, because the entry itself is unverified: the rest of the roadmap item behind PLAN-02 and the `!===` reading of `AsciiDocValidator` (both listed above).


- **Nothing in production records `credentials.store`, so a fresh install refuses every credential (found by the follow-up of a separate session to the API change, plan-marshall-mcp #88 `8137c9e` and #90 `91b60d5`, merged 2026-10-18/19; corroborated on 2026-10-19 against the code and the merged PR).** The daemon serves only the store recorded in `credentials.store` of `machine-config.json`; before one is recorded every credential operation is refused and `CredentialsResource` answers `409 {"error":"onboarding_incomplete"}`. The only legitimate writer is the credential-store aspect of the machine onboarding (`store.choose` to `store.move`), through `CredentialStoreChoice.record(LockManager, user, Store)` of `pm-runtime`; no production class calls it (grep of `pm-mcp-server/src/main` at `91b60d5`). The aspect belongs to PLAN-46 `machine-onboarding-unit`, which is parked on PLAN-42 `operator-wait-and-machine-scope`, PLAN-61 `token-usage` and PLAN-70 `plan-instance-alignment` (running). The aspect is not separable from the machine scope, so PLAN-46 is on the critical path of every use of a credential; PLAN-74's deliverable 7 is the part already delivered (the producer reads the recorded choice). The operator verb is NOT missing and no plan was added for it (checked 2026-10-19 after pulling the clients): `pm-operator config credentials --store os|file` is implemented in `pm-mcp-clients` (`ConfigCommand.CredentialsCommand`), designed in its terminal-client design, and the specification names `pm-operator credentials migrate os|file` for the move. What is missing is the runtime side the verb talks to: it posts to `/api/v1/config/credentials`, which no repository serves and no specification defines (the credential-store aspect of PLAN-46 is that side). The clients' call path is theirs to check. PLAN-46's spec carries the finding; its dependency on `credential-store-choice` was dropped (shipped as #90).

- **`SchemaSubset.checkString` evaluates a pattern before it checks `maxLength` (CodeRabbit lead on #126, verified in the code at `3d4baf7`, 2026-10-21).**
  `pm-core/src/main/java/de/planmarshall/core/role/corpus/SchemaSubset.java` lines 139 to 153 compile and match the schema `pattern` against the whole
  value and test `maxLength` afterwards, so a long value reaches the backtracking engine first. It is the verification-corpus path (PM-WORK-6, `CorpusValidator`),
  not the enrolled-schema path PLAN-89 bounded; whether a corpus value or schema can be attacker-controlled was not assessed. The thread is unresolved on #126.
  A fix (check the length first) changes behaviour, so it is not a PLAN-94 item. No plan owns it: the operator decides — a one-deliverable plan, or a bullet of
  the plan that next edits `core.role.corpus` — source: landing PLAN-89, corroborated 2026-10-21. **Staged on 2026-10-22 as PLAN-95 `schema-subset-length-first`** (operator decision: a small plan; runbook lane, no dependency).
  **CLOSED 2026-10-22 by #144 (`3cc1585`, PLAN-95, `landings/PLAN-95.md`):** corroborated against the merge: `checkString` tests `maxLength` first and returns, the pattern is not compiled for an over-long value, ADR `core-role-corpus/008` records it; SonarCloud for #144 reads 0 open issues, gate `OK`. To be relocated to `settled.md` at the next compaction. The CodeRabbit thread on #126 is not re-read here.
- **A spec edited in the ledger worktree is not what a lane reads (found 2026-10-23 at the landing of PLAN-25; checked).** The runbook lane reads the spec from the main checkout, where it is tracked; with `orchestrator.use_worktree` on, an orchestrator edit (the runbook hand-off, a rule added, a Done-when clause, a sequencing note) reaches the main checkout only through `land`. The adaptations of PLAN-25, PLAN-26 and PLAN-87 made on 2026-10-22 were committed in the ledger worktree (`689e282`, `6c0467f`) and never landed, so the main checkout shows the original text (checked: no `R17`, the `/plan-marshall task=…` hand-off, last commit `caa6eb5`), and the landing of PLAN-25 quotes the original rule list and Done-when. **PLAN-26 and PLAN-87 are running on the unadapted specs.** Remedy for the next adaptation: land the ledger first (the operator's word), then emit; for the two running lanes the operator decides whether to land and to tell them. The orchestrator reported those adaptations as done when only the worktree copy was changed; the dry-run `resolve` did not catch it because it read the same stale copy and succeeded. A candidate lesson for plan-marshall's lessons-routing inbox (the operator's call): the emit step of `next` and `analyze` should say whether the spec it points to is landed.
- **PLAN-25's package bookkeeping is partial (landing, checked).** `doc/plans/waits.adoc` and its index row are gone and the two `Depends on` cells are updated, but the implemented rows of Timeouts § Waits and the covered watch items (`PM-WATCH-JOB-5` in `implementation-watch/job-runtime.adoc`; `PM-WATCH-JOB-30` stays open by the lane's statement) were kept; the lane gives a reason for the specification rows (other parts anchor them) and none for `PM-WATCH-JOB-5`. The links check (R17) was not run for #145. Whether the kept rows are a deviation or the correct reading of "the covered items deleted" is the operator's; a follow-up of the next documentation pass if not.
- **Unverified lead (lane report, PLAN-25): `ProfileValidator` accepts a bounded wait the specification forbids.** `pm-core` `ProfileValidator` rejects only `bounded >= idle_timeout_seconds` when progress resets no timer, while the specification requires the 30-second margin (`idle − 30`); `ConnectionSettings` of #145 enforces the specified rule, so a profile the loader accepts in that narrow band is refused when a wait is built. Pre-existing, in the code of PLAN-19; no plan owns it; not checked against the validator by the orchestrator. Source: landing PLAN-25, 2026-10-23.
- **Unverified lead (lane report, PLAN-95): the shipped corpus schemas leave patterns unbounded before the regex engine.** `imported-item.schema.json` has nine `pattern` members and a `maxLength` only on `title`, a field without a pattern, so after #144 a long value still reaches those nine patterns unbounded. The enrolled path bounds this with `PATTERN_MAX_LENGTH_CEILING`; the corpus path does not. Not checked against the file by the orchestrator. The lane also refuted the premise that the schema can be attacker-controlled (source, overlay and imported-item schemas are class-path resources; values come from project- or hash-pinned sources and may be large). The operator decides whether it becomes a plan; no plan owns it. Source: landing PLAN-95, 2026-10-22.
- **Two landings of 2026-10-21 are incomplete (`inbox landing-check`).** PLAN-89 (`plans-index-and-traceability-corrections-006.md`): `missing_keys: [total_tokens]`
  and `harness`, `model`, `effort`, `plugin_version` `unknown`. PLAN-91 (`plan-91-plan-instance-advisories-008.md`): no `landing-facts` block at all, all nine required keys
  missing; the message is a narrative with a front-matter block. Both were reconciled from git, the pull requests and the code. The `/plan-marshall` lane on opencode
  wrote a free-form landing for PLAN-91; fold further occurrences here.

## Watches

- Owed to the milestone exit after PLAN-70 (#107, 2026-10-19) and PLAN-91 (#131, 2026-10-21: the concept and user documentation of the slice and the roadmap check-off), written in plan-marshall-documentation and not by any plan here: the roadmap check-off of the plan-instance item (`roadmap.adoc` line 233 still lists the prune reasons `plan_default` and `merge_by_operator`, the override event and `diff_summary` under "Remaining") and the documentation parts of PM-IMPL-5. The plan's statement that its change breaks `plan-marshall-mcp` was REFUTED on 2026-10-19 (`grep` finds no Java file there naming `CompositionRecord`, `CompiledInvariants`, `PlanFacts` or `RECIPE_ID_FACT` at `91b60d5`); it holds only for whoever first wires the composer into the assembly. Retire this watch when the roadmap line is updated.
> ↪ Relocated to `settled.md` § "Watch: documentation PR #95, the pin advance of the migration inventory" — retired 2026-10-21: merged as 963e879
- Filed 2026-10-21 in plan-marshall's `live-blockers` inbox (`pm-mcp-core-m2-m4-001` to `-004`, queued and uncommitted in that ledger worktree until its `land`): the executor-skew evidence of five runbook landings, the `total_tokens=unknown` recurrence (seven landings), the lane edit of the main checkout and the bot silence on a merge head (PLAN-81, the two unfiled lane-contract lessons), and the `ci` abstraction's lack of a repository selector. **Filed 2026-10-22 (`pm-mcp-core-m2-m4-005`, high priority, same inbox, same condition):** the runbook lane leaves SonarCloud issues open at the merge (38 over eight pull requests, none of its steps reads them; the quality gate passes with open issues) and a lane step plus an optional landing fact `sonar_open_issues` are asked for. The spec half is the epic's own and is not done yet (Standing Constraint and Done-when clause; the operator's word is owed).
- **The runbook lane's build routing fails in a fresh lane worktree (PLAN-96 landing, 2026-10-22; reported by the lane, not reproduced here).** The first routed gate returned `executor_absent_from_worktree`: the worktree's `.plan/` held `local/`, `orchestrator/`, `project-architecture/` and `marshal.json` but no `execute-script.py`, so every build ran in process (`mechanism: in_process_fallback`) and the learned timeout bound expired mid-build until `--timeout 3600` was passed. The gate itself passed twice (6913 tests). It adds to the executor-skew evidence already filed in plan-marshall's `live-blockers` inbox (`pm-mcp-core-m2-m4-001`); whether it is a new lesson for plan-marshall's `lessons-routing` inbox is the operator's call. Retire this watch when a lane worktree starts with the executor present or the operator rules it a duplicate.
  **Second observation 2026-10-22 (PLAN-95, #144):** same `executor_absent_from_worktree` on the first routed job (setup preflight `ready`), every later build `in_process_fallback`, and one in-process run hit the learned 776 s bound after 3047 passing tests (re-run with `--timeout 1800`). The cost is now seen twice in two lanes.
  **Third observation 2026-10-23 (PLAN-25, #145):** same failure and fallback, one gate hit the 617 s bound after 5266 passing tests; the setup-time `resolve` also reported the project root `down (unreachable)` while the worktree preflight said `ready`. Three lanes in a row.
- **The merge queue dequeued #145 on a Maven Central 403 (PLAN-25 landing, 2026-10-23; the failing job was not read by the orchestrator).** The merge-group run failed in `build / build (25)` resolving `org.apache.maven.surefire:surefire-junit-platform:pom:3.6.0` for `pm-core`; the PR checks were green, the operator re-enqueued it, and it merged. An infrastructure flake of the merge-group run, not of the code; the organisation registry is listed before Maven Central and the group filter lets it be asked for `de.planmarshall` only, so a Central 403 on a third-party coordinate is Central's. Retire this watch when a second dequeue does not recur over the next five merges, or the operator rules it noise.
- **`/tmp` inode exhaustion on the operator's machine (PLAN-96 landing, 2026-10-22).** The local gate failed with 3 of 1,048,576 inodes free (OpenRewrite and JUnit temp directories); the lane cleaned stale `build-map-seed-tree-*`, `junit-*`, `java-template*`, `base-probe-*`, `executor-mappings-*`, `guard-node-*` and `cuioss-review-bot-artifact-guard-*` directories on the operator's selected option, freeing about 508k inodes. `/tmp` is still at 52% inode use and the machine-wide cause was not identified. Expect the next full `verify` run in a lane to hit it again. **Hit again 2026-10-22 (PLAN-95): `/tmp` inode table 100 % full, the operator chose to clean build scratch.** Retire this watch when a lane reports `/tmp` inode use back at the usual level.
- The Secret Service path (Linux) is unproven end to end (stated on plan-marshall-mcp #88 and #90; the Keychain path was measured on macOS). In this repository `SecretServiceIT` of `pm-runtime` is `@EnabledOnOs(LINUX)` and skips itself by `assumeTrue` when there is no session bus address or the default collection is locked, so a CI run proves nothing for it. Proving it takes a Linux desktop session with an unlocked default collection running `verify -Pintegration-tests -pl pm-runtime -am`; that is plan work or the operator's, not an orchestrator read. Retire this watch when a run on such a session shows the test executed and green.
- The native build of `pm-mcpd` in `plan-marshall-mcp` against the deployed SNAPSHOT has not run since #90 and #92. It is the only check of
  the `kill` downcall descriptor of PLAN-20 and of the Keychain and Secret Service paths of PLAN-74, and `SecretStoreProducer` there compiles
  again since plan-marshall-mcp #88 (8137c9e), but no native build has run. PLAN-84 asks to run it or to name the plan that does.
  **Answered 2026-10-22 (PLAN-84, #135):** reported not run, owner repository `plan-marshall-mcp` (PLAN-78 Deliverable 3); no plan of `plan-marshall-mcp` is named (`git grep` of its `doc/plans` finds none), and the checks of #135 list no native build. The watch stays open until the build runs. **Decided 2026-10-22 (operator): a plan in `plan-marshall-mcp` owns it** — staged as PLAN-102 `native-build-package-in-plan-marshall-mcp` (runbook, launched from that repository, documents only: it writes the package `native-build-verification` and its row, and drops the dangling cross-repository links of that index: four packages of `pm-mcp-core` and four of `pm-mcp-clients` that are done).
- **Unverified leads from the review bot on #135 (CodeRabbit, 2026-10-22; third-party text, partly corroborated in the code).** (1) The sweep of remembered descendants (`ProcessTreeTracker.sweep`) checks that a member still runs and then signals it by numeric PID (`signals.signalProcess(pid, SIGTERM)`), so a member that exits and whose PID is reused between the check and the signal could be signalled; the bot rates it moderate and inferred, the window exists in the code, exploitability is unproven, and operating-system permissions bound the damage. (2) Where a platform reports no start instant, `SubprocessJobRunner.pidReused` answers true and no PID is signalled; the bot fears a cancelled or over-budget job left running, the code and ADR runtime-job/010 say the process is ended through its handle, and no test of an unreadable instant on the cancel path was read. Re-read both when the supervisor or the launcher subreaper is planned.

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
> ↪ Relocated to `settled.md` § "Watch: the link check left this repository" — PLAN-70 shipped and the documentation repository dropped the link (documentation #96, `b8c61b3`)

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
> ↪ Relocated to `settled.md` § "Watch: verification records of the opencode runbook landings" — the verified records of seven opencode runbook landings (PLAN-78, 88, 81, 90, 82, 83, 64); the watch itself stays open for the harness and the executor
> ↪ Relocated to `settled.md` § "Defect: untriaged SonarCloud issues of #120, #121 and #122" — all 29 are PLAN-87 Deliverable 5; the other 80 are PLAN-94 (operator decision of 2026-10-21)
- **Owed outside PLAN-64 (plan-reported, corroborated by the Javadoc):** the `pm-mcp-server` assembly in `plan-marshall-mcp` registers `PendingEntryRecordsSweep`
  and wires `StalledScopeDetector` with an `OperatorPort`; `process-and-overview` serves the `needs_operator` summary; the removed public `JobRefusal` API
  is a change the clients' build meets. The roadmap `_Remaining_` update and the concept and user documentation belong to the milestone exit.
> ↪ Relocated to `settled.md` § "Watch: #125 reached main without a rewrite the pre-commit gate applies" — carried by #127 (c7780a0)
> ↪ Relocated to `settled.md` § "Defect: untriaged SonarCloud issues of #120, #121 and #122" — the ten of #121 are counted in the 29 of PLAN-87 Deliverable 5
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
> ↪ Relocated to `settled.md` § "Defect: untriaged SonarCloud issues of #120, #121 and #122" — the one of #120 is counted in the 29 of PLAN-87 Deliverable 5
- **The trust assumptions of the question reader before it is wired (unverified lead).** CodeRabbit's architecture review of #116 says the
  confinement keeps the check-time limitation of `WorkspaceConfinement` (the snapshot opens the path after the check) and that an out-of-band
  change of `questions.jsonl` that is still syntactically valid is not detected (no digest, no replay). It proposes fixing the ownership and
  recovery guarantees of the runtime store before the production wiring. Inferred by the bot, not verified here. Re-read when the CDI producer
  is planned in `plan-marshall-mcp`.
> ↪ Relocated to `settled.md` § "Watch: documentation #36 during PLAN-41" — PLAN-44 shipped as #38 against the current specification


> ↪ Relocated to `settled.md` § "Watch: the schema parity tests and the sibling checkout" — settled by pm-mcp-core #77 (ADR 007): the specifications live here and the parity tests run in CI
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
- **SonarCloud lists 111 open issues on `main` (109 on 2026-10-21, 111 after #135); PLAN-87 owns 29 of them (found by `cleanup`, 2026-10-21).** The 29 of the pull requests #120, #121 and
  #122 are all open on `main` at the same file, rule and line (hypothesis of PLAN-87 corroborated). The other 80 lie in other files
  (the rules of all 109: `java:S4032` 22, `S7467` 19, `S5778` 15, `S6213` 10). **Staged on 2026-10-21 as PLAN-94 `sonar-main-backlog`** at the operator's word (runbook lane, WS-06, 8 deliverables: 58 issues in 31 files fixed in the code, the 22 `S4032` on package-only packages NOT removed because they are the reserved homes of staged plans, their settling is the operator's answer at refine; sequenced after PLAN-84) — source: `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false`.
  **Updated 2026-10-22:** #135 (PLAN-84) left two new smells (`S3776` `SubprocessJobRunner.java`, `S5778` `JobRecoveryStepTest.java`) and moved one line, so PLAN-94 now owns 82 (22 `S4032` and 60 further issues in 33 files) and has been re-stamped at `0bf64f7`. Deliverable 7 is decided (operator, 2026-10-22): the 22 packages stay, because the Module Structure Specification lists them and requires the `package-info.java`; `@SuppressWarnings` and `NOSONAR` cannot suppress the rule; the rule is ignored for `**/package-info.java` by an analysis property in the root `pom.xml`.
  **Updated 2026-10-22 (PLAN-19, #137):** the package `core.profile` landed with 176 open issues (123 `S1192`, two `S6218` bug-type issues that failed the reliability rating of the gate, four `S3776` in `ProfileValidator`) and a gate that read ERROR; `main` lists 293, and `S4032` lists 21 (the package holds code now). PLAN-94 Deliverable 9 owns the 176 and the coverage of the new code. Nothing in the spec of PLAN-19 asked for a clean Sonar search (finding `pm-mcp-core-m2-m4-005` in plan-marshall's `live-blockers` inbox; plan-marshall is deprecated, so the spec-side constraint is the fix that counts).
> ↪ Relocated to `settled.md` § "Defect: specification ahead of the work packages (#132 to #134)" — closed 2026-10-22 by the operator's decision that every specification is implemented: PLAN-96 to PLAN-101
- **Specification parts that no work package or staged plan names (found by the coverage read of 2026-10-22; an unverified lead per part).** A script over the 109 files of `doc/specification/` found 26 whose path no live plan spec and no package file under `doc/plans/` names (22 after PLAN-96 to PLAN-101 were staged; the four that left it are `client-profiles/03` and `04`, `domain-extensions/03` and `state-catalogue/10`): `client-profiles/02-placeholders`, `credentials-and-enrolment` (the index and `03-audit-and-outbound-https`), `domain-extensions` (the index and parts `01-four-axis-architecture`, `02-merge-algorithms`, `03-module-discovery`), `filesystem-layout`, `identifiers`, `job-runtime/03-parsers-and-gates`, `phase-workflows/06-finalize-re-entry-and-merge` and `07-questions-waivers-operator-influence`, `state-catalogue/02`, `03`, `05`, `07`, `08`, `10`, `13`, `store-schemas/15-screening-and-grants` and `17-re-planning`, `timeouts`, `workflow-dsl/03-reference-excerpt` and `08-remediation-units`. A path match is not a verdict: some are index parts, some are covered under another name (PLAN-19 may own the placeholders, `credentials-and-enrolment/03` may be delivered by PLAN-09 and PLAN-74), some are implemented in `plan-marshall-mcp` as units. The operator's rule is that every specification is implemented, so each part is to be classified (implemented, owned, unowned) and the unowned ones cut into packages. Evidence that the gap is real: the seven packages that hold only a `package-info.java` and that no plan builds (`core.architecture`, `core.build`, `core.discovery`, `core.domain`, `core.id`, `workflow.envelope`, `workflow.primitive`) name `domain-extensions/01` to `03`, `job-runtime/03`, `identifiers` and the envelopes and primitives specifications; and the roadmap has items with no package for the decision quorum with its calibration (Milestone 8), the Dependency Delta Check and the finalize pipeline (Milestone 10) and the domain extensions with module discovery (Milestone 6). PLAN-99, PLAN-100 and PLAN-101 wait on three of them. **Decided 2026-10-22 (operator: yes): staged as PLAN-103 `spec-coverage-pass`** (plan-marshall lane, WS-10, documents only): it classifies the 22 remaining parts as implemented, owned or unowned with evidence, cuts the packages for the unowned ones and for the three roadmap areas without a package (domain extensions with module discovery, Milestone 6; decision quorum and calibration, Milestone 8; Dependency Delta Check and finalize pipeline, Milestone 10), and replaces the `(no work package)` notes of `judgment-votes`, `own-calls` and `change-map`. It depends on PLAN-96.
> ↪ Relocated to `settled.md` § "Defect: the index names three done packages of pm-mcp-clients, and the dangling roadmap link" — closed by #136 (PLAN-93, 2026-10-22); the roadmap link is fixed in the documentation repository
- **The ninth landing without `total_tokens`: PLAN-93 (`/plan-marshall-runbook-lane` on opencode, 2026-10-22; the eighth was PLAN-89, 2026-10-21).** Same standing property as the opencode landings above; read tokens as unobserved.
  **The tenth: PLAN-84 (`rb-84-job-runner-residuals-001.md`, 2026-10-22):** `missing_keys: [total_tokens]`, same cause.
  **The eleventh: PLAN-85 (`rb-85-store-records-advisories-001.md`, 2026-10-22):** same.
  **The twelfth: PLAN-19 (`rb-19-profile-loader-001.md`, 2026-10-22):** `missing_keys: [total_tokens]`, same cause.
- **Step 3a of the job runtime specification is delivered for a probe only, and the owners of the rest are not corroborated (found by the analysis of PLAN-84, 2026-10-22).** `ProcessTreeTracker` (#135) ends a probe's descendants that left the group. Not built: the worker (`llm`) half, the Linux child-subreaper of the launcher (`PR_SET_CHILD_SUBREAPER` in `pm-exec`, `pm-mcp-clients`) and the specification's sentence "and the runtime status shows it". The landing names the supervisor (PLAN-30) for the worker wiring and the launcher repository for the subreaper, as the spec's Done-when allows ("or its owner is named"); but `doc/plans/supervisor.adoc`, `worker-launch.adoc`, `job-confinement-and-environment.adoc`, the specs PLAN-27, 29 and 30, and the packages of `pm-mcp-clients` (`origin/main` `07f2c87`) name no descendant, no subreaper and no step 3a. The nearest packages are `worker-launch` (PLAN-29, which starts `llm` jobs on the runner) and `job-confinement-and-environment` (PLAN-27, which composes the job). **Decided 2026-10-22 (operator, "as proposed"): the worker half is `worker-launch` (PLAN-29)**, carried into its spec (section *Carried From PLAN-84*, a claim stamped); PLAN-30 has a note. Counter-evidence found while carrying it, for the operator to overrule: ADR runtime-job/013 and the Javadoc of `SubprocessJobRunner` say "a worker is started by the supervisor … the supervisor wires the same tracker for a worker", so the PLAN-84 landing's naming of PLAN-30 is backed by merged design text, not by PLAN-30's spec; PLAN-29 is ready to decide at its trace matrix whether the wiring is one condition in the runner for the kind `llm` (its) or sits in the supervisor's start path, and tells PLAN-30 if the latter. Still owed: a package in `pm-mcp-clients` for the Linux child-subreaper of `pm-exec` (outside this ledger), and the place the runtime status shows a survivor (PLAN-29 reports it if none exists). The landing record is `landings/PLAN-84.md`.
- **The record's stage and platform gate had no owner (found by the analysis of PLAN-19, 2026-10-22).** `PM-WATCH-CLIENT-13` says the load-time gate (refuse `worker.eligible: true` and a non-null `zero_tool` when the record's stages did not pass on the platform the runtime runs on) "belongs to the launch path (`worker-launch`, not yet decided there)", but no staged spec carried it (`grep CLIENT-13` over `plans/`: PLAN-19 and PLAN-61 only). The landing declared the clause open (`deliverables_done=3` of 4). **Carried 2026-10-22 into PLAN-30 `supervisor`** (operator; section *Carried From PLAN-19*, a claim stamped; the orchestrator had first put it into PLAN-29), with the probe-workspace, decoy-server and verifying-binary checks the watch also names: each is built there or gets its own watch item.
- **PLAN-19 merged on a red SonarCloud gate, 176 issues open (found by the analysis of PLAN-19, 2026-10-22; operator-accepted).** The lane reported the Sonar block as an operator question and the operator chose to merge ("Merge now, gaps accepted"; "you fix Sonar"). The cause is the one filed as finding `pm-mcp-core-m2-m4-005`: the spec asked for Maven gates only and the lane never reads the Sonar search. Owned by PLAN-94 Deliverable 9. The spec-side fix is done 2026-10-22: a Sonar Standing Constraint and a Done-when clause in the 44 staged and parked specs that touch Java (PLAN-94 has the constraint and its own clause); the five docs-only specs (PLAN-40, 96, 102, 103) need none.
