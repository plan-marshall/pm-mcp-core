# WS-07: Build chain

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-07-build-chain.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The build side of the runtime: native build parsers, the failed and unparsed outcomes, the gate honesty facts and baseline separation, coverage report parsers and adaptive timeout learning.

## Scope

- In scope: pm-core core.build, pm-runtime job build parsers
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-04, WS-05, WS-06, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-08-native-build-parsers-jvm | staged | Native build output parsers for the JVM tools: Maven, Gradle and the OpenRewrite log |
| PLAN-V2-18-coverage-report-parsers | staged | Native coverage report parsers: JaCoCo, Cobertura, LCOV and Istanbul |
| PLAN-V2-28-native-build-parsers-npm-and-python | staged | Native build output parsers for npm, TypeScript, ESLint, Jest, TAP and pyproject |
| PLAN-V2-36-failed-unparsed-and-parser-wiring | staged | Wire the build output parsers into the job runner and make `failed_unparsed` carry its evidence |
| PLAN-V2-37-gate-honesty-facts | staged | Gate honesty facts: examined population, gate coverage, skipped tests and untracked side state |
| PLAN-V2-51-adaptive-timeout-learning | staged | Feed the learned build budgets: which runs count, poisoned and cold runs, the timing observation step |
| PLAN-V2-55-gate-baseline-separation | staged | Baseline separation of a red gate and the settlement step that stamps the gate's ledger row |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.
