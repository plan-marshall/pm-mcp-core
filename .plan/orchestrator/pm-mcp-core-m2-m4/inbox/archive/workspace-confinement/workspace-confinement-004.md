envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:39Z

# Candidate: an execute-phase success criterion required a pull request that the plan opens only at finalize

Source record: quality-check finding `df435a`, phase `3-outline`, source `qgate`, resolved `taken_into_account` in-run, file `doc/plans/README.adoc`.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

Deliverable 3 stated that the edits in `plan-marshall-documentation` are offered as a pull request whose description names the code pull request, and its third success criterion required that this pull request exists and names the code pull request of this plan. The outline's Approach section recorded the operator decision that the plan opens no early draft pull request and opens its code pull request at finalize. Deliverable 3 is an execute-phase deliverable with `execution_mode` automated, so when its criterion is evaluated the code pull request does not exist and cannot be named.

Resolution: per operator decision `88908b` the link rule was dropped; deliverable 3 opens the documentation pull request during execution without naming the code pull request, and the criterion requires only that the pull request exists.

## Log evidence

- Decision log `79e4f9`: "a sequencing contradiction: the documentation pull request must name a code pull request that the plan opens only at finalize".
- Decision log `cf7320`: resolution recorded.

## Why it may be a lesson

A work package that spans two repositories carried a cross-reference rule that is unsatisfiable under the usual flow (code pull request at finalize). The outline wrote the contradiction in after applying the operator's answer on pull request timing; the validator caught it.

Classification is left to the pickup.
