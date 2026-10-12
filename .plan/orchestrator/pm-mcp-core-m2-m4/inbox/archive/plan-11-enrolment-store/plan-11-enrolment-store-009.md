envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:24Z

# Triage passes --task-number to manage-findings resolve, which does not declare it

Component (suggested): plan-marshall:plan-marshall (verification-feedback / wait-region-unified-triage workflow) or plan-marshall:manage-findings, category bug (marketplace-owned)
Signal: script-failure cluster, notation plan-marshall:manage-findings:manage-findings

## Observation

In every triage round that allocated fix tasks, the leaf called `manage-findings resolve` with `--task-number` and was rejected (work log hash 1c6573): declared flags are only detail, hash-id, plan-id, resolution. Occurrences on 2026-10-12: 5 at 07:44, 10 at 09:34, 2 at 11:02, 1 at 12:01. The leaf recovered each time by retrying without the flag (the resolutions carry "Will be addressed by TASK-n" in the detail text instead).

## Candidate directive

Either add an optional `--task-number` to `manage-findings resolve` (recording the linked fix task as a structured field), or make the triage workflow document the canonical form and state that the task link goes into `--detail`. A structured link would also let finalize verify that every `fixed` finding has a completed task.

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); work log 1c6573.
