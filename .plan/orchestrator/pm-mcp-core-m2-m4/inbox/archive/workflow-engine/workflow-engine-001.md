envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T06:39:50Z

# Candidate lesson: ci-verify producer string rejected by verification-feedback

- Component: plan-marshall:phase-6-finalize (ci-verify.md) / plan-marshall:plan-marshall (workflow/verification-feedback.md)
- Suggested category: bug
- Signal: script/contract failure in phase 6-finalize

## Observation

ci-verify returned the producer `ci-verify-policy`. ci-verify.md says the dispatcher runs one
verification-feedback dispatch per ci-verify producer string, but the producer guard of
verification-feedback rejected it with `unknown_producer`. The run re-dispatched with
`producer=finalize-feedback`, which was accepted.

## Evidence

- decision.log 2026-10-12T19:38:21Z (plan-marshall:phase-6-finalize): "ci-verify triage dispatch with
  producer=ci-verify-policy rejected by verification-feedback (unknown_producer); re-dispatching with
  producer=finalize-feedback. Contract mismatch: ci-verify.md says one dispatch per ci_verify producer
  string, verification-feedback accepts none of them."

## Suggested correction

Make the producer vocabulary one set: either verification-feedback accepts every producer string
ci-verify can emit, or ci-verify.md names the producer the dispatcher must pass (finalize-feedback).
A test that compares the two vocabularies would keep them aligned.
