# Landing Analysis: PLAN-01 — Correct the published question-loop results

epic: question-loop-corrections
workstream: WS-01
pr: #213 (`pm-mcp-core`), #127 (`plan-marshall-documentation`)

> Landing record for one shipped plan. Written by the `analyze` verb from the inbox message
> `rb-01-correct-question-loop-results-001.md`, after checking its claims against git and the CI abstraction.
> Claims the checks could not settle are marked as unverified leads.

## Deliverable Fidelity vs Spec

Checked against `origin/main` of both repositories on 2026-10-28 (`pm-mcp-core` `8cf297b9`, with #213 as `3c4e58fd`;
`plan-marshall-documentation` `fa69be3`, #127). The deliverable claims are the landing's own statements; the rows below
say what this analysis read itself.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Overstated results corrected | shipped-as-specified (landing's claim, sampled) | `evaluation.adoc` changed by 37 lines in #127; the cut, R3 and Q5 statements the landing cites are in the diff. The per-figure wording was not re-read in full |
| D2 Measurement out of normative text | shipped-as-specified | #213 changes exactly the four specification parts of the spec; Client Profiles 01 § Resumability now reads "No form declared: the stage `resumability` has not settled one" in the shipped-values rows and links the evaluation rows for the candidates (read in the diff of `3c4e58fd`) |
| D3 Limits completed | shipped-modified | The raw evidence, the missing Sonnet repeat, Codex and the 2.1.2 to 2.1.4 range are named. **Not named: that the OpenCode runs had no inline settings** (`headless.inline_settings`, core #206): `evaluation.adoc` on `origin/main` holds no occurrence of "inline". The plan read the main-checkout copy of its spec (`spec_differ: true`), the older revision, so the limit and the HYPOTHESIS to verify the experiment code's OpenCode settings were not carried out |
| D4 Developer document | shipped-as-specified | `question-loop-experiment.adoc:14` states the code is not on `main` and names `verification/question-loop-experiment` (`488ce47`) |
| D5 Open questions | shipped-as-specified (landing's claim) | `open-questions.adoc` gained 54 lines; the file holds `opencode-worker-login` and `opencode-event-source` (from documentation #125) and the plan's questions beside them. The count of six was not re-counted |

Surface: the spec declares seven paths (four here, three in the documentation); the diffs of #213 (four files) and #127
(three files) are exactly those seven. No drift.

Not corroborated as a whole: the plan-level clause `BROKEN LINKS 0` against merged `main` is reported as failed by the
landing (12 broken links, four undefined watch ids). This analysis confirmed one cause: `model-work/01-roles-and-configuration.adoc:357`
links `PM-WATCH-CLIENT-14` and `implementation-watch/client-profiles.adoc` no longer defines it. The count of 12 was not
re-measured: the export of both trees for a clean run was declined, and the local documentation checkout is one commit
behind `origin/main`.

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none). The landing is therefore incomplete (`total_tokens`).
- Duration: not reported in the facts block.
- Lane: runbook lane on OpenCode, `deepseek-v4.1-flash/high`.
- Anomalies: the self-review ran five rounds (two beyond the limit, operator-authorised) and ended `budget-exhausted` with
  low-severity findings still appearing each round; the retry of the review bot came 85.5 minutes after a refusal under
  a 90-minute rule (recorded by the plan); the plan read the main-checkout copy of its spec, not the ledger copy.
- Gate: `verify -Ppre-commit` red locally on `SubprocessJobRunnerTest.pidReuse` and `AgentStartedDetectorTest.theOperatorBinaryMatches`,
  both host-dependent in `pm-runtime`, which the change does not touch; the same two failed in PLAN-02. CI `build / gate`
  and `integration-tests / test` green on the final heads. Not re-run by this analysis.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed two heads of #213 (one inline Minor, fixed; one trivial parity-check suggestion answered, not
  applied). The documentation PR had no active review bot; it was merged on the operator's selection without a review.
- CI/merge: `pm-mcp-core` #213 reads `merged` with `merge_commit_sha` `3c4e58fd…` (read by `ci pr queue-state`) and sits on
  `origin/main`. `plan-marshall-documentation` #127 is `fa69be3` on its `origin/main` and was merged with `gh pr merge`
  directly after `ci pr merge` returned 403 on that repository's ruleset probe, authorised by the operator.
- Sonar: `step.sonar-roundtrip.open_issue_count` is `n/a` (project not configured); nothing to compare.
- Collisions: none observed. #213 and #212 (PLAN-02) were disjoint as predicted.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-01 --status shipped`
- [x] row `pr` stamped — `#213`
- [x] row `landing` stamped — `landings/PLAN-01.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-01-correct-question-loop-results`
- [x] epic.md narrative reconciled (defects, watches, decisions, queue annotations)
- [x] defects opened: the landing is incomplete (`total_tokens`); the limit about inline settings is missing from `evaluation.adoc`
- [x] PLAN-03 dependencies updated (PLAN-01 landed; the missing limit is a lead for it)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- The limit "the OpenCode runs had no inline settings" is missing from the Limits of `evaluation.adoc`, and the experiment
  code's OpenCode launch settings were not checked. A one-sentence correction in `plan-marshall-documentation`; the
  operator decides whether it goes in as a direct pull request or is folded into PLAN-03.
- Twelve broken links and four undefined watch ids on merged `main` belong to the plans that removed the watch items
  (`PM-WATCH-CLIENT-14` in `model-work/01:357`, `PM-WATCH-PHASE-106/110/111` in the documentation repository, and the three
  items `phase-workflows.adoc` dropped). PLAN-03 repeats a `BROKEN LINKS 0` clause only after they are repaired or its clause
  is scoped to its own content, as the plan did.
- The CodeRabbit finding (derive the resumability table from the built-in profile set or add a parity check) is answered and
  unscheduled; it needs its own plan.
- `spec_differ: true`: the plan ran from the main-checkout copy of its spec, the older revision. The ledger copy is the one the
  orchestrator keeps; a launch from the main checkout does not see the incorporated changes. Recorded as a Watch.
