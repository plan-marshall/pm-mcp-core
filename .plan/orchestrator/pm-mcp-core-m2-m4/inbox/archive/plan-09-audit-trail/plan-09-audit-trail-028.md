envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T19:00:44Z

# Freshness gate refused because the build ran before the commit

- Source: orchestrator-observed during finalize. NOT corroborated in the plan work or decision log (the work log records only successful "Worktree freshness: basis=ledger-verified" transitions). Treat as orchestrator testimony.
- Suggested component: `plan-marshall:phase-5-execute` / `plan-marshall:phase-6-finalize` (freshness gate ordering).

## Observation

The freshness gate refused once because the verifying build had run against the tree before the last commit was made, so the ledger's worktree hash no longer matched HEAD; the build had to be repeated after committing.

## Candidate rule

Commit first, then run the verifying build that the freshness gate will check; a workflow step that builds and then commits should be reordered, or the gate's refusal message should name this ordering as the likely cause.
