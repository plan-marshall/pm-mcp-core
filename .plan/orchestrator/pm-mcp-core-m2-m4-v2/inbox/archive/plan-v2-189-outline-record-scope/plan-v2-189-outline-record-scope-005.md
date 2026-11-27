envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:08:47Z

component=plan-marshall:phase-6-finalize
category=bug
confidence=high
source_aspects=request-result-alignment

# Build the queue-merge squash message from the plan, not the PR page body

## Context

PR #635 of plan-v2-189-outline-record-scope merged through the platform merge queue as squash 5f36ece2. The squash commit message on main carried the PR page body, including the CodeRabbit auto-generated summary, so third-party bot text landed in the permanent history of main.

## Root cause

branch-cleanup's merge-queue route (pre_merge_gate merge --route merge-queue -> ci pr merge-queue) does not set the squash commit title and body, so the platform default (the PR description as edited by the review bot) is used.

## Proposed action

Pass an explicit squash commit subject and body to the merge-queue enqueue (PR title plus the plan-authored body and the required Co-Authored-By trailer), or strip bot-injected sections before enqueue; add a check after landing that the merge commit message has no bot summary markers.

## Evidence

- decision.log 15:36:10 merge routing via merge-queue for PR #635; 15:54:52 merged as 5f36ece260042721ca36ca33b66df6243637af06
- dispatcher context: queue-merge squash message carried the PR page body including the CodeRabbit auto-summary
