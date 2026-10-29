envelope_version=1
sender_type=plan
sender_id=scenario-replay-harness
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:46:43Z

# Candidate lesson: derive_gate_bundles yields zero bundles for this Maven project

Source: dispatcher-reported script-level friction

## What happened

`derive_gate_bundles` returned 0 bundles for pm-mcp-core, a Maven multi-module project. The gate step therefore
had nothing to derive from the project's modules.

## Candidate rule / question for the owner

Either the derivation does not recognise Maven-module projects (a gap to fix), or zero is the correct answer for a
non-marketplace project and the result should say so explicitly (which kind of zero) rather than reporting a bare
0 that reads like a failed derivation.

Likely owner: plan-marshall gate-bundle derivation (phase-5/6 gate tooling), plan-marshall repository.
