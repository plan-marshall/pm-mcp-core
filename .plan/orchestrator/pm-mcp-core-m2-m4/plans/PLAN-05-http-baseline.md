# PLAN-05: HTTP baseline: cui-http for calls, the mock web server for tests

epic: pm-mcp-core-m2-m4
workstream: WS-08

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-05-http-baseline.md` and is queued as one row file, `queue/PLAN-05.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Make the operator's HTTP rules of 2026-10-09 true for the code that exists today, before the work packages build on it: every HTTP call goes through `cui-http`, and every test that involves HTTP runs against `cui-test-mockwebserver-junit5`. This plan is not a package of `doc/plans/` yet; it adds its package file and index row in its own pull request, as the index asks for a newly discovered package.

## Deliverables

1. The three hand-made `FakeServer` test doubles (pm-provider-ci, pm-provider-github, pm-provider-gitlab) are replaced by `cui-test-mockwebserver-junit5`; no test starts `com.sun.net.httpserver.HttpServer` or a socket of its own.
2. `cui-test-mockwebserver-junit5` is a test-scope dependency of every module with an HTTP test, its version managed where the other cui test libraries are managed.
3. `CiHttpClient` and every other production HTTP call run through `cui-http`; a direct use of `java.net.http` that bypasses it is removed.
4. The package file `doc/plans/http-baseline.adoc` and its row in the index, set to `done` by the same pull request.

**Done when** (the package's own acceptance): No class named `FakeServer` and no `com.sun.net.httpserver` import remain in the repository; the provider tests pass on the mock web server; `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green.

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package): claim it with a draft pull request that sets its row to `in progress`, build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green. The documentation pull request in `plan-marshall-documentation` names the code pull request and merges after it.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: no identifier block.** This plan is expected to add no INFO, WARN or ERROR message. If the trace matrix shows one, stop and report it through the inbox message instead of taking an identifier of another workstream.

## Claim Labels

- OBSERVED: three hand-made HTTP test doubles exist, each on `com.sun.net.httpserver.HttpServer` — read at `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/FakeServer.java`, `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/FakeServer.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/FakeServer.java`; seven Java files name `FakeServer` (grep on 2026-10-09)
- OBSERVED: no `pom.xml` of this repository names `cui-test-mockwebserver-junit5` — grep over every `pom.xml` on 2026-10-09
- OBSERVED: `CiHttpClient` is the only production class that imports `java.net.http` — read at `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/CiHttpClient.java`
- OBSERVED: `cui-http` is a dependency of `pm-provider-ci`, `pm-provider-github`, `pm-provider-gitlab` and `pm-runtime`, and is forbidden in `pm-core` and `pm-workflow` — read at the module `pom.xml` files and the enforcer executions of `pom.xml`
- HYPOTHESIS: `CiHttpClient` bypasses `cui-http` rather than only naming `java.net.http` types that `cui-http` hands out — confirm/refute at `CiHttpClient.java` § its send path (verify-at-outline)
- HYPOTHESIS: the version of `cui-test-mockwebserver-junit5` is managed by the parent chain, as for `cui-test-juli-logger` — confirm/refute at `../pm-mcp-parent/pom.xml` and its parent; when it is not, the management goes there by pull request first (verify-at-outline)
- HYPOTHESIS: the provider modules are the only place with HTTP tests; `pm-provider-sonar` and `pm-runtime` have none — confirm/refute by a search of the test sources for a server or socket (verify-at-outline)
- Verify-first clause: settle the three hypotheses before scoping; if `CiHttpClient` already runs through `cui-http`, deliverable 3 shrinks to a guarding test and the scope is re-cut.

## Expected Surface

- OBSERVED: `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/CiHttpClient.java` — imports java.net.http today
- OBSERVED: `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/` — holds FakeServer.java and its users
- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/` — holds FakeServer.java and its users
- OBSERVED: `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/` — holds FakeServer.java and its users
- OBSERVED: `pm-providers/pm-provider-ci/pom.xml` — test dependency
- OBSERVED: `pm-providers/pm-provider-github/pom.xml` — test dependency
- OBSERVED: `pm-providers/pm-provider-gitlab/pom.xml` — test dependency
- HYPOTHESIS: `pom.xml` — only if the version is not managed by the parent
- HYPOTHESIS: `doc/plans/http-baseline.adoc` — new package file, since the index is the only place that states a package
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: no other plan by package; every plan through `doc/plans/README.adoc`
- Adjacent to: `pm-provider-sonar` and `pm-provider-git`, which hold no hand-made HTTP test double today and stay untouched unless the third hypothesis is refuted; `pm-runtime`, whose `runtime.ingest` uses the URL validation of `cui-http` and makes no call

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-05-http-baseline.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
