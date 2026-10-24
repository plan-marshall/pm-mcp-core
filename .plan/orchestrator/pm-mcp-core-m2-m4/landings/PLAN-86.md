# Landing Analysis: PLAN-86 — move the shared HTTP base into `pm-provider-http`

epic: pm-mcp-core-m2-m4
workstream: WS-08
pr: #156 — https://github.com/plan-marshall/pm-mcp-core/pull/156 — merge commit `3672829a82741ca1837ba37acd7d6fa842387c9b`

> Landing record written by the `analyze` verb from `rb-86-provider-http-module-001.md` after verifying
> the claims against the merged diff, the PR state, the SonarCloud API and the merged tree.

## Deliverable Fidelity vs Spec

Corroborated against `3672829a` (56 files, `+708/−357`), CI, and the merged tree.

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 `pm-provider-http` builds, moved tests green, ≥80 % coverage; no `de.planmarshall.provider.ci` class remains | met | the module exists (15 files under `pm-providers/pm-provider-http/`); `HttpClientTest`/`EndpointTest`/`JsonTest`, `verify -Pcoverage` reports JaCoCo 96 % instructions / 93 % branches; no `provider/ci` source remains |
| D2 Sonar, GitHub and GitLab depend on `pm-provider-http` + `pm-core`, not `pm-provider-ci` | met | their POMs/imports; the five-module reactor test run (3357 tests) recorded by the lane |
| D3 a sibling-provider adapter fails, a `provider-http` adapter passes, fixtures + `BuildGuardsIT` cases | met | `BuildGuardsIT` 13 cases, `fixturePasses` added; mutation-after evidence recorded |
| D4 repository statements of the old shape corrected | met | `CLAUDE.md`, the OpenCode bridge, READMEs, ADR 002, seven specification parts, `doc/adr/README.adoc`, `doc/Traceability.adoc` |
| D5 the empty `pm-provider-ci` module removed | met in the repository, verified | no `pm-provider-ci` file in the merged tree **except** the generated `.plan/project-architecture/pm-provider-ci/enriched.json`; no POM, no Java import, no aggregator entry |
| D6 the `module-structure.adoc` pull request in `plan-marshall-documentation` is open | **not met as written** | the lane may not edit outside this repository; the correction is in PR #156's body. The operator answered "open and merge" — **the external PR is still owed** |

Also met, verified: the package row/index bookkeeping (this plan has no package of its own), the R17 links
check (`BROKEN LINKS 0`, re-measured on the merge head), all four `verify` profiles green, and Sonar
`PR #156` = 0 open issues with gate `OK` (new coverage 96.2 %).

Footprint: 56 files; **12 undeclared** — mostly the D4 documentation half (`doc/Traceability.adoc`,
`doc/adr/README.adoc`, five specification parts, `README.md`, `pm-provider-sonar/README.adoc`,
`pm-provider-sonar/.../package-info.java`) plus the lane's standing-rules file. The spec declared the code
surface and only part of the documentation the same deliverables required — under-declaration again, not
scope drift into another module's code.

## Metrics and Anomalies

- Tokens: `unknown` (standing property; the only `missing_keys` entry).
- Anomalies: one pre-PR `sonar-build` failure ("Organization is not allowed to access data from non main
  branches") that cleared once the PR existed; three CodeRabbit findings, all handled; one learned 646 s
  and one explicit 1500 s bound expiry with no failing test; `executor_absent_from_worktree` again
  (eighth lane); the plugin/executor moved 0.1.1989 → 0.1.1996 during the run.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `a7932cec` with 3 findings (redaction wording, README/POM parity, shared-base
  wording) — fixed or answered, all threads resolved; re-triggers after later pushes refused on quota
  (recorded, standing authorization). Sourcery refused on its 7-day quota.
- CI/merge: green; squash merge `3672829a`. No conflict, no re-verify signal.
- Sonar: `step.sonar-roundtrip.open_issue_count: 0` — corroborated (`PR #156` = 0, gate OK); `main` stays at
  4, none of them this plan's.

## Reconciliation Actions

- [x] `PLAN-86` row → `shipped`, `pr` `#156`, `landing` `landings/PLAN-86.md`, `plan_marshall_plan_id`
      `rb-86-provider-http-module`
- [x] inbox message archived on consume
- [x] `epic.md`: landing entry; the owed external documentation PR recorded as an Open Defect; the
      `doc/plans` and `.plan/project-architecture` residue recorded as a Watch
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Owed outside (D6):** the `plan-marshall-documentation` PR correcting `module-structure.adoc`. The exact
  correction is in #156's body; the operator chose "open and merge" but no external PR exists yet.
- **Stale module naming in `doc/plans/`:** `README.adoc:141` (forgejo row), `forgejo-provider.adoc:14,39`,
  `module-developer-docs.adoc:8` and `access-grants.adoc:18` still name `pm-provider-ci`. Re-ground them with
  PLAN-49 and PLAN-40 (the lane's R08 forbade it here).
- **`.plan/project-architecture/`** still holds a `pm-provider-ci` entry — regenerate it (derived state,
  outside the lane's boundary).
