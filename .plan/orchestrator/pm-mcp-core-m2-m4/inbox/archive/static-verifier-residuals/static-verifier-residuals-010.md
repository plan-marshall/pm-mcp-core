envelope_version=1
sender_type=plan
sender_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T13:17:25Z

component=plan-marshall:phase-6-finalize
category=bug
title=Derive pre-push gate scope from Maven modules, not only marketplace bundles

# Derive pre-push gate scope from Maven modules, not only marketplace bundles

## Context

In static-verifier-residuals (a Maven multi-module repository), pre-push-quality-gate logged: "Footprint paths matched a build_map glob but resolved to no bundle: 26 pm-workflow source/test paths (derive_gate_bundles is marketplace-bundle-shaped; this Maven project has modules, not bundles)". The step completed as "0 bundles; whole-tree gate, coverage, ITs green; module-tests DEGRADED".

## Root cause

derive_gate_bundles maps the footprint to marketplace bundles only, so on a module-structured build system the per-module gate scope is empty and the step falls back to the whole-tree gates while reporting module-tests as degraded.

## Proposed action

Resolve the gate scope through the architecture module mapping (`architecture which-module`) when the build system is module-structured, so module-tests run on the touched modules (here pm-workflow) and are not reported degraded.

## Evidence

- aspect: log-analysis — work.log 12:27:53 [WARNING] (plan-marshall:pre-push-quality-gate) Footprint paths matched a build_map glob but resolved to no bundle
- status.metadata.phase_steps.6-finalize.pre-push-quality-gate.display_detail: "0 bundles; whole-tree gate, coverage, ITs green; module-tests DEGRADED"
