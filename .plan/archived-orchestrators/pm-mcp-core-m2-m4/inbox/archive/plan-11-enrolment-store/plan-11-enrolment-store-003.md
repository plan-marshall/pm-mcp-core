envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:12Z

# Refusal paths bypassed the shared audited-refusal helper

Component (suggested): pm-runtime enrolment (Enrolments, EnrolmentCheck), category bug
Signal: automatic-review, pr-comment findings fixed in-run on PR #30 (coderabbitai), round 5

## Observation

`EnrolmentCheck.recorded` already logged a refusal, appended its audit event and attached a failing sink's `UncheckedIOException` as suppressed. Two refusal paths did not use it:

- 4ea0b3 (`Enrolments.java:175`): `unenrol` appended `unenrol_refused` directly before `throw refusal`; a failing audit sink replaced the promised `ScopeActiveException` with an `UncheckedIOException`.
- e4c989 (`Enrolments.java:229`, CWE-778): `rebaseline` called `fileCheck.check(file)` directly, so an insecure-file refusal was neither logged (PM_MCP-162) nor audited.

Fixed by TASK-34/35: both go through the shared helper; tests use a failing sink and assert the audit event.

## Candidate rule

When a class has a helper that makes a refusal audited and failure-tolerant, every refusal of the same family goes through it; self-review enumerates the throw sites of each refusal type and checks each one uses the helper, with a failing-sink test per site.

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); findings 4ea0b3, e4c989.
