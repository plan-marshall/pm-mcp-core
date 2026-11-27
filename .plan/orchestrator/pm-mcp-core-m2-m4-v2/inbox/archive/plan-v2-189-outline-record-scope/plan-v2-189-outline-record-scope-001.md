envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:08:10Z

component=plan-marshall:phase-6-finalize
category=improvement
confidence=high
source_aspects=chat-history-analysis,request-result-alignment,llm-to-script-opportunities

# Carry a functional-review operator override into code-review's may-close check

## Context

In plan-v2-189-outline-record-scope the functional review blocked on four removed-assertion findings (tests that pinned members the plan deletes). The operator accepted them (operator_override). The code review then raised the same four findings at the same HEAD (99f905f2) and blocked again, because its may-close check cannot see the earlier acceptance. The operator had to answer the identical question a second time.

## Root cause

Operator-override acceptance is stored per step (phase_steps.functional-review.facts.acceptance) and not keyed by finding fingerprint and HEAD, so a later review gate re-derives the same blocking set from scratch.

## Proposed action

Persist override acceptances keyed by finding fingerprint plus HEAD, and have code-review's may-close read them: a finding already accepted at the same HEAD is pre-accepted and not re-prompted. Re-prompt only for new findings or a moved HEAD.

## Evidence

- status.json: functional-review and code-review both display "operator close: 4 finding(s) accepted", firing_count 2, same head_at_completion 99f905f2
- chat: second operator question says "Its own check can't see your earlier acceptance, so it blocks again"
