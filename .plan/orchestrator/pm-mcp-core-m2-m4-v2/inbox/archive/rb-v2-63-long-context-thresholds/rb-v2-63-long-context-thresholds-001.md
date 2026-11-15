envelope_version=1
sender_type=plan
sender_id=rb-v2-63-long-context-thresholds
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T15:25:01Z
revision=2
amended=2026-11-15T15:26:23Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-63-long-context-thresholds
epic=pm-mcp-core-m2-m4-v2
pr=#441
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2133
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Landing — PLAN-V2-63 `long-context-thresholds`

guard: none on this harness (unenforced)

Standing rules: r7 — applied R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R15, R17, R18, R19, R20 (R12 dropped: no log message; R14 and R16 dropped: no `core.store` and no `pm-runtime` change).

Merge commit: `15148a433d8b3be178b53d9d0ba11efd5ddb1c8c`. Branch and remote ref pruned; the worktree removed; the main checkout switched and pulled to the merge commit (1 commit).

## 1. Deliverables

Every Done-when of the spec is met; `deliverables_done` counts 4 of 4.

| Deliverable | Done-when / sub-clause | Order | Test(s) | Failing run | Commit |
|---|---|---|---|---|---|
| D1 — the member in `ProfileValidator` | valid entries load, an invalid entry is refused at the entry's pointer; the endpoint kind refuses it | `test-first` | `ProfileValidatorTest.refuses[2..5]` + `acceptedExtensions`, `EndpointProfileValidatorTest` (endpoint row) | base `666395f4` + the new tests: pointers expected at `/headless/long_context_thresholds/<model>` reported at `/headless/long_context_thresholds`, and four accepted documents refused "the member 'long_context_thresholds' is not declared by 'headless'" — `TEST-de.planmarshall.core.profile.ProfileValidatorTest.xml`, job `b2a25ad4` | `cb6dc28d` (green, 151 tests) |
| D2 — `LongContextThresholds` | `forModel` returns the entry of a model and nothing for any other; `HarnessProfile` keeps its components | `mutation-after` | `LongContextThresholdsTest` (5 cases) | `of(JsonNode)` → `EMPTY` at `f9bf8951`: `entryOfTheModel` failed 3 assertions, job `bd338e0d`; range guard → `Math.toIntExact` at `96289e87`: `unreadableValueIgnored` failed with an integer overflow at `LongContextThresholdsTest.java:104`, job `9901993e` | `9d7dbb90`, `f9bf8951`, `96289e87` |
| D3 — the cap on a profile document | the cap test passes against the real `EnvelopeBudget` | `mutation-after` | `EnvelopeBudgetProfileThresholdTest` (3 cases), `EnvelopeBudgetTest.ExactEquality`, `EnvelopePackerTest` refusals | `EnvelopeBudget.cappedFallback` mutated to drop the cap at `bb725125`: `profileFallbackCapped` failed (2 assertions) and `EnvelopeBudgetTest`'s two cap cases failed, job `9841f704` | `bb725125` |
| D4 — documentation, specification, package close | the package file and its row are gone; the guides and the ADR exist; the specification bullet points at them | `none` | links check artifact `.plan/temp/links-check-v2-63.txt` — `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0` | — | `2ab60f5f`, `dc104be8`, `c95b953d` |

Other sub-clauses: existing `ProfileValidatorTest`, `HarnessProfileTest` and `ShippedProfilesTest` cases stay green (the first is unchanged except two carried line rewraps and one added assertion message; the other two are untouched); `verify`, `verify -Ppre-commit` (its one formatter rewrite reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green at the merge head.

Per-head test runs (module-tests at every pushed head): `f9bf8951` 10 565 · `bb725125` 10 573 · `2ab60f5f` 10 573 · `dc104be8` 10 574 · `96289e87` 10 575 · `c95b953d` 10 575 · `612d2071` (merge head) 10 609, all green.

Evidence caveat, stated as it is: the two upper-bound refusal/acceptance cases in D1 were added during the review rounds and therefore carry no base-failing run of their own; the D1 order remains `test-first` for the cases that did fail first.

## 2. Personas and skills

* D1 — `persona-implementer` (`implementer` declared); skills `pm-dev-java:java-core`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`, `pm-dev-java:java-null-safety`.
* D2 — `persona-implementer`; skills `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java:java-null-safety`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
* D3 — `persona-implementer`; skills `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
* D4 — `persona-documenter`; skills `pm-documents:ref-asciidoc`, `pm-documents:ref-documentation`.
* Self-review and every PR finding — `persona-code-reviewer` (four rounds, the fourth on operator permission).
* skills added: none — no deliverable reached a module the Expected Surface did not name.

## 3. Hypotheses and observations

* "The member is refused today" — **confirmed**: `HEADLESS_OPTIONAL` at `ProfileValidator.java:208` at the branch base, the closed `headless` check at :546-552, and no Java code naming `long_context_thresholds`.
* "`HarnessProfile` has eleven components built in seven test classes and the production wiring" — **confirmed**: `HarnessProfile.java:49-52`, constructed at :158 and :179 and in the seven declared classes; the record was not touched.
* "`EnvelopeBudget` in the head of `main` differs from the signature the spec names (plain `OptionalInt` inputs)" — **confirmed**: `EnvelopeBudget.java:88-89` takes `@Nullable Long`; the D3 test adapts and the PR body says so.
* "The schema is already a file that declares the member, and no test reads the specification part" (the spec's R10 hypothesis) — **confirmed**: `harness-profile-v1.schema.json:416-421` declares it, `01-profile-schema.adoc:24` includes the file, and no test reads that part.
* The spec's HYPOTHESIS "no test reads the schema block" — **confirmed**, so only the bullet at :238 was edited and the schema file stayed untouched.

## 4. Findings

Self-review (4 rounds, the 4th on the operator's extension):

1. Round 1 — **major**: `model-work/06:19` kept the "Implemented by … Remaining:" form, which R05 r7 forbids → condensed to a requirement statement plus the guide and ADR links. Minors: the dead `isEndpoint` guard and its vacuous test → guard removed; "validated document" overclaim → corrected; "two types" miscount → three, with the "no production type" scoping; shared minimum constant → split into two. Nits: entry detail names the model id, pointer built from the constant, upper-bound pair added, `acceptedExtensions` heading added.
2. Round 2 — one minor (`workflow-envelope` Testing still said "validated") and the same word at three more sites → the read was made total on a parsed tree (positive and within the int range, otherwise ignored, so it never throws) and the four wordings aligned.
3. Round 3 — one nit (the Javadoc's "range of a threshold" ambiguity) → the class Javadoc now states the read's own range.
4. Round 4 — **clear**.

CI and review: CodeRabbit posted two inline findings, both disposed as answers with a stated reason and resolved (`coderabbit inline inline-1` and `inline-2`, see `## 5`). Sourcery refused on quota (optional bot). SonarCloud: 0 new issues, 0 hotspots, gate passed. No CI failure at either head.

## 5. Reviewers

| Reviewer | Required | Outcome | Head | History |
|---|---|---|---|---|
| `coderabbit` | yes | reviewed | `c95b953d`, carried forward to the merge head `612d2071` (`kind=unchanged_files_carry_forward`), with an earlier `kind=reviewed` naming `c95b953d` | triggered 14:38; reviewed 14:46; two findings answered and resolved 14:49–14:50; exempt from re-trigger (`review_complete_after_handling` + qualifying review on record) |
| `sourcery` | no (optional) | refused on quota | — | triggered 14:38 and again 15:06; both answered "used your own review budget of 250,000 diff characters for the last 7 days … another review in 19 hours". Optional, so no gate and no quota policy |

Standing review authorization: not invoked — `review-gate` answered `all_handled` with `merge_allowed: true` on the required reviewer's qualifying review, 0 unresolved threads and 2 of 2 body findings handled.

## 6. Operator questions

1. "The runbook lane is blocked at step 0: the executor (0.1.2123) is older than the lane text (0.1.2133) … How should I proceed?" — options {regenerate-then-run, stop-blocked}; answered in free text "is regenerated". No option was selected, so nothing was inferred: the executor was re-measured and reported `match` at `0.1.2133` before the run continued.
2. "… Should I implement the Carried 2026-11-13 refusal of a non-positive budget, a negative predicted cost and the named requireNonNulls?" — options {refuse-all-three, named-npes-only, leave-production}; selected **"Refuse all three"**.
3. "Spec Deliverable 4 says the specification bullet … becomes an 'Implemented by … Remaining: …' sentence. Standing rule R05 at revision r7 says no such paragraph may be written. Which governs?" — options {follow-R05-r7, spec-literal}; selected **"Follow R05 r7"**.
4. "The self-review has run 3 rounds (the lane's limit) … How should I proceed?" — options {continue-round-4, stop-budget-exhausted}; selected **"Continue with round 4"**.

Free-text operator messages, listed separately: "is regenerated" (question 1).

## 7. Rule check

* guard: none on this harness (unenforced) — so nothing was enforced, and this section names the run's own actions instead of claiming "all kept".
* Deviations I report against myself:
  * The lane text arrived truncated by the harness (it showed lines 1-723 of 1138 and saved the rest to `tool_1de95d732001re8PUFZ1VTChOA`); to read it in full I read that harness tool-output file at `/home/oliver/.local/share/opencode/tool-output/…` — outside the repository root. It contains the skill text only, no credential or configuration data, but it is a read outside the root and is disclosed here.
  * The links check (R17) and `git fetch` ran against `/home/oliver/git/plan-marshall-documentation`, outside the repository root, as R17 and the spec's Done-when prescribe. Its output is persisted at `.plan/temp/links-check-v2-63.txt` (written with a shell redirect into the sanctioned temp directory).
  * One mis-typed executor notation (`plan-marshall:tools-integration-sonar:sonar`) was rejected by the executor as `Unknown notation`; nothing ran.
  * The `/tmp` guidance was not needed; no temp file left the sanctioned `.plan/temp/`.
* Spec read: `spec_source: ledger_worktree`, with no `spec_differ` marker — so the copy read is the one the ledger holds and the main checkout's copy says nothing different.
* `executor-skew`: `skew` (0.1.2123 vs 0.1.2133) at start → the operator regenerated the executor → `match` (0.1.2133 = 0.1.2133), which is the verdict this run proceeded on.
* Distinct identities of the build outcomes: each build's `mechanism` was `daemon_longpoll` (the client's own `[BUILD-SERVER] resolved build` line); two wait bounds expired and each was re-attached or re-submitted at a raised bound (quality gate job `321e8e37`; `test` job `f5fc9859`), never as a fallback.

## 8. Build server

* Setup preflight: `ready` (the project is enrolled; `registered: true`).
* Every build ran routed (`daemon_longpoll`): 42 submissions in total — the four gate canonicals at the merge head included (pre-commit job `b2eb8aec`, 280 s, 10 609 tests; `test` `f4d97aa8`, 144 s; `verify` `5071f89d`, 136 s; coverage `1e5dda7e`, 128 s; integration `070268e4`, 98 s, 21 tests). The 42 include 2 compile-failure runs (a mistyped `OptionalLong.filter`), 3 deliberate mutation runs, 2 bound expiries and 1 flake re-run; 0 in-process, 0 degraded.
* Fallbacks/retries: none degraded; one launch-fault class did not occur; two bound expiries were re-attached/re-submitted at a raised bound (above). `killed`: none.
* Runtime stamp: `runtime_stamp_reason` was not carried; `plugin_version` is `unknown` (the resolve field answered `unknown` at setup and again at landing).

## 9. Residue

* **Owed to the content repository:** no shipped profile carries a `long_context_thresholds` entry yet (a shipped profile holds an entry only where the provider documents the price).
* **Owed to the profile-schema package:** `maximum: 2147483647` on `headless.context_budget_tokens` and on `headless.long_context_thresholds.additionalProperties`, so the published schema refuses what the validator refuses (CodeRabbit's second finding; the plan is bound to edit no schema file). Related observation from the reviewer: `HarnessProfileSchemaParityTest` compares required members and does not detect optional-member drift.
* **Surface delta (R19):** `pm-workflow/src/test/java/de/planmarshall/workflow/envelope/EnvelopePackerTest.java` is changed by the carried residue's refusals but is not named in the Expected Surface — recorded as the observation, not a scope expansion.
* **Spec-vs-rule tensions, disclosed:** the specification bullet was **pointed, not condensed** (it states a requirement), per the operator's R05 r7 decision; two spec-ordered bookkeeping edits sit against the literal wording of R06 (the module README gains ADR links) and R08 (PM-CLIENT-2's `Documentation:` line is retargeted). The spec's own D4 sentence for that bullet should be corrected at the next emit.
* **Left untouched:** the two pre-existing "Implemented by … Remaining:" paragraphs at `model-work/06:41,53` — another plan's prose, not this plan's diff.
* **Landing completeness:** `inbox landing-check` answers `complete: false` with `missing_keys: total_tokens, plugin_version` — both facts were unmeasurable this run (`total_tokens` has no accounting source in the lane, and `plugin_version` is the `runtime-info` seam's answered `unknown`, which the resolve result returned at setup and again at landing). PLAN-V2-81's landing records the identical state; it is the honest report, not a rejection.
* Cleanup: `cleanup_owed=false` — the worktree was removed (clean), the local and remote refs pruned, and the main checkout switched and pulled to the merge commit.
