envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:17:15Z

# Candidate lesson: script failure cluster - plan-marshall:plan-marshall:phase_handshake

- Source: script-failure signal (manage-logging signal-scan), marker class `[ERROR] ... script_failure`
- Notation: plan-marshall:plan-marshall:phase_handshake
- The run logged at least one non-zero exit of the phase handshake (a phase transition invariant check refused at least once). The error lines are in the plan work log; the leaf could not filter them out of the 2482-entry log within its budget, so the failing invariant is not quoted here.
- Classification deferred to the orchestrator.
