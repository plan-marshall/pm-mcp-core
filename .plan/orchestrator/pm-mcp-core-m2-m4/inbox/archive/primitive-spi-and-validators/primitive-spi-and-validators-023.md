envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:58:35Z

component=plan-marshall:plan-retrospective
category=bug
created=2026-10-10

# Do not grade paths outside the repository as missing files

## Source

Retrospective of plan primitive-spi-and-validators (artifact consistency, manifest decisions, request-result alignment). Read from the two script results.

## Context

Deliverable 6 declares 11 files to change in the sibling repository, written as `../plan-marshall-documentation/...`. The plan's realized footprint is taken from this repository and holds 23 files, every one of them declared. The artifact check reports `affected_files_recall: fail`, "Recall 68% below 70% threshold", at severity error; the manifest check reports `declared_vs_realized_set: fail` with 11 declared-but-unrealized paths. Both are true by construction for any plan with a deliverable in a second repository: six of those files were in fact changed there.

A second, smaller defect shows in the same result: `declared: 34`, `found: 23`, yet `missing[10]`. The path `../plan-marshall-documentation/doc/specification/workflow-dsl/05-primitives.adoc` is in `outline_only[11]` but not in `missing`.

## Root cause

The recall denominator counts every declared modification path, including paths that leave the repository root, while the numerator can only ever contain paths inside it. The check then grades the declaration, not the execution.

## Evidence

- `fragment-artifact-consistency`: `declared: 34`, `found: 23`, `missing[10]`, `recall_pct: 67.6`, `outline_only[11]`.
- `fragment-manifest-decisions`: `outline_only_count: 11`, `references_only_count: 0`.
- All 11 unrealized paths start with `../plan-marshall-documentation/`.
- Without them the recall is 23 of 23.

## Proposed action

- Report declared paths that resolve outside the repository root as their own population ("outside this repository, not measurable here") and take them out of the recall denominator.
- Make `missing` equal to declared minus found, and have the check assert that itself.
