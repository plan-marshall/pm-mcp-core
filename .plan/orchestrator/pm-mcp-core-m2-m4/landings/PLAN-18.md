# Landing Analysis: PLAN-18 — Verify-not-resolve pipeline and wrapper verification

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #43 (https://github.com/plan-marshall/pm-mcp-core/pull/43), merge commit 68c6bc3

> Landing record for one shipped plan. Lives at `landings/PLAN-18.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `verify-not-resolve-024.md` (landing-facts complete, no missing key),
corroborated on 2026-10-13 against:
- the PR (CI on head fdf2268, `/review` history);
- the file list of 68c6bc3;
- the index and `PmMcpLogMessages` on `origin/main`;
- `plan-marshall-documentation` #46;
- the archived plan's decision log and build logs.

Harness: Claude Code (the operator's default).

## Deliverable Fidelity vs Spec

31 files, +4,590; the plan counts 8 deliverables, the 8th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Verify-not-resolve pipeline and wrapper verification | shipped-modified | `runtime.job` main and test; ADRs runtime-job 001–003; `runtime-job.adoc`. Operator decisions: (1) the wrapper git-tracked/committed requirement dropped from PM-EXEC-1, PM-SEC-5 and the Job Runtime specification — wrappers verified by location, enrolled name and digest; (2) the forbidden free flags apply to canonical build jobs only, while metacharacters are refused in every job kind |
| Enrolment touch points | shipped | `core.store` `ProjectEnrolment`, `runtime.enrolment` `EnrolmentCheck` and `EnrolmentPolicy` (the mandatory `maxLength` for enrolled patterns, the interpreter host-tool refusal) |
| Index row | shipped-as-specified | `verify-not-resolve` `done` |
| Module documentation | shipped | developer guide and 3 ADRs in `pm-runtime/doc/` |
| Documentation PR | in flight | `plan-marshall-documentation` #46 OPEN. It was written before the review loop-backs, so it does not cover the interpreter host-tool refusal, the `maxLength` rule or the `projectScriptArguments` carrier — a follow-up commit there is owed |

Surface: beyond the declared `runtime.job`, the plan also wrote `core.store` (`ProjectEnrolment`),
`runtime.enrolment` and `pm-runtime/pom.xml`. The POM change adds the test-scope dependency
`cui-test-generator`, which CLAUDE.md prescribes for test data; no consent is recorded in the
decision log, and it is a library the repository already uses. Log identifier: WS-04 WARN 180
(`JOB_REFUSED`).

## Metrics and Anomalies

- Tokens: 5,181,280; wall time 109,245 s (about 30 hours).
- Gates: `verify -Ppre-commit` ran at 06:39Z, after the final commit fdf2268 (06:38Z); CI green.
- Open review concern: regex matching time under the enrolment lock (ADR runtime-job/001), carried
  into PLAN-20's spec.

## Routing and Merge Behavior

- Review: `/review` re-posted four times. The only barrier override was for the **optional**
  Sourcery bot, which declined to re-review; both required bots verified the head.
- CI/merge: green; merge queue, 68c6bc3.
- Collisions: none (an index-only overlap with #42, #44, #45 and #46, auto-merged).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#43`; `landing` `landings/PLAN-18.md`
- [x] 23 lessons: 2 promoted here (2026-10-13-07-001, -002), 1 folded into 2026-10-11-20-001, 20 routed (`pm-mcp-core-m2-m4-169.md` to `-188.md`)
- [x] PLAN-20 spec folded: the lock-release rule and the regex-time concern (no surface change)

## Follow-Ups

- `plan-marshall-documentation` #46 to complete and merge (operator).
- Unblocked: PLAN-20 `job-runner`. It shares `core.store` with the running PLAN-12, so it is next
  after PLAN-12.
