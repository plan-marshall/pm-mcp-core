envelope_version=1
sender_type=plan
sender_id=rb-v2-122-adoc-reading-retired
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-16T22:01:11Z
revision=1
amended=2026-11-16T22:02:38Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-122-adoc-reading-retired
epic=pm-mcp-core-m2-m4-v2
pr=#468
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-122 `adoc-reading-retired` — landing

Standing rules: **r7**, R01, R02, R05, R06, R08, R11, R17, R18, R19, R20 (the spec names r6; r7's revision log says the
staged specs pick r7 up when they start). PR
https://github.com/plan-marshall/pm-mcp-core/pull/468, merge commit `43c33c90`.

## Deliverables

| # | Deliverable | Done-when | Verdict | Order | Evidence | Commit |
|---|---|---|---|---|---|---|
| D1 | Delete `SpecificationDocument` and `pm.specification.root` | helper gone; property declaration and Surefire pass-through gone; `..` fallbacks | met | none | helper deleted; `pm-core/pom.xml` carries only `pm.product.root`; search at `72d698b9` found no test naming the property and no `..` fallback for it, so that clause had nothing to delete | `5a6dc924` |
| D2 | Shrink `maven-build.paths-code-extra` | `doc/log-messages/*.adoc` + `**/src/**` | met | none | `.github/project.yml`; the three listing files exist | `8a4d3775` |
| D3 | The ADR and the index | ADR 016 states the retirement; ADR 009 and the ADR 015 entry marked superseded | met | none | ADR 016 Consequences, ADR 009/015 statuses, ADR index rows 009 and 015, ADR 019's consequence | `f4c8e589`, `53f5234b`, `4971cc19`, `6b22f278` |
| D4 | Verify by search | no test source opens a path below `doc/` | met | none | `.plan/temp/rb-v2-122-adoc-reading-retired/search-before.txt` and `search-after.txt`; the only remaining reader is the sanctioned byte check of the three listings | — |
| D5 | Documents here | the project-setup sentences checked | met | none | `.plan/temp/rb-v2-122-adoc-reading-retired/project-setup-check.txt`: `project-setup.adoc:81` and `:97` quoted from the sibling `4dd67ea`; the document carries no sentence naming `skip-on-docs-only`; no sibling PR needed | — |

Done When bullets:

- **Bullet 1 (the search finds no reader; `verify` green in every module) — met, order `none`.** `search-after.txt`;
  local `verify` at `f4c8e589` executed 10765 tests with all 11 reactor modules `SUCCESS` and `BUILD SUCCESS`
  (`.plan/local/plans/NO_PLAN/build-results/default/maven-2026-11-16-180638.log:3934`), and at the final merged head
  `18cb0f09` it executed 10815 tests green (job `cb57146e60b64511be6cf45edcc753ad`).
- **Bullet 2 (a `doc/specification`-only pull request skips the build; the first such PR is the observation) — not met
  as written, by construction.** It is a post-merge observation, as the package file and ADR 015 say for their own
  change; it is carried here as an unverified expectation and owes a follow-up observation to the first such PR.

Per-head test runs: `test` and `test-compile` at `f4c8e589` (working-tree content), `53f5234b`, `4971cc19`,
`6b22f278` and the merged head `18cb0f09`. R01's four profiles at the merged head: `verify -Ppre-commit`, `verify`,
`verify -Pcoverage` and `verify -Pintegration-tests` (`-pl pm-core -am`: 21 tests; `-pl pm-runtime -am`: 43 tests), all
green.

## Personas and skills

- Personas applied: `persona-implementer` for D1/D2 (production/test deletion and configuration), `persona-documenter`
  for D3/D5 (ADRs and the project-setup check), `persona-code-reviewer` for all three self-review rounds and for
  triaging every PR comment.
- Skills named by the spec's deliverable rows: none (`skills: none named`).
- Read as R20 standards: `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `junit-integration`
  (`pm-dev-java`) and `cui-logging`, `cui-testing`, `cui-http-testing` (`pm-dev-java-cui`), plus the project skill
  `traced-implementation`. The change touches Java only by deleting a test helper; no skill added for surface drift.

## Hypotheses

- The spec's Deliverable 1 clause "the `..` fallback of every test that read the property" — **refuted**: at
  `72d698b9` no test names `pm.specification.root` and no test carries a `..` fallback for it
  (`pm-core/pom.xml:22-23,112` held the declaration and the pass-through, nothing else). The clause is answered by the
  search, not by a deletion.
- OBSERVED claims of the spec (ADR 015 on main with the include list; `paths-code-extra` in `.github/project.yml`) —
  **confirmed** at `72d698b9`.

## Findings

- Self-review round 1: 2 medium (Done-when 2 unrecorded; D5 not evidenced) and 7 low, all disposed — the two medium
  are closed by the progress record, the evidence file and this landing; the low preferences were fixed or left with
  the repository's amended-status precedent. Round 2: one low evidence nit, fixed. Round 3: `nothing open`.
- Sourcery inline 1 (ADRs misstate the readers): fixed in `4971cc19`, replied, resolved.
- Sourcery inline 2 (ADR 019 narrowing described as pending): fixed in `6b22f278`, replied, resolved.
- CodeRabbit inline 1 (the include list duplicates reader locations): **answered with the reason, not changed** — the
  one-glob list is Deliverable 2's operator decision of 2026-11-08, the reusable workflow accepts globs only, and
  ADR 015's scan alternative 3 was rejected by the operator; replied and resolved.

## Reviewers

- **coderabbit** (required): triggered 18:51, refused on quota ("Review limit reached"). Policy wait 90 minutes
  (next allowed `20:21:32Z`), re-triggered 20:47, reviewed `4971cc19` at 20:53 with one Minor finding
  (`final_review_risk_coverage` marker: `coveredCommitId 4971cc19`, `kind=reviewed`). `review_complete_after_handling`
  applies once every finding is handled, so its review covers the later heads and it was not re-triggered.
- **sourcery** (optional): triggered 18:52, approved twice on `53f5234b`, reviewed `4971cc19` with one finding (fixed),
  re-triggered after the merge push at 21:44 and refused on its own review budget (250k diff characters, 2h19m);
  optional, so no gate impact.

## Operator questions

none asked.

## Rule check

- `executor-skew`: **match** (executor and lane text `0.1.2135`). Spec read: `spec_source: ledger_worktree`, no
  `spec_differ` marker.
- guard: **none on this harness (unenforced)** — no guard-log line exists to read, and the run does not claim one.
- Deviations, each deliberate and named:
  - R17's links check ran as the standing rule spells it, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …`, not through the executor (`BROKEN LINKS 0`).
  - R01's `-Pcoverage` and `-Pintegration-tests` lanes ran as extra arguments to the resolved `build-maven:maven` verb,
    because the resolve map publishes only `verify`, `verify -Ppre-commit`, `test` and `test-compile` and R01 names four
    profiles.
  - `module-tests` hit the daemon's learned 367 s bound once; re-run with `--timeout 900` (raised bound), recorded.
  - The pre-commit profile inserted one blank line into `pm-workflow/.../LandingVariantValidationTest.java`; it was
    reverted with the file tools (never `git checkout`) before the push, and the tree is clean.
  - The work package file was deleted by an early `git rm`, so its deletion landed in the D1 commit and the index row
    removal in D2; content is as R01/R08 require.
- Surface delta (R19): besides the declared files, the change corrects `pm-core/README.adoc` and
  `pm-core/doc/developer/core-store.adoc` (false after D1) and edits `doc/adr/016` (required by D3) and
  `doc/adr/019` (a reviewer finding); the spec's Expected Surface names ADR 009, 015 and the index but not 016/019 —
  a spec gap worth a refine, not an expansion of scope.

## Build server

Setup preflight `ready`. Every build was routed (`mechanism=daemon_longpoll`, 15 jobs); no launch-fault retry and no
`killed` job. One bound expiry (the `test` run above). Builds blocked in the daemon queue for minutes at a time
(machine-global slots held by other plans); each still ran in the same call tree.

## Residue

- Done When bullet 2 is owed to the first `doc/specification`-only pull request after this merge.
- The spec's Expected Surface omits `doc/adr/016` (D3 requires it) and `doc/adr/019` (a review finding); a refine
  should add both.
- ADR 009's Context keeps its historical premise in the present tense; its status names the retirement, in the
  repository's amended-status precedent.
- Sourcery is over its 7-day review budget until about 00:04Z; optional.
- `inbox landing-check` reports `complete: false`, naming `total_tokens` and `plugin_version`. No surface measured
  them for this run: the `resolve` runtime stamp and `platform-runtime runtime-info` report no bundle version,
  the harness has no transcripts and the lane keeps no token meter. The lane's value rule forbids a guess at a
  required key, and the checker's COULD-NOT-READ class rejects `unknown` at every key, so the two keys stand as
  visible gaps rather than as invented facts.
- `trace.py` reports pre-existing specification findings (3 unimplemented inline schema blocks, 3 traceability
  mismatches, watch gaps) that this change does not touch.
