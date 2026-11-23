envelope_version=1
sender_type=plan
sender_id=rb-v2-175-links-check-requirement-removed
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-23T19:59:11Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-175-links-check-requirement-removed
epic=pm-mcp-core-m2-m4-v2
pr=#582
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
step.sonar-roundtrip.quality_gate_status=unknown
```

guard: none on this harness (unenforced)

## Deliverables

Spec: `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-175-links-check-requirement-removed.md`.
Standing rules read in full: **r7**, rules R02, R05, R08, R10, R13, R18, R19 binding; R17 (amended 2026-11-23) is the rule this plan retires.

**D1 — `CLAUDE.md`, section "Project" — met.**
The clause naming `trace.py`, `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` and its "runs locally before every merge" is gone; the sentence it ended ("Links here are relative, also into the documentation (sibling paths).") stands and the paragraph still reads as one (`CLAUDE.md:13-28`). Nothing else in the file changed.

- test: the file-search check for `BROKEN LINKS|INLINE SCHEMA BLOCKS` over `CLAUDE.md`, `.opencode`, `.claude`.
- order: `test-first`.
- failing run: `fc15f7ce`, output identifier `check-before.md` (the two hits: `CLAUDE.md:30` and `.opencode/rules/plan-marshall-standing-rules.md:37`); its content is in the PR body and in `.plan/temp/rb-v2-175-links-check-requirement-removed/check-before.md`.
- passed at `9ff32dec`; commit `9ff32dec`.

**D2 — `.opencode/rules/plan-marshall-standing-rules.md` — met.**
The same sentence removed the same way; the file is otherwise unchanged (including its pre-existing `source-sha256` pin, which the deliverable froze).

- test: the same check; zero hits in all three paths after the change; `.claude/skills/doc-review/` stays (a tool mention, no match).
- order: `test-first`.
- failing run: `fc15f7ce`, output identifier `check-before.md` (`.opencode/rules/plan-marshall-standing-rules.md:37`).
- passed at `c621b94e`; commit `c621b94e`.

**Done-when clauses beyond the two deliverables.**
The clause "the pull request body lists the remaining hits of the grep of deliverable 2 with a decision for each" — met, order `none`: the PR body carries the table (zero hits in the three paths, every statement outside them with a decision). The clause "`verify -Ppre-commit` is not needed… the plan's own check is the diff: two files, a few lines" — met in substance, order `none`: no Java and no resource is touched; the diff grew to four files only through the operator's extension below. The lane's own build gate was run anyway (see Build).

**D3 — operator extension (not a spec deliverable).**
Asked because both reviewers and my own self-review found the same three statements outside the declared surface, the operator selected "Extend this PR to the three doc/ lines". Fixed: `doc/Specification.adoc:17` (the tool can be run; no workflow runs it; no pull request of this repository is required to run or report it), `doc/adr/007…:6` (Status amended: the pre-merge requirement left on 2026-11-23) and `doc/adr/007…:52-53` (superseded note). A second reviewer finding then made `doc/adr/007…:76` false — the Consequences bullet claiming a `links` check fails here — and it was reworded at `c779dc97`. Commits `d9bb4b1c`, `c779dc97`.

**Per-head test runs (every pushed head).** `c621b94e` 12 748 tests, `d9bb4b1c` 12 748, `2377b472` 12 763, `c779dc97` 12 763 — all green.

## Personas and skills

- D1, D2: personas per the spec (`Persona: documenter`) → `persona-documenter`; skills `pm-documents:ref-asciidoc`, `pm-documents:ref-documentation`, loaded as standards.
- Self-review rounds and PR-comment triage: `persona-code-reviewer` (both sub-agent dispatches and my own triage).
- Skills added for surface drift: none by module. The operator's extension touched `doc/Specification.adoc` and `doc/adr/007…` — covered by the same two documentation skills; `pm-documents:ref-asciidoc`'s validator ran on both `.adoc` files.

## Hypotheses and claim labels

- OBSERVED (both files carry the sentence at head): **confirmed**. At `fc15f7ce` the sentence stood in `CLAUDE.md:28-30` and `.opencode/rules/plan-marshall-standing-rules.md:35-37`; the spec cited `CLAUDE.md:30-32`, two lines off after the ledger landings, with identical text.
- OBSERVED ("`git grep` over `origin/main` finds no other statement of it outside the archived ledger and the traceability tool's own documentation"): **refuted as written, true only for the literal tokens**. Three prose statements in `doc/` said the check runs before every merge (`doc/Specification.adoc:17`, `doc/adr/007…:6,52-53`); they were fixed on the operator's word. The tracked ledger holds many more, including two **staged** specs that still ask it textually (see Residue).
- LEAD (unverified): `plan-marshall-documentation` states the requirement as well — **not read**. The lane keeps every read inside this repository root, so the lead stays unverified and is handed over as owed.

## Findings

Self-review round 1 (read-only `persona-code-reviewer` sub-agent, pre-PR):

1. Done-when clause 3 not evidenced — the PR did not exist yet; closed by the PR body.
2. Three `doc/` statements of the removed requirement (Medium) — real drift; escalated to the operator, who selected the extension; fixed in `d9bb4b1c`.
3. The build claim carried no log identifier — fixed (`14c518da914f44bd9c674475787f1196.log`).
4. The `.opencode` bridge's `source-sha256` pin is stale (Low) — pre-existing (PLAN-V2-163 `e8a585f3` changed the baseline); D2 freezes that file, so reported, not re-pinned.
   Verdict after round 1: `open items: 2` (1 and 2), both closed.

Self-review round 2 (on the merged content, `fc15f7ce..7259b039`, the four files): `open items: 4`, all reported below; none is fixable inside this lane after the merge.

CI: green on every head — `c621b94e`, `d9bb4b1c`, `2377b472`, `c779dc97` (18 checks each, 0 failing).

PR comments (all triaged with `persona-code-reviewer`):

| Reviewer | Finding | Disposition |
|---|---|---|
| sourcery-ai | inline 1 "Documentation check guidance conflicts" (Medium) | fixed at `d9bb4b1c` after the operator's extension |
| sourcery-ai | inline 2 "Merge requirements conflict" (Medium) | fixed at `d9bb4b1c` |
| sourcery-ai | inline 3 "Repository links go unchecked" (High) | refuted — the removal is the operator's decision of 2026-11-23; stated reason, thread resolved |
| sourcery-ai | inline 4 "Broken documentation checks reach main" (Medium, at `d9bb4b1c`) | refuted, same reason; thread resolved |
| coderabbit | outside-diff-1 — "Remove the obsolete core pre-merge requirement from the specification" (`doc/Specification.adoc:17`) | fixed at `d9bb4b1c` |
| coderabbit | outside-diff-2 — "Remove the `links` check claim at line 76; no link check runs in this repository" | fixed at `c779dc97` |

## Reviewers

**coderabbit (required; `quota_limited`, `review_complete_after_handling`).** Triggered 2026-11-23T13:53:05Z on `c621b94e` → **refused on quota** 13:53:13Z ("Review limit reached… next included review in 7 minutes"). Per § 6 step 7 the next attempt waited the full 90 minutes: re-triggered 15:36:14Z → refused 15:36:25Z; re-triggered 17:07:44Z → "Review triggered", review submitted **17:10:36Z on `c621b94e`** (formal review plus the `final_review_risk_coverage` marker, `sourceCommitId = coveredCommitId = c621b94e`, `kind=reviewed`). It then reviewed the merged head **18:35:44Z on `2377b472`** and raised outside-diff-2.
Standing authorization: the reviewer is quota-limited and marked `review_complete_after_handling`, and a qualifying review is on record, so no re-trigger was posted for `c779dc97`; both of its findings are handled. The reviewer's own latest head is `2377b472`; the merged head is `c779dc97`.

**sourcery (optional).** Reviewed `c621b94e` (13:55:27Z, 14:00:37Z) and `d9bb4b1c` (17:56:47Z); 4 threads, all answered and resolved; silent at `2377b472` and `c779dc97`; the check run itself reported success on every head. Not gating.

## Operator questions

1. "How should the three doc/ statements be handled?" — options: {keep the surface and hand them to a follow-up, extend this PR to the three doc/ lines}. **Selected: "Extend this PR to the three doc/ lines".**
2. "How should the Sonar condition be decided?" — options: {merge and record the Sonar read as unknown, stop here and leave the PR open}. **Selected: "Merge and record the Sonar read as unknown".**
3. "Merge `c779dc97`?" (the reach rule: the head carried `main`'s production changes from the base merge, so the earlier selection did not carry over) — options: {merge `c779dc97` with Sonar recorded as unknown, stop here and leave the PR open}. **Selected: "Merge `c779dc97`, Sonar recorded as unknown".**

No free-text operator messages were used as a decision.

## Rule check

- guard: none on this harness (unenforced).
- **Spec read:** `spec_source=ledger_worktree`; no `spec_differ` marker — the ledger copy was the one read and the main-checkout copy said the same.
- **executor-skew:** `match` — `plan-marshall:runbook-lane:runbook_lane` 0.1.2183 against executor 0.1.2183 (the worktree executor produced at creation reports 0.1.2184).
- **Deliberate deviation (one):** the spec's `git grep -n -i …` was run with the file-search tool over the same three paths and the same pattern, because § 1 forbids git's `grep` subcommand. Stated in the PR body.
- **Surface/naming error (one):** `ci pr comments --output-format toon-summary` was rejected by argparse (`unrecognized arguments`); I did not change source, and retried with the documented surface.
- Shell: every script through `python3 .plan/execute-script.py` from the main checkout; every build through the resolved `build` commands; every CI action through `plan-marshall:tools-integration-ci:ci`. No `git stash`, `restore`, `checkout -- <path>`, `rebase` or forced push; no hard-coded build wrapper; no `sleep` (the 90-minute quota wait used `ci pr wait-for-comments` in bounded calls).
- Reads outside the repository root: none; the documentation repository was not opened.
- Base-evidence worktree: not needed (no Done-when says "fails at `{sha}`").
- Restarts, killed sub-agents, re-dispatches: none. Two self-review dispatches, both read-only.

## Build server

- Setup preflight: **ready** (project enrolled).
- Builds: **4, all `daemon_longpoll`** — `c621b94e` (222 s, log `14c518da914f44bd9c674475787f1196.log`), `d9bb4b1c` (221 s, `ab29d941ac6c4cc3a7c653da16aebff5.log`), `2377b472` (219 s, `fa5ac5dbee4c43b193a6668fcaa9381e.log`), `c779dc97` (210 s, `62db81f8224846f580f0e41c3bdcb546.log`). No `in_process_fallback`, no degraded result, no launch-fault retry, no queued-behind wait beyond `queued_seconds: 0`, no `killed` job.
- Runtime stamp: `plugin_version` is `unknown` (the resolve field); no `runtime_stamp_reason` was carried.
- Build classifier: `buildable` (source `architecture`) — `CLAUDE.md` and `.opencode/rules/` are outside the documentation module's prefix, so the fail-closed direction ran the gate on a docs-only change; the spec's own Done-when says the build was not needed.

## Residue

1. **Surface delta (R19).** The merged diff is four files, not the two the spec's Expected Surface declares: `doc/Specification.adoc` and `doc/adr/007-specifications-of-the-engine-in-this-repository.adoc` were added on the operator's word (question 1) and are reported here as undeclared-but-authorised.
2. **The spec's premise is incomplete.** `PLAN-V2-175:27` ("the two places of this repository") and `:55` ("no other statement of it") do not hold. Three `doc/` statements existed (fixed in this PR); the ledger holds many more.
3. **Two staged specs still ask the check textually.** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-125-…:55` and `PLAN-V2-126-…:52` still say "**Links:** run the links check (R17) …", although `standing-rules.md:115` says "the staged ones were corrected the same day" and `epic.md:211` lists only 135, 147, 150, 161 and 168. R17's void clause covers them; the text does not.
4. **`owner-register.md:56` (Wa76)** says in the present tense that "`CLAUDE.md:30` names an `INLINE SCHEMA BLOCKS 0` that is unreachable" — after this merge `CLAUDE.md` names it nowhere.
5. **`doc/adr/README.adoc:34`** lists ADR 007 as amended by 016, 027 and "the documentation's local link check", not by the 2026-11-23 removal now recorded in the ADR's Status. Needs a follow-up pull request (this one is merged).
6. **`.opencode/rules/plan-marshall-standing-rules.md:4`** carries a `source-sha256` that predates two earlier `CLAUDE.md` edits (last baseline change `e8a585f3`, PLAN-V2-163); a bridge re-emission refreshes it with the body.
7. **Owed outside this repository.** `plan-marshall-documentation` may restate the requirement (`doc/developer/project-setup.adoc` and the shared specifications) — not read here (lane read boundary), no pull request opened.
8. **Sonar.** The merge condition could not be met and was decided by the operator (question 2 then 3): the read was `undecidable` (`analysis_revision_unavailable`) at every head because CI classifies the change docs-only and skips `build / sonar-build`, so no analysis of PR 582 exists; `gate-status` answered HTTP 404. The landing records `unknown` at both Sonar facts rather than a `0`.

Cleanup: worktree removed clean, branch pruned locally and remotely, `main` switched and pulled to the merge commit `7259b039`. `cleanup_owed=false`.
