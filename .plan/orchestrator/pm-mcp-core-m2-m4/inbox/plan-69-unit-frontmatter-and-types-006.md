envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:26:30Z

# Candidate lesson: violation message kept "start state" after roots became all starting states

Source: review-bot finding b21e31 (coderabbitai, PR #91, Minor, resolved fixed, commit 116238e)
File: pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java:1427

## What happened

The gate-producer check began computing `reachableWithoutProducers` from
`definition.startingStates()` (entries may replace the single start state), but
its violation text still said "a path from the start state without a producer".
`checkReachability` and `checkExitPaths` had been migrated to the
`startingPoint(definition)` helper in the same change; this message was missed.

## Resolution in run

The gate-producer message names the starting point with the same
`startingPoint` helper, so a unit with entries reads from the entry-bound states.

## Candidate rule

When a concept is generalized (start state -> starting states), sweep every
diagnostic message that names the old concept, not only the computations; a
content search for the old phrase across the validator is the cheap check.
