# PLAN-38: Ask the review bot again after a fix commit

epic: pm-mcp-core-m2-m4
workstream: WS-09

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-38-review-bot-re-review.md` and is queued as one row file, `queue/PLAN-38.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Stop the review step of every plan from stalling after its first fix commit. `cuioss-review-bot` is
a required reviewer of this repository and reviews only when asked; the project configuration tells
plan-marshall not to ask again after a fix commit. PLAN-02 and PLAN-03 each stalled on this until
the operator made the bot optional for that plan, PLAN-03 overnight. This plan switches the
re-review on, so the review step posts the bot's `/review` comment itself after a fix commit.

## Deliverables

1. In `.plan/marshal.json`, the step parameters of `plan-marshall:automatic-review` set
   `re_review_on_loopback` to `true`. `required_bots` and `optional_bots` stay as they are
   (`coderabbit,cuioss-review-bot` required, `sourcery` optional). The value is set through
   `manage-config`, not by hand-editing the file, where a verb for it exists.
2. Proof on this plan's own pull request: after a commit pushed on top of the first review, a
   `/review` comment is posted and `cuioss-review-bot` publishes a review of the new head. If the
   plan needs no fix commit of its own, the proof is taken from the next plan that has one and
   that is said in the landing message instead of being claimed.

**Done when:** the setting is `true` on `main`; the pull request carries no other change.

Out of scope, and why: the reviewer's container image is pulled anonymously from Docker Hub and
sometimes fails on the pull rate limit (seen on #17). That is fixed in `cuioss/cuioss-organization`
by a plan of that repository; the pinned workflow version here is bumped afterwards, by Dependabot
or by a follow-up.

## Standing Constraints

- This is project configuration, not a work package of `doc/plans/`: no package file, no row in
  the index, no trace matrix, no documentation pull request.
- The usual flow of `CLAUDE.md` § Git Workflow: branch, commit, pull request, checks, review
  comments answered. No Java change, so the Maven gates have nothing to build; say so rather than
  reporting them green.
- Log messages: no identifier block. This plan adds no log message.

## Claim Labels

- OBSERVED: `required_bots` is `coderabbit,cuioss-review-bot`, `optional_bots` is `sourcery` and `re_review_on_loopback` is `false` — read at `.plan/marshal.json` § the step parameters of `plan-marshall:automatic-review` (main at 7d99648)
- OBSERVED: the caller workflow triggers on `pull_request` types `opened`, `reopened`, `ready_for_review` and on `issue_comment`, and states that there is deliberately no automatic re-review on push — read at `.github/workflows/cuioss-review-bot.yml` § `on`
- OBSERVED: plan-marshall registers `/review` as the trigger comment of this bot with the semantics `requires_explicit_trigger`, and posts it on a loop-back only when `re_review_on_loopback` is `true` — read at `plan-marshall:automatic-review` `standards/cuioss-review-bot.md` and `SKILL.md` § the loop-back re-review
- OBSERVED: the reusable workflow honours a slash command only from a commenter whose author association is OWNER, MEMBER or COLLABORATOR and who is not a bot — read at `cuioss/cuioss-organization` `.github/workflows/reusable-cuioss-review-bot.yml` § the `if:` of the `review` job
- HYPOTHESIS: the account plan-marshall posts the comment with passes that guard in this repository — confirm/refute at the first `/review` comment this plan posts and the workflow run it starts (verify-at-outline is not possible; verify at finalize)
- HYPOTHESIS: `manage-config` has a verb that sets this step parameter — confirm/refute at `plan-marshall:manage-config` § its step-parameter verbs (verify-at-outline)
- Verify-first clause: read `.plan/marshal.json` again before the change; if the value is already `true` or the bot is no longer required, stop and report instead of changing anything.

## Expected Surface

- OBSERVED: `.plan/marshal.json` — one value in the step parameters of the review step

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: no other plan; no staged spec declares `.plan/marshal.json`
- Adjacent to: `.github/workflows/cuioss-review-bot.yml`, which stays untouched — the trigger set is the organisation's design, and the pin moves only when `cuioss/cuioss-organization` releases the image mirror. Plans already running read the configuration of the main checkout, so the setting reaches them when `main` is pulled there.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-38-review-bot-re-review.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
