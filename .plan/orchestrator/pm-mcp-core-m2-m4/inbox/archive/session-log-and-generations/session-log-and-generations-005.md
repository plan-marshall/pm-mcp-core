envelope_version=1
sender_type=plan
sender_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T13:14:40Z

# Candidate lesson: stale target/test-classes after a rebase broke pm-workflow tests

- Source: dispatcher observation (in-run, not a filed finding)
- Plan: session-log-and-generations (PR #51)

## Observation

After a rebase onto main, fixtures that had been deleted upstream were still present as
stale build output under `target/test-classes`. The pm-workflow tests failed until the
module was built with `clean`. The quality gate (`verify -Ppre-commit`) does not clean
first, so a rebase that removes test resources can produce failures that come only from
leftover build output, not from the source.

## Possible lesson

After a rebase or a sync with main that deletes or renames test resources, run the
build with `clean`, or have the quality-gate canonical clean first, before reading a
test failure as a real defect. Candidate owners: plan-marshall:build-maven (canonical
command shape) or the finalize sync-with-main step.
