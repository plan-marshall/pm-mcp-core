envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:23:30Z

# Cover .pmwf and .json resources in architecture search --content

- Component: plan-marshall:manage-architecture
- Category: improvement
- Source: plan-retrospective (plan-v2-190-lane-entries-realized-composition)

## Context

In plan-v2-190 five dispatched leaves (refine, outline, both 3-outline Q-Gate rounds, the 4-plan Q-Gate and the functional review) reported the same gap: `architecture search --content` and `find` do not reach `*.pmwf` workflow fixtures or JSON schemas under `src/`, and Grep was denied to the subagents. The sweep for removed lane entries and trigger values in fixtures had to be done by `git ls-files` plus direct reads in one round and was left partly unproven in another.

## Root cause

The module-attributed inventory walks only the file kinds it classifies, so test resources of a custom DSL and JSON resources fall outside it; with Grep unavailable to leaves there is no sanctioned fallback.

## Proposed action

Include `src/**/resources/**` files (at least `.pmwf` and `.json`) in the content-search population, or let a project declare extra content globs in marshal.json. Until then, the coverage gap should be returned to the orchestrator explicitly, as the 4-plan Q-Gate did.

## Evidence

- aspect: llm_to_script_opportunities - same coverage gap reported in 5 dispatches
- aspect: permission_prompt_analysis - Grep not granted to dispatched leaves
- aspect: chat_history_analysis - Q-Gate: "the architecture inventory does not include *.pmwf or *.json: find returns 0 for both"
