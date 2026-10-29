# PLAN-V2-76: A judgment role as a voter of the decision quorum

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: PLAN-99 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-76-judgment-votes.md` and is queued as one row file, `queue/PLAN-V2-76.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: a record, if any, goes into `WorkflowLogMessages` or `CoreLogMessages`, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `judgment-votes` of `doc/plans/judgment-votes.adoc`: a judgment role votes: two requests, the confidence from the lower probability, a rationale composed by the server, the class rule, and the calibration form. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, PLAN-2026-10-22). Serves: Milestone 14 (the second half of the roadmap item _Implement the protocol `system_one` of the endpoint harness and the judgment vote_: the vote, the grading, the form in the calibration record), after the decision quorum of Milestone 8, whose roadmap items have no work package yet. Where: pm-workflow (the voter of the adjudication, the composition check of a declaration); pm-core (the members of the decision record and of the calibration record it fills).

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
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: doc/plans/judgment-votes.adoc exists (9 What bullets), row open, Depends judgment-endpoint, decision-engine, role-qualification, decision-quorum-and-calibration, Milestone 14; unchanged since 88ec9c70
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/model-work/10-decision-quorum.adoc`, `doc/specification/store-schemas/16-task-queue.adoc`, `doc/implementation-watch/model-work.adoc`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: anchors read at f4a1bd07: model-work/10 blind-input, judgment-vote, quorum-calibration; store-schemas/16 decision-calibration-record; PM-WATCH-DISP-63 at model-work.adoc:467; 10-decision-quorum.adoc changed only by #188
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-98, PLAN-V2-23, PLAN-34); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: PLAN-96 shipped (#143); PLAN-98, PLAN-V2-23 and PLAN-34 still open rows; the index adds a fifth dependency, decision-quorum-and-calibration
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `the decision quorum`, `adjudicate.decisions`, `Quorum Calibration`, `quorum declaration`; the index will keep this package `blocked` on them
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: doc/plans/decision-quorum-and-calibration.adoc exists (open, Depends decision-engine and role-qualification, Milestone 8); code absent: workflow/decision holds package-info only
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis verify-at-outline; the class-rule half lives in workflow/model (RoleUse, RoleComposition), so the surface is partly misplaced
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: milestone holds (14); the edges do not: the index lists four dependencies including decision-quorum-and-calibration, the spec lists PLAN-96/PLAN-98/PLAN-V2-23/PLAN-34 and omits it
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: package file and row exist; PLAN-98, PLAN-V2-23, PLAN-34 and the quorum package are all still open

## Expected Surface

- OBSERVED: `doc/plans/judgment-votes.adoc` — the package file (exists), deleted when the package is done
- OBSERVED: `doc/specification/model-work/10-decision-quorum.adoc` — Judgment Votes, Quorum Calibration, Model Identity
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — Decision Calibration Record, member form
- OBSERVED: `doc/specification/client-profiles/04-judgment-endpoint.adoc` — class rule of the judgment endpoint
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — role-composition and role-class-fit
- OBSERVED: `doc/specification/model-work/11-decision-records.adoc` — rationale_origin, probabilities
- OBSERVED: `doc/specification/store-schemas/18-decision-records.adoc` — decision record members
- OBSERVED: `doc/implementation-watch/model-work.adoc` — PM-WATCH-DISP-63 retired with the package
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/decision/` — voter of the adjudication; no class nameable yet (package-info only)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` — members of the decision and calibration record it fills; home unsettled, no class exists
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/RoleUse.java` — protocol half of the class rule (system_one vs openai_chat), left to this package by its Javadoc
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/RoleComposition.java` — class_mismatch for a text-form kind on a judgment role
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/ImportedItem.java` — exact_choice grading items (Method.EXACT_CHOICE); read
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/model/RoleCompositionTest.java` — class rule refusals
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-decision.adoc` — developer documentation of the package, does not exist yet
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-decision/` — ADRs of the package, new directory

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 7: 3 corroborated, 3 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0 and 6: `doc/plans/judgment-votes.adoc` exists (9 What bullets), row open; PLAN-98, PLAN-V2-23, PLAN-34 and the quorum package are all still open.
- Claim 3: `doc/plans/decision-quorum-and-calibration.adoc` exists (index open, depends on `decision-engine` and `role-qualification`, Milestone 8); it is a **fifth dependency** of this plan in the index and the spec's "(the decision quorum, no work package)" is stale; code is absent (`workflow/decision` is package-info only).
- Claim 5: the milestone holds (14), the edges do not: the index lists judgment-endpoint, decision-engine, role-qualification and decision-quorum-and-calibration; the spec lists PLAN-96, PLAN-98, PLAN-V2-23, PLAN-34 and omits the quorum package.

### Surface

- Added to the Expected Surface above: `core/role/corpus/` (read) and the note on `workflow/model/`.
- wrong: `doc/plans/judgment-votes.adoc` is annotated HYPOTHESIS "written by PLAN-96"; it exists.
- wrong: `pm-core/src/main/java/de/planmarshall/core/store/` is annotated "the decision and calibration records"; no decision or calibration record exists; the home (`core.store` here, `core.role` per the quorum package, `workflow.decision` per `module-structure`) is unsettled.
- wrong: `pm-workflow/.../workflow/decision/` is "package-info only", still true; the class-rule half lives in `workflow/model`, not `core/role`.

### Specification changes carried (not yet in the Deliverables above)

- #188 (`854a7c7a`) — Model Work 10 § Decision Declarations, paragraph **Model Identity** (anchor `model-identity`, watch `PM-WATCH-DISP-67`): two listed roles of one declaration count as one model when their enrolments' weights identities are equal, or else when their identifiers are equal after dropping everything up to the last `/`, everything from the first `:`, and case; the check errs towards refusing (`quorum_not_diverse`, naming both roles, identifiers and the shared identity). It binds the Done-when fixture quorum (a judgment role plus a worker role must name different models) and the composition check of a declaration; the rule itself belongs to the quorum package.

### Shipped or superseded since the last grounding

- PLAN-26 (#146): the class half of the class rule (`RoleUse.classesOf`, `RoleComposition` raising `class_mismatch`). PLAN-104 (#185): `ScopeTransaction`-based appends (ADR `core-store/044`), the seam a decision or calibration record write would use. Still absent: the protocol half (`system_one` vs `openai_chat`), the vote, `rationale_origin`, probabilities, the calibration `form`, any decision or calibration record type.
- Dependencies: PLAN-96 shipped; PLAN-98, PLAN-V2-23, PLAN-34 and `decision-quorum-and-calibration` open. `PM-WATCH-DISP-63` present (model-work.adoc:467), `PM-WATCH-DISP-67` added by #188.

### Open questions for the outline

- One home for the decision and calibration records; whether `exact_choice` is the corpus's method name (`ImportedItem.Method.EXACT_CHOICE` exists); the fixture quorum must satisfy the #188 model-identity rule; the calibration record's `constant_baseline` (#171) belongs to the quorum package.
- Package file Left-out: it links `decision-quorum-and-calibration`; the spec's Deliverable 9 says "no work package".

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 7: 2 corroborated, 3 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/judgment-votes.adoc exists at HEAD (cut by #143 9c9d99c6, dependencies edited by #151 35fba9ed) and doc/plans/README.adoc has the row judgment-votes (open; depends on judgment-endpoint, decision-engine, role-qualification, decision-quorum-and-calibration).
- Claim 3: doc/plans/decision-quorum-and-calibration.adoc exists (#151 35fba9ed) with an index row (open; depends on decision-engine, role-qualification) and names the decision quorum, adjudicate.decisions, the quorum declarations and the quorum calibration. Code is still absent (pm-workflow workflow/decision/ holds package-info.java only), so the package is cut but not built.
- Claim 6: Evidence 'no package file and no index row' is false at HEAD: doc/plans/judgment-votes.adoc and its index row exist, PLAN-96 shipped as #143; judgment-endpoint, decision-engine, role-qualification and decision-quorum-and-calibration are still in the table, so the package is not ready.

### Surface

- Added to the Expected Surface above: `pm-workflow/src/main/java/de/planmarshall/workflow/model/`, `pm-workflow/doc/developer/workflow-model.adoc`, `pm-workflow/doc/adr/workflow-model/`, `doc/specification/client-profiles/04-judgment-endpoint.adoc`, `doc/specification/model-work/01-roles-and-configuration.adoc`, `doc/specification/model-work/11-decision-records.adoc`, `doc/specification/store-schemas/18-decision-records.adoc`.
- understated: The composition check and its class rule are in pm-workflow/src/main/java/de/planmarshall/workflow/model/ (RoleUse.classesOf: vote and decision admit read_only or zero_tool, RoleComposition, CompositionRefusal), not in pm-core core/role/. Add that path, pm-workflow/doc/developer/workflow-model.adoc and pm-workflow/doc/adr/workflow-model/ (last 001). The protocol half of the class rule (system_one against openai_chat) is expressly left to this package by RoleUse javadoc and ADR workflow-model/001.
- understated: pm-core/src/main/java/de/planmarshall/core/role/ exists (RoleForm, RoleClass, ModelCatalogue); the grading bullet reads exact_choice items from pm-core/.../core/role/corpus/ (ImportedItem.Method.EXACT_CHOICE, CorpusValidator.CLOSED_METHODS): add core/role/corpus/.
- unresolvable: pm-core/src/main/java/de/planmarshall/core/store/ for the decision and calibration records: no decision or calibration record type exists at HEAD; decision-quorum-and-calibration places the calibration record in core.role and module-structure puts the decision records in workflow.decision. Name one home once PLAN-V2-23 and the quorum package decide.
- understated: Specification parts in What or Trace but not in the surface: doc/specification/client-profiles/04-judgment-endpoint.adoc (class rule), doc/specification/model-work/01-roles-and-configuration.adoc (#role-composition, #role-class-fit), doc/specification/model-work/11-decision-records.adoc and doc/specification/store-schemas/18-decision-records.adoc (rationale_origin, probabilities members).

### Specification changes carried (not yet in the Deliverables above)

- #171 — model-work/10 Quorum Calibration and store-schemas/16 Decision Calibration Record: the record now requires constant_baseline and a declaration is calibrated only above it: A calibration item answered by a judgment role is recorded in a record that requires constant_baseline, a member the quorum package owns; say so in bullet 6 so the fixture writes it. Surface: store-schemas/16-task-queue.adoc is already listed; no new path.

### Shipped or superseded since the last grounding

- superseded-by-shipped: PLAN-96 shipped #143 (doc/plans/judgment-votes.adoc, row in README); #151 35fba9ed cut decision-quorum-and-calibration.adoc, so the 'parked: decision quorum has no work package' note is stale; the quorum is cut but unbuilt (workflow/decision holds package-info.java only).
- partly-fixed: Class half of the class rule shipped by PLAN-26 #146: pm-workflow RoleUse.classesOf maps vote and decision to read_only or zero_tool and RoleComposition raises class_mismatch; the protocol half (system_one against openai_chat), the vote, rationale_origin server, probabilities and the form member of the calibration record are absent.

### Open questions for the outline

- The calibration record has three homes in the cut packages: this spec core/store, decision-quorum-and-calibration core.role, module-structure workflow.decision for decision records; no code exists for either record.

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-98 (`judgment-endpoint`); PLAN-V2-23 (`decision-engine`); PLAN-34 (`role-qualification`)
- Parked: the decision quorum (the declarations, the adjudication unit `adjudicate.decisions`, the decision rule, the calibration run) has no work package; the index keeps this package `blocked` on it
- Blocks: nothing
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-76-judgment-votes.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
