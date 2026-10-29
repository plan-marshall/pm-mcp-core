envelope_version=1
sender_type=plan
sender_id=plan-41-static-verifier-remaining-rules
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T12:16:04Z

# Candidate Lesson: Avoid Complex Lambdas in assertThrows (Sonar S5778)

## Observation
Sonar rule S5778 flags assertThrows calls when more than one invocation occurs inside the lambda (e.g. `assertThrows(..., () -> EntityKindRegistry.create(Set.of("a")))`). `Set.of` is an execution outside the targeted method under test.

## Remediation
Extract auxiliary collections, arguments, and setup calls to local variables outside `assertThrows(...)`.
