envelope_version=1
sender_type=plan
sender_id=rb-v2-50-r05-reduction-role-sets
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-19T07:03:32Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-50-r05-reduction-role-sets
epic=pm-mcp-core-m2-m4-v2
pr=#504
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-50: R05 reduction of the role sets, the model caller and the role qualification

PR #504, merged as `f19d2a1be117ab33cb3d6053332938d4f7de90c3` (merge group run 44476380775, success).
Base `main` at the merge. Worktree removed, branch and remote ref pruned, `main` fast-forwarded from
`76fb9503` to `f19d2a1b`.

Standing rules: **r7**, rules R01, R02, R05, R07, R08, R10, R13, R17, R18, R19, R20 (R06 and R12 do not
apply: no source change, no design decision).

## 1. Deliverables

Every Done-when is `order: none` — a documentation change captures no failing run, and no clause says
"fails at `{sha}`".

| # | Done-when / deliverable | Verdict | Evidence |
|---|---|---|---|
| D1 | The classification table is in the pull request body, one row per candidate with verdict, evidence and action | met | 22 rows in the body (10 `model-work/01` sections, 3 store-schema sections, 2 `harness-profiles/01` sections, 7 watch items, plus the gone ids) |
| D1 | Each row names an implementing class and a verifying test that exist at the head, and the Traceability lines agree | met | `git ls-files` at the head; `doc/Traceability.adoc` PM-WORK-1, PM-WORK-6, PM-CLIENT-5 name the same classes, tests and guides; no line needed changing (R08) |
| D2 | `model-work/01` and the role-set slice condensed, the class and test recorded per section | met, with one departure | eight sections condensed (`role-sets`, `role-assignment`, `role-composition`, `role-class-fit`, `model-caller`, `constant-baseline`, `qualification-topics`, `grading`); `closed-task-in-band` left as `mixed`; **departure:** the deliverable says `judge-instrument-check` is left as it is — it is condensed and pointed instead, because its premise (`JudgeInstrumentCheck` refuses `judge_unchecked`) is refuted at the head (see §3). If that clause is held binding, D2 is not fully met and `deliverables_done` is 4 |
| D3 | `harness-profiles/01`, `store-schemas/12`, `store-schemas/16` condensed outside the schema blocks, each file left untouched reported | met | `store-schemas/12` and `/16` condensed, their `include::` lines and frames byte-identical; `judge-check-record` left (two sentences); **`harness-profiles/01` left untouched and reported**: `profile-sets` is already condensed and pointed, `profile-verification` is `mixed` (the stage runner and the record writing are the assembly's) and PLAN-V2-63/71/119 are staged on the file |
| D4 | Each covered watch item deleted when its guard has a passing named test and no remainder, a mixed item rewritten to its remainder, `-57` kept | met | `-49` rewritten to its remainder (one case per refusal); `-51` rewritten to its remainder (the `judge-score` member validation); `-57` unchanged; `-66` unchanged (it *is* the export half); `-48`, `-64`, `-65` recorded gone; `PM-WATCH-CLIENT-13` recorded present and kept as PLAN-V2-145's; `PM-WATCH-CLIENT-16` kept. No deletion: no guard was both fully covered and free of a remainder |
| D5 | No dangling reference; Traceability in line; no `include::`, `[source,json]` frame, inline block, `*.schema.json` or validation-table row changed; the targeted tests pass; the links check does not rise | met | links check: `REFERENCED BUT UNDEFINED WATCH IDS []`, `WATCH ITEMS NOT REFERENCED FROM REQ/SPEC []`, `INLINE SCHEMA BLOCKS 0`, counts consistent (62/62/62 for model-work); the four files are prose only; targeted runs below |

Size, per file: `model-work/01` 76 409 → 76 867 (+458), `store-schemas/12` 63 430 → 63 523 (+93),
`store-schemas/16` 18 913 → 19 390 (+477), `implementation-watch/model-work.adoc` 84 585 → 85 259 (+674).
**The whole change grows the documentation by about 1.7 KB.** The cause is stated plainly: the review rounds
replaced compressed wording with statements the code actually makes (the judge task's end, the baseline
denominator, the ungraded cases) and the two rewritten watch guards now name their six covering tests, which
ADR 027 requires as their evidence. The three specification parts alone are +1 028 bytes.

### Runs

| Run | Command | Result |
|---|---|---|
| Full gate, head 1f2c58d8 | `verify` (`quality-gate`), routed | green, 11 187 tests, `compile, lint, test` — job `7096332835e845ddbe1b724ae85ae2d1` |
| Full gate, merge head 832bcc4f | `verify` (`quality-gate`), routed | green, 11 280 tests — job `6a6a5c8276f842ef881f00913e10a66d` |
| Full gate, merged tree | `verify` (`quality-gate`), routed | green, 11 280 tests — job `c170b70ba85c45c5a1c60106e7db7c3f` |
| pm-core targeted | `test -pl pm-core -Dtest=RoleValidationTableTest,RoleArtifactValidatorTest,AuditSchemaParityTest,TaskQueueSchemaParityTest,RoleSetLoaderTest,ShippedRolesTest,RoleEffectiveConfigurationTest,RoleDigestTest,RoleArtifactSchemaParityTest,ConstantBaselineTest,QualificationCheckTest,RoleTopicsTest,JudgeCheckTest,QualificationSchemaParityTest,ProfileLoaderTest,ProfileSetsContractTest,RoleSetsContractTest` | green, 226 tests — job `67a301a90a6144568dbbb7fad40fb324` |
| pm-runtime targeted | `test -pl pm-runtime -Dtest=CredentialStoreMoveRecordParityTest,WorkerJudgeScorerTest,WorkerGradingHarnessTest` | green, 21 tests — job `c7bc39ae25b447a783c82522993af022` |
| CI | `checks wait`, head 832bcc4f | green (build, gate, integration-tests, dependency-review; `sonar-build` skipped) |

The first targeted attempt used the spec's `+` separator, which surefire 3.6 does not read as OR
(`No tests matching pattern`, exit 1, job `b58f0d27dbe34c7a976905d3a01fb861`); the rerun used `,`.

## 2. Personas and skills

`persona-code-reviewer` for the three self-review rounds and for triaging the five PR findings;
`persona-implementer` and `persona-documenter` as the standards for the spec and watch prose. The spec names
no domain skill (`skills: none named`) and no persona, so no domain skill was loaded and none was added for
surface drift.

## 3. Hypotheses — confirmed and refuted

* The spec's classification premise (`judge-instrument-check` is `open` because `JudgeInstrumentCheck` still
  refuses a grading with `judge_unchecked`) is **refuted**: `GradingOutcome.JUDGE_UNCHECKED` is declared and
  never raised, `JudgeInstrumentCheck.java:27` names the refusal as the conformance harness's own, and
  `RoleGrader.java:160-179` records `PASSED`/`FAILED` and proceeds. The section is `implemented`.
* The screener-runs premise (`GradingProfile.SCREENER_RUNS` and `GradingOffer` raise the run count) is
  **refuted**: `GradingProfile` carries only the caller's `runsPerItem`. The residue is that a caller may
  request more than the specification's one run per item and form.
* `model-caller` is fully implemented — **refuted for its *Fit* step**: `ModelCallResult` carries four closure
  reasons and no `input_too_large`, so the prose is kept.
* `role-assignment` is fully implemented — **refuted**: no `escalation` member exists in `core.role`, and the
  advisory is the clients'.
* `PM-WATCH-CLIENT-13` is "already gone at the head" — **refuted**: it stands at
  `doc/implementation-watch/harness-profiles.adoc:89`, re-created 2026-11-14 for PLAN-V2-145.
* The judge reply "validates against `judge-score`" — **refuted**: the judge task's answer schema is
  `{"type": "object"}` (`WorkerJudgeScorer.judgeItem`) and `grade()` reads the `score` alone; the section now
  says so and the watch item carries the member validation as its remainder.

## 4. Findings and dispositions

Self-review, three rounds (`persona-code-reviewer`): round 1 — 8 findings (the clients'-display sentence
restored, the two refuted premises, `-51`'s citation set, the harness-profiles decisions, the stale line
citations, the evidence question, CLIENT-13); round 2 — 5 (the `-51` guard's untested clause, the body's
stale action, the Sonar/CI claims, the Summary wording, the spec's premises as a carry-back); round 3 — 3 (the
judge task's real end, two citation mismatches, two body numbers). All were corrected except the spec-text
carry-back, which the lane may not edit.

PR findings (`coderabbit`, 5): `inline-1` fixed in `1f2c58d8`; `inline-2` **refuted** (the judge task's answer
schema is `{"type": "object"}`; the bot withdrew it and recorded a learning), answered with that reason
`inline-3` fixed in `1f2c58d8`; `inline-4` fixed in `1f2c58d8`; `inline-5` fixed in `832bcc4f`;
`outside-diff-1` fixed in `832bcc4f`. All five threads replied to and resolved.

## 5. Reviewers

| Reviewer | Required | Reviewed | Outcome |
|---|---|---|---|
| `coderabbit` | yes (quota-limited, `review_complete_after_handling`) | head `2d1f9598` (marker `sourceCommitId=2d1f9598…, kind=reviewed`) and again `83f1d4d7` | refused on quota at 19:49Z, re-triggered at 21:21Z after the 90-minute window, then reviewed; 5 findings, all handled; the check passed on `1f1d4d7` |
| `sourcery` | no | heads `2d1f9598` and `83f1d4d7` | approved, no findings |

Standing review authorization was not needed: CodeRabbit reviewed and holds a qualifying review, so its
`review_complete_after_handling` exemption applied to the later heads and no further trigger was posted.

## 6. Operator questions

1. "How shall I proceed on the Sonar condition?" (no analysis exists for a documentation-only pull request) —
   options: (1) record "no analysis exists" and continue, (2) wait for the review and ask again before any
   enqueue, (3) stop now. **Selected: (2).**
2. "Shall I enqueue the merge on this state?" — options: (1) record "no analysis exists" and enqueue, (2) stop
   with the pull request open. **Selected: (1).**

No free-text operator messages were used as a decision.

## 7. Rule check

* `guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so nothing was
  enforced and no guard log exists for this run. The counts below are the run's own, read from its calls.
* **Rule breaks by the run itself, all in shell file operations** (§ Shell: files are read and searched with
  the file tools, no pipes): `git branch -r --list … | head -20`, `ls .plan/orchestrator`,
  `ls pm-runtime/src/test/java/.../worker/ | head -40`, `git ls-files | grep -i …`,
  `grep -rn "PM-WATCH-DISP-…" … | grep -v …`, `grep -rn "@DisplayName" … | head -20`, and the
  `grep -ro … doc` / `grep -c` / `grep -o` searches that should have been the Grep tool. None of them read
  outside the repository, and none changed a file.
* One rule-sanctioned external call: the links check `trace.py` with the documentation checkout, exactly as
  R17 and the spec spell it.
* Deviations from the resolved build commands: the targeted test runs passed `-Dtest=` through the same
  maven canonical (`--canonical module-tests`) instead of the whole-reactor `package`, as the spec's Done-when
  prescribes; the full `verify` ran through the resolved `quality-gate` canonical.
* Restarts, killed agents, re-dispatches: none. The self-review sub-agent ran three rounds on one session.
* Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker. `executor-skew`: `match`, executor and
  lane both `0.1.2155`.
* Model and effort did not switch during the run: `deepseek-v4.1-flash/high`, `high`.
* `plugin_version` is `unknown` (the runtime-info seam reported none); `total_tokens` is `unknown` (not
  measured).

## 8. Build server

* Setup preflight: **ready** (registered).
* Every build was routed: `mechanism=daemon_longpoll`, `requested=auto`, `resolved=routed`. No
  `in_process_fallback`, no degraded build, no launch-fault retry, no `killed` job.
* Queue waits (the machine was busy): 244 s, 379 s, 827 s, 495 s, 647 s, 1 384 s, 0 s for the last two. One
  build was re-attached four times before it ran; no build was backgrounded.
* One red build: the `+`-separated surefire pattern (exit 1, before any test ran), re-run with `,`.

## 9. Sonar

Configured (`plan-marshall_pm-mcp-core`). The read on every head returned `undecidable`,
`analysis_revision_unavailable`, and the quality-gate call answered HTTP 404: the pull request has **no
Sonar analysis at all**, because the CI change filter skips `sonar-build` for a documentation-only change.
There is no finding to fix and none to dismiss. The operator selected the option that records this state and
enqueues; `sonar_open_new_issues=unknown` and `step.sonar-roundtrip.open_issue_rules=unknown` are the honest
values for a read that did not settle — `n/a` would claim an answered absence.

## 10. Residue and carry-backs

* **The plan spec carries three refuted premises** and the lane may not edit `.plan/orchestrator/`:
  `PM-WATCH-CLIENT-13` is present (line 36), `judge-instrument-check` is not `open` (line 37), and the
  screener's repeated runs are gone (line 159). Corrected in the PR body only.
* `PM-WATCH-DISP-51`'s deletion, `harness-profiles/01`'s `profile-verification` reduction, and
  `PM-WATCH-CLIENT-13`/`-16` belong to plans whose surface includes `doc/ImplementationWatch.adoc` and the
  documentation checkout's `requirements/16-model-work.adoc` (PLAN-V2-63, -71, -119, -145).
* The dead `GradingOutcome.JUDGE_UNCHECKED` constant is a code plan's to remove.
* The `trace.py` count of `BROKEN LINKS` cannot be compared from the plan worktree (its depth breaks every
  `../../../../plan-marshall-documentation/...` link); the comparison used the main checkout, `BROKEN LINKS 130`
  on `main`, and no added target is among the broken ones.
* Nothing is owed to the milestone exit.
