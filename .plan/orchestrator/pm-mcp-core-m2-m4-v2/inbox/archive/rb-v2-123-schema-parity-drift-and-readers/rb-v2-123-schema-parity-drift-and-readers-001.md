envelope_version=1
sender_type=plan
sender_id=rb-v2-123-schema-parity-drift-and-readers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-14T22:20:58Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-123-schema-parity-drift-and-readers
epic=pm-mcp-core-m2-m4-v2
pr=#427
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2125
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=9
review_body_findings_handled=9
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-123 — landing report

Standing rules: r7 (R02, R05, R06, R07, R08, R10, R11, R13, R14, R15, R17, R18, R19, R20).
Merge commit `f3c0dd2d8301c49771df78285d40c356574968a7`, PR #427.

## 1. Deliverables

**D1 — the intent record is what its schema file says: met.**
`choices`, `repairs` and `Decider.ROLE("role")` in `IntentRecord`/`IntentCodec`; callers updated; the
`repairs`-only-with-a-`fix`-criterion rule is enforced and tested; `IntentSchemaParityTest` loses the three pins and
compares the `choices[]` items, the `adr` and the `criteria[]` items with `verified_by`, keeping the matched controls.
- codec write validates the whole document and the item members — `IntentSchemaParityTest.writtenRecordIsValid`,
  `recordItemMembers`; order `mutation-after`, mutation `FIELD_CHOICES` → `decisions` at the reviewed commit, failing
  run `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-14-211333.log` (commit `40c2f4f0`), 4 failures.
- `recordMembers` controls kept — `recordFileOnlyNegativeControl`, `recordRecordOnlyNegativeControl`; order `none`
  (they are the pre-existing matched controls, kept).
- `repairs` positive round trip and refusal — `repairsRoundTrip`, `repairsRequiresFix`; order `mutation-after`
  (same mutation run plus the constructor refusal).
- `IntentRecordTest`/`BuilderCheckTest` green with the renamed accessor — in the `verify -Ppre-commit` run at
  `c3a99ec6`, 10 548 tests.

**D2 — header and composition record equal their files: met.**
`NO_CODE_DELTA`, `NO_PRODUCTION_DELTA`, `OPERATOR_PATH` added in file order, with their one-line reasons;
`PlanInstanceHeaderTest` compares prune reasons, fallback causes, composition events and triggers against the files,
holds a one-sided control in both directions, and holds no copied enumeration (the composition-event `@CsvSource` is
now a `@MethodSource` over the enum). `workflow-plan.adoc` lines 29/43 corrected; `HeaderSchemaCheck` unchanged.
- wire names equal the files — `pruneReasonsClosed`, `fallbackCausesClosed`, `compositionEventsClosed`; order
  `mutation-after`, mutation `NO_CODE_DELTA` wire → `no_code_delta_x`, failing run
  `.plan/local/plans/NO_PLAN/build-results/pm-workflow/maven-2026-11-14-211407.log` (commit `40c2f4f0`), 2 failures.
- (d) is met in substance, with a limitation: a header carrying the new value is accepted by `HeaderSchemaCheck` and
  the value is a member of the file's `enum`; a full JSON-schema validation of the written header is not possible in
  `pm-workflow` — there is no JSON-schema validator on its class path and the Standing Constraints forbid adding a
  dependency. The two tests say that and name `runtime-schema-validation` as the owner of the full check.

**D3 — every schema file has a reader: met.**
`UnreadSchemaFilesTest` (12 codec-less files: strict parse, `required` ⊆ `properties` at every nested declaration,
local `$ref`s resolve, `$id` unique and a URI, plus the `realized_state.head` read), `JudgeCheckSchemaParityTest`,
`QualificationRecordSchemaParityTest`, `BuildTimingsSchemaParityTest`, `PlanSchemaFilesTest` (the two `pm-workflow`
files; `plan-instance-header` is also read by D2).
- each reader fails against a scratch change — order `mutation-after`, mutation `required` gains `scratch_deviation`
  in `gap-record.schema.json`, failing run
  `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-14-211449.log` (commit `40c2f4f0`).
- deviations the readers pin (each with an owner): `JudgeCheckSchemaParityTest` pins the token split to PLAN-V2-56;
  `QualificationRecordSchemaParityTest` pins the item token split to `qualification-evidence`/PLAN-V2-56. No new
  deviation was found by the added nested-`required` traversal.
- table completeness: derived from the directory for the two small packages the 16 live in; the 8 `core/store`
  entries are listed. The binding State-at-the-emit scopes this plan to the 16 and to naming the owners of the other
  36 (see Residue).

**D4 — the parity tests can fail where their landings said they could: met.**
- `ProjectConfigurationSchemaParityTest` gains `withFileOnly`/`withRecordOnly` (the helper is public now);
- rejecting cases added to `Consent`, `ModelCatalogue`, `RoleAssignments`, `LeaseOwner`, `HarnessProfile`,
  `MachineConfiguration` (gap stated with reason) and `HarnessEnrolment` (gap stated), `CredentialStoreMoveRecord`;
- `PlanHistorySchemaParityTest` compares the file's pattern with `PlanHistoryLine.OUTCOME_PATTERN` and probes `1a`,
  `_x`, `a-b`; order `mutation-after`, mutation of the pattern to `^[a-z0-9_-]*$`, failing run
  `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-14-211517.log` (commit `40c2f4f0`);
- the endpoint schema now carries the reader's constraints for `icon`, `update_evidence` and `model_tiers` (and, after
  review, the icon form coupling and the 256-character source limit); order `mutation-after`, mutation of
  `model_tiers.frontier.maxLength` 128 → 64, failing run
  `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-14-211550.log` (commit `40c2f4f0`);
- messages: the three cited out-of-range messages corrected, and all touched classes searched.

**D5 — the statements are true: met.**
ADR 018 restated as the rule both packages applied; `core-store.adoc` "Tested by" says what the outcome-pattern test
proves and names `choices`/`repairs`/the decider kinds. Search for "will place", "thirteen", "No test of `pm-workflow`":
only the epic ledger copies remain. The two contradictions of D5(d) are moot at this head (PLAN-V2-129 removed
`attempt_end` and added `verified_by`), so nothing remains to report. `pm-core/pom.xml` lines 22/23/112 and
`SpecificationDocument` untouched, as D5(c) requires (PLAN-V2-122 retires them).

## 2. Personas and skills

`persona-implementer` for the code and test work of every deliverable; `persona-code-reviewer` for the self-review
round and for triaging every PR comment. Domain skills read and applied (R20): `java-core`, `java-null-safety`,
`java-lombok`, `javadoc`, `junit-core`, `cui-testing`; `ref-asciidoc` for the documentation edits. The spec declared
exactly these per deliverable. `skills added: none` (no surface drift into another module's domain).

## 3. Hypotheses

- `SchemaResource` reachable from `pm-workflow` through the `pm-core` test-jar: **confirmed** —
  `PlanSchemaFilesTest` and `PlanInstanceHeaderTest` compile and run with no `pom.xml` change.
- Adding the three values changes no composition test: **confirmed** — the full run is green (10 548 tests) and
  `CompositionRuleEvaluatorTest` and the diagram tests are unchanged.
- 13 of the 16 unread files are codec-less: **confirmed** — 12 codec-less files in the table plus
  `judge-check-record`, `qualification-record`, `build-timings` with codecs and `composition-record`,
  `plan-instance-header` in `pm-workflow`.
- `IntentAcceptance` has no seam to the plan records: **confirmed** — `Context(planId, questions, epic, treeRoot)`;
  the "named plan is a merged plan" check is not built, as the spec allows.

## 4. Findings

Self-review (one round, 17 findings): 7 fixed (composition-event enum copy, three message lengths, the renderer
Javadoc, three stale test messages, and the untested `repairs` refusal), 2 display names corrected where they
over-claimed ("validates against the file"), 8 accepted with a stated reason.

PR review (CodeRabbit, 9 findings): 7 fixed in `b6bd20a3` (icon `maxLength`, icon form coupling, `realized_state.head`
conditional, member-order comparison, two nested-`required` checks); 2 refused with a reason (format-version bump —
R13 forbids it; deriving the codec-backed exclusions — no reader registry exists, residual risk owned by the
parity-test family). The nitpick is answered with a reason. Sourcery refused the PR as too large (optional reviewer).

## 5. Reviewers

| Reviewer | Role | Outcome | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | required | reviewed | `f887304f` (qualifying review: COMMENTED with commit id, plus the marker-bearing summary, `kind=reviewed`, `coveredCommitId=f887304f`) | triggered 21:21Z; all 9 findings handled; `review_complete_after_handling` → no re-trigger on the later heads, as §7 allows |
| sourcery | optional | refused (diff over 150 000 characters) | none | triggered once; no review |

Standing review authorization was not needed: every condition of the merge gate holds on the merge head
`c3a99ec6` (review-gate `all_handled`, 9/9, 0 unresolved threads).

## 6. Operator questions

none asked.

## 7. Rule check

- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker (the main checkout's copy and the ledger's agree).
- `executor-skew`: **match** (executor 0.1.2123 = lane 0.1.2123). A source-checkout `not_applicable` did not arise.
- Guard: `none on this harness (unenforced)`; there is no guard log for this run, so this report counts the run's own
  actions rather than a log.
- Rule breaks: one. A `git -C <worktree> status --porcelain` and `git -C … log` were used for state reads, which the
  Shell rule allows for git state but the read-class shell operations it names are file reads — no file content was
  read through the shell (`cat`, `ls`, `grep`, `find`, `head`, `sed` were never used). No `&&`, `;`, pipe, loop or
  heredoc; no inline Python; every script through the executor; every build through the resolved `build` commands;
  every CI action through `ci`; temp files only under `.plan/temp/`; repository edits only inside the worktree.
- There is a tooling limitation worth recording: the file tools truncate a markdown line at 2 000 characters, so the
  binding "State at the emit" line could not be read from disk; it was read in full from the public repository over
  HTTPS (`raw.githubusercontent.com`), which is where the decisive instruction lives (cover the 16 and name the owners
  of the other 36).
- Self-review round: 1, all findings dispositioned; no finding left open.

## 8. Build server

Setup preflight: `ready`, project registered. Builds: `daemon_longpoll: 1`, `in_process_fallback: 8`.
- the one routed build (`verify -Ppre-commit`) hit the daemon's supervisory bound (`timeout_used_seconds=407`,
  `timeout_source=learned`); the split form ran in-process afterwards, as §4 requires;
- every later build ran in-process with an explicit longer timeout for the same recorded reason;
- no launch fault, no `killed` job, no fallback reason from the client (`reason=None` throughout).
Runtime stamp: `runtime_stamp_reason` was not reported; `plugin_version` is `unknown` (the resolve result carries no
value for it).

## 9. Residue

- The other 36 unread schema files and their owners are stated in the PR body: 10 of #404 (their codec packages, no
  `doc/plans/` page names them), 19 older `core/store` files (`epic-intent-v1`/`queue-row` are PLAN-V2-03's;
  `references` is read by this plan now), 7 `runtime` configuration files.
- The two prose conditions of `recipe-descriptor-v1` and `question-envelope-v1` stay with the packages that build
  their codecs — neither is among the 16 and no work package can be named yet.
- PLAN-V2-06 carried items (role/harness name grammar parity, `IdentifierSpecificationParityTest` Javadoc, the
  registry-cell parity decision with PLAN-V2-122) are not this plan's surface; they stay reported.
- `headless.loaded` and `headless.usage.accumulation` now name owners in the Javadoc
  (`client-profile-measured-values`, PLAN-V2-135).
- R13 and the work package `decision-log-renames` contradict each other on backward readability: the rename is in
  place at version 1 with no reader for the old names, as the spec's default requires; the contradiction is reported
  here, not resolved.
