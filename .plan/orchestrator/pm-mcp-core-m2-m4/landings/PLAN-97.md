# Landing Analysis: PLAN-97 — Endpoint harness (9 of 10; two Done-when clauses not met as written)

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #214 (merged as 1c8329ce, 2026-10-28, merge queue)

> Landing record for one shipped plan. Source: inbox message `rb-97-endpoint-harness-001.md` (runbook lane, opencode, deepseek-v4.1-flash/high,
> 2026-10-28T11:30:36Z), corroborated against git at `1c8329ce`, the pull request through `ci` and the SonarCloud public API. The message is a lead;
> every fact marked "checked" was read by me; no test was re-run. The lane read the ledger-worktree copy of its spec (`spec_source: ledger_worktree`, `spec_differ: true`:
> the main-checkout copy lacked the Build and Links clauses), so it ran on the adapted spec.

## Deliverable Fidelity vs Spec

Ten deliverables. The lane counts **9 of 10**; the package Done-when has two clauses **not met as written**. I agree with both readings.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 implements the endpoint-harness sections | met through D2 to D9; two Done-when clauses not met | see the two clauses below |
| D2 the endpoint profile | shipped-as-specified | Reported: `ProfileValidator` reads `profile_kind` first, 14 refusal cases (`EndpointProfileValidatorTest`). Not re-run. |
| D3 loopback only here | shipped-as-specified | Reported: the remote form and `system_one` admitted by the schema, refused with the member named. |
| D4 the class rule | shipped-as-specified | Reported: `HarnessProfile` projects `zero_tool` alone; `ProfileWorkerEligibility` claims only the endpoint stages. |
| D5 the request, D6 what it accepts | shipped-as-specified | Reported: streaming off, no tool, the four enders, `unmeasured` for a missing count. ADR `runtime-endpoint/004` records the 4-characters-per-token composition. |
| D7 enrolment and the owner check | shipped-as-specified | Reported: path and digest of the socket owner, refused `harness_not_enrolled` before any request. |
| D8 weights identity | **met as a seam, not wired** | Checked: `WeightsReader` is referenced from `EndpointEnrolment`, `EndpointVerification` and `package-info.java` only; `EndpointEnrolment` and `EndpointHarness` have no main-source user but `package-info.java`. The callers are `pm-mcp-server`'s and the onboarding units'. |
| D9 verification of an endpoint | **met as a seam, not wired** | Checked: `EndpointVerification` is referenced from `ProfileStages` and `package-info.java` only (the `ProfileStages` reference was not read in depth). `pm-operator profile verify` and `harness.verify-profile` are the assembly's. |
| D10 left out | shipped-as-specified | Reported: nothing of `judgment-endpoint`, the clients' role views or `start-source-intake` built. |

**Done-when clauses not met as written:**
1. *A model that returns prose under a schema ends the attempt **and leaves a screening `unscreened`*** — the screening is `start-source-intake`, which D10 leaves out and which has no code here. The first half is met.
2. *SonarCloud: no open issue other than one dismissed with its reason* — the gate is green but **7 issues stay open** and the dismissal surface refused (HTTP 400).

**Whole diff, checked** (`git show --stat 1c8329ce`): **53 files, +5,227/−130**; `doc/plans/endpoint-harness.adoc` deleted (35 lines), `doc/plans/README.adoc` and `doc/Traceability.adoc` (+50) changed:
the package row and file are gone (R01 applied), Traceability carries the lines of PM-CLIENT-2, PM-WORK-1 and PM-WORK-6.

**Surface delta** (`inbox landing-check`): `unmeasured`. The lane reports undeclared files: `AuditEvent.java` (`harness_updated`), `HarnessUsage.java` (endpoint usage factory), `ProfileWorkerEligibility.java`, two READMEs, four new test classes; R19 treats them as expected.

## Metrics and Anomalies

- Tokens: `unknown` (the 42nd landing). Self-review: `clear` after two rounds (20 and 8 findings, must-fix fixed in `dd9c8e25` and `997df724`).
- Anomalies: the first implementation round was **written into the main checkout** instead of the worktree (9 files) and moved with `git apply`; the main checkout was restored clean (the fourth lane to do this).
  The Sonar PR run failed for #207, which the operator had closed and re-opened as #214. `git add -u <dir>`, `perl -0pi` and one heredoc are listed as shell rule breaks. The build daemon again could not launch `mvn` (`execution_failed`, exit -1); every build ran in-process.
- The operator was asked five questions (executor check, three on the red Sonar gate of #207, rebase or merge); the base was **merged**, not rebased or force-pushed.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `9d26fe60` (15 findings) and `01b3132b` (2), all fixed or answered, no thread open on #214; re-triggered five times afterwards, silent on its hourly allowance. Sourcery refused on size.
- CI/merge: green on every head, merged through the merge queue as `1c8329ce` (checked on `main`).
- Sonar: the lane's `open_issue_count=n/a`. **Checked: `pullRequest=214` gate OK and exactly 7 open issues: six `java:S1135` (TODO comments the OpenRewrite suppression recipe inserts) and one `java:S3776` (`AnswerSchema.check`, cognitive complexity).**
  This matches the lane's own account. `branch=main` reads **9 open issues** after the merge (it read 2 at #204).
- Collision: PLAN-97 shared documentation files with PLAN-107 (accepted, doc-only); PLAN-107 is still running, so the overlap is for its lane to merge.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#214`
- [x] row `landing` stamped `landings/PLAN-97.md`
- [x] row `plan_marshall_plan_id` stamped `rb-97-endpoint-harness`
- [x] epic.md narrative reconciled (Queue annotation; Open Defects for the unowned screening clause, the unwired seams and the Sonar residue)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Seven open Sonar issues and nine on `main`.** The six `S1135` TODO comments come from the OpenRewrite recipe on every build (a repository-wide generated source, not this plan's code) and the `S3776` smell is a small refactor. Recorded as an Open Defect; whether a small cleanup plan, a recipe change in the parent POM or the dismissal credential (the same unconfigured Sonar write access as before) resolves it is the operator's decision.
- **Unwired seams** (`EndpointEnrolment`, `EndpointHarness`, `EndpointVerification`): owed to `pm-mcp-server` and the onboarding units (`machine-onboarding-unit`); recorded in the same Open Defect line as PLAN-61's unwired library.
- **The `unscreened` clause** waits for `start-source-intake`, which has no staged owner here.
- Owed to the milestone exit (R05): the concept and user documentation of the endpoint harness and the roadmap check-off in `plan-marshall-documentation`.
- `doc/implementation-watch/client-profiles.adoc` still references `PM-WATCH-CLIENT-15`, a later package. Digest of the whole binary per request is a cost, not a defect (ADR 002).
