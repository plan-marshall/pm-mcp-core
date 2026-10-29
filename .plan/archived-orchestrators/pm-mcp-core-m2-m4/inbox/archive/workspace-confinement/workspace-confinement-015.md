envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:54Z

# Candidate: the pre-push check resolved no build group for a Maven multi-module change and could not run its divergence test

Source: run observation, not a finding record. Verified against the logs.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## Evidence

- Work log `c6adbd` (2026-10-09T23:04:38Z, WARNING, pre-push-quality-gate): "Footprint paths matched a build_map glob but resolved to no bundle", listing all 10 changed Java files of `pm-core` and `pm-runtime`, "proceeding; these are not gated as a bundle".
- Work log `93eee2` (23:07:17Z, WARNING, pre-push-quality-gate): "No module-tests canonical resolves in this project, so no pytest could be run and the divergence question cannot be answered — the scoped-green / whole-tree-red divergence class (PLAN-08) is UN-GATED at finalize for this push. Proceeding on honest degradation."
- Decision log `4ffcc8` (at plan time): "kept pre-push-quality-gate on an unknown build verdict: plan footprint unresolvable".
- Work log `0a2df5` (22:53:43Z, WARNING): "TASK-5 missing verification — falling back to architecture resolve".

## What happened

For this Maven multi-module repository the step that derives which build groups a change touches returned none, although every changed file matched a build mapping. The step then looked for a project-wide test command, found none at default scope, and named `pytest` as the thing it could not run, in a repository that has no Python tests. It proceeded on both warnings; the step ran three times in this plan and the first run logged both.

The whole-tree Maven verify did run in the execute phase each time (work log `a9924a`, `d98222`, `04aebb`), so the change was not shipped untested. What did not run is the specific comparison of module-scoped results against whole-tree results.

## Why it may be a lesson

The check degrades silently into a pass for every plan of this repository, and its warning text assumes a Python project. Whether the cause is the repository's build mapping or the derivation itself is not established by the log.

Classification is left to the pickup.
