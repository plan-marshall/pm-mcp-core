# PLAN-V2-139: Finish PLAN-V2-53: the dead judgment-endpoint link, the seven short assertion messages, the CLAUDE.md pointer and the duplicated predicate

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: the three-layer analysis of PLAN-V2-53 (`landings/PLAN-V2-53.md`: layer 1 L1-3, layer 2 "weak control", layer 3 "rule deviations" and "duplicated logic"), the merged pull request #365 (`50ec9a55f990c5b57322ea358f927375495fa508`) and the operator's word of 2026-11-11 to stage the residue plans (runbook lane, residue)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-139-outbound-guard-residue.md` and is queued as one row file, `queue/PLAN-V2-139.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (the current one; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R10: the specification edit changes link targets only, so every block a test reads stays byte-stable; R11: no ADR is written; R12: no message is added; R13: no stored format changes; R14, R15, R16: no protocol, DSL or runtime behaviour changes. R05 because `doc/specification/credentials-and-enrolment/03-audit-and-outbound-https.adoc` changes link targets; no requirement is implemented, so `doc/Traceability.adoc` is not edited. R07 because `CLAUDE.md` changes: see Carried Open Items.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-guard.adoc` (the link targets of § Specification Tracing and the term "exemption map"); no ADR (no design decision; ADR 023 stays as it is).
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-53 (#365) shipped the outbound-call guard `OutboundCallsArchTest`. The analysis of its landing found a link target that does not exist, seven assertions that pass the whole ArchUnit failure report as their message, a `CLAUDE.md` pointer written in AsciiDoc syntax, a parallel copy of the rule predicate, and a dependency the project's own allow-list does not name. This plan closes those five and changes nothing else: the rule, its exemption map, its allow-list and its fixtures stay as they are.

The change is documentation, one `CLAUDE.md` bullet and test code of one class. No production code, POM, plugin or dependency changes.

## Deliverables

Four small deliverables, one commit each. The test order of Deliverable 3 is `none` (a refactor guarded by the unchanged tests); Deliverable 2 is `none` (a message argument).

1. **The dead judgment-endpoint link (order 1).** `client-profiles/04-judgment-endpoint.adoc` does not exist in this repository's `doc/specification` nor at `plan-marshall-documentation` `origin/main` (read after a `git fetch`; `git ls-tree` finds no `client-profiles` or `judgment-endpoint` file). The spec of PLAN-V2-53 named it as the home of "Client Profiles § Judgment Endpoint" and the plan copied it into `doc/specification/credentials-and-enrolment/03-audit-and-outbound-https.adoc` (lines 42 and 44) and into `pm-runtime/doc/developer/runtime-guard.adoc` § Specification Tracing. Replace each with a link that resolves: the existing guide `pm-runtime/doc/developer/runtime-endpoint.adoc:28` already pins the historical commit `51f551fc` for this document, and the lane chooses between that pinned link, the current home of the section if the outline finds one, and removing the link with a sentence; it says which in the first progress note and the pull request body (R07: ask-at-refine, Carried Open Items 1). **Done when:** a search of the three files for `client-profiles/04-judgment-endpoint.adoc` finds only the pinned-commit link (if chosen) or nothing; the R17 check run exactly as the standing rule prescribes, with the absolute paths and after a `git fetch` of the documentation checkout, reports `BROKEN LINKS 0` for the files this plan touches; the lane says in the landing whether the run it made would have caught the old target.
2. **A message on the seven failure-report assertions (order 2).** Seven assertions of `OutboundCallsArchTest` pass `() -> result.getFailureReport().toString()` or a similar supplier as their message (`OutboundCallsArchTest.java:334-335`, `:343`, `:351`, `:403-404`, `:416`, `:424` at `50ec9a55`). Give each a message of 20 to 60 characters that says what is checked (as the other assertions of the class do) and keep the failure report visible when the assertion fails: the outline chooses how (a second assertion or a failure-detail method) and says which; no assertion is weakened. **Done when:** a scan of the class finds no assertion without a message of 20 to 60 characters; the failure report of a failing control is still printed (shown by the temporary removal of one control's violation and a revert, recorded in the progress note); `test -pl pm-runtime -am` is green with the method count of the branch base.
3. **One predicate, not two (order 3).** `connectionViolators()` (`OutboundCallsArchTest.java:239-248`) re-implements the `ArchCondition` `connectionFree` (`:205-218`), so the exemption-symmetry test does not exercise the condition rule A uses. Make the symmetry test evaluate the same condition (one definition, used by both), and strengthen the "every entry carries its specification sentence" test (`:378-388`) from "contains the section title and is at least 40 characters" to a check that the sentence names the exempt class's reason (the outline says what it compares). **Done when:** the class holds one definition of the connection predicate; the control fixtures, the symmetry tests and the allow-list tests pass unchanged in count; removing a type from the exemption map still fails the symmetry test (shown by the temporary mutation and a revert).
4. **`CLAUDE.md` (order 4; R07, see Carried Open Items).** (a) The pointer to the guide at the end of the outbound-call bullet is written in Markdown (`[Outbound-Call Guard Developer Guide](pm-runtime/doc/developer/runtime-guard.adoc)`), like the other links of the file; (b) the bullet says "exemption map", the term the test constant, the guide, the specification and ADR 023 use; (c) the ArchUnit test dependency of `pm-runtime` is named in § Dependencies and Versions, with the date and the operator decision it rests on (the spec of PLAN-V2-53 records decision A1, 2026-10-29: "the operator's word is given for the one new test dependency of `pm-runtime`"). **Done when:** the three edits are the only change in `CLAUDE.md`; the new link target exists; the statement in (c) names the decision with its date and the lane records in its progress note that it quoted the spec's sentence and could not confirm the operator's own record.

**Done when (plan):** Deliverables 1 to 4; no `pom.xml` in `git diff --stat`; the method count of `pm-runtime` equals the base; `OutboundCallsArchTest` is the only Java file touched. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed, in their own commit where they touch files outside the plan's scope), `test -pl pm-runtime -am` with `--timeout`, and `verify -Pcoverage` (a full verify exceeds the host's budget: per-module runs).

## Standing Constraints

- **Not here:** the gaps the rule has by design (method references to `URL::openStream`, `URL.getContent`, DNS lookups, subprocesses that run network tools, reflection; whole-class exemptions): they are design notes in `landings/PLAN-V2-53.md`, not defects of this plan; the nine fixtures' lack of `@UtilityClass` (they follow `pm-core`'s guard fixtures, as the spec of PLAN-V2-53 said); the rule's connection-type set, the exemption map, the allow-list.
- **No behaviour change.** The rule must fail on every control fixture and hold on the two positive controls exactly as before; no exemption or allow-list entry is added or removed.
- **No dependency or plugin is added.**
- **Where the project analyses its pull requests with Sonar** (read it over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count at the head (`0` confirmed, or every remaining issue dismissed); an `undecidable` read blocks.

## Claim Labels

All lines read at `50ec9a55f990c5b57322ea358f927375495fa508` (the merge commit of #365) through `git show`; the documentation checkout was read at its `origin/main` `ca1549b` after a `git fetch`.

- OBSERVED: `doc/specification/credentials-and-enrolment/03-audit-and-outbound-https.adoc:42` and `:44` carry `client-profiles/04-judgment-endpoint.adoc`; `pm-runtime/doc/developer/runtime-guard.adoc` § Specification Tracing carries it too (the leaf read it at the guide's line 103); the file exists nowhere (`git ls-tree -r origin/main` of the documentation checkout finds no `client-profiles` or `judgment-endpoint` path)
- OBSERVED: `pm-runtime/doc/developer/runtime-endpoint.adoc:28` pins `https://github.com/plan-marshall/pm-mcp-core/blob/51f551fc/doc/specification/client-profiles/04-judgment-endpoint.adoc` for the same document
- OBSERVED: `OutboundCallsArchTest.java:334-335`, `:343`, `:351`, `:403-404`, `:416`, `:424` pass the ArchUnit failure report as the assertion message (the orchestrator read `:331-336`, `:343` and `:351`; the others are the leaf's reading)
- OBSERVED: `connectionViolators()` at `:239-248` and `connectionFree` at `:205-218` express the same predicate twice; the "every entry carries its specification sentence" test at `:378-388` checks the section title and a length of at least 40 characters
- OBSERVED: `CLAUDE.md:146` writes `link:pm-runtime/doc/developer/runtime-guard.adoc[...]`; the other links of the file are Markdown; the bullet says "the exception map"
- HYPOTHESIS: the R17 check run by the lane of PLAN-V2-53 did not catch the dead target because the cross-repository link resolves relative to the worktree — confirm or refute by running the check from the main checkout and from the worktree (verify-at-outline)
- Verify-first clause: before editing, list the open pull requests for one that touches the files of the Expected Surface (PLAN-V2-81, which edits `pm-runtime/README.adoc` only, does not), re-read the files at the head, re-run the scans and the counts above, and drop what has changed; a refutation loops back to re-scope.

## Expected Surface

Code, documentation and the project file (R19: per file; every file OBSERVED at `50ec9a55`):

- OBSERVED, modified: `pm-runtime/src/test/java/de/planmarshall/runtime/guard/OutboundCallsArchTest.java` (D2, D3)
- OBSERVED, modified: `pm-runtime/doc/developer/runtime-guard.adoc` (D1; the term in D4(b) if the guide uses the other one)
- OBSERVED, modified: `doc/specification/credentials-and-enrolment/03-audit-and-outbound-https.adoc` (D1, link targets only)
- OBSERVED, modified: `CLAUDE.md` (D4)
- OBSERVED, read not edited: `pm-runtime/doc/developer/runtime-endpoint.adoc`, `pm-runtime/src/test/java/de/planmarshall/runtime/guard/fixtures/`

Not surface (R19): `doc/Traceability.adoc`, `doc/adr/`, `pm-runtime/README.adoc`, every `pom.xml`, every file under `src/main`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-53 (shipped, #365, `50ec9a55`: the guard this plan finishes).
- Overlaps at file level are decided by the disjointness gate at emit, from the Expected Surface above. Known: PLAN-V2-81 (running at staging) edits `pm-runtime/README.adoc`, which this plan does not touch; PLAN-V2-101 to 105 and 125, 126 name `pm-runtime` test directories (the assertion messages of untouched assertions are theirs; the seven here are this plan's).
- `CLAUDE.md` is edited by every plan that adds a rule; whichever merges second merges the base (a textual conflict at most).

## Carried Open Items

Ask-at-refine, one at a time, with a recommendation (R07):

1. **The target of the judgment-endpoint link (D1).** Options: the pinned historical link of `runtime-endpoint.adoc:28`, the current home of the section if one exists, or no link. Recommendation: the pinned link, because it is what the sibling guide already does; confidence about 70%.
2. **The dependency sentence in `CLAUDE.md` (D4(c)).** The operator's own record of decision A1 was not read; the sentence rests on the spec. Recommendation: name ArchUnit with the date and the decision; confidence about 85%.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-139-outbound-guard-residue.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
