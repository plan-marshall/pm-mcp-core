envelope_version=1
sender_type=plan
sender_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T20:03:36Z

# Candidate lesson: Pre-push quality gate doc assumes a marketplace/pyprojectx project shape

**Component (suggested):** plan-marshall:phase-6-finalize (pre-push quality gate)
**Category (suggested):** improvement

## Observation

The pre-push quality-gate workflow doc is written around the marketplace/pyprojectx shape: it names
`bundles`, `test-compile` and `module-tests` canonicals. In this Maven project (pm-mcp-core) none of those
map; only the whole-tree `quality-gate` canonical resolved. The agent had to work out which parts applied.

## Suggested correction

Make the pre-push gate build-system neutral: resolve its canonicals through the architecture API per
module/build system, and document the fallback (whole-tree `quality-gate`, then `verify`) explicitly when a
per-module canonical does not exist, instead of naming marketplace-specific canonicals.
