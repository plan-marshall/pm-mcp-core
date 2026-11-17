# Landing Analysis: PLAN-V2-122 — The adoc reading is retired

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #468 (merge `43c33c90`, 2026-11-16)

> Landing record for one shipped plan, **delivered**. Written by `analyze` (inbox scan) from the lane's landing message
> `rb-v2-122-adoc-reading-retired-001.md` (revision 1, amended 22:02:38Z; 5 of 5 deliverables claimed). The reading confirms all five
> deliverables and every claim the message makes about the code. Two stale sentences in ADRs remain. Nothing is blocking. A claim of the
> message is a lead; the diff size, the dangling references, the ADR 007 sentence and the stray blank line were re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `SpecificationDocument` and `pm.specification.root` deleted | met | helper deleted (-132 lines, the only `.java` in the PR); `pm-core/pom.xml` keeps `pm.product.root` only; no code, POM, README, `.github` or `CLAUDE.md` names the property or the class at the head (`git grep` re-read; only ADR prose hits remain). The clause "the `..` fallback of every test that read the property" had nothing to delete: only the helper named the property and it failed on unset, it did not fall back (`SpecificationDocument.java:35,48,50` at the parent) |
| D2 `paths-code-extra` shrunk | met | `.github/project.yml:33-35` is `doc/log-messages/*.adoc` and `**/src/**`; it covers `src/test/resources`, `src/guard-controls` and every `src/main` the scan tests read; no test reads `doc/LogMessages.adoc`, `Traceability.adoc`, a module `doc/` or a README |
| D3 the ADR and the index | met | ADR 016 (`:61-67`) states the retirement; ADR 009 and 015 status lines and the two index rows carry it; ADR 019 past tense (`:54-57`) |
| D4 verify by search | met | the only test readers below `doc/` are the three `*LogMessagesTest` classes reading `doc/log-messages/*.adoc` (`CoreLogMessagesTest.java:55`, `RuntimeLogMessagesTest.java:56`, `WorkflowLogMessagesTest.java:48`) |
| D5 documents here | met, one loose citation | `project-setup.adoc:81` supports the claim; `:97` is about where implementation state is recorded and names no docs-only skip, so "quoted from lines 81 and 97" is loose; nothing in `doc/developer` names `skip-on-docs-only` |
| Done-when 2 (a `doc/specification`-only PR skips the build) | not met by construction | a post-merge observation; this PR changes code and built. Owed to the first such PR |

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read the PR's own diff (`43c33c90^..43c33c90`, 12 files; the
range from the lane's base `72d698b9` is 53 files because #464, #465 and #467 sit between). The orchestrator re-read the dangling
references, ADR 007 line 45, the blank line and the PR's file count. By reading, no build, no test run, no write.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity |
|---------|-------|---------|----------|
| L1-1 | all five deliverables as in the table | confirmed | the lane is right |
| L1-2 | Expected Surface omits `doc/adr/016`, `doc/adr/019`, `pm-core/README.adoc`, `pm-core/doc/developer/core-store.adoc` | confirmed; the spec's D2 and D3 name 016 and 019 indirectly | minor, spec gap (the plan is shipped; no refine owed) |

### Layer 2 — Implemented

| Point | Reading | Verdict |
|-------|---------|---------|
| Deletion only | no `.java` file is touched but the deleted helper | clean |
| README and developer guide | `pm-core/README.adoc` and `core-store.adoc:2443-2448` no longer say the property stays | clean |
| Gate rewrite claim | the lane says it reverted the pre-commit blank line in `LandingVariantValidationTest`: the PR does not touch the file. The blank line at `LandingVariantValidationTest.java:16-17` on the head comes from #464 | confirmed; the stray line is #464's (already in PLAN-V2-114 item 38) |
| Package file and its index row | deleted | clean |

### Layer 3 — Correct

| Point | Reading | Verdict |
|-------|---------|---------|
| Dangling references | none in code, POM, README, `.github`, `CLAUDE.md`; ADR prose only: `007:45`, `009:23`, `015:29`, `016:16,24,60-62` | 009, 015 and 016 frame theirs as history; **ADR 007 line 45 still says in the present tense** "The schema parity tests read the specification of this repository (`pm.specification.root`)", which ADR 016 `:60` says it supersedes without the sentence being struck (re-read, minor) |
| ADR 009 Context | `:22-29` reads in the present tense ("read", "therefore can break", "That happened twice"); its Decision section describes `skip-on-docs-only=false`, which ADR 015 already superseded | the lane disclosed this; minor |
| `paths-code-extra` sufficient | a valid glob; `pom.xml` and `.github/**` are not on the default ignore list and so build; the skip widens only for `doc/**` and `{module}/doc/**` `.adoc` and `.md` | clean |
| ADRs agree | 009 to 015 and 016; 015 says 016 narrowed the entry; 016 matches `project.yml` | clean |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried here (the three reviewers' threads are resolved per the message; no finding rests on it) |
| review body findings total / handled | 1 / 1 | not re-verified (progress prose) |
| Sonar open new issues / hotspots | 0 | not re-queried |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` only (register E27). Surface delta unmeasured. Guard unenforced
on this harness. The lane named five deviations (the R17 check run directly, extra R01 profile arguments, a raised bound, one reverted
gate rewrite, the early `git rm`); none is a defect of the repository.

## Residue

1. **Carried into PLAN-V2-114, item 39:** strike or mark the ADR 007 sentence at `:45`; put ADR 009's Context premise in the past tense
   (or one line "premise retired by ADR 016").
2. **Already owned:** the `LandingVariantValidationTest` blank line (PLAN-V2-114 item 38, its own cleanup commit).
3. **Open observation, not a fix:** the first `doc/specification`-only pull request must skip the build (Done-when 2). The operator
   sees it at the first such PR; no plan owns it.
4. **Not routed:** the spec's Expected Surface gap (shipped plan); the loose `project-setup.adoc:97` citation; the Sourcery refusal (optional reviewer).
