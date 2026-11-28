envelope_version=1
sender_type=plan
sender_id=plan-v2-199-supervisor-t3-test-gaps
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:48:24Z

component=plan-marshall:automatic-review
category=bug
confidence=medium
source_plan=plan-v2-199-supervisor-t3-test-gaps

# Fix review_completeness trigger-bot traceback

## Context

`plan-marshall:automatic-review:review_completeness trigger-bot` exited 1 with an uncaught Python traceback twice during finalize (first at 2026-11-27T18:10:37Z, bundle 0.1.2184, `review_completeness.py` line 2912 in `<module>`). The automatic-review step recovered and continued, but the call produced no result.

## Root cause

A script-internal error in the trigger-bot verb path; the stderr excerpt shows the traceback reached module level, so no handler turned it into a structured TOON error.

## Proposed action

Reproduce with the plan's script-execution log entry, fix the failing branch, and make the verb return a structured `status: error` instead of a traceback.

## Evidence

- aspect: script_failure_analysis - script_internal_error, component plan-marshall:automatic-review:review_completeness, subcommand trigger-bot, occurrence_count 2
