envelope_version=1
sender_type=plan
sender_id=plan-21-entity-addressing
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T15:47:14Z

# Candidate lesson: outline leaf self-transitions out of 3-outline before the outline_prompt question is answered

**Source plan:** plan-21-entity-addressing
**Signal source:** orchestrator-observed event, verified in decision.log (4a0655, a37cf7)
**Suggested component:** plan-marshall:phase-3-outline (and the plan-marshall orchestrator's outline_prompt handling)

## What happened

- The deep-lane outline leaf returned an `outline_prompt` question (q-review-kind) for the orchestrator to ask the operator, but had already transitioned the plan phase out of 3-outline (to 4-plan) before returning.
- The operator answered with a non-recommended option (rename-to-lessons-review instead of keep-review-and-watch), which changes the deliverables.
- The orchestrator had to re-open the phase via a loop-back `set-phase 4-plan -> 3-outline` and re-dispatch the outline leaf once more, which also scheduled a loop-back re-entry marker / handshake re-capture.

## Gap

A leaf that hands an open operator question back to the orchestrator must not mark its phase complete or advance the phase; the transition belongs after the answer is applied. Otherwise every answer that changes the outline costs a forced set-phase re-open and a guarded-boundary re-capture.

## Possible corrective action

In phase-3-outline, when the return carries an outline_prompt (prompt-required envelope), skip the phase transition / step completion and return with the phase still at 3-outline; transition only on the re-dispatch that applies the answer (or when no question is pending).
