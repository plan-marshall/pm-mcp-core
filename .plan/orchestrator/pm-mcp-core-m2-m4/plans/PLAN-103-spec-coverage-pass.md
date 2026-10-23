# PLAN-103: Classify the specification parts no package names, and cut the packages that are missing

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-103-spec-coverage-pass.md` and is queued as one row file, `queue/PLAN-103.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: none: documents only.

## Objective

The operator's rule of 2026-10-22 is that every specification is implemented. A path search over the 109 specification parts of `doc/specification/` finds 22 that no live plan spec of the epic and no package file under `doc/plans/` names, and three areas where the roadmap has an item but no work package exists: the four-axis domain descriptors with module discovery (Milestone 6, roadmap line 379), the decision quorum and its calibration (Milestone 8, lines 499 and 500), and the pre-merge Dependency Delta Check with the finalize pipeline (Milestone 10, line 581). PLAN-99, PLAN-100 and PLAN-101 wait on the last two kinds; seven packages (`core.architecture`, `core.build`, `core.discovery`, `core.domain`, `core.id`, `workflow.envelope`, `workflow.primitive`) hold only a `package-info.java` and no plan builds them. A path match is a lead, not a verdict: some parts are index parts, some are covered under another name or implemented in `plan-marshall-mcp`. Classify each part, and cut a work package for every part that is truly unowned. Documents only: no code, no change of a specification.

## Deliverables

1. **The classification.** For each of the 22 parts below, state in the pull request body whether it is *implemented* (name the code and the test), *owned* (name the package file or the plan spec that builds it, or the package of `plan-marshall-mcp` or `pm-mcp-clients`), or *unowned*, with the evidence read at the head of `main`: `client-profiles/02-placeholders`; `credentials-and-enrolment (the index part)`; `credentials-and-enrolment/03-audit-and-outbound-https`; `domain-extensions (the index part)`; `domain-extensions/01-four-axis-architecture`; `domain-extensions/02-merge-algorithms`; `filesystem-layout (the index part)`; `identifiers`; `job-runtime/03-parsers-and-gates`; `phase-workflows/06-finalize-re-entry-and-merge`; `phase-workflows/07-questions-waivers-operator-influence`; `state-catalogue/02-plan-phases-1-3`; `state-catalogue/03-plan-phases-4-5-land`; `state-catalogue/05-git-commit-and-push`; `state-catalogue/07-plan-finalize-merge-post-run`; `state-catalogue/08-remediation-units`; `state-catalogue/13-question-adjudication`; `store-schemas/15-screening-and-grants`; `store-schemas/17-re-planning`; `timeouts (the index part)`; `workflow-dsl/03-reference-excerpt`; `workflow-dsl/08-remediation-units`.
2. **The packages for the unowned parts and the three roadmap areas.** For every *unowned* part, and for the decision quorum with its calibration, the pre-merge Dependency Delta Check with the finalize pipeline, and the domain extensions with module discovery, write a package file under `doc/plans/` in the form of the existing ones (the Serves line names the roadmap item and milestone, Where names the modules, What the bullets of the specification part, Trace, Done When) and its row in `doc/plans/README.adoc` with its dependencies. An index part (a part that only links its parts) needs no package; say so. Do not cut a part that is *implemented* or *owned*.
3. **The blocked packages learn their dependencies.** In the index, replace the note `(no work package)` of `judgment-votes`, `own-calls` and `change-map` (written by PLAN-96) with links to the packages of Deliverable 2 where one now exists; their status stays as the dependency rule says.
4. **The packages without a plan.** For each of the seven package-only packages, make the package file that builds it name the package in its `Where` line; a package that nothing builds is reported in the pull request body as a question for the operator (the package is not removed: the Module Structure Specification lists it).
5. **The links check, recorded**: `trace.py` over both trees from the worktree with the absolute paths, `BROKEN LINKS 0`.

**Done when:** the pull request body holds the classification of all 22 parts with evidence; every part classified *unowned* and every roadmap area named in the Objective has a package file and a row (or a stated reason it needs none); the three blocked packages link their new dependencies; each of the seven package-only packages is named by a package file or reported; the links check reports `BROKEN LINKS 0`. No file other than package files and `doc/plans/README.adoc` changes. The orchestrator stages the implementation plans of the new packages afterwards; this plan stages none.

**Trace starting set:** `doc/specification/` (the 22 parts); the roadmap of `plan-marshall-documentation` at `origin/main`, lines 379, 499, 500 and 581; `doc/plans/README.adoc`; the Open Defects of the epic ("Specification parts that no work package or staged plan names"); the Module Structure Specification § Packages.

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: 22 of the 109 specification parts under `doc/specification/` are named by no live plan spec of the epic (statuses `staged`, `launched`, `running`, `parked`) and by no package file under `doc/plans/`, by path — a script over the three trees at `9732401`; the 22 are the list of Deliverable 1
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: python over doc/specification/**/*.adoc (109 files), the ledger plans/ of the live rows (PLAN-103 excluded) and doc/plans/*.adoc at 4617865, substring match of the path relative to doc/specification/ including the .adoc suffix: 22 unnamed, equal to the list of Deliverable 1 (18 named in its text by path, the 4 index parts credentials-and-enrolment, domain-extensions, filesystem-layout and timeouts by file name)
- OBSERVED: the roadmap of `plan-marshall-documentation` has items for the domain descriptors with module discovery (line 379), the decision quorum (line 499), the quorum calibration (line 500) and the pre-merge Dependency Delta Check (line 581), and `doc/plans/` has no package file for any — `doc/roadmap.adoc` at `origin/main` `b8c61b3`; `ls doc/plans/` at `4617865`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: roadmap.adoc at origin/main (b8c61b3) items at lines {'domain descriptors': 379, 'decision quorum': 499, 'quorum calibration': 500, 'Dependency Delta Check': 581}; git grep -il over doc/plans at 4617865 finds none of the three areas
- OBSERVED: the seven packages `core.architecture`, `core.build`, `core.discovery`, `core.domain`, `core.id`, `workflow.envelope`, `workflow.primitive` hold only a `package-info.java` and the Module Structure Specification lists each — `ls` at `4617865`, `doc/specification/module-structure.adoc` at `origin/main` `b8c61b3`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: ls of the 7 packages at 4617865: package-info.java alone; module-structure.adoc at origin/main (b8c61b3) names each
- HYPOTHESIS: a part is implemented or owned although its path is named nowhere (index parts, `client-profiles/02-placeholders` by PLAN-19, `credentials-and-enrolment/03` by PLAN-09 and PLAN-74, `state-catalogue` parts as units of `plan-marshall-mcp`) — confirm/refute per part in Deliverable 1 (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package files can be cut from the specification parts without an operator decision per package (the milestone from the roadmap item, the dependencies from the specification); a part whose milestone or dependency the specification does not settle is reported, not guessed — confirm/refute at the first package (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before classifying, re-run the path search at the head of `main` (PLAN-96 to PLAN-102 and the plans that landed since change the set), list the open pull requests for one touching `doc/plans/README.adoc`, and re-read the roadmap at `origin/main` of the documentation repository; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- OBSERVED: `doc/plans/README.adoc` — the new rows and the dependency cells of `judgment-votes`, `own-calls`, `change-map`
- HYPOTHESIS: `doc/plans/` — the new package files; their names are the plan's choice and are not known here
- OBSERVED: `doc/plans/judgment-votes.adoc` — written by PLAN-96: its Depends-on note
- OBSERVED: `doc/plans/own-calls.adoc` — written by PLAN-96: its Depends-on note
- OBSERVED: `doc/plans/change-map.adoc` — written by PLAN-96: its Depends-on note

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`), which writes the three package files and the rows whose notes Deliverable 3 replaces
- Blocks: the unparking of PLAN-99, PLAN-100 and PLAN-101, and the implementation plans of the new packages
- Runs beside PLAN-26 (`role-sets-and-model-caller`, running; the operator accepted the overlap on 2026-10-23): PLAN-26 deletes its own package file `doc/plans/role-sets-and-model-caller.adoc` and its index row when it is done, and the `Depends on` cells that name it (for example those of `task-queue-and-protocol` and `endpoint-harness`) change with it. Do not edit that file or its row; re-read the index at the head of `main` and merge the base branch (the lane's step 7) before the merge gate, and if PLAN-26 has merged by then, drop its name from any dependency cell you write, as PLAN-96 did for `profile-loader`. PLAN-25 (`waits`) merged as #145 and removed its own row and package file: the index you read at the head of `main` already reflects it.
- Overlaps with: `doc/plans/README.adoc` through PLAN-96 and every plan
- Adjacent to: the roadmap of `plan-marshall-documentation`, which stays as it is

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-103-spec-coverage-pass.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
