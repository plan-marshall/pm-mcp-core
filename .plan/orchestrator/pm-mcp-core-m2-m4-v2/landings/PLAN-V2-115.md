# Landing Analysis: PLAN-V2-115 — Schema files foundation

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #311 (merge commit `d69959e6`; 23 files, +374/-102); second pull request: plan-marshall-documentation #160 (open, conflicting)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-115.md`. A pasted claim is a lead, never a fact.

Source: the plan's hand-written landing message `plan-v2-115-schema-files-foundation-001.md` (12:24Z; `landing-check` `complete: true`; 24,109,624 tokens, 73,307 s; 6 deliverables of the outline, 3 met and 3 met with residuals; code review 0 blocking, 2 advisory). The plan was started with the old worktree path, so `emit-landing` did not run (`not_orchestrator_pointer`), and the agent wrote the message by hand with the slug given explicitly.
Corroborated by the orchestrator: Sonar `main` (analysis of `d69959e6`) gate OK, 0 open BUGs; the `Maven Build` and `Integration Tests` runs of `d69959e6` (push and merge_group) green. Read at `d69959e6` by a read-only sub-agent (nothing built); the points marked **checked** were re-read by the orchestrator.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 ADR 016, layout, include convention | met | ADR 016 Decision and Consequences; index row; Status lines of ADR 007 and 012; the ADR 012 body line 66 is stale |
| D2 `SchemaResource` test helper | met, stricter than the spec | `SchemaResource.java`: `text`, `load`, `parse`; refuses absent, malformed UTF-8, non-JSON, non-object, empty, trailing content, duplicate member; no new dependency (`jackson-core` already compile) |
| D3 documents and `trace.py` in plan-marshall-documentation | **not on `main`** | PR #160 is open and `CONFLICTING`; PR #158 (`80feccd`, the same area of `trace.py`) merged first (checked: `gh pr view 160` mergeable `CONFLICTING`, `git log` of `trace.py`) |
| D4 developer documentation of the build | met | `pm-core/README.adoc`, `CLAUDE.md` |
| (outline) package file retired | met | no dangling link to `schema-files-foundation.adoc` |

## Three-Layer Analysis (standing rule R21, r6)

### Layer 1 — Specified
- **S1 (medium).** The spec has four deliverables, the landing message counts six (the outline split D3 and added the retirement of the package file); "6 of 6" is the outline's own count.
- **S2 (medium).** D3 and its Done-when line live in another repository and the spec says "merge only on the operator's word": "done" was satisfiable by an open PR.
- **S3 (high, process).** The verify-first clause covers `pm-mcp-core` pull requests only. Documentation PR #158 changed `trace.py` on `main` (report-only, no `test_trace.py`, 08:42Z) while the plan wrote #160 (a failing/PENDING/UNIMPLEMENTED split with 8 tests) on the older base `620615f`. A content conflict in `trace.py` was found with `git merge-tree`.
- **S4 (medium).** The helper's API (method names, no leading slash, strictness) is not fixed by the spec; plans 116 to 119 rely on it, and it is documented only in the README, the Javadoc and ADR 016. The parser was tightened beyond "the parser `SpecificationDocument` has".
- **S5/S6 (low).** No check that the tests JAR carries the helper (how `SchemaResource$1` went missing); ADR 007's body still has the sentence the spec says ADR 016 supersedes (only the Status line changed).

### Layer 2 — Implemented
- **I1 to I8 met** (see the table); the tests JAR includes `SchemaResource.class` and `SchemaResource$*.class` and nothing else beyond the generator package (checked, `pm-core/pom.xml:145-146`); four fixtures, all `*.schema.json`, not in the JAR; `SpecificationDocument.parse` delegates (`:107`), `jsonBlockAfter` unchanged; 8 test classes now get the stricter parser, the merge was green.
- **I9 (medium, checked).** `CLAUDE.md:30` names `INLINE SCHEMA BLOCKS 0`. It is not a broken instruction (`origin/main` of the documentation repository prints `INLINE SCHEMA BLOCKS <n>` since #158, report-only), but `0` is unreachable until plans 116 to 119 have moved the blocks; the landing message's claim that the check "cannot be run against `main`" is wrong. If #160 merges, `0` means "not PENDING" and the line changes meaning.

### Layer 3 — Correct
- **C1 (low).** Class-loader lookup uses `SchemaResource.class.getClassLoader()`, not the context loader; fine under Surefire, fails under a split loader (a Quarkus test in the assembly).
- **C2/C3 (low).** A leading slash is reported as "absent" without a hint; floats are `Double` (a parity test comparing numbers loses the textual form).
- **C4/C5 (low, checked in part).** No test for a nested duplicate member or an unclosed array (the duplicate check is on the token stream, so it works at every level: checked, `SchemaResource.java:~115`); `refusesDocumentsThatAreNoSingleSchemaObject` has no `@DisplayName`. Assertion messages 32 to 58 characters.
- **C6 (low).** R20 satisfied. **C7 (low).** A second loader exists (`CorpusSchemaResourcesTest.resource`); nothing migrates it.
- **C8 (note).** Merged without a CodeRabbit review at the final head (`32ef3c5`) under a recorded `barrier-ask-override`; Sonar 0 open, code review clean.

## Verdict

Everything in `pm-mcp-core` is met and stricter than the spec; main is green. The documentation half (D3) is not delivered: PR #160 conflicts with #158 and waits for a rebase and the operator's word. Plans 116 to 119 can start on the `pm-mcp-core` side; 119's closing statement "`trace.py` reports no inline schema block below `doc/specification`" needs the `trace.py` question settled.

## Ownership

- PLAN-V2-116 to 119: the helper API and the strictness (verify a block has no trailing content or duplicate member before moving it; each consumer already declares `pm-core:tests` as a `test-jar`: `pm-runtime/pom.xml:37`, `pm-workflow/pom.xml:33`; a moved schema ships in the main JAR, a production reader would need a native-image `resource-config` entry; move `$id`, `$defs` and member order byte for byte and compare `text()` with the block). PLAN-V2-122 owns the later retirement of `SpecificationDocument`.
- No owner: S3 (documentation PR #160 rebase on #158, which `trace.py` wins), I9 (`CLAUDE.md:30` wording), ADR 012 line 66, C4/C5 (test gaps), S4 (API contract). The operator decides (Wa76). Lessons the plan lists about plan-marshall tooling are not filed here (operator preference).

## Update 2026-11-07

The documentation half is delivered. #160 was merged with origin/main (trace.py conflict with #158 resolved: #158's `tag=` selection check kept inside #160's include check; #160's failing/pending/unimplemented split and `test_trace.py` kept), squash-merged as `82f8adc6`. On the merged documentation `main` against `pm-mcp-core` origin/main: `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0` (88 PENDING, 3 UNIMPLEMENTED), 8 tests green. `CLAUDE.md:30` of this repository still names an `INLINE SCHEMA BLOCKS 0` that holds only for blocks not yet pending; it becomes unconditional once 116 to 119 have moved the blocks.
