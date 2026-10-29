envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:17Z

# Store listing kept its own copy of the project-id grammar

Component (suggested): pm-core store (EnrolmentStore.projectIds), category improvement
Signal: automatic-review, pr-comment finding c95a6c fixed in-run on PR #30 (coderabbitai)

## Observation

`file()` validated project ids with `LockKey.identifier`, while `projectIds()` selected files with its own `FILE_NAME` regex. The grammars matched at the time, but a later change to `LockKey.identifier` would make an id resolvable by the store yet missing from the listing. TASK-13 made the listing use `LockKey.identifier`.

## Relation to the corpus

Same family as 2026-10-11-20-001 ("validation patterns for the same value must come from one constant") and 2026-10-11-07-009 (mirrored literals between writer and matcher). Candidate for a recurrence entry rather than a new lesson.

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); finding c95a6c.
