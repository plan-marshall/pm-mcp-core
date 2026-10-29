envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:51:48Z

# Candidate lesson: pre-push quality gate degrades to whole-tree verify in pm-mcp-core

- Component: plan-marshall:phase-6-finalize pre-push-quality-gate; pm-mcp-core build map / architecture config
- Signal: degradation warnings during finalize (not a hard failure)
- Evidence: work log WARNING 093f9a and ce0a3a (2026-10-10T06:41:58Z, 06:44:49Z); decision log 4ffcc8 (pre_push_quality_gate_inactive on unknown footprint)

## What happened

- All 63 Java paths under pm-core/src and pm-runtime/src matched a build_map glob but resolved to no bundle, so
  nothing was gated per bundle.
- No `module-tests` canonical resolves at default scope, so the scoped-green / whole-tree-red divergence check
  could not run and was reported UN-GATED.
- The gate fell back to a whole-tree `verify -Ppre-commit` (green), which is slow and also rewrites files.

## Suggested direction

Configure the pm-mcp-core build map / architecture so the Maven modules resolve as bundles and a
`module-tests` canonical (`test -pl {module} -am`) is available; otherwise every plan in this repo pays the
whole-tree cost and runs with the divergence check disabled.
