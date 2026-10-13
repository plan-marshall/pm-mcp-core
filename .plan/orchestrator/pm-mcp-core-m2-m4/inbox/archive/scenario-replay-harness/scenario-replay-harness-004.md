envelope_version=1
sender_type=plan
sender_id=scenario-replay-harness
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:46:31Z

# Candidate lesson: a test compared two hand-kept copies of a specified set, and an ADR claimed it caught drift

Source: CodeRabbit PR #50 inline comment c5bcb9 (fixed in-run, commit f56a7f6)

## What happened

`ScenarioScript.REFUSED` and `ScenarioScriptTest.SPECIFIED_BUT_REFUSED` both copied the Workflow DSL directive set
by hand, and the test compared the two copies with each other. ADR 004 (and the developer doc) stated that the
test notices when the specification adds a directive, but no code read the specification, so such drift would
stay green.

## Fix applied in-run

The test's copy was dropped; its cases now come from `ScenarioScript.REFUSED`, the one list. ADR 004 and the
developer doc no longer claim a comparison with the specification and state that REFUSED is kept by hand and
changes with the specification.

## Candidate rule

Two hand-maintained copies of one externally defined set never test each other; either derive the expected set
from its source at build/test time or keep one copy and say plainly it is kept by hand. And an ADR or doc may only
claim a check that a named test actually performs; the self-review should trace each "the test notices X" claim
to the assertion that does it. The repository's own CodeRabbit path instruction already states the first half.

Likely owner: the traced-implementation / doc-review skills (claim-to-test tracing), pm-mcp-core test standards.
