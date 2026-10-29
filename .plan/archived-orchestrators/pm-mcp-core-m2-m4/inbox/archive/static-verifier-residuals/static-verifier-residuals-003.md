envelope_version=1
sender_type=plan
sender_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T13:14:03Z

# Candidate lesson: cross-repository deliverables vanish from the task plan

Plan: static-verifier-residuals (PR plan-marshall/pm-mcp-core#66).
Signal source: Q-Gate finding 292092 (phase 3-outline) plus an orchestrator observation at
phase 4/finalize.

## What happened

Deliverable 8 (documentation updates in plan-marshall-documentation: implementation-watch
workflow-dsl.adoc and roadmap.adoc) first listed `../plan-marshall-documentation/...` paths as
affected files. The Q-Gate correctly flagged that `../` resolves to nothing inside the plan
worktree and would pollute references.affected_files. The fix removed those paths and described
the external change in prose only. Consequence: phase-4-plan produced NO task for the
documentation work — deliverable 8 became verification-only — and the orchestrator had to open
the issue and the documentation PR by hand. Per project CLAUDE.md, a plan that changes what a
specification states must update the documentation repository as part of its own work, so this
deliverable was mandatory, not optional.

## Candidate rule

A deliverable whose work lies in a sibling repository needs a first-class representation that
phase-4 turns into an executable task (e.g. a cross-repo task profile with a repository-qualified
target and its own PR step), not prose that the task planner cannot see. Until such a profile
exists, the outline should mark the deliverable explicitly as an orchestrator-owned manual step
so it is tracked rather than silently dropped.

## Suggested owner

plan-marshall phase-3-outline / phase-4-plan (cross-repository deliverable handling).
Classification deferred to the orchestrator.
