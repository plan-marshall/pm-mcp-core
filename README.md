# pm-mcp-core

The plain-Java engine of [plan-marshall-mcp](https://github.com/plan-marshall/plan-marshall-mcp):

| Module | What it is |
|---|---|
| `pm-core` | The foundation every other library module builds on: service interfaces, stores, the TOON encoder |
| `pm-providers` | The external integrations: `pm-provider-git`, `pm-provider-ci`, `pm-provider-github`, `pm-provider-gitlab` |
| `pm-runtime` | The daemon's logic that needs no framework: the runtime start, the credential stores, the language server client, the ingestion validator |

No module here uses Quarkus, CDI, Vert.x or an MCP library; the Quarkus assembly that wires them is `pm-mcp-server`
in plan-marshall-mcp. The modules are proprietary software; see [LICENSE.md](LICENSE.md). They are deployed as
`SNAPSHOT` versions to the GitHub Packages registry of the organisation `plan-marshall` on every merge to `main` and
are never released; the product consumes them from there, and they consume `pm-api` of
[pm-mcp-clients](https://github.com/plan-marshall/pm-mcp-clients) the same way.

A machine that builds needs a token for the registry; the setup is described in the developer documentation of
plan-marshall-mcp (`doc/developer/registry-setup.adoc`).
