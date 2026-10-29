envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:57:43Z

# Outline authored without the assessment pass (no CERTAIN_INCLUDE assessments)

- Source: Q-Gate record c93d13 (3-outline, warning), resolution `taken_into_account`.
- Suggested component: `plan-marshall:phase-3-outline` (deep lane).

## Observation

The first outline (6 deliverables, deep lane) was written with an empty assessment store (`findings_store_state: missing`), so validator 2.2 found no CERTAIN_INCLUDE assessment backing any affected file. The author then recorded one assessment per file after the fact. A follow-up record (4d7b10) shows the same gap recurring when the revised outline added two more files.

## Candidate rule

The deep-lane outline should record its CERTAIN_INCLUDE assessments while selecting affected files, not after Q-Gate asks for them; and every outline revision that adds an affected file must add its assessment in the same edit.
