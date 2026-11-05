# Landing Analysis: PLAN-V2-11 — The lesson record and its file format (`LessonId`, `LessonFrontMatter`, `Lesson`, `LessonCodec`)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #279 (merge commit `89c2e2a7`, `89c2e2a76a26efb6e98237ff93c19f3af87b5e29`; 20 files, +3202/-50)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-11.md`. Written by the `analyze` verb after verifying
> claims against ground truth (actual code, artifacts, PR state); a pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-11-lesson-record-and-codec-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules **r4**, R01–R14, R17–R20). Corroborated against
`origin/main` (the merge diff of `89c2e2a7`) and the PR state read through `ci pr view` and `ci checks status`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the package file and its index row | shipped, one disclosed deviation | `doc/plans/lesson-store.adoc` (+28), `doc/plans/README.adoc`; the text drops the two `PM-WATCH-STORE-56` references because the 2026-10-29 amendment deletes that watch item |
| D2 — the record types | shipped-as-amended | `LessonId` (100), `LessonFrontMatter` (756), `Lesson` (43), `LessonIdTest`, `LessonFrontMatterTest`; the four cut members (`golden_label`, `labelled_at`, `audience_override`, `tool_version_override`) and their pair rule are absent by the amendment |
| D3 — the codec | shipped, superseded in one clause | `LessonCodec` (829); the spec's "read with `StrictJson.read` … needs no YAML library" is replaced by SnakeYAML under the operator's decision of 2026-11-01 (`pm-core/pom.xml` +7, ADR `core-store/061`); five `Unreadable` reasons; `LessonCodecTest` (434) |
| D4 — schema parity | shipped-as-amended | `LessonFrontMatterSchemaParityTest` (269); the `dependentRequired` pair of the spec is absent by the amendment and the test pins its absence |
| D5 — documentation, decisions, reduction | shipped-as-amended | guide section (`core-store.adoc` +81), ADRs `061` and `062`, the traceability lines, the *File Format* and *Remedy* reductions in `10-job-and-lesson-records.adoc`, `PM-WATCH-STORE-56` deleted, the watch count 52 → 50 |

Surface delta: **20 files**. Outside the spec's declaration and authorised: `pm-core/pom.xml` (the `org.yaml:snakeyaml`
dependency, operator decision 2026-11-01), `doc/implementation-watch/store-schemas.adoc`, `doc/ImplementationWatch.adoc` and
`doc/Specification.adoc` (the count correction). No existing class was edited. CLAUDE.md's dependency list was **not** updated
for SnakeYAML; the decision lives in ADR 061 and the POM comment.

## Metrics and Anomalies

- Tokens `unknown`, duration not measured, `plugin_version` `unknown`: the open defect in `epic.md`, seventh recurrence.
- The spec was read at **r4**; `standing-rules.md` now carries r5 (R21 binds the orchestrator only). Self-review
  `budget-exhausted` after three rounds. Three operator questions (YAML library, scope change, amendment vs byte-equality)
  were answered; the YAML answer shaped the plan.
- Sonar: the lane read the public API (its `resolve` says `not_configured`); the PR's `SonarCloud Code Analysis` check is
  `SUCCESS` and the lane reports 0 open issues on the final head, new-code coverage 95.4 % (lane-stated). A daemon bound expiry
  on `verify -Pcoverage` was re-run in-process (`in_process_fallback`, 1 of 28 builds).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed the final head `54ddfc32` ("No actionable comments"); Sourcery approved `d3735bc0`
  and `54ddfc32`. No standing authorization was needed. This is the cleanest review shape of the three landings of this drain.
- CI/merge: `ci pr view --pr-number 279` reads `merged` with `89c2e2a7`; `ci checks status` reads `overall_status: success`
  over 20 checks.
- Collision check: ran beside PLAN-V2-110, 08 and 12; no collision in the diff.

## Three-Layer Analysis (standing rule R21, r5)

Done at the merge commit `89c2e2a7` by a read-only review (a delegated reader over the four classes, the five test classes, the
schema before and after, ADRs 061 and 062, the guide and the POM), then the points marked **checked** re-read by the
orchestrator. Nothing was built or run. What only a run could show is marked "not shown by reading".

### Layer 1 — Specified: sound, with a lossy reduction and loose ownership of the retirement rules

- **S1 (medium, checked).** The R05 reduction dropped two normative statements. The *File Format* bullet no longer says "YAML
  restricted to the JSON data model (YAML 1.2 core schema)": that restriction (unquoted `2026-10-31`, `yes`, `010`) now lives
  only in the `LessonCodec` Javadoc and ADR 061. The *Remedy* bullet lost `invalid_link_args` (the code that went with
  `remedy_check_mismatch`): `grep` finds it in the pre-merge file and in none of the current one. "Implemented by
  `LessonCodec`" on the Remedy bullet is also wrong, because the remedy rule is enforced in `LessonFrontMatter.Remedy`.
- **S2 (low-medium).** The schema, ADR 061, the guide and the POM comment say "YAML 1.2 library"; SnakeYAML is a YAML 1.1
  processor, and the 1.2 core-schema behaviour comes from the hand-written `JsonModelResolver` (`LessonCodec.java:811-828`).
- **S3 (medium-low).** Ownership of the retirement rules is split and PLAN-V2-16 contradicts itself. Spec line 366 requires a
  non-blank `covering_clause` and `covering_input` for `completely_covered`; PLAN-V2-16 D3 requires `into` for `merged` and
  `superseded`; PLAN-V2-11 D2 says the constructors state "the rules the specification text states beside the schema", yet
  `LessonFrontMatter.Retired` (:678-684) enforces none of them and PLAN-V2-16 D3 puts them in `retire`. PLAN-V2-16 D6 says
  "keep … -56 (undecided)" while its own line 56 says this plan deleted -56 (the lane reports this too). `empty_shell` is
  "empty body" in the `Lesson` Javadoc and "no non-whitespace character" in PLAN-V2-16 D2.
- **S4 (low).** The staged PLAN-V2-11 was not rewritten after the amendment (D4 still lists the `dependentRequired` pair, D5
  still says "byte for byte"); the commit body acknowledges the supersession.
- **S5 (low).** The schema leaves open whether a lesson can carry both `claim` and `retired`; PLAN-V2-16 later decides the end of
  a claim clears `claim`; the record allows both and `LessonCodecTest.fullRecordRoundTrips` builds a retired lesson that is
  still claimed.
- **S6 (low).** The schema says `format: date-time`; the codec accepts only the `Instant.toString` shape
  (`LessonCodec.java:547-559`), so a hand-edited `+00:00` or `.5Z` is `Unreadable`. ADR 061 sells hand-editing as the reason for
  the YAML library and does not state this restriction.

### Layer 2 — Implemented: all deliverables delivered; the tests catch structural regressions, not text-content edge cases or writer order

- **I1 (medium).** No test puts a special character into any front-matter string (newline, quote, backslash, an astral code
  point, a C1 control, U+0085, U+2028); `LessonCodecTest` uses fixed ASCII literals. This is how C1 went unnoticed.
- **I2 (low-medium).** The `AliasToken` branch (`LessonCodec.java:360-362`) is not tested alone: the alias test (:272-273)
  also contains an anchor, so the anchor guard fires first, and it asserts the reason only.
- **I3 (low-medium).** The parity test pins the member constants against the schema but not the writer's order for nested
  objects, nor each member's `type`, nullability, `additionalProperties` or the `minLength` of `title`, `component`,
  `tool_version`, `source.ref`.
- **I4 (low).** No test for `yes`/`no`/`on`/`off` as booleans, `.inf`, `.nan`, a scalar or empty front matter, a BOM, trailing
  spaces on a fence, a closing fence at EOF, `CODE_POINT_LIMIT`, the nesting and alias limits; boundary values are not tested
  on the accepted side (a statement of exactly 10 and 1000 characters, a check of 1 and 512).
- **I5 (low).** `LessonFrontMatterTest.derivedFromUntrustedIsOnlyCarried` asserts that getters return what was set; it cannot fail.

### Layer 3 — Implementation correctness: one unconfirmed round-trip risk, one wrong message

- **C1 (medium, not shown by reading).** The writer is a default Jackson `JsonFactory` (`StrictJson.java:63`, checked), which
  escapes only characters below 0x20 and writes non-ASCII raw; the reader is SnakeYAML. By the scanner's rules a front-matter
  string containing U+0085 (NEL) should read back as a space (`decode(encode(x))` differs from `x` with no error), a raw U+0080–U+009F
  other than U+0085 or U+FFFE/U+FFFF should make SnakeYAML refuse the file (`Unreadable(STRICT_READER)`), and U+2028/U+2029
  followed by `---` should hit the document-separator check inside a quoted scalar. `Lesson` checks only the body for UTF-8
  encodability and `encode` never verifies that its output decodes. Lesson text is derived from untrusted input
  (`derived_from_untrusted`), so a file the server itself wrote could become `present_unreadable`. This follows from SnakeYAML's
  scanner rules; it was not executed.
- **C2 (low, checked).** `LessonFrontMatter.java:150-152` formats "is format version %d, not %d" with `(FORMAT_VERSION,
  formatVersion)`: given version 2 it says "is format version 1, not 2". The codec's own message at `LessonCodec.java:388-389`
  is right.
- **C3 (low).** The resolver accepts more than the JSON data model, against the Javadoc at `LessonCodec.java:70`: `+1`, `0x1F`,
  `.5`, `~`, `Null`, `NULL`, `True`, `FALSE` (:813-817, checked); `revision: 0x1` reads and rewrites as `1`.
- **C4 (low).** Length bounds count UTF-16 units (`LessonFrontMatter.java:462,472`), JSON Schema counts code points: 600 emoji
  in a statement are refused although the schema allows them.
- **C5 (low).** `encode` has no size cap but `decode` caps the front matter at 1,000,000 code points (:197, :344): a large
  `sources` list encodes and cannot be read back; untested.
- **C6 (low).** A BOM, or `--- ` with a trailing space, gives `NO_OPENING_FENCE`; the unreadable reasons are uneven (a bad
  `lesson_id` or an empty title is `RULE_VIOLATION`, a wrong type, a bad enum or format version 2 is `STRICT_READER`).
- **C7 (info).** The anchor, alias, tag and multi-document refusal relies on SnakeYAML's scanner classes (ADR 061 states the risk); whether SnakeYAML works in the GraalVM native build of `pm-mcpd`: not shown by reading.
- **Code-standard misses (checked):** `LessonId.java:36` carries `// NOSONAR java:S6353` with no stated reason; `Lesson.java:35-36`
  throws `IllegalArgumentException` for a null front matter where the package uses `requireNonNull`, and
  `LessonCodecTest:431` passes `null` to a `@NullMarked` constructor to reach it; `LessonFixtures` is a hand-rolled
  `@UtilityClass`, not a `TypedGenerator` registered in `CoreGenerators`; `LessonCodecTest` enables the generator controller and
  uses none; CLAUDE.md's dependency list does not name SnakeYAML.
- **Checked and fine (by the reader):** duplicate keys are refused (:341); anchors and aliases are refused twice (token scan and
  `setMaxAliasesForCollections(0)`); multiple documents are refused; octal and leading-zero integers are refused; unquoted
  timestamps and dates stay strings; fence scanning handles CRLF, a body containing `---`, an empty body and a missing closing
  fence; no generic catch, no `@SuppressWarnings`, no shared mutable state (a `Yaml` per call).

### Verdicts

| Layer | Verdict | Open items |
|-------|---------|-----------|
| Specified | sound; a lossy R05 reduction (S1) and loose ownership of the retirement rules (S3) | S1–S6 |
| Implemented | complete; special-character and writer-order cases untested | I1–I5 |
| Correct | no defect confirmed; one round-trip risk (C1) that a test settles, one wrong message (C2) | C1 for the operator; C2–C7 as notes |

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-11 --status shipped`
- [x] row `pr` stamped — `279`
- [x] row `landing` stamped — `landings/PLAN-V2-11.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-11-lesson-record-and-codec` (from the sender `rb-v2-11-lesson-record-and-codec`)
- [x] epic.md narrative reconciled — seventh recurrence on the incomplete-landing defect; a Watch for the layer findings
- [x] PLAN-V2-16 reconciled with the lane's flagged contradictions and the S3/C1 findings it must read (a fold; no surface added)
- [x] three-layer analysis (R21) written above; C1 goes to the operator, no fix plan staged without the operator's word
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- PLAN-V2-16 (`lesson-store-operations`) is unblocked by this landing and builds on `LessonCodec`; it should settle S3 at its
  outline and add the special-character round trip (C1, I1) before it writes lessons.
- Owed outside this plan (lane): the concept and user documentation of the lesson record and the roadmap check-off belong to the
  milestone exit; the stale "one strict reading of `StrictJson`" statements in `core.store/package-info.java` (lines 16-17),
  `StrictJson.java:37` and `Traceability.adoc:1610` are outside the spec's edit permission.
