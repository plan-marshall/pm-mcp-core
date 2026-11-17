envelope_version=1
sender_type=plan
sender_id=rb-v2-153-machine-onboarding-remainder
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T01:14:45Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-153-machine-onboarding-remainder
epic=pm-mcp-core-m2-m4-v2
pr=#470
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-153 (machine-onboarding remainder)

PR #470, merge commit `1e9466ac`, merge-group run `43993230839` success. Standing rules: **r7 (2026-11-10)**, rules
**R01–R15, R17–R20** read in full at the session start; the first progress note says so.

## 1. Deliverables

| Deliverable | Verdict | Done-when and evidence |
|---|---|---|
| **D1** where each remaining deliverable lives | **met** | The table is in the PR body with a verdict per row (`D2`, `D7`, the `D8` states, the `D10` composition half, the `D11` call sites: buildable here / the assembly's / what the specification must correct). The one correction was made in this PR: `workflow-dsl/09` § Plan Behaviour Axes item 1's contradictory binding sentence (`53e1ce8e`), and `workflow-dsl/01`'s `onboard_run` list gained `auto` (`7eddafd6`). The package's Tasks 9/11/12 text was brought in line with `27378186` (`53e1ce8e`). Order: `none`. |
| **D2** the aspects' engine half | **not met as written** | The vocabulary and routing exist and are tested (`OnboardingResultTest`, `OnboardingContractReplayTest`), but the Done-when says every such branch has "a test that fails at `27378186`". Order: `none` — the at-base run could not be produced (see § 9) — so the clause is reported not met as written. |
| **D3** the settings states | **not met as written** | The states are delivered and tested (`MachineSettingsStatesTest`), and the two-changes case is proved by mutation: `verifyProvidersFirstChangeWins` fails when `transact` is mutated to overwrite (`MachineSettingsStatesTest:300`, build log `403eb6e6…`) and passes when restored (log `d507a176…`). Order: `mutation-after`. The at-`27378186` clause is unevidenced for the same reason as D2, so the deliverable is not fully met. |
| **D4** the axes in the composition and the landing check's contract | **met** | `LandingStepComposition` (merge + prune, every refusal), `PlanAxisLevels` (three levels, the posture, the readers' refusals), `MachineConfigurationStore.writePlanDefaults`; `LandingVariantValidation.Project` carries unknown answers with `LandingVariantValidationTest.unknownAnswersAreRefused`; the one-argument `compose(request)` is stated as the preview form with no production caller (Javadoc, Traceability, ADR 023); `doc/Traceability.adoc` PM-WF-25/PM-WF-26 name exactly what lands. Order: `none`. The assembly's wiring of the merge into the finalize phase is named owed. |
| **D5** the package closes | **met** | `doc/plans/machine-onboarding-unit.adoc` and its row are removed; the name is gone from every `Depends on` cell; five package files that pointed at it point at the Traceability entry or the assembly (`ca14c403`). Order: `none`. |

`deliverables_done` counts the fully met deliverables: **3 of 5**. The PR body said "4 of 5"; that was a miscount and
this landing is the record that corrects it.

**Owed to `plan-marshall-mcp`** (named, not delivered here): `onboarding.pmwf`; the seven aspect sub-workflows and their
probes/jobs; `auto.consent`/`auto.run`/`auto.result`/`auto.change`; the `settings.*` states as units and the settings' audit
(channel and device) at the local API; the `landing.*`, `method.*` and `ask.*` units; the `phase-6-finalize` unit and the
wiring of `LandingStepComposition` into its composition; the `DerivedTokenCache` registrations; the hold/retry that resumes
a held plan after a rotation.

**Landed by the earlier plans of the package** (as the package file recorded them): PLAN-46 Tasks 2, 5, 6, 7, 10; PLAN-V2-39
Tasks 3, 4; PLAN-V2-43 the two settings writers (documents mode, cleanup durations), `overrides.json`, and the engine half
of the landing check. This plan landed the rest of Tasks 1 and 8, Task 9's states, Task 11's composition half, Task 12's
contract, and the package's bookkeeping.

## 2. Personas and skills

| Step | Persona applied | Domain skills |
|---|---|---|
| D1 (docs/spec) | `persona-implementer` (as the spec declares; no production code) | `pm-dev-java:java-core`, `pm-dev-java:java-null-safety` (declared; the step is documentation and used `persona-documenter` judgment for the guide and ADR text) |
| D2, D7 (production code + tests) | `persona-implementer`, `persona-module-tester` | `java-core`, `java-lombok`, `java-null-safety`, `junit-core` |
| D3, D8 (production code + tests) | `persona-implementer`, `persona-module-tester` | `java-core`, `java-lombok`, `java-null-safety`, `junit-core` |
| D4, D10, D11 (production code + tests) | `persona-implementer`, `persona-module-tester` | `java-core`, `java-lombok`, `java-null-safety`, `junit-core` |
| D5 (documentation) | `persona-documenter` | `skills: none named` (the spec declares none) |
| self-review and PR triage | `persona-code-reviewer` | — |

Skills added for surface drift: none. The plan added `pm-core`'s `core.config.OnboardingAspect` and the ADR directory
`pm-workflow/doc/adr/workflow-onboarding/` (the guide and ADR of a new package, required by R06 and named in the PR body's
undeclared-surface note).

## 3. Hypotheses

- **D2/D7 can be built here once a proposal vocabulary exists in `pm-workflow`** — **confirmed**: the vocabulary and the
  router are delivered (`pm-workflow/src/main/java/de/planmarshall/workflow/onboarding/OnboardingRouter.java:36`,
  `AutoOnboardingResult.java:28`) and tested; the units stay owed.
- **The Expected Surface is inferred from the landing's accounting** — **partly confirmed**: `PlanFacts` needed a reader
  (`PlanAxisLevels.java:37`), `LandingStepComposition.java:34` is new, and `LandingVariantValidation.Project` changed type;
  `doc/specification/state-catalogue/19-onboarding-aspects-models-providers.adoc` needed no correction.
- **OBSERVED claims re-confirmed at the head**: `MachineConfigurationStore`'s two writers (`:149`, `:171` before this
  plan), `Traceability.adoc`'s PM-WF-25 "Not implemented yet", and `PlanInstanceComposer.compose(request)` → `compose(request,
  null)`.

## 4. Findings

- **Self-review round 1** (5 major, 9 minor, 2 nit): aspect names disagreed between the modules (fixed into
  `core.config.OnboardingAspect`); Traceability and two ADRs overclaimed the merge (reworded; a state two variants declare
  is now refused); the grammar's `onboard_run` lacked `auto`; the two-changes case moved to the state's own
  `verifyProvidersFirstChangeWins` and the mutation was re-run on it; a declined re-check wrote nothing (fixed and tested).
- **Round 2** (12 dispositions fixed, 8 new minor): `verifyProvidersUsing` for the post-rotation entry; the
  project-qualified withdrawal link with its test; the unbound variant's phase collision; Javadoc/ADR prose; Traceability's
  forward references.
- **Round 3** (8 dispositions fixed, 2 new minor doc defects): the merge's `@throws` clause and the settings prose; fixed in
  `984496b0`, not re-read by a fourth round — hence `self-review: budget-exhausted`, with nothing known open.
- **CI**: two infrastructure failures (Maven Central `429` on the sonar plugin and on the surefire provider) and two
  `LspClientTest` timeouts under load (`timeout`, `stubborn`); both reruns green. Not attributable to this branch.
- **PR comments** (CodeRabbit, 6 findings at `f6d0f306`): five fixed in `be04709f` (aspect vocabulary moved to `pm-core`;
  the routing prose; `LEVELS` derived; `level(level, null)` clears; the README package list) and one answered with a
  reason (the `plan_defaults` lists are held to the schema by `MachineConfigurationSchemaParityTest.planDefaults`; deriving
  them would parse a classpath resource inside a record constructor). All five threads resolved.
- **Sonar**: 29 new-code issues at `984496b0` → fixed in `89e7fde0`, `80e590ce`, `f6d0f306` (constants, the unnamed
  exception pattern, no unboxing of a nullable `Boolean`, the merge split for complexity, utility methods beside their
  caller, one invocation per `assertThrows`) → 0 confirmed at the merge head.

## 5. Reviewers

| Reviewer | State | Head | History |
|---|---|---|---|
| coderabbit (required, quota-limited, `review_complete_after_handling`) | **reviewed** | `f6d0f306` (marker `kind=reviewed`, `coveredCommitId=f6d0f306`); merge head `08e70d36` | Triggered once at PR creation; the bot's own hourly limit refused the `be04709f` review ("next included review in 34 minutes") and the final push; standing authorization applied: qualifying review on record, every finding handled, all threads resolved, every other gate condition green on the merge head |
| sourcery (optional, quota-limited) | **refused** | — | Refused twice: "larger than the review limit of 150,000 diff characters" (hard limit, no retry) |

`review-gate` at the merge head: `unresolved_review_threads=0`, `review_body_findings_total=5`,
`review_body_findings_handled=5`, `required_reviewers` satisfied, `merge_allowed: true`.

## 6. Operator questions

None asked. No question-tool call was made in the run: no rule had to bend, no blocker required a choice, and the merge
gates all held on their own verdicts. Free-text operator messages: none beyond the invocation.

## 7. Rule check

- **Guard**: `guard: none` on this harness — no tool-call guard was installed, so nothing was enforced and the lane does not
  claim "all kept". The guard log (`.plan/temp/guard.log`) has no line for the run.
- **Shell rule breaks by the run itself** (counted from the actions, not the log): one `git ls-files … | grep -c pmwf` pipe
  during surface reading; one `rg` invocation that the shell did not have; one `python3 - <<EOF` no-op snippet. Each was
  abandoned immediately; no file was read or written through them, and the work continued through the file tools and the
  executor.
- **Executor only**: every script through `python3 .plan/execute-script.py …`, every build through the resolved `maven` build
  command with `--project-dir`; every CI action through `ci`; no `gh`/`glab`. The one exception is the links check R17
  prescribes (`trace.py` from the documentation checkout).
- **Git**: no staging of everything, no stash/restore/rebase/forced push; the package file deleted with `git rm`; the base
  branch merged (step 7) and the worktree removed with `git worktree remove` (never forced).
- **`.plan/` writes**: `.plan/temp/rb-v2-153-machine-onboarding-remainder/` (progress, evidence, links check), the
  `ci-bodies` slots, and this landing file — nothing else.
- **Spec read** (`resolve`): `spec_source=ledger_worktree`, no `spec_differ` marker.
- **Executor skew**: `match` — executor `0.1.2135`, lane `0.1.2135`.
- **Builds**: `verify -Ppre-commit` at `be04709f` (10,226 tests) and at the merged head `08e70d36` (10,300 tests);
  `verify -Pcoverage` (5,063) and `verify -Pintegration-tests` (2,681) at `3672ab72`; targeted runs at each fix round. All
  green.

## 8. Build server

- Setup preflight: `ready` (registered). `build-routing` before the first build: `present`, `auto`.
- Mechanism: **every** build ran `daemon_longpoll`; no `in_process_fallback`. One submit waited 204 s in the machine queue;
  one routed call returned a live job and was re-attached with `build_server wait --job-id … --bound 900` (green, 141 s).
- No launch-fault retry, no `degraded` build, no `killed` job.
- CI-side infrastructure failures (not the build server): Maven Central `429` on `sonar-maven-plugin` and on
  `surefire-junit-platform`, each rerun green with `ci checks rerun`.

## 9. Residue

- **The at-`27378186` evidence is blocked.** `git-workflow worktree-create --detach --base 27378186` answered
  `executor_in_worktree: absent` ("generation exited 0 but wrote nothing new … copy-from-main is disabled"), and its re-use
  answered the same. Section 1 of the lane says such a tree is not evidence; it was removed (clean before the removal). So
  the clause "a test that fails at `27378186`" is unevidenced for D2 and D3, and those two deliverables are not counted as
  met. Artifact: `.plan/temp/rb-v2-153-machine-onboarding-remainder/evidence.md`.
- **D4's wiring is the assembly's**: `LandingStepComposition` is delivered and tested; no caller composes
  `phase-6-finalize` in this repository. Named in the table, ADR 023 and Traceability.
- **The PR body says "4 of 5"; the true count is 3 of 5** (D1, D4, D5). This landing is the correction; the merged PR body
  was not edited.
- **Sourcery** refused by diff size (150,000 characters); the PR is over it. Optional reviewer, no gate impact.
- **CodeRabbit** did not review the final head (`08e70d36`) — hourly limit; its qualifying review at `f6d0f306` plus the
  handled findings is the standing authorization recorded in § 5.
- The `LspClientTest` timing flake under load is pre-existing (the PLAN-V2-43 landing records it too) and passed on rerun.
