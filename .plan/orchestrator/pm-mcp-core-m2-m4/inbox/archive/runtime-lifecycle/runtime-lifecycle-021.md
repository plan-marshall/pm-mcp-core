envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:19:10Z

component=plan-marshall:manage-solution-outline
category=improvement
signal=plan-retrospective llm-to-script (verified)
evidence=3-outline Q-Gate leaf a4b20e5: "work/deliverable-hashes.toon was not created. Producing it means hashing solution_outline.md from the shell, and the rules forbid reading .plan/ files that way"; 4-plan Q-Gate leaf ac4901c: "I computed the hashes with a scratchpad helper script, because the workflow documents no hashing command" (scratchpad/hash_outline.py)

# No script verb computes the per-deliverable outline hashes the Q-Gate workflow requires

## Observation

The q-gate validation workflow expects `work/deliverable-hashes.toon` (per-deliverable fingerprints plus a `__whole_outline__` row) so a re-run can skip unchanged deliverables. No `manage-*` verb produces it. On this plan one leaf skipped the step (forcing a full re-check next time), and the next leaf improvised an ad-hoc scratchpad Python script, which is exactly the hand-made-helper pattern the workflow rules forbid.

## Suggested direction

Add a deterministic verb (e.g. `manage-solution-outline hash-deliverables --plan-id X`) that parses the outline's `### N.` deliverable sections, hashes each plus the whole document, and writes the TOON artifact; point the q-gate workflow step at it.
