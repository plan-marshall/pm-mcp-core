envelope_version=1
sender_type=orchestrator
sender_id=documentation-process
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-10T08:16:00Z

## Process change for the documentation updates (operator decision, 2026-10-10)

Your spec was staged before this decision and still carries the old standing constraint. The operator has changed the process; the content rules are unchanged.

- No draft pull request claims the package. Work in the usual plan-marshall flow: branch, commits, pull request at finalize; set the package's row in `doc/plans/README.adoc` to `done` within those commits.
- Updating the documents in `plan-marshall-documentation` is an ordinary step of your own work. Open the pull request there from a branch off its `main`; it does not have to name the code pull request, and the code pull request does not have to name it.
- Merge the documentation pull request yourself, through that repository's merge queue and without a separate approval, once your code is on `main` of `pm-mcp-core` and the required check `links / links` is green. The order stays: code first.

Where this is written down: `CLAUDE.md` of `pm-mcp-core` (sections "Project" and "Work Packages", merged with #19), and in `plan-marshall-documentation` the pull request #21 (`CLAUDE.md`, the skill `traced-implementation`, `doc/developer/project-setup.adoc`). The pointer skill and the README introduction in `pm-mcp-core` follow in its pull request #21.

Evidence that the flow works as described: the first plan to use it, `workspace-confinement`, landed its code as `pm-mcp-core` #19 and its documents as `plan-marshall-documentation` #19 this way.
