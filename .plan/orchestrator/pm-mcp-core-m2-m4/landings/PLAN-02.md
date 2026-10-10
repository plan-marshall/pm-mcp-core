# Landing Analysis: PLAN-02 — Primitive SPI and in-process content validators

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #18 (https://github.com/plan-marshall/pm-mcp-core/pull/18), merge commit abaf95d

> Landing record for one shipped plan. Lives at `landings/PLAN-02.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `primitive-spi-and-validators-025.md` (landing-facts complete, no missing key),
corroborated on 2026-10-10 against the PR state, the merge commit and the index on `origin/main`.

## Deliverable Fidelity vs Spec

Verdicts rest on the file list of the merge commit and the PR state. The content of the tests was
not read, so "the acceptance holds" is the plan's claim plus green CI, not an independent check.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| The typed `Primitive<P, R>` interface: name, parameter record, closed outcomes, side effects, awaited events | shipped-as-specified | `core/spi/Primitive.java`, `PrimitiveOutcome.java`, `PrimitiveResult.java`, `FreeParam.java`, `CycleMeasure.java`, `WorkflowExecutionContext.java` in abaf95d; `PrimitiveContractTest`, `SpiVocabularyTest` |
| The in-process content validators | shipped-modified | `core/spi/validation/` with `AsciiDocValidator`, `ConventionalCommitValidator`, `OutlineValidator` and their tests. Reported as partial by the plan: no validator for `request.md` or pull request bodies (PM-WF-6), and the `OutlineValidator` rules that need the working tree, task derivation or build extensions are absent |
| The contract of `OutcomeClass` / `AwaitedEvent` and the registry of primitives | shipped-modified | `PrimitiveRegistry.java`, `PrimitiveRegistrationException.java`, `PrimitiveRegistryTest`. Reported as partial: registry checks that need the closed gate list or the keyword list, and the binding of free parameters, are absent |
| Documentation pull request (the plan's own sixth deliverable) | not done | `plan-marshall-documentation` #20 is OPEN and unmerged; its checks read green on 2026-10-10 |
| Index row | shipped-as-specified | `doc/plans/README.adoc` on `origin/main` gives `primitive-spi-and-validators` the status `done` |

Surface: 23 realized paths against 3 declared entries, nothing added and nothing missing
(`inbox landing-check`, state `clean`). `PmMcpLogMessages` is byte-identical before and after the
merge; the WS-03 identifier block 070-079 is untouched.

## Metrics and Anomalies

- Tokens: 8,809,647 recorded. The plan reports this as overstated and gives 6,131,992 as its own
  sum of observed dispatch usage; neither figure was re-derived here.
- Duration: 35,521 s wall time (about 9.9 hours).
- Anomalies: three review fix rounds after the first push; the first fix round failed the Sonar
  quality gate with a bug the fix introduced; `archive-plan` reads `unknown` because it runs after
  the landing message.

## Routing and Merge Behavior

- Review: CodeRabbit participated, Sourcery approved. `cuioss-review-bot` reviewed the first commit
  only and was moved to `optional_bots` for this plan by operator decision. Eight review findings
  became fixes inside the PR (registry null checks, free-parameter declarations, AsciiDoc
  delimited blocks and table delimiters).
- CI/merge: merged through the merge queue, squash commit abaf95d. SonarCloud reported 0 new-code
  issues and 99.2% new-code coverage on the merged head (plan's claim). `verify -Pcoverage` and
  `verify -Pintegration-tests -pl pm-core -am` last ran locally four fix commits before the merged
  head; the CI integration-test job on the head was green.
- Collisions: none observed with PLAN-01 or PLAN-03, both still in flight.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-02 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-02 --field pr --value #18`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-02 --field landing --value landings/PLAN-02.md`
- [x] row `plan_marshall_plan_id` stamped — `primitive-spi-and-validators`
- [x] epic.md narrative reconciled (inbox dispositions, defects, watches)
- [x] Open Defects opened: unowned remainder of the roadmap item; nested-table delimiter unverified
- [x] Watches opened: documentation PR #20 unmerged; review bot reviews only the first commit
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- The remainder of the roadmap item (validators for `request.md` and pull request bodies, the
  working-tree rules of `OutlineValidator`, the gate-list and keyword-list registry checks, the
  binding of free parameters) is owned by no staged plan — recorded as an Open Defect for the
  operator to assign.
- The nested-table delimiter (`!===` implemented, `!==` named by CodeRabbit) is unverified against
  the AsciiDoc documentation — recorded as an Open Defect.
- 24 candidate lessons were promoted to the lessons store as 2026-10-10-07-001 to -024; the 16 that
  concern plan-marshall tooling sit in this repository's store and still need routing to
  plan-marshall.
- PLAN-07 `workflow-dsl-parser-and-ast` depended on this plan alone and is now unblocked.
