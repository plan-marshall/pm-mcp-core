# PLAN-74: The credential store as the operator's choice, and its move

epic: pm-mcp-core-m2-m4
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-74-credential-store-choice.md` and is queued as one row file, `queue/PLAN-74.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `credential-store-choice` of `doc/plans/credential-store-choice.adoc`: the credential store as the operator's choice, and its move. Serves: Roadmap Milestone 6 (the credential store the operator's choice); the credential-store aspect of the machine's onboarding (Use Cases, UC-ONB-7) in both clients of `pm-mcp-clients`. Where: pm-runtime packages `runtime.credentials` (the recorded store in place of `SecretStoreSelector`, the check of the OS store, the move) and `runtime.enrolment` (`EnrolmentKey` before and after the choice, the HMAC sidecars verified under the moved key), and the start sequence of `runtime.start` (a move resumed after a stop). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/credential-store-choice.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements CLI, Coordination & Security § Credential Store as an Operator Choice with § The move and the store rules of § Credential Stores (`PM-CRED-2`, `PM-CRED-3`). Present: the three stores behind `SecretStore` (`KeychainSecretStore`, `SecretServiceStore`, `FileSecretStore`) with the service name per instance and the project entry before the global entry, the enrolment integrity key in the keyring or the key file (`EnrolmentKey`), and `SecretStoreSelector`, which chooses the one active store at start by itself and falls back to the file store; that automatic choice is what this package replaces.
2. The recorded store: the runtime reads `credentials.store` of `machine-config.json` (`os` or `file`) and uses that store and no other; `SecretStoreSelector` and its fallback go. Before the store is recorded no credential is entered (`onboarding_incomplete`, naming the store step); a recorded OS store that is unavailable at start or later makes the credentials unavailable with its cause, a step that needs one held with `credential_store_unavailable`, a write refused with `credential_store_locked`, and nothing written to the file store in its place.
3. Recording the choice: an operation that writes `credentials.store` under `locks/machine-config.lock`, called by the credential-store aspect of the machine's onboarding and by the move, and refused for every other writer (`aspect_owned`); before an OS store is recorded, the check of that store writes, reads back, and deletes one test entry, each step a check line; asking the consent `check_credential_store` before it and the states `store.*` are `machine-onboarding-unit`'s.
4. The integrity key before the choice: until `credentials.store` is recorded the key is created where the runtime finds a store, in the OS store where one is available, else in the key file `<PM_MCP_BASE>/state/enrolment-hmac.key`; `EnrolmentKey` reads it from the recorded store once there is one, and the choice moves it like every other entry, nothing re-signed.
5. The move in either direction (OS to files, files to OS), an operator act of the aspect or of `pm-operator credentials migrate os|file`: recorded with from, to, and start; every Token Lane entry copied into the target and read back; the integrity key copied last and every HMAC sidecar (the enrolment files, `harnesses.json`, `logs/audit.head`) verified under it in the target; `credentials.store` switched in one write; every entry deleted from the old store; the record ended. A failed copy or read-back before the switch ends the move with nothing switched and the copies deleted from the target (`credential_store_failed`); a move cut by a stop resumes after the next start from its record, before the switch from the first entry, after it with the deletion; no store but the recorded one is read while it runs; the move is audited.
6. Tests per OS: the Keychain and Secret Service paths run with `@EnabledOnOs`, the Secret Service also against the session keyring like `SecretServiceIT`; the file store and the move between a fake OS store and the file store run everywhere.
7. The assembly: the producer of the active store in `plan-marshall-mcp` (`SecretStoreProducer`) follows in a pull request there.

**Done when** (the package's own acceptance): With no store recorded, entering a credential is refused with `onboarding_incomplete` and the integrity key is created in the store the runtime finds; with `os` recorded and the OS store unavailable, no file is written, the cause is reported, and a step needing a credential is held with `credential_store_unavailable`; no code path chooses a store by itself; a move in each direction copies and reads back every entry, moves the integrity key last after the sidecars verify, switches once, and empties the old store; a failed read-back leaves the old store in force and the target without copies (`credential_store_failed`); a move cut before and after the switch completes after a restart; the OS paths are tested on their OS; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-ONB-7
- Requirements: PM-CRED-2; PM-CRED-3; PM-CRED-4; PM-SEC-5
- Specification: the sections the first bullet of _What_ names; Store Schemas § Machine Configuration Store (`credentials.store`); § Machine State Records (the record of the move); State Catalogue § `store.move`; Runtime & Interaction Model § Local API (`credential_store_failed`, `credential_store_locked`)
- Watch: CLI, Coordination & Security

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/credential-store-choice.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that names `credential-store-choice`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/` — tests
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-credentials.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-credentials/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/enrolment/` — tests
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-enrolment.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-enrolment/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/start/` — tests
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-start.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-start/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/credential-store-choice.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/credentials-and-enrolment/01-provider-credentials.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/19-onboarding-aspects-models-providers.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-74-credential-store-choice.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
