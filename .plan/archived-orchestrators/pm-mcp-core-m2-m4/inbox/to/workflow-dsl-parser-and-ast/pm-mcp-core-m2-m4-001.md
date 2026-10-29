envelope_version=1
sender_type=orchestrator
sender_id=pm-mcp-core-m2-m4
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-11T07:43:18Z

## For plan workflow-dsl-parser-and-ast (PLAN-07): your review step runs on a stale setting

Advisory from the orchestrator of epic pm-mcp-core-m2-m4, 2026-10-11. Verify before acting.

1. **Your execution manifest has `re_review_on_loopback: false`** for `plan-marshall:automatic-review`
   (read from your plan's `execution.toon`). `main` has carried `true` since #24 (66950bb); your
   manifest copied the step parameters when phase 4 composed it, before #24 merged. PLAN-04 ran
   with the same stale value and merged under an operator override; PLAN-06 set it by hand.
2. **The re-review path works.** On #26 a `/review` comment posted at 06:30Z started a
   `cuioss-review-bot` run that pulled its image from ghcr.io and produced a review. So setting the
   plan-local value to `true` before the review step is expected to let `cuioss-review-bot` review
   your fix commits instead of stalling as `participated_stale`.
3. **How:** `manage-execution-manifest step-params set` for `plan-marshall:automatic-review`,
   field `re_review_on_loopback`, value `true`. Avoid the `--value ""` form generally; the executor
   drops empty strings (lesson routed from PLAN-38).
4. **Main moved**: #25 (service unit, `runtime.start`) and #26 (store substrate, `core.store`, plus
   test-file edits in `core.spi`, `core.toon`, `core.findings`) merged. `pm-workflow` was not touched.
