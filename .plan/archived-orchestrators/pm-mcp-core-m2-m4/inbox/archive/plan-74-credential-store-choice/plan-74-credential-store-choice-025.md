envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:23:07Z

component=plan-marshall:marshall-steward
category=bug
source=dispatcher-observed fact (1); work log d76807 (2026-10-17T18:13:58Z) and 4cf92e (2026-10-18T07:22:47Z)

# Stale executor in pm-mcp-core goes undetected: sync-baseline drift probe never resolves

## Observation

The executor in this repository has no `git-workflow commit` verb, so the finalize commits fell back to plain `git commit -F`. Both `finalize-step-sync-baseline` firings reported `drift=unknown, regenerated=false - marketplace anchor did not resolve`, recorded as degraded and non-fatal. The staleness was therefore never detected or repaired. The undeclared-verb and undeclared-flag rejections in this plan (`--verification-owed`, `deliverable-hashes`, `resolve-test-scope`) may be symptoms of the same skew.

## Rule

In a consumer repository (no `marketplace/` tree) the drift probe must resolve the marketplace anchor from the installed plugin cache. Otherwise every consumer repository silently runs a stale executor. `drift=unknown` should at least raise an operator-visible warning naming `marshall-steward upgrade`, not a silent degrade.
