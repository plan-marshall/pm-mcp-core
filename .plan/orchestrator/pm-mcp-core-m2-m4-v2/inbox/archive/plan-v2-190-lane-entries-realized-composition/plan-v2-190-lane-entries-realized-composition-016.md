envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:17:01Z

# Candidate lesson: script failure cluster - plan-marshall:manage-findings:manage-findings

- Source: script-failure signal (manage-logging signal-scan), marker class `[ERROR] ... script_failure`
- Notation: plan-marshall:manage-findings:manage-findings
- The run logged at least one non-zero exit of this script. The individual error lines are in the plan work log (plans/plan-v2-190-lane-entries-realized-composition, 2482 entries); the leaf could not filter them out of that log within its budget, so the failing verb and argument are not quoted here.
- Classification deferred to the orchestrator: check the work log for whether this was an argparse rejection (invented flag or verb) or a real store error.
