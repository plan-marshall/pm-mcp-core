envelope_version=1
sender_type=plan
sender_id=verify-not-resolve
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T07:21:47Z

# Candidate (Q-Gate 4-plan, fixed): stale Javadoc claim in a package-info.java missed by the deliverable's affected files

Source: Q-Gate finding fbdfac, phase 4-plan, scope_criterion_validator under_coverage for deliverable 2; resolution fixed.

## Finding

Deliverable 2 corrected the claim in EnrolmentCheck.java that the verify-not-resolve check does not run it yet. A content search for `verify-not-resolve` found the same claim in `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/package-info.java` (lines 27-31). That file was not in the affected files, and the claim would go stale.

## Resolution

package-info.java was added to deliverable 2 (write-replace, Javadoc only) and as TASK-3 step 8.

## Lesson shape

When a deliverable corrects a documented claim, search for every copy of the claim. package-info.java files restate class behaviour and are easy to miss. The outline author should run that content search, rather than leaving it to the gate.

Classification is left to the orchestrator.
