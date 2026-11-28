envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:42Z

# Candidate lesson: doc/Traceability.adoc edited by the notes but declared by no deliverable

- Source: Q-Gate 3-outline, scope_criterion_validator (under_coverage), hash ced42d, severity warning, resolution accepted
- File: doc/Traceability.adoc
- Finding: the outline's Notes for execution (R08/R19) say doc/Traceability.adoc gets the lines of the implemented requirements and links the new ADRs (core-store/091, workflow-verify/044, workflow-plan/029), but no deliverable lists the file in Affected files, so the phase-4 declared-set closure cannot place a step on it.
- Disposition: accepted - Traceability.adoc is shared bookkeeping the repository CLAUDE.md requires every plan to update in its own PR; it is updated during execute/finalize, not declared surface.
- Observation: the scope validator flags this repository-wide bookkeeping file on every plan whose notes mention it. Either the outline should declare Traceability.adoc in the deliverable that adds the ADRs, or the validator should know the file is per-plan bookkeeping.
