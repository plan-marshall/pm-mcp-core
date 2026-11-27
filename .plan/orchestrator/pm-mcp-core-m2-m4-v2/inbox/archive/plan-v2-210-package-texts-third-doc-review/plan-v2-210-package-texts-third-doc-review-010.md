envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:22:07Z

# Candidate lesson: script failure cluster plan-marshall:manage-findings:manage-findings

- Source: script-failure signal (manage-logging signal-scan), marker class script_failure
- Component: plan-marshall:manage-findings

## Observation

The notation `plan-marshall:manage-findings:manage-findings` produced at least one `[ERROR] ... script_failure` work-log line during this run (5 script_failure lines in total across 3 notations). The run continued and landed.

## Note

Content of the individual failing call is in the plan's work and script logs; classification and any root-cause reading are deferred to the orchestrator.
