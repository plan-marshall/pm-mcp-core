# PLAN-V2-195: The work package texts follow the two doc reviews, and the mechanisms without an owner get one

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: doc-review-2026-11-24-001.md sections 2, 3, 4 and doc-review-2026-11-25-001.md sections 3, 4; operator's cut, plan 8. Documents only: NO Java.
lane: runbook

> Staged plan spec — ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-195-package-text-edits-from-the-doc-reviews.md`; one queue row, lane `runbook`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R14, R18, R19, R20 (orchestrator confirms). Plan parameters: R06 none (the work package files are the product here); ADR none; layout WS-10. Skill first (R20): `ref-asciidoc` of `pm-documents`, and the section "Planning the Work" of `../plan-marshall-documentation/doc/developer/project-setup.adoc` for what a package file holds.

## Objective

The merged specifications changed what several open packages build. This plan edits their texts (the packages named below) and gives the mechanisms the reviews found without an owner a home: the light gate, the protected-path decision at the commit check, the build-neutral derivation, the build egress record mode, the compiled-in `coderabbit` record, the merge-state read, and the small rules of the second round. The phase `sanity-check` is a FEATURE of `phase-sub-workflows` (`Phase` gains it), not a correction; no Java plan is drafted for it.

## Deliverables

Four deliverables, 13 existing package files, the index and two new package files (16 files), no split needed. A package file keeps its shape (Tasks, Trace, Done When); the index row is the only place that states status and dependencies.

1. **The phase and finalize packages.**
   - `phase-sub-workflows.adoc` :15 ("`plan.pmwf` and the six phase units"): seven phases, the plan unit's call `phases.sanity-check` between `execute` and `finalize` (a local-only plan included), `Phase` gains `sanity-check` (and the stores that admit a phase are checked: `MetricsRow`, `FindingPhase`, `WorkflowStatus`, `status`, `metrics`, `finding-record`, `references`, `config-workflow` schemas), the lane entries `refine.analyze` (deep) and `outline.light` then `outline.light-gate` (light); README :73 ("The six phases as sub-workflows") says seven. :19: the outline record drops `scope_estimate` and `added_dependencies`, the plan's `change_type` is derived at acceptance, the read-only-versus-mutating deliverable refusal stays. :21: the Q-Gate bypass predicate (one deliverable, at most three affected files, `change_type` in {bug_fix, tech_debt, verification}); the composition of `realized` entering `execute.verify-sweep`, recomposed when a loop-back adds tasks; whole-tree widening on a realized deleted or renamed path claimed by a module, not on a declared `delete`. :39 Done When gains the matching tests. A new task takes round 2 V6: the second `derive-verification` call when `plan.describe-tasks` or `revise:TASK-NNN` submits `target_files`.
   - `finalize-prune-and-land-files.adoc` :8, :15, :32 and README :295: task 1's enum values exist on main (`PruneReason` `NO_CODE_DELTA`, `NO_PRODUCTION_DELTA`); the task is rewritten around the event `realized` and its rules (`no_production_delta` for the mutation step, the realized-footprint reading of `no_code_delta` as the lane-entries plan leaves it), Done When :32 says "evaluated at `realized`, an unreadable footprint keeps the steps".
   - `re-planning.adoc` :15-16: trigger kinds `infeasible`, `merge_conflict`, `ceiling` only; `base_advanced`, `downstream_limit`, `base_overlap_min_files` and the checkpoint `finalize.review-cap-check` removed; `execute.replan-check` fetches nothing and only applies queued chat revisions.
   - `finalize-pipeline-and-merge.adoc` :20 ("The composite wait `finalize.concurrent-wait`"): the wait region enters at `finalize.concurrent-wait`, no pre-wait cap gate; :15: the `open_ended` verdict-currency exception reads the build-neutral derivation (phase-workflows/06:32); task 5 (sanity-check) takes round 2 V3 (removed coordinates are `dependencies` items, `accept_kind` updates the baseline in both directions) and the fix-proof lookup in the change ledger, and its Done When :35 gains the optional test lines (a red run found through `Satisfies` and a failed test in a recorded test file; a run red only in other files, a compile error or `failed_unparsed` proves nothing; `fix_test_changed` on a digest change; a base change brought by a rebase raises no item); a new task takes V10 (`pr_title:text[1..72]` on every pr-author submit, the outline title in `facts`, no `pr_title_missing` refusal) and a new task the compiled-in review-bot records (`coderabbit`: `rate_interval_seconds: null`, full verb `@coderabbitai full review` used only as the remedy when there are no new commits).
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
2. **The worker, job and onboarding packages.**
   - `worker-activity.adoc` :18-19: the signals are `repeating`, `no_progress`, `failing_again`, `edit_churn`, `agent_started`; `over_size` and `outside_footprint` do not exist (`predicted_cost_tokens` stays a packing input); :36-37 Done When: re-check watch `PM-WATCH-DISP-68`.
   - `job-confinement-and-environment.adoc` :15 and :67: the narrower write set (a `writer` worker its plan worktree and `.marshall/local/temp/`; `read_only` and chat workers no project path; `write_paths` inside the project root never theirs) with the tests (a writer's write to the main checkout or another plan's worktree is refused; a `read_only` worker writes nothing of the project); :28: Build Egress has no owner — a new task "Build Egress and Build Read Set: the egress proxy of `build` and `project_script` jobs with its record mode, `egress_hosts`, `egress_refused`, `build_credential_files`" and :28 points at it.
   - `project-onboarding-unit.adoc` :16-17 and :37: after the `run_checkout_scripts` consent one base-branch build through the build egress proxy in record mode; the recorded hosts are proposed for confirmation, no host list is parsed from build configuration; (round 2 V12) the recording build also reads every compiled credential location of the project's build domains that exists on the machine, proposed as `build_credential_files`, confirmed or removed one by one, no configuration parsed, a missing credential shows as a failing recording build; reuse `CredentialLocations` of PLAN-V2-146 (staged), not a second list; Done When :37 gains the matching tests; the package depends on the Build Egress task above.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
3. **The owners of the remaining mechanisms.**
   - `initial-lane-router.adoc`: a new task "The light gate `outline.light-gate`" (budget cap, band, language-neutral neighbours, `neighbours_indeterminate`, the ratchet; needs the module graph of `domain-extensions-and-architecture`, so the index row gains that dependency) and a new task for the tie rule of `init.classify` (the union of the top candidates, no operator question; round 2 V8); Done When gains both.
   - `build-parsers-and-gate-honesty.adoc`: a new task "The build-neutral derivation of the freshness gate" (the gate derives from the latest succeeded build row: `git diff --name-only <head>..HEAD`, the paths dirty now and at the build, the build map's classification; the writer of the build row's new members, V4) next to task 1.
   - `domain-extensions-and-architecture.adoc` :15: `python` leaves `edge_resolvers`; `epic-resource-and-release.adoc` :17: a row is launchable when its prerequisites shipped and its claim sections parse, without re-grounding, the `unreadable` hold stays; optional `operator-reports.adoc` :16: the catalogue has no `epic.overlap_found`; `start-from-clients.adoc` task 8 (:22) and Done When (:38): the posture asked in `init.options` with `standard` preselected (round 2 V9, optional).
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
4. **The new packages and the index.** A new package `commit-boundary` (`doc/plans/commit-boundary.adoc`): the protected-path decision at the commit check (one operator decision on the diff of exactly the touched protected paths, `accept` or `refuse`, `refuse` leads to a `fix` task; `phase-workflows/03:153`), the main-checkout clause made conditional on confinement, and the lift removal (the code of those two parts is the plans of the cut; this package owns the decision). Recommended: a new package `ci-contract-finalize-operations` (`doc/plans/ci-contract-finalize-operations.adoc`): `ci.pr-observe` with the GitHub, GitLab and Forgejo mapping to `behind`, `ci.merge`, `ci.enqueue`, in `pm-provider-ci` and the providers; `finalize-pipeline-and-merge` task 3 keeps the `behind` route of `merge.route-refusal` and the sync decision over the realized footprint and depends on it (if the operator decides the other way, task 3 takes the provider read and this file is not created). The index `README.adoc` gains the two rows (status `open`) and the changed cells (:73, :295).
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter

**Done when** (against `origin/main` e451ed04): each cited line states its new text — fails at e451ed04 for every line named above (`phase-sub-workflows.adoc:15` says six phases; `finalize-prune-and-land-files.adoc:15` builds enum values that exist; `worker-activity.adoc` names no signal removal); every mechanism of the two reviews' section 4 has exactly one named owner (task or package) — fails at e451ed04 (no owner of the light gate, the commit decision, the build egress, the coderabbit record); no package text names a removed thing as built (`scope_estimate`, `added_dependencies`, `init.light-gate`, `outline.light-regate`, `finalize.review-cap-check`, `over_size`, `outside_footprint`, `base_advanced` of re-planning) — fails at e451ed04; the index and the package files agree on names and dependencies. `verify -Ppre-commit` (rewrites reviewed and committed) green. Sonar: 0 open new-code findings at the head or each key selected by the operator.

## Standing Constraints

- Documents only: no Java, no schema, no specification text of this repository is edited by this plan (the specifications are merged); a deviation found is a finding, not an edit.
- Names the specification sections instead of repeating them; a package text states the tasks, never the statuses (the index does).
- Read every cited line at the head first; drop what a merge settled. No more than 100 files per pull request.
- Sonar: fix or dismiss only on the operator's selection; an undecidable read blocks.

## Claim Labels

- OBSERVED: the package lines quoted above match `doc/plans/*.adoc` at 1ae69d3f: `phase-sub-workflows.adoc:15,19,21,39`, README `:73`, `:295`; `finalize-prune-and-land-files.adoc:8,15,32`; `re-planning.adoc:15-16`; `finalize-pipeline-and-merge.adoc:15,20,35`; `worker-activity.adoc:18-19,36-37`; `job-confinement-and-environment.adoc:15,28,67`; `project-onboarding-unit.adoc:16-17,37`; `domain-extensions-and-architecture.adoc:15`; `epic-resource-and-release.adoc:17`; `operator-reports.adoc:16`; `start-from-clients.adoc:22,38`.
- OBSERVED: `PruneReason.java:53-55` already holds `NO_CODE_DELTA` and `NO_PRODUCTION_DELTA`; the specification names the build-egress record mode and `build_credential_files` at `credentials-and-enrolment/02-enrolment-and-project-lifecycle.adoc:57`; `CredentialLocations` is on no branch of main (`git grep` finds nothing).
- OBSERVED: no package of `doc/plans` names the light gate, `ci.pr-observe`, Build Egress, a review-bot record or the protected-path decision.
- HYPOTHESIS: `build-parsers-and-gate-honesty` is the better home of the build-neutral derivation than `phase-sub-workflows` task 7 (which is the Q-Gate and execute task) — confirm against `doc/specification/job-runtime/03-parsers-and-gates.adoc` (Gate Honesty Verification) (verify-at-outline).
- HYPOTHESIS: the `create-pr` step's owner is `finalize-pipeline-and-merge` — the package `phase-sub-workflows` carries no finalize step task although `finalize-pipeline-and-merge` "Left out" names it for the step catalogue; settle at outline.
- HYPOTHESIS: the index row edits are inside this plan's surface although R19 names `doc/plans/README.adoc` as not surface — read R19 in the standing rules first (verify-at-outline).
- Verify-first clause: a package whose status is `done` at the head is not edited; report it.

## Expected Surface

- OBSERVED: `doc/plans/phase-sub-workflows.adoc`, `doc/plans/finalize-prune-and-land-files.adoc`, `doc/plans/re-planning.adoc`, `doc/plans/finalize-pipeline-and-merge.adoc`, `doc/plans/worker-activity.adoc`, `doc/plans/job-confinement-and-environment.adoc`, `doc/plans/project-onboarding-unit.adoc`, `doc/plans/initial-lane-router.adoc`, `doc/plans/build-parsers-and-gate-honesty.adoc`, `doc/plans/domain-extensions-and-architecture.adoc`, `doc/plans/epic-resource-and-release.adoc`, `doc/plans/operator-reports.adoc`, `doc/plans/start-from-clients.adoc`
- OBSERVED: `doc/plans/README.adoc` (the rows :73 and :295 and the new rows)
- HYPOTHESIS: `doc/plans/commit-boundary.adoc` (new), `doc/plans/ci-contract-finalize-operations.adoc` (new, recommended)

## Dependencies and Sequencing

- Depends on: none (documents only); the text of `project-onboarding-unit` and `worker-activity` should be edited after the worker-activity fold (PLAN-V2-150) is applied by the orchestrator.
- Overlaps with: the Java plans of the cut name the same packages in their Trace only; `doc/plans/README.adoc` is edited by every plan that closes a package (R19).
- Adjacent to: the five specification repositories (merged, untouched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-195-package-text-edits-from-the-doc-reviews.md
```

## Write-Boundary

The plan touches only its own repository documents; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).
