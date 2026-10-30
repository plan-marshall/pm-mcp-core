# PLAN-V2-61: The documentation domain verbs: asciidoc.verify-links, asciidoc.format and the asciidoctor diagnostics

epic: pm-mcp-core-m2-m4-v2
workstream: WS-08
origin: PLAN-136 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-61-documentation-domain-verbs.md` and is queued as one row file, `queue/PLAN-V2-61.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 7 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. In-repo share: the pure parts in `pm-core`; the registration of the verbs as primitives and the documentation review role are not in this repository (see the objective).

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-domain.adoc` (created by PLAN-V2-09 (`domain-axes-and-descriptors`), extended); ADR directory: `pm-core/doc/adr/core-domain/` (the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Seventh part of the work package `domain-extensions-and-architecture`: the three compiled domain verbs of the `documentation` domain with closed outcomes and findings in the provider-neutral finding record — `asciidoc.verify-links` and `asciidoc.format` as compiled checkers, `asciidoc.validate` as a confined job of the enrolled `asciidoctor` whose argument vector, diagnostics parser and `capability_missing` outcome are delivered here. The tone and content review is a role task (model-facing content, `plan-marshall-mcp`); the registration of the verbs in the primitive registry and the job execution are the assembly's.

## Deliverables

1. **`asciidoc.verify-links`** (compiled, line-based, no job): an `xref:` or `<<id>>` whose target document does not exist or whose anchor is not defined there (explicit `[#id]`, `[[id]]`, and the section ids `asciidoctor` derives with its default prefix and separator `_`); a `link:`, `image::` or `include::` target that does not exist inside the project root (confined, R09); an attribute reference `{name}` the document neither defines (`:name:`) nor receives as a built-in; external `http`/`https` targets are never fetched. Findings: `file`, `line`, `severity`, `rule`, `message`.
2. **`asciidoc.format`** (compiled house rules, a closed list): no trailing whitespace; exactly one newline at the end of a file; a blank line between a paragraph and a following list, table or delimited block; no tab indentation outside literal blocks. A pure function from text to formatted text and the changed lines as findings with rule `format.<rule>`; with `check` it writes nothing and reports.
3. **`asciidoc.validate`'s parts:** the argument vector per file (`asciidoctor -v --failure-level=WARN -o /dev/null <file>`), started through the enrolled Ruby interpreter (absolute path and SHA-256 digest) and never through the shebang; the parser of the `stderr` lines `asciidoctor: WARNING|ERROR: <file>: line <n>: <message>` into findings with rule `asciidoctor` (and a finding without a line for the same form without `line <n>`); a non-zero exit without a parsable line is `failed_unparsed`, never a pass; with `asciidoctor` or its interpreter not enrolled the outcome is `capability_missing` with a `detail` naming the enrolment command (`pm-operator enrol` with the host tool `asciidoctor` and its Ruby interpreter), and the compiled verbs still run. Matched control: a clean document under an enrolled tool ends without findings (PM-WATCH-EXT-37).
4. **Documentation:** extend the guide, the ADRs (where the verbs live, the closed finding shape), delete the implemented § Documentation Domain Verbs of `01-four-axis-architecture.adoc` and PM-WATCH-EXT-37 where covered, and remove the package row and file if this is the last plan of the package to merge. Name as owed to `plan-marshall-mcp`: the registration of the three verbs, the job that writes the plan worktree for `asciidoc.format`, and the documentation review role.

**Done when:** Each diagnostic form (`WARNING` and `ERROR`, with and without `line <n>`) yields the finding with file, line and severity; a non-zero exit without a parsable line is `failed_unparsed`; without the enrolment the verb ends `capability_missing` naming the enrolment command while `verify-links` and `format` still report on the same file; the argument vector starts with the enrolled interpreter; `verify-links` reports each of its four defect classes with a fixture and never fetches an external target; `format` reports every changed line with its rule and writes nothing in `check` mode. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXT-1, PM-SKILL-1, PM-EXEC-3
- Specification: `doc/specification/domain-extensions/01-four-axis-architecture.adoc` § Documentation Domain Verbs
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-37

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- A verb behind an unavailable host tool reports that and never a synthetic pass (Core Architectural Invariant 2).
- R09: `verify-links` adopts the confinement check itself.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: `doc/specification/domain-extensions/01-four-axis-architecture.adoc` lines 355-385: the verb table (`asciidoc.validate` job, `asciidoc.verify-links` compiled line-based checker "in `pm-core`; no job", `asciidoc.format` compiled house rules, tone and content a task of a documentation review role)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ProjectEnrolment.java` line 219 `ASCIIDOCTOR("asciidoctor")` is a host tool kind of the enrolment; `pm-core/src/main/java/de/planmarshall/core/spi/validation/AsciiDocValidator.java` validates model-submitted AsciiDoc text (blank, control characters, unclosed blocks, title and section levels) and is not a link or format checker
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/findings/Finding.java` line 80 is the provider-neutral finding record, with a source kind and provider; `pm-workflow/src/main/java/de/planmarshall/workflow/primitive/` holds only `package-info.java` (no primitive is registered in this repository yet: PLAN-V2-24, PLAN-V2-29, PLAN-V2-22 and PLAN-V2-64 declare that directory)
- HYPOTHESIS: the compiled verbs live in a new package of `pm-core` (`core.domain` or a sub-package of it, because the specification says "`pm-core`" for the checker and the verb ids belong to the `documentation` domain) — decide at the outline; the verb's finding shape is a mapping to `Finding` or a smaller value (verify-at-outline)
- Verify-first clause: before editing, list the open pull requests touching `core/domain/` and `workflow/primitive/`, check whether the primitive registration exists by then, and re-read the verb table at the head; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/domain/` (or a new sub-package) new classes: the link checker, the house-rule formatter, the `asciidoctor` argument vector and diagnostics parser, the `capability_missing` outcome (exact names at the outline); tests under `pm-core/src/test/java/de/planmarshall/core/domain/` and fixtures under `pm-core/src/test/resources/asciidoc/` (new)
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/01-four-axis-architecture.adoc` (§ Documentation Domain Verbs), `doc/implementation-watch/domain-extensions.adoc` (PM-WATCH-EXT-37)
- HYPOTHESIS: `pm-core/doc/developer/core-domain.adoc` and `pm-core/doc/adr/core-domain/` — created by PLAN-V2-09 (`domain-axes-and-descriptors`), extended additively (or created here if this plan lands first)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing in code (it can start at once); it shares the new guide and ADR directory with PLAN-V2-09 (`domain-axes-and-descriptors`), whichever lands first creating them.
- Overlaps with: `01-four-axis-architecture.adoc` and the guide with PLAN-V2-09 (`domain-axes-and-descriptors`) (different sections); the primitive directory `workflow/primitive/` with PLAN-V2-24, PLAN-V2-29, PLAN-V2-22, PLAN-V2-64 only if the lane registers the verbs here (expected: not, owed to the assembly). None with PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43.
- Adjacent to: `plan-marshall-mcp` (verb registration, the documentation review role); the host-tool enrolment of PLAN-46 (`machine-onboarding-unit`) and `ProjectEnrolment`.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-61-documentation-domain-verbs.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
