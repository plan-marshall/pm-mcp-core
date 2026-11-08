envelope_version=1
sender_type=orchestrator
sender_id=schema-transition
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-08T14:37:42Z

# Schema transition: the two open questions are decided; PLAN-V2-121 (`generated-listings`) can be unparked

Sender: the orchestrating session of the schema transition. Operator decision (2026-11-08). Nothing in this epic was edited; **nothing is to be emitted without the operator's word** (the standing instruction of 2026-11-07 holds).

## What was decided

1. `generated-listings`: the log message tables of `doc/LogMessages.adoc` are **generated** from the holders (identifier and template of every record, a committed file below `doc/`, included by the document, compared byte for byte with a fresh generation by one test per module); the Component and Description columns go (the Javadoc of the record keeps what it is for). The three protocol lists of `workflow-task.adoc` (`TaskRefusal`, `WaitAgain`, `End`) are **dropped** for links to the Javadoc; `ProtocolVocabularyParityTest` is deleted.
2. `runtime-schema-validation`: the schema files serve the tests; a stored record is not validated against them at run time; `networknt` stays test-scope. A record read at a boundary that the engine did not write revisits this.

## Where it is written

Pull request pm-mcp-core#326 (branch `docs/listings-and-validation-decided`): the package `doc/plans/generated-listings.adoc` (tasks rewritten, README row `blocked` -> `open`), `adoc-reading-retired.adoc` (the `**/doc/**` entry narrows to the generated files; no longer conditional), ADR 016. Merge it first; the documentation repository removes the two open-question entries in plan-marshall-documentation#164 afterwards.

## Asks

- PLAN-V2-121: unpark and adapt its spec to the rewritten package (the first task was "apply the decision"; it is now five concrete tasks). Its Expected Surface gains every `src/main` holder of `doc/LogMessages.adoc` only as read; the generated files and the new byte-check tests are new paths. Do not emit it before the operator's word; its dependencies are 115 (landed) and, for the log tables, the holder surfaces of 116-118 (check the overlap).
- PLAN-V2-122 (`adoc-reading-retired`): depends on 121 as before; its task "shrink the include list" is now unconditional.
