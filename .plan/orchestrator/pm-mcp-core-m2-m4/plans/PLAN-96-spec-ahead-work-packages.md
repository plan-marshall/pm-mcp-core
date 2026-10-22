# PLAN-96: Cut the work packages for the specification no package builds

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-96-spec-ahead-work-packages.md` and is queued as one row file, `queue/PLAN-96.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

The operator's three specification-only merges of 2026-10-21 (#132 to #134) and the specification before them added content no work package builds: the endpoint harness (`client-profiles/03`), the judgment endpoint (`client-profiles/04`), the judgment role as a voter of the decision quorum, the own calls of a writer, and the change map. The operator decided on 2026-10-22 that every specification must be implemented, so each becomes a work package. Write the five package files of `doc/plans/` and their five rows in `doc/plans/README.adoc`; the implementation plans PLAN-97 to PLAN-101 build them and wait on this one. Documents only: no code, no specification change.

## Deliverables

1. **The five package files**, each exactly as the section *Package Files* below gives it (`doc/plans/endpoint-harness.adoc`, `judgment-endpoint.adoc`, `judgment-votes.adoc`, `own-calls.adoc`, `change-map.adoc`). The text is the brief of the later plan; do not rewrite its substance. Correct a link or an anchor that does not resolve, and say in the pull request body which.
2. **The five rows of the index**, appended after the last row of the table in `doc/plans/README.adoc` in the order of the section *Index Rows*. Before writing, re-read the index at the head of `main`: a dependency that is done by then (its row gone from the table, its file deleted) is dropped from the rows; the cell of a `blocked` row keeps its note `(no work package)` for what has none.
3. **The links check, recorded.** Run `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .` from the worktree: `BROKEN LINKS 0`.
4. **Nothing is owed outside this repository by this plan.** The roadmap of `plan-marshall-documentation` already has an item for each area (the own calls in Milestone 9, the change map in Milestone 10, the endpoint harness and the protocol `system_one` with the judgment vote in Milestone 14); their check-off is written at the milestone exit.

**Done when:** the five package files exist with the text of this spec and every link of them resolves; the index has the five rows with the statuses and dependencies given; the links check reports `BROKEN LINKS 0` and the run is recorded in the pull request body. No file other than the five package files and `doc/plans/README.adoc` changes.

**Trace starting set:** the package files are cut from the specification parts they name (each file's own Trace section) and from the roadmap items their *Serves* line names.

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.
- **Checklist for durable multi-step protocols in `pm-runtime` and `core.store` (found by PLAN-74, 2026-10-18; four review rounds each found another unguarded transition of the credential-store move).** (1) A resume decides from the pair (record phase, state actually recorded), never from the phase alone, and a guard added to one phase branch gets its symmetric guard on every other branch; enumerate (phase x recorded state x writer) with a test per cell before the first review. (2) While a durable in-progress record exists, every other writer of the state it switches is refused under the same lock, and an authorization is verified against durable state, never asserted by the caller. (3) The record that drives recovery is deleted last, after every side effect (audit event, clean-up after a failure) is durable. (4) A decision with side effects (which store, which key) is decided once and persisted, never re-derived per call from a probe that can flap. (5) A copy-then-switch move has a test with a pre-populated target (an overlapping key and a target-only key) and a failure injected after the copy; the pre-existing-target hazard is checked for every kind of object the protocol copies (entries, integrity key, sidecars) in the same task. (6) A store's temp-file namespace is disjoint from its legal entry-name namespace. (7) A fix of a state-machine transition lists the specification section, the ADR, the Javadoc and the test that state it among its affected files and updates them in the same commit; ADR and test are checked against the specification text, not against the code they document. (8) The invariants of a multi-write protocol are stated per crash window, not per phase. (9) A test that asserts a file location derives the expected path from the production layout independently, never from the object under test. (10) A native binding is loaded once; only what can change at runtime (a lock state) is probed per operation.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: none: documents only. A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.
- **Sibling checkouts from the plan worktree (found by PLAN-72, 2026-10-17).** The plan runs in `.plan/local/worktrees/{plan}/`, three directories deeper than the main checkout, so `../plan-marshall-documentation` (as `CLAUDE.md` and the links of the package file write it) does not resolve there. Run the links check with the absolute paths of the sibling checkout, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .`, and record the run so the functional review finds it.

## Package Files

### `doc/plans/endpoint-harness.adoc`

````asciidoc
= A model on this machine: the endpoint harness
:nofooter:

link:README.adoc[Work packages of `pm-mcp-core`] » `endpoint-harness`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 14 (roadmap item _Implement the endpoint harness_, `PM-CLIENT-2`, `PM-WORK-6`); the level-2 screener of Milestone 7 (`PM-SEC-1`) is the first role it can carry
Where:: pm-core (the endpoint profile: schema, validation, the sets and overrides it shares with the client profile); pm-runtime (the HTTP adapter on the loopback interface, the owner check of the listening socket, the weights identity, the verification stages)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Implements* link:../specification/client-profiles/03-endpoint-harness.adoc[Client Profiles § Endpoint Harness]: A Model on This Machine, Endpoint Profile Schema, The Request, Enrolment, Weights Identity, Verification of an Endpoint (watch link:../implementation-watch/client-profiles.adoc#PM-WATCH-CLIENT-14[PM-WATCH-CLIENT-14]).
* *The endpoint profile*: a document of the profile sets with the member `profile_kind: "endpoint"`, its `client_id` in the namespace of the client profiles; no `tools`, `skill_loading`, `headless`, `connection`, `detection`, or `worker` block; no profile shipped. Validation applies the rules of a client profile where they concern the same members and refuses an `answer_reserve_tokens` not below `context_tokens`, a `weights_probe` that fills the members of the other source, and the combinations of `locality` the schema cannot express (`loopback` with an `https` `base_url`, with a `credential_key`, or without `executable` and `identity`; `remote` with the protocol `openai_chat`, with an `http` `base_url`, without a `credential_key`, or with an `executable`, `identity`, or `update_evidence`).
* *Loopback only here*: `base_url` names `127.0.0.1` or `[::1]`. The protocol `system_one` and the remote form are link:judgment-endpoint.adoc[`judgment-endpoint`]'s; this package admits the members for them in the schema (`protocol`, `locality`, `credential_key`, the nullable `executable`, `identity`, `update_evidence`) and refuses a profile that uses them until that package is done.
* *The class rule*: an endpoint harness runs exactly the role class `zero_tool` and no other; the server marks a `judge` assigned to it as ungraded wherever the assignment is shown (the role views of both clients are `pm-mcp-clients`').
* *The request*: one HTTP request per task, composed by the server: the launch prompt of a `zero_tool` worker as the system message and the task input as the user message, the model of the role's effective configuration, streaming explicitly off, the role's result schema as the response format where `result_schema` is `enforced` or `advisory`, no `tools` member and no tool choice, and every request value the endpoint has a default for sent explicitly. The role's context budget is `context_tokens` less `answer_reserve_tokens`; a screening batch is packed to it; a role artifact whose launch prompt alone does not fit is invalid on that harness.
* *What it accepts*: the server validates every answer against the result schema, whatever `result_schema` says. An answer that does not validate, a refusal text, an HTTP error, and a timeout each end the attempt like a worker without a valid result; an endpoint that does not answer its readiness check is a start failure; the token counts of the answer's usage object are recorded per task and a missing count is `unmeasured`.
* *Enrolment and the owner check*: an endpoint harness is enrolled like a harness started as a process (absolute path, digest and version of the serving program, checked against its update evidence when the digest changes). Before each request the runtime checks that the process owning the listening socket of `base_url` runs the enrolled executable; another program on the port refuses the request before it is sent (fate `refused`, reason `harness_not_enrolled`). The runtime never starts, stops, or configures the serving program.
* *Weights identity*: where the `weights_probe` of a profile (an endpoint profile's, or `headless.weights_probe` of a client profile) is not `null`, the identity of the weights of every allowed model is read at enrolment, at every start of the runtime, and at every project setup run, and recorded in the harness enrolment beside the model; it is a member of the qualification digest, so a model whose weights changed is unqualified until graded again, and the change is audited (`harness_updated`, naming the model and both identities).
* *Verification of an endpoint*: the stages `identity`, `launch`, `classes`, and `limits` of `pm-operator profile verify` and of the onboarding's `harness.verify-profile`, decided from what the runtime observes: the version probe, the owner of the listening socket, the models listing and the weights identity; readiness and the base task with streaming off, the contradicted result schema and the request without a schema (`result_schema` `enforced`, `advisory`, or `none`); the tool-call probe, recorded as `tools_ignored` and never a reason to wire the endpoint to a tool caller; the bisection of `context_tokens`. A failed `identity` or `launch` refuses the draft. The states of the onboarding that call the stages are link:machine-onboarding-unit.adoc[`machine-onboarding-unit`]'s (a coupling recorded on that edge, not as a dependency).
* *Left out*: the protocol `system_one` and the remote form (`judgment-endpoint`); the role views of the clients and the user documentation (`pm-mcp-clients`, the documentation repository); the screening that runs on an endpoint (`start-source-intake`).

== Trace

Requirements stay in `plan-marshall-documentation`; the specification sections and watch documents are those of this repository, or of `plan-marshall-documentation` where a link leads there. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/13-client-profiles.adoc#PM-CLIENT-2[PM-CLIENT-2]; link:../../../plan-marshall-documentation/doc/requirements/01-architecture.adoc#PM-ARCH-2[PM-ARCH-2]; link:../../../plan-marshall-documentation/doc/requirements/16-model-work.adoc#PM-WORK-1[PM-WORK-1]; link:../../../plan-marshall-documentation/doc/requirements/16-model-work.adoc#PM-WORK-6[PM-WORK-6]
* Specification: the sections the first bullet of _What_ names; link:../specification/client-profiles/01-profile-schema.adoc#profile-sets[Client Profiles § Profile Sets]; link:../specification/client-profiles/01-profile-schema.adoc#profile-verification[§ Profile Verification]; link:../specification/store-schemas/11-enrolment-store.adoc#harness-enrolment-store[Store Schemas § Harness Enrolment Store]; link:../specification/model-work/01-roles-and-configuration.adoc#role-artifact[Model Work § Role Artifact]
* Watch: link:../implementation-watch/client-profiles.adoc[Client Profiles]

== Done When

The fixtures of PM-WATCH-CLIENT-14 pass: a stub endpoint that streams unless told not to is asked with streaming off; one that returns prose under a schema ends the attempt and leaves a screening `unscreened`; one that answers a `tools` request with an invented result is recorded `tools_ignored`; one whose weights identity changes between two runtime starts makes the record stale and audits `harness_updated`; a stub listening on the profile's port from an executable other than the enrolled one is refused before any task text is sent. A test per validation refusal of the profile shows the draft refused with the member named, and a missing usage count is recorded `unmeasured`.
````

### `doc/plans/judgment-endpoint.adoc`

````asciidoc
= A first-level thinker as an endpoint: the protocol `system_one`
:nofooter:

link:README.adoc[Work packages of `pm-mcp-core`] » `judgment-endpoint`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 14 (the first half of the roadmap item _Implement the protocol `system_one` of the endpoint harness and the judgment vote_: the protocol, the remote form, the verification; `PM-CLIENT-2`, `PM-WF-18`, `PM-CRED-5`)
Where:: pm-runtime (the adapter of the protocol `system_one`, the remote form with its Token Lane credential, the verification differences); pm-core (the profile members it admits, in the endpoint profile of `endpoint-harness`)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Implements* link:../specification/client-profiles/04-judgment-endpoint.adoc[Client Profiles § Judgment Endpoint]: The Request and the Answer, Remote Form, Verification of a Judgment Endpoint (watch link:../implementation-watch/client-profiles.adoc#PM-WATCH-CLIENT-15[PM-WATCH-CLIENT-15]).
* *The request*: a task is two requests, `POST <base_url>/v1/systemone` with `Content-Type: application/json`, no compression, the profile's `request_timeout_seconds`, and `Authorization: Bearer <secret>` in the remote form. The body holds the `model` (the versioned id of the role), the `facts` of the vote entity as `state`, and one question of the type `choice` per question of the entity, keyed by its `question_id`, with its options as `criteria` keyed by `branch_key`. The first request lists the options sorted by `branch_key`, the second in the opposite order. It sends nothing else (no recommendation, no answer of another role, no tools) and never a header that opts into a slower or less accurate mode; the entity is packed to `context_tokens` less `answer_reserve_tokens`.
* *The answer*: the server reads `answers.<question_id>` with `choice` and `probabilities`, the member `model`, and `usage`, and never the `confidence` member. An answer is valid when its `model` equals the requested id, it holds an answer for every question, each `choice` is a `branch_key` of the question, and its `probabilities` name only the question's options, each between `0` and `1`, summing to `1` within `0.01`. An invalid answer, an HTTP error (`401`, `422`, `429`, `529`, a timeout, a body the runtime did not expect), and a refusal end the attempt like a worker without a valid result; **the runtime retries nothing inside an attempt**, the task's re-offer budget decides.
* *Versioned ids only*: the verification sends the model id of the role and refuses an id the service answers with another (`model_alias`); every answer is checked for the requested id, so a silent re-route ends the attempt; the model catalogue holds the versioned id, and a new release is another id, unqualified until graded.
* *The remote form* (`locality: "remote"`): the HTTPS origin of one service, enrolled by the operator's act only (a project's configuration can neither add one nor change its `base_url`). The secret is the Token Lane credential entry `credential_key`, read at the moment of a request and sent only to the origin of the entry's `url`; `base_url` must equal that origin, a redirect to another origin or to `http` ends the attempt, and the secret is registered with the redaction registry. There is no serving program, so `executable`, `identity`, and `update_evidence` are `null` and no owner check applies; the harness enrolment records the origin and the credential key; an unreachable origin, a refused key, or a failed verification is a start failure of the role. No screener runs on a `system_one` endpoint of the remote form.
* *The request body is never logged*: logs and the audit record hold the endpoint, the model id, the status, the token counts, and the duration; no log line, audit line, or error text holds a body or the key.
* *Verification of a judgment endpoint*: the stages of an endpoint with these differences: `identity` reads the models listing with the credential and checks that the role's model is listed (a loopback form also runs the version probe and the owner check); `launch` sends one typed request of two options in both orders, checks that the answer validates, that its `model` equals the requested id, and that both orders name the same options, sends a request whose criteria the instructions contradict and a request with one option only and records the answers, and refuses a service that answers free text (`result_schema` is `enforced`); `classes` does not apply; `limits` bisects between `1024` tokens and the service's refusal (`400` with `max_tokens_exceeded`), counting with `usage.input_tokens`, and on a remote form only on the operator's explicit choice, because every request is billed.
* *Left out*: the vote composed from the two answers, the class rule of a declaration, and the calibration (`judgment-votes`); the endpoint profile and the loopback harness (`endpoint-harness`); the operator's entry of the credential (`PM-CRED-1`, shipped); the user documentation that tells the operator to read the service's terms.

== Trace

Requirements stay in `plan-marshall-documentation`; the specification sections and watch documents are those of this repository, or of `plan-marshall-documentation` where a link leads there. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/13-client-profiles.adoc#PM-CLIENT-2[PM-CLIENT-2]; link:../../../plan-marshall-documentation/doc/requirements/02-workflow.adoc#PM-WF-18[PM-WF-18]; link:../../../plan-marshall-documentation/doc/requirements/01-architecture.adoc#PM-ARCH-2[PM-ARCH-2]; link:../../../plan-marshall-documentation/doc/requirements/07-credentials.adoc#PM-CRED-1[PM-CRED-1]; link:../../../plan-marshall-documentation/doc/requirements/07-credentials.adoc#PM-CRED-5[PM-CRED-5]
* Specification: the sections the first bullet of _What_ names; link:../specification/client-profiles/03-endpoint-harness.adoc#endpoint-profile-schema[Client Profiles § Endpoint Profile Schema]; link:../specification/credentials-and-enrolment/01-provider-credentials.adoc[Credentials & Enrolment § Provider Credentials]; link:../specification/store-schemas/11-enrolment-store.adoc#harness-enrolment-store[Store Schemas § Harness Enrolment Store]
* Watch: link:../implementation-watch/client-profiles.adoc[Client Profiles]; link:../implementation-watch/model-work.adoc#PM-WATCH-DISP-63[PM-WATCH-DISP-63] (the vote built on it is link:judgment-votes.adoc[`judgment-votes`]'s)

== Done When

The fixtures of PM-WATCH-CLIENT-15 pass: a stub that answers an alias request with a versioned id is refused (`model_alias`); one that answers `429` with a `retry-after` ends the attempt and is not retried inside it; one that redirects to another origin or to `http` receives no secret and the attempt ends (matched control: the entry's own origin succeeds); one whose probabilities do not sum to `1` gives an invalid answer; a fixture whose answer echoes the request body leaves neither the body nor the key in any log line, audit line, or error text. The two requests of a task list the same options in opposite orders.
````

### `doc/plans/judgment-votes.adoc`

````asciidoc
= A judgment role as a voter of the decision quorum
:nofooter:

link:README.adoc[Work packages of `pm-mcp-core`] » `judgment-votes`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 14 (the second half of the roadmap item _Implement the protocol `system_one` of the endpoint harness and the judgment vote_: the vote, the grading, the form in the calibration record), after the decision quorum of Milestone 8, whose roadmap items have no work package yet
Where:: pm-workflow (the voter of the adjudication, the composition check of a declaration); pm-core (the members of the decision record and of the calibration record it fills)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Implements* link:../specification/model-work/10-decision-quorum.adoc#judgment-vote[Model Work § Judgment Votes], the class rule of link:../specification/client-profiles/04-judgment-endpoint.adoc#judgment-endpoint[Client Profiles § Judgment Endpoint] in the composition check of link:../specification/model-work/01-roles-and-configuration.adoc#role-composition[Roles & Configuration], and the member `form` of the role configuration in link:../specification/store-schemas/16-task-queue.adoc#decision-calibration-record[Store Schemas § Decision Calibration Record] (watch link:../implementation-watch/model-work.adoc#PM-WATCH-DISP-63[PM-WATCH-DISP-63]).
* *Parts it votes on*: a part of the answer type `one`. A set that holds a part of another type for a kind whose declaration lists the judgment role ends the role's task `infeasible`, and the set escalates with `role_unavailable`.
* *Two orders, one vote*: the first request lists the options sorted by `branch_key`, the second in the opposite order, both carrying every question of the entity. For each question the choice `c` is the option the first request chooses; the confidence is the integer part of `100` times the lower of the probability of `c` in the first request and in the second. A second request that prefers another option gives `c` a probability below one half, so the vote stays below every score a declaration can name (`50` to `100`) and the set escalates (`below_score`, or `disagreement` if another role chose another option). The `confidence` member of the service's answer is never used.
* *A rationale the server composed*: `<model id>: <branch_key> at <p1> sorted, <p2> reversed`, with `rationale_origin: server`; a judgment role writes no prose, and no role of another kind reads the rationale as reasoning. The vote carries the versioned model id the answer named and both distributions in `probabilities` (`sorted` and `reversed`), so that a later model can be graded or trained on the decisions and the operator's final answers. The members of the vote record and their rendering are link:decision-engine.adoc[`decision-engine`]'s; this package fills them.
* *The class rule*: a judgment role serves the entity kinds that declare a typed form, today the kind `vote`; a kind of the text form (the screener's `screen`, the grading kind `zero_tool`) on a judgment role, and a typed kind on a role of the protocol `openai_chat`, is `class_mismatch` in the composition check.
* *Calibration form*: the role configuration of a calibration record carries `form` (`warm` or `fresh`; a judgment role is `fresh`) and, for a judgment role, `model` as the versioned id; the declaration digest covers the form. A judgment role answers a calibration item with its two requests, and the decision rule applies with the declared score. The calibration itself (the corpus items, the automatic share, the false automatic share, `calibration_failed`) is the quorum's.
* *Grading*: a judgment role is graded on the `exact_choice` decision items of its own topic before it decides, like every role of the class `zero_tool` (the mechanics are link:role-qualification.adoc[`role-qualification`]'s; the item kind is the roadmap's word and is confirmed against the corpus at the trace matrix).
* *What it can use*: only the entity's facts and options; a judgment role is one voter and never the only reason to trust a text that a fact quotes.
* *Left out*: the endpoint and its protocol (`judgment-endpoint`); the declarations, the adjudication unit, the decision rule, and the calibration run (the decision quorum, no work package); the rendering of the vote in both clients.

== Trace

Requirements stay in `plan-marshall-documentation`; the specification sections and watch documents are those of this repository, or of `plan-marshall-documentation` where a link leads there. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/02-workflow.adoc#PM-WF-18[PM-WF-18]; link:../../../plan-marshall-documentation/doc/requirements/13-client-profiles.adoc#PM-CLIENT-2[PM-CLIENT-2]; link:../../../plan-marshall-documentation/doc/requirements/16-model-work.adoc#PM-WORK-6[PM-WORK-6]
* Specification: the sections the first bullet of _What_ names; link:../specification/model-work/10-decision-quorum.adoc#blind-input[Model Work § Blind Input]; link:../specification/model-work/10-decision-quorum.adoc#quorum-calibration[§ Quorum Calibration]; link:../specification/store-schemas/18-decision-records.adoc#decision-record[Store Schemas § Decision Record]; link:../specification/model-work/11-decision-records.adoc[Model Work § Decision Records]
* Watch: link:../implementation-watch/model-work.adoc[Model Work]

== Done When

A fixture quorum whose roles are a judgment role (a stub of the protocol, both orders) and a worker role decides automatically when both choose the same option at or above the score, and escalates with `below_score` when the second order prefers another option, with `disagreement` when the roles differ, and with `role_unavailable` when a part is not of the type `one`; the vote record carries `rationale_origin: server`, the versioned model id, and both distributions, and a vote of a model carries `probabilities: null`; the composition check refuses a text-form kind on a judgment role and a typed kind on a role of the protocol `openai_chat` with `class_mismatch`; a calibration item answered by a judgment role records `form: fresh` and the versioned model id.
````

### `doc/plans/own-calls.adoc`

````asciidoc
= A writer states its own calls
:nofooter:

link:README.adoc[Work packages of `pm-mcp-core`] » `own-calls`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 9 (roadmap item _Implement the own calls of a writer_; `PM-WF-11`, `PM-WF-19`, `PM-TEST-6`)
Where:: pm-workflow (the parameter of `submit:TASK-NNN`, its checks, and the records); pm-core (the named parameter schema `own-call`, the decision record at the point `execute.own-call`)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Implements* link:../specification/phase-workflows/03-q-gate-and-execute.adoc#own-calls[Phase Workflows § Own Calls of a Writer], the schema `own-call` of link:../specification/hypermedia-format/02-link-grammar.adoc#named-parameter-schemas[Hypermedia Format § Named Parameter Schemas], the decision point `execute.own-call` of link:../specification/model-work/11-decision-records.adoc#decision-points[Model Work § Decision Points], and the `submit:TASK-NNN` parameter of link:../specification/state-catalogue/10-entity-units.adoc[State Catalogue § Entity Units] (watch link:../implementation-watch/phase-workflows.adoc#PM-WATCH-PHASE-113[PM-WATCH-PHASE-113]).
* *The submission*: `submit:TASK-NNN` carries the required parameter `own_calls:list<schema[own-call]>`, at most `10` items; an empty list states that the task settled nothing the plan left open. Each item carries `call` (`10..500` characters), `options` (one to four alternatives, each `1..200` characters; one alternative is the choice not to do it), `reason` (`10..500` characters, naming the evidence), `touches` (identifiers of the intent record and the outline: `AC-<n>`, `CN-<n>`, `NG-<n>`, `DC-<n>`, a deliverable number; may be empty), and `kind`: `design`, or `shortcut` with `site` (a path of the task's changed paths, with a line), `limit`, and `ends_when`.
* *The server checks*, each violation refused with `invalid_link_args` and `rejected.detail` naming the item: every identifier of `touches` exists in the plan's current intent record or outline; a `shortcut`'s `site` is a path of the task's Task Diff Record; and a call may not choose against a decision of the intent record whose decider is `operator` or `quorum` (such a choice is a task question, `ask:TASK-NNN`).
* *The records*: in the transaction that accepts the submit, each item becomes a decision record at the decision point `execute.own-call` with the decider `role`, its `options`, its `choice` (the `call`), and its `rationale` (the `reason`). A task that ends without its submit records no own call; a re-offer submits its own.
* *Reading is the change map's*: the pull-request body, the landing record, the completion report, and the self-review read the calls; that is the roadmap item of link:change-map.adoc[`change-map`] (Milestone 10), which also needs the module discovery. This package records the calls and nothing reads them yet.
* *Left to the content repository*: the guidance in the skill of the `implement` role (what an own call is and is not) and the writer scenarios for own calls and shortcuts are model-facing content and evaluation of `plan-marshall-mcp`.
* *Left out*: the reading of the calls at the pull request, the landing record, the completion report, and the self-review (`change-map`); the guidance of the `implement` role skill and the writer scenarios (`plan-marshall-mcp`); the rendering in both clients.

== Trace

Requirements stay in `plan-marshall-documentation`; the specification sections and watch documents are those of this repository, or of `plan-marshall-documentation` where a link leads there. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/02-workflow.adoc#PM-WF-11[PM-WF-11]; link:../../../plan-marshall-documentation/doc/requirements/02-workflow.adoc#PM-WF-19[PM-WF-19]; link:../../../plan-marshall-documentation/doc/requirements/12-testing.adoc#PM-TEST-6[PM-TEST-6]
* Specification: the sections the first bullet of _What_ names; link:../specification/workflow-dsl/07-loop-back-diagrams-verification.adoc[Workflow DSL § Shortcut Marker]
* Watch: link:../implementation-watch/phase-workflows.adoc[Phase Workflows]

== Done When

The guards of PM-WATCH-PHASE-113 pass: a submit without `own_calls` is refused; one with a `touches` identifier the records do not hold is refused; one with a call against a `DC` item of decider `operator` is refused and the detail names the item; one with a `shortcut` whose `site` is outside the Task Diff Record is refused; an empty list is accepted. A decision record exists per call at `execute.own-call` with the decider `role`, its options, its choice, and its rationale, written in the transaction that accepts the submit; a task that ends without its submit records none, and a re-offer submits its own.
````

### `doc/plans/change-map.adoc`

````asciidoc
= The change map of a large change
:nofooter:

link:README.adoc[Work packages of `pm-mcp-core`] » `change-map`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 10 (roadmap item _Implement the change map of a plan_; `PM-WF-13`, `PM-WF-21`), beside the Dependency Delta Check
Where:: pm-workflow (the computation, the facts of the finalize step, the judgment at the self-review); pm-core (the module discovery crawl it reads, over a tree of git objects)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Implements* link:../specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc#change-map[Phase Workflows § Change Map] and the removed-coordinates half of link:../specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc#dependency-delta[§ Dependency Delta Check] (watch link:../implementation-watch/phase-workflows.adoc#PM-WATCH-PHASE-114[PM-WATCH-PHASE-114]).
* *Two trees*: the in-process crawl of the module discovery runs over the tree at `base_ref`, read from git objects and never checked out, and over the worktree at `HEAD`. Enrolled discovery jobs are not run for the map; a module whose edges only such a job provides is drawn without them and marked `edges_partial`. The map is computed without a model, with the Dependency Delta Check, at Phase 6 entry and after every `finalize.settle-commit`.
* *The delta*: `modules_added[]`, `modules_removed[]`, `edges_added[]` and `edges_removed[]` (`from` module, `to` module), and the third-party coordinates of the dependency delta, `added[]` and `removed[]`, where a removed and an added coordinate of the same module and scope form a `replaced[]` pair. A module whose descriptor failed to parse on either side is named with its diagnostic, and the map is `partial`, never complete.
* *The rendering*: a fixed-width text diagram of at most `20` boxes: the modules the delta names and those with a path in the realized footprint, each with its direct neighbours; an added edge drawn `+-->`, a removed edge `--x`, a removed or replaced third-party dependency named under its module. A larger map is rendered as the lists alone. It is shown in the server-owned section `## Change Map` of the pull-request body, in the landing record, in the completion report, and as the resource `pm://plan/{plan_id}/change-map`.
* *Where it and the own calls are read*: the server-owned sections `## Own Calls` (shortcuts first, each with its limit and ending condition) and `## Change Map` of the pull-request body, the landing record, and the completion report, and the resource of the plan; the calls themselves are recorded by link:own-calls.adoc[`own-calls`]. The `simplify` review receives the shortcuts as facts and proposes the removal of none whose `ends_when` has not occurred.
* *Judged at the self-review*: the facts of `review-requirement` carry the map and the calls, and the self-review judges each own call against the intent record and each removed or replaced coordinate no call and no deliverable names; it is refused when it omits a result for a replaced coordinate that no own call names. An added coordinate stays the Dependency Delta Check's.
* *The result*: `change_map: {state: complete | partial | indeterminate, modules_added[], modules_removed[], edges_added[], edges_removed[], replaced[], cause}` in the finalize facts; `indeterminate` when the realized footprint is not `measured`. The map blocks nothing by itself.
* *Left out*: the module discovery, the build-file dependency parser, and the Dependency Delta Check (Milestone 10, no work package); the guidance and the rendering in the clients.

== Trace

Requirements stay in `plan-marshall-documentation`; the specification sections and watch documents are those of this repository, or of `plan-marshall-documentation` where a link leads there. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/02-workflow.adoc#PM-WF-13[PM-WF-13]; link:../../../plan-marshall-documentation/doc/requirements/02-workflow.adoc#PM-WF-21[PM-WF-21]; link:../../../plan-marshall-documentation/doc/requirements/15-extensions-and-recipes.adoc#PM-EXT-3[PM-EXT-3] (module discovery and build map, the crawl it reads)
* Specification: the sections the first bullet of _What_ names; link:../specification/domain-extensions/03-module-discovery.adoc[Domain Extensions § Module Discovery]; link:../specification/phase-workflows/03-q-gate-and-execute.adoc#own-calls[Phase Workflows § Own Calls of a Writer]; link:../../../plan-marshall-documentation/doc/specification/mcp-tools/03-resource-uris.adoc[MCP Tools § Resource URIs]; link:../specification/phase-workflows/04-finalize-step-bands.adoc[Phase Workflows § Step Catalogue] (`pre-submission-self-review`)
* Watch: link:../implementation-watch/phase-workflows.adoc[Phase Workflows]

== Done When

The guards of PM-WATCH-PHASE-114 pass: for fixture repositories whose base and head differ by an added module, a removed module edge, and a replaced coordinate, the map equals the crawl of each tree checked out in a scratch directory; a descriptor that fails to parse at `base_ref` yields `partial` with its diagnostic and never `modules_removed`; the status of the worktree is unchanged after the map is computed; the self-review is refused when it omits a result for a replaced coordinate that no own call names; a map of more than `20` boxes is rendered as the lists alone. The pull-request section, the landing record, and the completion report list the own calls and the change map of a fixture plan, shortcuts first.
````

## Index Rows

Append in this order after the last row of the table (`cleanup`), each row as the five cells of the table:

````asciidoc
|link:endpoint-harness.adoc[`endpoint-harness`]
|The endpoint harness: a model served on the loopback interface, its profile, its request, its enrolment and the identity of its weights
|`open`
|link:profile-loader.adoc[`profile-loader`]; link:role-sets-and-model-caller.adoc[`role-sets-and-model-caller`]; link:worker-launch.adoc[`worker-launch`]; link:role-qualification.adoc[`role-qualification`] (the weights identity is a member of the qualification digest)
|Milestone 14

|link:judgment-endpoint.adoc[`judgment-endpoint`]
|The judgment endpoint: typed questions answered with a probability per option by a hosted service or a model served in the same format
|`open`
|link:endpoint-harness.adoc[`endpoint-harness`]
|Milestone 14

|link:judgment-votes.adoc[`judgment-votes`]
|A judgment role votes: two requests, the confidence from the lower probability, a rationale composed by the server, the class rule, and the calibration form
|`blocked`
|link:judgment-endpoint.adoc[`judgment-endpoint`]; link:decision-engine.adoc[`decision-engine`] (the vote members of the decision record); link:role-qualification.adoc[`role-qualification`] (the grading); the decision quorum with its declarations, the adjudication unit, and its calibration (no work package)
|Milestone 14

|link:own-calls.adoc[`own-calls`]
|Own calls of a writer: the choices a task settled that the plan left open, submitted with the task, checked by the server, and recorded as decisions
|`blocked`
|link:decision-engine.adoc[`decision-engine`] (the decision records); link:phase-sub-workflows.adoc[`phase-sub-workflows`]; the task envelope with its `submit` link and the Task Diff Record (no work package)
|Milestone 9

|link:change-map.adoc[`change-map`]
|The change map: which modules a change adds or removes, which dependencies between modules it adds or removes, and which third-party dependencies it adds, removes, or replaces
|`blocked`
|link:own-calls.adoc[`own-calls`]; link:phase-sub-workflows.adoc[`phase-sub-workflows`]; the module discovery and the build-file dependency parser, the Dependency Delta Check, and the finalize pipeline of Milestone 10 (no work package)
|Milestone 10
````

## Claim Labels

- OBSERVED: none of the five package files exists and the index has no row for them — `ls doc/plans/` and `grep` of `doc/plans/README.adoc` at `0bf64f7`
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: ls doc/plans at 0bf64f7: none of endpoint-harness, judgment-endpoint, judgment-votes, own-calls, change-map; README.adoc names none of them
- OBSERVED: the index already records a dependency without a work package as a note `(no work package)` and keeps such a package `blocked` — `doc/plans/README.adoc` (rows `forgejo-provider`, `chat-plan-revision`, `runtime-control`) and its intro paragraph
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc at 0bf64f7: the string '(no work package)' occurs in the rows forgejo-provider, chat-plan-revision and runtime-control; the intro says 'A dependency that has no work package yet keeps a package blocked'
- OBSERVED: the specification parts and watch items the five packages cite exist with their anchors — `doc/specification/client-profiles/03-endpoint-harness.adoc`; `doc/implementation-watch/client-profiles.adoc`; `doc/specification/client-profiles/04-judgment-endpoint.adoc`; `doc/implementation-watch/client-profiles.adoc`; `doc/specification/model-work/10-decision-quorum.adoc`; `doc/specification/store-schemas/16-task-queue.adoc`; `doc/implementation-watch/model-work.adoc`; `doc/specification/phase-workflows/03-q-gate-and-execute.adoc`; `doc/implementation-watch/phase-workflows.adoc`; `doc/specification/hypermedia-format/02-link-grammar.adoc`; `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc`; `doc/implementation-watch/phase-workflows.adoc`
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: anchors found at 0bf64f7 in: doc/specification/client-profiles/03-endpoint-harness.adoc: [#endpoint-harness], [#endpoint-profile-schema], [#endpoint-request], [#endpoint-enrolment], [#weights-identity], [#endpoint-verification]; doc/implementation-watch/client-profiles.adoc: [#PM-WATCH-CLIENT-14]; doc/specification/client-profiles/04-judgment-endpoint.adoc: [#judgment-endpoint], [#system-one-request], [#remote-form], [#judgment-verification]; doc/implementation-watch/client-profiles.adoc: [#PM-WATCH-CLIENT-15]; doc/specification/model-work/10-decision-quorum.adoc: [#judgment-vote], [#quorum-calibration], [#blind-input]; doc/specification/store-schemas/16-task-queue.adoc: decision-calibration-record; doc/implementation-watch/model-work.adoc: [#PM-WATCH-DISP-63]; doc/specification/phase-workflows/03-q-gate-and-execute.adoc: [#own-calls]; doc/implementation-watch/phase-workflows.adoc: [#PM-WATCH-PHASE-113]; doc/specification/hypermedia-format/02-link-grammar.adoc: `own-call`; doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc: [#change-map], [#dependency-delta]; doc/implementation-watch/phase-workflows.adoc: [#PM-WATCH-PHASE-114]
- OBSERVED: the roadmap of `plan-marshall-documentation` carries one item for each area — the own calls (Milestone 9), the change map (Milestone 10), the endpoint harness and the protocol `system_one` with the judgment vote (Milestone 14, two items); the local checkout of the documentation (`8c1ddf4`) is nine commits behind `origin/main` and lacks them — `doc/roadmap.adoc` at `origin/main` `b8c61b3`, lines 546, 606, 754 and 755
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: roadmap.adoc extracted from documentation origin/main b8c61b3 with git archive: line 546 'Implement the own calls of a writer' (inside Milestone 9, lines 510-548), line 606 'Implement the change map of a plan' (Milestone 10, from 549), lines 754 and 755 'Implement the endpoint harness' and 'Implement the protocol system_one of the endpoint harness and the judgment vote' (Milestone 14, from 703); the same grep on the local checkout 8c1ddf4 finds none
- HYPOTHESIS: the dependency edges of the five rows are the orchestrator's reading of the specification (for example `endpoint-harness` after `profile-loader`, `role-sets-and-model-caller`, `worker-launch` and `role-qualification`), while the roadmap cuts the judgment endpoint and the judgment vote as one item and the orchestrator as two packages — confirm/refute against the package files of those dependencies when the rows are written (verify-at-outline)
  - verdict: unverifiable | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before writing, list the open pull requests for one that touches `doc/plans/README.adoc` (PLAN-19 deletes its own row and edits the cells that name `profile-loader`) and re-read the index at the head of `main`; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: open PRs (ci pr list) on 2026-10-22: #137, #129, #71; the index is touched by the packages' own pull requests as designed (PLAN-19's #137 deletes the row profile-loader and drops it from the cells that name it); the rows of this plan are appended, the merge-queue rebase settles the rest

## Expected Surface

- OBSERVED: `doc/plans/README.adoc` — the five rows appended
- HYPOTHESIS: `doc/plans/endpoint-harness.adoc` — new package file
- HYPOTHESIS: `doc/plans/judgment-endpoint.adoc` — new package file
- HYPOTHESIS: `doc/plans/judgment-votes.adoc` — new package file
- HYPOTHESIS: `doc/plans/own-calls.adoc` — new package file
- HYPOTHESIS: `doc/plans/change-map.adoc` — new package file

## Dependencies and Sequencing

- Depends on: nothing
- Blocks: PLAN-97, PLAN-98, PLAN-99, PLAN-100, PLAN-101
- Overlaps with: `doc/plans/README.adoc` is shared by every plan (each removes its own row); PLAN-19's pull request #137 is open and edits it
- Adjacent to: the roadmap of `plan-marshall-documentation`, which this plan leaves alone and lists as owed

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-96-spec-ahead-work-packages.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
