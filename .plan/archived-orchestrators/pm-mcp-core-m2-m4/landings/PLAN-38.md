# Landing Analysis: PLAN-38 — Ask the review bot again after a fix commit

epic: pm-mcp-core-m2-m4
workstream: WS-09
pr: #24 (https://github.com/plan-marshall/pm-mcp-core/pull/24), merge commit 66950bb

> Landing record for one shipped plan. Lives at `landings/PLAN-38.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-38-review-bot-re-review-007.md` (landing-facts complete, no missing
key), corroborated on 2026-10-10 against the PR state (MERGED 19:50Z), the merge commit and
`.plan/marshal.json` on `origin/main`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| `re_review_on_loopback` set to `true`; `required_bots` and `optional_bots` unchanged | shipped-as-specified | 66950bb changes one line of `.plan/marshal.json`; on `origin/main`: `required_bots` `coderabbit,cuioss-review-bot`, `optional_bots` `sourcery`, `re_review_on_loopback` `true` |
| Proof on a pull request: `/review` posted after a fix commit and the bot reviews the new head | not done | Both required bots skip `.plan/` paths: CodeRabbit "Review skipped due to path filters", cuioss-review-bot `classify-review-diff` `reviewable=false`. The spec's fallback applies: the proof is taken from the next plan with a fix commit. Handed to the epic as a Watch by operator decision |

Done-when: the setting is `true` on `main` and the PR carries no other change — both hold.

Surface: one realized path, `.plan/marshal.json`, equal to the declared surface (read from the
merge commit; `landing-check` reported `unmeasured`).

## Metrics and Anomalies

- Tokens: 1,685,938 recorded (not re-derived). Duration: 8,898 s (about 2.5 hours).
- Anomalies: init routed a one-line change to the deep lane (refine corrected it); the execution
  manifest copied the old step parameter at compose and needed a manual mirror; finalize ran a
  whole-tree `verify -Ppre-commit` (171 tests) for a change with nothing to build; the empty
  `--value ""` form failed twice. All six routed as plan-marshall lessons.

## Routing and Merge Behavior

- Review: no bot reviewed; all bots were made optional in the plan-local manifest for this plan
  only. `marshal.json` keeps both required bots.
- CI/merge: merged through the merge queue as squash commit 66950bb.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#24`; `landing` `landings/PLAN-38.md`; plan id stamped earlier
- [x] review-bot Open Defect closed as configured; Watch for the proof opened
- [x] 6 candidate lessons routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-033.md` to `-038.md`
- [x] WS-09 charter closes: no Open Defect names a configuration cause any more
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- The proof (deliverable 2) on the next plan PR with a loop-back fix commit: `/review` posted, the
  workflow run starts (the posting account passes the OWNER/MEMBER/COLLABORATOR guard), the bot
  reviews the new head. A guard rejection is a finding for `cuioss/cuioss-organization` or the
  posting identity, not for this setting.
- Running plans read the main checkout's `.plan/marshal.json`; it must be at 66950bb or later.
