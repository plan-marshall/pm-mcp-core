envelope_version=1
sender_type=plan
sender_id=rb-43-phase-sub-workflows
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-28T13:34:48Z

```landing-facts
schema=landing-facts/1
plan_id=rb-43-phase-sub-workflows
epic=pm-mcp-core-m2-m4
pr=n/a
merge_state=open
cleanup_owed=false
deliverables_total=10
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2028
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:open,reviews:open,merge:open
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

# PLAN-43 `phase-sub-workflows` — the engine and language half (session 5)

Standing rules: **r2 (2026-10-27)**, rules R01–R15, R17, R18, R19 (the spec's First Instruction still says r1; the
file at HEAD is r2).
Branch `feature/43-phase-sub-workflows` is pushed through `825d35ad`; no pull request was opened in this session.

## Deliverables

| # | Deliverable | Done-when | Verdict |
|---|---|---|---|
| 1 | Store Schemas § Phases as Sub-Workflows, § The Phase of a Record, State Catalogue § Plan Unit `phases` | engine/language half implemented | **not met as written** — the plan's history and § The Start of the Current Phase are PLAN-105's; the section carries a status note saying so |
| 2 | Phases as sub-workflows; depth 4→5; composition and pruning | met over the fixtures | **not fully met as written** — the phase set as one instance file per composed unit is PLAN-105's (operator decision 2026-10-28) |
| 3 | Lanes as a choice of phases; land as a sub-workflow of execute; adopt calls init then finalize | land sub-workflow proven | **not met as written** — the fixture corpus models no adopt route and reaches the land call from `phases.plan`, so “the land lane calls no refine, outline or plan phase” is not proven here; the real units are `plan-marshall-mcp`'s |
| 4 | The phase of a sub-workflow is the phase unit on its stack; no unit or state declares one | mechanism implemented, unit-tested | **met in the half** — no engine test runs the default plan end to end, so the stack proof is not end-to-end |
| 6 | Restructures the flat plan unit | `StateNode.phase`, `phase inherit`, `PlanUnitMerger`, `partVersions`, `CallFrame.callerPhase` all gone | **met** |
| 5, 7–10 | moved to PLAN-105 / PLAN-106 | — | not in scope |

Tests: `test` 7942 green; `verify -Ppre-commit` green (compile, lint, test); `verify` green. New coverage:
`DefaultPlanUnitTest` (the plan unit's call/entry pairs, the land call inside execute, the phase identity against
`WorkflowStatus.PHASES`, the seeded fuzzer over every default unit, the removed `phase` clause refused).

## Personas and skills

`persona-implementer` for the production half, `persona-module-tester` for the tests, `persona-documenter` for the
guides/ADRs/specification, `persona-code-reviewer` for both self-review rounds. The spec names no domain skills:
**skills: none named**. No skill was added for surface drift.

## Hypotheses

* HYPOTHESIS (Expected Surface inferred from `Where`; the plan's history lands in `core.store`): **refuted for this
  half** — no history type exists or is added; the history is PLAN-105's, and this plan touched `core.store` only for
  the status store (depth 5, no caller phase).

## Findings (self-review, two rounds)

* Round 1 (`0b752ec3`) opened 11 items. Closed: the fuzzer over the default units, the refine invariant's reference
  comparison, `plan.return`'s phase, the phase-clause refusal test, the phase/store parity guard, the stale
  developer-guide/Javadoc/specification-table statements, the ADR statuses (`workflow-unit/002`, `workflow-plan/013`).
* Round 2 (`de3fe24f`) re-graded: the verifier depth is **not** off by one (`CallStack` seeds the root, so five frames
  are admitted and the sixth refused); the land/adopt lane modelling, the `aborted` phase of `handed_back`/`returned`,
  the missing end-to-end engine test, the specification-part deletions (status notes instead) and the cross-unit
  state-id collision in `CallGraphExpansion` remain open.
* Fixed after round 2 (`825d35ad`): the plan-prefix phase rule narrowed to the plan unit's own non-phase states, the
  full `PHASES` parity guard, and the last `inherit`/merge lines of the diagram and DSL ADRs.

## Reviewers

No pull request, so no reviewer was triggered: `coderabbit` (required) and `sourcery` (optional) — **silent, not
asked**. The operator chose a code/docs split (291 files against a 100-file reviewer limit); the code+fixtures half
alone is ~240 files, so the split needs one more cut (fixtures on their own) or an operator waiver before the
review can run.

## Operator questions

1. “Split code and docs into two PRs?” — options {split code and docs into two PRs, one PR with all 291 files, stop
   before the PR and hand off}; selected **“Split code and docs into two PRs”**.
   Used this session: earlier decisions recorded in `progress.md` (the fourth-session paragraph of the spec; the
   operator's 2026-10-28 adoption of the orchestrator's composer recommendation).

Free-text operator messages: none this session.

## Rule check

* Guard: `guard: none on this harness (unenforced)` — no pre-tool-use hook exists here, so no “all kept” claim.
* Shell file operations by the run (read-class included): `find` was **not** used; `tail -n 8` was used once on
  `core-store.adoc` and one shell `grep -n` on a surefire XML — both are the run's own actions and both are
  departures from the lane's “files are read with the file tools” rule; all later reads used the file tools.
  `cp` copied the regenerated golden files into the test resources (five files), and `rm` removed the temporary
  `GoldenRegenTest`.
* Builds: every one through `plan-marshall:build-maven:maven run`; each routed build answered
  `execution_failed` (the daemon cannot launch `mvn` here, exit −1), so every build ran `--execution-mode in_process`,
  none backgrounded, none killed. One `-pl pm-workflow` run without `-am` failed to compile against the installed
  pm-core snapshot; repeated with `-am`.
* Writes: the worktree only; `.plan/temp/` for the progress file, this payload and the temporary regeneration test;
  the specification/guide/ADR edits are in the worktree.
* Sub-agents: two self-review sessions (`ses_e7d912b4…`, `ses_e7d70d1e…`), both read-only, both run in the main
  checkout against the worktree by path. No restart, no killed sub-agent, no re-dispatch.
* Spec read: `resolve --spec-from-ledger` → `spec_source: ledger_worktree`, `spec_differ: true`; the ledger copy
  carries the 2026-10-28 “State after the fourth session” paragraph, the main-checkout copy is older.

## Build server

Setup preflight `ready`; every build routed to the daemon came back `execution_failed` (`exit_code -1`) and ran
in-process; the worktree carries its own executor (`executor_in_worktree: present`), so routing was available and
degraded by the daemon only. No `killed` job. `runtime_stamp_reason`: none.

## Residue

* PLAN-105: the phase set as immutable instance files, the plan's history, the start of the current phase, and the
  lane pruning over the composed phase set (the `PlanInstanceComposerTest` cases removed with that subject).
* `plan-marshall-mcp` (`default-units-and-drift-tests`): the real `plan.pmwf`, `phase-1-init` … `phase-6-finalize`
  and `land.pmwf`, and the adopt lane's route.
* Open defects recorded, not fixed: `handed_back`/`returned` end with phase `aborted` (`WorkflowEngine.ended`);
  `CallGraphExpansion` keys states by bare id, so a cross-unit duplicate is silent; no engine test runs the default
  plan end to end.
* No pull request, no review, no Sonar (not configured for this project) and no merge in this session.
* Links check (R17): `BROKEN LINKS 15`, all in the sibling documentation's own files (roadmap, requirements 13/16,
  the three new opencode/macos articles); none in this diff.
