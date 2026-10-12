envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:13:12Z

component=plan-marshall:workflow-integration-sonar
category=bug
signal=orchestrator-observation (verified)
evidence=manage-findings list --type sonar-issue returns 85 findings for plan runtime-lifecycle while the PR #29 quality gate reported 5 new issues; the surplus 80 are project-wide issues

# A triage leaf fetched Sonar issues without --pr and stored 80 project-wide findings it could not delete

## Observation

During the finalize triage of PR #29 a sub-agent called the Sonar fetch without the pull-request scope. The fetch filed every open project issue into the plan's findings store: 85 sonar-issue findings, of which only 5 belonged to the PR (the SonarCloud gate comment counted "5 New issues"). manage-findings offers no delete, so the 80 out-of-scope findings stay in the plan store and had to be resolved or ignored by hand.

## Suggested direction

- Make the PR scope the default (or required) for `fetch_findings` when the caller runs inside a plan with a PR, and refuse a project-wide fetch into a plan store unless explicitly requested.
- Give manage-findings a sanctioned way to retire findings filed by a mis-scoped fetch (for example resolve-by-filter with a reason), so a scoping mistake is recoverable.
