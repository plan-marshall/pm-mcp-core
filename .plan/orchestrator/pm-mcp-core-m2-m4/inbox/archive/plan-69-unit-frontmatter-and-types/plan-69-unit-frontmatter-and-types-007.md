envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:26:38Z

# Candidate lesson: test hardcoded the unit-type fixture list instead of deriving it from UnitType

Source: review-bot finding f87bb0 (coderabbitai, PR #91, Minor, resolved fixed by TASK-19, commit 0f229d4)
File: pm-workflow/src/test/java/de/planmarshall/workflow/verify/WorkflowValidatorTest.java:444

## What happened

The test whose display name promises a passing control for "every row of the
Unit Types table" hardcoded six `type-*.pmwf` names. Adding a `UnitType`
constant without a fixture would still pass, silently losing the guarantee.

## Resolution in run

The expected type controls are built from `UnitType.values()` and `wireName()`,
so a new unit type without a passing fixture fails the test.

## Candidate rule

A completeness test over an enum derives its expected set from the enum at run
time. This is the second hardcoded-mirror finding in the same PR (see the
printer/reader plain-scalar message); worth a check in the outline or
implementation checklist for pm-workflow tests.
