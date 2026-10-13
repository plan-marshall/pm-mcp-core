# pm-mcp-core

The plain-Java engine of [plan-marshall-mcp](https://github.com/plan-marshall/plan-marshall-mcp):

| Module | What it is |
|---|---|
| [`pm-core`](pm-core/README.adoc) | The foundation every other library module builds on: service interfaces, stores, the TOON encoder |
| [`pm-workflow`](pm-workflow/README.adoc) | The workflow engine: the workflow language, the verifier, the interpreter of the state machine, the plan-domain primitives |
| [`pm-providers`](pm-providers/README.adoc) | The external integrations: [`pm-provider-git`](pm-providers/pm-provider-git/README.adoc), [`pm-provider-ci`](pm-providers/pm-provider-ci/README.adoc), [`pm-provider-github`](pm-providers/pm-provider-github/README.adoc), [`pm-provider-gitlab`](pm-providers/pm-provider-gitlab/README.adoc), [`pm-provider-sonar`](pm-providers/pm-provider-sonar/README.adoc) |
| [`pm-runtime`](pm-runtime/README.adoc) | The daemon's logic that needs no framework: the runtime start, the credential stores, the language server client, the ingestion validator |

The architectural decision records of the repository and of its modules are indexed in
[`doc/adr/README.adoc`](doc/adr/README.adoc).

No module here uses Quarkus, CDI, Vert.x or an MCP library; the Quarkus assembly that wires them is `pm-mcp-server`
in plan-marshall-mcp. The modules are proprietary software; see [LICENSE.md](LICENSE.md). They are deployed as
`SNAPSHOT` versions to the GitHub Packages registry of the organisation `plan-marshall` on every merge to `main` and
are never released; the product consumes them from there, and they consume `pm-api` of
[pm-mcp-clients](https://github.com/plan-marshall/pm-mcp-clients) the same way.

A machine that builds needs a token for the registry; the setup is described in the developer documentation of
plan-marshall-mcp (`doc/developer/registry-setup.adoc`).
