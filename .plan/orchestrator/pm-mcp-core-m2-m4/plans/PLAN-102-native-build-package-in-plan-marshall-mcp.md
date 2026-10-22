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
2. **The dangling cross-repository links of that index.** Today the index links `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection` of `pm-mcp-core` (all done or absorbed: a done package leaves the table and its file is deleted) and `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` of `pm-mcp-clients` (moved to `doc/plans/implemented/`). Re-read the rows against `origin/main` of both repositories, and drop each done package from the _Depends on_ cell that names it, as PLAN-93 did here; a dependency that is not done stays. Say in the pull request body which links were dropped.
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
* *The credential paths*: the Keychain downcalls (macOS) and the Secret Service client (Linux) of `pm-runtime` load in the native binary and complete a round trip where the host allows it, and are skipped with their reason where it does not.
* *The report*: each check is recorded `passed`, `failed`, or `skipped` with its reason, with the revision of the `SNAPSHOT` it ran against; a failure is a pull request in `pm-mcp-core`, not a workaround here.

== Trace

Requirements, specification sections, and watch documents stay in `plan-marshall-documentation` (those of the engine in `pm-mcp-core`) and are only referenced here. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/01-architecture.adoc#PM-ARCH-3[PM-ARCH-3]
* Specification: link:../../../pm-mcp-core/doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc[Job Runtime § Process Termination & Cancellation Protocol]; link:../../../pm-mcp-core/doc/specification/credentials-and-enrolment/01-provider-credentials.adoc[Credentials & Enrolment Specification]
* Watch: link:../../../pm-mcp-core/doc/implementation-watch/job-runtime.adoc[Job Runtime]

== Done When

The native build of `pm-mcpd` ran against the named `SNAPSHOT` and its report lists the four checks above; every `failed` check has a pull request in `pm-mcp-core`; the epic of `pm-mcp-core` has the report.
````

## Index Row

````asciidoc
|link:native-build-verification.adoc[`native-build-verification`]
|The native build of the daemon against the deployed engine: the metadata of the engine modules, the `kill` downcall, the Keychain and Secret Service paths
|`open`
|nothing
|Milestone 4
````

## Claim Labels

- OBSERVED: no package of `plan-marshall-mcp` names the native build as a check of the engine — `git grep -i` of `doc/plans` at `origin/main` (`38bd66d`) for `captureCallState`, `downcall` and `native build of` finds nothing; `integration-layers` mentions `verify -Pnative,integration-tests` only as its local test command
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -iE of captureCallState|downcall|native build of over plan-marshall-mcp doc/plans at origin/main (38bd66d) finds nothing
- OBSERVED: the `pm-mcp-core` `CLAUDE.md` names the native build of `pm-mcpd` in `plan-marshall-mcp` as the check of native-image metadata, and `plan-marshall-mcp` `CLAUDE.md` gives the command `verify -Pnative,integration-tests` with `GRAALVM_HOME` and `JAVA_HOME` — read at `4617865` and at `origin/main` `38bd66d`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-mcp-core CLAUDE.md names the native build; plan-marshall-mcp CLAUDE.md at origin/main (38bd66d) gives verify -Pnative,integration-tests with GRAALVM_HOME
- OBSERVED: the index of `plan-marshall-mcp` links packages that no longer exist — `pm-mcp-core`: `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection` (no file under `doc/plans/` at `4617865`); `pm-mcp-clients`: `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` (moved to `doc/plans/implemented/`) — `doc/plans/README.adoc` at `origin/main` `38bd66d`; the local checkout of `pm-mcp-clients` may lag, so the plan re-reads `origin/main`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: plan-marshall-mcp index at origin/main (38bd66d) links the 4 pm-mcp-core packages (no file in doc/plans at 4617865) and 4 pm-mcp-clients packages that are under doc/plans/implemented/ at pm-mcp-clients origin/main (9379836)
- HYPOTHESIS: this plan can be launched from the checkout of `plan-marshall-mcp` with the absolute path of this spec in the ledger worktree of `pm-mcp-core` (the lane takes a spec outside its repository) — confirm/refute at the first step of the lane (verify-at-outline); if it refuses, the operator copies the spec into that repository's `.plan/temp/`
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package depends on nothing (the native build of the daemon exists today) and serves Milestone 4 — confirm/refute against the index of `plan-marshall-mcp` and its roadmap items (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before writing, `git fetch` the three repositories, list the open pull requests of `plan-marshall-mcp` for one touching `doc/plans/README.adoc`, and re-read the index at `origin/main`; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `plan-marshall-mcp/doc/plans/native-build-verification.adoc` — new package file in the repository `plan-marshall-mcp`, not in this one
- HYPOTHESIS: `plan-marshall-mcp/doc/plans/README.adoc` — the new row and the dropped dependencies, in the repository `plan-marshall-mcp`

This plan touches no path of this repository; the entries above name another repository, so the reader of this section cannot anchor them here, and the plan is sequenced rather than paired by the disjointness gate (deliberate).

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: none in this repository
- Adjacent to: the native build itself, which the new package owns; the open pull requests of `plan-marshall-mcp`, unknown here

## Hand-Off Command

Launch it from the checkout `/home/oliver/git/plan-marshall-mcp` (the plan writes that repository):

```text
/plan-marshall-runbook-lane plan=/home/oliver/git/pm-mcp-core/.plan/local/worktrees/_orchestrator/.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-102-native-build-package-in-plan-marshall-mcp.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
