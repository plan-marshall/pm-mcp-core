# WS-05: Profiles, roles and tasks

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-05-profiles-roles-and-tasks.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The model work on the engine side: the profile loader, role sets and the model caller, the task queue and protocol, waits, consultation, the session as worker and role qualification.

## Scope

- In scope: pm-core packages core.profile, core.role, core.queue (task records); pm-workflow packages model, task, consult and the waits in engine
- Out of scope: launching and supervising worker processes (WS-04); the engine itself (WS-03)

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-19-profile-loader | parked | Profile loader and validation |
| PLAN-25-waits | staged | Bounded and progress waits |
| PLAN-26-role-sets-and-model-caller | staged | Role artifacts, role sets and the model caller |
| PLAN-28-task-queue-and-protocol | staged | Task queue and task protocol |
| PLAN-32-consultation | staged | Consultation through the server |
| PLAN-33-session-as-worker | staged | The interactive session as optional worker |
| PLAN-34-role-qualification | staged | Role qualification |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.
