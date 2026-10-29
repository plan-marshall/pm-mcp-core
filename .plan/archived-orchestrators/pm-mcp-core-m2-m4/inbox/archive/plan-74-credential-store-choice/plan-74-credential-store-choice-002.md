envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:22Z

component=pm-runtime
category=bug
source=qgate 6-finalize 6aae3c (code-review, error)

# A transient OS-store outage must not flip the store choice before it is recorded

## Observation

`CredentialStoreChoice.integrityKeyStore()`, called before any store was recorded, re-probed OS-store availability on each call. A transient OS outage flipped the answer between OS and file store, so a second integrity key was created in the other store (CredentialStoreChoice.java:330-335). Fixed by TASK-019 (5916b7c).

## Rule

A derived "which store" answer that has side effects (key creation) must be decided once and persisted, not re-derived per call from a probe that can flap. Probe results are inputs to a recorded decision, never the decision itself.
