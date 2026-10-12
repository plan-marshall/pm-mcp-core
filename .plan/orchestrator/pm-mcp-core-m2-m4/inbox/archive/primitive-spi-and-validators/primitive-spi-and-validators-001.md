envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:50:58Z

component=plan-marshall:phase-3-outline
category=improvement
created=2026-10-10

# Outline quality check reports "no assessment backs any file" when the outline ran no assessment pass

## Source

Quality-check finding 9768ef (phase 3-outline, severity warning) of plan primitive-spi-and-validators, resolved `accepted` by the operator on 2026-10-09.

## What happened

The assessment store of the plan held 0 assessments (`findings_store_state: missing`). The assessment-coverage check therefore reported that none of the 34 write paths and 4 read paths of deliverables 1 to 6 is backed by a CERTAIN_INCLUDE assessment. The check could not confirm any file; it did not show that a file is wrongly in scope. File existence was verified separately and passed.

The operator had to settle a finding that states "the check could not run", not a defect of the outline.

The stored finding detail also carried a wrong number: it says 26 of the write paths are new files; the operator's resolution text corrects this to 21.

## Proposed rule

When the outline was written without an assessment pass, the coverage check has nothing to compare against. It should say "not run, no assessments recorded" as its own outcome instead of filing a per-plan warning the operator must accept, or the outline should record assessments for the existing files it changes or reads.

## Evidence

- Finding 9768ef, resolution detail of 2026-10-09T21:46:01Z.
- Decision log dcd63f: "Starting verification: 6 deliverables, 0 assessments".
- Decision log b511a7: operator accepted 9768ef (no assessment pass at outline).
