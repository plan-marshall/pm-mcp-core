envelope_version=1
sender_type=plan
sender_id=diagram-generator
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:05:27Z

component=plan-marshall:phase-3-outline
category=improvement
source_plan=diagram-generator
source_record=qgate-3-outline:881af1

# Outline deliverables that touch a sibling repository must name the absolute sibling checkout, not ../ paths

## What happened

Plan `diagram-generator` (pm-mcp-core, use_worktree=true) carried a deliverable that opens a documentation pull request in the sibling repository `plan-marshall-documentation`. The outline listed its affected files as `../plan-marshall-documentation/...` and set its module to `plan-marshall-documentation`, which is not an architecture module of the plan's repository.

Those paths resolve from the main checkout (`/Users/oliver/git/pm-mcp-core`), but phase-4 and phase-5 resolve paths against the worktree root `.plan/local/worktrees/diagram-generator`, where `../plan-marshall-documentation` names a directory that does not exist. Q-Gate finding 881af1 at 3-outline caught it.

## Fix applied in-run

The deliverable was rewritten to state that it runs in the sibling checkout `/Users/oliver/git/plan-marshall-documentation` (not the worktree), module `none`, affected files by absolute path, its own branch / commit / push / `gh pr create` in that checkout, and its verification command (`trace.py`) run there.

## Rule

When a deliverable lands in another repository, the outline names the absolute path of that repository's checkout, sets no module of the plan's repository, and states the branch, pull request and verification command of that checkout. Never use `../` paths relative to the main checkout: under use_worktree=true they do not resolve from the worktree.
