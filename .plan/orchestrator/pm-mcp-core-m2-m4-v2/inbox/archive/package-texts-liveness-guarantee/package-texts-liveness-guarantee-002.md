envelope_version=1
sender_type=plan
sender_id=package-texts-liveness-guarantee
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T22:59:05Z

component=pm-documents:ref-asciidoc
category=improvement
title=Add a stray tool-call markup check to asciidoc validate

# Add a stray tool-call markup check to asciidoc validate

## Context

In PLAN-V2-213 (package-texts-liveness-guarantee) the phase-5 leaf wrote the three new package files `awaiting-bounds.adoc`, `model-slot-release.adoc` and `stall-clocks-and-overdue-waits.adoc` with trailing `</content>` / `</invoke>` lines left over from the tool call. Each task's verification ran `asciidoc verify-links` and `asciidoc validate`, and neither reported the lines. Code-review round 1 found them, which forced a fix commit and a full re-fire of functional-review. Every later functional-review and code-review round (7 rounds) then repeated the same stray-markup scan by hand.

## Root cause

`asciidoc validate` checks header attributes and list formatting, but not for non-AsciiDoc tool-call tags at the start or end of a file. So the one deterministic check a docs-only plan runs cannot catch this class of defect.

## Proposed action

Extend `pm-documents:ref-asciidoc:asciidoc validate` with an error-level rule that flags lines matching tool-call markup (`</content>`, `</invoke>`, `<parameter`, `antml:`, `function_calls`) in `.adoc` files. A docs-only task's verification then fails before commit, and reviewers no longer need a manual scan.

## Evidence

- request-result-alignment: stray tool-call markup written into all three new package files in 5-execute, caught only by code-review round 1
- llm-to-script-opportunities: stray-markup scan repeated by hand in 7 review rounds
- chat-history: code-review round 1 findings 1-3 (awaiting-bounds.adoc:32-33, model-slot-release.adoc:31, stall-clocks-and-overdue-waits.adoc:34); TASK-001/002 verification commands were verify-links + validate only
- functional-review self_reports: "Three new package files ended with leftover tool-call lines; they are removed" (commit 6e4a9cf7)
