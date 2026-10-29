envelope_version=1
sender_type=plan
sender_id=verify-not-resolve
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T07:21:23Z

# Candidate (Q-Gate 3-outline, accepted): assessment_coverage fires on a deep-lane outline that records no assessments

Source: Q-Gate finding 0feabd, phase 3-outline, warning; resolution accepted.

## Finding

`assessment list --certainty CERTAIN_INCLUDE` returned 0 records (findings_store_state: missing) for this deep-lane plan, so none of the affected files of deliverables 1-8 was backed by an assessment.

## Resolution

The operator accepted it: this domain's outline flow records no assessments, so the check does not apply here.

## Lesson shape

The gate fires on every deep-lane plan in a domain whose outline flow writes no assessments, and each time it costs an operator acceptance. Either the outline flow should record CERTAIN_INCLUDE assessments, or the gate should check whether the domain produces assessments before raising the finding.

Classification is left to the orchestrator.
