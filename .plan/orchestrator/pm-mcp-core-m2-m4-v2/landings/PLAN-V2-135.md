# Landing Analysis: PLAN-V2-135 — The model that answered is recorded

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #594 (https://github.com/plan-marshall/pm-mcp-core/pull/594), merge `bd4926f608a34ece02a697758da47a2dd05707d9` (2026-11-25T14:33:47Z), 50 files (+2018/-73)

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb of
> the thirty-seventh run (2026-11-25) from the inbox message `rb-v2-135-served-model-recorded-002.md` (revision 0),
> corroborated against git (`git show --stat bd4926f6`), the PR (`ci pr view`: merged, merge commit `bd4926f6`) and the merged
> code, read by one read-only level-3 leaf and re-read by the orchestrator. Message `-001` (the earlier partial landing, PR open)
> is superseded by `-002` and is archived with it.

## Deliverable Fidelity vs Spec

The lane reported 6 of 6 deliverables met. The diff bears that out for the files; the defects below are in what the files do, not in
what they contain.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `headless.usage.model` is a required member | shipped-as-specified, one unnamed extension | `ProfileStages.USAGE_MEMBERS`, `ProfileValidator.usage` (`:956`), `ProfileValidatorTest` refusal rows, `HarnessProfileSchemaParityTest` pin flipped; the existing `output.result_pointer` check was tightened to the RFC 6901 scan too (`ProfileValidator.java:944`, see L2-1) |
| D2 `readModel`, `ServedModels` | shipped-as-specified | `UsagePointers.readModel`, `ServedModels` (record at `:29`), `UsagePointersTest$ModelPointer`, `ServedModelsTest` |
| D3 `served_models` in the task row | shipped-as-specified, tests thin at two branches | `MetricsRow`, `MetricsCodec.servedModels`, `metrics.schema.json`, `MetricsStoreTest.servedModelsRoundTrip` and `$RefusedTask`; see L2-2 |
| D4 `model_differs` server kind | shipped-modified (flat members, operator answer 2026-11-24) | `ActivityEntry.Kind.MODEL_DIFFERS` with flat `launch_model` / `served_model`, `SecretRedactor.redactModels`, `activity-entry.schema.json`; the spec text (nested record) was never amended |
| D5 watch, mapping, pump, no derivation | shipped with two defects in the pump wiring | `ServedModelWatch`, `ActivityEventMapper.servedModel`, `WorkerActivityPump.appendModelChange`; no production class constructs the pump with a watch (as D5 says); see L3-1 and L3-2 |
| D6 old statements gone | shipped-as-specified | grep of `doc/` finds none; the `#served-model` bullet of `01-profile-schema.adoc` is the one spec edit |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze` surface), 41 tool calls, no write. Reason: reading 50 merged files against a 56 KB spec is the depth the dispatch rule exists for. The leaf returned 34 findings and 13 conformance rows.
- **Orchestrator re-read** (by `git show bd4926f6:…`, line-numbered): `WorkerActivityPump.java:120-212` and `ServedModelWatch.java:60-177` (both L3 defects), `SecretRedactor.java:725`, `ServedModels.java:29,59-85`, `ProfileValidator.java:753,944,965,975-1005`, the parent `activity-entry.schema.json:13` (eleven kinds). Every point recorded below as `checked` was re-read; the two points that depend on the next layer of a run (a failing real recorder; the assembly wiring) are `not shown by reading` and are kept as such.
- **Verdict:** layer 1 findings (minor, spec drift, no claim addressable); layer 2 findings (one major test gap, three minors); layer 3 findings — **two major defects**, five minors.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | Claim 2 (`usage` takes one set of the seven names as required and allowed) | checked | confirms | parent `ProfileValidator.usage`; the lane changed exactly that |
| L1-2 | Claim 6 (`UsagePointers` has no `@Builder`) | checked | refutes | the parent record already carried `@Builder` (the binding re-ground record says so) |
| L1-3 | Claim 8 (`USAGE_KEYS` is the seven figures; `model` must not enter it) | checked | confirms | `ActivityEventMapper.java:50`, the turn loop reads the seven only |
| L1-4 | Claim 9 (activity entry has ten kinds) | checked | refutes | parent `activity-entry.schema.json:13` lists eleven (`egress` is the eleventh); the binding record corrects it |
| L1-5 | Claim 12 (the pump is constructed in two test classes) | checked | refutes (count) / confirms (substance) | three test classes (`WorkerActivityPumpTest`, `OffTrackScenarioTest`, `SupervisionPassTest`), no production class |
| L1-6 | Claim 17 (`SecretRedactor.redact` masks text, tool target, link target) | checked | refutes | the parent also masked the egress destination, which was the template for `model_differs` |
| L1-7 | Claim 22 (HYPOTHESIS: the `output.result_pointer` check is the model for the `model` shape) | checked | confirms | the parent check was `startsWith("/")`; the lane replaced it with a scan (L2-1) |
| L1-8 | D4 text (nested `ModelDiffers(launch, served)`) against the binding record (flat) | checked | refutes | the lane asked the operator, who chose flat; the spec text stays as written |
| L1-9 | The spec never says who reads `served()` and when | checked | refutes | the gap is the cause of L3-2 |
| L1-10 | Carried Open Item 2 ("operator decides") has no recorded answer; the landed alias rule is symmetric and release-aware, beyond the recommended variant (b) | checked | refutes | `ServedModelWatch.sameModel`, `:159-166` |
| L1-11 | The stage-usage statement (Open Item 6) corrected to "every figure" | not_shown_by_reading | none | `01-profile-schema.adoc` moved; the D6 diff touches the `#served-model` bullet only |

The numbered claims of this spec's `## Claim Labels` section are numbered list items, which the claim parser does not address (the section parses as one claim, the verify-first bullet). No corpus verdict is persisted from this layer: a finding that names no addressable corpus claim persists nothing, and the section is `parsed`, so `--section-scope` does not apply.

**Layer 1 verdict:** findings — 7 spec drifts (L1-2, L1-4, L1-5, L1-6, L1-8, L1-9, L1-10), all minor and history now; not shown by reading — 1 (L1-11).

### Layer 2 — Implemented

| Finding | Deliverable | What | Verdict |
|---------|-------------|------|---------|
| L2-1 | D1 | The existing `output.result_pointer` check changed from `startsWith("/")` to the RFC 6901 scan (`ProfileValidator.java:944`): a profile with a malformed `~` escape loaded before and is refused now. No deliverable names it, ADR core-profile/017 says "the same check" and omits the tightening, and `kind_pointer` (`:1005`), `models_pointer` (`:483`) and others still use `startsWith("/")`, so pointer validation is uneven. Tested and recorded by the lane as H22. | checked, minor |
| L2-2 | D3 | No test for the `MetricsRow` rule "a task closed without an attempt records no served models" (`MetricsRow.java:461-463`) and none for the codec refusals of a string other than `unmeasured`, JSON null, a non-array or a non-string element. `servedModelsRefusals` covers a repeated and an empty identifier only. | checked, minor |
| L2-3 | D5 | **No pump-level test of "per attempt" or "commit only after the write".** Deleting both `watch.restart()` calls (`WorkerActivityPump.java:133,155`) fails no test, and no test fails the recorder write and retries. The retry path is where L3-1 sits and the suite does not find it. | checked, **major** |
| L2-4 | D1 | The 256-character limit is tested on the refusing side only (257); no test accepts exactly 256. | checked, info |
| L2-5 | D5 | `OffTrackDetectorTest.modelDiffersRaisesNoSignal` compares two detections for equality; it would fail if the detector read `MODEL_DIFFERS`, but it does not show the baseline raises a signal. | checked, minor |
| L2-6 | D2–D5 | Order: the spec says each deliverable tests first; D2–D5 ran mutation-after (the lane's own table says so). Only D1 has a recorded failing run. | checked, minor |
| L2-7 | — | Files with no deliverable of their own but needed by the row component: `DeletedMemberRefusalTest`, `MetricsRowBuilderContractTest`, `UsagePointersBuilderContractTest`, `ActivityEntryBuilderContractTest`, `HarnessProfileSchemaParityTest`, `PlanTokensTest`; and `ProfileStages.StartContext` reuses the usage-member constant `MODEL` as a null message (`ProfileStages.java:168`, coupling). | checked, info |
| — | D1 | `ShippedUsageFixturesTest.shippedProfilesDeclareTheModelPointer` is green only against the pm-api SNAPSHOT that carries `usage.model`; the lane reports it green and the PR's CI passed. Whether it stays green on `main` for every future SNAPSHOT is not shown by reading. | not_shown_by_reading |

The lane's narrative is borne out by the diff for every file, test and ADR it names (15 cases in `ServedModelWatchTest`, four ADRs, three Traceability sections), with one exception: "the watch commits only after the write" (inline-9) does not hold on the retry path (L3-1).

**Layer 2 verdict:** findings — 7 (1 major, L2-3).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `WorkerActivityPump.java:193-196`, `ServedModelWatch.java:131-135` | A batch `[line with no model, line with a fallback model C]`; the write of C's `model_differs` entry throws at `:206`. `offset` advances only at `:182`, so the retry re-reads the batch. | On the retry the no-model line reaches `change == null` and calls `watch.recorded()`, which commits the stale `next = C` left by the failed attempt; `see(C)` then finds C equal to the previous model and writes nothing. The entry is lost for good, and the comment at `:208-209` and ADR 023 ("the retry of the same line offers it again") do not hold. A batch whose failing line is its first is safe. | **major** |
| L3-2 | `WorkerActivityPump.java:133,155`, `ServedModelWatch.java:89-95` | The pump's switch path (`pump` → `closeTracked`) and `close(...)` read the tail, close the attempt and call `watch.restart()` inside one call, which clears `served`. `served()` has no production caller and the pump exposes no accessor. | The assembly cannot obtain the closing attempt's `served()` after the call, and a call before it misses the tail's lines: the row's `served_models` is `unmeasured` or short by the tail's models for a warm worker's task switch or lease-end close. `finish(...)` works when the caller pumps first. No document states the read order and no test exercises it. Latent until the assembly wires it. | **major** |
| L3-3 | `ServedModelWatch.java:71-75` | A launch model that the entry refuses (empty or over 128) | `Difference` reaches `ActivityEntry` and throws `IllegalArgumentException` inside `appendModelChange` on every model-bearing line; with L3-1's retry semantics the pump does not advance. Needs a caller that passes it. | minor |
| L3-4 | `MetricsCodec.java:193` (write side) | A secret-shaped, bidi or zero-width identifier reported by a harness | Activity entries mask both identifiers (`SecretRedactor.redactModels`); the row column takes the raw strings. Whether the assembly masks before building the row is not shown by reading. | minor, not shown |
| L3-5 | `ServedModels.java:29`, accessor `identifiers()` | `new ServedModels(null)` is public and the accessor returns null for `unmeasured` | CLAUDE.md: absence is `Optional`, never a `@Nullable` return (`measured()` is the Optional form; the accessor stays public). | minor |
| L3-6 | `SecretRedactor.java:725` | `isRedactionOf` is public with one same-package caller (`ActivityEntry`) | CLAUDE.md: package-private where possible; a hand-built entry with launch == served == `[REDACTED]` is valid, so the "two different models" rule is softened by a masking detail. | minor |
| L3-7 | `UsagePointers.java` (`readModel`), `ActivityEventMapper.servedModel` | Every line of a stream | `readModel` admits invisible/format characters (`isISOControl` only; the lane records this as specified), resolves through `JsonPointer.resolve` which builds an exception for each non-matching line, and the mapper parses each line a second time. Efficiency only; not shown whether it matters at the supervision rate. | minor, not shown |
| L3-8 | `ServedModelWatch.sameModel`, `:159-166` | Two spellings of one release date (`base-20250514`, `base-2025-05-14`) | Two models; `served()` holds the raw strings, so an alias and its release can both appear as distinct. The code does more than the spec sentence, not less. | info |

**Layer 3 not shown by reading:** the retry behaviour of the real recorder; whether the assembly masks `served_models`; the hot-path cost; green state on `main` against every pm-api SNAPSHOT.

**Layer 3 verdict:** findings — 8 (2 major: L3-1, L3-2; both in the pump/watch wiring; the pointer scan, `readModel` bounds, codec/schema agreement, the distinctness refusal, the `ActivityEntry` rules and the masking collision handling were read and agree).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | Absence is `Optional`, never a `@Nullable` return | deviates | `ServedModels.identifiers()` (L3-5) |
| CLAUDE.md | Package-private over public where possible | deviates | `SecretRedactor.isRedactionOf` (L3-6) |
| CLAUDE.md | JUnit 5, `@ParameterizedTest` for 3+ variants | deviates | `UsagePointersTest.notReported` packs six variants into one `assertAll`; `ActivityEntryValidationTest.members` adds four to an existing one |
| CLAUDE.md | A record of four or more components carries `@Builder` | conforms | `ServedModels` (1) and `Difference` (2) keep canonical constructors |
| CLAUDE.md | No generic `Exception`/`RuntimeException` catch or throw | conforms | catches are `ProfileRefusal` and `IllegalArgumentException` only |
| CLAUDE.md | Assertion messages 20–60 characters | conforms | every static message read is in range; the dynamic one in `ShippedUsageFixturesTest` was not measured |
| CLAUDE.md | No AAA comments, generators for test data, no dependency change | conforms | no pom change in the 50 files |
| CLAUDE.md | Specification, watch, traceability, guides and one ADR per decision here | conforms | four ADRs, three guides, three Traceability sections |
| standing rules R10/R13 | Schema and codec agree; `format_version` stays 1 | conforms | `metrics.schema.json` and `activity-entry.schema.json` in the codec's order |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as unobserved, not zero). `plugin_version`: `unknown`. The landing-check reports `complete: false` on exactly these two keys, the known recurrence (Open Defect 7, owner E27); no new defect.
- Duration: not measured by the landing.
- Anomalies: two operator questions in the first session (predecessor open; wire shape), both answered; the hard cross-repo predecessor ran the wrong way round (the clients profile change landed before the validator accepted it), which is what kept every code PR of the epic red until this merge. The lane read a harness tool-output file outside the repository root once and says so.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `1a179d30` and `f6d0e66f`; 13 body findings, all dispositioned per the lane. Re-read at 2026-11-25T15:11Z through `runbook_lane review-gate --pr-number 594`: `unresolved_review_threads: 0` (matches) and `review_body_findings_total: 13` (matches); `review_body_findings_handled` re-reads as `0` because the dispositions live in the lane's progress file, which no longer exists, so the number `13` is recorded as **not re-verified**, not as a difference. Both are post-merge-stable (the merge was 14:33:47Z; no record in the re-read post-dates it). Sourcery refused on quota.
- CI/merge: merged through the merge queue as `bd4926f6`; the merge head `4798b488` was green at 12 911 tests per the lane.
- Sonar: the landing says `sonar_open_new_issues=0`, quality gate ok. The read-side re-read (`read_open_issues --project plan-marshall_pm-mcp-core --pr 594`) answers `count_status: undecidable` (`head_revision_unavailable` after the merge) with `analysis_revision 4798b488…`, which is the lane's last head. The `0` is therefore the lane's measured claim, not re-verified; no contradiction.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-135 --status shipped`
- [x] row `pr` stamped `#594`
- [x] row `landing` stamped `landings/PLAN-V2-135.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-135-served-model-recorded` (the sender)
- [x] messages `-001` and `-002` archived by their exact names
- [x] `epic.md` reconciled (Decisions and Watches; the validator-ordering lesson)
- [x] the residue of this landing folded into the staged PLAN-V2-150 as its new Deliverable 5 (the two majors, L2-3, L3-3, L3-5, L3-6, L2-1/L2-2 as documentation and test lines), the surface of the spec extended in the same edit
- [x] resume anchor condensed and moved; `queue-view.md` regenerated

## Follow-Ups

- **The two layer-3 majors are reported to the operator in the same turn** (never fixed inline). They are staged as Deliverable 5 of PLAN-V2-150 because that plan already owns `WorkerActivityPump.java`, `ActivityEntry.java` and the worker tests, and shares no file with a running plan except `WorkerActivityPump.java` with 162's open PR (the gate decides at emit).
- The spec text drifts (L1-2, L1-4, L1-5, L1-6, L1-8, L1-10) are history; no spec edit.
- The pm-api SNAPSHOT order of events (clients first, validator second) is recorded as a lesson for the next cross-repo predecessor: the validator side must accept the new member BEFORE the clients deploy it, or the epic's CI turns red for every code PR.
- Owed outside this repository (carried from the landing): the `model_differs` display in the clients, the `pm-mcp-server` wiring of `ServedModelWatch` and `served_models` (which is where L3-2 becomes live), the per-version harness outputs (PM-WATCH-CLIENT-20).
