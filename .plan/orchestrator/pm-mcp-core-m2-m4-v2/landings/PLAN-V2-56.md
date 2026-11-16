# Landing Analysis: PLAN-V2-56 — The grading codecs follow the token-usage closure

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #465 (merge `e456fbf3`, 2026-11-16)

> Landing record for one shipped plan, **delivered with one reported gap**. Written by `analyze` (inbox scan) from the lane's landing
> message `rb-v2-56-qualification-token-split-001.md` (`deliverables_done` 3 of 4). The reading below confirms the lane's own gap and
> finds three minor points the lane did not name. A claim of the message is a lead; the review and Sonar numbers were re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 codecs write and read neither `tokens_in` nor `tokens_out`, refuse a document that carries one; `TokenTotal` keeps its split | met | members gone from `FORM_MEMBERS`, both `ITEM_MEMBERS`, the read loops and the three writers (`QualificationRecordCodec.java:112-119`, `JudgeCheckCodec.java:79-82`); both loops end in `default -> throw undeclared(field)` (`QualificationRecordCodec.java:238-241,306-308`, `JudgeCheckCodec.java:158-163`); `TokenTotal.java:32-127` unchanged in its split; tests `QualificationRecordCodecTest.java:74-79,102-107`, `JudgeCheckTest.java:233-244` |
| D2 `QualificationSchemaParityTest` exists, green, fails on a one-sided member | met | 319 lines, ordered member lists and required sets asserted (`:178-186`, `:205-209`); red-before evidence is a job id in the message and was not reproducible from files |
| D2 clause "every document the codecs write validates against its schema" | **not met as written** (reported by the lane) | the judge-check document validates; the qualification document violates the schema on the required `thin` mark (`qualification-record.schema.json:64,70`, re-read), which the codec neither writes nor accepts (`QualificationRecordCodec.java:266-281,471-477`); pinned by the test (`members()` expects `file-only:thin`, `documents()` requires a non-empty violation set naming `thin`, `:187-190,239-243`). The spec forbids editing the schema and did not name `thin`: a spec gap, not misconduct |
| D3 comments name sections that exist; no other stale title | met | `UsageAggregate.java:34` and `UsageAggregateTest.java:40` cite `§ Metrics Store`, which exists (`store-schemas/05-...adoc:12`, "Aggregates are never persisted" at `:34`); `TokenTotal.java:15-26` corrected; sweep of the three modules finds none of the removed titles; `ProfileValidator` and `AttemptEnd` untouched (absent/clean at the head) |
| D4 ADR and developer documentation | met | ADR `core-role/006-grading-records-persist-the-total-only.adoc`; ADR 005 Status points to it; `core-role.adoc:266-286`; `Traceability.adoc` PM-WORK-6; the `core-store/048` link resolves |

The settled residue of 2026-11-09 owned by this plan (`settled.md` § "Two code residues of the token-usage closure") is discharged: the
codec change is made and the source comments of the named classes carry no stale title.

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read the merged diff (`72d698b9..e456fbf3`, 14 files, +458/-120)
against the spec; the orchestrator re-read the `thin` lines of the schema, the codec refusal sites and the body of ADR 005, and
re-queried the review threads and Sonar. By reading, no build, no test run, no write.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity |
|---------|-------|---------|----------|
| L1-1 | D2's validity clause is satisfiable only for the judge-check record; the spec's own premise (a document validates as written) was unchecked against the `thin` member | confirmed (re-read) | the lane's report is right |
| L1-2 | Expected Surface omits the two #427 parity tests (`JudgeCheckSchemaParityTest`, `QualificationRecordSchemaParityTest`), which had to change to un-pin the drift, and `doc/Traceability.adoc` (R19 bookkeeping) | confirmed | minor |

### Layer 2 — Implemented

| Point | Reading | Verdict |
|-------|---------|---------|
| Refusal tests | `QualificationRecordCodecTest.java:102-107` asserts `StoreRefusal` naming the file (two cases: `tokens_in` on a form, `tokens_out` on an item); `JudgeCheckTest.java:233-244` covers `tokens_in` on an item only; none asserts the cause "undeclared member" | minor (L2-1) |
| Negative controls | the protective checks are the ordered-list equalities; `negativeControls` (`:247-260`) exercises `SchemaParity.withFileOnly`/`withRecordOnly` at record level only (the helper, not the form, item or topic lists); the judge-check nested class has none | minor (L2-2) |
| ADR 005 body | Status says "superseded in part", but the Decision, Alternatives and Consequences (`:15-30`) still say both codecs write and read the two members and reject storing only the total: the body contradicts the code | minor (L2-3, re-read) |
| Count breakdown in the message | 14 files is right; "12 + Traceability + 2" is 15; real split is 11 spec-surface files + Traceability + the 2 tests | minor, message only |
| `THIN_LIMIT=20` | duplicated from the schema description in the parity test; the test fails loudly when `thin` is added, the intended tripwire | info |

### Layer 3 — Correct

| Point | Reading | Verdict |
|-------|---------|---------|
| Other producers or consumers of the split | none in `src/main`, `src/test/resources`, `pm-workflow`, `pm-runtime`; `RoleGrader` and its fixtures use split-less totals only | clean |
| Dead code | none in the codecs; `TokenTotal.measured(l,l,l)`, `splitIn`/`splitOut` and `plus`'s split branch have no `src/main` user outside `TokenTotal` until PLAN-V2-60: the settled decision of 2026-11-10, stated in the new Javadoc and ADR 006 | info (orphan-sweep target) |
| Write path | `QualificationRecordCodec.write` and `JudgeCheckCodec.write` silently discard a split an in-memory `TokenTotal` carries (`QualificationRecordCodec.java:492-495`); nothing refuses it at write; no producer creates one today | info, low risk (L3-1) |
| New documentation | `core-role.adoc:266-286` and ADR 006 say the judge-check record validates and the qualification record validates but for `thin`: supported by the code and the test | clean |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | 0 (13 threads on #465, 0 unresolved) |
| review body findings total / handled | 1 / 1 | not re-verified (progress prose) |
| Sonar open new issues / hotspots | 0 | 0 / 0 (SonarCloud, `pullRequest=465`) |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` only (known defect E27, the platform-runtime seam returns no
bundle version). Surface delta unmeasured (declared and realized not supplied). Lane rule check: one shell `grep` by the run; guard
unenforced on this harness (stated by the lane).

## Residue

1. **The surviving `thin` defect (owner: package `qualification-evidence`, open, no staged plan in the epic).** The package lists the `thin` mark
   and the record member `thin` (`doc/plans/qualification-evidence.adoc:15-16`); `PLAN-V2-50` names the package as
   owner of `judge_unchecked`; `PLAN-V2-96` (the grading adapter) does not name it. Until a plan builds it, no qualification record
   validates against its schema. Recorded as a Watch in `epic.md`.
2. **Carried into PLAN-V2-114, item 37:** ADR 005's body (L2-3), the refusal assertions and negative controls (L2-1, L2-2), and the
   write-time refusal of a carried split (L3-1, decision in the outline: refuse or document).
3. **Not routed:** the orphan `TokenTotal` split (settled, PLAN-V2-60 uses it); the count arithmetic of the message.
