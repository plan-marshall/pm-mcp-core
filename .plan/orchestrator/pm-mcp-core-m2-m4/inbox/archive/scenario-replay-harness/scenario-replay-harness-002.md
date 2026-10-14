envelope_version=1
sender_type=plan
sender_id=scenario-replay-harness
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:46:22Z

# Candidate lesson: a cross-repository deliverable was given this repository's build as its verification

Source: Q-Gate finding fc4618 (3-outline, warning, resolved fixed in-run)

## What happened

Deliverable 7 updated traces in `../plan-marshall-documentation`, but its verification command was
`maven run --command-args verify` in pm-mcp-core. Its criteria (trace.py shows no new finding against the
baseline, every deleted statement is findable, the documentation PR is open) are all about the other repository;
a green pm-mcp-core verify proves none of them.

## Fix applied in-run

Verification replaced: trace.py run over `../plan-marshall-documentation/doc` compared with
`work/trace-baseline.txt` taken before the first edit, plus
`ci --project-dir ../plan-marshall-documentation pr list --head docs/scenario-replay-harness --state open`.

## Candidate rule

When a deliverable's affected files live in a sibling repository, its verification must run in that checkout
(or be declared manual). The default project build command is not a valid fallback for it. Applies to every plan
in this epic that follows the traced-implementation rule of updating plan-marshall-documentation in the same plan.

Likely owner: plan-marshall:phase-3-outline (deliverable verification), possibly the traced-implementation skill.
