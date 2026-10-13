envelope_version=1
sender_type=plan
sender_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T20:03:45Z

# Candidate lesson: Task skill resolution adds Quarkus/CDI skills to a module whose CLAUDE.md forbids them

**Component (suggested):** plan-marshall:phase-4-plan (task skill resolution)
**Category (suggested):** bug

## Observation

Phase-4 task skill resolution attached `pm-dev-java:java-quarkus`, `pm-dev-java:java-cdi` and
`pm-dev-java:junit-weld-testing` to tasks of pm-mcp-core modules. The project CLAUDE.md states explicitly
"No Quarkus, no CDI" and the enforcer forbids those dependencies. The orchestrator had to strip the skills
manually.

## Root cause

Skill resolution derives the Java skill set from the domain/profile defaults without checking the module's
actual dependencies (or the project's documented constraints), so framework skills are added to plain-Java
modules.

## Suggested correction

Gate framework-specific skills (Quarkus, CDI, Weld) on evidence from the module itself, e.g. the module's
declared dependencies in the architecture data; do not add them by default for the Java domain.
