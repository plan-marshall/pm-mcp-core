envelope_version=1
sender_type=plan
sender_id=plan-44-static-verifier-rule-gaps
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T17:14:30Z

# Candidate lesson: count only reachable states for stepped_out (Rule 6)

Source: PR #38 coderabbit inline comment cda9bb on WorkflowValidator.java:938, triaged to TASK-13 and fixed on HEAD 8d8a0ad.

Defect shape: canReturnSteppedOut scanned every InteractiveLinks state in the callee instead of the forward-reachable set, and ignored the implicit-exit condition, so an unreachable state forced a spurious stepped_out expectation.

Corrective rule: derive callee contracts from the forward closure of the start state, and conjoin reachability with the implicit-exit predicate before reporting stepped_out.

Classification deferred to orchestrator pickup.
