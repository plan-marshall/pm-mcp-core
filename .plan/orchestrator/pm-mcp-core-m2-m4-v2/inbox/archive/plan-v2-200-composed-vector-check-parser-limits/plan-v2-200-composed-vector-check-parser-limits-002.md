envelope_version=1
sender_type=plan
sender_id=plan-v2-200-composed-vector-check-parser-limits
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T21:26:27Z

component=plan-marshall:automatic-review
category=improvement
title=Carry or answer pending findings of a PR closed by close-and-reopen

# Carry or answer pending findings of a PR closed by close-and-reopen

## Context

In plan-v2-200-composed-vector-check-parser-limits automatic-review Branch 5 closed PR 638 and opened PR 641 to re-request a CodeRabbit review. Five pr-comment findings filed on PR 638 (three Sourcery inline comments, the Sourcery review body, the SonarCloud gate comment) were later triaged and resolved in the store (one rejected, one fixed via TASK-8, three taken into account), but the respond pass skipped all five as belongs_to_pr_638, so no reply or thread resolution reached GitHub — including the rejection verdict and the fix.

## Root cause

Close-and-reopen re-binds pr_number but leaves the old PR's findings bound to the closed PR, and the respond pass only answers on the current PR.

## Proposed action

On close-and-reopen either re-home the pending findings to the replacement PR (with a link to the original thread) or have the respond pass post on the closed PR; at minimum surface the unanswered set in the step's return.

## Evidence

- triage hand-back: "Skipped, belongs_to_pr_638: 4c1a6b, 6f6039, 9a2a38, 3b560c and 01d2fb"
- second triage: "The 8 skipped rows were 5 findings belonging to PR 638 and 3 already responded"
- automatic-review iteration 5 reported foreign_stored_findings: 5
