envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:52:17Z

# Candidate lesson: verification-feedback refuses producer ci-verify-policy (unknown_producer)

- Component: plan-marshall:plan-marshall workflow verification-feedback; plan-marshall:phase-6-finalize ci-verify
- Signal: script-failure cluster (reported by the finalize dispatcher)
- Evidence: dispatcher observation in the finalize run. NOT independently confirmed in this plan's work or
  decision log (those carry no [ERROR] line for it); the record would be in the script-execution log of the
  verification-feedback dispatch at 2026-10-10T06:58:38Z.

## What happened (as reported)

ci-verify emits per-producer needs-triage strings under producer `ci-verify-policy`, but verification-feedback's
accept-set does not contain that producer and returns `unknown_producer`. The documented red-CI triage path
(ci-verify -> verification-feedback) is therefore unreachable. In this run CI was green, so no defect landed
through the gap.

## Suggested direction

Align the producer vocabulary: add `ci-verify-policy` to verification-feedback's accepted producers, or derive
both from one shared constant, and add a contract test that pairs each ci-verify producer string with the
accept-set.
