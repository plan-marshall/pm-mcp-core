envelope_version=1
sender_type=plan
sender_id=rb-106-plan-intent-and-outline-records
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-28T10:45:52Z

```landing-facts
schema=landing-facts/1
plan_id=rb-106-plan-intent-and-outline-records
epic=pm-mcp-core-m2-m4
pr=#210
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2025
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# PLAN-106 — the intent and outline records, the simplify ladder and the constraint checks

Merged as #210, merge commit `70bee082cb68462a2618cde4b9e8c39507c181fa`. Standing rules read: r2 (the file's revision; the spec text says r1), rules R01–R15, R17, R18, R19. Spec read from `main_checkout` (`spec_source: main_checkout`; no `spec_differ`).

## 1. Deliverables

Every Done-when's evidence order is **none** (tests were written and run green; no failing run was captured, and no clause names a "fails at {sha}"), so no failing-run commit/output identifier is reported.

### D1 — the intent record — met
| Done-when clause | Verdict | Test | Order | Commit |
|---|---|---|---|---|
| round-trips through `submit:refine`, the light `submit:outline` and a re-planning | met (library acceptance for all three entry points; the assembly's endpoints are owed) | `IntentRecordTest.roundTripsThroughEveryEntryPoint`, `revisionMovesCurrentToHistory`, `refusesReusedRemovedIdentifier` | none | dfb4eda0, d4843bd8 |
| `request.md` § Clarified Request rendered from the record | met | `rendersClarifiedRequest`, `replacesExistingClarifiedRequest`, `appendsClarifiedRequest` | none | dfb4eda0 |
| an epic-started plan's required claims seeded from the row's `done_when` and the epic's non-goals | met | `seedsRequiredClaimsFromEpic` | none | dfb4eda0, 9a8304d8 |
| a revision moves the current record to `history[]` | met | `revisionMovesCurrentToHistory` | none | dfb4eda0 |
| the link/identifier forms and the controls of the retired watch items | met | `EarsFormTest` (9), decider tests, `capabilityLinkRoundTrips`, `checksCurrentBehaviour` | none | dfb4eda0, d4843bd8, 907a2e2d |

### D2 — the outline record and the planning parts — not fully met
Met: the outline record round-trips and `outline.md` is rendered from it (`OutlineRecordTest.roundTripsAndRenders`, order none, dfb4eda0); the capability spec and its index (`CapabilitySpecTest`, dfb4eda0/9a8304d8); the builder check's submission and facts (`BuilderCheckTest`, 13c04be0).
**Not met as written:** "the builder check, the review results and escalation, the capability specification and its measurement, the scope-held Q-Gate row, the opt-in mutation step `default:mutation` and the knowledge search are each exercised by the phase that owns them". Those phases are workflow units of the assembly (`plan-marshall-mcp`), which is not this repository's surface; only the library parts the phases read are implemented and tested here.

### D3 — the `review-simplify` restraint ladder — not fully met
Met: proposals with the clone candidates as evidence — a run of 49 tokens is no candidate and 50 is, at most 20 are named longest first, a candidate is never a finding; the review task carries no write tool and never the writer slot; the accepted subset is recorded `accepted` and every other proposal `declined` (`SimplifyStepTest`, order none, 3ef06011).
**Not met as written:** "applies the accepted subset in the plan worktree". The server records the disposition and returns the settle routing; the file edits are the writer task's (`finalize.simplify-apply`), which the assembly owns and this repository does not carry. The write-tool/writer-slot guarantee is declarative on `ReviewTask` (asserted by its test), not derived from the role registry.

### D4 — constraints and non-goals — not fully met
Met: a self-review that omits a `CN-<n>` or `NG-<n>` (or `DC-<n>`) is refused naming it; `respected`/`followed` carry evidence; `violated`/`crossed`/`departed` are findings; `not_verified` is `clean_unverified`; `OutlineValidator` refuses the downstream `unchanged` guard directly and through two others, accepts a both-kinds deliverable, exempts inspection/demonstration, and accepts the guard-first matched control (`SelfReviewSubmissionTest`, `OutlineCoverageTest`, order none, cf03eb0b).
**Not met as written:** "the builder check always asks the operator about a decision or criterion that permits what a constraint rules out". `BuilderCheck` *requires* such a question to be the operator's when it is submitted; producing and asking the question is the `refine.builder-check` task's work, which the assembly owns.

Per-head test runs: the merge head `a5125d9e` ran the full `verify -Ppre-commit` (8094 tests green); the push heads ran the module gates (`dfb4eda0` 3646, `13c04be0` 3673, `3ef06011` 5877, `cf03eb0b` 3695, `da6bc157` 8078, `d4843bd8` 8084, `9a8304d8` 5912, `ce9da594` 5914, `d8534db1` 5915, `7ba5962a` 5912).

## 2. Personas and skills

| Step | Persona |
|---|---|
| D1 production code | `persona-implementer` |
| D1 unit tests | `persona-module-tester` |
| D2 production code / tests | `persona-implementer`, `persona-module-tester` |
| D3 production code / tests | `persona-implementer`, `persona-module-tester` |
| D4 validation code / tests | `persona-implementer`, `persona-module-tester` |
| documentation | `persona-documenter` |
| self-review rounds and every PR comment triage | `persona-code-reviewer` |
| trust boundaries (evidence confinement, scope binding) | `persona-security-expert` |

Per deliverable, the spec declares no `Skills:`/`Persona:` lines — `skills: none named`. No domain skill was added: the Expected Surface's modules (`core.store`, `core.spi.validation`, `workflow.finalize`, `workflow.verify`) are the ones the work touched. Surface delta: `doc/plans/phase-sub-workflows.adoc` (the package file's retired-watch links) is an undeclared shared file; R19 treats it as expected, and it is reported here.

## 3. Hypotheses

- HYPOTHESIS "the record types land in `core.store` beside the other plan records, the schemas in `SchemaRegistry`" — **confirmed**: `IntentRecord`, `IntentStore`, `IntentCodec`, `OutlineRecord`, `OutlineStore`, `OutlineCodec` in `pm-core/src/main/java/de/planmarshall/core/store/`, and `intent-record`/`outline-record` in `SchemaRegistry.defaults()` (`pm-workflow/src/main/java/de/planmarshall/workflow/verify/SchemaRegistry.java:66`).
- OBSERVED "no `IntentRecord`/`OutlineRecord`/`PlanHistory`, and no `intent-record`/`outline-record` in `SchemaRegistry.defaults()`" — corroborated before the work (the types did not exist; the registry did not hold the names).
- OBSERVED "`OutlineValidator` exists with a test" — corroborated (`pm-core/src/main/java/de/planmarshall/core/spi/validation/OutlineValidator.java`).
- OBSERVED "`FinalizePipelineValidator`/`FinalizeStepDescriptor`/`composition-rules.json` exist" — corroborated; the simplify step's order (`5`) matches `FinalizeStepDescriptor.SIMPLIFY_ORDER` and the catalogue row.

## 4. Findings

**Self-review (2 code rounds; budget-exhausted at the 3-round limit).** Round 1 reported 17 findings; the 13 code/doc defects were fixed (`d4843bd8`): the builder-check minor-decision overflow (now a refusal), `THEN` before `IF` accepted (now refused), the deleted normative clarified-request sentence (restored), a dangling Javadoc type, `depends` uniqueness, `current_behaviour` presence/form, dead code, the outline status completeness, a capability-link round-trip test, retired-watch citations, wording. Round 2 verified every fix and reported three small regressions, all fixed in `907a2e2d`. **Still open** (reported, not code defects): the D1/D2/D3/D4 scope items in §1, and the declarative write-tool guarantee.

**CodeRabbit.** Full-round findings: 12 actionable + 2 nitpicks. Fixed: builder-check decision capacity, self-review `kindOf` outside the judged kinds, `MET` evidence, capability-index order, nested capability specs, epic required claims (spec rule), first-revision refusal, evidence confinement, scope-transaction writes, numeric conversion failures, per-added-range clone runs, text-block/newline lexing, invalid evidence paths, cross-scope transactions (both stores), noncanonical status keys, symlink escape. Every thread was answered and resolved (`9a8304d8`, `ce9da594`, `d8534db1`). Dismissed with reasons: the schema-registry parity nitpick (ADR 010 keeps the registry names-only) and the clone-index memory nitpick (the candidate cap bounds the output; the detector is called on the plan footprint and the trade-off is recorded in ADR workflow-finalize/004).

**Sourcery.** Optional reviewer; refused every trigger with "your pull request is larger than the review limit of 150,000 diff characters". Not required.

**Sonar.** R18 read: the PR carried 106 open issues after the first reviewable head, then 6, 3, 8 (as fixes landed), and **0 at the merge head `a5125d9e`, gate OK**. The lane's resolve reports `sonar: not_configured` (no local Sonar block), so the facts fence carries `n/a`; the R18 public read is the measurement above.

**CI.** Green on every reviewed head; the merge queue's merge-group run succeeded.

## 5. Reviewers

| Reviewer | Required | State | Head reviewed | Trigger/wait history |
|---|---|---|---|---|
| CodeRabbit | yes (quota-limited) | reviewed, then quota-limited | `7ba5962a` (formal review, `kind=reviewed`); earlier `afeb1179` | triggered on every push; after `ce9da594`/`d8534db1`/`a5125d9e` the trigger returned the rate-limit notice (NEUTRAL, "review limit reached"). Standing authorization used for `a5125d9e`: qualifying review on `7ba5962a`, every finding handled and every thread resolved, re-triggered once after the last push, all other gates green. |
| Sourcery | no (optional) | refused | — | refused on size (150k diff characters), three times. |

## 6. Operator questions

1. "The pre-run executor check is `could_not_check` … How should I proceed?" — options {Proceed on the surface evidence, Stop and regenerate the executor}; selected **"Proceed on the surface evidence"**.
2. "R05 and the Expected Surface ask to delete the implemented specification sections … How should I treat these sections?" — options {Keep schemas, add implemented note, Delete bodies keep anchors}; selected **"Keep schemas, add implemented note"**.

Free-text operator messages: none beyond the two selections.

## 7. Rule check

`guard: none on this harness (unenforced)`.

The run's own actions that did not keep a §1 rule:
- A sub-agent used shell `ls`/`find`/`grep` for file reading before re-reading the rules (three calls); every read after that used the file tools. It also ran `install -Ppre-commit -pl pm-core -DskipTests` through the executor to refresh `~/.m2` before a `-pl pm-workflow` gate — an executor build, but not one of the resolved gate canonicals.
- The first routed build was submitted with a relative `--project-dir` and failed before compilation; every gate after that ran explicitly in-process. No build was backgrounded; no killed job.
- The spec read's own facts: `spec_source: main_checkout`, no `spec_differ` marker.
- The sibling documentation link checker (R17) was run: 0 broken links in this repository; 15 broken anchors remain in `plan-marshall-documentation` (articles and requirements), which are out of this repository's scope.

## 8. Build server

Setup preflight: `ready`. The first routed submission failed on the relative worktree path; from then on every gate ran in-process (`--execution-mode in_process`), so no build degraded by the daemon and none was killed. The merge head's full gate: 8094 tests green (`maven-2026-10-28-100724.log`). No `runtime_stamp_reason` was carried.

## 9. Residue

- D2's phase-owned parts, D3's worktree application, D4's builder-check asking and D1's entry-point wiring belong to the assembly's workflow units, not this repository; they are the reason `deliverables_done` is 1 of 4.
- Two CodeRabbit nitpicks are accepted as trade-offs (schema-registry parity; clone-index memory), as described in §4.
- The retired watch items `PM-WATCH-PHASE-106/107/110/111` are removed from this repository; the sibling documentation's articles still link two of them, which is a `plan-marshall-documentation` follow-up.
