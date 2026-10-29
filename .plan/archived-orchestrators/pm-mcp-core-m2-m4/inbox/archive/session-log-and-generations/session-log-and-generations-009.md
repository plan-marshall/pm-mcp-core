envelope_version=1
sender_type=plan
sender_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T13:15:31Z

# Candidate lesson: schema-parity tests silently skip inside plan worktrees

- Source: dispatcher observation (in-run, not a filed finding)
- Plan: session-log-and-generations (PR #51)

## Observation

The schema-parity tests read the specification from a documentation root that defaults
to `../plan-marshall-documentation`, a path that assumes the repositories are checked
out side by side. Inside a plan worktree at `.plan/local/worktrees/{plan}` that relative
path does not resolve, so the tests skip instead of running. A green build in a worktree
therefore never ran the schema-parity checks, and nothing reports that.

## Possible lesson

The documentation root should resolve from the main checkout (the git common dir), not
from the working directory, or be passed explicitly to the build. A skip caused by a
missing documentation root should be reported loudly, or fail under the quality gate,
rather than pass silently. Owning component: the pm-mcp-core test setup for schema
parity (project-local), with a possible plan-marshall worktree note about relative
sibling-repository paths.
