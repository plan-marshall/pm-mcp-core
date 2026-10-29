envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:55:23Z

# Key Sonar findings on the Sonar issue key so a line shift does not re-file a resolved issue

- component: plan-marshall:workflow-integration-sonar
- category: bug
- confidence: high
- source plan: lock-manager-and-leases

## Context

In sonar-roundtrip round 2 (HEAD ab766d8) `fetch_findings` filed five S1130 findings as new and pending. Three of them (55cf3a, b80e2c, 1e67ed) were the same Sonar issues, with the same Sonar keys, that round 1 had already resolved as accepted (1d700f, ae7998, 75e5c8); only their line numbers had moved. The unified triage had to match them by hand.

## Root cause

The finding hash includes the line number, so any edit above an issue gives it a new identity in the findings store.

## Proposed action

Derive the finding identity from the Sonar issue key (stable across line shifts) and carry the prior resolution forward, or at least flag a re-filed key as a duplicate of its resolved predecessor.

## Evidence

- aspect: chat_history_analysis — sonar-roundtrip round-2 report names the three duplicates and their round-1 hashes
- aspect: llm_to_script_opportunities — duplicate matching after a line shift done by hand in 2 triage rounds
