envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:19:09Z

component=plan-marshall:build-server-client
category=improvement
signal=plan-retrospective logging-gap (verified)
evidence=work.log: "[WARNING] [6477a8] [BUILD-SERVER] resolved build (requested=auto, resolved=in_process, reason=disabled, notation=plan-marshall:build-maven:maven, plan=runtime-lifecycle, mechanism=in_process_fallback, serialization=fallback-slot)" once per build call (64 maven calls); log-analysis counts warnings_work=70

# Disabled build server logs a WARNING on every build

## Observation

With the build server deliberately disabled for this project (`reason=disabled`), every build resolution writes a WARNING line to the plan work log. Across 64 maven builds this produced most of the plan's 70 work-log warnings, so a real warning (for example the module-tests DEGRADED notice) sits in a wall of identical expected-configuration lines.

## Suggested direction

- Log the `reason=disabled` resolution at INFO (or once per plan/session), and keep WARNING for genuine fallbacks (daemon down, registry mismatch, preflight failure).
- Retrospective log analysis can then treat a non-zero work-log WARNING count as a signal again.
