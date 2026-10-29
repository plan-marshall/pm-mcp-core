envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:57:02Z

# `architecture search` misused twice: router flag order and sibling-verb `--path`

- Source: script-failure cluster `plan-marshall:manage-architecture:architecture` — work-log `[ERROR] ... script_failure` 9eb4c3 (2026-10-14T13:12:54Z) and d7dbc3 (13:15:23Z), phase 5-execute, TASK-016 envelope.
- Suggested component: `plan-marshall:manage-architecture` (client-api.md § search) / `plan-marshall:persona-plan-marshall-agent` (search guidance).

## Observation

1. `architecture.py --plan-id plan-09-audit-trail search --content --pattern AUDIT_ROTATION` -> argparse rejection: `--plan-id` placed before the verb, but `search` declares `--plan-id` on the subcommand (the CI-router habit applied to a script that takes it after the verb).
2. `architecture search ... --path ...` -> "`--path` is not declared for `architecture search`: [category, content, ignore-case, literal, pattern, plan-id, pre, pre-ref, project-dir] — flag `--path` is declared on sibling verb(s): `which-module --path`". The agent tried to scope a content search to one file/directory.

## Candidate rule

Both are documented signatures (verb-scoped `--plan-id` position; sibling-verb flag). The second also hints at a real capability gap: there is no path/module scope on `search --content` (only `--category`), so agents reach for `--path`. Either document "scope by `--category`, then Read the known file" in client-api.md § search, or consider a `--module` scope on `search`.
