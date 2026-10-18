envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-18T09:32:34Z

## What landed

plan-74-credential-store-choice shipped as #90 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
pr=#90
merge_state=merged
cleanup_owed=false
deliverables_total=10
deliverables_done=10
total_tokens=15213155
total_wall_seconds=71198.0
harness=claude
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:pending,archive-plan:pending
step.functional-review.deliverables_met=8
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=8
step.code-review.rules_cited=8
```

## Residue

- The PR merged through the merge queue as 44f904e. The review-bot step for the last head was closed by the operator with sourcery's re-review refused (review budget exhausted); cuioss-review-bot and coderabbit reviewed the head with no actionable comments.
- Owed to `plan-marshall-mcp`: the assembly's `SecretStoreProducer` still uses the deleted `SecretStoreSelector`, and the credential-store aspect must call `CredentialStoreChoice.record(LockManager, user, store)`. The Keychain and Secret Service paths are unproven on the Linux development host (a macOS run or the native build of `pm-mcpd` proves them).
- Recorded advisories, not fixed in this plan: the first-recording key carry looks only at the store the instance found; a stop between the first recording's switch and the key delete leaves the key in both stores; the `credential_store_moved` audit event carries no transaction id, so a re-delivered record is not recognised by the reader; the schema text of `target_held_key` (12-machine-stores.adoc) and the layout comment (02-machine-runtime.adoc:86) are inexact in one corner.
- `emit-landing` and `archive-plan` are listed as `pending` in `steps` because this message is written by `emit-landing` itself, before either has recorded an outcome; both run immediately after.
- 35 candidate lessons are in the epic inbox (28 from lessons-capture, 7 from the retrospective).
