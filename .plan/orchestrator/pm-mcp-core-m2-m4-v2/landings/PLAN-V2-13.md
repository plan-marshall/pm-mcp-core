# Landing Analysis: PLAN-V2-13 — plan-instance-versions (continuation)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #503 (merge `412d141e`, merged 2026-11-18T23:08:13Z; read at head `76fb9503`)

> Landing record, runbook lane (`rb-v2-13-plan-instance-versions`, harness opencode, model `deepseek-v4.1-flash/high`).
> Drained from `inbox/rb-v2-13-plan-instance-versions-001.md` on 2026-11-19. Every claim of the landing message is a lead:
> PR state, merge SHA, the review numbers, the double-baseline delta and the PR body were read back against git and the
> `ci` abstraction. `inbox landing-check`: `complete: false`, missing `total_tokens` and `plugin_version`, both
> recorded by the lane as `unknown` (a failed read, not a zero).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the triggers (server events compose in the entering transaction; operator events from the next transition; refused composition) | shipped-modified | `WorkflowEngine.java:1137-1155` (`composeAtEntry`), `InstanceVersionEngineTest.ServerEvents.composesInTheEnteringTransaction`. The creation path `start(…, store, tx, history)` throws where the PR says "a value" (L2-1); `recordKept` has no caller (L2-2) |
| D2 the version in force | shipped-as-specified | `InstanceComposition.unitsOf`, `WorkflowEngine.java:874-927` (`loadUnit`), `WorkflowStatusCodec` `instance_version` (`intOf`, range check) |
| D3 only verified versions | shipped-as-specified, one vacuous assertion | `PlanInstanceStore.requireVerified`, `Verified`; `PlanInstanceStoreTest.java:228` asserts before any write (L2-3) |
| D4 Rules 13 and 14 | shipped-as-specified, fail-open under `CompositionChecks.NONE` (L3-6) | `PlanInstanceComposer.java:558`, `CompositionChecksTest` |
| D5 the resources | shipped-as-specified | `PlanInstanceResources`, `PlanInstanceResourcesTest` |
| the package file and its index row removed | shipped-as-specified | `doc/plans/plan-instance-versions.adoc` deleted in the diff |
| PM-WATCH-DSL-21 guarded per server event | partial | the `finalize.next-step` half is not guarded; the lane states it and the watch stays |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`, resolved from `plan.effort`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing.
- **Re-read by the orchestrator at the file and line** (all held): `WorkflowEngine.java:297-306` and `:1225-1235` (the creation path throws `IllegalStateException`), `:602-610`, `:645-660` and `:1140-1155` (the non-transactional entry points), `:340-348` with `:872-876` and `:925-929` (the same-transaction claim), `CompositionEvent.java:27-31` (`START` is a SERVER trigger), `status.schema.json:77-80`, `PlanInstanceVersions.java:72-90`, `PlanInstanceStoreTest.java:199-231`, `ModelIdentity.java:36-38`, `LockDoubles.java:28-30`, `InstanceComposition.java:87` (no caller of `recordKept` in `*.java`), the PR body's ADR number and baseline sentence, and the baseline diff.
- **Not re-read, recorded as unverified leads:** `WorkflowValidator.java:2031` and `workflow-verify.adoc:14` (the leaf's L1-1 about the validator seams), `PlanInstanceStore.java:184-198` and the `Verified` mark covering the text only, `PlanInstanceComposer.java:558`, `PlanInstanceResources.java:45`, `WorkflowEngine.java:587` (string literal) and the test line numbers `InstanceVersionEngineTest:344/394/456`, `CompositionChecksTest:346-408`.
- **Verdict:** layer 1 findings (3, minor/info); layer 2 findings (1 major, several minor); layer 3 findings (1 major, several minor). No blocker.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's Expected Surface lists `WorkflowValidator.java` and its test as closing the Rule 13/14 seams in code; neither changed, the seams were closed by wording in `workflow-verify.adoc` | unverified lead (not re-read) | refutes | `WorkflowValidator.java:2031` |
| L1-2 | The spec counts five server events; `CompositionEvent.START` is also `Trigger.SERVER`, so a port may return it mid-plan and the engine composes it | checked | refutes | `CompositionEvent.java:29`, `WorkflowEngine.java:1142` |
| L1-3 | The schema says `instance_version` is null only for epic and workspace scopes; the new `start` overload admits a plan with null before its creation transaction composed version 1 | checked | refutes | `status.schema.json:79`, `WorkflowEngine.java:275` |

No finding names a labelled claim of the spec's `## Claim Labels` beyond the three the cleanup of 2026-11-17 already settled; nothing is persisted by `corpus set-verdict`.

**Layer 1 verdict:** findings — 3 (L1-1 not re-read)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `WorkflowEngine.java:1137` | `ServerEvents.composesInTheEnteringTransaction` | **L2-1 (major)** the PR body says "a refused start is a value, so the caller creates no plan"; `start(scopeType, scopeId, store, tx, history)` throws `IllegalStateException` from `composeStart` (`:1231`) and drops the refusal's reason and violations, and the refused log line staged at `:1189` is unwound with it; no test calls the overload | checked (re-read; grep of `pm-workflow/src/test` finds no call with a transaction argument) |
| D1 | `InstanceComposition.java:87` | none | **L2-2** `recordKept` (`kept` / `identical_adopted`, D1's log of a kept candidate) is declared on the port and called nowhere | checked |
| D3 | `PlanInstanceStore.java:184` | `unverifiedVersionRefused` | **L2-3** the assertion "nothing was written for a version without its mark" (`PlanInstanceStoreTest.java:228`) runs before any `recordComposed` call, so it cannot fail; the PR evidence table says it fails under the mutation | checked |
| report | n/a | n/a | **L2-4** the PR body names ADR `core-store/073` (that is the explorer-store ADR; the instance-version ADR is `074`), and says the double baseline "loses the three entries", the diff removes one line (`PlanHistoryEngineTest#HoldingEverything#1`) | checked |
| all | n/a | n/a | the lane's "all 5 deliverables done" holds for code and tests; the PM-WATCH-DSL-21 guard is partial (stated) | checked |

**Layer 2 verdict:** findings — 4

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `WorkflowEngine.java:650-657`, `:602`, `:407`, `:441`, `:1145-1149` | `unwindToFallback(status, "plan.return")` (or `stepOut`, `advance(status[,event])`) with a composition port mounted and `serverEventAt("plan.return") = FALLBACK` | these entry points build `Run(status)` with no transaction or history, so the composition returns `precondition_unmet`; no overload carries store, transaction and history for `unwindToFallback` and `stepOut`; the fallback composition cannot happen through the fallback entry point, and the scope gets a condition. Whether the assembly's mapping fires it is not shown | **major** |
| L3-2 | `WorkflowEngine.java:653` | a composed version that prunes or renames the fallback state | `unwindToFallback` validates the state against the built `unit.states()` while the chain reads the version in force; the guard passes and the chain faults `UNRESOLVED_NAME`, or rejects a state the version carries | minor |
| L3-3 | `WorkflowEngine.java:345` with `:874`, `:927` | `composeOperatorEvent` then `advance` inside one `ScopeTransaction` | the Javadoc says the advance reads the version through the port's units; the new `Run` has no `justComposed`, reads the committed store, and faults "carries no plan unit"; the test commits between the two calls | minor |
| L3-4 | `WorkflowEngine.java:1150` | a second server-event composition in one advance | `precondition_unmet` after the first version is staged; the refused scope keeps the condition after the commit and only `unwindToFallback` clears it (the open thread `PRRT_kwDOVBd2NM65PW5X`) | minor |
| L3-5 | `PlanInstanceVersions.java:75` (unverified lead, not re-read) | a stored unit file changed on disk after its commit | stored units are parsed but not digest-checked at read; a parse refusal reads as empty and the engine reports "carries no plan unit", hiding the parse reason | minor |
| L3-6 | `PlanInstanceComposer.java:558`, `CompositionChecks.NONE` (unverified lead) | an assembly that builds the composer with `CompositionChecks.NONE` | Rules 13 and 14 are skipped silently, a version composes with undefined roles and unresolved skills; codified by `noChecksComposes`; the Done-when asks for refusal | minor |
| L3-7 | `PlanInstanceStore.java:194` (unverified lead) | a copy of a returned `PlanInstance` with its header and units swapped | the `Verified` mark covers the version and the digest of the plan-unit text only; the swapped set is stored (in-process) | minor |
| L3-8 | `PlanInstanceResources.java:45` (unverified lead) | a malformed header | `version(int)` throws `IllegalArgumentException` where the Javadoc says empty; `status` shows the composing event, not the last attempt | minor |
| L3-9 | `WorkflowEngine.java:587`, `:126` (unverified lead) | the question path | still raises the literal `"precondition_unmet"` instead of the new constant | info |

**Layer 3 not shown by reading:** the RoleResolver is fixed at composer construction, so whether an override event composes against the post-override role set depends on the assembly; the 11255 tests, coverage and Sonar need a run.

**Layer 3 verdict:** findings — 9 (1 major: L3-1; L3-5 to L3-9 not re-read)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | no generic `Exception`/`RuntimeException` caught or thrown in production | conforms | leaf read; `IllegalStateException`/`IllegalArgumentException` only |
| CLAUDE.md § Code Standards | `Optional` only as a return type | conforms | leaf read |
| CLAUDE.md § Code Standards | a class of only static methods is a Lombok `@UtilityClass` | **deviates** | `ModelIdentity.java:36-38` and `LockDoubles.java:28-30` are final classes with a private constructor (re-read) |
| CLAUDE.md § Code Standards | no new hand-written fake (ADR 026) | conforms | the baseline loses one entry and gains none (re-read) |
| CLAUDE.md § Code Standards | records of four or more components carry `@Builder` | conforms | leaf read, not re-read |
| CLAUDE.md § Build | coverage 80%, verify, Sonar | not_shown_by_reading | needs a run; the `start(…, tx, history)` overload and the `recordKept` path have no test |

## Metrics and Anomalies

- Tokens: `unknown` (the lane measured none; recorded as unobserved).
- Duration: not measured in the message; two sessions (the first lane stopped out of context at `be033795`).
- Anomalies: the first lane stopped out of context; the quality gate hit the daemon's 330 s bound twice (re-run with 600 s); one `pm-core` test timed out under load and passed in isolation; CI at `1137301b` failed on Maven Central `429` and was rerun green; `plugin_version` is `unknown`.

## Routing and Merge Behavior

- Review: coderabbit required, sourcery refused (diff limit). **The landing's review numbers are contradicted.** `unresolved_review_threads=0` and `review_body_findings_total=10`; `runbook_lane review-gate --pr-number 503 --required-reviewer coderabbit` on the merged head reads `unresolved_review_threads: 2` and `review_body_findings_total: 12`, `merge_allowed: false`, reason `unresolved_review_threads`. The two open threads were posted 2026-11-18T22:50:24Z on head `2cacb5d3`, eighteen minutes before the merge at 23:08:13Z, and carry no resolution: `PRRT_kwDOVBd2NM65PW5X` (`workflow-engine.adoc:161`, the guide says the refusal clears on commit, only `unwindToFallback` clears the condition) and `PRRT_kwDOVBd2NM65PW5v` (`CompositionChecksTest.java:258`, derive the reason comparisons from `CompositionRefusal.Reason.values()`). Recorded as a **false claim**: the landing asserted a merge-gate state the provider does not show. `review_body_findings_handled=10` is **not re-verified**: the lane's progress file holds no `finding:` line.
- CI/merge: merge queue, `412d141e`; CI rerun green after a Maven Central `429`.
- Sonar: `sonar_open_new_issues=0` claimed, `quality_gate_status` not carried; not re-read in this analysis (no Sonar read run), so it stays the lane's claim.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#503`
- [x] row `landing` stamped
- [x] row `plan_marshall_plan_id` stamped `rb-v2-13-plan-instance-versions`
- [x] epic.md: an Open Defect for the incomplete landing and the false review claim
- [x] the residue carried to PLAN-V2-114 item 54 (L2-1 to L2-4, L3-1 to L3-8, the two open threads)

## Follow-Ups

- L3-1 and L2-1 (major) go into PLAN-V2-114 item 54; the operator may prefer a plan of their own, because PLAN-V2-15 (now unblocked) builds on the engine entry points.
- PM-WATCH-DSL-21 stays open for its `finalize.next-step` half; PM-WATCH-DISP-67 stays open for `decision-quorum-and-calibration`.
- The plan-creation caller (`start-from-clients`) must not create a plan on a refused `start`.
