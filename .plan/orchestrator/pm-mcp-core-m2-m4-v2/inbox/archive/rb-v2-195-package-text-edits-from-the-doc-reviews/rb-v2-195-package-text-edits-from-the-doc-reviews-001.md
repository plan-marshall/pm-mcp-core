envelope_version=1
sender_type=plan
sender_id=rb-v2-195-package-text-edits-from-the-doc-reviews
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-26T07:04:34Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-195-package-text-edits-from-the-doc-reviews
epic=pm-mcp-core-m2-m4-v2
pr=#623
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=6
review_body_findings_handled=6
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

# PLAN-V2-195 landing — the package texts follow the two doc reviews

Merge commit `2b4bdee8e8d0b1f199ba6fe15a85124c7e4247e1`, PR #623, merged through the merge queue.
Standing rules read: **r7 (2026-11-10)**; the spec drafted R02, R05, R06, R08, R10, R11, R14, R18,
R19, R20 — applicable here R05 (documents), R08 and R19 (the index is bookkeeping, not surface), R01,
R07, R10; R06 parameter `none`, ADR `none`, layout WS-10. R14/R18/R20 concern code and did not apply
(this plan edits no Java).

## 1. Deliverables

The spec has one Done-when block for the whole plan; its clauses are graded here.

- **D1 — the phase and finalize packages** (commit `248955aa`): `phase-sub-workflows` (seven phases,
  `phases.sanity-check`, stores, lane entries, outline record, Q-Gate/execute parts, the second
  `derive-verification` task), `finalize-prune-and-land-files` (the `realized` composition),
  `re-planning` (the three triggers), `finalize-pipeline-and-merge` (verdict currency, the sanity
  task's V3 and fix-proof, the wait region, `pr_title`, the `coderabbit` record), `README.adoc:73`.
- **D2 — the worker, job and onboarding packages** (commit `051ff0bd`): `worker-activity` (the closed
  signal set), `job-confinement-and-environment` (narrower write set, the new *Build Egress and Build
  Read Set* task), `project-onboarding-unit` (the recording build, `build_credential_files`).
- **D3 — the owners of the remaining mechanisms** (commit `2753d132`): `initial-lane-router` (light
  gate, tie rule), `build-parsers-and-gate-honesty` (build-neutral derivation), the four small package
  edits.
- **D4 — the new packages and the index** (commit `c6d1af2b`): `commit-boundary.adoc`,
  `ci-contract-finalize-operations.adoc`, the two index rows and the dependency cells.

Done-when clauses:

- **(a) each cited line states its new text** — met; order `mutation-after`; the failing output came
  from reverting the committed fix in three representative files (`phase-sub-workflows.adoc:15`,
  `finalize-prune-and-land-files.adoc:15`, `worker-activity.adoc:18`), commits under mutation
  `248955aa`/`051ff0bd`, restored with a clean tree; artifact
  `.plan/temp/rb-v2-195-package-text-edits-from-the-doc-reviews/mutation-after.md`. The base worktree
  at `e451ed04` was tried first and refused (`executor_in_worktree: absent`), so it is not cited.
- **(b) every mechanism of the two reviews' section 4 has exactly one named owner** — met by the
  package texts (light gate → `initial-lane-router`; protected-path decision → `commit-boundary`;
  build-neutral derivation → `build-parsers-and-gate-honesty`; build egress → 
  `job-confinement-and-environment`; `coderabbit` → `finalize-pipeline-and-merge`; `ci.pr-observe` /
  `ci.merge` / `ci.enqueue` → `ci-contract-finalize-operations`; D4/Unicode → PLAN-V2-194 /
  PLAN-V2-188); order `none`. One orchestrator-level duplication remains, see §9.
- **(c) no package text names a removed thing as built** — met; order `none`; the removed names appear
  only in removal statements or refusal tests.
- **(d) the index and the package files agree on names and dependencies** — met; order `none`; after
  the self-review and the review fixes.
- **(e) `verify -Ppre-commit` green** — met at `4799e53f` and at the merge head `2a8a1d9e`
  (12963 tests, no rewrites); order `none`.
- **(f) Sonar: 0 open new-code findings** — **not met as written**. The PR is 16 `doc/plans/*.adoc`
  files; the project's docs-only rule skips `build / build` and `build / sonar-build`, so Sonar never
  analyses the PR and `read_open_issues` answers `undecidable` / `analysis_revision_unavailable` (the
  gate-status read is HTTP 404). The operator selected **“Merge with the Sonar condition waived”**
  twice (the second time after a review of the current head arrived post-question). The landing
  records `sonar_open_new_issues=unknown`, never 0.

Per-pushed-head test runs: `4799e53f` (module-tests, test-compile, verify, verify -Ppre-commit),
`900535b9` (module-tests, test-compile), `315483a8` (module-tests, test-compile), `1e2f7e6b` and
`2a8a1d9e` (module-tests, test-compile, verify, verify -Ppre-commit). All green; 12917 then 12963
tests.

## 2. Personas and skills

- Production/test/security personas did not apply. Documentation work used
  `persona-documenter`; every self-review round and the triage of the PR comments used
  `persona-code-reviewer`.
- Each deliverable named `pm-documents:ref-asciidoc` as its domain skill; it was loaded and its
  validate-format workflow ran on every changed `.adoc`. No skill was added for surface drift
  (`skills added: none`).

## 3. Hypotheses

- “`build-parsers-and-gate-honesty` is the better home of the build-neutral derivation than
  `phase-sub-workflows` task 7” — **confirmed** (`job-runtime/03` Gate Honesty Verification is the
  gate's honesty section and reads the build row; task 7 is the Q-Gate/execute task).
- “The `create-pr` step's owner is `finalize-pipeline-and-merge`” — **confirmed**; the new
  `pr_title` task landed there in D1.
- “The index row edits are inside this plan's surface although R19 names `doc/plans/README.adoc` as
  not surface” — **confirmed**; R19 calls the index shared bookkeeping governed by R08, and this plan
  is a documents plan whose product is the texts.

## 4. Findings

Self-review (3 rounds; `persona-code-reviewer`, read-only sub-agents):

- Round 1, fixed in `f0891b82`: **H1** the prune task named removed rules (`footprint_disjoint`,
  `report-change-type`, `change_type_exempt`); **M1** `FootprintBoundary` was named in the wrong
  module; **M2** `commit-boundary` owned and disowned the same code; **M3** `build-parsers` claimed
  PLAN-V2-192's reconcile-stamp removal; **M4** `ci-contract` vs `forgejo-provider`; **L1–L5** line
  drift (no action), two index dependencies, the `Serves` milestone, the watch file and a test line,
  and the roadmap lines.
- Round 2, fixed in `712d88af`: **N1** the commit-boundary wording still overlapped PLAN-V2-190/193;
  **N2** the prune task was 190's deliverable; **N3** the Forgejo dependency/milestone was
  unrecorded; **N4** the test predicate said “disjoint” where the spec says “holds no code and build
  glob”; plus `core.profile` `CredentialLocations` in `Where`.
- Round 3 (budget limit), fixed in `4799e53f`: the prune title/tests/index and the commit-boundary
  `Where`/index row. Two items remain open at the orchestrator level (§9). The operator chose
  “Proceed to PR and gates”.

PR comments (all threads replied and resolved):

- `coderabbit inline inline-1` (launchability needs the landing analysis) — `315483a8`.
- `coderabbit inline inline-2` (build-map dependency) — `900535b9`.
- `coderabbit inline inline-3` (freshness-derivation dependency) — `900535b9`.
- `coderabbit inline inline-4` (`realized` prerequisite in the index) — `900535b9`.
- `coderabbit inline inline-5` (a `parked` row is never launchable) — `315483a8`.
- `coderabbit inline inline-6` (writer write-set contradiction) — answered with a stated reason:
  the merged `job-runtime/02` item 2 contradicts the review and the plan; a specification finding,
  not a package edit. CodeRabbit agreed (“the specification needs correction”).

## 5. Reviewers

- `coderabbit` (required, `quota_limited`, `review_complete_after_handling`): triggered at
  `20:58:55Z`, refused `rate_limited`; waited 90 minutes with `ci pr wait-for-comments`; re-triggered
  at `22:40Z`; reviewed `4799e53f` (4 actionable comments, all handled), then `1e2f7e6b`
  (2 comments, both handled), then `2a8a1d9e` with no actionable comments; check SUCCESS. The
  `review_complete_after_handling` exemption is on record (`final_review_risk_coverage` names
  `2a8a1d9e`, `kind=reviewed`).
- `sourcery` (optional, `quota_limited`): refused `hard_quota` at `20:58:58Z`; re-triggered after the
  90-minute wait and after the merge head; **APPROVED** at `1e2f7e6b` and again a clean summary at
  `2a8a1d9e`.

## 6. Operator questions

1. “Self-review reached its 3-round limit … continue or proceed?” — options {Proceed to PR and gates
   (Recommended); Run a fourth self-review round}; selected **Proceed to PR and gates**.
2. “How should I proceed?” on the Sonar condition — options {Merge with the Sonar condition waived;
   Stop; leave the PR open}; selected **Merge with the Sonar condition waived**.
3. “What should I do with the third-round inbox item?” — options {Decline as out of scope; stage a
   follow-up (Recommended); Fold the in-surface lines in; Fold the whole message in}; selected
   **Decline as out of scope; stage a follow-up**.
4. Re-asked the Sonar question after a review of the current head arrived (`00:10:21Z`) — selected
   **Merge with the Sonar condition waived** again.

No free-text operator messages were sent.

## 7. Rule check

- `guard: none on this harness (unenforced)` — the resolve result reported no tool-call guard for
  opencode, so no guard log line exists and “all kept” is not claimed.
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker.
- `executor-skew`: `match` (executor and lane text both `0.1.2183`).
- My own rule breaks, reported: two shell calls used `git ls-files … | grep …` to find the
  PLAN-V2-190/193 spec files — a shell file listing plus a pipe, against the Shell rule; `Glob` would
  have answered. No other shell file operation was used; files were read with Read/Grep.
- Sub-agents: three self-review dispatches (`general`, read-only), each recorded in the progress file.
  No restarts, killed sub-agents or re-dispatches.
- The base worktree at `e451ed04` was created through the sanctioned verb, answered
  `executor_in_worktree: absent` (not evidence), was clean, and was removed with `git worktree remove`.
- The `.plan/orchestrator/` changes in the base merges are upstream ledger commits brought in by the
  prescribed merge, not edits by this lane.

## 8. Build server

- Setup preflight: `ready`; registered.
- Every build ran `daemon_longpoll` (0 `in_process_fallback`, 0 degraded, 0 killed).
- Two bound expiries: the first `module-tests` at the merge head hit the daemon floor (`300s`,
  `timeout_source: floor`) and the second the explicit `600s`; both were re-run with `--timeout 1800`
  and finished (881–897s). `verify` and `verify -Ppre-commit` were run with `--timeout 1800`.
- The project publishes all four canonicals; `quality-gate` maps to `verify`, and the plan's Done-when
  spelling `verify -Ppre-commit` was run explicitly as well.

## 9. Residue

- **Third doc-review round declined** (orchestrator message of `2026-11-25T20:47:36Z`, read and
  dispositioned per the operator's selection). Read and **not applied** here, to be staged as a
  follow-up: the `finalize-pipeline-and-merge` Done-When `queue_required` re-dispatch and the
  `checks_failed` line; the `project-onboarding-unit` merge-queue probe; the `decision-engine` task 1
  and Done-When (server-written `blocks`; `decision-engine.adoc` is outside this plan's Expected
  Surface); the `initial-lane-router` refine Done-When lines; and the nine §4 owners (extended
  `ci-contract-finalize-operations` for `ci.checks`/`ci-verify`, `project-onboarding-unit` task 4,
  `finalize-pipeline-and-merge` for `finalize.land-message` at 65%, `phase-sub-workflows` task 7 for
  the per-deliverable build scope and the surfacer selection, PLAN-V2-202 for delivered
  consultations, `decision-engine` for `blocks` at 95%, `initial-lane-router` for refine
  `evidence_class` at 60%, and `phase-sub-workflows` task 7 or `remediation-units` for `FIX -> fixed`
  at 55%).
- **Build-neutral derivation named twice**: PLAN-V2-192's text names both
  `build-parsers-and-gate-honesty` and `finalize-pipeline-and-merge`; this plan's package texts name
  one, per its spec. The orchestrator must reconcile PLAN-V2-192.
- **`forgejo-provider` row**: it now owns the Forgejo `behind` mapping but its index row records no
  dependency on `ci-contract-finalize-operations` (outside this plan's row surface).
- **Specification drift found, not edited** (Standing Constraint): `workflow-dsl/06-reusable-sub-workflows.adoc`
  still says “six phases”; `job-runtime/02` item 2 lets a project-root `write_paths` entry stay
  writable for `writer` workers, contradicting `doc-review-2026-11-24-001` §1 and this package's
  line-70 tests. Both go to the doc-review channel.
- **Self-review**: `budget-exhausted` (3 rounds); round-3’s two lane-level fixes were not confirmed by
  a fourth round, by the operator's choice.
