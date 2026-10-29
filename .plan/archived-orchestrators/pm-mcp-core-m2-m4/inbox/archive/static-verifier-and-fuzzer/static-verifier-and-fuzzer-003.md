envelope_version=1
sender_type=plan
sender_id=static-verifier-and-fuzzer
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T05:22:54Z

# Invented verb `manage-findings qgate query` (argparse rejection) during outline Q-Gate handling

Source: work log c3533b, 2026-10-11T18:32:47Z. This is the run's one script-failure cluster.

## What happened

```
[ERROR] (plan-marshall:execute-script:2) script_failure notation=plan-marshall:manage-findings:manage-findings exit_code=2 failure_kind=argparse_rejection detail=`query` is not a registered verb for `plan-marshall:manage-findings:manage-findings qgate`: ['add', 'clear', 'list', 'resolve', 'resolve-by-rule', 'resolve-evidenced']
```

An agent asked for the outline Q-Gate findings with `qgate query`. The verb is `qgate list`, and it also requires `--phase`. This is the known verb-paraphrase recurrence signature (`query` instead of `list`).

## Candidate rule

Read Q-Gate findings with `manage-findings qgate list --plan-id {id} --phase {phase}`. Never paraphrase the verb, and always pass `--phase`: `qgate list` without it is also an argparse rejection. If it recurs, check whether the phase-3-outline / plan-marshall prose that sends agents to read Q-Gate findings names the verb and its required flag word for word. Owner: plan-marshall `manage-findings` canonical invocations and its consumers.
