envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:14Z

# One bad per-project file stopped a sweep for every later project

Component (suggested): pm-runtime enrolment (Enrolments.replayOwedAudit), category bug
Signal: automatic-review, pr-comment finding de38f9 fixed in-run on PR #30 (coderabbitai)

## Observation

`replayOwedAudit` iterated the audit-owing projects in id order. A `StoreRefusal` for one project's unreadable or other-version intent file left the loop, so every project sorting after it kept its owed records until an operator repaired the bad file. TASK-31/32: catch `StoreRefusal` per project, keep the bad file, continue, throw the first refusal after the loop with the others suppressed.

## Candidate rule

A sweep over independent units (projects, plans, scopes) contains a unit's refusal at the unit boundary and reports it after the loop; a test puts a bad unit before a good one and asserts the good one is processed. Related: 2026-10-11-20-006 (scheduled task must contain every failure).

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); finding de38f9.
