# PLAN-75: Redistribute the work packages by component

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-75-work-package-redistribution.md` and is queued as one row file, `queue/PLAN-75.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Recut the work packages of `doc/plans/` so that the plans of this epic are sized 8 to 12 deliverables (the size the operator authorized on 2026-10-17) and so that at least three plans can run in parallel. The plan is not a package of the roadmap: it edits the index `doc/plans/README.adoc` and the package files, in one pull request in this repository, and adds its own package file and index row in its first commit and removes both in its last, so that no row or file of it outlives it.

The cleanup of 2026-10-16 measured the cause (47 live plans, 309 deliverables, mean 6.6; 8 plans with 3 or fewer deliverables, 2 with more than 12; the dependency graph hub-and-spoke and 15 waves deep; every plan in one conflict component through `core.store`). Merging siblings that conflict anyway costs no parallelism, because the gate sequences them regardless; splitting an oversized plan along its component lines gives the later waves a second plan to run beside another one. Merging along a dependency edge is excluded: it only enlarges a hub. This plan does the recut as documentation; the specs and queue rows of the ledger follow it (see Orchestrator follow-up).

## Deliverables

Counts are the numbered deliverables of the staged specs at 238c823 (the bullets of § What of the package files).

1. **Own package file and row, first and last.** The first commit adds `doc/plans/work-package-redistribution.adoc` and its row; the last removes both. A plan that leaves its row behind is the defect found for `corpus-overlay-extensions` and `module-developer-docs`.
2. **Merge `project-machine-store` (2) into `operator-and-question-stores` (4)**: 6 deliverables, both in `pm-core` `core.store`, five shared files, both ready. The surviving file keeps the bullets of both unchanged and names `core.store` and `core.config` in _Where_; the absorbed file and row are deleted; _Depends on_ is the union (both: nothing).
3. **Merge `lane-route-pruning` (5) into `plan-instance-alignment` (6)**: 11 deliverables, both in `workflow.plan`, eleven shared files, both ready; the discussion of the design in `lane-route-pruning` § Discussion stays the first task of the merged package.
4. **Merge `stalled-scope-detection` (1) into `removed-concepts` (7)**: 8 deliverables, eight shared files (`core.store`, `runtime.start`); `removed-concepts` deletes the scope session log that stalled detection would otherwise read, so the order inside the package is removal first.
5. **Merge `epic-findings-review` (6) into `epic-decomposition` (6)**: 12 deliverables, both on epic coordination, both waiting on `chat`.
6. **Split `start-from-clients` (21).** It keeps the bootstraps and the clients' side: bullets 1, 2, 3, 4, 12, 13, 16, 19, 20 (9). The new package `start-entry-and-screening` takes the entry as a directory and the sources: bullets 5, 6, 7, 8, 9, 10, 11, 14, 15, 17, 18, 21 (12), inherits the former _Depends on_, and `start-from-clients` depends on it in addition. Package names may be adjusted with the operator at refine; the cut lines may not move without a recorded reason.
7. **Split `plan-and-epic-control` (14).** It keeps the plan scope: bullets 1, 2, 3, 4, 5, 6, 8 (7). The new package `epic-actions` takes the epic resource, its actions and the project release: bullets 7, 9, 10, 11, 12, 13, 14 (7) and depends on `plan-and-epic-control`.
8. **Rows that name a changed package.** Every row of the index that names a merged, split or deleted package is rewritten: an absorbed name becomes the survivor; a split name becomes the half the row needs, or both halves where the package file does not say. The conservative default is both. No row may name a deleted package, and no package file may link to one.
9. **Conservation and report.** Every moved bullet appears exactly once afterwards (the sum of the bullets of the changed packages equals before: 21 + 14 + 6 + 2 + 5 + 6 + 6 + 1 + 7 + 4 = 72 across the ten packages, 72 after), and the pull request body lists, per changed package, old file, new file(s), bullet counts before and after, and the rows re-pointed. Under **Owed to other repositories** it lists the files of `plan-marshall-documentation` (`lane-route-pruning` 1 file, `epic-decomposition` 3, `start-from-clients` 3), `pm-mcp-clients` (`epic-findings-review` 1, `epic-decomposition` 1, `start-from-clients` 2, `plan-and-epic-control` 1) and `plan-marshall-mcp` (`operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection`, 1 each) that name a changed package, each with the one-line correction; a pull request there is opened only where a link would break.

**Done when:** the index lists exactly the intended rows (four absorbed rows gone, two new rows, ten package files changed or removed as above); `python3 ../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py ../plan-marshall-documentation/doc .` reports `BROKEN LINKS 0`; no row and no package file names a deleted package; the bullet count is conserved (72 before, 72 after); the merged packages carry 6, 11, 8 and 12 deliverables and the split ones 9, 12, 7 and 7; and the index holds no row and no file of this plan.

**Trace starting set:** documentation only, no requirement of its own.

- Requirements: none
- Specification: none
- Watch: none

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Documentation only.** No production code, no test, no POM change. The Maven gates have nothing to build; say so rather than reporting them green.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package): the usual plan-marshall flow, a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it, and for one that names any of the ten changed packages: such a pull request would be working a package this plan recuts, so the recut waits for it or the operator decides.
- **No plan of the ten changed packages runs before this one has landed.** Their package files are what this plan edits.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository. This plan changes none of them. It opens a pull request in another repository only where a link there would break (Deliverable 9); merging that one is the operator's.
- **Do not leave a design choice open that changes the file set.** Where two readings of a cut differ in the files they create, ask the operator at refine.
- **Log messages: none.** **Format versions: untouched.**

## Claim Labels

- OBSERVED: the deliverable counts are those of the staged specs at 238c823 — `start-from-clients` 21, `plan-and-epic-control` 14, `epic-decomposition` 6, `epic-findings-review` 6, `plan-instance-alignment` 6, `lane-route-pruning` 5, `operator-and-question-stores` 4, `project-machine-store` 2, `removed-concepts` 7, `stalled-scope-detection` 1 — counted from `## Deliverables` of each spec by the cleanup of 2026-10-16
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: counted from ## Deliverables of the ten staged specs at 238c823: 21, 14, 6, 6, 6, 5, 4, 2, 7, 1 = 72
- OBSERVED: the merge pairs conflict on code: `operator-and-question-stores` with `project-machine-store` 5 shared files, `plan-instance-alignment` with `lane-route-pruning` 11, `removed-concepts` with `stalled-scope-detection` 8 — read from the declared surfaces by the cleanup of 2026-10-16 (`corpus cross-check` at 238c823)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: corpus cross-check at 238c823 listed 5, 11 and 8 shared declared paths for the three merge pairs before the surfaces were sharpened
- OBSERVED: the packages are named outside this repository in `plan-marshall-documentation` (`lane-route-pruning` 1 file, `epic-decomposition` 3, `start-from-clients` 3), `pm-mcp-clients` (`epic-findings-review` 1, `epic-decomposition` 1, `start-from-clients` 2, `plan-and-epic-control` 1) and `plan-marshall-mcp` (`operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection`, 1 each) — `git grep` of the local checkouts on 2026-10-17; the checkout of `plan-marshall-mcp` was five commits behind `origin/main`
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -l of the local checkouts on 2026-10-17 gave the file counts named in the claim; the plan-marshall-mcp checkout was five commits behind origin/main
- HYPOTHESIS: the cut lines of Deliverables 6 and 7 are the component lines of the two packages and each half can be worked by one plan — confirm/refute by reading the package files `start-from-clients.adoc` and `plan-and-epic-control.adoc` for a bullet that needs both halves (verify-at-outline)
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the rows that name `start-from-clients` or `plan-and-epic-control` each need one half only — confirm/refute per row from its package file; the default is both (verify-at-outline)
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that names this package or any of the ten changed packages, re-count the deliverables of each changed package at the current `main`, and check `doc/plans/README.adoc` for rows added since 238c823; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `doc/plans/operator-and-question-stores.adoc` — Deliverable 2, survivor of the merge with `project-machine-store`; file-exact list drafted by the orchestrator on 2026-10-17 from the package files that name a changed package (`grep -l`), replaces the directory entry
- OBSERVED: `doc/plans/project-machine-store.adoc` — Deliverable 2, deleted
- OBSERVED: `doc/plans/plan-instance-alignment.adoc` — Deliverable 3, survivor of the merge with `lane-route-pruning`
- OBSERVED: `doc/plans/lane-route-pruning.adoc` — Deliverable 3, deleted
- OBSERVED: `doc/plans/removed-concepts.adoc` — Deliverable 4, survivor of the merge with `stalled-scope-detection`
- OBSERVED: `doc/plans/stalled-scope-detection.adoc` — Deliverable 4, deleted
- OBSERVED: `doc/plans/epic-decomposition.adoc` — Deliverable 5, survivor of the merge with `epic-findings-review`
- OBSERVED: `doc/plans/epic-findings-review.adoc` — Deliverable 5, deleted
- OBSERVED: `doc/plans/start-from-clients.adoc` — Deliverable 6, keeps 9 of 21 bullets
- HYPOTHESIS: `doc/plans/start-entry-and-screening.adoc` — Deliverable 6, new, takes 12 bullets
- OBSERVED: `doc/plans/plan-and-epic-control.adoc` — Deliverable 7, keeps 7 of 14 bullets
- HYPOTHESIS: `doc/plans/epic-actions.adoc` — Deliverable 7, new, takes 7 bullets
- OBSERVED: `doc/plans/chat.adoc` — Deliverable 8, a link or a _Depends on_ names a changed package
- OBSERVED: `doc/plans/cleanup.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/decision-engine.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/epic-auto.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/machine-onboarding-unit.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/operator-reports.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/operator-wait-and-machine-scope.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/plan-instance-versions.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/project-onboarding-unit.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/recipe-workflows.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/role-sets-and-model-caller.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/task-queue-and-protocol.adoc` — Deliverable 8, as above
- OBSERVED: `doc/plans/README.adoc` — the rows of the changed packages and the _Depends on_ column of every row that names one
- OBSERVED: `doc/plans/work-package-redistribution.adoc` — this plan's own package file, added in the first commit and removed in the last

## Dependencies and Sequencing

- Depends on: nothing in the index.
- Blocks: PLAN-14, PLAN-15, PLAN-23, PLAN-64, PLAN-65, PLAN-70 (ready, in a merge group), PLAN-48, PLAN-51, PLAN-52, PLAN-53 (changed packages). They are not emitted before this plan has landed; the specs of the merged and split packages are regenerated after it (see below).
- Overlaps with: every plan through `doc/plans/README.adoc` and its own package file; this plan runs alone and first, among the plans of the changed packages.
- Adjacent to: PLAN-20, PLAN-69, PLAN-72 and PLAN-74 do not name a changed package and may run beside it.
- Orchestrator follow-up (not part of this plan): after the landing the orchestrator regenerates the specs of the merged packages from their package files (the surface of a merged spec is the union of the surfaces of its members), stages the two new rows, transitions the absorbed rows to `superseded` with the survivor named, and re-runs `cleanup`.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-75-work-package-redistribution.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
