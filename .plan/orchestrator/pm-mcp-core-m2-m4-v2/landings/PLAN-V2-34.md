# Landing Analysis: PLAN-V2-34 — Apply R05 to the shipped waits, worker launch, task protocol and consultation

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #456 (merge `a62d4db2`, 2026-11-16T15:11:35Z, merge-group run `43893777208`)

> Landing record for one shipped plan. Written by `analyze` (inbox scan) from the lane's second landing message
> `rb-v2-34-r05-reduction-waits-launch-protocol-002.md` (the first, filed with `merge_state=open`, was recorded as an
> observation). A claim of the message is a lead; every number below was re-read at its `file:line` on `main`.

## Deliverable Fidelity vs Spec

The plan is documentation only. All five deliverables are present in the diff; the Q12 clause (settled 2026-11-10, answer "3")
is met in its table form and **not met in its intent**: the three model-work parts did not get shorter, they grew.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 classification + ADR | shipped-modified | ADR `doc/adr/027-condense-and-point-the-implemented-specification-parts.adoc` exists, next free number, listed at `doc/adr/README.adoc:47`, three rules at `:30-41`, evidence rule `:47-49`. The PR body (squash message) carries the 02 table (77 rows) and the 05 table (26 rows) but **no row of the 04 table**: it says "The 46-row table stands" and lists none (message line 540), so Q12 clause (1) is unmet for 04 at the merge. |
| D2 waits | shipped-modified | only the "Applies to" cells of `bounded-wait` and `progress-wait` changed (-67 bytes, `timeouts/01` 552 -> 552 lines); `progress-interval` and the four rows that stay are outside the diff. `PM-WATCH-JOB-5` was relabelled (`Not built (owner runtime-metrics)` on the `provider_rate_limit` clause), not rewritten to its remainder: its guard still states the engine behaviours `ModelWait` and `ModelWaitTest` cover (`job-runtime.adoc:102`). `PM-WATCH-JOB-30` kept for a true reason (`wait_reason` only in `MetricsStoreTest:437`). |
| D3 launch and slots | shipped-modified | `04-worker-launch.adoc` 270 -> 267 lines, 42055 -> 41622 bytes (-1.0%), 7 hunks; the Launch Contract (`:40-50`) and the `worker-output` Activity bullet (`:257`) lie in no hunk (byte-identical). The six named sections were read narrowly: `model-and-effort`, `job-prompt` and other sections that `LaunchComposer`/`PromptComposer` and their tests cover were never tabled or touched. |
| D4 task protocol and consultation | shipped-modified | `02` 201 -> 192 lines but 29911 -> 30956 bytes (+3.5%); `05` 60 -> 60 lines, 6701 -> 6977 bytes (+4.1%); 13 developer-guide link tails appended in `02`, 3 in `05`. `PM-WATCH-DISP-56` deleted with its only inbound link (no reference left in the tree or the documentation checkout). `-41/-42/-47` kept with remainders. |
| D5 the check | shipped-as-specified | `PM-WF-5`, `PM-WORK-2` and the Traceability introduction name files that all exist (`git ls-files`); no `include::` line and no schema file changed (`git diff -G'include::'` hits only ADR prose); no test under `src/test` reads a touched part. |

The three model-work parts together: **+888 bytes (+1.13% of 78667)**, -12 lines net across the four spec files. The lane's
"27 sentences actually shortened or merged (14+8+5)" is measured at 13 hunks / about 16 paragraphs in `02`, 7 hunks in `04`,
6 paragraphs in `05`; the 57/10/24 "condensed" counts include sentences that were left unchanged and only gained a guide link.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), and the orchestrator
re-read the points marked "re-read" against `main` before writing them. By reading, no build, no test run, no write.

- **Dispatch:** one read-only leaf, own context — reason: a documentation diff over four specs, two watch files, the Traceability
  and an ADR, read against three modules' code; one landing's read must not share context with another's.
- **Re-read by the orchestrator:** the byte and line table (`git ls-tree -l` at `a62d4db2^` and `a62d4db2`); `ProfileValidator.java:1095-1108`
  at the parent; PR body lines 530-560; `02-task-protocol.adoc:130-176`; `05-consultation.adoc:50-58`; `WorkerState.java:168-186`;
  `git grep validateSubmit` over `*/src/main/*`; the `04` diff lines naming the relay; `runtime-launch.adoc:248-260`;
  `workflow-consult.adoc:97-100`; `TaskProtocol.java:386-403`. The rest of the leaf's `checked` points (the Traceability file
  existence, the ADR's template sections, the per-sentence class ties of `04`) were not re-read and stand as the leaf's reading.
- **Verdict:** layer 1 findings (3); layer 2 findings (8); layer 3 findings (3, all in the condensed text).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's premise that `ProfileValidator` "rejects only `bounded >= idle`" (Grounding corrections Claim 0, D2) is false at the parent: the validator enforces both the range 15..3600 (`integer(...,15,3600)`) and the margin `bounded > idle - 30` for a profile without progress frames. PLAN-V2-68 (#426, `6bd6daac`) shipped it before this plan ran. The lane's refutation is right; its cite `:1102` is the margin, the floor/cap is `:1099`. The `bounded-wait` row is `implemented`, not `mixed`. | checked (re-read) | refutes | `pm-core/.../profile/ProfileValidator.java:1099-1106` at `a62d4db2^` |
| L1-2 | The clause says "condensed" without defining it, and the Done-when has no size or sentence-change criterion, so unchanged prose that gained a guide link counts as condensed and a result that grew the parts satisfies the clause. | checked | confirms | spec lines 158 and 42; measured table above |
| L1-3 | Q12 says "all three parts ... sentence by sentence" while deliverable 3 names six sections of `04`; the lane took the narrow reading and the other sections of `04` were never tabled. | checked | refutes | `04-worker-launch.adoc` (sections outside the six) |

**Layer 1 verdict:** findings — 3

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| Q12 (2) the floor | `worker-states` (`02:134-175`) is **not condensed**: the nine-row table is untouched. The PR table ties it to `WorkerState` and `WorkerStateCodecTest`, but `WorkerState.State` has 8 values and no `awaiting_answer` (`pm-core/.../store/WorkerState.java:168-186`) and no test covers the "Leaves to" column; `awaiting_answer`/`suspended` belong to the open package `question-continuation`, so the closed-list reason `keep` applied and was not used | none shown for the "Leaves to" column | the lane's "worker-states is condensed as the two rows above show" is not borne out by the diff | checked (re-read) |
| Q12 (2) the floor | `skills-loaded-at-submit` (`04:246`): 3 sentences to 2 (the PR says four to two), linked to `workflow-task.adoc`; `TaskProtocol.java:396-399`, `TaskProtocolRefusalTest:147-165` cover sentence 1 | `TaskProtocolRefusalTest:147` | PR says "four sentences to two" | checked |
| Q12 (2) the floor | `model-and-effort` (`LaunchComposer.java:118,142,401`, `LaunchComposerTest:93`) and `job-prompt` (`PromptComposer.java:24-30,66,109-123`, `PromptComposerTest`) are covered by a class and a test and were left in full prose, untabled | `LaunchComposerTest`, `PromptComposerTest` | the floor was not applied to them | checked |
| Q12 (1) the table | the 04 sentence table is absent from the PR body at merge | - | PR body "The 46-row table stands" lists no row; the "10 condensed / 36 keep (all assembly-owned half)" split cannot be re-read | checked (re-read) |
| D4 `05` consult-reference | `05-consultation.adoc:55` presents the `consultation_ids` refusal as implemented ("The reference is recorded with the result") with `SubmitValidation`/`ConsultationServiceTest:204` as evidence | `ConsultationServiceTest:198-224` (the method, not the submit path) | `validateSubmit` (`ConsultationService.java:376`) has no caller in any `src/main` of this repository (re-read: the only `main` hit is its own Javadoc at `:69`); `TaskProtocol.submit` (`:386-403`) checks status, kind and skills only and never reads `consultation_ids`; `TaskRefusal.INVALID_LINK_ARGS` has no producer. The assembly checkout at `/home/oliver/git/plan-marshall-mcp` (`3b93248`, 2026-10-27, may lag origin) has no caller either. | checked (re-read) |
| D4 guide | `pm-workflow/doc/developer/workflow-consult.adoc:99-100` says "The server calls it in its submit path before it accepts the submission" | - | false in this repository; the lane recorded the residue as "Owed to the milestone exit: Nothing" so it is unowned | checked (re-read) |
| D5 | `pm-runtime/doc/developer/runtime-launch.adoc:255-258` (Owed) still says the launch sections "carry an `Implemented by` pointer", which ADR 027 and the merged specs deny; `:250-254` still lists the start-failure guard as owed though `StartFailureGuard.java` exists | - | the diff made the statement false and did not touch the guide; the lane noticed and left it | checked (re-read) |
| D1 | ADR 027 template sections, number and README line | - | none | checked |

**Layer 2 verdict:** findings — 8 (the floor, the 04 table, the 04 reading, `consultation_ids`, the guide, the Owed note, the JOB-5 relabel, the watch counts below)

### Layer 3 — Correct

A layer-3 finding of a documentation plan is a statement that is now wrong or a normative statement lost. None is fixed inline.

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `04-worker-launch.adoc:44-48` (diff) | the job-token step lost "the relay must be the release of the daemon it connects to" and "the relay sends both on every request" | no class of this repository covers the relay, so ADR 027's own rule ("a sentence no class and no test covers stays") is breached; the sibling `runtime-lifecycle/01:75` suggests the first clause may be meant to go, the second stays in `08-security.adoc:72` | minor |
| L3-2 | `05-consultation.adoc:55`, `02-task-protocol.adoc:67` | a reader takes the submit refusals as implemented | `consultation_ids` and the schema step are refused by nothing in this repository's submit path; the sections state them as built | major |
| L3-3 | `02-task-protocol.adoc:32` | the three `pm_wait` answer bullets folded into one sentence with three nested parentheticals and an xref inside a parenthesis | readability worse than the bullets, the section not shorter in bytes | minor |

**Layer 3 not shown by reading:** the links check `BROKEN LINKS 0`, the green targeted runs of 85 and 56 tests, the CodeRabbit finding bodies, the later heads `f40d2c0a`/`fa46f2d0`, the sibling's `GAPS WATCH` line (the leaf's grep of the sibling found no DISP-55/56; the lane says it lists them) — each needs a run or a current sibling.

**Layer 3 verdict:** findings — 3

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | links are relative | conforms | the added links resolve (`workflow-task.adoc`, `workflow-consult.adoc`, `runtime-worker.adoc`) |
| CLAUDE.md | ADR numbering per directory, template, README line | conforms | `doc/adr/027`, `README.adoc:47` |
| CLAUDE.md | a plan changes the developer documentation its change touches | deviates | `runtime-launch.adoc:255-258` still holds the claim this reduction made false; the consult residue has no owner |
| ADR 027 | a sentence no class and no test covers stays | deviates | `04:44-48` (L3-1); `05:55`, `02:67` (L3-2) |
| ADR 027 / ADR 016 | no `include::` line and no schema file changed | conforms | `git diff -G'include::'` |
| CLAUDE.md | links check before every merge | not_shown_by_reading | the PR body claims `BROKEN LINKS 0` |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as unobserved, not zero). `plugin_version` `unknown` too: `landing-check` `complete: false` for `total_tokens` and `plugin_version` (known defect E27, recurrence).
- Model: `deepseek-v4.1-flash/high`, harness `opencode`, guard none (unenforced).
- Anomalies: the lane's two early shell searches (read-only); builds were daemon-routed with queue waits up to 609 s; two self-review rounds (8 and 12 findings); the 02/05 growth above; the PR body has the 04 count without rows.
- Surface: the lane reports only declared files plus the R08/R19 bookkeeping; the diff matches (4 specs, 2 watch files, `Traceability.adoc`, ADR + README). No collision with PLAN-V2-43 (`doc/plans/machine-onboarding-unit.adoc` is not on this surface).

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) refused three times (08:11, 09:48, 11:20Z) and reviewed on attempt 4 at 14:27Z (head `322fb301`, 6 findings: 5 fixed in `8fc21e0`, 1 answered out of scope); Sourcery (optional) approved. The head at merge was later (`f40d2c0a` and the `main` merge); the gate treated the one qualifying review as the review at any head. **Re-read against the provider:** `unresolved_review_threads` 0 = 0 (reproduced); `review_body_findings_total` 6 = 6 (reproduced); `review_body_findings_handled` 6 is **not re-verified**: the progress file records the dispositions in prose (line 42: "5 fixed in 8fc21e0, 1 answered ... review-gate all_handled"), not as `finding:` lines, and the post-merge `review-gate` reports 0 handled for want of those lines. Not a difference.
- CI/merge: merge queue, `build (25)`, `build (26)`, `sonar-build` success, merge-group `success`; no conflicts; the branch was merged forward with `main` at the operator's instruction.
- Sonar: `sonar_open_new_issues=0` against the measured 0 open issues and 0 hotspots to review on `pullRequest=456`; matches. `step.sonar-roundtrip.open_issue_rules=none`.
- Operator instruction: the lane received the 2026-11-16 instruction that the Q12 departure was not accepted and followed it item by item (02 and 05 tables, the corrected `05` sentence, merged `main`, quota policy from attempt 4). The instruction's item 2 (apply the floor) was applied to the table, not to the text (see Layer 2).

## Reconciliation Actions

- [x] row `status` -> `shipped`
- [x] row `pr` stamped `#456`
- [x] row `landing` stamped `landings/PLAN-V2-34.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-34-r05-reduction-waits-launch-protocol`
- [x] epic.md: the Watch for #456 retired; queue annotation for 34 added; Open Defect for the Q12 shortfall added (decision pending, below)
- [x] residue routed into PLAN-V2-114 item 35 (see Follow-Ups)
- [ ] resume anchor and `queue-view.md` (written at the end of the drain)

## Follow-Ups

- **PLAN-V2-114 item 35** (new): the false and lost statements — `05:55`/`02:67`, `workflow-consult.adoc:99`, `runtime-launch.adoc:250-258`, the relay clauses of `04`, `PM-WATCH-JOB-5`, the watch-index counts — and the decision for `consultation_ids` (wire `validateSubmit` into `TaskProtocol.submit` or state the section as not built).
- **Operator question (one, below):** whether Q12's floor gets a second pass that actually shortens `worker-states`, `model-and-effort`, `job-prompt` and the unchanged "condensed" rows, with the 04 table.
- The `ProfileValidator` premise of this spec is stale (PLAN-V2-68 shipped #426); the spec is shipped and not edited; no staged spec repeats the premise (PLAN-V2-68 is `shipped`).
- Sibling record: documentation `implementation-watch` index counts 61 items for `model-work.adoc` against 60 and its `GAPS WATCH PM-WATCH-DISP` lists 55 and 56 (lane's statement; `BROKEN LINKS 0` there) — a sibling-repository correction, recorded in item 35 as a read at its head.
