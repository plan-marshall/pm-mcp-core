envelope_version=1
sender_type=plan
sender_id=workflow-dsl-parser-and-ast
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T15:32:49Z

# Candidate lesson: review bots caught invariant and contract gaps in the DSL parser and AST that the plan's own tests missed

**Signal source**: automatic-review. Review-bot findings on PR #27 remediated in-run (`--type pr-comment --resolution fixed`), handled by loop-back fix tasks TASK-011..TASK-015 (iteration 1/3) and TASK-016 (Sonar S1192, iteration 2/3).

**Component (suggested)**: project-local, `pm-workflow` (workflow.ast / workflow.dsl)

## The defects (all fixed before merge)

- `d08736` (sourcery): `CallValue.EnumList` / `ParamType.EnumOf` kept duplicate enum values despite the distinct-values contract. The parser now refuses a repeated value (TASK-11, TASK-13).
- `44fcd3` (sourcery): `InstanceHeaderReader` called `skipChildren()` for unknown members and so bypassed the duplicate-member check in nested objects. The rule now holds at every depth (TASK-12).
- `923866` (sourcery): instance-header refusals reported the JSON token's start column instead of the offending column (TASK-12).
- `411246` (sourcery): a one-sided `Text`/`Int` range vanished on print. The grammar only has `[low..high]`, so construction now refuses a single bound (TASK-11).
- `ba754a` (coderabbit): the parser hard-coded the keyword lists of five enums (one of them `OutcomeClass` from pm-core) while the printer derives them, so the round trip could break silently. Replaced by one generic enum lookup (TASK-13).
- `bd7b54` (coderabbit): Javadoc in three places claimed that every parsed node carries a `SourceLocation`, which the model does not do. Corrected to list the nodes that do (TASK-15).

## Pattern worth classifying

Each of these is a stated invariant that had no enforcing mechanism or test: distinctness, duplicate keys at depth, round-trip closure over enum constants, and a documentation claim about location coverage. For parser and AST work in this epic (static verifier, engine), state these invariants as test obligations in the outline: a round-trip test that iterates every enum constant, duplicate detection at every nesting depth, constructor refusal of states the grammar cannot express, and a claim-vs-model check of the package docs.
