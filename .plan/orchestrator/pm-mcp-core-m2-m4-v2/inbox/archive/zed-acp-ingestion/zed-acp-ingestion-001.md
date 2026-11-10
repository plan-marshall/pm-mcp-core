envelope_version=1
sender_type=orchestrator
sender_id=zed-acp-ingestion
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-10T12:24:37Z

# Ingestion "The Promise That Killed Atom": two Milestone 4 items for the job runtime and the worker usage

Sender: the ingest-ideas session of 2026-11-10. The specification changes are merged: pm-mcp-core #354 (`1b5dc230`), plan-marshall-documentation #178 (`ca1549b`), pm-mcp-clients #400. Nothing in this epic was edited. Re-read the plans that touch job confinement, the launcher, the usage parsing, and the worker activity against them, and plan the two items below.

## New Milestone 4 items (roadmap of plan-marshall-documentation)

1. Git metadata out of a worker's reach (job-runtime/02 #git-metadata, PM-WATCH-JOB-48): the repository's git directory and each worktree's `.git` link file read-only in a worker's mount namespace on Linux and denied by the sandbox profile on macOS; `git_metadata` in the launch report (job-runtime/01 #launch-report); their digest compared when a worker ends and before every `git` job, `git_metadata_changed` and the plan held on a difference; tracked hook definitions (`.husky/**`, `.githooks/**`, `lefthook*.yml`, `.pre-commit-config.yaml`) as protected paths (phase-workflows/03 #protected-paths), refused before the next commit job. It touches the packages of job confinement and environment and of the launcher's hardening.
2. The model that answered (harness-profiles/01 #served-model, PM-WATCH-CLIENT-20): a required usage pointer `model` in the profile schema, `served_models` in the metrics row (store-schemas/05), the server activity entry `model_differs` (store-schemas/16 #activity-journal), recorded and shown, never acted on. It touches the usage parsing (PLAN-61 token usage lineage) and the worker activity packages, and the shipped `claude` profile (`model` of its `assistant` messages).

## Also changed
- store-schemas/13 #change-size: a capture also records `head`, the commit it measured (for the clients' read of a plan's changes, Milestone 10).
