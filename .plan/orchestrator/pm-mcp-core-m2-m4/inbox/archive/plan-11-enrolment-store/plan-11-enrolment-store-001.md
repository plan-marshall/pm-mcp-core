envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:07Z

# Javadoc stated behaviour the code did not have (three times in one PR)

Component (suggested): pm-runtime enrolment (project-local practice), category bug
Signal: automatic-review, pr-comment findings fixed in-run on PR #30 (coderabbitai)

## Observation

Three CodeRabbit findings on PR #30 were the same defect: Javadoc described a mechanism that no code implemented.

- 4f63fe (`Enrolments.java:43`): the class Javadoc said a scope creation reads the enrolment under the same lock; no scope-creation caller existed. TASK-14 restated it as a requirement on the future caller.
- 2c9f79 (`EnrolmentCheck.java:32`): the Javadoc said recovery and every verify-not-resolve check run the check; no production caller existed at that point. Fixed by wiring recovery (EnrolmentIntegrityStep, OwedEnrolmentAuditStep) and stating what is not wired yet.
- a8ae90 (`Enrolments.java:324`): the Javadoc said a project whose records cannot be handed over keeps its sidecar; the code removed it after every non-throwing handover. TASK-48 corrected the Javadoc (round 9).

## Relation to the corpus

Recurrence of active lessons 2026-10-11-20-001 (guarantee in Javadoc shipped without its mechanism) and 2026-10-12-12-003 (docs must describe the enforced mechanism). Candidate for a merge_into / recurrence entry rather than a new lesson.

## Candidate rule

Before the PR, check every sentence in class and method Javadoc that names a caller, a guard or a kept/removed artifact against a production call site or a test that fails without it; state not-yet-wired behaviour as a requirement on the future caller.

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); findings 4f63fe, 2c9f79, a8ae90.
