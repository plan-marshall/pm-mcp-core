# Landing Analysis: PLAN-93 — Drop the cross-repository dependencies the index still names although they are done

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #136 (https://github.com/plan-marshall/pm-mcp-core/pull/136), merged as squash `7756dfd` through the merge queue on 2026-10-22

> Landing record for one shipped plan. Lives at `landings/PLAN-93.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> reconciliation contract.

Source of the landing: inbox message `rb-93-index-done-dependencies-001.md` (`kind: landing`, sender `rb-93-index-done-dependencies`, `/plan-marshall-runbook-lane` on opencode, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`; `landing-facts` block present. `harness=opencode`, `model=deepseek-v4.1-flash`. `surface_delta` measured: declared 1, realized 1, added 0, missing 0, `state: clean` (base `7756dfd`). No candidate-lesson messages came with it.

## Deliverable Fidelity vs Spec

| # | Deliverable | Verdict | Evidence (read at `7756dfd`) |
|---|-------------|---------|------------------------------|
| 1 | The three done dependencies | met | `git show 7756dfd` touches `doc/plans/README.adoc` alone, 3 lines: `profile-loader` Depends-on is `nothing`; `job-confinement-and-environment` keeps `profile-loader`; `runtime-control` keeps `supervisor`, `task-queue-and-protocol`, `machine-onboarding-unit` (the consent store) and the operator-answer clause. The three `pm-mcp-clients/doc/plans/<name>.adoc` links are gone. `pm-mcp-clients` `origin/main` (`beeea77`) lists `profile-documents`, `exec-read-denied-paths` and `installation-layout` `done`, files under `implemented/` |
| 2 | Every other cross-repository cell | met, no diff needed (the landing says "not fully met" only for the links check, below) | `git grep` of `../../../` links in the index at `7756dfd`: four remain, the two sibling indexes (line 247), the module-structure specification (line 8) and `plan-marshall-mcp/doc/plans/question-loop-verification.adoc` (line 93). In `plan-marshall-mcp` `origin/main` (`38bd66d`) the row is `open` and the file exists (`git cat-file -e`). The hypothesis "no other cell names a done package" is corroborated |

**Done-when, clause 3 (`trace.py … BROKEN LINKS 0`)**: the plan skipped it by the operator's choice (the command lives in a sibling checkout the runbook lane may not call) and reported the clause not met. The orchestrator ran it, read-only, on 2026-10-22: against the local documentation checkout (8c1ddf4, nine commits behind its `origin/main`) it reports `BROKEN LINKS 1`, the known `roadmap.adoc:242` link to `pm-core/doc/developer/core-queue.adoc` (not this landing's: the index it changed has no broken link); against the documentation `origin/main` (`b8c61b3`, extracted with `git archive` into the scratchpad) and this repository at `7756dfd` it reports `BROKEN LINKS 0`. The clause holds; the plan's "not met" is a gap in its evidence, not in the result.

## Surface delta (measured by the orchestrator)

1 file merged, 1 declared entry (`doc/plans/README.adoc`), 0 undeclared, 0 declared entries untouched.

## Metrics and Anomalies

- `total_tokens`: not in the message (opencode; the ninth landing with it). `deliverables_total=2`, `deliverables_done=1` in the facts block: D2 produced no diff, which is the correct outcome and not a gap.
- Wall clock: not reported. The first CodeRabbit run waited 90 minutes on a quota refusal and was triggered again; the second and a `full review` escalation both finished with no actionable comments; no review thread existed (`ci pr reviews`: 0). Sourcery (optional) refused on a hard quota of three days.
- CI (plan-reported, `ci checks`): `build / conclusion` success with `build` and `test` skipped by the repository's docs-only path rule, `integration-tests / conclusion` success, `dependency-review` success. SonarCloud: 0 open issues on the pull request (API, 2026-10-22); the quality-gate endpoint returned no `projectStatus` for `pullRequest=136` (a docs-only diff has no analysed code), not an error of the landing.
- Self-reported rule breaks (plan-reported, all disclosed in its Rule check): `persona-documenter` loaded after the edit; no `verify` or `test-compile` on the head, by the operator's explicit choices (docs-only diff).
- Environment, plan-reported and consistent with the executor-skew watch: the installed `runbook_lane` registers only `resolve`; the skill's `build-classify` verb is absent (a stale install). Fifth runbook landing to report it; the open item stays "regenerate the executor".

## Routing and Merge Behavior

Merged through the merge queue as a squash; no ejection. The branch (`chore/93-index-done-dependencies`) was cut after #126 landed (the verify-first clause was kept: it listed the open pull requests, found none touching the index). Between the last analysis (`3d4baf7`) and this landing three pull requests merged that no plan of this ledger produced: #132 `672d746`, #133 `ab1826f`, #134 `50322a7`, all 2026-10-21 18:22Z, authored by the operator, "Specification only" (see Staged-spec impact).

## Staged-spec impact

- PLAN-19 `profile-loader` (running): its Depends-on cell now reads `nothing`, the readiness the spec's unparking already assumed. Its branch `feature/19-profile-loader` is based on `50322a7`, so it contains #132 to #134 (verified `merge-base --is-ancestor`); PLAN-84 and PLAN-85 branches contain them too.
- PLAN-27 `job-confinement-and-environment`: its cell keeps `profile-loader` only; its spec already lists that dependency (re-grounded at `99971f3`). PLAN-59 `runtime-control` (parked): its index cell lost `installation-layout`; it stays parked on `supervisor`, `task-queue-and-protocol` and the operator-answer clause, as the spec records.
- **#132 to #134 (outside the ledger)** changed 23 specification and watch files (`client-profiles/01-profile-schema.adoc`, new `client-profiles/04-judgment-endpoint.adoc`, `model-work/01`, `06`, `09`, `10`, `11`, `store-schemas/05`, `16`, `18`, `phase-workflows/01`, `03`, `04`, `05`, `workflow-dsl/02`, `07`, `state-catalogue/10`, `hypermedia-format/02`, three watch files incl. the new `PM-WATCH-CLIENT-14`/`-15`) and changed no package file under `doc/plans/` and no production code. Specs that name these files by path (computed by `grep -F` over `plans/`; shipped, superseded and retired ones among them are frozen): PLAN-19, 26, 28, 29, 30, 31, 32, 34, 35, 42, 43, 46, 47, 48, 50, 52, 53, 55, 58, 60, 61, 62, 64, 66, 70, 72, 73, 78 and 92. Their deliverables still match their package files, but the specification each builds against grew; each reads it at its own refine. Not re-grounded here: that is a `cleanup` pass.
- **Specification ahead of the packages.** The new content has no work package: the judgment endpoint (`system_one` protocol, `04-judgment-endpoint.adoc`), judgment votes in the quorum, the `stop_reason` member of the `turn` event in the profile schema and the stop-reason / attempt-end columns of a metrics row, `submit:TASK-NNN` `own_calls`, the change map, the dependency delta. `grep` of `doc/plans/` and of the ledger finds no owner (`stop_reason` belongs in PLAN-19's schema work, which already contains it). Recorded as an Open Defect for the operator.
- `corpus declaration-currency` over the 23 paths returned `disagree` for all 94 specs: it compares declared surfaces with a footprint and says nothing about the claims that cite those files, so it is no evidence either way.

## Reconciliation Actions

- Row `PLAN-93`: `shipped`, `pr=#136`, `landing=landings/PLAN-93.md`, `plan_marshall_plan_id=rb-93-index-done-dependencies`; message `-001` archived.
- Owed outside, resolved: the documentation repository's `roadmap.adoc:242` no longer links `core-queue.adoc` at its `origin/main` (`b8c61b3`: no `core-queue` in the file; `trace.py` 0 broken links). The local documentation checkout is nine commits behind. Open Defect struck.
- Open Defect: the landing carried no `total_tokens` (ninth).

## Follow-Ups

- Nothing in this repository is owed by PLAN-93 itself.
- Decide the owner of the specification-ahead-of-packages content (above), and run `cleanup` before the next emit so the staged specs name the new spec parts.
