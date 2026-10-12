envelope_version=1
sender_type=plan
sender_id=plan-38-review-bot-re-review
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-10T19:58:12Z

## What landed

plan-38-review-bot-re-review (PLAN-38) shipped as #24 (merged via merge queue, squash 66950bb): `re_review_on_loopback` of `plan-marshall:automatic-review` is now `true` on main. Deliverable 1 is done; deliverable 2 (the re-review proof) is NOT delivered and is handed to the epic as a watch item (operator decision).

```landing-facts
schema=landing-facts/1
plan_id=plan-38-review-bot-re-review
epic=pm-mcp-core-m2-m4
pr=#24
merge_state=merged
cleanup_owed=false
deliverables_total=1
deliverables_done=1
total_tokens=1685938
total_wall_seconds=8898.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

**Watch item requested (operator decision) — verify the cuioss-review-bot re-review on later plans.** The proof the PLAN-38 spec asked for (deliverable 2) could not be taken on PR #24: the PR touches only `.plan/marshal.json`, and BOTH required bots skip `.plan/` paths by design — CodeRabbit posted "Review skipped due to path filters", and cuioss-review-bot's `classify-review-diff` returned `reviewable=false` (0 files after its filter), so its `review / review` job was skipped. For this plan only, all bots were made optional in the plan-local manifest; `marshal.json` keeps `required_bots: coderabbit,cuioss-review-bot`, `optional_bots: sourcery`. No `/review` comment was posted.

Please add a watch item to the epic and check it after the next few plans that have a loop-back fix commit on a PR with reviewable (non-`.plan/`) code:
1. After the fix commit, plan-marshall posts a `/review` comment on the PR (trigger B of `automatic-review`).
2. The `cuioss-review-bot Review` workflow run starts from that comment — this also resolves the spec's open hypothesis that the posting account passes the reusable workflow's author-association guard (OWNER/MEMBER/COLLABORATOR, not a bot).
3. cuioss-review-bot publishes a review of the new head, and the review step does not stall.
If step 2 fails (guard rejects the account), that is a finding for the `cuioss/cuioss-organization` workflow or the posting identity, not for this setting.

**Note for running plans:** plans already in flight read the main checkout's configuration, so they get the new setting once `main` is pulled there.

**Quality gates:** no Java changed; the whole-tree `verify -Ppre-commit` ran green at finalize (171 tests) but does not cover this change. The push proceeded on the build-freshness exemption (no build owed), not on build evidence.

**Lessons:** six candidate-lesson messages were sent separately (`plan-38-review-bot-re-review-001` … `-006`).
