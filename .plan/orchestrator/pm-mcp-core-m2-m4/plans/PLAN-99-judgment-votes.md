# PLAN-99: A judgment role as a voter of the decision quorum

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-99-judgment-votes.md` and is queued as one row file, `queue/PLAN-99.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: a record, if any, goes into `WorkflowLogMessages` or `CoreLogMessages`, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `judgment-votes` of `doc/plans/judgment-votes.adoc`: a judgment role votes: two requests, the confidence from the lower probability, a rationale composed by the server, the class rule, and the calibration form. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, 2026-10-22). Serves: Milestone 14 (the second half of the roadmap item _Implement the protocol `system_one` of the endpoint harness and the judgment vote_: the vote, the grading, the form in the calibration record), after the decision quorum of Milestone 8, whose roadmap items have no work package yet. Where: pm-workflow (the voter of the adjudication, the composition check of a declaration); pm-core (the members of the decision record and of the calibration record it fills).

## Deliverables

The bullets of the package file `doc/plans/judgment-votes.adoc` § What, in its words (authored by the orchestrator on 2026-10-22; the file itself is PLAN-96's).

1. *Implements* Model Work § Judgment Votes, the class rule of Client Profiles § Judgment Endpoint in the composition check of Roles & Configuration, and the member `form` of the role configuration in Store Schemas § Decision Calibration Record (watch PM-WATCH-DISP-63).
2. *Parts it votes on*: a part of the answer type `one`. A set that holds a part of another type for a kind whose declaration lists the judgment role ends the role's task `infeasible`, and the set escalates with `role_unavailable`.
3. *Two orders, one vote*: the first request lists the options sorted by `branch_key`, the second in the opposite order, both carrying every question of the entity. For each question the choice `c` is the option the first request chooses; the confidence is the integer part of `100` times the lower of the probability of `c` in the first request and in the second. A second request that prefers another option gives `c` a probability below one half, so the vote stays below every score a declaration can name (`50` to `100`) and the set escalates (`below_score`, or `disagreement` if another role chose another option). The `confidence` member of the service's answer is never used.
4. *A rationale the server composed*: `<model id>: <branch_key> at <p1> sorted, <p2> reversed`, with `rationale_origin: server`; a judgment role writes no prose, and no role of another kind reads the rationale as reasoning. The vote carries the versioned model id the answer named and both distributions in `probabilities` (`sorted` and `reversed`), so that a later model can be graded or trained on the decisions and the operator's final answers. The members of the vote record and their rendering are `decision-engine`'s; this package fills them.
5. *The class rule*: a judgment role serves the entity kinds that declare a typed form, today the kind `vote`; a kind of the text form (the screener's `screen`, the grading kind `zero_tool`) on a judgment role, and a typed kind on a role of the protocol `openai_chat`, is `class_mismatch` in the composition check.
6. *Calibration form*: the role configuration of a calibration record carries `form` (`warm` or `fresh`; a judgment role is `fresh`) and, for a judgment role, `model` as the versioned id; the declaration digest covers the form. A judgment role answers a calibration item with its two requests, and the decision rule applies with the declared score. The calibration itself (the corpus items, the automatic share, the false automatic share, `calibration_failed`) is the quorum's.
7. *Grading*: a judgment role is graded on the `exact_choice` decision items of its own topic before it decides, like every role of the class `zero_tool` (the mechanics are `role-qualification`'s; the item kind is the roadmap's word and is confirmed against the corpus at the trace matrix).
8. *What it can use*: only the entity's facts and options; a judgment role is one voter and never the only reason to trust a text that a fact quotes.
9. *Left out*: the endpoint and its protocol (`judgment-endpoint`); the declarations, the adjudication unit, the decision rule, and the calibration run (the decision quorum, no work package); the rendering of the vote in both clients.

**Done when** (the package's own acceptance): A fixture quorum whose roles are a judgment role (a stub of the protocol, both orders) and a worker role decides automatically when both choose the same option at or above the score, and escalates with `below_score` when the second order prefers another option, with `disagreement` when the roles differ, and with `role_unavailable` when a part is not of the type `one`; the vote record carries `rationale_origin: server`, the versioned model id, and both distributions, and a vote of a model carries `probabilities: null`; the composition check refuses a text-form kind on a judgment role and a typed kind on a role of the protocol `openai_chat` with `class_mismatch`; a calibration item answered by a judgment role records `form: fresh` and the versioned model id. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-18; PM-CLIENT-2; PM-WORK-6
- Specification: the sections the first bullet of _What_ names; Model Work § Blind Input; § Quorum Calibration; Store Schemas § Decision Record; Model Work § Decision Records
- Watch: Model Work

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `Judgment Votes`, `judgment-vote`, `judgment role`, and the index has no row for `judgment-votes` — `grep` of `doc/plans/` and `ls doc/plans/judgment-votes.adoc` at `4617865`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -il of Judgment Votes, judgment-vote, judgment role over doc/plans at 4617865 finds nothing; no row and no file for judgment-votes
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/model-work/10-decision-quorum.adoc`, `doc/specification/store-schemas/16-task-queue.adoc`, `doc/implementation-watch/model-work.adoc`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: 3 cited path(s) exist at 4617865 and are unchanged since 0bf64f7 (git diff --stat 0bf64f7..4617865 empty): doc/specification/model-work/10-decision-quorum.adoc, doc/specification/store-schemas/16-task-queue.adoc, doc/implementation-watch/model-work.adoc
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-98, PLAN-47, PLAN-34); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: queue rows at 4617865: PLAN-96 staged, PLAN-98 staged, PLAN-47 staged, PLAN-34 staged; PLAN-96 is staged and writes the package files
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `the decision quorum`, `adjudicate.decisions`, `Quorum Calibration`, `quorum declaration`; the index will keep this package `blocked` on them
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -il of the decision quorum, adjudicate.decisions, Quorum Calibration, quorum declaration over doc/plans at 4617865 finds nothing
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 7 OBSERVED surface paths checked at 4617865, all exist
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `doc/plans/judgment-votes.adoc` — the package file, written by PLAN-96; deleted when the package is done
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/decision/` — the decision quorum and records (`package-info.java` only today)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/` — the role artifacts and the composition check
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` — the decision and calibration records
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-decision.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-decision/` — ADRs of the package's design decisions; new directory
- OBSERVED: `doc/specification/model-work/10-decision-quorum.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/implementation-watch/model-work.adoc` — PM-WATCH-DISP-63 retired with the package

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-98 (`judgment-endpoint`); PLAN-47 (`decision-engine`); PLAN-34 (`role-qualification`)
- Parked: the decision quorum (the declarations, the adjudication unit `adjudicate.decisions`, the decision rule, the calibration run) has no work package; the index keeps this package `blocked` on it
- Blocks: nothing
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-99-judgment-votes.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
