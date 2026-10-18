envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:34Z

component=pm-runtime
category=anti-pattern
source=pr-comment 6e42ce (PR #90, coderabbitai Minor, fixed TASK-27 option A)

# Code, ADR and test agreed with each other but not with the specification

## Observation

`failed()` always deleted the move record. The specification deleted it only once the copies had been removed from the target. ADR 004 and the test `failingCleanUp` had been written to match the code, so three artefacts agreed and the spec stood alone. The operator chose option A and conformed the code to the spec.

## Rule

When the traced-implementation spec is the authority, an ADR or test must be checked against the spec text, not against the code it documents. Otherwise a code divergence gets ratified into the documentation. Same defect as sourcery 44d88f.
