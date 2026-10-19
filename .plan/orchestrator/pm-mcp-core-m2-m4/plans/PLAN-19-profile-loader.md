# PLAN-19: Profile loader and validation

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-19-profile-loader.md` and is queued as one row file, `queue/PLAN-19.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `profile-loader` of `doc/plans/profile-loader.adoc`: profile loader and validation. Serves: Roadmap Milestone 2. Where: pm-core package `core.profile`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/profile-loader.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. The loader reads the whole profile documents that are resources of `pm-api` and validates them against the profile schema (with the `headless` members `install_locations`, `install_hint`, `auth_probe`, `models_probe`, `model_tiers`, `events`, and `resumability`, and the top-level `icon`); a failing profile is refused as a whole. The usage block and its rule that `input_tokens` and `output_tokens` are never `not_applicable` are `token-usage`'s.
2. The installed profile sets: the machine set `<PM_MCP_BASE>/profiles/` and the project set `<PM_MCP_BASE>/projects/<project_id>/profiles/` beside the shipped one, the nearest set winning as a whole document and never merged; a project profile only for a `client_id` the machine or shipped set defines, with equal `detection`, `identity`, and `headless.executable`. A machine or project profile counts only with its verification record: `profile_digest` equal to the SHA-256 of the canonical JSON (RFC 8785) of the document and the HMAC sidecar verified with the key of the enrolment store; otherwise it is refused `profile_unverified` and the next set stays in force. A record whose harness version or digest differs from the enrolled harness within the supported range marks the profile `verified_on_other_build` and launches go on. Writing the sets is the onboarding's (`machine-onboarding-unit`); this package reads them and reports the state it found.
3. Build test: every shipped profile validates.

**Done when** (the package's own acceptance): Every shipped profile loads and validates; for each hardening rule that concerns the parts read here, and for each pattern-constrained new member (`install_locations`, `install_hint`, `icon.source` with https and SVG or PNG only, `resumability.resume.argv` with its session-id slot exactly once), there is a fixture profile that the loader refuses as a whole, with the document and the JSON Pointer of the failing value asserted; a machine profile of a `client_id` the release does not ship is loaded; a machine or project profile without a record, edited after its record, or with a failing HMAC is refused `profile_unverified` and the next set's profile is in force; a project profile whose `detection` differs from the machine one is refused; a record of another harness build yields `verified_on_other_build`; a build test fails when a class of the loader names a harness, proven by a control fixture.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-5
- Specification: Client Profiles § The Profile Is the Harness; § A Profile Is Untrusted Input; § Profile Sets; § Unknown Harness (no closed list of client ids)
- Watch: PM-WATCH-CLIENT-13

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-core/doc/developer/core-profile.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-core/doc/adr/core-profile/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/profile-loader.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/profile-loader.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/profile-loader.adoc` § What, Trace, Done When (origin/main at 7d99648)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/profile-loader.adoc § What, Trace and Done When re-read at 238c823 equal the Deliverables, trace set and acceptance above
- OBSERVED: the index gives this package the status `open` and the dependencies nothing inside this repository; plus `pm-mcp-clients` / `profile-documents` merged and its `SNAPSHOT` deployed (`open` there on 2026-10-09) — read at `doc/plans/README.adoc` § Packages
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row profile-loader re-read at 238c823: open; depends on profile-documents; matches the claim
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing on 2026-10-09
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-core core/profile holds only package-info.java at 238c823 and has no test directory: greenfield
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; surface checked against the code at 238c823 and corrected where it was wrong or omitted a package
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (60), `WorkflowLogMessages` (5); `PmMcpLogMessages` is deleted — read on main at 238c823 on 2026-10-16
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: CoreLogMessages has 5 records, RuntimeLogMessages 60, WorkflowLogMessages 5 at 238c823 (claim said 3, 54, 3); one holder with its own test per module and PmMcpLogMessages gone still hold; claim rewritten
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: whether the package logs is decided at the trace matrix; no implementing code exists yet
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the specification sections of the trace starting set name no HTTP call of this package; pm-core and pm-workflow cannot depend on cui-http anyway
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `profile-loader`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row profile-loader is open in the index at 98ce971; open PRs (ci pr list): #71 (a dependabot bump) only, none names the package; doc/plans/profile-loader.adoc is unchanged since 3323ce7; code claims of the spec were not re-read

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/profile/` — tests; new directory
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-profile.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/profile-loader.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: `pm-mcp-clients` / `profile-documents`
- Overlaps with: no other plan by package; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-19-profile-loader.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
