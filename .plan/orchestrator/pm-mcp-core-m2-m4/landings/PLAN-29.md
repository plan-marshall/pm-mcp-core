# Landing Analysis: PLAN-29 — Worker launch and harness enrolment

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #172 (merged as 4806a30405eeaa79066922af6de84c79e741a6ac)

> Landing record for one shipped plan. Source: inbox message `rb-29-worker-launch-001.md` (runbook lane,
> opencode, created 2026-10-25T09:55:37Z, amended 2026-10-25T17:21:28Z, revision 1), corroborated against git at
> `4806a304`, the PR and its checks through the `ci` abstraction, the lane's progress file
> (`.plan/temp/rb-29-worker-launch/progress.md`), the SonarCloud public API for `pullRequest=172` and a links check
> run from the orchestrator. The message is a lead; every fact below marked "checked" was read from git, the PR or a
> run of mine, not taken from it.

Standing rules: r1 and R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R14, R16, R18 named by the
landing. R17 is not in its list; the lane ran the links check anyway (below). R05 is named and not met as written.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the job kind `llm` | shipped-as-specified | Checked: `LaunchComposer`, `HeadlessBlock`, `LaunchRequest`, `WorkerLaunch`, `WorkerStart` and the `SubprocessJobRunner` change exist in `4806a304`; the named vector tests exist (`writerVector`, `readOnlyVector`, `zeroToolVector`, `zeroToolForcesJobPrivate`, `tokenAndRecord`, `operatorAddedHarness` in both `HarnessVerifierTest` and `LaunchComposerTest`). Not run by me. |
| D2 — the job token | shipped-as-specified | Checked: `JobTokens` (`runtime.security`) and `JobTokenStore` (`runtime.launch`) exist; `JobTokenStoreTest.revoke` and `WorkerJobTest.startedWorker` exist. The spec declared a test directory under `runtime.security`; there is none: the token is tested from `runtime.launch`. |
| D3 — the harness enrolment store and the refusals | shipped-as-specified | Checked: `HarnessEnrolmentStore`, `HarnessEnrolment`, `HarnessVerifier`, `HarnessVerification`, `LaunchRefusal` exist; `tampered`, `notEnrolled`, `changedWithoutEvidence`, `unsupportedVersion`, `acceptedSelfUpdate`, `rebaselineUnreadable` and `WorkerJobTest.refusedWorker` exist. The lane's text names one more refusal code than the spec's three, `harness_store_unreadable` (an enrolment entry whose required object member is absent, `null` or not an object); `harness_tampered` was already in the specification, and `harness_store_unreadable` is named in no specification text, before or after this PR (checked in `04-worker-launch.adoc` at both heads). |
| D4 — the launch a resumed generation parameterises | shipped-as-specified | Checked: `ephemeralWithheld` exists in `LaunchComposerTest`; the continuation is `question-continuation`'s, as the deliverable says. |
| D5 — launch prompt, writing skill, extension isolation | shipped-modified | Checked: `PromptComposer`, `FreeTextDerivation`, `ContractEnvelope`, `LinkSchema`, `LinkParameter`, `SkillSource`, `ResolvedSkill` exist and `sharedPrefix`, `prefixOrder`, `closedStringField`, `roleSkillDoesNotAddWritingSkill` exist. The isolation clause is **met by composition only** (see Done-when 9). |

Carried from PLAN-84 (termination step 3a, the worker half): shipped. Checked: the PR diff changes
`SubprocessJobRunner` (descendant tracking for `llm`) and does not touch `ProcessTreeTracker.java`, which the spec
declared; the lane put the wiring in the runner as the epic's decision of 2026-10-22 allowed. ADR `runtime-launch/003`
records it. The Linux child subreaper stays owed by `pm-mcp-clients`.

Whole diff, checked (`git diff 6bd90ca7 4806a304`, first parent `6bd90ca7`): **57 paths** (36 `A`, 20 `M`, 1 `D`),
+5051 / −119: 22 + 6 `src/main` Java paths (`A`/`M`), 9 + 3 test Java paths, 5 added and 11 modified `.adoc` paths and
the deleted `doc/plans/worker-launch.adoc`. The landing does not state a path count.

Surface delta (`inbox landing-check` with the declared surface of `PLAN-29-worker-launch.md`, 18 entries, against the
57 realized paths): `expansion_detected`. **16 realized paths were never declared:** `pm-core` `Finding`,
`RemediationClass`, `SuppressReason`, `AuditEvent` and two `findings` tests; `runtime.job.WorkerStart`,
`JobEnvironmentComposer`, `SubprocessJobRunnerTest`, `WorkerJobTest`; `RuntimeLogMessages`; `pm-runtime/README.adoc`;
`doc/LogMessages.adoc`, `doc/Traceability.adoc`, `doc/specification/store-schemas/11-enrolment-store.adoc` and
`doc/plans/client-profile-rendering.adoc`. The lane names the `WorkerStart` and `JobEnvironmentComposer` seams and the
`pm-core` `Finding`/`AuditEvent` change in its own text; the log, traceability, README and store-schema paths are the
standard companions of a code change. **5 declared entries were not touched:** `ProcessTreeTracker.java`,
`doc/implementation-watch/client-profiles.adoc`, `doc/implementation-watch/job-runtime.adoc`, the ADR directory
`runtime-security/` and the test directory `runtime/security/`. The spec is terminal, so its declaration is not corrected.

Done-when, clause by clause (the spec's one paragraph):

1. **A launch-vector test per role class against a fixture profile — reported met, corroborated by presence.** The three
   named tests exist; not run here.
2. **A tampered harness enrolment refuses the launch — reported met, corroborated by presence** (`HarnessVerifierTest.tampered`,
   `HarnessEnrolmentStoreTest.tampered`).
3. **A `zero_tool` launch has no tool, no MCP entry and no loaded setting — reported met, corroborated by presence**
   (`zeroToolVector`, `zeroToolForcesJobPrivate`); "no loaded setting" is a property of the composed vector, see 9.
4. **A launch under a role assignment or plan override uses the effective values, a tier rendered to the profile's model —
   reported met, corroborated by presence** (`writerVector`).
5. **The job record carries `harness_version`, probed again after a digest change — reported met, corroborated by presence**
   (`tokenAndRecord`, `acceptedSelfUpdate`).
6. **A harness the operator added from a verified profile is enrolled and launched like a shipped one — reported met.** The
   PR body qualifies it as "enrolment + verification; the launch path is the same `LaunchComposer`"; `operatorAddedHarness`
   exists in both test classes. No launch was ever started on a live harness.
7. **The writing skill is derived from the link schemas — reported met, with mutation-after evidence.** The progress file
   records the failing run at `430ce0b9` (`FreeTextDerivationTest.java:47`, expected false, was true) and the restored pass;
   `430ce0b9` exists in git. I did not re-run it.
8. **Two launches of one role agree byte for byte up to the end of the role skill — reported met, corroborated by presence**
   (`sharedPrefix`, `prefixOrder`).
9. **A worker loads the project's instruction files and skill directories and no plugin, hook, MCP server, endpoint or
   environment value from any settings file — MET BY COMPOSITION ONLY.** `LaunchComposerTest.extensionIsolation` asserts the
   composed vector (env is exactly `headless.env`, the MCP configuration holds only the job relay) and the
   `extension_isolation` capability is required by `LaunchComposer.requireClass`; the landing says itself "not observed on a
   live harness in this environment". The clause as worded is behavioural, and nothing runs a worker yet (no production caller).
10. **A `string` field with an `enum` or a `pattern` does not trigger it — reported met** (`closedStringField`).
11. **A role artifact that names the writing skill does not add it to a task whose links carry no free text — reported met**
    (`roleSkillDoesNotAddWritingSkill`).
12. **Before every launch the executable is re-digested — reported met, with mutation-after evidence** (`unchanged`, failing run
    at `430ce0b9`, progress file line 44).
13. **The three refusals are recorded in `refusal_reason`; an accepted self-update rewrites `harnesses.json` and is audited
    `harness_updated` — reported met, corroborated by presence** (`unsupportedVersion`, `changedWithoutEvidence`, `notEnrolled`,
    `acceptedSelfUpdate`, `refusedWorker`).
14. **Sonar (Standing Constraint of the spec corpus, R18) — MET, checked.** `pullRequest=172`: gate `OK`, 0 open issues,
    new-code coverage 82.8 (the landing's `open_issue_count=0` and 82.8 both read back). `main` still reads 4 open issues,
    none of them this diff's.
15. **Links check (R17, `BROKEN LINKS 0`) — NOT MET as measured, and not this plan's.** `trace.py` from the main checkout at
    `4806a304` reports `BROKEN LINKS 1`: `phase-workflows/05-finalize-review-metrics-descriptor.adoc:170` →
    `PM-WATCH-GEN-60` (`missing-anchor`). The documentation checkout is 6 commits behind `origin/main`, whose
    `implementation-watch/cross-cutting.adoc:234` defines `[#PM-WATCH-GEN-60]` (checked), so the break is the stale sibling
    checkout, not `main`. The lane said the same. The PLAN-40 break (`runtime-enrolment.adoc:200`) is **gone**: #170 fixed it
    (`d4cf3675`; line 200 now reads `11-migration.adoc#PM-MIG-1`, checked), not this plan.
16. **`verify -Ppre-commit`, `verify`, `verify -Pintegration-tests` green; `verify -Pcoverage` green (R01) — REPORTED, one NOT MET.**
    The lane reports `pre-commit` green at `c134c681` (5058 tests), integration tests green at `4a3a824e` (2353) and a full
    `verify` green at `14ef3946` (7367 tests); the full `verify` at `4a3a824e` timed out past its bound with 0 failures. CI on #172
    is `success` over 30 checks (both JDK legs, integration tests, `sonar-build`, SonarCloud, `quarkus-alignment`, checked).
    **`verify -Pcoverage` is reported red in `pm-provider-git` (branch coverage 0.79 < 0.80) and was not run by me**: an unverified
    lead, recorded as a Watch. None of it was re-run by this verb; implementation builds are the lane's.

R01 deliverables beyond the Done-when, checked: the package file is deleted, its index row is gone, and `worker-launch` left
the _Depends on_ column of `supervisor`, `slots-usage-and-budgets`, `question-continuation` and `endpoint-harness` (the first two
now read `nothing`). `doc/plans/client-profile-rendering.adoc` was rewired to the developer guide.

## R05 — what was asked and what was done

The operator's instruction for this landing was: delete the specification sections and watch items PLAN-29 implemented, and
only those; leave the start-failure guard and output/usage reading in place; carry an Owed list. **The lane did the third and
not the first, and said so in the PR body but not in its rule check.**

- `doc/specification/model-work/04-worker-launch.adoc`: 40 lines removed, 83 added. The launch table, the agent-composition
  bullets and the refusal sentence were replaced by condensed paragraphs that open "Implemented by `…`" with a link to the new
  guide and a "Remaining:" clause. The section anchors stay. The PR body gives the reason: many xrefs in other specification
  parts and watch items target them.
- `doc/implementation-watch/model-work.adoc`: **no line removed**, six added ("Implemented (the self-update and refusal half)
  … Remaining: the start-failure guard"). The two declared watch files of `client-profiles` and `job-runtime` are untouched.
- `client-profiles/01-profile-schema.adoc`, `job-runtime/01-pipeline-and-subprocesses.adoc` and `store-schemas/11-enrolment-store.adoc`
  carry pointers and one added field (`events`), not deletions.

So the implemented prose is condensed and pointed at the code; it is not deleted. R05 as written ("the deletion of the implemented
specification sections and watch items") is **not met**; the departure is reasoned and disclosed in the PR body. It is also the
operator decision already open ("whether the instruction to delete implemented specification sections and covered watch items
stands"), now with a second data point beside PLAN-27.

## Owed section, checked against the PR body

The PR body carries the section and it names what was asked: the start-failure guard goes to PLAN-30 with its hook point
(`SubprocessJobRunner.startWorker` returns a `Started` job, `refuseWorker` a refused `JobId`, a `HarnessVerification.Refused`
is the start-failure event), the specification sections left in place and why, output and usage reading, and the Linux child
subreaper. **One remainder in it has no owner:** "the verify-not-resolve argument-schema validation of the composed worker
vector … which the worker start path does not run yet" (Model Work § Launch Contract). No staged spec names it: a search of
`plans/` finds verify-not-resolve only in PLAN-11, 18, 20 and 27, all shipped. Recorded as an Open Defect.

## Metrics and Anomalies

- Tokens: unknown (opencode measured none; `inbox landing-check` reports `missing_keys: [total_tokens]`).
- Model: `deepseek-v4.1-flash/high`, harness `opencode`, effort `high`, plugin and executor `0.1.1996`.
- Duration: not supplied. The PR's checks span 4766 s.
- Anomalies:
  - **The package was larger than one pass.** The operator was asked "PLAN-29 is a full subsystem … How should I proceed?" and chose to continue implementing; two builds were killed externally and one hit the 1200 s bound, the operator chose a re-run each time.
  - **CI was red for a defect outside the diff, and the lane fixed it on this branch at the operator's word.** The lane reports `main`'s own run red because of a schema/record mismatch between the specification and `Finding` after #171 (`docs(spec): suppression reasons, fix attribution, …`, which changed specifications only, checked) — I did not read that run. The fix is in the diff: `Finding`, `RemediationClass`, `SuppressReason`, `AuditEvent` and a `FindingSchemaParityTest` that derives the record tree from the types instead of a hand-kept set. It is **outside PLAN-29's declared surface** and is a `pm-core` change.
  - **Free text "merge and land" was not taken as a merge selection**; the lane asked, the operator chose "wait for a green CI on the current head", and the merge ran after CI was green on `25fdbad6` and on `c134c681`.
  - Self-review: one round, 20 findings, the confirmation round not dispatched (recorded as a process deviation). CodeRabbit: 10 inline threads plus 3 outside-diff findings, all handled. Sourcery refused (diff over 150,000 characters), optional, not gating.
  - Rule breaks the lane reported itself: the self-review sub-agent used `ls` once and `cd … && git …` once. The guard was `none on this harness (unenforced)`.
  - `executor_absent_from_worktree` again: every build ran `--execution-mode in_process`.
  - `PM-WATCH-GEN-60` is reported undefined by the lane's links check and by mine (stale documentation checkout, above).

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `4a3a824e`, `a898c37a`, `02215816` and `25fdbad6`; re-triggered after the last push on `c134c681` and rate-limited with no ETA; merged under the standing review authorisation (qualifying review on `25fdbad6`, its three findings fixed in `c134c681`, every other gate green). Sourcery (optional) refused.
- CI/merge: checked, `ci pr view` reads `merged`, head `feature/29-worker-launch`, merge commit `4806a30405eeaa79066922af6de84c79e741a6ac`, first parent `6bd90ca7` (#175). The merge was the operator's word ("merge and land" after a green CI), as the project rule requires.
- Sonar: read from the public API (above), gate OK, 0 open.
- Collision: the gate predicted PLAN-29 against PLAN-31 (code overlap, accepted by the operator at the emit) and the realized footprint confirms it: `declaration-currency` shows **34 of the 57 paths** inside PLAN-31's declared surface — the launch package, the `runtime-launch` ADR directory, the model-work watch and the index. PLAN-31 therefore has a real merge to make, and the ADR numbers `runtime-launch/001`–`004` are now taken (PLAN-31 writes `005` or later). PLAN-32 shares the model-work watch and the index README (2); PLAN-30 shares the index, the model-work watch and the profile-schema spec (3); PLAN-34 and PLAN-35 share the watch and the index (2 each); PLAN-97 shares 20 paths (the launch package, which it depends on); PLAN-42 and PLAN-71 share only the index README with it (1 each). **The 16 undeclared paths touch neither running plan:** PLAN-31 and PLAN-32 declare none of them (checked, path by path against `declaration-currency`'s per-spec lists). They fall inside the declared surface of staged and parked specs that have not started: PLAN-36 and PLAN-37 (`Finding`, `RemediationClass`, `SuppressReason` and the two `findings` tests), PLAN-46, 47, 57, 58 and 62 (`WorkerStart`, `JobEnvironmentComposer`, `RuntimeLogMessages`), PLAN-39 (`pm-runtime/README.adoc`), PLAN-45 (`11-enrolment-store.adoc`), PLAN-49 and PLAN-63 (parked). So the unpredicted expansion is a pairing caveat for those plans, not an observed collision. `corpus declaration-currency`: 6 specs checked and clean, 1 could not be checked (PLAN-102, prose).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-29 --status shipped`
- [x] row `pr` stamped — `#172`
- [x] row `landing` stamped — `landings/PLAN-29.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-29-worker-launch` (from the sender)
- [x] epic.md narrative reconciled: queue annotation for PLAN-29; `total_tokens` recurrence folded into the existing Open Defect; the PLAN-40 broken-link Open Defect retired (fixed by #170); two new Open Defects (R05 practice; the unowned worker-vector validation); the step-3a Watch updated; the "lanes must merge the base" and "unadapted specs" Watches updated; new Watches (the `Finding` change reaching PLAN-36 and PLAN-37; `verify -Pcoverage` red in `pm-provider-git`; PLAN-30 is releasable)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **PLAN-30 can resume.** Its hold reason ("until PLAN-29 is on `main`") is met at `4806a304`. The hold message says to re-run `action=outline` against the merged launch package; the Owed section names the hook points it should read.
- **PLAN-31 must merge `4806a304` before its merge gate** and take ADR numbers after `runtime-launch/004`; 34 of its declared paths are now changed on `main`.
- **PLAN-32** must merge the base too (index README, the model-work watch) and still runs on an unadapted spec: check its Sonar read and links check at its landing.
- **The verify-not-resolve validation of the composed worker vector is unowned** (Open Defect).
- **R05 practice**: the implemented prose was condensed, not deleted (Open Defect and the open operator decision).
- **`total_tokens=unknown`**: folded into the standing Open Defect.
- **Owed outside this repository**: the Linux child subreaper (`pm-mcp-clients`, `pm-exec`), the `pm-mcp-server` wiring that starts a worker (nothing calls the launch yet), and the concept and user documentation of worker launch and harness enrolment at the milestone exit.
