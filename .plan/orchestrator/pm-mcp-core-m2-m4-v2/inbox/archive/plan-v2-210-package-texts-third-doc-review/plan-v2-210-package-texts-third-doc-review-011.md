envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:26:19Z

# Batch post-push review findings before replaying the finalize pipeline

- Source: plan-retrospective (aspects plan-efficiency, chat-history-analysis, logging-gap-analysis)
- Component: plan-marshall:phase-6-finalize
- Category: improvement
- Confidence: high

## Context

A docs-only plan (14 AsciiDoc files, 6 deliverables) spent 6.19M of its 7.98M tokens (78%) in 6-finalize. Each review finding after the push (functional review D5.C2, code-review contract drift twice, CodeRabbit ff1860/f421e7, then 94fbb8) caused a loop-back and a full replay of the finalize pipeline: security audit 6 firings, functional review 6, code review 5, automatic review 5. The operator was asked twice whether to keep paying for replays (about 2 h and 2-3M tokens each).

## Root cause

Findings that arrive one per review round each trigger a separate loop-back, and every loop-back re-runs every HEAD-bound step, including steps whose input did not change in a way that matters to them.

## Proposed action

Collect the findings of all reviewers of one round (internal reviews plus bot reviews on the same HEAD) into one triage and one fix envelope before re-entering execute, and let steps whose verdict cannot change on a docs-only delta (security audit over a prose-only footprint) carry their prior verdict instead of re-firing.

## Evidence

- aspect: plan_efficiency — max_phase_token_share=0.78, tokens_per_file_modified=570248, total_tokens_per_deliverable=1330579 (all over fallback thresholds)
- aspect: chat_history_analysis — operator gates "replay cost after 3.7M tokens" and "finding 94fbb8 ... each fix means a full replay"
- status.json phase_steps firing counts: security-audit 6, functional-review 6, code-review 5, automatic-review 5
