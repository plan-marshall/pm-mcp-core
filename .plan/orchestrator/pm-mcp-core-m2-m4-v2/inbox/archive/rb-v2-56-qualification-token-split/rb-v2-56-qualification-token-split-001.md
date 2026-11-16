envelope_version=1
sender_type=plan
sender_id=rb-v2-56-qualification-token-split
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-16T17:16:50Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-56-qualification-token-split
epic=pm-mcp-core-m2-m4-v2
pr=#465
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-56, qualification and judge-check codecs

Standing rules: r7 (2026-11-10), read in full; the spec's First Instruction names r4 and the file governs. Rules that
applied: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R14, R17, R18, R19, R20. Merge commit
`e456fbf3`; branch `fix/v2-56-qualification-token-split` pruned; worktree removed; main checkout at `e456fbf3`.

## Deliverables

| # | Done-when / clause | Verdict | Test | order | Failing run | Commit |
|---|---|---|---|---|---|---|
| D1 | Both codecs write and read neither `tokens_in` nor `tokens_out`, and refuse a document that carries one; `TokenTotal` keeps its split | met | `QualificationRecordCodecTest` (`canonicalText`, two `unreadable` cases), `JudgeCheckTest#tokenSplitRefused`, both existing parity tests' split cases | test-first | job `7b9e0c45b2374f8cbc185c28378fb6e7` at base `85365a0e` with the new tests: `no form or item writes an input split ==> expected: <false> but was: <true>`; both parity tests `expected: <[]> but was: <[record-only:tokens_in, record-only:tokens_out]>` (`pm-core/target/surefire-reports/TEST-de.planmarshall.core.role.qualification.QualificationRecordCodecTest.xml`) | `f6469335`, `05cdc444` |
| D2 | `QualificationSchemaParityTest` exists, is green, and fails when a member is added to or removed from one side only | met | `QualificationSchemaParityTest` (members, required sets, enumerations, documents, negative controls; plus the judge-check record-level member difference added at round 1) | test-first | job `c204e6f464ef4e5a96948dadf7f03340` at base `85365a0e`: 5 of 8 tests failed, the two codecs' documents rejected as `tokens_in`/`tokens_out` not defined and their member lists carrying `record-only:tokens_in`, `record-only:tokens_out` (`.../TEST-...QualificationSchemaParityTest.xml`) | `18910b66`, `626c2700`, `47b813b9`, `05cdc444` |
| D2 | "every document the codecs write validates against its schema" | **not met as written** | pinned in `documents()`: the qualification document violates the schema on the required `thin` mark, which this plan does not own | none | — | `626c2700` |
| D2 | red before Deliverable 1 and green after | met | as above | test-first | as above | as above |
| D3 | The comments name sections that exist; the sweep finds no other stale title | met | reading: `UsageAggregate.java:34`, `UsageAggregateTest.java:40` cite `§ Metrics Store` (which keeps "Aggregates are never persisted"); no other hit for the removed titles in `pm-core`, `pm-workflow`, `pm-runtime`; `ProfileValidator` untouched; `AttemptEnd.java` absent at the head | none | — | `46d03cef` |
| D4 | The ADR and the developer documentation are written | met | `pm-core/doc/adr/core-role/006-grading-records-persist-the-total-only.adoc`; ADR 005 Status superseded in part; `core-role.adoc` § Tokens and the Split and Tests; `doc/Traceability.adoc` PM-WORK-6 `Verified by`/`Documentation` | none | — | `5185064a`, `626c2700`, `47b813b9` |
| Done-when | `verify -Ppre-commit` green, rewrites reviewed and committed | met | whole reactor, 10746 tests | none | — | last green job `d712961636a84ab7b31fdc801f19d86d` at `c0c28507` (the first run, job `3c8cc1d6dc3646678e4369d5aec943c3`, failed on `LspClientTest.timeout`/`stubborn` in untouched `pm-runtime` under build-queue load and passed on the immediate retry; the gate's one rewrite, a stray blank line in `pm-workflow/.../LandingVariantValidationTest.java`, was rejected by editing the file, leaving the tree clean) |
| Done-when | `verify` of `pm-core` green | met | 5149 tests | none | — | job `4e6d1f8289ef4c00b76502143816393c` at `05cdc444` |
| Done-when | `verify -Pcoverage` green | met | 10746 tests | none | — | jobs `e0fdeb017ac842cb8ef50d01151869c1` at `47b813b9` and `b0154005a2d3429c9d2cda56526d58ee` at `c0c28507`; the very first attempt (job `adca8c78…`) hit the wrapper's 300 s floor and is recorded as such |
| Done-when | `verify -Pintegration-tests -pl pm-core -am` green | met | 21 integration tests | none | — | jobs `37415d54eb60406383e5c51283b6e9ac` at `47b813b9` and `52e35a3afc7b4804893b177ee6525d0d` at `c0c28507` |
| Done-when | Sonar: no open issue, gate green before the PR is ready | met | SonarCloud read of the PR | none | — | 1 issue found at `47b813b9` (`java:S1612`, fixed in `05cdc444`); 0 confirmed at `05cdc444` and at the merge head `c0c28507`, gate `OK` |
| Done-when | Links check (R17) | met | `trace.py` over the sibling checkout at `origin/main` `4dd67ea6` | none | — | `BROKEN LINKS 0` |

Per-head test runs: `module-tests` green at `05cdc444` (5149, job `26a3aae4dcca49faa8816ae8bf9db22f` was the earlier pm-core run at `5185064a`), pre-commit/verify green at `47b813b9`, `05cdc444` and the merge head `c0c28507`.

`deliverables_done` is 3 because Deliverable 2's document-validation clause is not met as written: see Residue.

## Personas and skills

- Personas applied as standards: `persona-implementer` (the two codecs and `TokenTotal`), `persona-module-tester` (the
  parity test and the codec tests), `persona-code-reviewer` (all three self-review rounds and the PR-comment triage).
- The spec declares no `Skills:`/`Persona:` lines: `skills: none named`. R20's skills were read and applied: `junit-core`,
  `java-core`, `javadoc`, `cui-testing` (via the generator rule), `java-lombok`, `java-null-safety`.
- `skills added: none` — no surface drift beyond the declared files.

## Hypotheses

- HYPOTHESIS (the Javadoc carries more stale wording than the draft found): **confirmed in part** — `TokenTotal`'s
  Javadoc described the split as carried by the grading records
  (`TokenTotal.java:16-21` at base) and is corrected (`TokenTotal.java:15-26` at head); `QualificationRecordCodec` and
  `JudgeCheckCodec` never named the split in their class Javadoc, and `package-info.java:13-14` names "tokens", not the
  split, so no change was owed there.
- The verify-first clause of the spec is **contradicted** by the binding Re-ground record: the schema blocks left
  `store-schemas/16` (#323), #427 added `QualificationRecordSchemaParityTest` and `JudgeCheckSchemaParityTest`, and no
  calibration codec exists. Confirmed at the head: the two tests exist and read both schema files.
- OBSERVED claim 2 (the codecs still carry the members): **confirmed at base** (`QualificationRecordCodec.java:92-93`,
  `114`, `121-122`, `245-246`, `317-318`, `534-537`; `JudgeCheckCodec.java:73-74`, `82-83`, `165-166`, `278-281`) and
  removed at head.
- OBSERVED claim 3 ("no test reads these two schemas"): **contradicted** — the two #427 tests read them; the spec's D2
  therefore lands as a third test beside them, and the two existing ones were un-pinned in the same change.

## Findings

Self-review (three rounds, read-only sub-agent with `persona-code-reviewer`):

- Round 1: 13 findings. Fixed: two false documentation claims that the qualification document validates as written; the
  ADR Context asserting no test read the schema files; the missing judge-check record-level member comparison; the
  missing configuration/topic required-set assertions; the `Traceability` PM-WORK-6 lines; the injected `thin` value;
  both class JavaDocs; the ADR naming the sibling guards. Recorded as an R19 surface observation: the two #427 parity
  test files are not in the spec's Expected Surface.
- Round 2: 5 findings. Fixed: the new test's class Javadoc still claimed as-written validity; three "every written
  document" phrasings; the sibling Javadoc's absolute. Noted only: a pure properties-order reorder of the schema passes
  (the canonical text sorts members anyway).
- Round 3: **open items: none**.

PR comments (all answered on the thread and resolved):

- `sourcery-ai` "persisted records become unreadable" — declined: R13 forbids a reader for the older shape and a
  migration, and nothing is released; the old shape was invalid against the schema.
- `sourcery-ai` "views lose the split" — declined: the split is not a member of these records; a view derives it
  (ADR `core-store/048`).
- `sourcery-ai` "the missing mark is not pinned" / "incorrect thin marks escape validation" — `05cdc444`: the violation
  set must be non-empty and every entry must name `thin`; the repair copy uses `putIfAbsent`.
- `sourcery-ai` "unchecked warning is suppressed" (twice) — `05cdc444`: typed copy helper, no `@SuppressWarnings`.
- `sourcery-ai` "parity samples bypass generators" / "schema fixtures use fixed values" — `05cdc444`: values generated.
- `sourcery-ai` "wrong field order passes parity tests" — declined: `CanonicalJson` writes members sorted, so the text has
  no codec order; the ordered member lists are asserted.
- `coderabbitai` "derive `ITEM_MEMBERS` from the schema" — declined: the codec declares its own shape and the parity test
  is the drift guard; a runtime classpath read would move the failure from the build to the first record read.
- Sonar `java:S1612` at `QualificationSchemaParityTest.java:113` — fixed in `05cdc444` (method reference).

## Reviewers

| Reviewer | Required? | Result | Head reviewed | Trigger / wait history |
|---|---|---|---|---|
| coderabbit | required (`review_complete_after_handling`, `quota_limited`) | reviewed | `47b813b9` with the `final_review_risk_coverage` marker naming that commit and a finished `kind=reviewed` payload | triggered once at 15:46 (`@coderabbitai review`); answered with a review at 15:54; its later trigger answers reported "review limit reached"/"already reviewed". The done exemption applies: its review is on record, every finding of it is handled, so later heads raise no obligation. |
| sourcery | optional (`quota_limited`) | reviewed, then refused on its budget | two reviews at `47b813b9` (15:53, 15:54) | triggered with `@sourcery-ai review` at 15:46 and again at 17:00; the second trigger was refused ("used your own review budget … in 2 days"), recorded. Optional, so it gates nothing. |

## Operator questions

none asked. No free-text operator messages were received.

## Rule check

- Guard: **guard: none on this harness (unenforced)** — `.plan/temp/guard.log` has no line for this run; there is no
  pre-tool-use enforcement on this harness, so "all kept" is not claimed.
- Rule breaks by the run's own actions: one. A `grep -n "members(" …` was issued through the shell once while locating the
  compile error's call sites; the rule wants the file tools for searching, and the Grep tool was used for every later
  search. No other shell file operation (read-class included), no `git grep`, no inline Python, no `sleep`, no redirect,
  no workaround after a refusal.
- Every build was submitted through the resolved build commands with the default routing; every script through the
  executor. One exception by rule: R17's links check runs `trace.py` from the sibling checkout with absolute paths, as
  R17 itself prescribes.
- Gate rewrite: the pre-commit profile rewrote one line in an untouched test file (an extra blank line); it was reviewed
  and rejected by editing the file back, leaving `git status` clean, so no cleanup commit was needed.
- Spot check (no tool available for this): a scan of the final diff against the base branch finds 14 files: the 12 files
  the spec's Expected Surface explains (the two codecs, `TokenTotal`, `UsageAggregate` and its test, two codec/parity test
  files, the new parity test, two ADRs, the developer guide) plus `doc/Traceability.adoc` (shared bookkeeping under
  R08/R19). The two #427 parity test files are undeclared in the surface — an R19 observation, recorded above.
- Restarts, killed sub-agents, re-dispatches: none. One build job was queued behind other sessions' builds for minutes at
  a time; no build was backgrounded and no wait was slept.
- Spec read: `spec_source: ledger_worktree` (no `spec_differ` marker was reported), both at setup and at the landing-time
  re-run.
- `executor-skew`: `match` (executor 0.1.2135 = lane 0.1.2135) at the start of the run. At landing time `resolve`
  reports `executor_version: 0.1.2136`, i.e. the executor was regenerated or re-stamped during the run by another
  session; every script call of this run went through that executor and none was refused.

## Build server

- Setup preflight: `ready` (project enrolled, verified daemon handshake). `build-routing` answered `auto`
  (`executor_in_worktree: present`).
- Per-mechanism build count: `daemon_longpoll` for all 26 builds of this run; `in_process_fallback`: 0.
- Fallbacks: none. One `timeout` bound expiry: the first `verify -Pcoverage` (job `adca8c78…`) hit the wrapper's own
  300 s floor (`timeout_used_seconds: 300`, `timeout_source: floor`); it was re-run once with `--timeout 900` and was
  green. One `killed` job: none. Queue waits: builds were queued behind other sessions' jobs for up to ~700 s at a time;
  no `directory_busy` error occurred.
- `runtime_stamp_reason`: not reported by `resolve`. `plugin_version` is `unknown` — the platform-runtime seam returned no
  bundle version for this install.

## Residue

- **The surviving `thin` defect (owned by `qualification-evidence`).** `qualification-record.schema.json:64,70` requires
  a `thin` mark on every topic result; `QualificationRecordCodec` writes none and refuses one
  (`QualificationRecordCodec.java:266-281`, `471-477`). This plan does not own that member (`PLAN-V2-129`'s baseline
  note, `design-audit-002.md`), and its spec forbids editing the schema. The gap is recorded in ADR `core-role/006`, the
  developer guide and `Traceability.adoc`, pinned by `QualificationSchemaParityTest` (non-empty violation set, all naming
  `thin`), and it is the reason Deliverable 2's document-validation clause is reported **not met as written** and
  `deliverables_done` is 3. A later sweep must not read the green parity test as "records now validate".
- The spec's Expected Surface does not name `JudgeCheckSchemaParityTest.java` and `QualificationRecordSchemaParityTest.java`,
  which this change modifies to un-pin the token drift (R19 observation, not expansion). `doc/Traceability.adoc` was
  added as shared bookkeeping.
- `sourcery` is out of review budget for ~2 days; it is optional and had already reviewed the head twice.
