# WS-06: Runtime worker and endpoint

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-06-runtime-worker-and-endpoint.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The runtime side of workers and endpoints: runtime control and metrics, endpoint residues, the continuation hold, the supervisor calls, the worker vector check, process-tree sweeping, the egress record and the outbound-calls guard.

## Scope

- In scope: pm-runtime worker, launch, endpoint, job, security
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-04, WS-05, WS-07, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-31-supervisor-calls-and-pass | staged | The supervisor's inbound calls and its supervision pass |
| PLAN-V2-42-runtime-metrics | staged | What the runtime is doing: its metrics |
| PLAN-V2-46-runtime-endpoint-residues | staged | Close the residues of the endpoint packages: one limits bisection, a lower-complexity answer check, specific c |
| PLAN-V2-47-continuation-hold-and-budget | staged | A hold ends at the token budget, and the supervisor's half of the continuation |
| PLAN-V2-48-worker-vector-verify-not-resolve | staged | Verify the composed worker vector without resolving it |
| PLAN-V2-53-outbound-calls-guard | staged | Enforce the closed list of outbound calls with an architecture rule in pm-runtime, and put the corpus fetch on |
| PLAN-V2-59-egress-record | staged | Record the worker egress state: the job record's egress field and the egress activity entry |
| PLAN-V2-66-process-tree-sweep-pid-reuse | staged | The PID-reuse window of the process-tree sweep, and the unreadable start instant |
| PLAN-V2-73-runtime-control | parked | Controlling the runtime: stop, start, restart, upgrade |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.
