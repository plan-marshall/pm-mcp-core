# Landing Analysis: PLAN-V2-200 — the composed-vector check's parser limits

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #641 (https://github.com/plan-marshall/pm-mcp-core/pull/641), merge `11ec6b72` (squash through the merge queue, parent `bec253a3`; the lane's first pull request #638 was closed and replaced by #641 to re-request the required CodeRabbit review), analysed on the forty-third run, 2026-11-27

> Landing record. Source: the plan's inbox message `plan-v2-200-composed-vector-check-parser-limits-005` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics) with four candidate lessons (001 to 004), read against git (`git diff 11ec6b72^ 11ec6b72`: 13 files, +893/-95) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read L3-1, L3-2 and L3-8 at their lines (`WorkerVectorCheck.java:180-190, 281-351`, `04-worker-launch.adoc:49-50`, ADR 014 `:20-25`, the parent `toolSet`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 tests first | shipped; part of the presence tests are characterisation, not red | the earliest-literal, JSON-literal, duplicate-tool, residual-separator and adjacency cases are red on the parent tree (reasoned from `git show 11ec6b72^`); the 7 `Presence` cases are green on the parent (the vector and the absent set are hand-written, the composer's `fill` is never called: L3-6); the empty-member tool case is a control (the parent refused it) |
| D2 JSON element: the literals around the value are checked | met | `WorkerVectorCheck.java:180-210` (`jsonSpans`, exempt only the value; element 0 and the path slots unchanged) |
| D3 one approach, one ADR | met | ADR `runtime-launch/014`: adjacent slots refused at validation (`ProfileValidator.java:1414-1465`, called from `launchArguments`, env values exempt), a known value matched at the cursor, a slot bounded from both ends; the presence dependence stated, not removed |
| D4 documents | partly | ADR 010, ADR 014, the guide, `core-profile.adoc` done; `harness-profiles/02-placeholders.adoc` untouched, the rule went to `01-profile-schema.adoc:87` and `04-worker-launch.adoc` (a surface deviation, content present) |
| Done-when "fails at" with a run id | not shown by reading | the commit cites run `maven-2026-11-27-144453`; the leaf reasoned each case red or green |
| Done-when shipped-profile shapes conform | met | additions only in the test classes; `LaunchComposerTest` untouched |
| Done-when builds, coverage, integration | not shown by reading | the lane reports none locally for coverage; Sonar `0` open new issues, 94.3 percent coverage on new code (the lane's read) |

## Three-Layer Analysis

### Layer 1 — Specified

All four deliverables shipped. The title word "limits" means limits of correctness: the spec asks for no cap on input length, element count or slot count, and the code adds none (the inherent bounds are the validator's list cap of 128 and `SESSION_ID {1,128}`).

### Layer 2 — Implemented

Production: `WorkerVectorCheck` +211 (`match()` picks `bounded()` for an element with one slot, else `carried()`; `knownValue()` for `{{model}}` and `{{cwd}}`; per-element exempt spans for rule (d); a `HashSet` and a duplicate refusal for `{{tools}}`), `ProfileValidator` +37 (`noAdjacentSlots`). Tests +437 lines: 20 methods, 38 invocations; about 14 red on the parent tree. Five paths outside the Expected Surface (all repository-rule documents: Traceability, the watch file, `01-profile-schema.adoc`, `04-worker-launch.adoc`, `core-profile.adoc`).

### Layer 3 — Correct

No major defect. The code is correct for every case the leaf constructed; every lane declaration checked out; the residual is WORSE than stated. Re-read at the lines: L3-1, L3-2 and L3-8 hold.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `04-worker-launch.adoc:49-50`; `runtime-launch.adoc:151,236`; `Traceability.adoc:1249,2354`; ADR 010 `:43` | **re-read:** the code exempts element 0 (`:184-187`, the loop starts at 1, comment "the specification's standing constraint"); the rule text says "no character of the vector outside the value of a JSON slot" with no exemption | text and code disagree | minor |
| L3-2 | ADR 014 `:23-24, :82-83` | **re-read:** says an empty `{{tools}}` member was accepted before; the parent already had `tool.isEmpty() ||` (`git show 11ec6b72^`, line 339); only the duplicate is new | a false sentence in an ADR | minor |
| L3-3 | `runtime-launch.adoc:132-134` | the example `{{tools}}_{{session_id}}` (`read_bad_abc`) is about a path-typed slot but its first slot is the tools grammar; it shows no fault; every other place uses `{{prompt_file}}-{{result_schema}}` | misleading example | minor |
| L3-4 | `WorkerVectorCheckTest` `KnownValues` | single-slot elements are served by `bounded()`; the `knownValue()` branch of `carried()` is reached only through a last slot; replacing it by the old `indexOf` changes no test | gap | minor |
| L3-5 | `WorkerVectorCheck.java:338-340` | no test of the adjacent-slot guard inside `carried()`, which the ADR and the guide call fail-closed | gap | minor |
| L3-6 | `WorkerVectorCheckTest` `Presence`; `keptIndices` `:226` vs `LaunchComposer.java:447` | the presence tests do not call `fill`, though four documents say they bind the two; `keptIndices` tests `isFlag` on the template element, `fill` on the rendered element | a change of `fill` alone fails nothing; a template element rendering to `--x` before an absent slot would be refused falsely | minor |
| L3-8 | `WorkerVectorCheck.java:341-350` | **re-read:** `indexOf` of the next literal from the cursor ends the first value at the earliest occurrence; with the job directory `/opt/pm/run/jobs/gen-1` the element `/opt/pm/run/jobs/gen-1/prompt.md-/opt/pm/run/jobs/gen-1/result-schema.json` is cut at the `-` of `gen-1` and refused by rule (c); `{{result_schema}}-{{prompt_file}}` is cut at the `-` of `result-schema.json` and refused in a clean directory; a wrong split can also be accepted; no test shows the wrong accept; the commit message "fail-closed" is wrong (the docs do not say it) | a conforming launch can be refused (the lane stated only the wrong accept); no shipped profile has the shape | minor |
| L3-7, L3-9 to L3-11 | `ProfileValidator.java:1403-1406`; `04-worker-launch.adoc:162`; `01-profile-schema.adoc:87` | the adjacency refusal reaches `zero_tool.args`, `weights_probe.argv`, `version_probe` and the probe lists which never enter the vector, the text lists fewer; two rule letters for one fault; "names each tool of the set once" reads as completeness; no watch back-link | wording and reach | info |
| clean | | no regex over values (no backtracking), literals compared with `startsWith`/`endsWith`/`indexOf`, the overlap guard of `bounded()`, `split(",", -1)`, rule order and messages naming rule and position never the value, adjacency by `start == previousEnd` | | clean |

**Layer 3 verdict:** findings — 11 (seven minor, four info); not shown by reading — builds, coverage, integration, Sonar, the cited run, the operator's R07 answers (cited in ADR 014 as "the operator's answers of 2026-11-27"), the shipped profiles of `plan-marshall-mcp`. No major finding, nothing reported at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | no Mockito, assertions with a message of 20 to 60 characters, test data from generators | conforms | the leaf read the new classes |
| CLAUDE.md Module Documentation | an ADR per decision, developer documentation | partly | ADR 014 and the guide exist; they carry L3-1 to L3-3 |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `11ec6b72` |

## Metrics and Anomalies

- Harness claude, model `claude-sonnet-5-5`, effort medium, plugin `0.1.2184`, executor `0.1.2184`; `total_tokens=19245117`, `total_wall_seconds=45455` (12 h 38 min); finalize used 54 percent of the tokens. `complete: false` for four keys, all unobserved facts recorded `unknown`, never 0: `cleanup_owed` (the lane observed the worktree and the branches removed), `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`.
- The reviewer quota: CodeRabbit refused on its free quota; automatic-review fired six times (four loop-backs spent only waiting, one operator grant "Give it one more round"); close-and-reopen replaced #638 by #641; the five findings filed on #638 were skipped on #641 and got no reply on GitHub (lesson 002).
- A first sonar-roundtrip failed (`analysis_revision_unavailable`): the CI verdict cached from #638 was applied to #641; one retry confirmed 0 (lesson 003).
- `pr` recorded by `create-pr` was #638; the merged pull request is #641.

## Routing and Merge Behavior

- Merge: through the merge queue as `11ec6b72`.
- Four candidate lessons (001 `automatic-review` quota waits charged to the loop-back budget, 002 pending findings of a PR closed by close-and-reopen, 003 `workflow-integration-sonar` waiting for the analysis of a new head, 004 `phase-4-plan` Step 8 `qgate add --type warning`) forwarded verbatim to the plan-marshall `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-075` to `-078` (uncommitted in that ledger until its land).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#641`; `landing` `landings/PLAN-V2-200.md`; `plan_marshall_plan_id` `plan-v2-200-composed-vector-check-parser-limits`
- [x] the five messages archived (the four lessons forwarded first)
- [x] all minor and info points staged as deliverables 1 to 3 of the NEW PLAN-V2-214 (the one leftovers plan, which also takes the wording residue of PLAN-V2-187 and the two points of PLAN-V2-201 the operator carried to it); the residual's code change is an R07 ask-at-refine question
- [x] the Decisions entry and the Open Defects written; resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** PLAN-V2-214 deliverables 1 to 3 (and 4, 5 for the carried points).
- **Owed:** the shipped profiles of `plan-marshall-mcp` against the adjacency refusal (not checkable from this repository).
