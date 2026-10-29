envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:37Z

# Candidate: no assessment recorded for doc/plans/README.adoc (deliverable 3)

Source record: quality-check finding `725bd2`, phase `3-outline`, source `qgate`, resolved `taken_into_account` in-run, file `doc/plans/README.adoc`.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

The assessment coverage check (2.2) found 0 `CERTAIN_INCLUDE` assessments, so the one affected file of deliverable 3, `doc/plans/README.adoc`, was not backed by an assessment. The file existed and its row `workspace-confinement` read `open`, as the deliverable stated.

Resolution: an assessment was recorded for `doc/plans/README.adoc`, read on disk; the same record covers the file in the later deliverable 4, and `CLAUDE.md` of deliverable 4 got its own assessment.

## Log evidence

- Decision log `79e4f9`: deliverable 3 flagged by assessment coverage (0 of 1 file) and by a sequencing contradiction (finding `df435a`, sent separately).

## Why it may be a lesson

Third instance of the same gap as findings `264bb2` and `8aa192`: the outline recorded no assessment for any affected file, for documentation files as well as for source files.

Classification is left to the pickup.
