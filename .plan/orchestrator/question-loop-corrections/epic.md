# Epic: Question-loop verification: corrections and follow-up

slug: question-loop-corrections

> Hand-written narrative for one epic under `.plan/orchestrator/question-loop-corrections/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it. `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug question-loop-corrections`
> on the merged tree, and `git add` the result.

## Vision

The work package `question-loop-verification` of `plan-marshall-mcp` measured a worker's question loop (Q1 to Q7, R1 to
R4) on Claude Code, OpenCode and Antigravity, but only at smoke scale (3 runs, 2 fixtures), and landed as
`plan-marshall-mcp` #107, `plan-marshall-documentation` #124, `pm-mcp-core` #197, and the follow-up package
`question-loop-follow-up` (`plan-marshall-mcp` #108). A review of the landing on 2026-10-27 found that several published
statements are stronger than the runs support, that measurement figures were written into normative specification
text, that the raw evidence was deleted with the plan's worktree, and that the follow-up package cannot run as written
(a dependency cycle with `question-continuation`, no durable evidence, a weakened scale and criteria rule). This epic
corrects the published results, repairs the follow-up package and its dependencies, and then runs the re-measurement at
the specified scale, so that `question-continuation` implements settled values and not smoke-scale candidates. Done
when the three plans have landed and the roadmap item of Milestone 4 can be checked on settled values.

## Queue annotations

- PLAN-01 — touches the four specification parts #197 changed in this repository and, in `plan-marshall-documentation`,
  `doc/specification/evaluation.adoc`, `doc/developer/question-loop-experiment.adoc` and
  `doc/discussions/open-questions.adoc`; disjoint from PLAN-02.
- PLAN-02 — touches `doc/plans/` here and `doc/plans/` of `plan-marshall-mcp`; disjoint from PLAN-01. Its change to
  `doc/plans/question-continuation.adoc` concerns PLAN-73 of the sibling epic `pm-mcp-core-m2-m4` (see Watches).
- PLAN-03 — runs in `plan-marshall-mcp` (the package `question-loop-follow-up` as PLAN-02 leaves it); its surface lies
  outside this repository, so the gate reads it as indeterminate and sequences it. It is launched by the operator's word
  after PLAN-02 has landed and the operator accepted the run budget the package asks for.

## Decisions

- 2026-10-27 — Epic hosted in `pm-mcp-core`: the corrected specifications and the package `question-continuation` live
  here, and its ledger lands through the shared ledger worktree. Alternatives: `plan-marshall-mcp` (its ledger is not in a
  worktree, so landing would need a branch switch in its main checkout, and the core specifications would not resolve as
  a surface there).
- 2026-10-27 — Three plans in two workstreams: corrections of the published results (WS-01) and the follow-up (WS-02:
  package revision, then the re-measurement). The re-measurement stays a separate plan because it needs an operator run
  budget and runs in another repository.
- 2026-10-27 — Parallelization scope 2 (operator).
- 2026-10-27 — The `pm-mcp-core` executor in the main checkout was stale (0.1.1924 against the registry's 0.1.2017) and
  was regenerated from the plugin cache before scaffolding; the old file is kept in the session scratchpad. The shared
  ledger worktree was fast-forwarded from #78 to `origin/main` (#195), with no pending paths.

## Open Defects

- The raw evidence of the smoke run (`runs.jsonl`, stub logs, per-invocation archives under `target/question-loop/` in
  the plan's worktree `plan-marshall-mcp/.plan/local/worktrees/question-loop-verification`) was deleted when the
  worktree was removed; no copy exists on this machine. The figures in the evaluation reference can no longer be checked
  against a source. — source: landing review 2026-10-27 (PLAN-01 records it, PLAN-02 prevents a repeat)

## Watches

- `pm-mcp-core-m2-m4` PLAN-73 (`question-continuation`, staged, runbook lane) reads `question-loop-verification` as
  `done` and its dependencies as met, although no `headless.resumability`, `questions` or bound value was settled. A
  finding was sent to that epic's inbox on 2026-10-27. — re-check at the landing of PLAN-02, which makes the package
  depend on `question-loop-follow-up`.
- The local main checkout of `plan-marshall-mcp` is at `6d9638f` and has not pulled #107 and #108. — re-check before
  PLAN-02 or PLAN-03 is launched there.
