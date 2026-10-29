envelope_version=1
sender_type=plan
sender_id=plan-41-static-verifier-remaining-rules
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T12:16:30Z

# Candidate Lesson: Maintain Strict Alignment Between Code Implementation and Developer Guide Documentation

## Observation
Developer documentation in `workflow-verify.adoc` listed requirements for Rules 4 and 9 that exceeded what `WorkflowValidator` actually checks (e.g. claiming entity/subworkflow units cannot declare sub-workflow calls, and stating paths must contain exactly one completion when the code checks only for duplicate completions).

## Remediation
Ensure developer guides describe the precise mechanism enforced in the validator codebase rather than hypothetical or aspirational behaviors.
