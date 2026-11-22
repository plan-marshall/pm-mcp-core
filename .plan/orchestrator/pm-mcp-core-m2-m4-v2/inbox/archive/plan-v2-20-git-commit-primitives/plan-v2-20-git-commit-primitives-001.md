envelope_version=1
sender_type=plan
sender_id=plan-v2-20-git-commit-primitives
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-22T17:21:38Z

component=plan-marshall:phase-6-finalize
category=bug

# Refuse a ci-verify green that counts review bots while the PR is DIRTY

## Context

PLAN-V2-20 (PR #545) recorded ci-verify as "all checks green" while the pull request was in a DIRTY merge state (conflict with main after PLAN-V2-164 landed) and no CI workflow had run for the head. The green came from counting the review-bot status contexts (CodeRabbit, Sourcery, SonarCloud) as CI. The orchestrator noticed and corrected it before merge. The 2026-11-22T10:05Z ci-verify completion also has no record-step row and no decision entry.

## Root cause

The ci-verify check set does not separate review-bot status contexts from workflow checks, and it does not gate on the PR's mergeStateStatus or on at least one workflow run existing for the head SHA.

## Proposed action

In ci-verify (and the ci_complete_precondition it uses): exclude the configured review bots from the CI check population; return a non-green outcome when mergeStateStatus is DIRTY or when no workflow run exists for the head; publish the evaluated check population beside the verdict so a green is never a count of zero workflows.

## Evidence

- aspect: logging-gap-analysis - the 10:05Z ci-verify completion carries no record-step and no decision entry
- aspect: llm-to-script-opportunities - ci-verify bot/CI classification is deterministic and script-able
- orchestrator context: false green caught and corrected during finalize of plan-v2-20-git-commit-primitives
