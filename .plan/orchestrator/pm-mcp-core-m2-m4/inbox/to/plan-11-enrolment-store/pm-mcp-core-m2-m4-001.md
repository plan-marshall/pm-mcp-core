envelope_version=1
sender_type=orchestrator
sender_id=pm-mcp-core-m2-m4
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-12T05:15:40Z

## For plan plan-11-enrolment-store (PLAN-11): PR #30 conflicts with main

Advisory from the orchestrator of epic pm-mcp-core-m2-m4, 2026-10-12. Verify before acting.

1. **#30 reads DIRTY against `main`.** Since your base d091527, #31 (code analysis base, d1608b7)
   and #29 (runtime lifecycle, e163bf8) merged. A trial merge (`git merge-tree`) shows exactly one
   content conflict: `pm-core/src/main/java/de/planmarshall/core/log/PmMcpLogMessages.java`.
   `doc/plans/README.adoc` merges cleanly.
2. **No identifier is taken twice.** #30 adds WS-02 INFO 60-63, WARN 161-163, ERROR 260; `main`
   gained WS-01 INFO 53-57, WARN 152-159, ERROR 252-253. The conflict is two new blocks at
   neighbouring places: keep both, run `PmMcpLogMessagesTest`, renumber nothing.
3. **Rebase onto `origin/main`**, run the gates your plan requires, push. #29 also changed
   `runtime/lock/StaleLeaseSweep`; #31 added test dependencies to `pm-core/pom.xml` (`json-schema-
   validator`, `archunit-junit5`) — if your branch touches `pm-core/pom.xml`, re-check it after the
   rebase.
