envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:38Z

component=plan-marshall:manage-solution-outline
category=anti-pattern
signal=script-failure-cluster
evidence=work.log 2026-10-11T16:21:11Z [ERROR] script_failure notation=plan-marshall:manage-solution-outline:manage-solution-outline exit_code=2 failure_kind=argparse_rejection

# get-deliverable invoked with --number instead of --deliverable-number

## Observation

A phase-5 leaf called `manage-solution-outline get-deliverable --number N`. The parser declares only `--deliverable-number` and `--plan-id`: "`--number` is not declared for `plan-marshall:manage-solution-outline:manage-solution-outline get-deliverable`: ['deliverable-number', 'plan-id']". Exit 2, retried with the canonical flag.

## Why it matters

This is the typed-ID flag paraphrase signature (`--number` for `--deliverable-number`, like `--id` for `--lesson-id`). The rule exists in persona-plan-marshall-agent, but the call site that prompted the paraphrase is worth finding: if any phase-5 workflow prose or example says "deliverable number" next to a `get-deliverable` call without quoting the flag, it invites this rejection. Either quote the canonical invocation verbatim at that call site, or consider whether a `--number` alias is warranted.
