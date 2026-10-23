# Landing Analysis: PLAN-103 — classify the unnamed specification parts and cut the missing work packages

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #151 — https://github.com/plan-marshall/pm-mcp-core/pull/151 — merge commit `35fba9ed48c7e2fe9702fe9371588b1f077b1226`

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims against
> ground truth (the merged diff, CI state, the index and the package files, and an independent run of
> the documentation traceability/link checker). The inbox message
> `rb-103-spec-coverage-pass-001.md` was the lead.

## Deliverable Fidelity vs Spec

Docs-only plan; corroborated against `35fba9e` (single parent `6381976`… the base at merge, 11 files,
`+360/−10`, **all of them `doc/plans/*`**), the CI checks (28 checks, `overall_status: success`), and an
independent `trace.py` run against the merged tree of `/home/oliver/git/pm-mcp-core` at `35fba9e`.

| Deliverable (spec) | Verdict | Evidence |
|---|---|---|
| D1 classification of all 22 parts, with evidence | shipped-modified (23, not 22) | the PR body carries the per-part table; the verify-first re-run added `hypermedia-format/03-composite-wait-and-encoder`, which lost its only namer when PLAN-25 (#145) deleted the `waits` package and row — correctly classified and served rather than dropped |
| D2 every unowned part and roadmap area has a package file and a row | shipped-as-specified | **8 package files added** (`access-grants`, `build-parsers-and-gate-honesty`, `client-profile-rendering`, `decision-quorum-and-calibration`, `domain-extensions-and-architecture`, `finalize-pipeline-and-merge`, `re-planning`, `remediation-units`), each with exactly one row in the `== Packages` table (checked by grep per name), each with a `Serves`/`Where` header and a status |
| D3 the three blocked packages link their dependencies, statuses as the rule says | shipped-as-specified | the index rows name the dependencies (`access-grants` → `start-source-intake`, `operator-wait-and-machine-scope`; `judgment-votes` open; `change-map` blocked on `own-calls`), and two false notes were corrected |
| D4 each of the seven package-only packages is named or reported | shipped-modified | three are now named by package files; `core.build` by `build-parsers-and-gate-honesty`; `core.id`, `workflow.envelope` and `workflow.primitive` are put to the operator in the PR body and **remain unowned** (operator decision, not a defect of the plan) |
| D5 `BROKEN LINKS 0` | shipped-as-specified, re-measured | test-first in the lane (two failing `trace.py` runs recorded); re-run by the orchestrator against the merged tree: `BROKEN LINKS 0`, `REQS 115`, `REQS NOT IN ROADMAP []` |

**Done-when:** "no file other than package files and `doc/plans/README.adoc` changed" — corroborated: the
merged diff is 11 files, all `doc/plans/*`. "The plan staged no implementation plan" — corroborated:
no orchestrator queue row, spec or task was created by it.

## Metrics and Anomalies

- Tokens: **`unknown`** — `inbox landing-check` reports `complete: false`, `missing_keys: [total_tokens]`;
  the nineteenth opencode runbook landing with the standing property.
- Duration: not reported; `verify` runs are the measurable part (6931 tests before the merge, 7160 after).
- Anomalies:
  - One flaky local failure, `LspClientTest.stubborn` (an LSP initialize timeout in `pm-runtime`, no
    module of this diff), green on the rerun — the same flake the PLAN-94 landing saw.
  - `executor_absent_from_worktree` again (in-process fallback on every build) — the sixth lane in a row.
  - **The operator's chosen remedy for the Sonar condition was to repair the executor**, and the lane
    regenerated `.plan/execute-script.py` (`--target claude --force`) to get the 0.1.1989 verbs. The
    lane warns that regenerating against the flat opencode install breaks `resolve_bundles_root`. That
    is a locally generated, git-ignored file — recorded as a Watch, not a repository change.
  - The lane discloses shell `ls`/`grep`/`find` use and one read outside the repository root (locating
    the documentation checkout by reading the plugin cache), both re-verified with the file tools. No
    credential was read and no repository file was written.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `50cdeeb` (4 inline findings) and `010c7769` (1 outside-diff finding); all
  fixed, replied to and resolved, the last one in `07b745ff`. Six quota refusals in between. Sourcery
  refused on its own budget and is optional.
- CI/merge: `overall_status: success` over 28 checks; squash merge `35fba9e`. No conflict, no re-verify
  signal, no collision with the plans in flight (its whole surface is `doc/plans/`).
- Sonar: `n/a` in the landing facts and `not_configured` in the resolve — correctly NOT reported as a
  clean result. This analysis found the cause (see the Open Defect in `epic.md`): `.plan/marshal.json`
  carries no `credentials_config` block for `workflow-integration-sonar` at all, so every lane in this
  epic reads `not_configured`; the project's own PR analysis does run in CI (`sonar-build`).
- Surface: the whole realized set is inside the declared `doc/plans/` surface.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-103 --status shipped`
- [x] row `pr` stamped `#151` — `orchestrator queue --set-row PLAN-103 --field pr --value #151`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-103 --field landing --value landings/PLAN-103.md`
- [x] row `plan_marshall_plan_id` stamped `rb-103-spec-coverage-pass` — `orchestrator queue --set-row`
- [x] Open Defect added: the **8 new package files have no orchestrator row and no staged spec** —
      they need a `decompose` pass (see Follow-Ups)
- [x] Open Defect added and folded with the Sonar residue: **`.plan/marshal.json` has no
      `credentials_config` for `workflow-integration-sonar`**, the root cause of every
      `sonar: not_configured` this epic has recorded; remedy is `manage-providers` (operator)
- [x] inbox message archived on consume
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **Stage the 8 new work packages.** They are `open`/`blocked` rows of `doc/plans/README.adoc` with no
  orchestrator row and no `plans/PLAN-NN-*.md`: `access-grants`, `build-parsers-and-gate-honesty`,
  `client-profile-rendering`, `decision-quorum-and-calibration`,
  `domain-extensions-and-architecture`, `finalize-pipeline-and-merge`, `re-planning`,
  `remediation-units`. Staging eight specs is a `decompose`-scale act, not a small op — recorded as an
  Open Defect for the next `decompose`, not done inline here.
- **Operator decisions now owed:** (a) the three package-only packages with no builder (`core.id`,
  `workflow.envelope`, `workflow.primitive`) — remove, or cut a package; (b) whether
  `phase-workflows/07` and `workflow-dsl/03` deserve packages of their own rather than the argued
  classifications.
- **Sonar credential configuration** — one `manage-providers` run would end the `not_configured` class
  across every lane of this epic.
