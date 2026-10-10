envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:23Z

component=plan-marshall:build-maven
category=bug
created=2026-10-10

# For a Maven project the pre-push quality gate derives no module from the changed Java files

## Source

Observed by the orchestrator during plan primitive-spi-and-validators. Only partly confirmed from the decision log.

## What happened (as reported by the orchestrator)

The pre-push quality gate derived zero bundles: all 22 Java paths "matched a build_map glob but resolved to no bundle". Neither `test-compile` nor `module-tests` resolves at default scope. The gate therefore degraded to the whole-tree quality gate alone.

## Verification of the wording

- NOT confirmed from the decision log: the count of 22 paths, the quoted message, and the statement about `test-compile` and `module-tests`. The log has no such entry; they may be in the work log or in the step's own output, which this step did not read.
- Related and confirmed: at plan time the manifest kept the step on an unknown verdict — "pre_push_quality_gate_inactive — kept pre-push-quality-gate on an unknown build verdict: plan footprint unresolvable — no materialized worktree carries evidence of what this plan changed" (4ffcc8, twice).
- Related and confirmed: the step ran four times and was recorded `executed` each time (f5ec40), with no token or duration measure.
- Related: for the README-only deliverables the log records "No buildable module" (79faf8, b926b9) and "plan footprint touches no build_map glob" (af87e8). Those concern non-Java files and are correct as such; they do not show the Java case.

## Proposed rule

The mapping from a changed path to its build unit must work for Maven modules (`pm-core/src/main/java/...` resolves to module `pm-core`), not only for the bundle layout of the marketplace. When a path matches a build glob and resolves to no unit, that is reported as a defect of the mapping, and the scoped commands (`test-compile`, `module-tests`) resolve per Maven module.

## Evidence

- Decision log 4ffcc8, f5ec40, 79faf8, b926b9, af87e8.
