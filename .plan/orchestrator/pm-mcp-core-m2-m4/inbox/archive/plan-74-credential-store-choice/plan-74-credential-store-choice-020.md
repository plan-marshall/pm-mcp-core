envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:47Z

component=plan-marshall:manage-solution-outline
category=bug
source=script-failure cluster, work log c471c4 x2 (2026-10-17T15:22:19Z and 15:36:24Z, both inside q-gate-validation dispatches)

# q-gate-validation calls `manage-solution-outline deliverable-hashes`, which does not exist

## Observation

Both q-gate-validation dispatches (after phase-3 and after phase-4) called `manage-solution-outline deliverable-hashes` and were rejected with argparse_rejection exit 2. The registered verbs are exists, get-deliverable, get-field, get-module-context, list-deliverables, read, resolve-path, update, validate and write. Two independent dispatches failed on the same call, so the cause is probably in the workflow doc, not in agent invention.

## Hypothesis

Either `plan-marshall/workflow/q-gate-validation.md` names a verb the script lacks, or the project executor is stale (see the stale-executor candidate from this plan) and maps an older script than the workflow docs expect. Check the workflow text against the current `manage-solution-outline` argparse.
