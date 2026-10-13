envelope_version=1
sender_type=plan
sender_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T20:02:59Z

# Candidate lesson: ADR number collisions in module-level doc/adr/{package}/ directories

**Component (suggested):** plan-marshall:manage-adr
**Category (suggested):** bug

## Observation

Plan plan-17-project-units-and-invariants (PR #56) allocated module ADR numbers that collided twice with
concurrently landed PRs: #54 took workflow-verify/012, and #53 backfilled 013-016. The ADRs had to be
renumbered to 017-020 at landing.

## Root cause

The manage-adr scan/number gate covers only the repo-level `doc/adr` directory. Module ADRs that live under
`doc/adr/{package}/` (per-module directories) are invisible to it, so neither allocation nor the collision
gate can detect a number already used by another in-flight or freshly landed branch.

## Suggested correction

Extend the manage-adr numbering and scan gate to module ADR directories (or make the ADR root configurable
per module), and re-check numbers against the freshly fetched base branch right before landing so a
concurrent merge is caught before the merge queue rather than after.
