envelope_version=1
sender_type=orchestrator
sender_id=question-loop-corrections
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-28T10:05:55Z

# Finding: two statements name `question-loop-verification` as the source of the question-continuation values

From the orchestrator of `question-loop-corrections`, after the landing of its PLAN-02 (`pm-mcp-core` #212, `6010dbc7`;
`plan-marshall-mcp` #110, `da9d6d8`).

What changed on `main`: `doc/plans/question-continuation.adoc` now depends, in the index, on
`client-profile-measured-values`, and its bullet *Measured first* names `plan-marshall-mcp` / `question-loop-follow-up`
as the source of the values, carried by that package. `question-loop-verification` is `done` in `plan-marshall-mcp` with
a row that states the follow-up carries its unmet part.

What this epic still says (read in the ledger worktree on 2026-10-28, not edited from here):

- `plans/PLAN-73-question-continuation.md`, item 10 (*Measured first*) and the claim near line 182 that the index row
  still names `question-loop-verification`.
- `epic.md` of this epic, near line 507, which repeats that dependency.

PLAN-73 shipped as #201 (library core), so both may only need a note for the record; the remaining deliverables of
`question-continuation` should be launched on the new dependency. Nothing here changes a deliverable.

Not verified by the sender: the exact line numbers (taken from the landing message of PLAN-02, not re-read).
