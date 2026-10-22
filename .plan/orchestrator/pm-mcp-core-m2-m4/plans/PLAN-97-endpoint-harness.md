# PLAN-97: A model on this machine: the endpoint harness

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-97-endpoint-harness.md` and is queued as one row file, `queue/PLAN-97.json`,
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
- R12 — log message holder: a record goes into `RuntimeLogMessages` (the adapter, the owner check) or `CoreLogMessages` (the profile validation), at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `endpoint-harness` of `doc/plans/endpoint-harness.adoc`: the endpoint harness: a model served on the loopback interface, its profile, its request, its enrolment and the identity of its weights. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, 2026-10-22). Serves: Milestone 14 (roadmap item _Implement the endpoint harness_, `PM-CLIENT-2`, `PM-WORK-6`); the level-2 screener of Milestone 7 (`PM-SEC-1`) is the first role it can carry. Where: pm-core (the endpoint profile: schema, validation, the sets and overrides it shares with the client profile); pm-runtime (the HTTP adapter on the loopback interface, the owner check of the listening socket, the weights identity, the verification stages).

## Deliverables

The bullets of the package file `doc/plans/endpoint-harness.adoc` § What, in its words (authored by the orchestrator on 2026-10-22; the file itself is PLAN-96's).

1. *Implements* Client Profiles § Endpoint Harness: A Model on This Machine, Endpoint Profile Schema, The Request, Enrolment, Weights Identity, Verification of an Endpoint (watch PM-WATCH-CLIENT-14).
2. *The endpoint profile*: a document of the profile sets with the member `profile_kind: "endpoint"`, its `client_id` in the namespace of the client profiles; no `tools`, `skill_loading`, `headless`, `connection`, `detection`, or `worker` block; no profile shipped. Validation applies the rules of a client profile where they concern the same members and refuses an `answer_reserve_tokens` not below `context_tokens`, a `weights_probe` that fills the members of the other source, and the combinations of `locality` the schema cannot express (`loopback` with an `https` `base_url`, with a `credential_key`, or without `executable` and `identity`; `remote` with the protocol `openai_chat`, with an `http` `base_url`, without a `credential_key`, or with an `executable`, `identity`, or `update_evidence`).
3. *Loopback only here*: `base_url` names `127.0.0.1` or `[::1]`. The protocol `system_one` and the remote form are `judgment-endpoint`'s; this package admits the members for them in the schema (`protocol`, `locality`, `credential_key`, the nullable `executable`, `identity`, `update_evidence`) and refuses a profile that uses them until that package is done.
4. *The class rule*: an endpoint harness runs exactly the role class `zero_tool` and no other; the server marks a `judge` assigned to it as ungraded wherever the assignment is shown (the role views of both clients are `pm-mcp-clients`').
5. *The request*: one HTTP request per task, composed by the server: the launch prompt of a `zero_tool` worker as the system message and the task input as the user message, the model of the role's effective configuration, streaming explicitly off, the role's result schema as the response format where `result_schema` is `enforced` or `advisory`, no `tools` member and no tool choice, and every request value the endpoint has a default for sent explicitly. The role's context budget is `context_tokens` less `answer_reserve_tokens`; a screening batch is packed to it; a role artifact whose launch prompt alone does not fit is invalid on that harness.
6. *What it accepts*: the server validates every answer against the result schema, whatever `result_schema` says. An answer that does not validate, a refusal text, an HTTP error, and a timeout each end the attempt like a worker without a valid result; an endpoint that does not answer its readiness check is a start failure; the token counts of the answer's usage object are recorded per task and a missing count is `unmeasured`.
7. *Enrolment and the owner check*: an endpoint harness is enrolled like a harness started as a process (absolute path, digest and version of the serving program, checked against its update evidence when the digest changes). Before each request the runtime checks that the process owning the listening socket of `base_url` runs the enrolled executable; another program on the port refuses the request before it is sent (fate `refused`, reason `harness_not_enrolled`). The runtime never starts, stops, or configures the serving program.
8. *Weights identity*: where the `weights_probe` of a profile (an endpoint profile's, or `headless.weights_probe` of a client profile) is not `null`, the identity of the weights of every allowed model is read at enrolment, at every start of the runtime, and at every project setup run, and recorded in the harness enrolment beside the model; it is a member of the qualification digest, so a model whose weights changed is unqualified until graded again, and the change is audited (`harness_updated`, naming the model and both identities).
9. *Verification of an endpoint*: the stages `identity`, `launch`, `classes`, and `limits` of `pm-operator profile verify` and of the onboarding's `harness.verify-profile`, decided from what the runtime observes: the version probe, the owner of the listening socket, the models listing and the weights identity; readiness and the base task with streaming off, the contradicted result schema and the request without a schema (`result_schema` `enforced`, `advisory`, or `none`); the tool-call probe, recorded as `tools_ignored` and never a reason to wire the endpoint to a tool caller; the bisection of `context_tokens`. A failed `identity` or `launch` refuses the draft. The states of the onboarding that call the stages are `machine-onboarding-unit`'s (a coupling recorded on that edge, not as a dependency).
10. *Left out*: the protocol `system_one` and the remote form (`judgment-endpoint`); the role views of the clients and the user documentation (`pm-mcp-clients`, the documentation repository); the screening that runs on an endpoint (`start-source-intake`).

**Done when** (the package's own acceptance): The fixtures of PM-WATCH-CLIENT-14 pass: a stub endpoint that streams unless told not to is asked with streaming off; one that returns prose under a schema ends the attempt and leaves a screening `unscreened`; one that answers a `tools` request with an invented result is recorded `tools_ignored`; one whose weights identity changes between two runtime starts makes the record stale and audits `harness_updated`; a stub listening on the profile's port from an executable other than the enrolled one is refused before any task text is sent. A test per validation refusal of the profile shows the draft refused with the member named, and a missing usage count is recorded `unmeasured`. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-2; PM-ARCH-2; PM-WORK-1; PM-WORK-6
- Specification: the sections the first bullet of _What_ names; Client Profiles § Profile Sets; § Profile Verification; Store Schemas § Harness Enrolment Store; Model Work § Role Artifact
- Watch: Client Profiles

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `03-endpoint-harness`, `endpoint harness`, `profile_kind`, and the index has no row for `endpoint-harness` — `grep` of `doc/plans/` and `ls doc/plans/endpoint-harness.adoc` at `4617865`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -il of 03-endpoint-harness, endpoint harness, profile_kind over doc/plans at 4617865 finds nothing; no row and no file for endpoint-harness
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/client-profiles/03-endpoint-harness.adoc`, `doc/implementation-watch/client-profiles.adoc`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: anchors found at 4617865 in: implementation-watch/client-profiles.adoc: PM-WATCH-CLIENT-14
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-26, PLAN-29, PLAN-34; PLAN-19 shipped as #137); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: queue rows at 4617865: PLAN-96 staged, PLAN-19 shipped, PLAN-26 staged, PLAN-29 staged, PLAN-34 staged; PLAN-96 is staged and writes the package files
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 6 OBSERVED surface paths checked at 4617865, all exist
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `doc/plans/endpoint-harness.adoc` — the package file, written by PLAN-96; deleted when the package is done
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/` — the endpoint profile beside the client profile (the client profile loader of PLAN-19, #137, lives there: 17 classes and the package-info; the endpoint profile goes beside it)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/` — the harness enrolment: the executable owner check and the weights identity
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` — the launch of a task (`package-info.java` only today)
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/` — the HTTP adapter of the endpoint harness (new package; the HTTP call belongs to `pm-runtime`, behind an interface `pm-core` declares)
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-endpoint.adoc` — developer documentation of the new package
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-endpoint/` — ADRs of the package's design decisions; new directory
- OBSERVED: `doc/specification/client-profiles/03-endpoint-harness.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — PM-WATCH-CLIENT-14 retired with the package

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-26 (`role-sets-and-model-caller`); PLAN-29 (`worker-launch`); PLAN-34 (`role-qualification`)
- Blocks: PLAN-98
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-97-endpoint-harness.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
