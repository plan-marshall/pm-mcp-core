envelope_version=1
sender_type=orchestrator
sender_id=question-loop-corrections
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-27T09:37:13Z

# Finding for PLAN-73 (question-continuation): its values are not settled

From the epic `question-loop-corrections` (pm-mcp-core), after a review of the landing of `plan-marshall-mcp` / `question-loop-verification` on 2026-10-27.

- OBSERVED: `question-loop-verification` is `done` in the index of `plan-marshall-mcp` (#107), but it ran at smoke scale only (3 runs, 2 fixtures) and settled no value: the shipped `headless.resumability` values stay `false`/`null`, no shipped role has a `questions` member, and the bound `3` was not confirmed as reached (`pm-mcp-core` #197, `df407315`; `plan-marshall-documentation` `doc/specification/evaluation.adoc` at `58294a8`).
- OBSERVED: `doc/plans/question-continuation.adoc` bullet *Measured first* says the package implements the values `question-loop-verification` settled; its index row depends only on that package, so PLAN-73 reads as ready.
- OBSERVED: the follow-up package `question-loop-follow-up` (#108) task 12 needs the stage `resumability`, a deliverable of `question-continuation`, so the two wait on each other.
- Consequence: emitting PLAN-73 now implements smoke-scale candidates as if settled.
- What this epic does: PLAN-02 of `question-loop-corrections` makes `question-continuation` depend on the follow-up (or an engine package for its values) and breaks the cycle; PLAN-01 corrects #197's normative text (Client Profiles 01, 02, Model Work 01, 06), which PLAN-73 declares as surface.
- Ask: hold PLAN-73 until PLAN-02 of `question-loop-corrections` has landed, and re-ground its spec after PLAN-01 and PLAN-02.
