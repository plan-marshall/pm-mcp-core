# Landing Analysis: PLAN-32 — Consultation through the server

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #169 (merged as e27d616fb2ab51cafb6aabf83bb32d73882fc0c8)

> Landing record for one shipped plan. Source: inbox message `rb-32-consultation-001.md` (runbook lane,
> opencode, 2026-10-25T18:39:46Z, revision 0), corroborated against git at `e27d616f`, the PR and its checks
> through the `ci` abstraction, the lane's progress file (`.plan/temp/rb-32-consultation/progress.md`), the
> build logs it cites, the SonarCloud public API for `pullRequest=169` and a links check run from the
> orchestrator. The message is a lead; every fact below marked "checked" was read from git, the PR or a run of
> mine, not taken from it.

Standing rules: r1 and R01 to R12, R13, R15, R18 named by the landing (R14, R16 and R17 are not in its list; the
spec is the unadapted one). R05 is named and not met as written, as for PLAN-29.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1, D2, D3 (the consultation service, the link rules, the delivery and submit validation) | shipped-as-specified | Checked: `workflow.consult` gains `ConsultationService`, `ConsultationRequest`, `ConsultationOutcome`, `ConsultationRefusal`, `ConsultationFact`, `ConsultationLink`, `SubmitValidation` (7 `A`) and changes `package-info.java`; `ConsultationServiceTest` holds 18 `@Test` methods (the landing says 18/18) with the seven named Done-when tests present, plus `ConsultTestLocks`, `ConsultationFixtures`, `FailingCreateLocks`. Not run by me. |
| Docs | shipped | Checked: `workflow-consult.adoc` added, `workflow-task.adoc` extended, ADRs `workflow-consult/001`–`004` added. |
| Package retirement | shipped | Checked: `doc/plans/consultation.adoc` is `D`, its index row left `doc/plans/README.adoc`, and the _Depends on_ of the one row that named it lost it (no `consultation` match remains in the index or in `doc/plans/*.adoc`). |

Whole diff, checked (`git diff 4806a304 e27d616f`, first parent `4806a304`): **22 paths** (16 `A`, 5 `M`, 1 `D`),
+2087 / −40: 9 Java `src/main` paths (7 `A`, 2 `M`), 4 test Java paths (all `A`), 9 `.adoc` paths. The landing states
no count.

Surface delta (`inbox landing-check`, declared surface of `PLAN-32-consultation.md`, 9 entries, against the 22 realized
paths): `expansion_detected`. **3 undeclared:** `doc/Traceability.adoc` (the standard companion),
`pm-workflow/doc/developer/workflow-task.adoc` and `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`
(the deliberate surface drift, ADR `workflow-consult/004`; the lane's own hypothesis check says the declared surface was
"partly refuted" at depth one, naming `TaskProtocol.java:329`). **3 declared and untouched:**
`doc/specification/model-work/05-consultation.adoc`, `doc/implementation-watch/model-work.adoc` and
`doc/specification/timeouts/01-job-budgets-and-waits.adoc`. The spec is terminal; its declaration is not corrected.

Done-when, clause by clause (the spec's five):

1. **A consultation is answered once, a second answer refused — reported met, corroborated by presence**
   (`answersOnceAndRefusesSecond`; `mutation-after`).
2. **A fenced asker's answer reaches the successor's offer once, with no `consult` offered to the successor for it —
   reported met, corroborated by presence** (`fencedAskerDeliversToTheSuccessorOfferOnce`,
   `answerThenFenceDeliversToTheSuccessorOnce`; `mutation-after`).
3. **A fourth `consult` link of a task is not offered — reported met, corroborated by presence**
   (`fourthConsultLinkNotOffered`; `mutation-after`).
4. **A `lost` consultation is delivered as `unavailable` — reported met, corroborated by presence**
   (`lostConsultationDeliveredUnavailable`, `lostThenFenceRepairsTheDurableDeliveryToFacts`; `mutation-after`).
5. **A submit omitting a delivered `consultation_id`, or naming one not delivered, is refused — reported met,
   corroborated by presence** (`submitValidatesConsultationIds`; `mutation-after`).
   For all five the mutation-after claim is backed by evidence I could check: the five build logs the landing cites
   (`maven-2026-10-24-094859`, `-094932`, `-095024`, `-095100`, `-095127`) exist under `NO_PLAN/build-results/pm-workflow/`,
   the commits `0d81379b` and `0117c9ee` exist, and the progress file records the same table (line 70). I did not open
   the logs or re-run anything.
6. **Sonar (Standing Constraint, R18) — MET, checked.** `pullRequest=169`: gate `OK`, 0 open issues, new-code coverage
   83.5, duplication 0.0. The lane's first analysis (7 issues: S3776, S135 ×2, S5778, S1845 ×3) was fixed in `8af6e875`
   (progress file line 22); I read only the final state.
7. **Links check (R17) — not in the lane's rule list and not run by it; measured by me: `BROKEN LINKS 1` from the main
   checkout at `4a8c034c`, the same stale-sibling `PM-WATCH-GEN-60` (`phase-workflows/05:170`) as at PLAN-29, not a
   defect of this diff.**
8. **Build gate — reported, not re-run.** Full gate green at `8af6e875` (7343 tests), `cac50f3d` (7340), `c4a6628e`
   (7343) and `0117c9ee` (7399); the project publishes no `module-tests` canonical, so the lane gated with
   `verify -Ppre-commit`. CI on #169 is `success` over 30 checks (checked). `verify -Pcoverage` and
   `verify -Pintegration-tests` are not mentioned by the landing; R01 asks for them, so they are **not evidenced** here.

## R05 — what was done

`doc/specification/model-work/05-consultation.adoc` and the watch item `PM-WATCH-DISP-55` (it exists, checked) are
**untouched**: no deletion, no "Implemented by" pointer, no `doc/implementation-watch` edit, no specification edit at all
(the only `doc/` paths in the diff are the index, `Traceability.adoc` and the deleted package file: none under
`doc/specification/` or `doc/implementation-watch/`). The lane gives the reason in its Residue ("their remaining halves — serving `pm_wait`,
supervision, accounting — are the server assembly's and `pm-runtime`'s … precedent: PLAN-28 left its specification part in
place") and its self-review round 1 carried the finding "delete the implemented specification sections" as residue. This
is another landing that does not delete (PLAN-25 kept its rows and watch items, per its annotation; the lane cites PLAN-28
as its precedent, which I did not re-check here; PLAN-29 condensed the prose and kept the anchors). It is the
open operator decision, recorded at the R05 Open Defect.

## Metrics and Anomalies

- Tokens: unknown (opencode measured none; `inbox landing-check` reports `missing_keys: [total_tokens]`).
- Model: `deepseek-v4.1-flash/high`, harness `opencode`, effort `high`; plugin and executor `0.1.1996`. `steps` ends
  `self-review:budget-exhausted` (a true statement, not a defect of the landing).
- Duration: not supplied; the PR's checks span 4681 s.
- Anomalies:
  - **Self-review hit the three-round limit with 5 findings open** (14, 8, 5 findings); the operator chose "Fix all 5, then
    proceed", fixed in `27d4b9cc` and `8af6e875`.
  - **CI failed on `c4a6628e` for a defect on `main`** (`FindingSchemaParityTest` after #171), the failure the lane of
    PLAN-29 reported and fixed in #172; the lane merged `origin/main` into its branch (`0117c9ee`) instead of rebasing, because
    its rule forbids rebase. This **corroborates** the PLAN-29 landing's claim that `main` was red after the specification-only #171.
  - Three builds were cut or bounded: two killed by the kernel under memory pressure (996 s, 640 s), one hit its 1500 s bound
    and was re-run at 2400 s; three restarts, each re-reading the lane text. `ci pr create` first failed "Not authenticated"
    and succeeded after the operator's "should work now".
  - Rule breaks the lane reported itself: shell `grep`, `find` and `tail` used for searches and logs, `pgrep`, `free` and
    `date` for diagnosis. The guard was `none on this harness (unenforced)`.
  - `executor_absent_from_worktree` again: every build ran `in_process`.
  - CodeRabbit's one nitpick (centralise the role-name pattern in `pm-core`) was answered with a reason and left open:
    a cross-module cleanup nobody owns.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `8af6e875` and `0117c9ee` (no actionable comment), refused twice on
  quota; Sourcery (optional) refused on quota, not gating.
- CI/merge: checked, `ci pr view` reads `merged`, head `feature/32-consultation`, merge commit
  `e27d616fb2ab51cafb6aabf83bb32d73882fc0c8`, first parent `4806a304` (#172). The landing records the merge-group run
  `40221461645`; not re-read.
- Sonar: read from the public API (above), gate OK, 0 open.
- Collision: none observed between running plans. `declaration-currency` over the 22 realized paths: 9 specs checked and
  clean, 1 could not be checked (PLAN-102, prose). **One declaration under-covers a staged spec:** the undeclared
  `TaskProtocol.java` and `workflow-task.adoc` are inside the declared surface of PLAN-73 `question-continuation`
  (staged, depends on PLAN-30), which now builds on a `TaskProtocol` that refuses a consultation task's generic
  `submit` and `infeasible`. Every other overlap is the index README (PLAN-30, 31, 34, 35, 42, 46, 47, 71, 97 one file
  each), a single shared file that the gate does not prompt on. PLAN-31, the one plan still in flight, shares only the index.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-32 --status shipped`
- [x] row `pr` stamped — `#169`
- [x] row `landing` stamped — `landings/PLAN-32.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-32-consultation` (from the sender)
- [x] epic.md narrative reconciled: queue annotation for PLAN-32; `total_tokens` recurrence folded into the existing Open Defect; the R05 Open Defect extended with this landing; the unadapted-spec Watch retired; the "lanes must merge the base" Watch narrowed to PLAN-31; new Watch for the `TaskProtocol` change reaching PLAN-73
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **PLAN-31 is the only plan in flight** and must merge `e27d616f` as well as `4806a304`; the index README is the one shared file.
- **PLAN-73** reads `TaskProtocol.java` and `workflow-task.adoc` as changed by #169 when it starts (Watch).
- **R05**: another landing that kept the prose, an open decision (Open Defect).
- **`verify -Pcoverage` and `verify -Pintegration-tests`** are not evidenced for #169 (the first is also the PLAN-29 Watch lead on `pm-provider-git`).
- **Owed outside this repository**: the server assembly's side of consultation (serving `pm_wait`, supervision, accounting) in `plan-marshall-mcp`, the question's durable home in the asking entity's facts, the model-caller hand-off, and the concept and user documentation at the milestone exit.
- **`total_tokens=unknown`**: folded into the standing Open Defect.
