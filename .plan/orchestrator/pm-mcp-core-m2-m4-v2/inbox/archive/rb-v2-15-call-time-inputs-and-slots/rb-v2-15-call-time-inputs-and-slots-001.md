envelope_version=1
sender_type=plan
sender_id=rb-v2-15-call-time-inputs-and-slots
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-19T20:12:12Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-15-call-time-inputs-and-slots
epic=pm-mcp-core-m2-m4-v2
pr=#513
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=2
total_tokens=unknown
harness=opencode
model=beam-free/high,deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Landing — PLAN-V2-15 (call-time inputs, slots, frame identity, git.commit_and_push)

PR #513 merged through the merge queue; merge commit `bb6a9d33129164e3d89e5747b337f17075455f66`. Source copy read: `ledger_worktree` (`spec_differ` not reported). Standing rules read: r7. `executor-skew`: `match` (0.1.2155).

The plan is **partially implemented**: the persistence model, the slot fill, the frame identity and the provider subset commit are in; the D3 call-time bind and some spec/fixture updates are deferred. `deliverables_done` counts only the fully-met deliverables.

## Deliverables

- **D1 — closed types and validators — MET.** `InputType` gains `Text`/`CommitMessage`/`Path`/`PathList`; `InputValidator`, `InputRefusal`, `PathRules`; frontmatter reads/prints them. `PathRulesTest`, `InputValidatorTest`, `InputTypeTest`, `InputRefusalTest` (order `none`; pass-criteria, green).
- **D2 — slots in grammar and verifier — NOT MET AS WRITTEN.** Grammar (`slot`/`slot_type`/`slot_description`, `slot_ref`, `fills_clause`), `SlotRef`, `StateNode.fills`, printer, and Rule 6 slot checks are implemented and tested (`SlotGrammarTest`, `SlotVerifierTest`, `SlotBindingVerifierTest`, `UnitFrontmatterSlotTest`). The Done-when's `verify/refused/*.pmwf` fixtures per check, the full message/files passing fixture and the `fills` CRLF twin are not added (inline unit tests used instead).
- **D3 — persist slot/provenance and bind at the call — NOT MET.** Implemented: `CallFrame` `commandId`/`correlationId`/`argSources`, `WorkflowStatus.slots`, codec + schema (`WorkflowStatusCodecTest`, `WorkflowStatusSchemaParityTest`), `EngineEvent.TasksSettled.submissions`, `InputBindingContext` port, slot fill from task submission and operator act (`SlotFillTest`), frame ids (`InputBindingTest`). **Deferred:** the call-time bind that resolves a slot into the frame's `bound_args`/`arg_sources`, validates against type/confinement/staging set and consumes the slot — blocked on a text carrier for the in-memory `ast.CallFrame` (Decision 9 keeps text out of the grammar). Docs (Traceability, core-store guide, ADR workflow-engine/018) state this as deferred.
- **D4 — git.commit_and_push first user — NOT MET AS WRITTEN.** `CommitInput.paths` and `JGitOperations` subset commit (setOnly) are in with tests (`JGitOperationsTest`). The specification updates to `06-reusable-sub-workflows.adoc`/`state-catalogue/05`, the `unit-fixtures/defaults/git-commit-and-push.pmwf` fixture and the call-site matrix are not done.
- **D5 — documentation and specification — PARTIALLY MET.** Ten ADRs (renumbered to 077/078 per R11 against the merged main), `workflow-input.adoc`, extended developer guides, grammar/Rule 6/store-schemas prose, Traceability PM-WF-16, watch items. The `06-reusable-sub-workflows.adoc`/`state-catalogue/05` parts (D4) are missing. Links check: no broken links from touched files; the 3090 sibling-link reports are the plan-worktree-depth artifact of R17.
- **D6 — identity types and registry rows — MET.** `CommandId`/`CorrelationId` + tests, `Ports.ids`, registry rows, `IdentifierSpecificationParityTest`.

## Personas and skills

Per step: `persona-implementer` (production), `persona-module-tester` (tests), `persona-code-reviewer` (review triage). The spec declares no `Skills:`/`Persona:` lines → `skills: none named`.

## Hypotheses

- `list<path>[lo..hi]` parses: a real defect was found and fixed (the reader required a trailing `>`), covered by `UnitFrontmatterSlotTest`.
- `CommitCommand.setOnly` commits only the named paths leaving pre-staged index entries: confirmed by `JGitOperationsTest.commitsOnlyListedPaths`.
- Dominance check over `UnitGraph` affordable: deferred — the weaker "filled somewhere" check ships (per the spec's allowance), recorded in ADR workflow-engine/017.

## Findings

All 25 CodeRabbit findings were triaged and addressed: format-character path rule (U+200B), unreachable `path.double-slash` removed, surrogate-safe `InputRefusal` echo, slot/input name collision refused both orders, `fills` admitted only on task/operator-wait states, task fill restricted to awaited entities, honest deferred docs (Traceability, core-store, ADR 018), real slot round-trip test (was tautological), unused dead branch removed, S3457 format-string fixed. The re-enabled `WorkflowStatusSchemaParityTest.membersEqualSchema` exposed a **pre-existing** MEMBERS/schema divergence owned by `process-and-overview` (title, created_at, …); left `@Disabled` with an accurate reason as an answered finding outside this plan's scope.

## Reviewers

- `coderabbit` (required, quota-limited, review-complete-after-handling): reviewed; 8 actionable + 13 minor + 3 nitpick, all handled; check SUCCESS on the head. Its stale review body's `Minor comments: 13` section kept `runbook_lane review-gate` at `undecidable_read`, so the merge-gate review condition was **overridden by the operator's explicit selection** ("Enqueue now, overriding the gate").
- `sourcery` (optional): skipped — PR exceeds its 150k-diff limit.

## Operator questions

1. "Merge PR #513: D3 bind deferred + review-gate undecidable_read" — options {wait-for-fresh-review, enqueue-override, stop}; selected **wait-for-fresh-review**.
2. "CodeRabbit will not re-review; the gate stays undecidable" — options {enqueue-override, stop}; selected **enqueue-now, overriding the gate**.

## Rule check

- **Guard: none on this harness (unenforced).**
- Failures not mine: one full-reactor local `verify` failed `pm-runtime` `LspClientTest.timeout/.stubborn` (PT2S LSP-init timeouts) under a saturated daemon — module untouched, CI green; and one CI `quarkus-alignment` 404 on `quarkus-bom-3.40.1` that a re-run cleared (transient Central/CDN; the POM is fetchable).
- The merge pulled three main commits; I re-merged `origin/main`, rebuilt green, and renumbered the plan ADRs (R11).

## Build server

`build_server: ready` at setup. Builds ran on the daemon (`daemon_longpoll`); no fallbacks beyond one `pm-provider-git` coverage check that initially sat at 0.79 branches and passed after the empty-subset-commit test.

## Residue

- **D3 call-time bind** (resolve/validate/provenance/consume) — the principal follow-up.
- **D2** `verify/refused/*.pmwf` per-check fixtures + full passing fixture + `fills` CRLF twin.
- **D4** `06-reusable-sub-workflows.adoc`/`state-catalogue/05`/`git-commit-and-push.pmwf` + call-site matrix.
- `verify -Pcoverage` (full) and `verify -Pintegration-tests` not run in the plan worktree (coverage of the three touched modules was exercised; pm-provider-git restored to ≥0.80 branches).
- External hand-offs: `plan-marshall-mcp` production `git-commit-and-push.pmwf` + `StateCatalogueDriftTest`, the `TasksSettled` producer passing `submissions`, and the `InputBindingContext` implementation; PLAN-V2-20 (`git-commit-primitives`) and PLAN-V2-10 (production registries).
- `review-gate` fact keys (`unresolved_review_threads`, `review_body_findings_*`) are `unknown`: the gate never returned a read (undecidable on the stale body), never a number recalled from the cycle.
