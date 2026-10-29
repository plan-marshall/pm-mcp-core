envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:35Z

# Consumer sweep missed a cross-module test asserting the reworded log message

- Source: Q-Gate record ffe995 (4-plan, error, scope_criterion_validator under_coverage, pm-runtime/src/test/.../enrolment/EnrolmentsTest.java), resolution `fixed`.
- Suggested component: `plan-marshall:phase-3-outline` (consumer sweep) / `plan-marshall:phase-4-plan`.

## Observation

Rewording PM_CORE-100 (`CoreLogMessages.WARN.AUDIT_RECORD_DEFERRED`, a pm-core message) broke `EnrolmentsTest.AuditIntent.assertOwedOnce` in pm-runtime, which asserts the old text. The outline's consumer sweep only looked at pm-runtime's own log messages and the pm-core test, so the failure would only have surfaced in the pm-runtime test run of a later task. The scope-criterion validator caught it by operationalising the success criterion as a content search.

## Candidate rule

When a deliverable changes the text of a log message (or any string asserted in tests), the consumer sweep must content-search all test sources of all modules for the old wording, not only the owning module.
