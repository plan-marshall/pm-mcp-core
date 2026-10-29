envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T06:40:22Z

# Candidate lesson: manage-solution-outline get-deliverable called with `--number`

- Component: plan-marshall:manage-solution-outline
- Suggested category: anti-pattern
- Signal: script failure cluster (argparse_rejection, 2 occurrences, phase 5-execute)

## Evidence

- work.log 2026-10-12T17:11:13Z and 17:11:14Z: script_failure notation=plan-marshall:manage-solution-outline:manage-solution-outline
  exit_code=2: "`--number` is not declared for ... get-deliverable: ['deliverable-number', 'plan-id']".

## Suggested correction

The flag is `--deliverable-number`. Find where `--number` is suggested (task or execute workflow
prose) and fix it.
