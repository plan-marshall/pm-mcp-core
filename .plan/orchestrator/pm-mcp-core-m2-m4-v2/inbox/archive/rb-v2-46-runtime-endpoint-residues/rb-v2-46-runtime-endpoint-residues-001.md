envelope_version=1
sender_type=plan
sender_id=rb-v2-46-runtime-endpoint-residues
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-19T07:33:49Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-46-runtime-endpoint-residues
epic=pm-mcp-core-m2-m4-v2
pr=#494
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=10
review_body_findings_handled=10
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-46 landing — the endpoint residues

Staged spec `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-46-runtime-endpoint-residues.md`, read from the
shared ledger worktree (`spec_source: ledger_worktree`, no `spec_differ`). Standing rules read in full at revision **r7**
(the spec named r4; the file's own precedent says a starting plan picks the current revision). Rules that bound the run:
R01–R14, R16–R20 (R15 dropped: no `pm-workflow` change). PR #494, merge commit `bb8456d88f279bc4ad20dc81844a5572e4de05ce`.

## Deliverables

The binding rescope of 2026-11-14 dropped **D1** (the shared `limits` bisection) and **D4a/D4b/D4c** (the three #222
nits) because PLAN-V2-129 deleted the judgment endpoint; the run implemented D2, D3 and D5. The Done-when clauses of the
dropped deliverables are moot, not met; the table below grades the surviving ones.

| Done-when | Verdict | Order | Test | Failing run | Green at |
|---|---|---|---|---|---|
| `AnswerSchema.check` under the complexity bound; `AnswerSchemaTest` unchanged and green; one case per untested refusal | met | `none` | `AnswerSchemaTest` (17 cases: 11 pre-existing untouched, 5 parameterized refusals, 1 guard-order pin) | — | `c30c2327` |
| no `TODO: Catch specific` remains in `pm-runtime`; each replaced catch has a refusing fixture | met | `mutation-after` | `AnswerSchemaTest.unresolvedReference`, `EndpointHarnessTest.malformedListing`, `EndpointVerificationTest.malformedModelsListing`/`malformedAnswer`, `WeightsReaderTest.unresolvedPointer` | `b7b4119f`, daemon log `4cbf9b9ef89f4c5fbf0b30f32b87f3c7.log` — the six catches mutated to `IllegalStateException` failed 7 fixtures across the 5 reachable sites | `b7b4119f` |
| missing, `null` or non-object top-level `harnesses` is `harness_store_unreadable`; empty object stays valid | met | `test-first` | `HarnessEnrolmentStoreTest.topLevelHarnessesRefused`, `.emptyHarnessesObjectReadsAsEmptyStore` | `b7b4119f`, daemon log `2deb4816d102453aa03f5ff55ad6509a.log` ("Expected StoreException … but nothing was thrown") | `51122218` |
| developer documentation and one ADR per surviving design decision | met | `none` | `pm-runtime/doc/developer/runtime-endpoint.adoc`, `runtime-launch.adoc`, ADR `runtime-launch/011`, `PM-SEC-5` trace line | — | `88993a41`, `a7aa7ad7` |
| the pull request body lists the D1 duplication finding | met | `none` | PR #494 body § "Finding not in this plan": the duplicated `listing`/`modelIds`/`allowedModels` helpers | — | — |
| Sonar: no open issue of the pull request, quality gate green | met | `none` | `sonar read_open_issues --pr 494` — 0 issues, 0 hotspots; gate OK, new-code coverage 98.2 %, duplication 0 % | — | `9576a71b` |
| Build: `verify -Ppre-commit`, `verify`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am` | met | `none` | 11291 tests in the three full runs; 43 ITs | — | `9576a71b` |
| Links check (R17) | met | `none` | `trace.py` over the documentation checkout fast-forwarded to `origin/main` | — | `BROKEN LINKS 26`, none in this plan's files; `INLINE SCHEMA BLOCKS 0` |

Per-head test runs (`module-tests` + `test-compile`): `44f16e3a`, `c5430fdd`, `772bdc25`, `242ac025`, `78572c13`,
`a7aa7ad7`, `9576a71b` — green at each.

The `AnswerSchemaTest` refusals are not merely reached: the five cases the existing suite never exercised (depth bound,
non-object schema, a `type` of another shape, a non-local `$ref`, `minItems`) are parameterized and assert the exact
detail, and `typeRefusalPrecedesConstRefusal` pins the guard order so a reordered check fails the test.

## Personas and skills

Production code: `persona-implementer`; tests: `persona-module-tester`; documentation: `persona-documenter`; every
self-review round and the triage of the review comments: `persona-code-reviewer`. The spec declares no `Skills:` line
(`skills: none named`); the R20 coding skills were loaded as standards (`java-core`, `java-lombok`, `java-null-safety`,
`javadoc`, `junit-core`, `junit-integration`, `cui-logging`, `cui-testing`). No skill was added for surface drift: the
only undeclared files are the pre-commit profile's rewrites of main's own code, reported below.

## Hypotheses

- OBSERVED `java:S3776` at `AnswerSchema.java:60` and six `java:S1135` — **confirmed** at the head: the Sonar query for
  `java:S3776,java:S1135` on `main` returned exactly those seven issues in `runtime/endpoint/`.
- HYPOTHESIS "`JsonDocument.parse` and `JsonPointer.resolve` throw only `ProfileRefusal`" — **confirmed** by reading
  both: every refusal is `ProfileRefusal`; Jackson's checked exceptions and `NumberFormatException` are caught inside
  them. `catch (ProfileRefusal _)` is therefore the complete set at every site, and no behaviour can change.
- Claim 5 (`decode` reads a missing/non-object `harnesses` as an empty map) — **confirmed**, and fixed by D5.
- Claims 0, 3 and 6 (the duplicated bisection, the nits, the PLAN-V2-39 merge-order premise) — **contradicted** at the
  head, as the rescope already recorded; the deliverables were dropped accordingly.

## Findings

Self-review (three rounds, `persona-code-reviewer`, adversarial-refute lenses): 10 findings, all fixed or explained.
The substantive ones: the sixth `catch (RuntimeException _)` in `measureSchema` was unreachable and had no fixture, so
it was deleted rather than narrowed; the endpoint guide's claim that `ProfileRefusal` was "the only exception a read of
this package catches" was false against `EndpointOutcome`'s `ProfileRefusal | IllegalArgumentException`; the "every
refusal message is pinned" claim was narrower than the test; the launch-failure display name contradicted its assertion;
the malformed-body fixtures drew random words that could parse as `true`/`null`; the ADR's truncated-file refusal code
was wrong (`harness_tampered`, not `harness_store_unreadable`). Findings the review deferred and the run declined with a
reason: the `KEYWORDS`-registration refactor of `AnswerSchema` (predates this change; the reviewer marks it a follow-up).

CodeRabbit reviewed head `88993a41` (merge risk *low*): 7 inline findings + 3 nitpicks, all 10 handled — 9 fixed in
`772bdc25` (each thread answered and resolved) and 1 declined with the stated reason above. Sourcery (optional) was
refused on its own 7-day budget throughout.

**Owed finding for the epic (not fixed here):** Store Schemas § Unreadable Store requires a store refusal to count as a
start failure of the role on that harness; the launch refusal path records the fate without calling
`StartFailureGuard.startFailed`, and no production code calls it, so neither the `harness_store_unreadable` nor the
`harness_tampered` refusal reaches the guard's hold threshold. The ADR states the gap; closing it is a behaviour change
outside this plan.

## Reviewers

| Reviewer | Kind | Outcome | Head | History |
|---|---|---|---|---|
| coderabbit | required, `quota_limited`, `review_complete_after_handling` | **reviewed** | `88993a41` covered; the review is the PR's review under the done exemption once its findings were handled | trigger 09:27Z refused on quota; 90-minute waits and re-triggers at 10:59Z (refused), 11:00Z escalated (refused), 12:32Z (refused); operator chose "keep waiting 90-minute cycles"; 14:10Z trigger accepted, review 14:20Z |
| sourcery | optional, `quota_limited` | refused on its own budget (next slot ~11 h) | — | triggered at 09:27Z and 16:15Z; optional, does not gate |

## Operator questions

1. Asked: "PLAN-V2-46 is green on head 88993a41 except for the required CodeRabbit review … How do you want to
   proceed?" — options: keep waiting 90-minute cycles / close and reopen the PR / stop the run and land it open →
   **selected "Keep waiting 90-minute cycles"**. The policy's step 2 (close and reopen) was presented and not chosen,
   because the refusal is account-level and a new PR cannot lift it. No free-text instruction was given.

## Rule check

`guard: none` on this harness (unenforced), so there is no guard log to read and no "all kept" claim is made.

- Shell: every script through `python3 .plan/execute-script.py`, every build through the resolved `build` commands,
  every CI action through `ci`; one command per call; files read and searched with the file tools; temp files only under
  `.plan/temp/`. No `sleep`; the waits used were `ci pr wait-for-comments` and the queue settle.
- `.plan/`: writes only under `.plan/temp/` plus the `ci` body slots the `prepare-*` verbs returned. The spec and the
  orchestrator ledger were not edited.
- Git: no stash, no `restore`/`checkout --`, no forced push, no rebase; main moved four times and was merged each time
  with the trailer. Every commit ends with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; the PR body carries no
  attribution footer.
- `executor-skew`: `skew` at the start (executor 0.1.2149 vs lane 0.1.2152) — the documented remedy (regenerate) was
  applied through the registered notation `plan-marshall:tools-script-executor:generate_executor preflight`, which
  rewrote the executor to 0.1.2152; the re-check answered `match`. At landing the executor reads 0.1.2155.
- **R19 surface delta (observation):** three commits carry the pre-commit profile's rewrites of code that arrived from
  main and that this plan does not own: `44f16e3a` (`QualificationSchemaParityTest`, formatting),
  `242ac025`/`78572c13` (import order and a test's `throws` in `pm-core`/`pm-workflow`). They were produced by the gate
  the repository requires, reviewed as formatting-only, and are listed in the PR body as an observation.
- **Build-gate bound:** the routed `-Ppre-commit` run hit the daemon's 600 s bound once; it was re-run with a raised
  bound (`--timeout 1800`) rather than re-submitting the same bound.
- **Environment flakes (not this change):** `LspClientTest.timeout`/`.stubborn` failed twice under the loaded daemon (a
  2 s initialize timeout in the untouched `runtime.lsp` package), green in isolation (6/6) and in CI on every head; and
  the `quarkus-alignment` check failed once on a transient 404 of `quarkus-bom-3.40.1.pom` (`CANNOT DETERMINE` is
  blocking by design) and passed after `ci checks rerun`.
- Landing `resolve` was re-run at the end; `plugin_version` is `unknown` (the resolve does not measure it) and is
  written as such rather than guessed.

## Build server

Setup preflight on the worktree: **ready** (enrolled, version 1). Every build ran with the default routing and resolved
to `daemon_longpoll` — no `in_process_fallback`, no launch-fault retry. One routed `-Ppre-commit` run hit the daemon's
supervisory bound and returned `timeout` (`timeout_used_seconds=600`, `timeout_source=explicit`); it was re-run with a
raised bound (`--timeout 1800`) rather than re-submitting the same bound. No `killed` job occurred.

## Residue

- The `harness_store_unreadable`/`harness_tampered` start-failure counting described above is owed to the epic.
- The duplicated models-listing helpers (`listing`, `modelIds`, `allowedModels` in `EndpointVerification`, `listing` in
  `EndpointHarness`) stay duplicated; the spec puts them outside the plan and the PR body lists them.
- The `KEYWORDS`-registration refactor of `AnswerSchema` (advisory `minProperties` would pass unenforced) is a follow-up
  the reviewer named, not this plan's.
- ADR numbering: `origin/main` holds `001–008` and `010`, so 009 is free there, but open PR #418 adds `009-…` to the
  same directory; this plan took **011** to keep numbers unique (R11's intent), as the ADR source and PR body state.
- Mailbox: `mailbox_state=no_mailbox`, `delivery_state=never_delivered` on all three reads — the orchestrator delivered
  nothing to this plan, and the landing states that rather than claiming an empty mailbox.
