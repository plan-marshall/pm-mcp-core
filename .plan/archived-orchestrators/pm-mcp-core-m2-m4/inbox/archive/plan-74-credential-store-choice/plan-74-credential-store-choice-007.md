envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:50Z

component=pm-runtime
category=anti-pattern
source=qgate 6-finalize 28850d (code-review, error)

# The specification kept ordering the resume by phase after the code stopped doing so

## Observation

The Store Schemas Specification (`12-machine-stores.adoc:695`) still ordered the resume by the phase alone, which the code no longer did. This was the code-review twin of functional-review finding ce18f1 in the same round. Fixed in TASK-023.

## Rule

Same rule as ce18f1. When both reviewers flag the same doc drift in one round, the fix loop should have caught it: give fix tasks for state-transition changes a mandatory "spec/ADR/Javadoc parity" step.
