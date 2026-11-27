envelope_version=1
sender_type=plan
sender_id=plan-v2-200-composed-vector-check-parser-limits
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T21:26:36Z

component=plan-marshall:workflow-integration-sonar
category=improvement
title=Wait for the Sonar analysis of a new PR head before counting new-code issues

# Wait for the Sonar analysis of a new PR head before counting new-code issues

## Context

In plan-v2-200-composed-vector-check-parser-limits the sonar-roundtrip step ran right after PR 638 was replaced by PR 641. SonarCloud had not yet published an analysis for head 63dce25d, so fetch_findings returned count_status undecidable (analysis_revision_unavailable). The step was marked failed, a finalize halt was recorded, and the dispatch was the plan's only error-terminated one (76,451 tokens). The next firing found the analysis and confirmed 0 issues. The step params carry ce_wait_timeout_seconds: 600.

## Root cause

The fetch did not wait for an analysis of the current head to appear before deciding the count; a not-yet-published analysis is treated as a terminal failure rather than an awaitable state.

## Proposed action

When the analysis revision is empty or behind the head, await it (bounded by ce_wait_timeout_seconds) before returning undecidable; only fail after the bound elapses.

## Evidence

- status.metadata.halts.6-finalize.sonar-roundtrip: outcome failed, reason "sonar new-code count undecidable: analysis_revision_unavailable"
- dispatch boundary 6-finalize 19:00:10Z termination_cause error, 76451 tokens
- sonar-roundtrip firing_count 3, prior_firings failed then done
