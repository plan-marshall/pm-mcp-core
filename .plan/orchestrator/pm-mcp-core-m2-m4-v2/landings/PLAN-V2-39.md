# Landing Analysis: PLAN-V2-39 — the harness scan, the list and the admission of an unknown harness's verification

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #412 (merge `59e9b815`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-14 from the message
> `rb-v2-39-machine-harness-verification-001.md` of the runbook lane (harness opencode, model
> deepseek-v4.1-flash/high, executor 0.1.2118, standing rules r7). The message is a lead. The three layers were read by
> one read-only leaf from git at `origin/main` `32a711f9` (no build, no test run); the orchestrator re-read the points
> marked "re-read"; the provider figures were re-read from the SonarCloud public API.

## Deliverable Fidelity vs Spec

**Headline: the lane built a layer of pure value classes and a precondition function; it built none of the runtime work the spec's D4 prose names (the scan, the login and model probes, the confined `probe` jobs, the isolation stage), and the landing message names only the Icon Store and the recorded fixtures as owed.** The landing facts say `deliverables_total=2`, `deliverables_done=1`; the message says "the four Done-when clauses, all met" and "D4's deliverable prose NOT met" with the Icon Store as the one named gap (re-read, message lines 48 and 61).

| Item (spec D4 and D12) | Verdict | Evidence |
|------------------------|---------|----------|
| Harness scan as a confined probe job | not built | `HarnessScan` is a sorted value (`pm-core/.../profile/HarnessScan.java:33`); no class runs anything. |
| Login and model probes | not built (a state transition only) | `HarnessProbe.java:37-107` derives all_passed / one_failed / all_failed from two id sets; the model probe has no class. |
| Verification as a confined job of kind `probe` | not built | `HarnessVerification.admit` (`HarnessVerification.java:100`, re-read) validates and returns a `JsonNode`; ADR `core-profile/014`: "The gate decides and does not run". |
| Gate: only from `harness.verify-profile` after `add-consent` `yes` with the tokens | partly | admission is a precondition (`HarnessVerification.java:110-167`); `state`, `kind` and `answer` are caller-asserted values, nothing ties them to a `ConsentGate.Decision` or a consent record (`ConsentGate.java:192` already refuses `YES_REMEMBER` on a NEVER kind, so `requireAnswer` is largely redundant). |
| `VERIFY_HARNESS_PROFILE` never remembered | met | `ConsentKind.java:39`; compared as a value at `HarnessVerification.java:118,133`. |
| Draft validated as a profile before anything runs | met | `HarnessVerification.java:106` calls `ProfileValidator.validate`; `readable_paths` is not checked (owned elsewhere; the schema file requires it, `harness-profile-v1.schema.json:356,420`, so a draft that passes the Java validation can still fail the schema). |
| `harness.none` after every chosen harness fails; order, preselection, default by `harness_id` | met (pure) | `HarnessProbe.restsInNone()` (`:95`), `HarnessNone.java:35-87`, `HarnessScan.java:58-68,76-86`. |
| The list with coverage, version-keyed, `harness_store_unreadable` | met (pure) | `HarnessList.java:83-103`, `HarnessUsageCoverage.java:46,106`, `HarnessListings.java:51-66`. |
| Enrolled harness whose executable is no longer found | partly | only the profile-removed case gets a row (`HarnessListings.java:55-66`); the lane admits it. |
| **Harness Icon Store and its sanitising accept step** | **not built (named by the lane)** | `git ls-tree` shows no icon class (re-read); the operator chose on 2026-11-13 to proceed to the PR with the item open. |
| Decoy-in-home control, isolation stage, the `claude` `AGENTS.md` fallback (PM-WATCH-CLIENT-16, #395) | **not built (not named)** | `ProbeWorkspace`, `DecoyServers` and `VerifyingBinaryCheck` are not in the 25-line diffstat (re-read: none matched); the watch files are untouched; the message says "no test covers the guard" and never that the isolation stage was not built. |
| D12 the install-path seam | met | `VerifiedUsage.java:52-80` (two evaluations that agree on figures and start context), `UsageTrustRefusal.java`, ADR `runtime-launch/008`; no production caller (the verb is owed to plan-marshall-mcp). |
| D12 the recorded fixtures and repeated evaluation (a real run) | not built (named) | PM-WATCH-CLIENT-21 fixtures not added. |
| `readable_paths` | not built (correct) | belongs to `job-confinement-and-environment`. |

Done-when scans: `git grep -nF '[a-z][a-z0-9-]{1,31}' -- '*/src/main/java/*'` returns `core/id/ShortName.java:30` only (the pathspec without the trailing `/*` matches nothing); at the #412 merge commit `HarnessCandidate.java:44` carried a copy that the PLAN-V2-06 guard would have rejected, and #410 (merged right after) migrated it. `readable_paths` appears in no main Java code.

**`doc/Traceability.adoc` (+46 lines) overclaims:** it lists "the harness scan" and "the admission ... reached only from harness.verify-profile" as implemented by classes that run nothing. PM-WATCH-CLIENT-16 and -17 stay open, which is correct; -20 and -21 are read-only or D12 guard items and were not touched.

## Three-Layer Analysis

### Layer 1 — Specified (not coherent as a runtime build)

- **A dependency the spec did not list.** The Objective and "Where" put the scan, the probes and `ProbeWorkspace`/`DecoyServers`/`VerifyingBinaryCheck` in `pm-runtime` as confined probe jobs. The settled asks of 2026-11-10 hand the `paths` stage, `readable_paths` and the probe-job confinement (its Task 7) to the open package `job-confinement-and-environment`, and the spec's own re-grounding record notes that the stage runner is missing. "Dependencies and Sequencing" lists none of these, and Carry #187 ("settle at the outline") has no recorded settlement. The run-the-job half of D4 had no owner and no buildable base: **this is the orchestrator's miss at the emit, not the lane's**: my re-grounding of 2026-11-13 read the same facts ("no stage-runner class exists", the boundary with `profile-verification-stages`) and wrote them as a boundary, not as a blocker.
- Stale at the emit: the Expected Surface lists `ProbeWorkspace`, `DecoyServers` and `VerifyingBinaryCheck` as D4 surface while the same text says their controls belong elsewhere; "validated as a profile" does not cover `readable_paths`; PM-WATCH-CLIENT-16 was "owed to the isolation stage of D4", which D4 has none.
- The boundary with `profile-verification-stages` holds on paper; in practice nobody owns the stage runner (`StageRunner` is absent).

### Layer 2 — Implemented

As the table. Every new class in `pm-core` and `pm-runtime` has no production caller; they are inert until the assembly (`plan-marshall-mcp`) consumes them (expected, and the message does not say so).

### Layer 3 — Correct

Nothing runs, so the confinement, process, icon-store, SVG and record questions do not apply (the new files use no `java.net`, `ProcessBuilder` or `java.nio.file`, and the exception map of the outbound-call guard was not touched). By reading:

1. **A behaviour change outside the spec (moderate, re-read):** `HarnessEnrolment.updated` (`pm-runtime/.../launch/HarnessEnrolment.java:96-114`) replaces the usage report with an empty DECLARED report on any version or digest change; it was added to satisfy a CodeRabbit finding and is in neither the spec nor the surface. It also drops a profile-DECLARED report, not only a VERIFIED measurement, so after a legitimate self-update the list shows `not_reported`. Store Schemas 11 does not say a self-update clears the report.
2. **A wrong refusal code (minor, re-read):** `requireStages` uses `TOKENS_MISSING` for an empty, a duplicate and an unknown stage (`HarnessVerification.java:137-152`).
3. A simple-name collision across modules: `core.profile.HarnessVerification` (new) and `runtime.launch.HarnessVerification` (existing sealed interface, imported at `WorkerSupervisor.java:37`).
4. A duplicate derivation: `HarnessUsageCoverage.of(UsageResult)` is unused in production and `HarnessListings.coverage(UsageReport)` re-derives the same coverage; the literal `harness_store_unreadable` is defined again at `HarnessUsageCoverage.java:47` beside `LaunchRefusal.java:40`.
5. `client_id` against `harness_id`: the new core classes use `harnessId`, `HarnessListings` and `VerifiedUsage` read `enrolment.clientId()` (consistent with the spec's "the member stays `client_id` until harness-renames"); the verification-record example at `01-profile-schema.adoc:417` that the reader refuses was untouched.

## Conformance to the Project's Own Rules

- **`@Builder` rule broken (re-read):** `HarnessVerification.Request` has eight components and no `@Builder` (`HarnessVerification.java:72`); `HarnessCandidate` and `HarnessListEntry` carry it.
- Javadoc: `HarnessList.java:27` uses Markdown backticks instead of `{@code}` and names a `pm-runtime` class from a `pm-core` doc.
- Conforming: `@NullMarked`, `Optional` as a return only, no generic catch, no logging (no `LogRecord` change), no Mockito or hand-written fake, no AAA comments, `Generators` used; about 55 assertion messages checked by script, all 20 to 60 characters (multi-line messages and `assertAll` headings not counted).
- The lane did not load the Java skills (the `@Builder` rule and the `HARNESS_ID` copy are what they would have caught).
- Sonar `pullRequest=412`: gate `OK` (re-read); the lane reports 0 open.

## Unverified leads

`ProfileFixtures` and the `ProfileRules` semantics; the bodies of `HarnessListTest`, `HarnessListingsTest` and `VerifiedUsageTest` beyond a sample; `core-profile.adoc`, `runtime-launch.adoc` and ADRs 013 and 008 in full; the `OutboundCallsArchTest` result; the `harness_store_unreadable` coverage for every harness path; the second self-review round; the Sonar and CodeRabbit threads.

## Metrics and Anomalies

`total_tokens` unknown (opencode lane), harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2118; `deliverables_total=2`, `deliverables_done=1`; three self-review rounds (round 1: three blockers, the Icon Store accepted and reported, partial stage-token consent admitted and fixed in `98ae0522`); the base merge and formatter rewrites at the tail; a file moved into the worktree by `mv` after being created untracked in the `pm-core` tree.

## Routing and Merge Behavior

One pull request through the merge queue. No `candidate-lesson` message from the runbook lane; nothing routed. The deficit of this landing is a spec and emit-gate matter (a dependency the Dependencies section did not carry), kept here and in the follow-ups; it is not a lesson for `plan-marshall:*`.

## Reconciliation Actions

Row PLAN-V2-39 `running` -> `shipped` (a partial delivery: the row's status vocabulary has no "partial", so the landing record and the new plan carry the remainder); `pr` `#412`, `landing` `landings/PLAN-V2-39.md`, `plan_marshall_plan_id` `rb-v2-39-machine-harness-verification`; message archived; Watch entry in `epic.md`; decision log; resume anchor.

## Follow-Ups

Placed 2026-11-14. No staged plan fits, so under the leftover rule a regular cleanup plan is cut:

1. **New plan PLAN-V2-142 `harness-verification-residue`** (WS-04, runbook lane): D1 the Harness Icon Store and its sanitising accept step (the operator-settled item the lane left; a hand-written SVG rewriter needs its own design round and a negative-control test list); D2 the corrections of PLAN-V2-39's own code and claims (`doc/Traceability.adoc`: PM-CLIENT-5 and PM-SEC-8 no longer claim the scan or the run as implemented; `@Builder` on `HarnessVerification.Request`; the stage-list refusal code; narrow the `HarnessEnrolment.updated` drop to VERIFIED reports, ask-at-refine, recommendation narrow, about 70%; the duplicate `harness_store_unreadable` literal and the unused `HarnessUsageCoverage.of`; the Javadoc of `HarnessList`; the enrolled-but-executable-gone row; the name collision is for `harness-renames`).
2. **The confined probe jobs, the stage runner, the isolation stage with the decoys and the `claude` `AGENTS.md` fallback (PM-WATCH-CLIENT-16)** depend on the open packages `job-confinement-and-environment` (Task 7, Task 2) and `profile-verification-stages`, neither of which has a staged plan in this epic: **an operator decision** (stage a plan for them, or accept that they wait for the assembly). Told to the operator; recorded in the anchor; PLAN-V2-142 does not take them.
3. **PLAN-V2-43** consumes `HarnessScan`/`HarnessList` for `auto.run` and `roles.default` order (its own dependency on 39 is met for the value classes only).
4. PM-WATCH-CLIENT-21 fixtures and the repeated real evaluation need a plan that can run a real harness: with item 2.
5. The install verb that calls `VerifiedUsage.install` is owed to `plan-marshall-mcp`.
