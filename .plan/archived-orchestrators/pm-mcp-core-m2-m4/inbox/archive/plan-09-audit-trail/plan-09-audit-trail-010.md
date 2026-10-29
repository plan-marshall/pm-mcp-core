envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:06Z

# Revised outline added files without their CERTAIN_INCLUDE assessments

- Source: Q-Gate record 4d7b10 (3-outline, warning, pm-core/src/test/.../store/EnrolmentStoreTest.java), resolution `fixed` (assessments 1f6f5e, e4f405).
- Suggested component: `plan-marshall:phase-3-outline` (revision path).

## Observation

The revision that resolved f9ad42 added `OwedAuditEvents.java` (read) and `EnrolmentStoreTest.java` (write-replace) to deliverable 2's affected files without recording assessments for them, so validator 2.2 fired again and cost another Q-Gate round. Recurrence of c93d13 within the same plan.

## Candidate rule

The outline revision path must record an assessment for every affected file it adds in the same edit (same rule as c93d13, applied to revisions).
