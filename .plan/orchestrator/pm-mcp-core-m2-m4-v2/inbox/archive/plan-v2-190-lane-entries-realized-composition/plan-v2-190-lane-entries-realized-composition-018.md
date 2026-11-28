envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:17:10Z

# Candidate lesson: script failure cluster - plan-marshall:manage-status:manage-status

- Source: script-failure signal (manage-logging signal-scan), marker class `[ERROR] ... script_failure`
- Notation: plan-marshall:manage-status:manage-status
- The run logged at least one non-zero exit of this script. The error lines are in the plan work log; the leaf could not filter them out of the 2482-entry log within its budget, so the failing verb is not quoted here.
- Context: the same signal scan reported boundary causes budget_yield x2, verification_handback x1 (not counted), clean_exit_queue_empty x1.
- Classification deferred to the orchestrator.
