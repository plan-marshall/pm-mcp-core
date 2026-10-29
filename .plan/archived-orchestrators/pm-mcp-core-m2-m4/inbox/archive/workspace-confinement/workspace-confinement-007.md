envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:43Z

# Candidate: operator fixed the refusal form as an outcome value, no checked exception

Source record: quality-check finding `1dcff6`, phase `3-outline`, source `user_review`, resolved `taken_into_account` in-run.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

Operator decision on finding `8605b5`: the confinement check returns the outcome type of `pm-core` carrying `path_traversal_rejected`. No dedicated checked exception, no new exception type.

Resolution: deliverable 2, the overview diagram and the Approach state that a refused path is returned as the `ConfinementOutcome` of `pm-core` carrying `path_traversal_rejected`.

## Log evidence

- Decision log `1fb8c1`: resolution recorded.

## Why it may be a lesson

This is a design decision other work packages of the same larger piece of work will meet again: a refused project path is a value, not an exception. A later plan spec that consumes the confinement check and leaves the refusal form open would reopen a settled question.

Classification is left to the pickup.
