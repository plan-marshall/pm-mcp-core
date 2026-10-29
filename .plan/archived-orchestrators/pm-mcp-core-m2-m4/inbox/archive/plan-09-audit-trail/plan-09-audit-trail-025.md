envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T19:00:28Z

# change_type heuristic classified an audit-trail feature as "verification"

- Source: plan work log c9b372 (2026-10-14T07:42:52Z, phase 1-init): "(change-type-heuristic) Detected change_type=verification (confidence=1.0)", overridden 8 s later by 852e9f "Metadata: change_type=feature". Orchestrator-observed signal, corroborated by the log.
- Suggested component: `plan-marshall:phase-1-init` (change-type heuristic) / `plan-marshall:manage-status`.

## Observation

The request was "anchored audit trail with hash chain and verification" — new packages, formats, rotation, ADRs. The heuristic reported `verification` at confidence 1.0, apparently keyed on the word "verification" in the title. A confidence of 1.0 on a keyword hit is overconfident; the operator/orchestrator had to override it.

## Candidate rule

The change-type heuristic should not score a single keyword as certainty; a request that creates new production code should outrank a "verification" keyword, and keyword-only matches should cap below the auto-accept threshold.
