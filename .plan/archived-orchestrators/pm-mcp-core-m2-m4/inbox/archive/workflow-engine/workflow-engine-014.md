envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T06:40:28Z

# Candidate lesson: specification correction scheduled after the code that depends on it

- Component: plan-marshall:phase-3-outline
- Suggested category: improvement
- Signal: Q-Gate finding d1810d (3-outline, taken_into_account)

## Evidence

- The Approach said the specification correction (retry_counters in
  store-schemas/02-workflow-state-store.adoc) is made before the codec. But the correction sat in the
  documentation deliverable 8, which depended on deliverables 1-6, so task planning would have
  scheduled it after the code.
- Resolution: the correction and the retry-key survey were split into their own deliverable 2
  (no dependencies), and the store deliverable depends on it.

## Suggested correction

The traced-implementation rule "correct the specification first" needs its own deliverable in the
outline, with the code deliverables depending on it. It must not be folded into the closing
documentation deliverable.
