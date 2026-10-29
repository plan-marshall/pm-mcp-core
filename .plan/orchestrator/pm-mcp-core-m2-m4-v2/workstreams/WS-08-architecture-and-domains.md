# WS-08: Architecture and domains

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-08-architecture-and-domains.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Domain extensions and architecture: domain axes and descriptors, merges, module discovery, build-map and derive verification, queries, resolvers, the documentation domain verbs and the change map.

## Scope

- In scope: pm-core core.architecture, core.domain, pm-provider-git change map
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-04, WS-05, WS-06, WS-07, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-09-domain-axes-and-descriptors | staged | The domain descriptor and Axes A and B: applicability, profiles and build-map routes |
| PLAN-V2-14-module-discovery | staged | Server-owned module discovery: the live crawl, the descriptor parsers and the build-file dependency parsers |
| PLAN-V2-30-architecture-merges | staged | Provenance-preserving merges of dependency edges (Axis C) and path ownership (Axis D) |
| PLAN-V2-35-build-map-and-derive-verification | staged | The build map and the deterministic derive-verification, and the resolver enablement binding |
| PLAN-V2-61-documentation-domain-verbs | staged | The documentation domain verbs: asciidoc.verify-links, asciidoc.format and the asciidoctor diagnostics |
| PLAN-V2-78-change-map | parked | The change map of a large change |
| PLAN-V2-79-architecture-resolvers | parked | The Axis C resolvers: build-system, import, cross-reference, LSP and project-script |
| PLAN-V2-80-architecture-queries | staged | The architecture queries: the server-side computation behind pm://architecture/* |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.
