envelope_version=1
sender_type=plan
sender_id=package-texts-liveness-guarantee
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T22:58:53Z

component=plan-marshall:phase-6-finalize
category=bug
title=Resolve sonar-roundtrip as no-surface when CI skipped sonar-build on docs-only PR

# Resolve sonar-roundtrip as no-surface when CI skipped sonar-build on docs-only PR

## Context

PLAN-V2-213 (package-texts-liveness-guarantee, PR #644) changed 16 AsciiDoc files and nothing else. CI skipped `sonar-build` as not necessary, so SonarCloud has no analysis for the PR. The first `sonar-roundtrip` dispatch returned `status: error`, `count_status: undecidable`, `count_status_reason: analysis_revision_unavailable`, marked the step `failed` and recorded a 6-finalize halt ("sonar new-code count undecidable: marker undecidable"). The leaf asked the orchestrator to decide. The step only passed on a later firing with "no sonar analysis: CI skipped sonar-build (docs only)".

## Root cause

The step fails closed on any missing analysis revision. It does not first check whether the PR's CI deliberately skipped the Sonar job (docs-only footprint, `exempt-unscanned` freshness basis), so an expected absence looks the same as a broken analysis.

## Proposed action

Before failing closed, have sonar-roundtrip read the PR's `sonar-build` check conclusion (or the plan's docs-only / `exempt-unscanned` basis). When the job was skipped, return `done` with `count_status: no_surface` (work_performed false) on the first firing, and do not record a halt. Keep fail-closed for a sonar job that ran and produced no analysis.

## Evidence

- status.metadata.halts.6-finalize.sonar-roundtrip: outcome failed, reason "sonar new-code count undecidable: marker undecidable"
- phase_steps.sonar-roundtrip: firing_count 4, prior_firings failed, done, done; final display_detail "no sonar analysis: CI skipped sonar-build (docs only)"
- chat-history: sonar leaf hand-back "for a docs-only PR where CI ran no Sonar analysis, either accept the failed step and proceed, or have Sonar analyse the PR. This step cannot decide that."
- routing-decisions: mis_prune:sonar-roundtrip predicate no_code_delta, step not removed, step ran
