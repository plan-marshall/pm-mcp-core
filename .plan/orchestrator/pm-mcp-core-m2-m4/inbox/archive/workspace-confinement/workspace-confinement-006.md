envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:41Z

# Candidate: operator dropped the rule that the documentation pull request names the code pull request

Source record: quality-check finding `88908b`, phase `3-outline`, source `user_review`, resolved `taken_into_account` in-run.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

Operator decision on finding `df435a`: drop the link rule. The documentation pull request in `plan-marshall-documentation` is opened during execution without naming the code pull request; the operator connects the two. The success criterion of deliverable 3 that required the link is removed.

Resolution: deliverable 3 no longer states that the documentation pull request names the code pull request; the decision is recorded in the Approach.

## Log evidence

- Decision log `12b2cd`: "User review: 3 change requests recorded".
- Decision log `bfcf53`: resolution recorded.

## Why it may be a lesson

This is a standing operator answer for work packages of this larger piece of work that touch `plan-marshall-documentation`: the two pull requests are not cross-linked by the plan. A later plan spec that restates the link rule would run into the same contradiction as finding `df435a`.

Classification is left to the pickup.
