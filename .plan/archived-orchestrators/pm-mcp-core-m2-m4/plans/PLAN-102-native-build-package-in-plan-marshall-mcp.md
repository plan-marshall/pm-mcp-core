# PLAN-102: Name a package in plan-marshall-mcp for the native build of the daemon

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-102-native-build-package-in-plan-marshall-mcp.md` and is queued as one row file, `queue/PLAN-102.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-04.
- R12 — log message holder: none: documents only.

## Objective

PLAN-84 (#135, `0bf64f7`) reported the native build of `pm-mcpd` against the deployed `SNAPSHOT` of `pm-mcp-core` as not run, because no plan owns it: it is the only check of the `kill` downcall descriptor with `options.captureCallState` and of the Keychain and Secret Service paths, and no package of `plan-marshall-mcp` names it. The operator decided on 2026-10-22 that a plan in `plan-marshall-mcp` owns it. **This plan works in the repository `plan-marshall-mcp`, not here**: it writes that repository's package file and index row, and repairs the cross-repository links of its index that point at packages done here or in `pm-mcp-clients`. Documents only; the native build itself is the package's work.

## Deliverables

1. **The package `native-build-verification`**: add `doc/plans/native-build-verification.adoc` to `plan-marshall-mcp` with exactly the text of the section *Package File* below, and its row to the table of `doc/plans/README.adoc` as the section *Index Row* gives it (after the last row). Correct a link or an anchor that does not resolve and say so in the pull request body.
2. **The dangling cross-repository links of that index.** Today (read at `origin/main` of `plan-marshall-mcp` on 2026-10-27) the index links `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection`, `role-sets-and-model-caller`, `task-queue-and-protocol`, `waits`, `worker-launch`, `supervisor` and `role-qualification` of `pm-mcp-core` (all done or absorbed: a done package leaves the table and its file is deleted; `supervision-scenarios` of `pm-mcp-core` is still open and stays) and `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` of `pm-mcp-clients` (moved to `doc/plans/implemented/`). The `question-loop-verification` row is `done` there (#107) and its follow-up `question-loop-follow-up` is open: leave both rows as they are. Re-read the rows against `origin/main` of both repositories, and drop each done package from the _Depends on_ cell that names it, as PLAN-93 did here; a dependency that is not done stays. Say in the pull request body which links were dropped.
3. **The links check, recorded**: the documentation repository's `trace.py` over both trees from the worktree (the absolute-path command of the Standing Constraints), `BROKEN LINKS 0`.

**Done when:** `doc/plans/native-build-verification.adoc` and its row exist in `plan-marshall-mcp`, every link of the index resolves on `origin/main` of the three sibling repositories, the links check reports `BROKEN LINKS 0` and the run is recorded in the pull request body. The pull request touches only `doc/plans/` of `plan-marshall-mcp`.

**Trace starting set:** the `pm-mcp-core` `CLAUDE.md` ("Native-image metadata … is verified by the native build of `pm-mcpd` in plan-marshall-mcp against the deployed `SNAPSHOT`"); the landing of PLAN-84 (`landings/PLAN-84.md`); the Open Defects and Watches of the epic.

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Package File

````asciidoc
= The native build of the daemon against the deployed engine
:nofooter:

link:README.adoc[Work packages of `plan-marshall-mcp`] » `native-build-verification`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 4 (the job runtime and the credential stores of `pm-mcp-core` run in the daemon)
Where:: pm-mcp-server (the native build of `pm-mcpd`), pm-e2e (the native layout)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Run it*: `verify -Pnative,integration-tests` with `GRAALVM_HOME` and `JAVA_HOME` on a GraalVM 25 installation, against the `SNAPSHOT` of `pm-mcp-core` that its merge deployed, and against the client binaries of `pm-mcp-clients` (the native layout of `pm-e2e` needs them, otherwise it is skipped and the build output says so).
* *The native-image metadata of the engine*: `pm-mcp-core` modules carry their metadata (`META-INF/native-image/de.planmarshall/<name>/`) and name this build as its check; the build proves that it resolves every class of the modules the daemon uses.
* *The `kill` downcall*: the native check of the descriptor with `options.captureCallState` that `pm-runtime` (`ProcessGroupSignals`, `KillBinding`) binds through the foreign function interface, by signalling a real process group in a native test; it is the only check of that descriptor.
* *The process-path downcall*: the `libproc` `proc_pidpath` downcall (`jint, void*, jint -> jint`) that `pm-runtime` (`runtime.worker.SystemProcessExecutables`, added by #190) registers in `reachability-metadata.json` loads in the native binary on macOS; on Linux the same class reads `/proc/<pid>/exe` and the check is skipped with that reason.
* *The credential paths*: the Keychain downcalls (macOS) and the Secret Service client (Linux) of `pm-runtime` load in the native binary and complete a round trip where the host allows it, and are skipped with their reason where it does not.
* *The report*: each check is recorded `passed`, `failed`, or `skipped` with its reason, with the revision of the `SNAPSHOT` it ran against; a failure is a pull request in `pm-mcp-core`, not a workaround here.

== Trace

Requirements, specification sections, and watch documents stay in `plan-marshall-documentation` (those of the engine in `pm-mcp-core`) and are only referenced here. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/09-technology.adoc#PM-TECH-3[PM-TECH-3] (the native executable); link:../../../plan-marshall-documentation/doc/requirements/01-architecture.adoc#PM-ARCH-3[PM-ARCH-3]
* Specification: link:../../../pm-mcp-core/doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc[Job Runtime § Process Termination & Cancellation Protocol]; link:../../../pm-mcp-core/doc/specification/credentials-and-enrolment/01-provider-credentials.adoc[Credentials & Enrolment Specification]
* Watch: link:../../../pm-mcp-core/doc/implementation-watch/job-runtime.adoc[Job Runtime]

== Done When

The native build of `pm-mcpd` ran against the named `SNAPSHOT` and its report lists the five checks above (the metadata, the `kill` downcall, the `proc_pidpath` downcall, the credential paths, the report); every `failed` check has a pull request in `pm-mcp-core`; the epic of `pm-mcp-core` has the report.
````

## Index Row

````asciidoc
|link:native-build-verification.adoc[`native-build-verification`]
|The native build of the daemon against the deployed engine: the metadata of the engine modules, the `kill` and `proc_pidpath` downcalls, the Keychain and Secret Service paths
|`open`
|nothing
|Milestone 4
````

## Claim Labels

- OBSERVED: no package of `plan-marshall-mcp` names the native build as a check of the engine — `git grep -i` of `doc/plans` at `origin/main` (`38bd66d`) for `captureCallState`, `downcall` and `native build of` finds nothing; `integration-layers` mentions `verify -Pnative,integration-tests` only as its local test command
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: needs plan-marshall-mcp origin/main; its local ref is stale (38bd66d, 21 Oct) and was not fetched read-only
- OBSERVED: the `pm-mcp-core` `CLAUDE.md` names the native build of `pm-mcpd` in `plan-marshall-mcp` as the check of native-image metadata, and `plan-marshall-mcp` `CLAUDE.md` gives the command `verify -Pnative,integration-tests` with `GRAALVM_HOME` and `JAVA_HOME` — read at `4617865` and at `origin/main` `38bd66d`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-mcp-core CLAUDE.md Build Rules: native-image metadata is verified by the native build of pm-mcpd in plan-marshall-mcp against the deployed SNAPSHOT; plan-marshall-mcp CLAUDE.md gives verify -Pnative,integration-tests
- OBSERVED: the index of `plan-marshall-mcp` links packages that no longer exist — `pm-mcp-core`: `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection` (no file under `doc/plans/` at `4617865`); `pm-mcp-clients`: `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` (moved to `doc/plans/implemented/`) — `doc/plans/README.adoc` at `origin/main` `38bd66d`; the local checkout of `pm-mcp-clients` may lag, so the plan re-reads `origin/main`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: at f4a1bd07 job-runner, operator-and-question-stores, project-machine-store and stalled-scope-detection are absent from doc/plans; the set of dangling links is larger now (supervisor #190 and others)
- HYPOTHESIS: this plan can be launched from the checkout of `plan-marshall-mcp` with the absolute path of this spec in the ledger worktree of `pm-mcp-core` (the lane takes a spec outside its repository) — confirm/refute at the first step of the lane (verify-at-outline); if it refuses, the operator copies the spec into that repository's `.plan/temp/`
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis about the lane; the launch outcome cannot be read
- HYPOTHESIS: the package depends on nothing (the native build of the daemon exists today) and serves Milestone 4 — confirm/refute against the index of `plan-marshall-mcp` and its roadmap items (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: the new package file does not exist yet, nothing to check
- Verify-first clause: before writing, `git fetch` the three repositories, list the open pull requests of `plan-marshall-mcp` for one touching `doc/plans/README.adoc`, and re-read the index at `origin/main`; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedure; the fetch and the open-PR list are not reachable read-only

## Expected Surface

- HYPOTHESIS: `plan-marshall-mcp/doc/plans/native-build-verification.adoc` — new package file in the repository `plan-marshall-mcp`, not in this one
- HYPOTHESIS: `plan-marshall-mcp/doc/plans/README.adoc` — the new row and the dropped dependencies, in the repository `plan-marshall-mcp`

This plan touches no path of this repository; the entries above name another repository, so the reader of this section cannot anchor them here, and the plan is sequenced rather than paired by the disjointness gate (deliberate).

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**. The spec is prose (no comparable surface): the gate reads it as indeterminate and sequences it.

- Claims re-read: 6: 3 corroborated, 0 contradicted, 3 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`). `plan-marshall-mcp` and `pm-mcp-clients` could not be fetched read-only by the leaf (their local `origin/main` refs are stale); a later fetch may change the dangling-link count.

### Specification changes carried (not yet in the package text this spec embeds)

- **#190 (`f4a1bd07`) added a third downcall to `pm-runtime/.../META-INF/native-image/de.planmarshall/pm-runtime-credentials/reachability-metadata.json`:** `libproc` `proc_pidpath(jint, void*, jint) -> jint`, used by `runtime.worker.SystemProcessExecutables` (macOS only; Linux reads `/proc/<pid>/exe`). The embedded Package File "What" names only `kill`, Keychain and Secret Service, so the native check list ("four checks") is short by one. `KillBinding` (`Linker.Option.captureCallState`) and `ProcessGroupSignals` still exist and are registered; Job Runtime 01 (Process Termination), Credentials and Enrolment 01 and `KillBinding.java` are unchanged.
- **Dangling cross-repository links grow:** `supervisor` (#190, `supervisor.adoc` deleted) is now also gone from `pm-mcp-core` `doc/plans`; at the local `plan-marshall-mcp` ref its README links `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection`, `role-sets-and-model-caller`, `task-queue-and-protocol`, `waits`, `worker-launch`, `supervisor` of `pm-mcp-core` (9) and `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` of `pm-mcp-clients` (4); the spec's Deliverable 2 names eight: re-read after a fetch.
- The Trace names requirement `PM-ARCH-3` ("STDIO Host Transport, Local Relay & Frontier Protocol Compliance"); the native-executable requirement is `PM-TECH-3` (documentation `09-technology.adoc` line 33; `doc/Traceability.adoc` line 1059 here).
- #187, #188: `PM-WATCH-JOB-38` and `-39` are added to the job-runtime watch, unrelated to the native build.

### Open questions for the outline

- Whether to add the `libproc` `proc_pidpath` check (macOS host only, skipped on Linux) to the package file's "What" and the "four checks" Done-when; whether the Trace cites `PM-TECH-3` instead of or beside `PM-ARCH-3`; which of the 9 + 4 dangling links are still present at the remote after a fetch.

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 6: 3 corroborated, 0 contradicted (re-scoped by this section), 3 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Surface

- understated: plan-marshall-mcp/doc/plans/README.adoc Depends-on cells also link four more shipped pm-mcp-core packages absent from pm-mcp-core doc/plans at HEAD: role-sets-and-model-caller, task-queue-and-protocol, waits, worker-launch; Deliverable 2 names only four of eight

### Shipped or superseded since the last grounding

- partly superseded by shipped plans: four further pm-mcp-core packages linked in the plan-marshall-mcp index are gone at pm-mcp-core HEAD: role-sets-and-model-caller (#146), task-queue-and-protocol (#160), waits (#145), worker-launch (#172); the plan's named list of four understates the dangling set, its re-read instruction covers them

### Open questions for the outline

- hand-off command uses the _orchestrator worktree path of the spec; user memory says a lane resolves specs from the main checkout only, and the spec is now landed at /home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-102-native-build-package-in-plan-marshall-mcp.md
- plan-marshall-mcp checkout HEAD is b94cdfa while its local origin/main is 38bd66d (21 Oct) and the other repositories' origin refs could not be fetched read-only: every dangling-link finding rests on local refs and must be re-read after git fetch

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: none in this repository
- Adjacent to: the native build itself, which the new package owns; the open pull requests of `plan-marshall-mcp`, unknown here

## Hand-Off Command

Launch it from the checkout `/home/oliver/git/plan-marshall-mcp` (the plan writes that repository), after the ledger land that carries this spec (the lane reads the copy in the main checkout of `pm-mcp-core`; runbook adaptation 2026-10-27; the spec names the lane's refusal of a spec outside its repository as a hypothesis to confirm at its first step, with the operator's fallback of copying the spec into that repository's `.plan/temp/`):

```text
/plan-marshall-runbook-lane plan=/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-102-native-build-package-in-plan-marshall-mcp.md
```

This plan touches no path of `pm-mcp-core`, so it runs beside any plan of this repository without a collision and without a build of `pm-mcp-core` (documents only, one pull request in `plan-marshall-mcp`; the links check needs the documentation repository's `trace.py` over both trees, R17).

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
