# PLAN-V2-207: The landing commit's subject on `landing.main` is the bound subject of the `commit-message` task, not the planned pull-request title

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: doc-review-2026-11-25-002.md section 1 ("`landing.main` message"; merged as core #617, `515c1fa3`), with the principle "a mechanism that predicts something the process later observes as a fact is replaced by the observation".
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules. Small: about 6 files. It changes the provider primitive only; the call site that binds the subject is the assembly's (companion note below), and the task `finalize.land-message` itself is not built by this plan (its owner is a package decision, see Dependencies).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R11, R13, R14, R19, R20 (the orchestrator's candidates; an id the lane finds not applicable it says so). Plan parameters: R06 developer documentation `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`; R12: no message is added; layout WS-01.

## Objective

The merged specification (phase-workflows/06 Landing Variants; state-catalogue/18 `finalize.land-message` and `finalize.land-main`; workflow-dsl/06 Stage 3 and its call-site row) writes the subject of the landing commit on `landing.main` with a `commit-message` task over the actual change, not from the plan's recorded `pr_title`. The engine still builds the message from `prTitle`: `GitResolveMessagePrimitive` call site `FINALIZE_LAND_MAIN` reads `Params.prTitle()` and joins it with the server sections. This plan makes the call site read the bound `message` (the subject the task state wrote before the merge mutex) and append the server sections as the body, and removes `prTitle` from the call site's inputs.

## Deliverables

Two deliverables, about 6 files, no split needed.

1. **The call site reads the bound subject.** `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitResolveMessagePrimitive.java`: `FINALIZE_LAND_MAIN` (`:194-205`) no longer builds from `prTitle`; the subject is the bound `message` read under `step_or_fallback` (the same way every bound message is read: a message `CommitMessages.validate` refuses goes to `needs_interactive`), and the server sections, if any, follow as the body separated by one blank line. `Params.prTitle` (`:310-312`, `:319-320`, `:334-335`: the component, its parameter documentation and the builder) is removed, and the `@param` text of `CallSite.FINALIZE_LAND_MAIN` names the bound message and `body` as its inputs. The call site `ADR_RENUMBERING` stays unchanged. **Ask-at-refine (put it in the outline's question once, with the recommendation):** whether a landing call site with no bound message falls back to `needs_interactive` or to a derived subject; recommended: `needs_interactive`, because the spec says the task wrote the subject before the merge mutex and a missing subject is a lane fault, not a case to guess.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the cases in `GitResolveMessagePrimitiveCallSiteTest` (`:43-53, 71-91, 118, 144` carry the old shape): a bound subject with and without server sections gives the subject alone or the subject, a blank line and the body; a call site with no bound message is `needs_interactive` and names the call site; a message the validator refuses is `needs_interactive`; a `prTitle` can no longer be given (the builder has no such member; the compile is the control). Red at the head; then the primitive.
2. **The developer documentation.** `provider-git-primitive.adoc:193` (the list of call sites and their inputs: `finalize.land-main` reads the bound `message` and the optional `body`), the Javadoc of the call site and of `Params`, and the Traceability row that names the call site's inputs.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:**
- Deliverables 1 and 2.
- A bound subject of the `finalize.land-main` call site is the first line of the message and the server sections follow after one blank line; fails at 90505ad2 (the subject is the title).
- `Params` has no `prTitle`, and no production class reads one; fails at 90505ad2.
- The other call sites (`finalize.settle-commit`, `epic.commit-ledger`, `decomposition.commit`, `git.land-removal`, `git.land-unit`, `git.land-setup`, `finalize.hand-back`, `adr-renumbering`) answer as before: the existing cases of the call-site test stay green.
- A scratch mutation that restores the title as the subject fails the new tests (reported by log id).
- Build: `verify -Ppre-commit` (rewrites reviewed and committed), `verify -pl pm-providers/pm-provider-git -am`, `verify -Pcoverage`.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).
- **Companion (outside this repository; state it in the landing message):** the assembly `plan-marshall-mcp` binds this call site today with `prTitle`; after this plan it binds `message = slot.landing_message` (the slot the task state `finalize.land-message` writes). Grep the assembly at the outline: if it holds a binding, a companion pull request there is part of the landing; if it holds none (the call site is bound in no code yet), say so.

## Standing Constraints

- Read every cited line at the head first and drop what a merge settled (name the commit). The lines are those of the doc review on `3ba79c4f`; the head is `90505ad2`.
- Work to the Done-when. Ask only the one question this spec marks ask-at-refine, once.
- No behaviour change beyond the call site; pre-1.0: no shim, no overload that still takes a title.
- A refusal is asserted by reason, not by type. A literal stays only where it is the contract (the call-site wire name).
- No dependency or plugin is added. No more than 100 files per PR. No model-facing content.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: `FINALIZE_LAND_MAIN("finalize.land-main")` at `GitResolveMessagePrimitive.java:194-205` builds `Optional.ofNullable(parameters.prTitle()).map(subject -> body == null ? subject : subject + "\n\n" + body)`; `Params` carries `prTitle` and `body` (`:310-312`, `:319-320`, `:334-335`), read by the orchestrator at 90505ad2.
- OBSERVED: `GitResolveMessagePrimitiveCallSiteTest.java:43-53, 71-91` build the expected message from `prTitle` and `body`; `provider-git-primitive.adoc:193` lists `finalize.land-main` with `prTitle`.
- OBSERVED (the doc review): the call site now runs `step_or_fallback` with `message = slot.landing_message`; the server appends the server sections as the body; the `ADR_RENUMBERING` call site stays.
- HYPOTHESIS: `CommitMessages.validate` accepts a landing subject of the shape the task writes (it validates every bound message); confirm at the class.
- HYPOTHESIS: the assembly has no binding of this call site yet (the call-site table is bound by the assembly, the primitive reads no store); grep `plan-marshall-mcp` at the outline.
- Verify-first clause: if a merge since changed the call-site set (PLAN-V2-169, shipped as #568, closed the primitives' residue), the lines move; follow the names.

## Expected Surface

- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitResolveMessagePrimitive.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitResolveMessagePrimitiveCallSiteTest.java`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`
- HYPOTHESIS: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/package-info.java`, `doc/Traceability.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/specification/phase-workflows/06-finalize-and-merge.adoc`

## Dependencies and Sequencing

- Depends on: none in this repository. The task state `finalize.land-message` (role `commit-message`, slot `landing_message`) is the producer of the bound subject and has NO package yet: the doc review leaves the owner open between `finalize-prune-and-land-files` and `finalize-pipeline-and-merge`; the orchestrator recommends `finalize-pipeline-and-merge` (65%) and the operator decides. This plan does not wait for it: the primitive reads a bound message either way.
- Overlaps with: none of the staged plans names `GitResolveMessagePrimitive` (PLAN-V2-20 and 169 shipped); PLAN-V2-196 (commit-unit residue) edits other files of the commit unit; the gate decides at emit.
- Adjacent to: the assembly (companion note in the Done-when).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-207-landing-main-message-from-the-bound-subject.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
