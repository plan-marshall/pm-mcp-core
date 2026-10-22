# PLAN-98: A first-level thinker as an endpoint: the protocol `system_one`

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-98-judgment-endpoint.md` and is queued as one row file, `queue/PLAN-98.json`,
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

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-05.
- R12 — log message holder: a record goes into `RuntimeLogMessages`, never one that holds a request body or the key, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `judgment-endpoint` of `doc/plans/judgment-endpoint.adoc`: the judgment endpoint: typed questions answered with a probability per option by a hosted service or a model served in the same format. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, 2026-10-22). Serves: Milestone 14 (the first half of the roadmap item _Implement the protocol `system_one` of the endpoint harness and the judgment vote_: the protocol, the remote form, the verification; `PM-CLIENT-2`, `PM-WF-18`, `PM-CRED-5`). Where: pm-runtime (the adapter of the protocol `system_one`, the remote form with its Token Lane credential, the verification differences); pm-core (the profile members it admits, in the endpoint profile of `endpoint-harness`).

## Deliverables

The bullets of the package file `doc/plans/judgment-endpoint.adoc` § What, in its words (authored by the orchestrator on 2026-10-22; the file itself is PLAN-96's).

1. *Implements* Client Profiles § Judgment Endpoint: The Request and the Answer, Remote Form, Verification of a Judgment Endpoint (watch PM-WATCH-CLIENT-15).
2. *The request*: a task is two requests, `POST <base_url>/v1/systemone` with `Content-Type: application/json`, no compression, the profile's `request_timeout_seconds`, and `Authorization: Bearer <secret>` in the remote form. The body holds the `model` (the versioned id of the role), the `facts` of the vote entity as `state`, and one question of the type `choice` per question of the entity, keyed by its `question_id`, with its options as `criteria` keyed by `branch_key`. The first request lists the options sorted by `branch_key`, the second in the opposite order. It sends nothing else (no recommendation, no answer of another role, no tools) and never a header that opts into a slower or less accurate mode; the entity is packed to `context_tokens` less `answer_reserve_tokens`.
3. *The answer*: the server reads `answers.<question_id>` with `choice` and `probabilities`, the member `model`, and `usage`, and never the `confidence` member. An answer is valid when its `model` equals the requested id, it holds an answer for every question, each `choice` is a `branch_key` of the question, and its `probabilities` name only the question's options, each between `0` and `1`, summing to `1` within `0.01`. An invalid answer, an HTTP error (`401`, `422`, `429`, `529`, a timeout, a body the runtime did not expect), and a refusal end the attempt like a worker without a valid result; **the runtime retries nothing inside an attempt**, the task's re-offer budget decides.
4. *Versioned ids only*: the verification sends the model id of the role and refuses an id the service answers with another (`model_alias`); every answer is checked for the requested id, so a silent re-route ends the attempt; the model catalogue holds the versioned id, and a new release is another id, unqualified until graded.
5. *The remote form* (`locality: "remote"`): the HTTPS origin of one service, enrolled by the operator's act only (a project's configuration can neither add one nor change its `base_url`). The secret is the Token Lane credential entry `credential_key`, read at the moment of a request and sent only to the origin of the entry's `url`; `base_url` must equal that origin, a redirect to another origin or to `http` ends the attempt, and the secret is registered with the redaction registry. There is no serving program, so `executable`, `identity`, and `update_evidence` are `null` and no owner check applies; the harness enrolment records the origin and the credential key; an unreachable origin, a refused key, or a failed verification is a start failure of the role. No screener runs on a `system_one` endpoint of the remote form.
6. *The request body is never logged*: logs and the audit record hold the endpoint, the model id, the status, the token counts, and the duration; no log line, audit line, or error text holds a body or the key.
7. *Verification of a judgment endpoint*: the stages of an endpoint with these differences: `identity` reads the models listing with the credential and checks that the role's model is listed (a loopback form also runs the version probe and the owner check); `launch` sends one typed request of two options in both orders, checks that the answer validates, that its `model` equals the requested id, and that both orders name the same options, sends a request whose criteria the instructions contradict and a request with one option only and records the answers, and refuses a service that answers free text (`result_schema` is `enforced`); `classes` does not apply; `limits` bisects between `1024` tokens and the service's refusal (`400` with `max_tokens_exceeded`), counting with `usage.input_tokens`, and on a remote form only on the operator's explicit choice, because every request is billed.
8. *Left out*: the vote composed from the two answers, the class rule of a declaration, and the calibration (`judgment-votes`); the endpoint profile and the loopback harness (`endpoint-harness`); the operator's entry of the credential (`PM-CRED-1`, shipped); the user documentation that tells the operator to read the service's terms.

**Done when** (the package's own acceptance): The fixtures of PM-WATCH-CLIENT-15 pass: a stub that answers an alias request with a versioned id is refused (`model_alias`); one that answers `429` with a `retry-after` ends the attempt and is not retried inside it; one that redirects to another origin or to `http` receives no secret and the attempt ends (matched control: the entry's own origin succeeds); one whose probabilities do not sum to `1` gives an invalid answer; a fixture whose answer echoes the request body leaves neither the body nor the key in any log line, audit line, or error text. The two requests of a task list the same options in opposite orders. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-2; PM-WF-18; PM-ARCH-2; PM-CRED-1; PM-CRED-5
- Specification: the sections the first bullet of _What_ names; Client Profiles § Endpoint Profile Schema; Credentials & Enrolment § Provider Credentials; Store Schemas § Harness Enrolment Store
- Watch: Client Profiles; PM-WATCH-DISP-63 (the vote built on it is `judgment-votes`'s)

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `04-judgment-endpoint`, `system_one`, and the index has no row for `judgment-endpoint` — `grep` of `doc/plans/` and `ls doc/plans/judgment-endpoint.adoc` at `4617865`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -il of 04-judgment-endpoint, system_one over doc/plans at 4617865 finds nothing; no row and no file for judgment-endpoint
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/client-profiles/04-judgment-endpoint.adoc`, `doc/implementation-watch/client-profiles.adoc`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: anchors found at 4617865 in: implementation-watch/client-profiles.adoc: PM-WATCH-CLIENT-15
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-97); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: queue rows at 4617865: PLAN-96 staged, PLAN-97 staged; PLAN-96 is staged and writes the package files
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 6 OBSERVED surface paths checked at 4617865, all exist
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `doc/plans/judgment-endpoint.adoc` — the package file, written by PLAN-96; deleted when the package is done
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/` — the adapter of the protocol `system_one` beside the adapter of `endpoint-harness` (new package of that plan)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` — the Token Lane credential entry the remote form reads
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/` — the harness enrolment of the origin and the credential key
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/` — the endpoint profile members `protocol`, `locality`, `credential_key`
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-endpoint.adoc` — developer documentation, extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-endpoint/` — ADRs of the package's design decisions
- OBSERVED: `doc/specification/client-profiles/04-judgment-endpoint.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — PM-WATCH-CLIENT-15 retired with the package

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-97 (`endpoint-harness`)
- Blocks: PLAN-99
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-98-judgment-endpoint.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
