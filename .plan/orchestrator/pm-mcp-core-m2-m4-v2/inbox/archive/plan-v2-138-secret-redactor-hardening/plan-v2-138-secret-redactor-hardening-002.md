envelope_version=1
sender_type=plan
sender_id=plan-v2-138-secret-redactor-hardening
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-13T18:19:05Z

component=project-config
category=improvement
route=pm-mcp-core
confidence=high

# Align final_merge_without_asking with the CLAUDE.md merge rule

## Context

PLAN-V2-138 ran branch-cleanup with `final_merge_without_asking: false` (manifest step_params). The CLAUDE.md of pm-mcp-core says the plan lifecycle merges its own pull request through the merge queue without asking again. The late pre-merge phase therefore recorded a `pre-merge-consent` merge authorization whose reason is the CLAUDE.md standing authorization plus the operator's continue-unattended instruction, with no interactive confirmation.

## Root cause

The project's marshal.json knob and the repository's written merge rule disagree, so every unattended plan has to bridge the gap with a reasoned consent record instead of a configured one.

## Proposed action

Decide which side is authoritative: either set `branch-cleanup.final_merge_without_asking: true` in the project configuration so it matches CLAUDE.md, or change CLAUDE.md to say the plan asks before the merge. The operator decides; the retrospective only names the mismatch.

## Evidence

- aspect: manifest_decisions - branch-cleanup step_params final_merge_without_asking=false, use_merge_queue=true
- status.json merge_authorizations.pre-merge-consent reason: standing authorization in CLAUDE.md, no interactive confirmation
