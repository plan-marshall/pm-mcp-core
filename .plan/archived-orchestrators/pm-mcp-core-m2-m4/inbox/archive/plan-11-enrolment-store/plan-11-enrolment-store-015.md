envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:24:04Z

# The trace matrix ran as an execute task and changed the design after the outline was approved

Component (suggested): pm-mcp-core planning practice for traced-implementation work packages, category improvement (project-local)
Signal: plan-retrospective, chat-history and request-result-alignment aspects

## Observation

Deliverable 1 (traced-implementation trace matrix) was planned as TASK-1 of 5-execute. It found contradiction C1 (the specs require the re-baseline to show size and SHA-256, but the sidecar was a bare HMAC) and gaps G1-G3 (second refusal outcome `enrolment_insecure`, operator command in the refusal, extra requirement links). The operator took all four, which added `EnrolmentSidecar` as a closed record, changed D2/D3/D4/D6/D8 and rewrote ten task descriptions mid-execute. The outline and the Q-Gate had approved a design the matrix then invalidated.

## Candidate directive

For work packages that follow traced-implementation, build the trace matrix during refine or outline (it reads only documentation) so contradictions and gaps reach the outline and its Q-Gate, not the execute phase.

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); session transcript, TASK-1 escalate_ask (reason trace_matrix_contradiction).
