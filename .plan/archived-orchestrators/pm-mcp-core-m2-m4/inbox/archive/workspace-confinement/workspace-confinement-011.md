envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:49Z

# Candidate: both package homes named in the plan spec were wrong against the module-structure package table

Source: run observation, not a finding record. Verified against the logs.
Plan: `workspace-confinement` (PLAN-03, spec `.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-03-workspace-confinement.md`), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## Evidence

- Work log `1eacd1` (2026-10-09T21:12:20Z), refine step 3b: "Source premise verification: 8 claims checked, 6 valid, 2 refuted (package homes core.config and runtime.security do not match module-structure package table; deferred to outline)".
- Decision log `a3bba4`: the outline placed the confinement check and outcome types in `de.planmarshall.core.store` of `pm-core` and the runtime entry in a new package `de.planmarshall.runtime.workspace` of `pm-runtime`.
- Decision log `a9a215`: the runtime package was settled by an operator answer (Q1).

## What happened

The staged spec inferred `core.config` and `runtime.security` as the homes of the new types. Neither matches the package table of the Module Structure Specification. Refine caught both, its confidence came out at 88.5% against a threshold of 95%, and the phase returned three questions to the operator before it could finish (work log `fee21c`).

## Why it may be a lesson

Two of eight premises of one spec were refuted on the same kind of claim (package placement), and both had been inferred rather than read from the package table. Other staged specs of the same larger piece of work that name package homes may carry the same kind of inference.

Classification is left to the pickup.
