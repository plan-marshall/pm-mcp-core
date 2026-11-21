# pm-mcp-core

The plain-Java engine of [plan-marshall-mcp](https://github.com/plan-marshall/plan-marshall-mcp).

Its modules and what each of them is are listed only in the
[Module Structure Specification](../plan-marshall-documentation/doc/specification/module-structure.adoc#modules) of
plan-marshall-documentation, checked out beside this repository; each module keeps its README in its own directory.

The architectural decision-log records of the repository and of its modules are indexed in
[`doc/adr/README.adoc`](doc/adr/README.adoc).

No module here uses Quarkus, CDI, Vert.x or an MCP library; the Quarkus assembly that wires them is `pm-mcp-server`
in plan-marshall-mcp. The modules are proprietary software; see [LICENSE.md](LICENSE.md). They are deployed as
`SNAPSHOT` versions to the GitHub Packages registry of the organisation `plan-marshall` on every merge to `main` and
are never released; the product consumes them from there, and they consume `pm-api` of
[pm-mcp-clients](https://github.com/plan-marshall/pm-mcp-clients) the same way.

A machine that builds needs a token for the registry; the setup is described in the developer documentation of
plan-marshall-mcp (`doc/developer/registry-setup.adoc`).
