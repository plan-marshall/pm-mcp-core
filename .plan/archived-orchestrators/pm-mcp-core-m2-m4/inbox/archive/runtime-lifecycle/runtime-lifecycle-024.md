envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:19:13Z

component=plan-marshall:plan-marshall
category=bug
signal=plan-retrospective chat-history (verified, leaf-reported)
evidence=unified-triage leaf ae43f72: "commit-add rejected deliverable: 0, as triage.md prescribes, with 'Missing required field: deliverable'. Each task is instead tagged with the nearest existing deliverable: 3, 3, 6, 4, 7, 8 and 2"

# Triage workflow prescribes deliverable 0 for fix tasks, but manage-tasks commit-add rejects it

## Observation

`verification-feedback` / triage.md tells the triage leaf to create fix tasks with `deliverable: 0` (finding-originated, not tied to an outline deliverable). `manage-tasks commit-add` treats 0 as missing and refuses it. The leaf worked around it by attributing each of TASK-20..26 to the "nearest" outline deliverable, which silently misattributes fix work in per-deliverable metrics, commit chaining and retrospective deliverable alignment.

## Suggested direction

Pick one contract: either accept `deliverable: 0` (or an explicit `origin: fix` with no deliverable) in commit-add's validation, or change triage.md to require the source deliverable and document how to derive it. Add a contract test that the triage workflow's example payload passes commit-add validation.
