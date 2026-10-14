envelope_version=1
sender_type=plan
sender_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T13:14:21Z

# Candidate lesson: pre-push gate degrades on a Maven multi-module project

Plan: static-verifier-residuals (PR plan-marshall/pm-mcp-core#66).
Signal source: orchestrator observation during 6-finalize (pre-push gate).

## What happened

- `derive_gate_bundles` resolved no bundles for this Maven multi-module project (pm-core,
  pm-workflow, pm-runtime, pm-providers/*), so the pre-push gate had no bundle set to run.
- The `test-compile` and `module-tests` canonical commands did not resolve at default scope
  (they are module-scoped in this repository: `test -pl {path} -am`).

The pre-push gate therefore degraded instead of running the module tests of the changed
modules; correctness rested on the separate full verify.

## Candidate rule

The pre-push gate should derive its scope from the realized footprint mapped to Maven modules
(architecture which-module on each changed path) and invoke the module-scoped canonicals per
affected module, falling back to the root `verify` canonical rather than degrading when no
bundle mapping exists. A degraded gate must name what it skipped so it is not read as green.

## Suggested owner

plan-marshall phase-6-finalize pre-push gate (derive_gate_bundles) and/or build-maven /
manage-architecture canonical resolution for multi-module projects. Classification deferred
to the orchestrator.
