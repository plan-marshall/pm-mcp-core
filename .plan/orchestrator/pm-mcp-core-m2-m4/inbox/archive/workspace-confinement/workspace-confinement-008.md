envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:44Z

# Candidate: an operator answer with two readings was planned on one reading only; the operator wanted both

Source record: quality-check finding `931308`, phase `3-outline`, source `user_review`, resolved `taken_into_account` in-run.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

The operator clarified the documentation answer: keep the planned split (code documentation in this repository; specification and requirement documents in `plan-marshall-documentation` updated directly by this plan) AND add a fourth deliverable that rewords `CLAUDE.md` and the section "How to Work a Package" of `doc/plans/README.adoc` so they state that split and no longer require claiming a package with a draft pull request. This is an explicit operator decision to change `CLAUDE.md` and that README section beyond the package's own row.

Resolution: deliverable 4 was added with `CLAUDE.md` and `doc/plans/README.adoc` as affected files; deliverable 3 was adjusted so its criteria on `README.adoc` leave room for that section.

## Log evidence

- Decision log `a9a215` (21:33:12Z): the outline applied the answer with the note "reading: orchestrator's; alternative reading 'update convention documents to state the split' not planned".
- Decision log `1e525b`: deliverable 4 added after the operator's review.
- Work log `6575ac`: the outline went from 3 to 4 deliverables.

## Why it may be a lesson

The outline saw that the answer had two readings, picked one, recorded the other as not planned, and did not ask. The operator's review restored the other reading as an additional deliverable, at the cost of a further outline dispatch.

Classification is left to the pickup.
