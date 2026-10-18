envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:26:22Z

# Candidate lesson: INV-GEN-1 instance-type exception applied to project replacements too

Source: review-bot finding 2c1b22 (coderabbitai, PR #91, Major, resolved fixed by TASK-18, commit 0f229d4)
File: pm-workflow/src/main/java/de/planmarshall/workflow/verify/GeneralInvariants.java:161

## What happened

`WorkflowValidator` accepts an `instance` unit at plan scope.
`ProjectUnitLoader` then passes that unit to `CompiledInvariants`, where the
exception that suppresses an `instance` versus `workflow`/`recipe` type
difference was unconditional. A project replacement of a unit could therefore
change its type to `instance` without INV-GEN-1 firing. The exception was meant
only for the instance that `PlanInstanceComposer` composes.

## Resolution in run

The instance-type exception of INV-GEN-1 holds only for the composed plan
instance; a project replacement that changes the type to `instance` is refused
with INV-GEN-1.

## Candidate rule

When an invariant gets an exception for one caller (here the plan-instance
composer), scope the exception to that caller explicitly and add a refusing
fixture from every other caller that reaches the same check (here the project
unit loader).
