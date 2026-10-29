envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:16:13Z

component=plan-marshall:phase-5-execute
category=improvement
source_plan=plan-06-store-substrate
source_record=plan-retrospective aspects request-result-alignment, manifest-decisions

# Re-run plan-mandated verify profiles on every execute re-entry

## Context

PLAN-06's standing constraint requires `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and
`verify -Pintegration-tests` green before done. The manifest's `phase_5.verification_steps` hold only
`verify:quality-gate` and `verify:module-tests`. The first execute exit ran coverage and integration
tests because the task text asked for them. The two finalize fix rounds re-entered execute
(TASK-22..31: journal `format_version` 2, owed audit intents, streaming digests; TASK-32..33) and ran
only the quality gate and `verify`; the freshness check reported `required_coverage_unknown`. The only
post-fix evidence for coverage and integration tests was CI at eeaa7c5.

## Root cause

Verify profiles that a plan's request makes mandatory live in prose, not in the manifest's
verification steps, so a re-entry that runs the manifest steps alone drops them.

## Proposed action

When the request or the project's done rule names extra verify profiles, compose them into
`phase_5.verification_steps` (or a re-entry step list) so every execute exit runs them; at minimum,
have the execute exit refuse "fresh" while `required_coverage_unknown` holds for a plan that mandates
coverage.

## Evidence

- aspect: manifest_decisions - verification_steps [verify:quality-gate, verify:module-tests]
- aspect: request_result_alignment - coverage and integration tests not re-run after the two fix rounds
- chat: last execute exit "could not confirm test coverage either way (required_coverage_unknown)"
