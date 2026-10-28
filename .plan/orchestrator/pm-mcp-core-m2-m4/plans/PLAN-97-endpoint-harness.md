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

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

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

**Done when** (the package's own acceptance): The fixtures of PM-WATCH-CLIENT-14 pass: a stub endpoint that streams unless told not to is asked with streaming off; one that returns prose under a schema ends the attempt and leaves a screening `unscreened`; one that answers a `tools` request with an invented result is recorded `tools_ignored`; one whose weights identity changes between two runtime starts makes the record stale and audits `harness_updated`; a stub listening on the profile's port from an executable other than the enrolled one is refused before any task text is sent. A test per validation refusal of the profile shows the draft refused with the member named, and a missing usage count is recorded `unmeasured`. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names `PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33`, `-43`, `-48`, `-55`, `-64` or `-65` is the sibling's own links to watch items deleted by #190 and #196, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-2; PM-ARCH-2; PM-WORK-1; PM-WORK-6
- Specification: the sections the first bullet of _What_ names; Client Profiles § Profile Sets; § Profile Verification; Store Schemas § Harness Enrolment Store; Model Work § Role Artifact
- Watch: Client Profiles

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `03-endpoint-harness`, `endpoint harness`, `profile_kind`, and the index has no row for `endpoint-harness` — `grep` of `doc/plans/` and `ls doc/plans/endpoint-harness.adoc` at `4617865`
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: doc/plans/endpoint-harness.adoc exists since #143 (10 What bullets), row open, Depends role-qualification, Milestone 14; unchanged since 88ec9c70; the spec still says it is written by PLAN-96
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/client-profiles/03-endpoint-harness.adoc`, `doc/implementation-watch/client-profiles.adoc`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: client-profiles/03-endpoint-harness.adoc exists unchanged; PM-WATCH-CLIENT-14 now at implementation-watch/client-profiles.adoc:152 (moved when #190 deleted CLIENT-13)
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-26, PLAN-29, PLAN-34; PLAN-19 shipped as #137); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: PLAN-96 (#143), PLAN-26 (#146) and PLAN-29 (#172) are shipped; only PLAN-34 role-qualification is still open and the sole dependency in the index
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: runtime/launch holds 19 classes (HarnessEnrolment, HarnessEnrolmentStore, HarnessVerifier, LaunchComposer ...); the harness enrolment is there, not in runtime/enrolment; runtime/endpoint does not exist; core/profile holds 17 classes, none handles profile_kind
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row Milestone 14; roadmap item Implement the endpoint harness unchecked on documentation origin/main (PM-CLIENT-2, PM-WORK-6, PM-WATCH-CLIENT-14)
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: package file and row exist (#143); PLAN-96 landed; PLAN-34 still open; the open-PR list is not readable read-only

## Expected Surface

- OBSERVED: `doc/plans/endpoint-harness.adoc` — the package file (exists since #143), deleted when the package is done
- OBSERVED: `doc/specification/client-profiles/03-endpoint-harness.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — Profile Sets and Profile Verification sections
- OBSERVED: `doc/specification/store-schemas/11-enrolment-store.adoc` — Harness Enrolment Store, weights map
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — PM-WATCH-CLIENT-14 retired with the package
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` — endpoint profile validation (profile_kind, locality combinations, answer_reserve_tokens)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileStages.java` — endpoint stages identity, launch, classes, limits
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/VerificationRecord.java` — verification record of the endpoint stages
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileLoader.java` — loads a profile document by profile_kind
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/EndpointProfile.java` — the endpoint profile record the text names
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/HarnessUsage.java` — answer usage counts, a missing count is unmeasured
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/RoleDigests.java` — weights_identity member of the qualification digest
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/HarnessEnrolment.java` — enrolment with the weights map
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/HarnessEnrolmentStore.java` — weights identity recorded beside the model
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/HarnessVerifier.java` — harness_not_enrolled and harness_updated, owner check, weights audit
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java` — request composition seam
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/PromptComposer.java` — launch prompt of a zero_tool worker as the system message
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchRefusal.java` — refusal harness_not_enrolled before the request is sent
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/` — new package, HTTP adapter on the loopback interface
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java` — a test per validation refusal
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileStagesTest.java` — endpoint stage fixtures
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/HarnessVerifierTest.java` — owner check, harness_updated on a changed weights identity
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/HarnessEnrolmentStoreTest.java` — weights map in the enrolment
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/` — new test package, stub endpoint fixtures of PM-WATCH-CLIENT-14
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-endpoint.adoc` — developer documentation of the new package
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-endpoint/` — ADRs of the new package, new directory

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07` (the specification pull requests #181, #184, #186 to #189, #191 and #192 and the code plans #185 and #190 have landed since the pass of `88ec9c70`). This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 6: 2 corroborated, 4 contradicted (re-scoped by this section), 0 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: `doc/plans/endpoint-harness.adoc` exists since #143 (10 What bullets equal the 10 Deliverables by count), unchanged since `88ec9c70`; the Objective's "written by PLAN-96, must land first" is spent.
- Claim 2: PLAN-96, PLAN-26 and PLAN-29 are shipped; only PLAN-34 `role-qualification` is open and the index's sole dependency (the edge is partly built: `RoleDigests.qualification` already carries `weights_identity`).
- Claim 3: the harness enrolment with its weights map is `runtime/launch/HarnessEnrolment`, `HarnessEnrolmentStore`, `HarnessVerifier` (19 classes in `launch/`), **not** `runtime/enrolment/` (which holds the project-enrolment integrity classes, e.g. `HarnessesSidecarCheck`); `runtime/endpoint` does not exist; `core/profile` holds 17 classes, none handles `profile_kind`.
- Claim 5: package file and row exist; PLAN-34 is still open; the open-PR list is not readable.

### Surface

- Added to the Expected Surface above: `ProfileValidator`, `ProfileStages`, `VerificationRecord` (`core.profile`), `HarnessEnrolment`, `HarnessEnrolmentStore`, `HarnessVerifier` (`runtime.launch`), the ADR directories `runtime-launch/` (006) and `core-profile/` (006), `doc/LogMessages.adoc`.
- wrong: `pm-runtime/.../runtime/enrolment/` is annotated as where the harness enrolment lives; it is `runtime/launch/`; path stays, annotation wrong.
- wrong: `pm-runtime/.../runtime/launch/` is annotated "package-info.java only"; it holds 19 classes.
- wrong: `doc/plans/endpoint-harness.adoc` is annotated HYPOTHESIS "written by PLAN-96"; it exists.

### Specification changes carried (not yet in the Deliverables above)

- #187 (`f287eb50`) — Client Profiles 01 § headless block and § Profile Verification: a new required member `headless.readable_paths` and a new stage `paths` (a decoy file outside the read set), a condition of `worker.eligible`. It binds the client profile's headless block, **not** the endpoint profile (no headless block; stages identity, launch, classes, limits). `readable_paths` is absent from the code; the open package `job-confinement-and-environment` will edit the same `ProfileValidator` and `ProfileStages`: an overlap to sequence, not a dependency, and the `paths` stage must not apply to an endpoint.
- #190 (`f4a1bd07`) — Client Profiles 01 § Runtime check on load: `ProfileWorkerEligibility` in `runtime.worker` consults the record before every start; `PM-WATCH-CLIENT-13` deleted. An endpoint profile has no worker block (open question below).
- #185 (`71a15338`) — Store Schemas 11 § Harness Enrolment Store, "Unreadable Store": an enrolment entry whose required object member (`events`, `usage_report.figures`, a present `weights`) is absent, `null` or not an object is refused `harness_store_unreadable`; the endpoint enrolment's weights map is under this rule (the code exists since #172).
- #188 (`854a7c7a`) — Model Work 10 § Model Identity: quorum voters are compared by the weights identities in the enrolments, so the weights this plan reads must be exact and comparable.

### Shipped or superseded since the last grounding

- PLAN-29 (#172): `HarnessEnrolment.weights`, `HarnessEnrolmentStore`, `HarnessVerifier`, `LaunchRefusal.HARNESS_NOT_ENROLLED`, `AuditEvent.HARNESS_UPDATED` (process harnesses only). PLAN-31: `HarnessUsage` with `unmeasured`. #190: `WorkerParameters`, `VerificationRecord.tool`, `ProfileWorkerEligibility`, `ProbeWorkspace`, `DecoyServers` (loopback sockets), `VerifyingBinaryCheck`. Still absent: `profile_kind`, the listening-socket owner check, any reader of `weights_probe`, `runtime/endpoint`.
- Dependencies: PLAN-96, PLAN-26, PLAN-29 shipped; PLAN-34 open (PLAN-97 is ready once it lands). `PM-WATCH-CLIENT-14` present (client-profiles.adoc:152).

### Open questions for the outline

- Does an endpoint harness pass or bypass the #190 load-time gate `ProfileWorkerEligibility` (it reads the `worker` block through `WorkerParameters`; an endpoint profile has none)?
- Order against `job-confinement-and-environment` on `ProfileValidator`/`ProfileStages`.
- Does an endpoint enrolment carry `usage_report.start_context` (#174)? `module-structure.adoc` lists no `runtime.endpoint` package.
- Is the PLAN-34 edge still needed, given `weights_identity` is already in `RoleDigests`?

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 6: 2 corroborated, 4 contradicted (re-scoped by this section), 0 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/endpoint-harness.adoc exists at HEAD with the 10 bullets of this spec and an index row (open; depends role-qualification; Milestone 14); added by #143 9c9d99c6 (PLAN-96)
- Claim 2: PLAN-96 landed as #143 9c9d99c6; PLAN-26 shipped as #146 964f673d; PLAN-29 shipped as #172 4806a304; only PLAN-34 (role-qualification) is still an open row and README at HEAD lists it as the sole dependency
- Claim 3: pm-runtime/.../launch/ is not package-info only: it holds HarnessEnrolment; HarnessEnrolmentStore; HarnessVerifier; LaunchComposer; WorkerLaunch and about 20 more types (#172); the harness enrolment with its weights map is in launch/ and not in runtime/enrolment/ (project enrolment integrity classes and HarnessesSidecarCheck); runtime/endpoint does not exist yet (new); core/profile (17 classes) exists
- Claim 5: same account as claim 0: package file and index row exist at HEAD (#143 9c9d99c6); the PR #71 part could not be re-read here

### Surface

- Added to the Expected Surface above: `pm-core/src/main/java/de/planmarshall/core/store/HarnessUsage.java`, `pm-core/src/main/java/de/planmarshall/core/role/RoleDigests.java`, `doc/specification/client-profiles/01-profile-schema.adoc`, `doc/specification/store-schemas/11-enrolment-store.adoc`, `pm-core/doc/developer/core-profile.adoc`, `pm-runtime/doc/developer/runtime-launch.adoc`.
- wrong: runtime/enrolment is the project enrolment integrity package (HarnessesSidecarCheck only for harnesses); the harness enrolment is pm-runtime/.../runtime/launch/HarnessEnrolment.java; HarnessEnrolmentStore.java; HarnessVerifier.java (replace the path)
- wrong: runtime/launch is no longer package-info only; add LaunchComposer; PromptComposer; WorkerLaunch; LaunchRefusal as the request and refusal seam to extend
- understated: add pm-core/src/main/java/de/planmarshall/core/role/RoleDigests.java and EffectiveRole.java (weights identity already in the qualification digest) and pm-core/.../store/HarnessUsage.java (unmeasured usage)
- understated: spec parts to delete from but not listed: doc/specification/client-profiles/01-profile-schema.adoc (Profile Sets; Profile Verification) and doc/specification/store-schemas/11-enrolment-store.adoc (Harness Enrolment Store); docs to extend: pm-core/doc/developer/core-profile.adoc; pm-runtime/doc/developer/runtime-launch.adoc (adr dirs core-profile and runtime-launch exist)
- understated: doc/plans/endpoint-harness.adoc is not hypothetical any more: exists at HEAD from #143 (state it as OBSERVED)

### Specification changes carried (not yet in the Deliverables above)

- #174 — store-schemas/11 Harness Enrolment Store (usage_report.start_context; events): Say whether an endpoint enrolment carries start_context (its four stages run no usage stage) so HarnessEnrolment.UsageReport and HarnessEnrolmentStore in pm-runtime/.../launch/ stay consistent; add those two files to the surface
- #171 — model-work/01 Judge Instrument Check (judge exempt from qualification but checked as an instrument via judge-check.json): Reconcile D4 (a judge on an endpoint is ungraded) with the instrument check that binds to the qualification digest incl. weights identity; correct client-profiles/03 line 23 if needed

### Shipped or superseded since the last grounding

- partly fixed: weights identity in enrolment and digest: HarnessEnrolment.weights (Map of Weights with weightsId and readAt) and HarnessEnrolmentStore (#172 4806a304); RoleDigests puts weights_identity into the qualification digest and EffectiveRole.weightsIdentity (#146 964f673d); no code reads a weights probe
- partly fixed: refusal and audit ids: LaunchRefusal and HarnessVerifier already use harness_not_enrolled and audit harness_updated for process harnesses (#172); the listening-socket owner check is absent (no socket owner code in pm-runtime)
- partly fixed: client profile weights_probe validation: ProfileValidator.weightsProbe validates headless.weights_probe; no profile_kind or endpoint profile exists

### Open questions for the outline

- the package file is written by PLAN-96 in the spec text but exists since #143; Deliverables and Expected Surface need rewording and the PLAN-96 dependency removal
- module-structure.adoc (documentation repository) lists no runtime.endpoint package and no endpoint harness; the spec does not say who registers it

## Dependencies and Sequencing

- Depends on: PLAN-96 (#143), PLAN-26 (#146), PLAN-29 (#172) and PLAN-34 (`role-qualification`, #196), all shipped; nothing else
- Blocks: PLAN-98
- Runs beside (runbook adaptation 2026-10-27, standing rule R19): PLAN-61 (`token-usage`, running) and PLAN-43 (`phase-sub-workflows`, emitted with this plan; no shared file). **A real code overlap with PLAN-61, accepted by the orchestrator's judgement and disclosed here:** seven files (`ProfileStages`, `ProfileStagesTest`, `VerificationRecord`, `HarnessEnrolment`, `HarnessEnrolmentStore`, `HarnessEnrolmentStoreTest`, `HarnessUsage`): PLAN-61 adds the `usage` stage figures and `start_context` to the usage report, this plan adds the endpoint stages and the weights map use; PLAN-61's pull request (#204) is open and is expected to merge first. PLAN-73's merged change (#201) also touched `ProfileStages` (the `resumability` stage result). **Read at the head of `main` before editing, and merge the base branch (the lane's step 7) before the merge gate.** This plan serves Milestone 14, outside the milestones 2 to 4 of the epic: it is emitted to use the free slot because it is disjoint from the other ready plans. **ADR numbers are taken at write time** against `origin/main` (R11): `core-profile` 007, `runtime-launch` 007, a new `runtime-endpoint` (001). The open questions of the Re-grounding Record (does an endpoint pass the #190 load-time gate, the order against `job-confinement-and-environment` on `ProfileValidator`/`ProfileStages`, `start_context` in the endpoint enrolment) are asked at refine.
## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-97-endpoint-harness.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
