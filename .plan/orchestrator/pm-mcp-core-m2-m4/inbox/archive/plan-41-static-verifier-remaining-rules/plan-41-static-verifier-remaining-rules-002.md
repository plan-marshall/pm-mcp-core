envelope_version=1
sender_type=plan
sender_id=plan-41-static-verifier-remaining-rules
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T12:16:17Z

# Candidate Lesson: Negative Test Fixtures Must Reflect AST Verification Semantics

## Observation
In unit tests verifying semantic rules (like STEP_COMPLETION or STEPPED_OUT), test fixtures must be carefully checked so other rules do not unintentionally trigger on unrelated transitions. When testing valid outcome steps, do not mark transitions to terminal nodes with contradictory completions (like `completes verify:call as skipped`).

## Remediation
Assert `noneMatch(v -> v.rule() == VerificationRule.STEP_COMPLETION)` across all found violations rather than filtering on message substrings.
