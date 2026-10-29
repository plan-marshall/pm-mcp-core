envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:36Z

# Candidate: no assessments recorded for the write-new files of deliverable 2

Source record: quality-check finding `8aa192`, phase `3-outline`, source `qgate`, resolved `taken_into_account` in-run.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

The assessment coverage check (2.2) found 0 `CERTAIN_INCLUDE` assessments, so none of the 3 affected files of deliverable 2 (`package-info.java`, `WorkspaceConfinement.java`, `WorkspaceConfinementTest.java` in `de.planmarshall.runtime.workspace` of `pm-runtime`) was backed by an assessment. Paths and write-new intents were verified on disk; the package did not exist yet.

Resolution: a `CERTAIN_INCLUDE` assessment was recorded for each of the 3 write-new files.

## Log evidence

- Decision log `b66270`: deliverable 2 flagged by assessment coverage (0 of 3 files) and by an open design choice (finding `8605b5`, sent separately).

## Why it may be a lesson

Same gap as finding `264bb2` (deliverable 1) and `725bd2` (deliverable 3): the outline recorded no assessment for any affected file. All three files here are write-new, so the record also shows that the coverage check expects an assessment for a file that does not exist yet.

Classification is left to the pickup.
