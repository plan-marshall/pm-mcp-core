envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:22:03Z

# Candidate lesson: package text claimed the main-checkout guard implements a conditional clause

- Source: review-bot finding 94fbb8 (PR #636, coderabbitai, Major, Functional Correctness), resolution fixed (TASK-10, commit ed4c9b68)
- Component: doc/plans/commit-boundary.adoc:18

## Observation

Line 18 claimed the guard implements the confinement condition. `mainCheckoutBreaches` takes no confinement input, `FootprintBoundary.check` refuses any returned path, and `GitStageAndCommitPrimitive.execute` runs the check for every scope, so the refusal is unconditional. No plan under doc/plans/ owned the condition. The line was reworded to say the guard refuses unconditionally today and the confinement condition is owed work of no package of the index.

## Candidate rule

Second instance in the same PR (see f421e7) of a package text asserting existing code satisfies a clause without reading the code path. Verify the call chain against the clause's conditions before writing "already implemented".

Classification deferred to the orchestrator.
