envelope_version=1
sender_type=plan
sender_id=rb-v2-120-limits-in-data-files
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-08T21:17:44Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-120-limits-in-data-files
epic=pm-mcp-core-m2-m4-v2
pr=#321
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2084
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

Standing rules: r6 (2026-11-02); rules read and applied: R01, R02, R05, R06, R08, R10 (replaced), R14, R15, R16, R17, R18, R19, R20. Open items: none from the plan; the two low observations in Residue are optional follow-ups.

## Deliverables

| # | Done-when | Order | Verdict | Test / evidence | Commit |
|---|---|---|---|---|---|
| D1 | The stalled threshold lies in a pm-runtime properties resource between `# tag::stalled-threshold[]` and `# end::stalled-threshold[]`; the constant is read at class load and fails fast when the resource or the key is absent | mutation-after | met | `thresholdIsReadFromTheLimitsResource`, `readsAnAlternateResource`, `anAbsentLimitsResourceFailsTheLoad`, `anAbsentKeyFailsTheLoad`, `aNonNumericValueFailsTheLoad`; failing run commit `52d9e30e`, output identifier job `f8ea60837f6c4a9893b4a2c4138b8fdd` (`StalledScopeDetectorTest.java:664` and `:675`, 2 failures after the loader was mutated to default `0`); green at `656ef7fc` | `52d9e30e` |
| D2 | `MAX_UNIT_FILE_BYTES`, `MAX_FRONTMATTER_LINES` and `MAX_ECHO_LENGTH` lie in a pm-workflow properties resource, one tag per limit, read at class load, fail fast | mutation-after | met | `constantsAreReadFromTheLimitsResource`, `readsAnAlternateResource`, `anAbsentLimitsResourceFailsTheLoad`, `anAbsentKeyFailsTheLoad`, `aNonNumericValueFailsTheLoad`; failing run commit `6d74142c`, output identifier job `40ecdeb1ca9e49e99dd583848694a587` (`UnitFrontmatterReaderTest` "The bounds": 11 tests, 2 failures); green at `656ef7fc` | `6d74142c` |
| D3 | The specification includes the tagged lines where it states the value; the sentence names the key and no longer repeats the number | none | met | `doc/specification/timeouts/02-runtime-leases-operator-web.adoc:51-53` and `doc/specification/workflow-dsl/01-grammar.adoc:260-266`; the include paths resolve; `trace.py` reports `BROKEN LINKS 0` | `9ab74a2a`, corrected in `7b3159f6`, `79c0250d` |
| D4 | The two tests that match a number in the prose are replaced by a test of the load and of the behaviour at the limit | mutation-after | met | the old `thresholdIsTheSpecifiedValue` and `constantsAreTheSpecificationValues` are gone; the load tests above; the boundary tests `shouldHoldFromTheThresholdOn` and the reader's cap/echo tests stay green; neither test names `pm.specification.root` | `52d9e30e`, `6d74142c` |

Done-when bullets: the load tests with their negative controls — met; both tests no longer name `pm.specification.root` — met; `verify`, `verify -Ppre-commit` and `verify -Pcoverage` green (and R01's `verify -Pintegration-tests`) — met at `656ef7fc`; no `Pattern.compile` over a specification document in the test sources — met on the merged head (PLAN-V2-117, brought in by the merge, replaced the credential test's specification read with a schema resource).

Per-head test runs: `test -pl pm-runtime -am` at `79c0250d` (6567 tests), `test -pl pm-workflow -am` at `79c0250d` (6683), `verify -Ppre-commit` at `79c0250d`, `7b3159f6` tree, `79c0250d`, `6e87c8ba` tree, `656ef7fc` (9488 tests each), `verify` at `79c0250d`, `57f37ed2` (9488), `verify -Pcoverage` at `79c0250d`, `6e87c8ba` tree (9488), `57f37ed2` (9488), `verify -Pintegration-tests` at `79c0250d` (2903), `aa1e705e` (2904).

## Personas and skills

- Production code (D1, D2): `persona-implementer`.
- Tests (D4): `persona-module-tester`.
- Documentation and specification (D3, R06): `persona-documenter`.
- Every self-review round and every PR comment: `persona-code-reviewer`.
- The spec declares no `Skills:` or `Persona:` lines: `skills: none named`.
- R20 skills read before the outline: `pm-dev-java:java-core`, `java-null-safety`, `java-lombok`, `javadoc`, `junit-core`, `junit-integration`, `pm-dev-java-cui:cui-logging`, `cui-testing`, `cui-http-testing`, and the project skill `traced-implementation`.
- Skills added for surface drift: `pm-dev-java:java-core` and `java-lombok` applied to the pm-core `LimitProperties` (the shared reader added for the Sonar duplication gate); reason: the class moved to pm-core.

## Hypotheses

- `StalledScopeDetector.THRESHOLD` in `pm-runtime/src/main/java/de/planmarshall/runtime/start/StalledScopeDetector.java` — confirmed (line 46 at the base).
- The three limits in `UnitFrontmatterReader` of `pm-workflow` — partially refuted: `MAX_FRONTMATTER_LINES` and `MAX_ECHO_LENGTH` are in `UnitFrontmatterReader.java:77,83`; `MAX_UNIT_FILE_BYTES` is in `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProjectUnitLoader.java:112`. Recorded as a surface delta.

## Findings

- Self-review round 1: docs made false by the move (fixed), `{@value}` on non-constants (fixed), "compiled constant" wording (fixed), number copies in the guides (fixed), loader Javadoc (fixed), type tables (fixed); the Expected-Surface gap for `ProjectUnitLoader` accepted and recorded; the duplicated loader accepted at that round and later removed for Sonar. One High item: the plan Done-when on `Pattern.compile` was unmet by a pre-existing test outside the surface — later resolved by the merge of `origin/main`.
- Self-review round 2: ADR wording, two general "compiled constant" specification passages, loader Javadoc and style nits — fixed; historical ADR values, Traceability copies and the `+1` boundary accepted or fixed later.
- Self-review round 3: no new Medium/High; state `budget-exhausted` because the spec-defect Done-when stayed open at the round limit. The operator chose to proceed; the merge resolved it.
- CodeRabbit: the include-rendering finding was answered (the plan mandates the include and forbids a number copy; ADR 016 records the GitHub rendering); the file-size overflow and its `Integer.MAX_VALUE` boundary were fixed in `48caad57` and `93eef82c`; the alternate-resource nitpick was fixed with a test resource per module and a pm-core test; the README-row finding was answered with R01.
- CI: the first Sonar run failed on new-code duplication (27.1% against 3%) — the two `LimitProperties` copies were merged into `pm-core` in `6e87c8ba`; the second failed on new-code coverage (27.7% against 80%) — a pm-core test and resource were added in `aa1e705e`. Both are green on the merged head.
- Sourcery: quota refusal at 10:23 (optional, non-gating); approved the PR at `57f37ed2` at 18:58.

## Reviewers

- `coderabbit` (required, quota_limited): reviewed `79c0250d` and `aa1e705e` (qualifying, `final_review_risk_coverage` kind `reviewed`); re-triggered after every push; rate-limited on `57f37ed2` and `656ef7fc` (SKIPPED/NEUTRAL). Standing authorization: qualifying review on `aa1e705e`; every finding fixed or answered and every thread resolved; re-triggered once after the last push with the rate-limit refusal recorded; every other gate condition holds on `656ef7fc`.
- `sourcery` (optional): refused on quota at 10:23; approved at `57f37ed2` (18:58). Non-gating.

## Operator questions

1. Asked "Self-review reached its 3-round limit with one Done-when open (Pattern.compile over a specification document; pre-existing CredentialStoreMoveRecordParityTest, PLAN-V2-117). How to proceed?" — options: open the PR and report not met as written / expand the surface and fix now / stop before the PR — selected "Open the PR and report it as not met as written".
2. Asked "The PR is merged as 7d1720c8, but the worktree is dirty: the quality gate rewrote the import order of pm-core/src/test/java/de/planmarshall/core/SchemaResource.java (a file main changed, not part of this plan). How should I proceed?" — options: revert the gate's rewrite then remove the worktree / commit the rewrite to the merged branch then remove / leave the worktree and report the residue — selected "Revert the gate's rewrite, then remove the worktree".

No free-text operator messages were received.

## Rule check

- Own rule break: one shell pipe (`git ls-files | grep …`) at exploration, which § 1 Shell forbids; no further pipe was issued.
- Guard: none on this harness (unenforced); no guard refusal exists to report.
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker was reported.
- `executor-skew`: `match` at session start (executor 0.1.2076, lane 0.1.2076); the landing-time resolve reports executor 0.1.2084.
- Every script ran through `.plan/execute-script.py`; every build through the resolved `build` commands; every CI action through `ci`. The R17 links check ran the documentation repository's `trace.py` directly, as R17 prescribes.
- Surface deltas recorded: `pm-core/src/main/java/de/planmarshall/core/config/LimitProperties.java` (shared reader), `pm-core/src/test/java/de/planmarshall/core/config/LimitPropertiesTest.java` and its resource, `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitLimits.java`, the two module test resources, `doc/specification/timeouts/03-retention-harness-config.adoc`, `doc/specification/domain-extensions/06-configuration.adoc`, `doc/specification/runtime-lifecycle/02-plan-pause-and-stalled-plans.adoc`, `doc/implementation-watch/runtime-lifecycle.adoc`, `pm-runtime/src/main/java/de/planmarshall/runtime/start/package-info.java`, the two module ADRs and `doc/Traceability.adoc`. `ProjectUnitLoader.java` was outside the declared surface; the spec's HYPOTHESIS had placed all three limits in `UnitFrontmatterReader`.

## Build server

- Setup preflight: `ready` (registered).
- Mechanism: every build was `daemon_longpoll`; no `in_process_fallback`, no launch-fault retry, no `killed` job.
- One bound expiry: `verify -Pcoverage` at the 1391 s bound (`timeout_used_seconds: 1391`, `timeout_source: explicit`) on the `6e87c8ba` tree; re-run with the bound raised to 2400 s, green.
- Two intended failing runs are the mutation-after evidence (`f8ea60837f6c4a9893b4a2c4138b8fdd`, `40ecdeb1ca9e49e99dd583848694a587`), not build-gate failures.
- No `runtime_stamp_reason` was reported.

## Residue

- None open. Optional low observations: `doc/Traceability.adoc:1925,2019` still state the three numbers in prose; the includes render as links on GitHub; a malformed properties escape surfaces without the resource name; the `MAX_UNIT_FILE_BYTES + 1` boundary is covered but a `0` limit would be accepted.
