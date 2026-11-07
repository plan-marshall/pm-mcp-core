envelope_version=1
sender_type=plan
sender_id=plan-v2-92-providers-tests
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-07T08:21:08Z

component=plan-marshall:manage-execution-manifest
category=improvement
confidence=high
source_plan=plan-v2-92-providers-tests

# Compose request Done-when verification (coverage, Sonar) into the manifest

## Context

The PLAN-V2-92 request's Done-when demanded `verify -Pcoverage` and a SonarCloud pull-request search with no open issue and a green quality gate before the PR is reported ready. The composed manifest carried only `verify:quality-gate` and `verify:module-tests` in phase 5, and `sonar-roundtrip` in phase 6. Coverage never ran in any phase, and the PR body's Test Plan marks it "not run". The plan landed (PR #312) without it.

## Root cause

Manifest composition selects verification steps from marshal.json candidates and the change type only; verification commands the request names explicitly are not mapped onto manifest steps, and no gate compares the request's Done-when against the composed step set.

## Proposed action

Have the outline (or manifest compose) extract the request's explicit verification demands (coverage profile, integration-test profiles, external scans) and either compose a matching step or record an explicit, operator-visible waiver; surface the mismatch before 6-finalize closes.

## Evidence

- aspect: request_result_alignment - gap "Done-when: verify -Pcoverage ... Not run in any phase"
- aspect: manifest_decisions - phase_5.verification_steps = [verify:quality-gate, verify:module-tests]
- aspect: chat_history_analysis - create-pr leaf: "`verify -Pcoverage` and the SonarCloud pull-request check are marked not run"
