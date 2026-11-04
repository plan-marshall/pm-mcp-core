envelope_version=1
sender_type=plan
sender_id=rb-v2-11-lesson-record-and-codec
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-01T23:42:47Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-11-lesson-record-and-codec
epic=pm-mcp-core-m2-m4-v2
pr=#279
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
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

## 1. Deliverables

Standing rules read: **r4**, rules R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R17, R18, R19, R20.

### D1 — the package file and its index row — commit `2cbaed3a`
* `doc/plans/lesson-store.adoc` exists, the row is in `doc/plans/README.adoc` (status `open`, depends on nothing), and the three _Depends on_ cells name `lesson-store` — **met**.
* "the file exists with that text" — **met with one disclosed deviation**: the text drops the two `PM-WATCH-STORE-56` references (the *Left out* link and "and the open -56" in the *Watch* line), because the 2026-10-29 amendment deletes that watch item and a surviving link would dangle. Order: `none`.
* links check — `BROKEN LINKS 0` on the merged head (recorded in the PR body; at the first commit it was 1, the sibling's own link `articles/beads.adoc:143 → pm-mcp-core#PM-WATCH-DISP-71`, which `main` has since added). Order: `none`.

### D2 — the record types — commit `9b9f1dce`
* `LessonId`, the closed enumerations, `LessonFrontMatter` with its nested records and rules, `Lesson` — **met**.
* `LessonIdTest` and `LessonFrontMatterTest` cover every rule with a refusing case and a passing control, and the three watch-fixture ids parse — **met**. Order: `mutation-after`; failing run at `9b9f1dce` with the status/retired rule mutated, output identifier maven job `9ba72f48cbca407b892539e79714516f` (8 failures); restored and green at the same commit (job `ae7127bcdc0c4ce4b18303e335ca288c`).
* The amendment's cut is applied: `golden_label`, `labelled_at`, `audience_override` and `tool_version_override` and the `labelled_at`-iff-`golden_label` rule are absent by decision.

### D3 — the codec — commit `4644707e`
* `LessonCodec` reads and writes the file; the writer emits the canonical JSON object; `decode` returns the sealed `Readable`/`Unreadable` with five reasons; `encode` refuses a lesson without front matter; the member lists are package-private constants — **met**.
* **"read with `StrictJson.read` … needs no YAML library" — superseded by the operator's decision of 2026-11-01** (a YAML library; ADR `core-store/061`). The reader is SnakeYAML with the YAML 1.2 core schema, anchors/aliases/tags/second documents refused; the writer is unchanged.
* Round trip, body with `---`, one refusing case per reason, anchor/alias/tag, CRLF decoded (ADR 061) — **met**. Order: `mutation-after`; failing run at `4644707e` with the undeclared-member check mutated, output identifier maven job `d8835bd75fbe4165a87836921fcca417` (1 failure); restored and green (job `1af7651dd9d34396902a33241c38b5f3`). The self-review and review rounds added a full-record round trip, a superseded retirement, an empty body, a body whose first line is a fence, a non-UTF-8 file and a non-encodable body.

### D4 — schema parity — commit `0f5c4631`
* `LessonFrontMatterSchemaParityTest` reads the block after `== Lesson Record`, and pins the title, the members and their order, the required sets, every enumeration, the `lesson_id` pattern at its three places, the format version and the bounds — **met**.
* The test passes at the head and fails when the schema changes without the code — **met**. Order: `mutation-after`; failing run at `0f5c4631` with the schema enum `"bug"` changed to `"defect"`, output identifier maven job `8a3937a36391494aaaf06db60c3f45d9` (1 failure); restored and green (job `38d52a82670b47dfa4c48db0be7645dc`). Shown by a temporary mutation of the schema block in the worktree, restored in the same session (not on a separate scratch copy), tree verified clean.
* "the `dependentRequired` pair" — **superseded by the amendment** (the pair was `golden_label`↔`labelled_at`); the test pins its absence. `skip-on-docs-only: false` still set.

### D5 — documentation, decisions, reduction — commit `60522df0`
* The guide section, ADR `core-store/061` and `062` (next free numbers against `origin/main`, whose highest was 060), every new public type with Javadoc, `package-info.java` not edited, the *File Format* and *Remedy* reductions, PM-WATCH-STORE-56 deleted, the PM-IMPL-2 traceability lines — **met**.
* The reduction leaves no anchor dangling — **met** (`BROKEN LINKS 0`).
* "the lesson block compares equal to its `origin/main` version byte for byte" — **superseded by the amendment** (the block differs only by the four mandated members and the pair). The Job Record block and both anchors are unchanged.

## 2. Personas and skills

* D1: `persona-documenter` (documentation). D2: `persona-implementer` for the types, `persona-module-tester` for the tests. D3: `persona-implementer` and `persona-module-tester`; `persona-security-expert` was not needed (no trust boundary or secret is touched). D4: `persona-module-tester`. D5: `persona-documenter`. Every self-review round and every PR-comment triage: `persona-code-reviewer`.
* Skills: the spec declares none — `skills: none named`. R20's coding skills were read and applied: `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` (and `cui-logging`/`cui-http-testing` were not needed: no log record and no HTTP in this plan).
* `skills added: none, reason: the Expected Surface held (no module outside `core.store` was touched)`.

## 3. Hypotheses

* HYPOTHESIS "the new classes are named `LessonId`, `LessonFrontMatter`, `Lesson` and `LessonCodec` and live in `core.store`" — **confirmed** (the spec text names the types; ADR `core-store/062` decides the package).
* HYPOTHESIS "a CRLF lesson file is refused as `Unreadable`" — **refuted**: a CRLF file is decoded (the YAML reader accepts CRLF and the fence scan strips the CR), pinned by `LessonCodecTest.crlfFileIsDecoded` and recorded in ADR 061.
* The verify-first clause held: no open PR named `lesson-store` or added a `Lesson*` class, `doc/plans/lesson-store.adoc` did not exist, and the specification was re-read at `1331370a`.

## 4. Findings

* **Self-review round 1** (sub-agent, `persona-code-reviewer`): 14 findings. Fixed: the PM-WATCH-STORE-19 guard (it described the cut label), the missing full-record round trip, the guide's "every store codec" claim, the R20 assertion messages, the `rule_violation` wording, `MergedFrom.by` over-strictness, the empty body and fence-first body tests. Declined with reasons: the `package-info.java` claim (the spec forbids editing it — named as a follow-up), the `pom.xml` and count-file surface deltas (authorised, named), the YAML core-schema scalar spellings, the masked alias branch, the private `@Nullable` returns (fixed in round 2), the package-file deviation (named).
* **Self-review round 2**: 12 items. Fixed: the tag-message length, the guide's substrate-table claim, the six `@Nullable` private returns converted to `Optional`, the 2026-10-29 decision recorded in the specification, the failure-modes table, the SnakeYAML-internals risk in ADR 061, the `retired.into`/`covering_*` round trip, the core-schema qualifier.
* **Self-review round 3 (the limit)**: every round-2 item verified fixed; no blocker and no major. One non-blocking observation remains open (see Residue), so the self-review state is `budget-exhausted`, not `clear`.
* **CodeRabbit** (required reviewer), three reviews: 7 actionable comments in the first, 2 in the second, none in the third. Fixed: the invalid-UTF-8 body (`STRICT_READER`), the guide's "planned resolver" wording, the non-encodable body (unpaired surrogate), the leading-zero integer read as octal, the multiple-document test's guard message. Declined with reasons and resolved: the "one owner" reading of the package vs. plan names, root `sources` vs. the last revision's `sources`, revision-number uniqueness/contiguity, the verdict-specific `covering_*` and `into` rules (all four belong to the retirement/revision write path of PLAN-V2-16, and the schema does not encode them). Answered: the `pom.xml` version (the parent manages `org.yaml:snakeyaml:2.6`) and the alias fixture (an alias cannot precede its anchor).
* **Sourcery** (optional reviewer): `Approved` on `d3735bc0` and on `54ddfc32`; it also refused once on its own quota (recorded).
* **CI**: green on every pushed head; the final head `54ddfc32` passed the Maven build (25 and 26), the integration tests, the dependency review and the SonarCloud analysis.
* **SonarCloud (R18)**: the lane's `resolve` reported `not_configured` and the provider verb answered `unconfigured` (no credentials), so the read was made through the public API that R18 names. At `d3735bc0` 23 open issues were found and fixed (constant duplication, `[0-9]`→`\d`, a super-linear decimal regex, octal-leading integers, S5778/S5785 in the tests); the final head `54ddfc32` carries **0 open issues**, the quality gate passed and the new-code coverage is 95.4 %.
* **Operator questions**: see section 6.

## 5. Reviewers

| Reviewer | Required | Result | Head reviewed | Trigger / wait history |
|---|---|---|---|---|
| `coderabbit` | yes (`quota_limited: true`) | **reviewed** the current head `54ddfc32` (`final_review_risk_coverage` marker, `kind=reviewed`, "No actionable comments") | `92871575`, `07a10790`, `54ddfc32` | triggered at 20:10, 20:36, 21:36, 23:03; rate-limited once (20:37) and "already reviewed" twice; no standing authorization was needed at the end |
| `sourcery` | no (`quota_limited: true`) | **approved** `d3735bc0` and `54ddfc32`; refused once on its own 7-day quota (next allowed 5 h 45 min) | `d3735bc0`, `54ddfc32` | triggered at 20:10; the quota refusal was recorded and not waited on (optional) |

## 6. Operator questions

1. "Confirm the front-matter default" — options: JSON object, no YAML library / add a YAML library / a hand-written block-YAML reader. Selected: **add a YAML library** ("eventually we need yaml later as well").
2. "The YAML choice triggers a scope change or a stop" — options: proceed now with a YAML library / stop and report the blocker. Selected: **proceed now with a YAML library**.
3. "Amendment vs Deliverable 5 byte-equality" — options: amendment governs / keep the block byte-stable. Selected: **amendment governs**.

No free-text operator messages were sent outside those answers.

## 7. Rule check

* **The guard reported `none` on this harness (unenforced)**, so nothing is reported as "all kept" and no guard-log line exists for the run. The count below is the run's own actions.
* **Shell file operations the run issued**: one `ls -la` of the progress directory at the start (rule: files are listed with the file tools), one `rm -rf pm-core/.plan` to remove the `effective-pom.xml` the build had written into the worktree, and the `find`/`grep`/`javap` calls over build logs and the snakeyaml jar to read a dependency's API. All are recorded here as deviations; no source file was read or written through the shell.
* **The sub-agents' rule breaks count as the run's**: round 1 used `git grep` twice and `ls` twice; round 3 reported `git grep` twice and `ls -la`/`ls -R` once each. They are reported by the reviewers themselves.
* **R17**: the links check was run with the absolute sibling paths as R17 prescribes (`python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …`), which is a direct interpreter call outside the executor — the standing rule names the command, so it governs here. The sibling checkout was fast-forwarded to its `origin/main` (`52ada4a`) before the first run because the spec requires the check against that revision.
* **A daemon bound expiry**: `verify -Pcoverage` hit the build server's supervisory bound (`timeout_used_seconds: 785`, `timeout_source: learned`); it was re-run once in-process (`in_process_fallback`) and passed (8588 tests), and the next builds used the default routing again.
* **`.plan/` writes**: only `.plan/temp/…` (the progress file, the trace matrix, the landing) and the `ci` body slots under `.plan/local/plans/NO_PLAN/work/ci-bodies/`.
* **Spec read**: `spec_source=ledger_worktree`, no `spec_differ` marker.
* **`executor-skew`**: `match` (executor 0.1.2041, lane 0.1.2041).
* **No rule was bent silently**; the three operator decisions and every deviation above are stated in the PR body and here.

## 8. Build server

* Setup preflight: **`ready`** (registered, project root the worktree).
* Per-mechanism build count: **`daemon_longpoll` 27**, **`in_process_fallback` 1**. The fallback is the `verify -Pcoverage` re-run after the daemon's bound expiry (`timeout_used_seconds: 785`, `timeout_source: learned`); the reason is the bound, not a launch fault, and no build retried a launch fault.
* No `killed` job.
* Gates on the final head `54ddfc32`: `verify -Ppre-commit` 8622 tests, `verify` 8622, `verify -Pcoverage` 8588 (at `d2c1cdf9`, the pre-merge head; the merge brought only `main`'s changes), `verify -Pintegration-tests -pl pm-core -am` 18 (at `d2c1cdf9`). CI ran the full build on `54ddfc32`.
* `runtime_stamp_reason`: none carried.

## 9. Residue

* **Open self-review observation (non-blocking)**: when a hand-edited file's closing fence is misplaced and the following lines are YAML-invisible (blank or a `#` comment), they are absorbed into the front matter instead of making the file `Unreadable`. It needs a malformed input and is not a defect of the format; it is recorded for the second plan.
* **Stale claims this plan may not edit**: `pm-core/src/main/java/de/planmarshall/core/store/package-info.java` (lines 16-17) and `StrictJson.java:37` still say the codecs read through "the one strict reading of `StrictJson`", which the lesson codec does not; the spec forbids editing `package-info.java` and no existing class is edited. `doc/Traceability.adoc:1610`'s "every store codec shares" is outside this plan's declared trace edit.
* **Undeclared surface** (authorised, recorded in ADR 061 and the amendment): `pm-core/pom.xml` (the `org.yaml:snakeyaml` dependency), `doc/implementation-watch/store-schemas.adoc` (PM-WATCH-STORE-56 deleted), `doc/ImplementationWatch.adoc` and `doc/Specification.adoc` (the watch count 52 → 50, which also corrected a pre-existing off-by-one against 51 items).
* **For the orchestrator**: `PLAN-V2-16-lesson-store-operations.md:37` still lists `golden_label`, `audience_override` and `tool_version_override` among acts "NOT built", while its own line 56 says this plan cut those fields from the record. The second plan's text needs that reconciliation before it runs.
* **Owed to the milestone exit**: the concept and user documentation of the lesson record and the roadmap check-off.
