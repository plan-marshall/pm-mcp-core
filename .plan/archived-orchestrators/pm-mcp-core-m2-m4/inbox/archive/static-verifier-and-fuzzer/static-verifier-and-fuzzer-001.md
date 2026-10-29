envelope_version=1
sender_type=plan
sender_id=static-verifier-and-fuzzer
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T05:22:42Z

# Review bots caught two real verifier/fuzzer defects that the local gates missed

Source: pr-comment findings 28dec1 (round 1) and 4f579a (round 2) on PR #32, both resolved `fixed`; decision log b8d3d9.

## What happened

All local gates passed: quality gate, module tests, and 98.5% coverage on new code. CodeRabbit still found three real defects in `pm-workflow` `workflow.verify`:

1. `WorkflowValidator` EXIT_PATH, workspace branch (line 272): the check filtered only on the backward closure of the start state. That flagged every terminal state of a workspace unit, the abort terminals included, so any workspace unit that declared a terminal state could never pass. The plan-scope branch put terminals into `exits` and did not have the bug. Fixed by TASK-10 and TASK-12.
2. `TransitionFuzzer` line 149: a hard-coded list of built-in guard atoms (`InWorktree`, `HasOpenFindings`, `AllTasksCompleted`) mirrored the `GuardExpr` variants. Fixed by TASK-13, which now derives the list from an exhaustive switch.
3. `TransitionFuzzer`: the int run counter in the for-loop wraps to MIN_VALUE at MAX_VALUE, so a run at the limit never ends. Fixed by TASK-13.

## Candidate rule

- A rule that holds per scope (plan vs workspace) needs a refusing **and** a passing fixture for each scope. Each fixture must include a terminal state, because the scope branches compute different exit sets.
- A list that mirrors a sealed type's variants must be derived from that type, for example through an exhaustive switch. A hand-written copy is a defect, as the repo's own CodeRabbit path instruction already says.
- Every loop counter that can reach a configured upper limit needs a boundary test at that limit.

Possible owners: pm-mcp-core test conventions for `pm-workflow`, or a project-level lesson for verifier work.
