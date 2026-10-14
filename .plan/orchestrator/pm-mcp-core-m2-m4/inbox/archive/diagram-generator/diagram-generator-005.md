envelope_version=1
sender_type=plan
sender_id=diagram-generator
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:06:14Z

component=pm-workflow
category=bug
source_plan=diagram-generator
source_record=pr-comment:e74848

# Identifier sanitising for a target grammar must be collision-free, not a lossy character map

## What happened

On PR #49 (pm-mcp-core) coderabbit flagged `MermaidRenderer` (pm-workflow, line 137): state ids were mapped to Mermaid aliases by replacing `.`, `-` and `_` with one character. The workflow grammar accepts `build.judge`, `build-judge` and `build_judge` as distinct states, so the map could merge two states into one node and render a wrong diagram without any error.

Fixed in-run by TASK-006: aliases are allocated per render, the plain mapping stays the first choice (so the golden output is unchanged), and a colliding id gets a deterministic collision suffix; tests and the ADR / guide were updated.

## Rule

Whenever an identifier from one grammar is rewritten into a narrower grammar (diagram node ids, file names, env var names, anchors), the mapping is not injective by default. Allocate names per output with a collision check and a deterministic suffix, and add a test with ids that differ only in the characters the map folds.
