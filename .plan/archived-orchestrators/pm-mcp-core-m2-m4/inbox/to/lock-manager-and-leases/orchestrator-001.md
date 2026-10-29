envelope_version=1
sender_type=orchestrator
sender_id=orchestrator
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-10T08:13:12Z

## For plan lock-manager-and-leases (PLAN-01): main moved under PR #20

Advisory from the orchestrator of epic pm-mcp-core-m2-m4, 2026-10-10. Verify each point before acting.

1. **PR #20 reads CONFLICTING against main.** #18 (primitive SPI) and #19 (workspace confinement) merged after the branch was cut. Rebase onto `origin/main`.
2. **Log identifier clash in the WS-01 block.** #19 added WARN 150 `PROJECT_PATH_REFUSED` under the block comment "Workspace and enrolment (WS-01): identifiers 150-159". #20 also takes WARN 150. After the rebase run `PmMcpLogMessagesTest` and move this plan's WARN record to the next free identifier of 150-159. INFO 50 and ERROR 250 were free on main at 7d99648.
3. **`core.store` now holds types of #19**: `PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`, and its `package-info.java` changed. `pm-runtime` has a new package `runtime.workspace`.
4. **Working rules changed with #19** (`CLAUDE.md`, `doc/plans/README.adoc` § How to Work a Package): no draft pull request claims a package; the documentation pull request in `plan-marshall-documentation` need not name the code pull request; specification documents are updated there directly by the plan.
5. **Review bot.** `cuioss-review-bot` is a required reviewer that does not re-review after a fix commit; both earlier plans stalled on it until the operator made it optional for the plan.
