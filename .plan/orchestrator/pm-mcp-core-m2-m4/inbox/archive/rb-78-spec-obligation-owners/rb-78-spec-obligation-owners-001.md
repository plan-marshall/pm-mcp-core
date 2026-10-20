envelope_version=1
sender_type=plan
sender_id=rb-78-spec-obligation-owners
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-20T07:59:19Z

```landing-facts
schema=landing-facts/1
plan_id=rb-78-spec-obligation-owners
epic=pm-mcp-core-m2-m4
pr=#111
merge_state=merged
cleanup_owed=false
deliverables_total=15
deliverables_done=14
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced)

## 1. Deliverables

All paths are `doc/plans/`. The plan changes documents only; its tests are the checks in its Test matrix.

| # | Result | Test / check | order | Failing run | Commit |
|---|---|---|---|---|---|
| D1 | met | anchor + Done When checks for Seatbelt/egress | test-first | 993a7e7, grep of `macos-confinement` over doc/plans → `No matches found` | f176495 |
| D2 | met | git-identity anchor + Done When (ambiguous, abbreviated, two refs, fetch/checkout/reset/rebase/worktree) | test-first | 993a7e7, `git-identities` absent | f176495 |
| D3 | met | update-evidence/role-classes/profile-verification anchors + stage checks | test-first | 993a7e7, `update-evidence` absent | 78ecb61 |
| D4 | met | writing-skill/prefix/isolation/refusal checks | test-first | 993a7e7, `job-prompt` absent | 5794f25 |
| D5 | met | `agent_started` round-trip and action refusal | test-first | 993a7e7, `agent_started` absent | 7dabd8c |
| D6 | met | process-tree detection, impostor binary, fixed threshold | test-first | 993a7e7, `worker-supervision` absent | 564c0d3 |
| D7 | met | four navigation figures + skill residency, shown in the completion report | test-first | 993a7e7, `navigation-figures` absent | 3e1c1cd |
| D8 | met | comparison footprint checks | test-first | 993a7e7, `comparison-footprint` absent | fa0501f |
| D9 | met | review-simplify bullet (clone boundaries, `declined`, `settle-commit`); attestation Done When (`release_unattested`, archive mismatch, `sha256` shown only); truncation listed as No owner found | test-first | 993a7e7, `finalize-step-bands` not in this package; truncation absent everywhere | 50a9c39 |
| D10 | met | Control First / Combined Pressure / Read Matches + nine fields + operator decision | test-first | 993a7e7, `behavioural-conformance-harness` absent | fa0501f |
| D11 | met | intent record, outline record, #97/#98 parts, epic intent, revision that changes intent, skill residency; split | test-first | 993a7e7, `intent-record-store` absent | 50a9c39, 20ad1ab, 8045066, 3e1c1cd |
| D12 | met as disposition | listed under Owed to a staged plan (PLAN-84); no file expected | none | — | — |
| D13 | **not met as written** | band held (4–10 bullets); no obligation bullet removed or relaxed; two pre-existing § What cross-references re-pointed by the operator-authorised split (`epic-resource-and-release.adoc:17`, `chat.adoc:17`), and six review bullets moved verbatim | test-first | 993a7e7, the obligations absent | 8045066, a254f95 |
| D14.1/14.2/14.3 | met | per-topic agreement bullet + Done When; 14.2 (PLAN-86) and 14.3 (five items, two drafts) listed | test-first / none | 993a7e7, `qualification-topics` absent | c14e1a9 |
| D15 | met | constraint/non-goal bullet and Done When; (4) role-skill wording and (5) corpus item listed as Owed outside (`plan-marshall-mcp`) | test-first | 993a7e7, `PM-WATCH-PHASE-110` absent | 50a9c39 |

`test-compile` run on the pushed head: CI's `build / conclusion` and `integration-tests / conclusion` ran on a80da5f (success); the docs-only gate replaced the local Maven builds (see §7).

## 2. Personas and skills

- Production code: none (documentation only).
- Self-review rounds 1–3: `persona-code-reviewer` (loaded as a standard in the sub-agent).
- Documentation work: `persona-documenter` was not separately loaded; the spec names no `Persona:` or `Skills:` lines, so the kind-of-work table selected none. `skills: none named`.
- No surface drift; no skill added.

## 3. Hypotheses

- H1 "each proposed owner is the package whose _Where_ the obligation fits" — **confirmed** for D3 (`core.profile`), D4 (`runtime.launch`/`runtime.security`), D5 (`core.queue`/`workflow.task`), D6 (`runtime.worker`), D7 (`worker-activity` journal + `operator-reports` completion report), D8/D10 (`workflow.scenario`), D14.1 (`core.role`); **refuted** for D2 (`pm-provider-git` has no package; the operator moved it to `job-confinement-and-environment`) and for D11's `epic-decomposition` (at the 12-bullet limit; split by operator decision).
- H2 "`job-confinement-and-environment` can take Seatbelt, the egress proxy and the git identity check within 12 bullets" — **confirmed**: 6→8.
- Known gap: `model-work/09`'s "most expensive turns" and "largest tool results" are not named by D7 and remain unowned (listed under No owner found in the PR body).

## 4. Findings

- Self-review round 1: 15 findings; fixed the split's stale `chat.adoc` reference, restored the review test assertion, corrected the `_Implements_` wording, quoted Control First verbatim, separated the attestation refusal from the archive mismatch, named the role-skill wording, and added Done When checks. Open at the time: body/landing evidence.
- Self-review round 2: 12 items; fixed the D15 (4)/(5) double disposition, `agent_started` ownership in three files, `^{commit}` source, README/cross-reference records; added the remaining checks.
- Self-review round 3: verified those fixes, closed the count/T3/row-ending/draft gaps; the only open items left were the PR body and this landing (written now). The lane's 3-round limit is reached, so self-review is reported `budget-exhausted`, not clear.
- CI: 31 checks, 0 failing on a80da5f; CodeRabbit skipped by the `skip-bot-review` label, Sourcery skipped the review and posted a quota comment (optional bot; no code finding). No unresolved threads.

## 5. Reviewers

| Bot | Required | Result | Head reviewed | History |
|---|---|---|---|---|
| cuioss-review-bot | yes | silent | — | labelled `skip-bot-review` per the plan's standing suppression; never triggered |
| coderabbit | yes | refused (label-skip) | — | its own comment: "Review skipped — only excluded labels are configured: skip-bot-review"; never triggered |
| sourcery | no | refused (quota) | — | quota comment: budget used, "request another review in 2 days and 15 hours"; not re-triggered |

Standing authorization: not applicable (no quota-limited required reviewer, and no qualifying review exists). The operator selected "Merge without the reviews (rule-bending)" after a question that named the exact gap: no required reviewer reviewed a80da5f.

## 6. Operator questions

1. "May `../plan-marshall-documentation` be read read-only and `trace.py` run?" — options yes / no; selected **Yes, read-only and run trace.py**.
2. "`epic-decomposition` is at 12 bullets; how to handle D11's epic-intent obligation?" — options Needs a split / Move to phase-sub-workflows / Split epic-decomposition; selected **Split epic-decomposition**.
3. "D2 git identity owner?" — options No owner found / Add to job-confinement-and-environment; selected **Add to job-confinement-and-environment**.
4. "D9 truncation rule owner?" — options No owner found / process-and-overview / operator-reports; selected **List under No owner found**.
5. "`build-classify` is missing from the resolved executor (unknown_verb); how to handle the build gate?" — options docs-only by inspection / run the Maven gate anyway / stop; selected **Docs-only by inspection**.
6. "Self-review is at its 3-round limit; how to proceed?" — options proceed as budget-exhausted / run a fourth round; selected **Proceed as budget-exhausted**.
7. "PR #111 is green but no required reviewer reviewed a80da5f; merge?" — options obtain the reviews first / merge without / leave open; selected **Merge without the reviews (rule-bending)**.

One question (the three-part refine batch) was aborted by the harness and re-asked; it is not counted.

## 7. Rule check

- Guard: none on this harness (unenforced).
- `build-classify` was unavailable in the resolved executor (only `resolve` is registered); the operator selected docs-only by inspection. Because of that, the local Maven gates (`quality-gate`, `module-tests`, `verify`, `-Ppre-commit`, `-Pcoverage`, `-Pintegration-tests`) were not run — the plan's standing constraint says the Maven gates have nothing to build for a documentation change. The doc checks were run instead: `git diff --check` clean, `git status --porcelain` only `doc/plans/*.adoc`, `trace.py` `BROKEN LINKS 0`, and the CI's required checks green on a80da5f. T4 is reported as the lane's docs-only gate, not as the Maven list.
- `trace.py` was run with the absolute path of the sibling checkout after the operator's authorisation; the lane's executor-only rule was not bent for any other script.
- Required reviewers were not obtained; the operator selected the merge with that exact gap named (§5, §6.7).
- No other rule was bent; the guard log is not applicable on this harness.

## 8. Residue

- Two required reviewers did not review a80da5f (cuioss-review-bot silent, coderabbit label-skipped); the merge proceeded on the operator's explicit selection.
- `model-work/09`'s "most expensive turns" and "largest tool results" remain unowned (not named by D7; listed under No owner found).
- The truncation rule (`hypermedia-format/01` rule 4) has no owner (operator decision).
- D15(4) the role-skill wording and D15(5) the corpus item authoring are owed to `plan-marshall-mcp`; D12 to PLAN-84; D14.2 to PLAN-86.
- D13 is not met as written: two pre-existing § What cross-references were re-pointed by the operator-authorised split, and six review bullets moved verbatim to `epic-review.adoc`.
- Self-review is `budget-exhausted` at the lane's 3-round limit, not clear.
