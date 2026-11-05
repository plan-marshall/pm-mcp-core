envelope_version=1
sender_type=plan
sender_id=rb-v2-01-primitive-failure-reasons
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-10-31T23:48:30Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-01-primitive-failure-reasons
epic=pm-mcp-core-m2-m4-v2
pr=#267
merge_state=open
cleanup_owed=true
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:blocked,merge:pending
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

## Deliverables

**D1 — the contract types: met.** All Done-when clauses are covered by `OutcomeReasonTest` and
`PrimitiveResultTest`; order `test-first` (failing run `bab73dca`, build log `216f71ae604248ecac29509a7e393993`,
compile failure `OutcomeReason` missing; green at `db12c287`). `PrimitiveContractTest` is unchanged and green
(`order: none`, regression). Commit `db12c287`.

**D2 — the registry-time checks: met.** `PrimitiveRegistryTest` + `ReasonPrimitives`; order `test-first` (failing run
`e6ee5193`, build log `dd663ba84a8e4162b5ace4509e62a8ac`, compile failure on the five new `Reason` constants; green at
`4d646d19`). `fixtureCoversEveryReason` unions `TestPrimitives.refused()` and `ReasonPrimitives.refused()`; the
completeness expectation is `EnumSet.allOf(Reason.class)` at run time; the 30 existing refusals keep their reason.
Commit `4d646d19`, later refined by `7e06dce0` for the Sonar findings.

**D3 — the specification: met.** `05-primitives.adoc`: the SPI block carries `OutcomeReason`, `reasonType()` and the
fourth component; the `SKIP` bullet is `skipped` plus a reason; the new `[#outcomes-and-reasons]` subsection states the
rule, the admitting classes, the closed condition table, that a reason is never an edge, and the prose example; existing
anchors stay and the concrete example is byte-stable. Order `none`. The test-tree search for `05-primitives` in `*.java`
finds only the main-source Javadoc reference in `pm-workflow/.../primitive/package-info.java:13`. R17 links check:
`BROKEN LINKS 2`, both the sibling's pre-existing `model-work` links to `first-level-thinker-counterfeit-policy.adoc`.
Commit `0d3afcee`.

**D4 — the developer guide and eight ADRs: met.** `core-spi.adoc` has the two contract rows, the pairing invariant,
the five refusal rows in enum order, `[#outcomes-and-reasons] Outcomes and Reasons`, one added step, and two Pitfalls
items; ADRs `005`–`012` each carry all seven template sections and use the next free numbers against `origin/main`
(001–004 at `7f72526`). Every registry claim names its test. Order `none`. Commit `520c6969`, refined by
`ced91c4f` and `f9f98be5`.

**D5 — the documentation hand-off: met.** `plan-marshall/plan-marshall-documentation#138` (branch
`chore/primitive-outcome-reasons`, commit `4f641ae`) carries exactly three hunks: the PM-IMPL-5 sentence, the
`OutcomeReason` row plus the extended `PrimitiveResult` row, and the roadmap Present list. Order `none`. Merging it is
the operator's. It is listed in PR #267's body.

**Overall Done-when:** all clauses met except the one that depends on the merge being possible — the four builds are
green on `7e06dce0` (`verify -Ppre-commit`, `verify`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`),
SonarCloud lists 0 open new-code issues with a green quality gate, and the links count is the sibling's own only. The
five deliverables' tests pass beside the full reactor (`8,444` tests in `verify`); no existing outcome enum, primitive
fixture or `new PrimitiveResult(` site was edited.

**Per-head test runs:** `pm-core` scoped tests at `db12c287` (91), `4d646d19` (106), `c6eef100` (108), `7e06dce0` (108);
full `verify` at `c6eef100`, `f9f98be5`, `7e06dce0`. A project that publishes no `module-tests` canonical is gated by the
gate canonical it does publish.

## Personas and skills

- Production code (`OutcomeReason`, `PrimitiveOutcome`, `PrimitiveResult`, `PrimitiveRegistry`,
  `PrimitiveRegistrationException`, `package-info`): `persona-implementer`.
- Unit tests (`OutcomeReasonTest`, `PrimitiveResultTest`, `PrimitiveRegistryTest`, `ReasonPrimitives`):
  `persona-module-tester`.
- Documentation (`05-primitives.adoc`, `core-spi.adoc`, ADRs, Traceability): `persona-documenter`.
- Self-review rounds and PR-comment triage: `persona-code-reviewer`.
- The spec declares no `Skills:`/`Persona:` lines; `skills: none named`. R20 coding skills read as standards:
  `pm-dev-java:java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing`,
  `cui-logging`. No surface drift: no module outside the spec's Expected Surface was touched.

## Hypotheses

- HYPOTHESIS "the opening of a pull request in `plan-marshall-documentation` is possible from the lane" — **confirmed**;
  PR #138 exists.
- HYPOTHESIS "`Optional<Class<? extends OutcomeReason>>` reads well at the declaration" — **confirmed**; every fixture
  writes `Optional.of(WellFormedReason.class)` and compiles without a raw cast.
- Verify-first clause — confirmed at HEAD: `ci pr list --state open` showed no overlapping PR; the 59
  `implements PrimitiveOutcome` sites and 23 `new PrimitiveResult` sites match the spec's counts.

## Findings

- Self-review round 1 (sub-agent): found the missing `doc/Traceability.adoc` lines, a `ReasonPrimitives` whose
  well-formed primitives could not execute, missing assertion messages, a `@UtilityClass` omission, an unexercised
  interface default, an unused fixture, three ADR wording/accuracy defects, an incomplete guide defaults list, and an
  over-claim that the admitting-class refusal was proven. All fixed in `ced91c4f`.
- Self-review round 2: found a dead guide anchor in Traceability (the guide heading had no explicit id), an inaccurate
  "eight nested enums" count, and two residual process items. The anchor, the count and the unused fixture were fixed in
  `f9f98be5`; the ADR-date and fixture-budget items are reported below.
- SonarCloud on the first head reported 10 new issues (S2583/S2789/S2589 null-tolerance checks, S3776 cognitive
  complexity 17, two S5778 test lambdas). All were fixed: the reason checks were split into helpers with a stated
  suppression on the deliberate null-tolerance points, and the test lambdas hoisted their arguments. The re-analysis
  reports 0 open issues and a green gate at `7e06dce0`.
- PR comments: CodeRabbit and Sourcery quota notices and our own triggers only; there are no review threads and no
  pending review findings to triage. The SonarCloud summary comment records the green gate.

## Reviewers

- `coderabbit` (required, quota-limited): **refused**. Triggered at 20:35Z on `f9f98be5`; refused 20:34:50Z; waited 90
  minutes and re-triggered at 22:05Z on the current head `7e06dce0`; refused 22:05:37Z; waited 90 minutes and
  re-triggered at 23:37Z; refused 23:38:08Z with no ETA. No qualifying review exists, so the standing authorization does
  not apply. Next allowed trigger: 2026-11-01T01:08:08Z.
- `sourcery` (optional, quota-limited): **refused**. Triggered 20:35Z; refused 20:34:44Z with a hard quota of "1 day and
  2 hours". Optional, so it does not gate the merge.

## Operator questions

None asked. No question tool was used and no free-text operator message was received.

## Rule check

- `guard: none on this harness (unenforced)`.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker. The spec's own First Instruction standing rules
  were read at revision r4; the rules that bind: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R17, R18,
  R19, R20.
- `executor-skew`: `match` (executor `0.1.2041`, lane `0.1.2041`).
- Deviations recorded, not silent: (1) `PrimitiveResult.reason` is an `Optional` record component, mandated by spec
  Decision 4 and consistent with the existing `spawnedJobId` component, against R20's general rule; (2) `ReasonPrimitives`
  is 574 lines, over `junit-core`'s 400-line test budget, which R20 says is reported, not enforced; (3) ADRs 005–012
  carry the plan date `2026-10-31` while the template asks for the merge date — correct them at merge if it slips.
- The documentation PR was created by running the `ci` verb with `GH_REPO=plan-marshall/plan-marshall-documentation`
  because `ci` has no repository selector; every other CI action ran without it. The R17 links check ran the prescribed
  `trace.py` from the main checkout. The Sonar read used the public REST API, since `resolve` reports `not_configured`
  and the public read needs no token (R18).

## Build server

Setup preflight: `ready` (enrolled). Builds: `daemon_longpoll: 20`, `in_process_fallback: 0`. One fallback entry:
`verify -Pcoverage` at `c6eef100` hit the daemon's supervisory bound (`timeout`, `timeout_used_seconds 640`,
`timeout_source explicit`) and was retried with `--timeout 1500`, passing in 848 s; the later coverage runs used the
raised bound. No launch-fault retry, no degraded fallback, no `killed` job.

## Residue

- **PR #267 is open and unmerged.** All non-review gates are green: CI green at `7e06dce0`, `verify`,
  `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am` green at `7e06dce0`, Sonar
  quality gate green with 0 open new-code issues, 0 review threads.
- **The required reviewer could not be obtained.** CodeRabbit refused on quota twice across two full 90-minute waits
  (last refusal 2026-10-31T23:38:08Z); the next allowed trigger is 2026-11-01T01:08:08Z. No qualifying review exists,
  so the standing review authorization does not apply and the merge was not attempted. The quota policy's remaining
  steps (further waits/escalation, then a fresh PR) are for the operator's continuation.
- **Documentation PR #138 is open**; merging it is the operator's.
- The worktree `.plan/local/worktrees/v2-01-primitive-failure-reasons` is retained; cleanup is owed.
