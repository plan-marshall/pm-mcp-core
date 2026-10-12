envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:40Z

# Candidate: a design choice deferred to implementation time changed the deliverable's file set

Source record: quality-check finding `8605b5`, phase `3-outline`, source `qgate`, resolved `taken_into_account` in-run, file `pm-runtime/src/main/java/de/planmarshall/runtime/workspace/WorkspaceConfinement.java`.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What the record says

The change description of `WorkspaceConfinement.java` deferred to implementation time whether a refusal is handed to the caller as the outcome value or thrown as a dedicated checked exception. The two readings have different file sets: a dedicated checked exception is a new public type of `de.planmarshall.runtime.workspace`, and no affected-file entry and no success criterion of deliverable 2 covered it. The listed set was complete only for the outcome-value reading.

Resolution: per operator decision `1dcff6` deliverable 2 fixes the outcome-value form: `WorkspaceConfinement` returns the `ConfinementOutcome` of `pm-core`, declares no checked exception and creates no exception type; a success criterion covers it.

## Log evidence

- Decision log `b66270`: deliverable 2 flagged "by an open design choice that changes the file set".
- Decision log `45e008`: resolution recorded.

## Why it may be a lesson

The source spec left the refusal form open and the outline carried the open choice forward instead of asking; the scope and file-set check (2.12) caught it because one of the two readings adds a file.

Classification is left to the pickup.
