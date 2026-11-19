envelope_version=1
sender_type=plan
sender_id=rb-v2-13-plan-instance-versions
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-18T23:09:36Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-13-plan-instance-versions
epic=pm-mcp-core-m2-m4-v2
pr=#503
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=10
review_body_findings_handled=10
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-13 plan instance versions (continuation)

Standing rules: revision **r7** (2026-11-10), read at session start; rules in force for this plan: R01, R02, R03, R04,
R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19, R20. R21 binds the analysis, not the lane. The
spec's First Instruction names r4; the file's current revision r7 was read and is what the run followed.

This was the continuation the spec's `## Continuation (2026-11-18)` section asked for: it reused the branch the first lane
left at `be033795` and its uncommitted fixes, and it followed the six steps of that section in order.

## Deliverables

Every Done-when is **met**; `deliverables_done=5`. No order below is `test-first`: the implementation was written by the
first lane, so each is `mutation-after` — the committed fix was mutated, the run refused it, the mutation was reverted
(`git status --porcelain` empty after each run).

| Deliverable | Done-when | Test | Order | Failing run |
|---|---|---|---|---|
| D1 the triggers | each server event composes in the entering transaction and the chain selects from it | `InstanceVersionEngineTest$ServerEvents.composesInTheEnteringTransaction` (5 events, derived from the enum's triggers) | mutation-after: the run does not take the port's units of the version it just composed | `2bd55264d389423396cd63b12c3a4491` — every case faults "the version 2 in force carries no plan unit" |
| D1 | a refused server composition raises `precondition_unmet` on the composing transition | `refusedRaisesPreconditionUnmet` | mutation-after: `composeAtEntry` composes nothing | `da7f1afeb58347bdbb35edb3ad310ccb` — expected `ConditionRaised`, was `Ended` |
| D1 | an operator event is in force from the next transition, and the next transition runs it | `OperatorEvents.takesForceFromTheNextTransition` (3 events) + an advance of the returned status | mutation-after: the Composed branch records without taking force | `f543be63733f4654bb2482611f65729e` |
| D1 | a refused operator event is refused and logged | `refusedIsReturnedAndLogged` | mutation-after: the Refused branch records nothing | `d1142573e0cd4ed88f3d8ce29fe03f56` |
| D1 | at `start` a refused composition creates no plan | `refusedStartCreatesNothing` | mutation-after: `checkRules` returns no refusal | `af118861266f4ac1b577c1b7a11cea2f` |
| D2 the version in force | after a switch the engine runs the stored sub-unit text although the project's copy changed | `VersionInForce.runsTheStoredSubUnitText` | mutation-after: `loadUnit` reads the level's copy | `69d08728efe44de2b3fada502f6ad79b` — the project copy ran |
| D2 | a version already in force is read through the units port | `VersionInForce.runsTheVersionInForceFromThePort` | mutation-after: `loadUnit` ignores the version | `69d08728efe44de2b3fada502f6ad79b` |
| D2 | the position the composition reads is the advance's, from the call stack | `composesInTheEnteringTransaction`, `positionFromTheCallStack` | mutation-after: `composeAtEntry` composes nothing | `da7f1afeb58347bdbb35edb3ad310ccb`, call-stack case `2bd55264d389423396cd63b12c3a4491` |
| D2 | the `instance` history line is written in the transaction that takes force, and a line keeps its own version | `composesInTheEnteringTransaction` | mutation-after: `takeForce` drops the line | `1ccf65799a814510a5d461df4d224a76` |
| D2 | `instance_version` is a field of the workflow state | `WorkflowStatusCodecTest`, `WorkflowStatusInstanceVersionTest` | none (a member's presence is its round trip and its two refusals; the codec retained it verbatim before, so no failing run without the field is possible) | — |
| D3 only verified versions | the store refuses a version the composer did not return, and a text the composer did not print | `PlanInstanceStoreTest$Versions.unverifiedVersionRefused` | mutation-after: `requireVerified` checks nothing | `0ca356a11569403d812f2cb9dcf16d01` |
| D4 Rules 13 and 14 | a missing role, a wrong class, a quorum on one model and an unresolved skill refuse at `start`, at a server event and at an operator event | `CompositionChecksTest$Roles`, `$Skills`, `refusedStartCreatesNothing`, `noChecksComposes` | mutation-after: `checkRules` returns no refusal | `af118861266f4ac1b577c1b7a11cea2f` — every refusal fails, controls pass |
| D4 | the PM-WATCH-DISP-67 table, the refusal per row and the matched control | `CompositionChecksTest$ModelIdentities` | mutation-after: `ModelIdentity.shared` answers empty | `3807d0a2ad96461f860529b9abdfdb11` |
| D5 the resources | the text of a version is read through the package's API | `PlanInstanceResourcesTest$Text.readWithHeader` | mutation-after: `text(version)` answers empty | `33f2b784ddb24666abba853b62475666` |
| D5 | the diagram of the version in force is read through the package's API | `PlanInstanceResourcesTest$Diagram.marksTheActiveState`, `refusesAnUnknownActiveState` | mutation-after: the diagram renders without the active state | `7af255db90d44ae89a22a9bb22ac548e` |
| D5 | the status names the version and its event; every resource is empty for an unwritten version | `PlanInstanceResourcesTest$Status.namesVersionAndEvent`, `unwrittenVersionIsAbsent` | mutation-after: `text(version)` answers empty (the `version` read backs all three) | `33f2b784ddb24666abba853b62475666` |
| all | `verify`, its quality gate, `-Pcoverage`, `-Pintegration-tests` green | build gate at `f7f8e778` (the merged head) | none | quality gate jobs `3791a9366fa94896ab5e0df4d77a48bf`, `a8af71cc39324cf48320c870fbfcf5f6` (330 s bound expired; raised to 600 s, which the daemon bound may only raise); `verify` `ee1237bad5b04828a764815f7eaf015e` and `85158eb861cb41abb528dd59b8063d7e` (11255 tests); `-Pcoverage` `5692e8a2df2244efa55d022140ba716c` (11165); `-Pintegration-tests -pl pm-core` `93e07c7352c4492e9bdb1f628424e281`; `-pl pm-runtime` `7ce0e75923b34e48aa03034a023f409d` |
| all | the package file and its index row removed | `doc/plans/plan-instance-versions.adoc` deleted; the README row, `re-planning`'s depends-on and `phase-sub-workflows`'s link updated | none | — |

Per-head test runs of `module-tests` (`test -pl pm-workflow[,pm-core] -am`): `51021da3` 7910, `dfb195a4`, `a866ddb6`,
`17e221b6` 7934, `d7d97aab`, review-fix heads 7997 and 8086, `2cacb5d3` 11255 (full `verify`). Log identifiers are in the
progress file (`.plan/temp/rb-v2-13-plan-instance-versions/progress.md`).

## Personas and skills

- Implementer persona for the production code (D1–D5, the review fixes), module-tester for the tests, code-reviewer for
  both self-review rounds and for triaging every PR comment.
- The spec declares no `Skills:` or `Persona:` lines: `skills: none named`. No surface drift: every touched module was in
  the Expected Surface (or its extended bullet); `LockDoubles`, `VerifiedContractTest`, `WorkflowStatusInstanceVersionTest`
  and `PlanSchemaFilesTest`'s new test are implied by the deliverables, not new surface.

## Hypotheses

- The spec's HYPOTHESIS (the Expected Surface inferred, not traced) — unverifiable by reading, as the claim itself said;
  the surface held: no file outside it was edited except the shared bookkeeping (R08) and the ADR renumbering (R11).
- Phase 0 hypotheses of this run, each confirmed by reading: (1) `InstanceVersionEngineTest.FakeComposition`/`FakeUnits`
  were the scan's only new doubles — confirmed, `pm-workflow/target/hand-written-doubles-found.txt:21-22`;
  (2) `PlanInstanceStore.recordComposed` checked no provenance — confirmed, `PlanInstanceStore.java:139,184` before the
  fix; (3) no `PlanInstanceResources` test existed — confirmed by search before writing it.

## Findings

**Self-review round 1** (sub-agent, persona-code-reviewer) found a HIGH-severity defect and 14 further findings; all are
fixed or answered (commits `b6f40e16`, `5ba389c5`):

- HIGH: a version switch cleared the unit cache and re-read the version through the store, whose files the composing
  transaction had only staged — the chain after a composing transition would fault with the real port, and every test
  masked it with a mock. Fixed by `InstanceComposition.unitsOf`, the units of the version a composition just returned;
  the tests now read the composed version *only* through it (the units port answers no files there), and the committed
  path is covered by `runsTheVersionInForceFromThePort`. Evidence: mutation job `2bd55264d389423396cd63b12c3a4491`.
- The two transaction-bearing `advance` overloads dropped their transaction; history lines were stamped with the version
  at flush rather than at production; a `Ports` contract test had been relaxed into a tautology; a "resets no counter"
  assertion was vacuous; the quorum table asserted the comparison, not the refusal; two doc statements said Rules 13/14
  are unchecked; an `OptionalInt` claim; a wrong supersession pointer; a broken link after the package-file removal; a
  duplicate Javadoc; a Traceability sentence claiming the quorum runs at task creation. All fixed.
- Round 2 verified the fixes and found five residuals (a Limitations bullet, three descriptions of the port without
  `unitsOf`, a Traceability paragraph still listing this work as not implemented, the composition needing the plan's
  history — now a named refusal — and the non-plan scope guard). All fixed in `5ba389c5`.

**CI and PR findings.** `coderabbit` posted 10 inline findings on `5ba389c5`/`1137301b`; every one has a reply and a
resolved thread, and `review-gate` reports `review_body_findings_total=10`, `handled=10`:

- fixed: the next-transition coverage (the operator test now advances), the codec's `Math.toIntExact` overflow
  (`intOf`), the re-usable provenance mark (`Verified` now names the text digest; a changed text is refused), the
  recovery entry points (`stepOut`/`unwindToFallback`/non-persisted overloads report `precondition_unmet` instead of
  throwing), a second version in one advance (`precondition_unmet`), the event sets (derived from `trigger()`), the
  schema's refusal enum (parity test) and the model-check wire names (mapping test).
- declined with a reason: deriving `CompositionChecksTest`'s resolver maps from the fixtures — each map is a deliberate
  variant (class and model cases), so deriving them would remove the matched controls.
- answered with a reason: the same-transaction advance after `composeOperatorEvent` — the composition's locked call is
  the boundary, stated in the Javadoc and the guide; preserving the staged resolver across calls would put version
  state in the engine, against its "no state outside the store" invariant.
- one residual is recorded as a known deviation: the schema's `cause.refusal` enum is held to `CompositionResult.Refusal`
  by a parity test rather than generated, because generating it would add a build plugin (CLAUDE.md forbids that without
  the operator's decision); CodeRabbit's second reply asked for generation.

**Build and environment.**

- The CI run at `1137301b` failed on Maven Central `429 Too Many Requests` (infrastructure), not the code; a rerun was
  green.
- The quality gate hit its 330 s bound twice on a loaded machine; the split/raised-bound rule was used (`--timeout 600`,
  which the daemon bound may only raise).
- One `pm-core` test (`DiagnosticCollectorRedactionTest`) timed out under load in a scoped run and passes in isolation
  (job `d44b6b8e57c544da9f76c46a846d9811`); it is not this plan's code.

## Reviewers

- `coderabbit` (required): reviewed. Qualifying review: the `COMMENTED` review of `1137301b7f8c…` carrying the
  `final_review_risk_coverage` marker (`kind=reviewed`, `coveredCommitId=1137301b7f8c…`, a head of this PR); the resolve
  result marks it `review_complete_after_handling`. Its 10 findings are all handled (above), every thread is resolved,
  and `review-gate --required-reviewer coderabbit` answers `merge_allowed: true` with `unresolved_review_threads=0`.
  Trigger history: `@coderabbitai review` at 18:41 (first review 18:57), re-triggered after the later pushes (the bot
  answered that it had already reviewed the last commit; two later heads were covered by the answers, and the last
  reviews came back rate-limited / neutral). No re-trigger is owed for a later head under the `review_complete_after_handling`
  rule once every finding is handled.
- `sourcery` (optional): refused — "your pull request is larger than the review limit of 150,000 diff characters". Not a
  gate.

## Operator questions

none asked. Every decision was a rule the code or the docs could answer; the two judgement calls (the declined fixture
derivation and the answered same-transaction boundary) are recorded with their reasons above rather than escalated,
because neither bends a rule.

## Rule check

- Shell: **two breaches by me**, both reported rather than hidden: one `sed -n` read of `doc/Traceability.adoc` while
  looking for the PM-IMPL-5 block, and one `git grep` over `pm-workflow/src` while finding the `composition.record(`
  call sites. Both were replaced by the Read/Grep tools for everything after them. No `cat`/`ls`/`find`/`head` was used
  otherwise, no `&&`/`;`/pipe in a command, no temp file outside `.plan/temp/`.
- `guard: none on this harness (unenforced)` — no guard-log line can exist for this run.
- Rendered builds: every build was the resolved canonical through the build seam, routed to the daemon
  (`mechanism=daemon_longpoll` for every one); no build ran in-process and none was backgrounded. Fallbacks: none of the
  launch-fault class; two builds hit the daemon's 330 s bound (`a8af71cc…` timed out, `43b03cc9…`/`3791a936…` waited) and
  were re-run with a raised explicit bound, which is the sanctioned response, not a fallback.
- Spec read: `spec_source=ledger_worktree` (no `spec_differ` marker), so the newer copy was read.
- `executor-skew`: `match` (executor 0.1.2155, lane 0.1.2155) — recorded and proceeded.
- `.plan/` writes: the progress file and the landing message under `.plan/temp/`, the `ci` body slots under
  `.plan/local/plans/NO_PLAN/work/ci-bodies/` (each returned by a `prepare-*` verb). No spec or orchestrator file was
  edited.
- Commit trailers: every commit ends with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; the PR body carries no
  attribution footer.
- Restarts / re-dispatches: none; one session, two self-review sub-agent rounds on the same child session.
- Mailbox: `no_mailbox` / `never_delivered` at all three reads (setup, before self-review, before the merge gate);
  nothing was delivered, so nothing needed a disposition line.

## Build server

Setup preflight: `ready` (worktree preflight, `registered: true`, version `"1"`). Every build routed to the daemon
(`daemon_longpoll`); one unintended in-process build never happened. Per-build notes: two jobs were waited on with
`build_server wait` after a 300 s long-poll returned `queued` (`33f2b784…`, `7af255db…`); two runs (`a8af71cc…`,
`3791a936…`) were re-run with `--timeout 600` after the 330 s explicit bound expired; no `killed` job and no
`runtime_stamp_reason` was reported (the stamp's `plugin_version` is `unknown` because resolve could not read it).

## Residue

- `PM-WATCH-DSL-21` stays open with its remaining half named (the `finalize.next-step` scenario over a composed version
  that prunes a finalize step below order `900`).
- The schema enum's derivation (rather than its parity test) is a known deviation, recorded above and in the thread.
- `PM-WATCH-DISP-67` stays open for `decision-quorum-and-calibration`, as the spec settled.
- The plan-creation caller (`start-from-clients`) must not create the plan on a refused `start`; the PR body says so.
