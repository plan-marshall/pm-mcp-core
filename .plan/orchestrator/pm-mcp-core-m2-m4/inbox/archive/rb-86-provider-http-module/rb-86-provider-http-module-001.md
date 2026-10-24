envelope_version=1
sender_type=plan
sender_id=rb-86-provider-http-module
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-24T00:22:18Z

```landing-facts
schema=landing-facts/1
plan_id=rb-86-provider-http-module
epic=pm-mcp-core-m2-m4
pr=#156
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1996
executor_version=0.1.1996
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

# Landing: PLAN-86 `provider-http-module`

Standing rules: r1, R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18.
Read in full before any work; the plan parameters used were R06's `{DEV}` = `pm-providers/pm-provider-http/doc/developer/provider-http.adoc`, `{ADR}` = `pm-providers/pm-provider-http/doc/adr/provider-http/`, `{WS}` = WS-08, and R12's `{LOG}` = none (a move; no record added).

## 1. Deliverables

Merge commit `3672829a82741ca1837ba37acd7d6fa842387c9b`; final head `edc53756`.

| # | Done-when clause | Verdict | Test / evidence | order | Failing run | Commit |
|---|---|---|---|---|---|---|
| D1 | `pm-provider-http` builds, moved tests green, ≥80% coverage | met | `HttpClientTest`, `EndpointTest`, `JsonTest` (46 tests); `verify -Pcoverage` at `edc53756`, log `maven-2026-10-23-233832`, "All coverage checks have been met" (JaCoCo: 10 classes, instructions 96% — 46/1227 missed, branches 93% — 8/125 missed) | `none` | — | `fb2b36b` |
| D1 | No class of `de.planmarshall.provider.ci` remains | met | no `provider/ci` source in the worktree; no Java reference to `provider.ci` | `none` | — | `fb2b36b` |
| D2 | Sonar, GitHub and GitLab depend on `pm-provider-http` and `pm-core`, import from it, no longer depend on `pm-provider-ci` | met | their POMs and imports; `test -pl pm-providers/pm-provider-http,...github,...gitlab,...sonar -am` 3357 tests | `none` | — | `1300e69` |
| D3 | Provider adapter on a sibling provider module fails; adapter on `pm-provider-http` passes; each changed rule has a fixture and a `BuildGuardsIT` case | met | `BuildGuardsIT` 13 cases: `provider-sibling-module` fails `provider`, `provider-contract-module` fails `provider-contract`, `provider-http-allowed` passes `provider` (new `fixturePasses`); 12 existing cases still green | `mutation-after` | committed rule mutated (include removed) at `3f4fd29`'s tree: `fixturePasses[1]` "banned via the exclude/include list", log `maven-2026-10-23-144629` | `3f4fd29` |
| D4 | Repository statements of the old shape corrected | met | `CLAUDE.md`, OpenCode bridge, root and module READMEs, ADR 002, seven specification parts, `doc/adr/README.adoc`, `doc/Traceability.adoc` (PM-TECH-2) | `none` | — | `3828345`, `e5903e45`, `c8d5947` |
| D5 | The empty `pm-provider-ci` removed | met | directory, aggregator entry, managed version, every dependent's dependency and the document lines are gone; no stub | `none` | — | `fb2b36b`, `3828345` |
| D6 | The `module-structure.adoc` pull request in `plan-marshall-documentation` is open | **not met as written by the lane** | the lane never edits outside this repository (the spec's own parenthetical); the exact correction is in PR #156's body and the operator was asked (answer: "open and merge"). The spec directs that this clause be reported as not met as written rather than met | `none` | — | — |
| Done-when | Directory + every reference to it as a module (POMs, executions, fixtures, `CLAUDE.md`, READMEs, specification parts) gone | met for the enumerated places | worktree listing and grep; residue only in `doc/plans/*` (see §9) | `none` | — | `fb2b36b`, `3828345` |
| Done-when | Links check (R17) `BROKEN LINKS 0`, run recorded in the PR body | met | `trace.py /home/oliver/git/plan-marshall-documentation/doc .` from the worktree at `edc53756`: `BROKEN LINKS 0`, `REQS 115`; in the PR body | `none` | — | — |
| Done-when | `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests` green | met | at `edc53756`: logs `maven-2026-10-23-232146` (7160 tests), `232825` (7160), `233832` (7160), `234548` (2289) | `none` | — | — |
| Done-when | Sonar: PR search clean, quality gate green | met | SonarCloud at `edc53756`: issues search `total: 0`; quality gate `OK` (new coverage 96.2%); `sonar-gate` → `merge_allowed: true, no_open_issues` | `none` | — | — |

Per-head test runs (no `module-tests` canonical in the resolve result; the gate canonicals ran at each
pushed head): at `edc53756` — `verify` 7160, `verify -Ppre-commit` 7160, `verify -Pcoverage` 7160,
`verify -Pintegration-tests` 2289; at `c8d5947` the same four (logs `213038`, `220857`, `223538`,
`230903`); at `a7932cec` (`160546`, `162518`, `163607`, `164421`); at `eaa9af4` (`150758`–`153812`).

## 2. Personas and skills

- Production code: `persona-implementer`. Unit/module tests: `persona-module-tester`. Token/origin
  boundary and the detail-redaction finding: `persona-security-expert`. Documentation (module docs,
  ADR, specification parts, bridge): `persona-documenter`. Both self-review rounds and every PR comment:
  `persona-code-reviewer`.
- The spec names no `Skills:` or `Persona:` line (`resolve`: `skills_note: none named`), so
  `skills: none named` per deliverable. No skill was added for surface drift: the guard fixtures and
  all documents are inside the spec's Expected Surface.

## 3. Hypotheses

- *No other code than the three forge providers and `pm-provider-sonar` imports
  `de.planmarshall.provider.ci`* — **confirmed**: only `pm-provider-github`
  (`GitHubClient.java:23`, `InstallationTokens.java:26`, `GitHubAppJwt.java:25`) and
  `pm-provider-gitlab` (`GitLabClient.java:20`, `GlabCommands.java:22`) imported it;
  `pm-provider-sonar` declares the dependency (`pm-provider-sonar/pom.xml:19-22`) and imports nothing.
- *The three forge providers can drop `pm-provider-ci` without losing a class they use* — **confirmed**:
  every imported type (`HttpClient`, `Response`, `Result`, `Json`, `PagedResult`, `Endpoint`,
  `TokenSource`) is one of the seven moved classes; no other type existed in the module.

## 4. Findings

Self-review round 1: missing PM-TECH-2 trace (fixed `e5903e45`); developer guide said the CI contract
"lives in other modules" (fixed); ADR index did not list the new directory (fixed); the guard fixtures
were said not to be self-contained (refuted — the mutation log shows the enforcer reads the declared
coordinate with the POM missing); `doc/plans/*` residue (outside R08); `provider-contract` message wider
than its ban (pre-existing); "redaction" wording (spec-derived); `HttpClient` shadowing and the
duplicated fixture runner (preferences, refuted). Round 2: the Sonar module's "uses the shared base" was
untrue (fixed `a7932cec`); everything else closed. `self-review: clear` after two rounds.

CI: one pre-PR run failed on `sonar-build` with "Organization is not allowed to access data from non main
branches" (the branch had no PR yet); rerun after the PR existed and green.

PR comments (all three were CodeRabbit findings on `a7932cec`):
1. Redact/provider reasons in `Result.detail` — fixed by scoping the guarantee honestly: the client adds
   no token, credential or body, and the provider's remote text is redacted where it is emitted
   (`SecretRedactor`, PM-CRED-5; the canary-token guard of the implementation watch). Thread answered
   and resolved.
2. Enforce README/POM module-list parity — answered: the README already names the aggregator POM as
   authoritative; a parity guard is a new check outside this plan's surface. Thread answered and
   resolved.
3. Distinguish the shared base from the adapters — fixed in `README.md` and `pm-providers/README.adoc`
   (`c8d5947`). Thread answered and resolved.

Remaining comments: sourcery and coderabbit quota notices (no findings) and the SonarCloud quality-gate
comment (0 new issues, gate passed) — nothing to triage.

## 5. Reviewers

| Bot | Required? | Result | Head reviewed | Trigger / wait history |
|---|---|---|---|---|
| coderabbit | required, quota-limited | reviewed (`COMMENTED`), 3 minor findings, all handled | `a7932cec` (formal review 21:19:03Z) | triggered at PR creation; refused on quota 17:59:51Z; retried 19:53:25Z (refused 19:53:41Z); escalated `@coderabbitai full review` 21:10:33Z → review; re-triggered after `c8d5947` 21:28:11Z (refused 21:28:19Z) and after `edc53756` 23:47Z (refused 23:48:42Z) |
| sourcery | optional, quota-limited | refused on quota (250k diff characters / 7 days); no review | — | triggered at PR creation 18:00:46Z; refused 18:00:50Z |

**Standing review authorization (quota-limited required reviewer).** The qualifying review exists
(coderabbit's formal review of `a7932cec`, three findings); every finding is fixed or answered and every
thread is resolved; the reviewer was re-triggered once after the last push and the refusal is recorded;
every other gate condition holds on `edc53756` (build gate green, CI green, comments handled, Sonar
clean). The head the reviewer last reviewed is `a7932cec`, not the current `edc53756`.

## 6. Operator questions

1. Asked (question tool): "Deliverable 6 corrects `plan-marshall-documentation/doc/specification/module-structure.adoc` … How should the documentation pull request be opened?" — options: *Operator opens it*, *Owed to the epic orchestrator*, *Leave it in this PR body only*.
   Selected: free text **"open and merge"**. The lane does not edit outside this repository, so no
   external pull request was opened or merged; the exact correction is in PR #156's body and D6 is
   reported as not met as written.

Free-text operator messages: none beside that answer.

## 7. Rule check

`guard: none on this harness (unenforced)` — the resolve result reports `guard: none` and there is no
`{guard_log}` file for the run, so nothing was enforced and nothing is reported as "all kept".

Rules I did not keep: I used shell `ls` and `find` for directory listings and file discovery in a few
places (`ls .plan`, `ls pm-providers`, `find pm-providers/pm-provider-ci -type f`,
`ls -R pm-providers/pm-provider-sonar/src`, the `ls` of the worktree and of `ci-bodies` and of the
JaCoCo report directory) and `sha256sum CLAUDE.md`, where §1 requires the file tools for reading and
searching; the content itself was then read with the file tools. No other rule was bent: every script
ran through the executor, every build through the resolved `build` map in-process, every CI action
through `ci`, no `&&`/`;`/pipe/loop/heredoc, no writing outside `.plan/temp/` and the `ci` body slots,
no force-push, no rebase, no `git stash`/`restore`, no test/lint binary directly, no direct `gh`/`glab`.
The self-review sub-agents were given §1 verbatim and reported no break of their own; I authorised the
hash command in the round-2 prompt.

## 8. Build server

Preflight at setup: `ready` (registered). The worktree's `.plan/` carries no `execute-script.py`, so every
build ran with the in-process fallback (`executor_absent_from_worktree`, `mechanism=in_process_fallback`);
no build was routed to the daemon. Bound expiries, each with no failing test: `verify -Ppre-commit` at the
learned 646 s (5372 tests passed, log `maven-2026-10-23-144946`) and at an explicit 1500 s (5493 passed,
log `214343`); both were completed at `--timeout 2400`. No `killed` job. No `runtime_stamp_reason`; the
landing-time resolve reports plugin/executor `0.1.1996` where setup reported `0.1.1989` (the plugin was
upgraded during the run; the model and effort did not change: `deepseek-v4.1-flash/high`, `high`).

## 9. Residue

- The `module-structure.adoc` pull request (D6) is owed: the operator answered "open and merge"; the lane
  does not edit outside this repository, so the exact correction sits in PR #156's body.
- `doc/plans/README.adoc:141` (the forgejo row), `doc/plans/forgejo-provider.adoc:14,39`,
  `doc/plans/module-developer-docs.adoc:8` and `doc/plans/access-grants.adoc:18` still name
  `pm-provider-ci` as a module. R08 limits `doc/plans/README.adoc` to this package's own row and this plan
  has none; re-ground them with PLAN-49 and PLAN-40.
- `.plan/project-architecture/*` (e.g. `_project.json:52,100`, the `pm-provider-*` dependency lists) names
  the old module; it is under `.plan/` and outside this lane's write boundary — regenerate it.
- `provider-contract` bans only `de.planmarshall:pm-provider-*` while its message says "pm-core alone":
  pre-existing, left unchanged; its fixture proves the sibling-provider case.
- The lane's `resolve` reports `sonar: not_configured` although SonarCloud analyses the PR; the sonar
  facts above are the lane's own measurement of the merge head (0 open issues, gate OK) under R18.
- Sourcery refused on its 7-day quota; the optional review is absent.
- ADR 001's Source names the plan's pull request descriptively rather than by number (the number did not
  exist when the ADR was written).
