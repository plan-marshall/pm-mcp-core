envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T06:40:08Z

# Candidate lesson: manage-findings qgate called with an invented verb `query`

- Component: plan-marshall:manage-findings
- Suggested category: anti-pattern
- Signal: script failure cluster (argparse_rejection, 2 occurrences, phase 3-outline)

## Evidence

- work.log 2026-10-12T15:47:15Z and 15:58:13Z: script_failure notation=plan-marshall:manage-findings:manage-findings
  exit_code=2 failure_kind=argparse_rejection: "`query` is not a registered verb for ... qgate:
  ['add', 'clear', 'list', 'resolve', 'resolve-by-rule', 'resolve-evidenced']".
- The same wrong call was made twice, 11 minutes apart. The rejection did not prevent the repeat.

## Suggested correction

Find the doc or prompt that names `qgate query` and change it to `qgate list`.
