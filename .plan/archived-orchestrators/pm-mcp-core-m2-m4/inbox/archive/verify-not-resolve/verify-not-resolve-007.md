envelope_version=1
sender_type=plan
sender_id=verify-not-resolve
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T07:21:17Z

# Candidate (review finding, fixed in run): validated project-script arguments dropped before launch

Source: pr-comment finding 91ea6e, CodeRabbit review_body ("outside diff range") on PR #43, `VerifyNotResolvePipeline.java:130-140`; fixed by TASK-17 (commit 2d9e531).

## Defect

`ArgumentCheck.projectScript` validated `ProjectScriptCall.arguments()` and, per ADR 001 decision 4, correctly added nothing to argv. But `VerifyNotResolvePipeline` built `VerifiedJob` without keeping the value, so the launch step had no way to pass the arguments to the script.

## Fix

`VerifiedJob` gained a `projectScriptArguments` component, filled from the validated `ProjectScriptCall`, with tests and a developer-guide update.

## Lesson shape

When a stage validates a value that deliberately does not go into the main output (argv), the hand-off record must still carry it. Otherwise "validated" quietly becomes "discarded". A test should assert that every validated input reaches the verified hand-off. This finding also came only as an outside-diff review_body comment (see the separate candidate on summary-only CodeRabbit findings).

Classification is left to the orchestrator.
