envelope_version=1
sender_type=plan
sender_id=rb-85-store-records-advisories
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-22T09:51:49Z

```landing-facts
schema=landing-facts/1
plan_id=rb-85-store-records-advisories
epic=pm-mcp-core-m2-m4
pr=#129
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced)

# PLAN-85 — store-record advisories

## 1. Deliverables

The spec's Done-when: every item fixed with a test that fails without the change, or shown by the code to be no
defect and recorded in the PR body; the durable-protocol checklist applied to the question log; ADRs numbered against
`origin/main`. All six deliverables are met.

| D | Done-when | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|
| 1 | duplicate id and second pending `waiver_requests` refused | `WorkflowStatusCodecTest.requestIdTwice`, `onePendingAtATime` | `test-first` | pre-change `99971f3`, `maven-2026-10-21-133625.log` | `57873f9` |
| 1 | repeated waiver id: shown to be **no defect** (last readable line wins, per Store Schemas) and recorded in PR #129 § D1 | `WaiverLogTest.repeatedIdTakenWhole` | `none` | no failing run — the code already was the specified rule | `57873f9` |
| 2 | `decision` on a non-first revised line refused | `QuestionLogRevisionTest.decisionOnlyOnTheFirstQuestion` | `mutation-after` | mutation of `314ea7d`, `maven-2026-10-21-134347.log` | `314ea7d` |
| 2 | decision of a set whose first question is closed defined and tested | `QuestionLogRevisionTest.decisionAfterTheFirstQuestionClosed` | `mutation-after` | mutation of `4c3f158`, `maven-2026-10-21-182959.log` | `4c3f158` |
| 3 | part into a set the log holds refused | `QuestionLogMalformedTest.partIntoExistingSet` | `mutation-after` | mutation of `0d011e7`, `maven-2026-10-21-134704.log` | `0d011e7` |
| 3 | part after a revision or a closing line in the same append refused | `partAfterRevisionInOneAppend`, `partAfterFirstQuestionClosedInOneAppend` | `mutation-after` | mutations of `207659d` and `02e0ed9`, `maven-2026-10-21-195919.log`, `maven-2026-10-22-083330.log` | `207659d`, `395a4f8` |
| 3 | the no-first-question refusal interpolates its ids | `QuestionLogMalformedTest.partNamesNoFirstQuestion` | `test-first` | `fae029a` with the unformatted message, `maven-2026-10-21-153302.log` | `fae029a` |
| 4 | unreadable enrolment file surfaces the refusal instead of a match | `ProjectResolverEnrolmentTest.unreadableEnrolmentBesideMatch`, `emptyEnrolmentAndSidecar`, `rootConflictBesideRefusedFile`, `unreadableFileBesideMatch` | `test-first` | `482a605`, `maven-2026-10-21-135127.log`; read failure: `maven-2026-10-21-195806.log` | `3320c55`, `207659d` |
| 5 | untested `QuestionRecord` guard covered | `QuestionRecordCodecTest.evidenceCount` | `mutation-after` | mutation of `dc9db66`, `maven-2026-10-21-140707.log` | `dc9db66` |
| 5 | untested `QuestionLog` guard covered | `QuestionLogMalformedTest.partNamesASetTheLogDoesNotHold` | `mutation-after` | mutation of `dc9db66`, `maven-2026-10-21-150836.log` | `dc9db66` |
| 5 | the three oversized test classes split along their nested groups (no test lost) | — refactor | `none` | full suite green after the split | `dc9db66` |
| 6 | ADR 033/034 Alternatives written; `PM-WF-12` and `PM-SEC-3` traced | — documents | `none` | the sibling link check: `BROKEN LINKS 1`, a pre-existing link in `plan-marshall-documentation/doc/roadmap.adoc:242` | `36c3b71` |

`test-compile` was run at every pushed head: `b1f2a1a`, `5cb3fd7`, `c8689af`, `4c3f158`, `207659d`, `3c70205`,
`02e0ed9`, `395a4f8`.

## 2. Personas and skills

- Production code: `persona-implementer` (with `ref-code-quality`).
- Unit and module tests: `persona-module-tester`.
- Documentation: `persona-documenter`.
- Self-review and every PR-comment triage: `persona-code-reviewer`.
- `skills: none named` — the spec carries no `Skills:` or `Persona:` lines, and no surface drift was found (the
  Expected Surface's hint held; `WorkflowStatus` and `ProjectResolution` were touched as the guard's and the
  result's natural homes).

## 3. Hypotheses

- OBSERVED "PLAN-14 shipped as #93 and recorded the advisories" — corroborated: the types exist in `core.store`
  (`WaiverLog`, `QuestionLog`, `QuestionRecord`, `ProjectResolver`).
- HYPOTHESIS "each advisory is a real defect" — **partly refuted**: the repeated waiver id is the specified
  last-readable-line rule, not a defect (`WaiverLog.Lines.current`, `WaiverLog.java:84-87`); the rest are confirmed
  defects at the code, each with a failing-run record above. The note that the `enrolment_root_conflict` name may
  differ was right: the resolver returns `EnrolmentRefused` for an unreadable file and `RootConflict` only for two
  readable matches (`ProjectResolver.java:165-174`).

## 4. Findings

- Self-review round 1: 11 findings (1 major — the no-defect record and the checklist belong in the PR body; 5 minor;
  5 nit). All code/docs fixed in `e160c9c`; the two verdict-level items are carried in PR #129.
- Self-review round 2: 2 new findings (a `%s` formatted on the second half of a concatenation; two doc lists missing
  the new refusals). Fixed in `fae029a`.
- Self-review round 3: nothing open.
- CodeRabbit round 1 (`5cb3fd7`): guide's ADR list, ADR 037 blast radius, derive the required-member test inputs.
  Fixed in `6e6913b`; the derivation dropped three universal members and was corrected in `c8689af`.
- CodeRabbit round 2 (`6e6913b`): ADR 037 cross-project overclaim. Fixed in `a72303b`.
- CodeRabbit full review (`c8689af`): decision of a set whose first question is closed (fixed `4c3f158`), `S3776`
  complexity extraction (applied `4c3f158`).
- CodeRabbit full review (`4c3f158`): part after a same-append revision (fixed `207659d`), unreadable-file read
  failure still throwing (fixed `207659d`), ADR 034 rationale and ADR 041 consequence wording (fixed `207659d`).
- CodeRabbit review (`02e0ed9`): part after the first question closes in the same append (fixed `395a4f8`).
- Every raised thread is resolved; the last review's single finding is fixed in the merged head.
- CI: one superseded `build / sonar-build` failure at `5cb3fd7` (SonarSource analyzer internal error on
  `StoreSnapshot.java` plus "Organization is not allowed to access data from non main branches"); a newer run at the
  same head was fully green. One `pm-runtime` `LspClientTest.stubborn` 2-second initialize timeout on a local
  `verify` at `4c3f158` in an untouched module; the same head's `test` run passed.

## 5. Reviewers

| Reviewer | Required | State | Head(s) | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed, last `02e0ed9`; current head `395a4f8` **not reviewed** | `5cb3fd7`, `6e6913b`, `c8689af`, `4c3f158` (twice), `02e0ed9` | triggered 12 times; refusals on quota at 2026-10-21T20:31Z (`207659d`), 2026-10-22T06:05Z (`3c70205`), 09:02Z (`395a4f8`); every finding fixed and every thread resolved; the operator selected the substitution of its last formal review for the current-head requirement, for `207659d`, then `3c70205`, then for the rest of the run at `395a4f8` |
| sourcery | no (optional) | refused on quota | — | refused 2026-10-21T16:11Z and 16:18Z ("2,500,000 diff characters for the last 7 days", next request in 2 days 1 hour) |

## 6. Operator questions

1. Asked: the installed `runbook_lane` exposes only `resolve`, while the skill also documents `build-classify`; the
   diff is Java plus docs. Options: run the full build gate without the classifier / stop and report the stale
   executor. **Selected: run the full build gate without the classifier.**
2. Asked at head `207659d`: CodeRabbit last reviewed `4c3f158`, not the current head; re-trigger refused on quota.
   Options: wait and re-trigger (Recommended) / merge now with the `4c3f158` review (bends the current-head rule) /
   stop and report blocked. **Selected: merge now with the `4c3f158` review.**
3. Free text from the operator: "Was there at least one COdeRAbbit review. If so, then merge" — answered factually
   (formal reviews on `5cb3fd7`, `6e6913b`, `c8689af`, `4c3f158`) and the question was asked again, because free text
   is not a selection.
4. Asked at head `3c70205` (the base merge moved the authorized head). Options: merge now with the `4c3f158` review /
   wait for a review of `3c70205` / stop and report blocked. **Selected: merge now with the `4c3f158` review.**
5. Asked at head `395a4f8` (base unchanged; CodeRabbit last reviewed `02e0ed9`; quota-refused). Options: merge now and
   keep the substitution for this run / merge now for `395a4f8` only / wait for a review of `395a4f8` (Recommended) /
   stop and report blocked. **Selected: merge now and keep the substitution for this run.**

## 7. Rule check

guard: none on this harness (unenforced).

Rules not kept, and one sanctioned bend:

- **Shell — `sed` used once to view a line range** of `QuestionRecordCodecTest.java` while planning the split. The
  file was otherwise read with the read tool; it was a read-only slip, not a guard refusal.
- **Current-head review (section 7) bent by operator selection** — CodeRabbit did not review the merged head
  `395a4f8` (quota); the operator selected the substitution of its last formal review (`02e0ed9`) for the current-head
  requirement, as recorded in section 6.
- **Build commands** — the resolved canonical name `quality-gate` is not a Maven goal in the installed executor; the
  project's own documented command (`verify -Ppre-commit`, per `CLAUDE.md`) was used, scoped to the modules the plan
  or the base changed. `build-classify` was skipped on the operator's answer (section 6.1).
- **Quality-gate rewrites of upstream code not committed** — the pre-commit profile rewrote five `pm-runtime` files
  from upstream #135 (catch-block reformat plus `cui-rewrite:disable` TODO markers). They were restored rather than
  committed: they belong to #135's surface, not this plan's. The rewrites on this plan's own files were committed
  (`b1f2a1a`, `5cb3fd7`).
- No guard refusals: this harness has no tool-call guard.

## 8. Residue

- SonarCloud reports 7 new issues on the PR (quality gate passed; 98.6% new-code coverage) — informational, not
  triaged into this plan.
- The sibling `plan-marshall-documentation` link check reports one pre-existing broken link,
  `doc/roadmap.adoc:242` → `pm-core/doc/developer/core-queue.adoc`, which no file satisfies; not this plan's surface.
- `pm-runtime` `LspClientTest.stubborn` can time out at 2 seconds locally (observed once, in a module this plan does
  not touch); the same head's full `test` run passed.
- Sourcery's optional review is quota-refused until 2026-10-24 (it does not gate).
- The concept and user documentation of the stores and the roadmap check-off of `operator-and-question-stores` remain
  owed to the milestone exit in `plan-marshall-documentation` (PLAN-14's landing recorded the same).
