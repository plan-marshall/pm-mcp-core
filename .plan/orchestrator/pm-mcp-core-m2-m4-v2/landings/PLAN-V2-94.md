# Landing Analysis: PLAN-V2-94 — the static-only classes are `@UtilityClass`, the generic catches of `pm-runtime` are settled

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #437 (merge `27d28bb7`, head `7c18690a`), `merged` through the merge queue; 67 files, +142 / −204

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-94-utility-classes-and-generic-catches-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high; `total_tokens` unknown, `plugin_version` unknown: `landing-check` reports `complete: false`, missing `total_tokens` and `plugin_version`). Every point below marked re-read was read again by the orchestrator at its `file:line` at `27d28bb7` (`git show`), not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 `@UtilityClass` for the static-only classes | met | 61 classes (core 20, workflow 32, runtime 5, git 3, sonar 1); the scan `private [A-Z]…() {` at `27d28bb7` finds 14 sites, equal to the landing's list: the two held-out (`ProfileValidator.java:272`, `SecretRedactor.java:323`, both re-read), the four nested holders, the four builders, the four instance-member classes. The five static-import files switched to plain imports. |
| D2 generic catches of `pm-runtime` | met | the catch scan finds 18 sites at the State's lines (re-read count 18); the only code edit is `@SuppressWarnings("java:S1181")` with its reason at `SystemProcessExecutables.java:157` (re-read; the catch is now at `:165`); the other eleven are listed in the PR body (7 mentions of the job-runner and supervisor sites re-read in the body). |
| Done-when: Sonar, build, links | met, partly by the lane | Sonar re-read for `pullRequest=437&resolved=false`: total 0, gate `OK`; `review-gate`: 0 unresolved threads, 0 body findings, `merge_allowed: true`. The verify, coverage, integration and links runs are the lane's claims (not re-run, not re-verified). |
| Expected Surface | met | exactly the 61 classes, the five import files and `SystemProcessExecutables`; the leaf found no unexplained file in the 67. |

## Three-Layer Analysis

Read by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), no build, no test run, no write; reason: every full ship is read on its own leaf. The orchestrator re-read the points marked above.

- **Dispatch:** one read-only leaf per landing — reason: reading merged code is the depth the dispatch rule exists for.
- **Verdict:** layer 1 findings (3 minor); layer 2 clean, with one not shown by reading; layer 3 findings (3 minor).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D2 text says the six `TODO: Catch specific` markers are "resolved by the decision"; Done-when and State say they are PLAN-V2-46's and drop the clause | checked | refutes | spec D2 vs State; the State binds |
| L1-2 | Claim Labels count 17 catch sites and omit `JobSettlement:224`; Objective and State say 18 | checked | refutes | scan at `27d28bb7` finds 18 |
| L1-3 | "the repository has no mocking library" | checked | refutes | EasyMock in `pm-core/pom.xml:88`, `pm-workflow/pom.xml:65`; the conclusion holds because all 61 were already `final` |
| L1-4 | Lombok is a dependency of the five modules; 63 − 2 = 61 | checked | confirms | poms; diff 61 |

**Layer 1 verdict:** findings — 3 (minor, all settled in the State, left in the older text of a shipped spec).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1 | 61 `@UtilityClass`; `WorkflowLogMessages.java:24,31,50,105` like `CoreLogMessages` | none: nothing asserts the annotation or the scan | none | checked; the test is not shown |
| D2 | `SystemProcessExecutables.java:157` | none: no catch's behaviour changed | none | checked; met only vacuously on "a catch this plan changes has a test" |

No test was weakened: the two test edits are import swaps (`DefaultUnitLoaderTest`, `ProjectUnitLoaderTest`). **Layer 2 verdict:** clean; one point not shown by reading (that a build is green).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `WorkerSupervisor.java:325` (re-read) | any `RuntimeException` of the start path | the generic catch stays: it releases the slot and rethrows; it carries the in-code reason (`:321-323`) and the recipe marker, but no `@SuppressWarnings`; the State allowed the keep; a `finally` guarded by a started flag would remove the catch with identical behaviour and was not done | minor |
| L3-2 | `JobSettlement.java:219` (re-read) | untouched | `@SuppressWarnings("java:S1181")` on a `RuntimeException` catch, where S1181 does not apply, with the reason only in the Javadoc `:215`, which says the specification names the handler (PLAN-V2-127 I1 disputes that); pre-existing, not introduced here | minor |
| L3-3 | `pm-workflow/doc/adr/workflow-dsl/005-frontmatter-read-by-its-own-line-parser.adoc:26` (re-read) | — | still says `UnitFrontmatterReader` is a plain final class with a private constructor because `pm-workflow` does not use Lombok: false twice | minor |

No instance state silently made static, no reflection or subclassing of the 61, no static import left for the Lombok limitation. **Layer 3 not shown by reading:** that the gate, verify, coverage, integration runs are green. **Layer 3 verdict:** findings — 3 (minor, none a defect of what shipped).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md Code Standards | Lombok `@UtilityClass` for static-only classes | conforms | 61 annotated; 14 remaining constructors each named with its reason |
| CLAUDE.md Code Standards | never catch or throw generic types; last-resort handlers carry `@SuppressWarnings` with reason | deviates (minor, State-sanctioned) | `WorkerSupervisor.java:325`, `JobSettlement.java:219`; the four `invokeExact` `Throwable` catches conform (`MacSecurity:313`, `Posix:38`, `KillBinding:72`, `SystemProcessExecutables:165`); PLAN-V2-46's six and `MacSecurity:305` belong to other plans |
| CLAUDE.md Code Standards | JUnit 5 only, no Mockito | conforms | two import swaps |
| CLAUDE.md Dependencies | no new dependency | conforms | no pom in the 67 files |

## Metrics and Anomalies

- Tokens: unknown (the harness measured none; recorded as unobserved, not zero).
- Duration: not carried.
- Anomalies: the quality gate at `54de43bf` returned `running` after the client's 300 s and was re-attached with a bounded wait; a concurrent lane had overwritten the shared `NO_PLAN` body slot (round 2, proven by `ci pr view`); one explore sub-agent used a read-only `awk` print on the spec line over 2000 characters (the single rule deviation the lane records). No degraded build result.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota limited) refused four times (10:45Z to 15:31Z), the review landed at 17:40Z on `54de43bf` with 0 findings; Sourcery (optional) refused on its 7-day budget. The operator chose "keep waiting and retrying" at the fourth refusal. Re-read against the provider: 0 unresolved threads and 0 body findings, equal to the landing; `review_body_findings_handled: 0` is trivially consistent (no finding to handle). No false claim.
- CI/merge: merged through the merge queue, `27d28bb7` on `main`; no conflict reported. Executor skew at the start (0.1.2123 vs 0.1.2133), the operator chose regenerate.
- Sonar: landing `sonar_open_new_issues=0`, re-read 0 and gate `OK`: equal; no open rule.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#437`
- [x] row `landing` stamped `landings/PLAN-V2-94.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-94-utility-classes-and-generic-catches` (the sender, not typed by hand)
- [x] epic.md narrative reconciled
- [x] leftovers placed (below); `landings_incomplete`: 1 (`total_tokens`, `plugin_version` unknown: recorded as an Open Defect)
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- L3-3 (ADR drift `workflow-dsl/005`), L3-1 (the `WorkerSupervisor` restructure) and L3-2 (the `JobSettlement` annotation and its I1 Javadoc) → PLAN-V2-114 (`analysis-findings-fixes`), new items.
- `SecretRedactor` conversion: already carried by PLAN-V2-59; `ProfileValidator`: PLAN-V2-114 item 25. Queue annotation added.
- The 65 hand-written private constructors of 64 test classes: PLAN-V2-126 (already owned, D5).
- Overlap by hunk with PLAN-V2-67 at `MacSecurity.java:304-313`: none predicted, none observed in this landing (67 runs on its own PR #445); recorded for the merge of 67.
