envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:13:09Z

component=plan-marshall:phase-6-finalize
category=bug
signal=orchestrator-observation (verified)
evidence=work.log 17:19:26Z WARNING "derive_gate_bundles is marketplace-bundle shaped" (38 Maven module paths resolved to no bundle); 17:23:49Z WARNING "No module-tests canonical resolves in this project ... module-tests arm is DEGRADED"; decision.log dcbeae (phase-4 deriver drops the integration-tests profile)

# pre-push-quality-gate is marketplace-shaped: zero bundles and a permanently DEGRADED module-tests arm in a Maven repository

## Observation

In pm-mcp-core (Maven, modules pm-core / pm-runtime / pm-providers/*):

1. `derive_gate_bundles` resolved the plan footprint (38 paths under pm-core/ and pm-runtime/) to zero bundles because it maps paths to marketplace bundles; only the whole-tree arms gated them.
2. The module-tests (and test-compile) canonicals do not resolve at default scope in this project, so the gate recorded the module-tests arm DEGRADED on every firing, although `verify -Ppre-commit` in the same gate ran the full suite (333 tests) green.
3. Related, phase 4: the verification-command deriver maps integration-test files to `verify -pl pm-runtime -am` without `-Pintegration-tests`, so the declared IT command had to be appended to each IT task by hand.

## Suggested direction

Derive gate units from the build system's module structure (architecture `which-module`) for non-marketplace projects, resolve module-tests per module (`test -pl {module} -am`), and treat a whole-tree run that already executed the tests as satisfying the module-tests arm instead of reporting DEGRADED. Let the deriver honour a module's integration-test profile.
