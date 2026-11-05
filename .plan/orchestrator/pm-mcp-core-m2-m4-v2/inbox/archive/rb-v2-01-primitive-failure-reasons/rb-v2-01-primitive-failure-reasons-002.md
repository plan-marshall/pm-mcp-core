envelope_version=1
sender_type=plan
sender_id=rb-v2-01-primitive-failure-reasons
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-01T18:33:54Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-01-primitive-failure-reasons
epic=pm-mcp-core-m2-m4-v2
pr=#271
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

## Deliverables

**D1 — the contract types: met.** `OutcomeReasonTest` and `PrimitiveResultTest`; order `test-first` (failing run
`bab73dca`, build log `216f71ae604248ecac29509a7e393993`, compile failure `OutcomeReason` missing; green at
`db12c287`). `PrimitiveContractTest` unchanged and green (`order: none`). Delivered with `PrimitiveResult.reason`, the
three-argument constructor and the pairing invariant; a hand-written builder (Lombok's `@Builder.Default` does not
apply to record components) defaults both optional components.

**D2 — the registry-time checks: met.** `PrimitiveRegistryTest` + `ReasonPrimitives`; order `test-first` (failing run
`e6ee5193`, build log `dd663ba84a8e4162b5ace4509e62a8ac`, compile failure on the five new `Reason` constants; green at
`4d646d19`). `fixtureCoversEveryReason` unions both fixture sets; the completeness expectation is
`EnumSet.allOf(Reason.class)` at run time; the 30 existing refusals keep their reason. A raw non-`Class` reason payload
is refused as `MALFORMED_REASON_TYPE` rather than escaping as a `ClassCastException` (in both the registry and the result
constructor).

**D3 — the specification: met.** `05-primitives.adoc`: the SPI block carries `OutcomeReason`, `reasonType()` and the
fourth component; the `SKIP` bullet is `skipped` plus a reason; the `[#outcomes-and-reasons]` subsection states the
rule, the admitting classes, the closed condition table, that a reason is never an edge, and the prose example marked as
`PLAN-V2-20`'s; existing anchors stay and the concrete example is byte-stable. Order `none`. The test-tree search for
`05-primitives` in `*.java` finds only a main-source Javadoc reference. R17 links check: `BROKEN LINKS 2`, both the
sibling's pre-existing `model-work` links.

**D4 — the developer guide and eight ADRs: met.** `core-spi.adoc` has both contract rows, the pairing invariant, the
five refusal rows in enum order, `[#outcomes-and-reasons] Outcomes and Reasons`, one added step, the two Pitfalls items,
and the builder/testing updates; ADRs `005`–`012` carry all seven template sections at the next free numbers. Every
registry claim names its test. Order `none`.

**D5 — the documentation hand-off: met.** `plan-marshall/plan-marshall-documentation#138` carries exactly three hunks;
it is listed in PR #271's body. Merging it is the operator's.

**Overall Done-when:** met. `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and
`verify -Pintegration-tests -pl pm-core -am` are green on the merged head `9477cc85`; CI is green; SonarCloud reports 0
open new-code issues and a green quality gate. `pm-workflow` is unchanged and green in the full reactor.

## Personas and skills

Production code: `persona-implementer`; unit tests: `persona-module-tester`; documentation: `persona-documenter`;
self-review and PR triage: `persona-code-reviewer`. The spec declares no `Skills:`/`Persona:` lines (`skills: none
named`). R20 skills read as standards: `pm-dev-java:java-core`, `java-lombok`, `java-null-safety`, `javadoc`,
`junit-core`, `pm-dev-java-cui:cui-testing`, `cui-logging`. No surface drift.

## Hypotheses

- The documentation-repository PR is possible — **confirmed** (`#138`).
- `Optional<Class<? extends OutcomeReason>>` reads well — **confirmed**.
- Verify-first clause — confirmed at HEAD (no overlapping open PR; 59/23 site counts match).

## Findings

- Self-review round 1: missing Traceability lines, a non-executable `ReasonPrimitives` fixture, missing assertion
  messages, `@UtilityClass`, an unexercised interface default, an unused fixture, ADR wording defects, an incomplete
  guide defaults list, an over-claim. Fixed in `ced91c4f`.
- Self-review round 2: a dead guide anchor, an inaccurate enum count, two process items. Fixed in `f9f98be5`; ADR dates
  and the 574-line fixture are reported as deviations.
- SonarCloud: 10 new issues on the first head (null-tolerance checks, S3776, two S5778 test lambdas) — fixed; later
  S1905 (deliberate wildcard cast) and S2637 (builder default) — fixed with stated suppressions; final count 0.
- CodeRabbit reviews: `7e06dce0` (prose example fixed, reason-payload CCE fixed, Optional component answered and
  withdrawn, 3-arg constructor test added); `feb6346a` (builder defaults fixed, refusal-table drift answered,
  `precondition_unmet` fixture added); `af30c70c` (non-`Class` payload in the result constructor fixed, two test
  nitpicks fixed). All threads resolved.

## Reviewers

- `coderabbit` (required, quota-limited): refused repeatedly on quota; after the fresh-PR step it reviewed `7e06dce0`
  (marker `kind=reviewed`), then `feb6346a` and `af30c70c` (markers on each head). Every finding handled, every thread
  resolved. Final re-trigger on `9477cc85` refused at 2026-11-01T18:17:47Z and recorded; the standing authorization was
  used for the merge because a qualifying review and every handled finding existed.
- `sourcery` (optional, quota-limited): refused on a hard quota; optional, does not gate the merge.

## Operator questions

1. Asked: "The fresh PR #271 is open, CI and Sonar are green (0 issues), but CodeRabbit still refuses … How should I
   proceed?" — options "Keep waiting and retrying" and "Stop here; leave #271 open"; selected **"Keep waiting and
   retrying"**.
2. The operator's earlier free-text "retry code-rabbit" was carried out as a reviewer retry; it was not treated as
   permission to merge. Free-text "close existing pr, create a new one" was carried out as the quota policy's fresh-PR
   step.

## Rule check

- `guard: none on this harness (unenforced)`.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker. Standing rules r4; binding rule ids R01–R12, R17–R20.
- `executor-skew`: `match` (executor `0.1.2041`, lane `0.1.2041`).
- Deviations: (1) `PrimitiveResult.reason` is an `Optional` record component, mandated by spec Decision 4 and consistent
  with `spawnedJobId`, against R20's general rule; (2) the four-component builder is hand-written because
  `@Builder.Default` does not apply to records; (3) `ReasonPrimitives` is 574 lines, over the 400-line test budget R20
  says to report; (4) ADRs carry the plan date `2026-10-31` while the template asks for the merge date.
- The documentation PR used `GH_REPO=plan-marshall/plan-marshall-documentation` with the `ci` verb (no repository
  selector exists); the R17 links check used the prescribed `trace.py`; the Sonar reads used the public REST API, since
  `resolve` reports `not_configured` and the public read needs no token (R18). A doc-table drift test was attempted and
  removed as brittle; the follow-up was answered as a PR comment.

## Build server

Setup preflight: `ready`. Every build was submitted to the daemon (`daemon_longpoll`); `in_process_fallback: 0`. One
fallback entry: `verify -Pcoverage` at `c6eef100` hit the daemon's bound (`timeout_used_seconds 640`, explicit) and was
retried with `--timeout 1500`, passing in 848 s; later coverage runs used the raised bound. No launch-fault retry, no
degraded fallback, no `killed` job. Builds on the merged head `9477cc85`: pre-commit 557 s, verify 354 s, coverage
770 s, integration 78 s.

## Residue

- PR #271 is **merged**; merge commit `44cfe8d71f562576e21f7a7f6d71470a8180c6c0`.
- The worktree was removed, the local and remote branch refs pruned, and the main checkout switched and pulled to the
  merge commit.
- `plan-marshall/plan-marshall-documentation#138` is open; merging it is the operator's.
