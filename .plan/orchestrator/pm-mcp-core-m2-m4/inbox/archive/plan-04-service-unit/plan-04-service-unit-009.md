envelope_version=1
sender_type=plan
sender_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T06:41:39Z

# Candidate lesson: the pre-push quality gate's per-bundle arm is always DEGRADED on Maven projects

Source: script failure cluster. `derive_gate_bundles` resolved no bundle for this Maven project.

The pre-push-quality-gate's per-bundle arm derives the bundles to check through `derive_gate_bundles`, which assumes a marketplace-bundle layout. For a Maven multi-module project it resolves nothing. No default-scope module-tests canonical exists to fall back on, so the arm records DEGRADED on every run here. Every pm-mcp-core plan carries that noise, and the arm verifies nothing.

Candidate rule / fix direction: on non-bundle projects, the per-bundle arm should fall back to the architecture-resolved module-tests canonical (per changed module) or declare itself not-applicable. It should not report DEGRADED.

Scope hint: plan-marshall framework (pre-push-quality-gate); affects every Maven/Gradle consumer project.
