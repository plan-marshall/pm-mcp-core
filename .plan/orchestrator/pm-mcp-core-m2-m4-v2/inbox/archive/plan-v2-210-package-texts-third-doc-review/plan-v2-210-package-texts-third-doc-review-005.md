envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:22:01Z

# Candidate lesson: package text claimed existing code implements a rule it inverts

- Source: review-bot finding f421e7 (PR #636, coderabbitai, Major, Security and Privacy), resolution fixed (TASK-9, commit bc1440f2)
- Component: doc/plans/commit-boundary.adoc:17

## Observation

Line 17 said the declared-path lift removal needs no task because `ProtectedPaths` already implements it. The matcher does the opposite: a path in `declaredFiles` is not reported, and `ProtectedPathsTest.declaredPathLiftsThatPathOnly` asserts that. The specification requires a declared protected path to still need an operator decision at commit time. The operator chose to restore the lift-removal task.

## Candidate rule

Before a package text drops a task on the grounds that "the code already does it", read the named class and its test to confirm the behaviour matches the specification clause, not merely that the class exists. Recurs with finding 94fbb8 (commit-boundary line 18) in the same PR.

Classification deferred to the orchestrator.
