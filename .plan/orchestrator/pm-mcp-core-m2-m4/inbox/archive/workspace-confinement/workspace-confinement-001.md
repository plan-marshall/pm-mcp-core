envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:30Z

# Candidate: outline reported its quality check as passed with an empty assessment store (deliverable 1)

Source record: quality-check finding `264bb2`, phase `3-outline`, source `qgate`, resolved `taken_into_account` in-run.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

The assessment coverage check (2.2) found the plan's assessment store empty (0 `CERTAIN_INCLUDE` assessments, store state `missing`), so none of the 7 affected files of deliverable 1 was backed by an assessment. Every other check passed for those files.

Resolution: a `CERTAIN_INCLUDE` assessment was recorded for each of the 7 files; the two existing files were read on disk, the five write-new files were absent as declared.

## Log evidence

- Work log `c974eb` (21:33:15Z): the outline phase closed with "3 deliverables, Q-Gate: pass".
- Decision log `0d62e9` (21:34:17Z): the validator started with "3 deliverables, 0 assessments".
- Decision log `dcd103`: deliverable 1 flagged only by assessment coverage, 0 of 7 files assessed.
- Decision log `46c126`: validator summary "0 passed, 3 flagged".

## Why it may be a lesson

The outline phase wrote no assessment at all in two dispatches and still reported its own check as passed; the gap surfaced only in the separate validation hand-off and cost one further outline dispatch. Findings `8aa192` and `725bd2` are the same gap for deliverables 2 and 3 and are sent as their own messages.

Classification is left to the pickup.
