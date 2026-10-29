# Landing Analysis: PLAN-43 — Phases as sub-workflows, the core (1 of 5 fully met; the engine and language half landed)

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #220 (merged as 645ef28e, code and fixtures) and #224 (merged as 38ef09fd, documents), 2026-10-28, merge queue

> Landing record for one plan that shipped **partially, by design of its split**. Source: inbox message `rb-43-phase-sub-workflows-005.md` (runbook lane, opencode,
> deepseek-v4.1-flash/high, 2026-10-28T20:57:42Z, the fifth and sixth sessions), corroborated against git at `38ef09fd`, the index and the SonarCloud public API.
> The message is a lead; every fact marked "checked" was read by me; no test was re-run and CI was not read through `ci` (the `gh` CLI was intermittently unauthenticated).
> The lane read the ledger-worktree copy of its spec (`spec_source: ledger_worktree`, `spec_differ: true`). Earlier messages `-001` to `-004` were archived as observations.

## Deliverable Fidelity vs Spec

The plan was split on 2026-10-27 into PLAN-43 (the core: deliverables 1 to 4 and 6), PLAN-105 and PLAN-106. The lane counts **1 of 10** (`deliverables_total=10` is the package's number;
the plan's own set is five). I agree: only D6 is fully met as written.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 store schemas, phase of a record | **not met as written** | The engine and language half is implemented and documented; the plan's history and § The Start of the Current Phase are PLAN-105's (status note in the section). |
| D2 phases as sub-workflows, depth 4 to 5 | **not met as written** | Checked on `main`: the plan unit calls its phase units, no `phase` member. The phase set as one instance file per composed unit and the lane pruning over it are PLAN-105's; `PlanInstanceStore` still writes `plan.v<N>.pmwf` only. |
| D3 lanes as a choice of phases, land inside execute, adopt | **not met as written** | The land call inside `execute` is proven over the fixtures; the corpus models no adopt route, so "the land lane calls no refine, outline or plan phase" is not proven here. The real units are `plan-marshall-mcp`'s (`default-units-and-drift-tests`, blocked). |
| D4 the phase is the phase unit on the stack | **met in the half** | The derivation is implemented and unit-tested per unit; **no engine test runs the default plan end to end**. |
| D6 restructure the flat plan unit | **met** | Checked: `PlanUnitMerger`, `partVersions`, `CallFrame.callerPhase` and `phase inherit` appear in no main source file on `main`; `StateNode` has no phase member. |
| D5, D7 to D10 | moved | D5 to PLAN-105; D7 to D10 to PLAN-106 (shipped partially, #210). |

**Whole diff, checked:** #220 256 files (+2,309/−2,619); #224 35 files (+628/−196). 291 in all, the cut decided on 2026-10-28: code with all fixtures, documents apart (177 modified fixtures drop the removed clause and cannot merge without the code).

**Surface delta** (`inbox landing-check`): `unmeasured`.

## Metrics and Anomalies

- Tokens: `unknown` (the 44th landing), `plugin_version` `unknown`. Self-review: `budget-exhausted` after two rounds (not clear); the open items below are what it left.
- Anomalies: the plan took **six lane sessions** (a stop on a non-compiling branch, three more stops at capacity, then the PR cut). The build daemon again could not launch `mvn` (`execution_failed`); every build ran in-process. #221 (documents, base `main`) could not build before the code landed (the `store-schemas/02` block is read by the schema-parity test) and was closed and replaced by #224 on the merged base.
- Rule breaks disclosed by the lane: shell `grep`/`tail` reads, one `git branch -D` on an abandoned branch, `git add -A` on two source subtrees to cut the split branches, `cp -a`/`rm` and `git worktree remove` of four temporary worktrees, two `api.github.com` fetches outside `ci` (timed out).

## Routing and Merge Behavior

- Review: **CodeRabbit (required) skipped on file count on both PRs** (`NEUTRAL / skipping`) and posted no review on any head; the 100-file limit was waived by the operator, who said the question stayed with them and then told the lane to merge all green PRs. **Both merges went in without a required review**, on the operator's explicit word; the gap is the lane's own account and I record it as such. Sourcery ran on #224 (success) and skipped #220.
- CI/merge: both through the merge queue, green (30 checks each by the lane's account). Checked: both merge commits are ancestors of `origin/main`.
- Sonar: **checked: `pullRequest=220` gate OK, 0 open; `pullRequest=224` gate OK, 0 open; `branch=main` 9 open** (unchanged: PLAN-43 added none). The lane fixed the eight issues it was sent in `f2bdd499`.
- Collision: none recorded; the lane merged `main` into #224's branch.

## Reconciliation Actions

- [x] row `status` → `shipped` (the core landed; the package `phase-sub-workflows` stays OPEN on PLAN-105 and PLAN-106's assembly halves)
- [x] row `pr` stamped `#220` (code and fixtures; the documents are #224)
- [x] row `landing` stamped `landings/PLAN-43.md`
- [x] row `plan_marshall_plan_id` stamped `rb-43-phase-sub-workflows`
- [x] epic.md narrative reconciled (Queue annotation, Open Defects for the lane's open items, the Observed bullet superseded)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Owed to PLAN-105** (its spec already carries each, "State after PLAN-43"): the phase set as immutable instance files, the lane pruning over the composed set (the four removed composer cases), the plan's history, the start of the current phase, the composed-graph provenance for the diagram grouping, and the `handed_back`/`returned` phase (`WorkflowEngine.ended` derives `aborted` from the outcome class where the store schema says `completed` for every plan ended through `finalize.archive-plan`; the lane's reading, the schema line not read in full).
- **Owed to `plan-marshall-mcp`** (`default-units-and-drift-tests`, blocked): the real `plan.pmwf`, `phase-1-init` to `phase-6-finalize`, `land.pmwf`, the adopt lane's route, and with them the end-to-end test of the default plan.
- **No owner:** `CallGraphExpansion` keys states by bare identifier and is context-insensitive (a cross-unit duplicate id is silent; ADR `workflow-verify/033` documents the conservative direction); it matters when the real units arrive. Recorded as an Open Defect.
- **Cleanup the lane did not do:** the remote branches `feature/43-phase-sub-workflows` (`825d35ad`) and `feature/43-phase-sub-workflows-docs` and the local worktree `.plan/local/worktrees/43-phase-sub-workflows` still exist; the operator removes them when they choose, the orchestrator does not.
- The R17 links check reads `BROKEN LINKS 15`, all in the sibling documentation repository's own files (not re-run by me). The concept and user documentation and the roadmap check-off stay owed to the milestone exit.
