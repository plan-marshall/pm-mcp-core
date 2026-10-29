envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T06:40:25Z

# Candidate lesson: manage-providers addressed with a non-existent script notation

- Component: plan-marshall:manage-providers
- Suggested category: anti-pattern
- Signal: script failure cluster (invalid notation, 2 occurrences, phase 6-finalize)

## Evidence

- script-execution-2026-10-12.log 20:08:36Z and 21:47:08Z: `plan-marshall:manage-providers:manage-providers --help`,
  exit 1: "Invalid notation ... The third part 'manage-providers' appears to be a subcommand, not a
  script name. Correct format: plan-marshall:manage-providers:credentials manage-providers".
- Both calls came from the sonar-roundtrip rounds.

## Suggested correction

The skill name is not the script name here (`credentials`). Make the canonical invocation in the
manage-providers SKILL.md and every reference from sonar-roundtrip name `plan-marshall:manage-providers:credentials`.
