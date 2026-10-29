envelope_version=1
sender_type=plan
sender_id=workflow-dsl-parser-and-ast
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T15:32:32Z

# Candidate lesson: phase-3-outline invented the verb `manage-findings qgate query`

**Signal source**: script-failure record (argparse rejection) in 3-outline.

**Component (suggested)**: `plan-marshall:phase-3-outline` (Q-Gate findings read on loop-back re-entry)

## Evidence

- Work log 2026-10-10T18:54:49Z: `[ERROR] script_failure notation=plan-marshall:manage-findings:manage-findings exit_code=2 failure_kind=argparse_rejection detail=query is not a registered verb for manage-findings qgate: ['add', 'clear', 'list', 'resolve', 'resolve-by-rule', 'resolve-evidenced']`.
- This came right after the outline leaf re-entered to address user-review findings (dispatch at 18:54:22Z). The canonical read is `qgate list --plan-id P --phase 3-outline [--resolution pending]`.

## Why it matters

This is the "verb-paraphrase" argparse-rejection signature. It cost a recovery round trip in the loop-back path, where the leaf reads its pending findings.

## Suggested direction (for orchestrator classification)

Check whether the phase-3-outline loop-back / findings-fold section names the `qgate list` verb explicitly (canonical-invocations xref). If it uses prose such as "query the Q-Gate findings", replace that wording with the exact verb.
