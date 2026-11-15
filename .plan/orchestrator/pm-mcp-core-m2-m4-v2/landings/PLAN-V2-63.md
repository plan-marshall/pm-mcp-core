# Landing Analysis: PLAN-V2-63 — `headless.long_context_thresholds`, the reading of the member and the cap of the envelope budget

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #441 (merge `15148a43`), `merged` through the merge queue, no label; 22 files, +656 / −73

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-63-long-context-thresholds-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2133, standing rules r7; message revision 2). The message is a lead. The three layers were read by one read-only leaf from git at `15148a43` (no build, no test run); the orchestrator re-read the points marked "re-read".

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 the member in `ProfileValidator` | met, one named step missed | `HEADLESS_OPTIONAL` holds `NAME_LONG_CONTEXT_THRESHOLDS` (`ProfileValidator.java:218-219`); `longContextThresholds(...)` beside the `context_budget_tokens` check (`:617-625`, method `:873-890`): an object (empty valid), key length ≤ 128 (`LONG_CONTEXT_MODEL_ID_MAX_LENGTH` `:48`), value an integer from 10 000 (`:53`) to `Integer.MAX_VALUE`; the pointer escapes the model id (`escape()` `:880`) as `modelEfforts` does. No check that the model id is a model of the profile (the spec is silent; the schema does not require it). Tests: refusals `ProfileValidatorTest.java:124-141` (9 999, "many", −1, a 129-character name, 2^31, a non-object, each with a valid sibling), accepted cases `:435-465`, endpoint refusal `EndpointProfileValidatorTest.java:85-86`. **Not done: the conversion of `ProfileValidator` to `@UtilityClass`** the spec's State section made this plan's last step (re-read at `origin/main`: `public final class ProfileValidator` `:39`, empty `private ProfileValidator()` `:272`, no `UtilityClass`); the landing message does not mention it; PR #437 (PLAN-V2-94) holds the class out for this plan and touches neither it nor its test (re-read: 0 files), so nobody converts it now. |
| D2 `LongContextThresholds` | met, a deviation | `of(JsonNode)` returns an empty value for an absent or null `headless` or member; `of(LoadedProfile)` delegates; `forModel` is a byte-exact `Map.get`; `Map.copyOf` makes it immutable; a non-positive-int value is skipped silently. **The spec's binding `forModel` returns `OptionalLong`; it returns `OptionalInt`** (re-read `LongContextThresholds.java:95-97`; the record holds `Map<String,Integer>`, the D3 test casts `(long) threshold.getAsInt()` `EnvelopeBudgetProfileThresholdTest.java:110-112`, the ADR and guides document `OptionalInt`); harmless because the validator caps at `Integer.MAX_VALUE`; the landing message does not name the deviation. `HarnessProfile` unchanged. No production caller of `LongContextThresholds` or `EnvelopeBudget.resolve` exists in any `src/main` (as the spec expects; the wiring into `plan.describe-tasks` stays owed, stated in ADR 016 and Traceability). |
| D3 the cap | met | `EnvelopeBudget.cappedFallback` takes `Math.min(tokens, threshold)`; tests: threshold below the fallback (`EnvelopeBudgetProfileThresholdTest.java:68-80`), role value above the threshold kept and flagged (`:82-91`), absent threshold unchanged (`:93-104`), equality (`EnvelopeBudgetTest.ExactEquality` `:157-184`); removing the `min` fails `profileFallbackCapped` by reasoning (the lane's own mutation run unverified). The test parses the document with `JsonDocument.parse` and does not run the validator (the spec said so; the docs were reworded in round 2). |
| D4 documentation | mostly met | `core-profile.adoc:117-132`, `core-role.adoc:112-117`, ADR `core-profile/016` (new, no number collision), `workflow-envelope/001:23` and `003:14` corrected, `envelope-packing` package file deleted with its README row, Traceability PM-WF-14, PM-CLIENT-5 and the PM-CLIENT-2 `Documentation:` line. The bullet at `01-profile-schema.adoc:238` is pointed (a link sentence appended), not condensed, on the operator's answer (landing question 3, R05 r7); anchor kept. Stale: `Traceability.adoc:494,521` still name the deleted `envelope-packing` package. |
| Carried residue of PLAN-V2-71 | met | ADR `core-profile/015` "host" → "harness" (no "host" left), README ADR list 013-016 (`pm-core/README.adoc:94-113`), the `marginRefusalWording` message, the two 121-character lines rewrapped, the Traceability `Documentation:` line retargeted (anchor `core-profile.adoc#_reading_and_validation` not rendered). |
| Carried residue of PLAN-V2-40 | met | ADR `workflow-envelope/001:23`, merge-cascade semantics in the guide, `packEnvelopes` refuses a budget ≤ 0 (`EnvelopePacker.java:140-142`), `PlannedTaskSummary` refuses a negative cost (`:77-79`); `EnvelopePackerTest` is outside the Expected Surface and was declared. |

Done-when: D1 (without the conversion), D2 (with the type deviation), D3, D4 as in the table. CI: all checks success or skipped; Sonar `pullRequest=441&resolved=false` total 0; `Maven Build` (`43729476745`) and `Integration Tests` (`43729476622`) of `15148a43` on `main` success. The schema file `harness-profile-v1.schema.json` was not touched; the validator agrees with it on type, minimum 10 000 and `propertyNames.maxLength` 128 (`:416-421`).

## Three-Layer Analysis

### Layer 1 — Specified
Coherent. The binding State section wins over the older D2/D3 text on `OptionalLong` and `@Nullable Long`; the lane chose `OptionalInt`. The spec's D1 "maximum as for `context_budget_tokens`" and the schema's missing maximum conflict: the lane followed the validator and recorded the schema gap. Spec gap: whether an entry must name a model of the profile.

### Layer 2 — Implemented
As the table.

### Layer 3 — Correct
No functional bug found in the validator, the reader or the cap. By weight:
1. `ProfileValidator` keeps its empty private constructor: the `@UtilityClass` rule of `CLAUDE.md` ("a class that holds only static methods is a Lombok `@UtilityClass`") stays unmet for it (process miss, no runtime bug).
2. The escape of a model id is untested: no test uses `/` or `~` in a model id; removing `escape()` (`ProfileValidator.java:880`) fails nothing; failing input `{"a/b": 9999}`, expected pointer `/headless/long_context_thresholds/a~1b`.
3. Two bare `assertEquals` without a message in `EnvelopePackerTest.namesTheMissingCollections` (`skillUris`, `dependsOnTaskIds`), against the 20-60 rule.
4. Five new Javadoc lines over 120 characters: three in `LongContextThresholds`, one in `LongContextThresholdsTest`, one in `EnvelopeBudgetProfileThresholdTest`.
5. Validator and schema disagree on the upper bound (the validator refuses above 2 147 483 647; the schema declares no `maximum` for `context_budget_tokens` or the thresholds); `HarnessProfileSchemaParityTest` compares required members only and does not see optional-member drift. The 128 bound counts UTF-16 units, JSON Schema `maxLength` counts code points (low).
6. `LongContextThresholds.of` reads `"long_context_thresholds": null` as empty although the validator refuses it; `forModel(null)` throws an undocumented NPE.
7. ADR 016 says seven `HarnessProfile`-constructing test classes; the tree has eight (`HarnessProfileBuilderContractTest`).
8. `EnvelopeBudgetProfileThresholdTest` import order (cosmetic); `core.profile/package-info.java:22-23` still says `client-profiles/` (and `pm-runtime/.../launch/package-info.java:29`, unverified).
Process: ADR number 016 correct; open ad-hoc PR #418 edits ADR 014 and `core-profile.adoc` (expect a textual conflict there, no ADR collision).

## Leftovers (placed, none asked)
- The `ProfileValidator` `@UtilityClass` conversion, the model-id escape test, the schema `maximum` and an optional-member parity pin, the `OptionalInt` versus `OptionalLong` statement, ADR 016 "seven" → "eight", the null and `forModel(null)` behaviour, the stale `package-info` paths, `Traceability.adoc:494,521`, the over-120 lines and the `EnvelopePackerTest` messages: PLAN-V2-114 new item 25, conversion included (item 21 of the same plan edits `ProfileValidator`; PLAN-V2-94 holds the class out and its PR does not touch it).
- Shipped profiles carry no entry yet (owed to `plan-marshall/pm-mcp-clients`, `pm-api` resources, `ShippedProfilesTest:53`); the wiring into `plan.describe-tasks` remains owed (no production caller): no staged owner in this epic; stated in ADR 016 and Traceability.

## Unverified leads
The lane's mutation runs and test counts (job ids, 10 575 tests); the rendered anchor `core-profile.adoc#_reading_and_validation`; whether #437 will conflict with this PR's `ProfileValidator` change (it does not touch the file).
